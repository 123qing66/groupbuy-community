import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 下单并发压测（Issue #27：200 并发 / 1000 库存 / 无超卖）。
 *
 * <p>为什么用虚拟线程而不是固定 200 线程池：并发度由 {@link Semaphore} 精确控制成
 * {@code -Dconcurrency=200}，虚拟线程只负责让"200 个请求同时在飞"这件事不额外吃内存。
 * 固定线程池也能做，但 1000 个任务排队时，线程池的排队语义会掺进延迟数据里。</p>
 *
 * <p>跑法（先跑 SeedData 拿到 token）：</p>
 * <pre>
 *   java perf/LoadTest.java
 *   java perf/LoadTest.java -Dconcurrency=200 -Dtotal=1000
 * </pre>
 *
 * <p><b>重跑前必须重置</b>：同一批用户对同一活动有幂等键
 * {@code groupbuy:order:idem:<userId>:<activityId>}，不清掉的话第二轮请求
 * 会命中幂等直接返回上一轮订单（HTTP 200、code 0），但库存一点没扣 —— 判定会失真。
 * 用 {@code perf/seed.sh} 重置，它会把订单、库存、幂等键、活动缓存一起清掉。</p>
 */
public class LoadTest {

    private static final Pattern CODE = Pattern.compile("\"code\"\\s*:\\s*(-?\\d+)");

    public static void main(String[] args) throws Exception {
        String base = System.getProperty("base", "http://127.0.0.1:8080");
        long activityId = Long.getLong("activity", 999001L);
        long goodsId = Long.getLong("goods", 999001L);
        int concurrency = Integer.getInteger("concurrency", 200);
        int total = Integer.getInteger("total", 1000);
        int numPerOrder = Integer.getInteger("num", 1);
        Path runDir = Path.of(System.getProperty("rundir", "perf/.run"));

        Path tokenFile = runDir.resolve("tokens.txt");
        if (!Files.exists(tokenFile)) {
            System.err.println("[失败] 找不到 " + tokenFile.toAbsolutePath() + "，先跑 java perf/SeedData.java");
            System.exit(1);
        }
        List<String> lines = Files.readAllLines(tokenFile, StandardCharsets.UTF_8);
        if (lines.size() < total) {
            System.err.printf("[失败] token 只有 %d 个，不够 %d 个请求；加大 -Dusers 重跑 SeedData%n",
                    lines.size(), total);
            System.exit(1);
        }
        List<String> tokens = new ArrayList<>(total);
        for (int i = 0; i < total; i++) {
            String line = lines.get(i).trim();
            if (!line.isEmpty()) {
                tokens.add(line.substring(line.indexOf('|') + 1));
            }
        }
        if (tokens.size() < total) {
            System.err.printf("[失败] 有效的 token 只有 %d 个，不够 %d 个请求；重跑 SeedData%n",
                    tokens.size(), total);
            System.exit(1);
        }

        HttpClient http = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(5))
                .build();

        // 预热：先把客户端到 Tomcat 的连接与 TLS/keep-alive 建立好，避免首个请求单独背连接开销
        for (int i = 0; i < Math.min(5, total); i++) {
            send(http, base, activityId, goodsId, numPerOrder, tokens.get(i));
        }

        long[] latencyNanos = new long[total];
        int[] codeOf = new int[total];
        Map<Integer, Integer> codeCount = new ConcurrentHashMap<>();
        Map<Integer, Integer> httpErrorCount = new ConcurrentHashMap<>();
        AtomicInteger cursor = new AtomicInteger(0);
        Semaphore gate = new Semaphore(concurrency);

        System.out.printf("开始压测：并发 %d，总请求 %d，活动 %d，商品 %d，每单 %d 份%n",
                concurrency, total, activityId, goodsId, numPerOrder);
        long startNs = System.nanoTime();

        try (ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < total; i++) {
                final String token = tokens.get(i);
                pool.execute(() -> {
                    try {
                        gate.acquire();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                    try {
                        int slot = cursor.getAndIncrement();
                        long t0 = System.nanoTime();
                        Resp r = send(http, base, activityId, goodsId, numPerOrder, token);
                        latencyNanos[slot] = System.nanoTime() - t0;
                        codeOf[slot] = r.code();
                        codeCount.merge(r.code(), 1, Integer::sum);
                        if (r.httpStatus() != 200) {
                            httpErrorCount.merge(r.httpStatus(), 1, Integer::sum);
                        }
                    } catch (Exception e) {
                        int slot = Math.min(cursor.get(), total - 1);
                        codeOf[slot] = -1;
                        codeCount.merge(-1, 1, Integer::sum);
                    } finally {
                        gate.release();
                    }
                });
            }
        }
        double wallSeconds = (System.nanoTime() - startNs) / 1_000_000_000.0;

        // ---------- 统计 ----------
        long[] sorted = latencyNanos.clone();
        Arrays.sort(sorted);
        int success = codeCount.getOrDefault(0, 0);
        int stockShort = codeCount.getOrDefault(10003, 0);
        int duplicated = codeCount.getOrDefault(10015, 0);
        int throttled = codeCount.getOrDefault(429, 0);
        int notOngoing = codeCount.getOrDefault(10002, 0);
        int other = total - success - stockShort - duplicated - throttled - notOngoing;

        StringBuilder sb = new StringBuilder();
        sb.append("=== 压测结果（#27）").append(System.lineSeparator());
        sb.append(String.format("参数            并发 %d / 总请求 %d / 库存 1000 / 每人限购 1%n", concurrency, total));
        sb.append(String.format("总耗时          %.2f s%n", wallSeconds));
        sb.append(String.format("吞吐            %.1f req/s%n", total / wallSeconds));
        sb.append(String.format("成功(code=0)    %d  (%.1f%%)%n", success, success * 100.0 / total));
        sb.append(String.format("库存不足 10003  %d%n", stockShort));
        sb.append(String.format("重复下单 10015  %d%n", duplicated));
        sb.append(String.format("被限流 429      %d%n", throttled));
        sb.append(String.format("活动未开始 10002 %d%n", notOngoing));
        sb.append(String.format("其他失败        %d%n", Math.max(other, 0)));
        if (!httpErrorCount.isEmpty()) {
            sb.append("非 200 的 HTTP 状态：").append(httpErrorCount).append(System.lineSeparator());
        }
        sb.append(String.format("延迟 P50        %d ms%n", ms(sorted[(int) (total * 0.50)])));
        sb.append(String.format("延迟 P95        %d ms%n", ms(sorted[(int) Math.min(total * 0.95, total - 1)])));
        sb.append(String.format("延迟 P99        %d ms%n", ms(sorted[(int) Math.min(total * 0.99, total - 1)])));
        sb.append(String.format("延迟 Max        %d ms%n", ms(sorted[total - 1])));
        sb.append(System.lineSeparator());

        // ---------- 判定 ----------
        int expectedSuccess = Math.min(total, 1000);
        if (success == expectedSuccess && stockShort == 0 && throttled == 0) {
            sb.append("✅ 全部请求成功，无超卖、无限流误伤。请再跑 perf/sql/02-verify.sql 核对库存与订单守恒。")
                    .append(System.lineSeparator());
        } else if (success + stockShort == total) {
            sb.append(String.format(
                    "⚠️ 成功 %d + 库存不足 %d = 总请求 %d：库存先被抢完，属正常表现；核对 available 是否为 0 且不为负。%n",
                    success, stockShort, total));
        } else {
            sb.append("❌ 出现非预期分布，重点看「其他失败」与 HTTP 状态码。").append(System.lineSeparator());
        }
        if (duplicated > 0) {
            sb.append(String.format(
                    "⚠️ 有 %d 个请求返回 10015：说明幂等键或唯一索引生效（这是对的），但本轮用的用户上一轮下过单，数据没重置干净。%n",
                    duplicated));
        }

        String report = sb.toString();
        System.out.println();
        System.out.print(report);

        Path out = runDir.resolve("result.txt");
        Files.writeString(out, report, StandardCharsets.UTF_8);
        System.out.println();
        System.out.println("报告已写入 " + out.toAbsolutePath());
        System.out.println("下一步：mysql ... groupbuy < perf/sql/02-verify.sql");
    }

    private static long ms(long nanos) {
        return nanos / 1_000_000;
    }

    private static Resp send(HttpClient http, String base, long activityId, long goodsId,
                             int num, String token) throws Exception {
        String body = "{\"activityId\":" + activityId + ",\"goodsId\":" + goodsId + ",\"num\":" + num + "}";
        HttpRequest req = HttpRequest.newBuilder(URI.create(base + "/api/orders"))
                .timeout(Duration.ofSeconds(30))
                .header("Content-Type", "application/json; charset=utf-8")
                .header("Authorization", "Bearer " + token)
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        Matcher m = CODE.matcher(resp.body());
        int code = m.find() ? Integer.parseInt(m.group(1)) : -1;
        return new Resp(resp.statusCode(), code);
    }

    private record Resp(int httpStatus, int code) {
    }
}
