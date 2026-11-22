import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 压测数据准备（Issue #27）：批量注册居民账号并换取 JWT，供 LoadTest 使用。
 *
 * <p>为什么不用 SQL 直插用户：密码列是 BCrypt 密文，直插要么伪造哈希（脆），
 * 要么引入 spring-security-crypto 依赖（要写 pom）。走注册接口由服务端加密，
 * 最贴近真实链路，也顺便压一遍 #1/#2。</p>
 *
 * <p>JDK 21+ 支持单文件直接运行，不需要编译、不需要加依赖：</p>
 * <pre>
 *   java perf/SeedData.java                              # 默认 1000 个用户
 *   java perf/SeedData.java -Dusers=200                  # 少造一点
 *   java perf/SeedData.java -Dbase=http://127.0.0.1:8080 # 换地址
 * </pre>
 *
 * <p>产物：{@code perf/.run/tokens.txt}，每行 {@code userId|token}，
 * 按 userId 升序，LoadTest 按这个顺序取前 N 个。</p>
 *
 * <p>可重复执行：用户名固定为 {@code perf_u0001...}，已存在（10012）直接跳过注册去登录。</p>
 */
public class SeedData {

    /** 压测账号统一密码，随便定的，只用于本地压测库 */
    private static final String PASSWORD = "perf123456";

    /** 注册/登录阶段并发度：不参与被测量，50 足够快，又不会把 BCrypt 的 CPU 打满 */
    private static final int DEFAULT_CONCURRENCY = 50;

    private static final Pattern CODE = Pattern.compile("\"code\"\\s*:\\s*(-?\\d+)");
    private static final Pattern MSG = Pattern.compile("\"msg\"\\s*:\\s*\"([^\"]*)\"");
    private static final Pattern TOKEN = Pattern.compile("\"token\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern USER_ID = Pattern.compile("\"userId\"\\s*:\\s*(\\d+)");

    public static void main(String[] args) throws Exception {
        String base = System.getProperty("base", "http://127.0.0.1:8080");
        int userCount = Integer.getInteger("users", 1000);
        int concurrency = Integer.getInteger("concurrency", DEFAULT_CONCURRENCY);
        Path runDir = Path.of(System.getProperty("rundir", "perf/.run"));
        Files.createDirectories(runDir);

        HttpClient http = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(5))
                .build();

        // ---------- 0. 服务必须先活着，否则后面 1000 个请求全是连接失败，看不出真原因 ----------
        HttpResponse<String> health = call(http, base + "/api/health", null, null);
        if (health.statusCode() != 200 || !health.body().contains("UP")) {
            System.err.println("[失败] 服务没起来：" + base + "/api/health 返回 " + health.statusCode()
                    + "，先把应用启动（先起 MySQL 与 Redis）");
            System.exit(1);
        }
        System.out.println("[1/3] 健康检查通过：" + base);

        // ---------- 1. 并发注册 + 登录 ----------
        Queue<String> ok = new ConcurrentLinkedQueue<>();
        Queue<String> fail = new ConcurrentLinkedQueue<>();
        AtomicInteger existed = new AtomicInteger();

        Semaphore gate = new Semaphore(concurrency);
        try (ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 1; i <= userCount; i++) {
                final int idx = i;
                pool.execute(() -> {
                    try {
                        gate.acquire();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                    try {
                        prepareUser(http, base, idx, ok, fail, existed);
                    } finally {
                        gate.release();
                    }
                });
            }
        } // close() 会等所有虚拟线程跑完

        System.out.printf("[2/3] 账号准备完成：成功 %d 个，其中已存在 %d 个，失败 %d 个%n",
                ok.size(), existed.get(), fail.size());
        if (!fail.isEmpty()) {
            System.err.println("      前 5 条失败原因：");
            fail.stream().limit(5).forEach(s -> System.err.println("      - " + s));
        }

        // ---------- 2. 落盘 ----------
        List<String> lines = new ArrayList<>(ok);
        lines.sort(Comparator.comparingLong(s -> Long.parseLong(s.substring(0, s.indexOf('|')))));
        Path tokens = runDir.resolve("tokens.txt");
        Files.write(tokens, lines, StandardCharsets.UTF_8);

        System.out.println("[3/3] token 已写入 " + tokens.toAbsolutePath() + "（共 " + lines.size() + " 行）");
        if (!fail.isEmpty()) {
            System.exit(1);
        }
        System.out.println();
        System.out.println("下一步：java perf/LoadTest.java");
    }

    /** 注册（已存在则跳过）+ 登录，成功则把 userId|token 放进 ok */
    private static void prepareUser(HttpClient http, String base, int idx,
                                    Queue<String> ok, Queue<String> fail, AtomicInteger existed) {
        String username = String.format("perf_u%04d", idx);
        try {
            String regBody = "{\"username\":\"" + username + "\""
                    + ",\"password\":\"" + PASSWORD + "\""
                    + ",\"realName\":\"压测用户" + idx + "\""
                    + ",\"roleCode\":\"ROLE_RESIDENT\"}";
            HttpResponse<String> reg = call(http, base + "/api/users/register", regBody, null);
            int regCode = code(reg.body());
            if (regCode == 10012) {
                existed.incrementAndGet();      // 上次压测留下的，直接登录
            } else if (regCode != 0) {
                fail.add(username + " 注册失败 code=" + regCode + " msg=" + msg(reg.body()));
                return;
            }

            String loginBody = "{\"username\":\"" + username + "\",\"password\":\"" + PASSWORD + "\"}";
            HttpResponse<String> login = call(http, base + "/api/auth/login", loginBody, null);
            int loginCode = code(login.body());
            if (loginCode != 0) {
                fail.add(username + " 登录失败 code=" + loginCode + " msg=" + msg(login.body()));
                return;
            }
            String token = group(TOKEN, login.body());
            String userId = group(USER_ID, login.body());
            if (token == null || userId == null) {
                fail.add(username + " 登录响应缺少 token/userId：" + trim(login.body()));
                return;
            }
            ok.add(userId + "|" + token);
        } catch (Exception e) {
            fail.add(username + " 异常：" + e.getClass().getSimpleName() + " " + e.getMessage());
        }
    }

    // ==================== HTTP 小工具（不引第三方库，用正则取字段） ====================

    private static HttpResponse<String> call(HttpClient http, String url, String jsonBody, String token)
            throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(10));
        if (jsonBody == null) {
            b.GET();
        } else {
            b.header("Content-Type", "application/json; charset=utf-8")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody, StandardCharsets.UTF_8));
        }
        if (token != null) {
            b.header("Authorization", "Bearer " + token);
        }
        return http.send(b.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private static int code(String body) {
        String v = group(CODE, body);
        return v == null ? -1 : Integer.parseInt(v);
    }

    private static String msg(String body) {
        return group(MSG, body);
    }

    private static String group(Pattern p, String body) {
        Matcher m = p.matcher(body);
        return m.find() ? m.group(1) : null;
    }

    private static String trim(String s) {
        return s == null ? "null" : (s.length() > 200 ? s.substring(0, 200) + "..." : s);
    }
}
