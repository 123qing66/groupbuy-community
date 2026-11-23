package com.groupbuy.redis;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.groupbuy.dto.ActivityVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * {@link CacheClient} 序列化回归 + 缓存三板斧单测（A 负责，#4 考核点 / Issue #28 前置修复）。
 *
 * <p><b>序列化回归（远端 develop 合入）：</b> CacheClient 原来用裸 {@code new ObjectMapper()}，
 * 而缓存落点 {@link ActivityVO} 带 {@code LocalDateTime}。没注册 {@code JavaTimeModule} 时会抛
 * {@code InvalidDefinitionException}，异常被 {@code writeLogical} 的 catch 吞掉——表现不是报错，
 * 而是<b>缓存静默地永远写不进去</b>。这组用例用内存 Map 顶掉 Redis，专验"写进去能原样读回来"。</p>
 *
 * <p><b>三板斧（本地 feature 合入）：</b> 空值缓存防穿透、逻辑过期互斥锁防击穿、TTL 抖动防雪崩。</p>
 *
 * <p>纯 Mockito，不连 Redis，CI 可跑。</p>
 */
@ExtendWith(MockitoExtension.class)
class CacheClientTest {

    private static final String KEY = "activity:detail:999001";
    private static final LocalDateTime START = LocalDateTime.of(2026, 11, 19, 10, 0, 0);
    private static final LocalDateTime END = LocalDateTime.of(2026, 11, 30, 22, 0, 0);

    @Mock
    private StringRedisTemplate redis;

    @Mock
    private ValueOperations<String, String> ops;

    /** 假装是 Redis：key → json */
    private final Map<String, String> store = new ConcurrentHashMap<>();

    /** 断言用的 mapper，和 CacheClient 内部保持同一套配置 */
    private final ObjectMapper json = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private CacheClient cacheClient;

    @BeforeEach
    void setUp() {
        lenient().when(redis.opsForValue()).thenReturn(ops);
        lenient().when(ops.get(anyString()))
                .thenAnswer(inv -> store.get(inv.getArgument(0, String.class)));
        lenient().doAnswer(inv -> {
            store.put(inv.getArgument(0, String.class), inv.getArgument(1, String.class));
            return null;
        }).when(ops).set(anyString(), anyString(), anyLong(), any(TimeUnit.class));
        // 逻辑过期分支要抢锁，默认让它抢到
        lenient().when(ops.setIfAbsent(anyString(), anyString(), anyLong(), any(TimeUnit.class)))
                .thenReturn(true);

        cacheClient = new CacheClient(redis);
    }

    // ==================== 序列化回归（配合 PR #54） ====================

    @Test
    @DisplayName("回源后真的写进了缓存，且 LocalDateTime 能往返（原来的 bug 就死在这）")
    void writeLogical_shouldStoreSerializableValue() throws Exception {
        AtomicInteger loads = new AtomicInteger();

        ActivityVO result = cacheClient.getWithLogicalExpire(
                KEY, ActivityVO.class, 30, TimeUnit.MINUTES, counting(loads));

        assertEquals(1, loads.get(), "第一次应该回源");
        assertEquals(START, result.getStartTime());

        String stored = store.get(KEY);
        assertNotNull(stored, "缓存没写进去 —— 序列化又崩了，检查 objectMapper 有没有注册 JavaTimeModule");

        CacheClient.CacheWrapper wrapper = json.readValue(stored, CacheClient.CacheWrapper.class);
        assertTrue(wrapper.getExpireAt() > System.currentTimeMillis(), "逻辑过期时间应该是将来的时间");

        ActivityVO cached = json.readValue(wrapper.getData(), ActivityVO.class);
        assertEquals(START, cached.getStartTime(), "时间字段没原样回来");
        assertEquals(END, cached.getEndTime());
        assertEquals("阳澄湖大闸蟹 8 只装", cached.getTitle());
    }

    @Test
    @DisplayName("未过期时直接命中缓存，不回源")
    void notExpired_shouldHitCache() {
        AtomicInteger loads = new AtomicInteger();

        cacheClient.getWithLogicalExpire(KEY, ActivityVO.class, 30, TimeUnit.MINUTES, counting(loads));
        ActivityVO second = cacheClient.getWithLogicalExpire(
                KEY, ActivityVO.class, 30, TimeUnit.MINUTES, counting(loads));

        assertEquals(1, loads.get(), "第二次不该再回源");
        assertEquals(START, second.getStartTime());
    }

    @Test
    @DisplayName("逻辑过期后只回源一次并重建缓存（#28 防击穿走的就是这条路径）")
    void logicallyExpired_shouldRebuildOnce() throws Exception {
        store.put(KEY, expiredWrapper(sample()));

        ActivityVO fresh = sample();
        fresh.setTitle("重建之后的标题");
        AtomicInteger loads = new AtomicInteger();

        ActivityVO result = cacheClient.getWithLogicalExpire(
                KEY, ActivityVO.class, 30, TimeUnit.MINUTES,
                () -> {
                    loads.incrementAndGet();
                    return fresh;
                });

        assertEquals(1, loads.get(), "抢到锁的线程回源一次就够了");
        assertEquals("重建之后的标题", result.getTitle(), "应该返回重建后的新值");

        CacheClient.CacheWrapper rebuilt = json.readValue(store.get(KEY), CacheClient.CacheWrapper.class);
        assertTrue(rebuilt.getExpireAt() > System.currentTimeMillis(), "重建后过期时间应该刷新到将来");
    }

    @Test
    @DisplayName("过期但没抢到锁：拿旧值返回，绝不回源")
    void logicallyExpired_withoutLock_shouldServeStaleValue() throws Exception {
        store.put(KEY, expiredWrapper(sample()));
        lenient().when(ops.setIfAbsent(anyString(), anyString(), anyLong(), any(TimeUnit.class)))
                .thenReturn(false);

        AtomicInteger loads = new AtomicInteger();
        ActivityVO result = cacheClient.getWithLogicalExpire(
                KEY, ActivityVO.class, 30, TimeUnit.MINUTES, counting(loads));

        assertEquals(0, loads.get(), "没抢到锁就不该碰数据库");
        assertNotNull(result);
        assertEquals(START, result.getStartTime(), "拿到的应该是缓存里的旧值");
    }

    // ==================== 防穿透（本地 feature 合入） ====================

    @Test
    @DisplayName("防穿透：命中 __EMPTY__ 直接返回 null，不回源")
    void getEmptyHit() {
        store.put("empty:key", "__EMPTY__");
        ActivityVO result = cacheClient.get("empty:key", ActivityVO.class, 10, TimeUnit.MINUTES,
                () -> { throw new AssertionError("命中空值不该回源"); });
        assertNullValue(result);
    }

    @Test
    @DisplayName("防穿透：回源为 null 时缓存 __EMPTY__，TTL 为 2 分钟")
    void getNullThenCacheEmpty() {
        ActivityVO result = cacheClient.get("empty:key2", ActivityVO.class, 10, TimeUnit.MINUTES,
                () -> null);
        assertNullValue(result);
        assertEquals("__EMPTY__", store.get("empty:key2"));
        verify(ops).set("empty:key2", "__EMPTY__", 2, TimeUnit.MINUTES);
    }

    // ==================== 防雪崩：TTL 抖动（本地 feature 合入） ====================

    @Test
    @DisplayName("防雪崩：写入 TTL 在 base±10% 范围内抖动，不是固定值")
    void ttlHasJitter() {
        long base = 600_000L; // 10 分钟

        // 采样多次，验证既有正抖动也有负抖动（概率上 30 次几乎必然覆盖两侧）
        for (int i = 0; i < 30; i++) {
            cacheClient.set("ttl:k" + i, sample(), 10, TimeUnit.MINUTES);
        }
        org.mockito.ArgumentCaptor<Long> ttlCap = org.mockito.ArgumentCaptor.forClass(Long.class);
        verify(ops, times(30)).set(org.mockito.ArgumentMatchers.startsWith("ttl:k"),
                anyString(), ttlCap.capture(), eq(TimeUnit.MILLISECONDS));
        boolean sawPlus = false;
        boolean sawMinus = false;
        for (long ttl : ttlCap.getAllValues()) {
            assertTrue(ttl >= base - base * 0.1 && ttl <= base + base * 0.1 + 1,
                    "TTL 超出 ±10% 抖动范围: " + ttl);
            if (ttl > base) sawPlus = true;
            if (ttl < base) sawMinus = true;
        }
        assertTrue(sawPlus && sawMinus,
                "应同时出现正负抖动，实际 sawPlus=" + sawPlus + " sawMinus=" + sawMinus);
    }

    // ==================== 工具 ====================

    private static void assertNullValue(Object o) {
        org.junit.jupiter.api.Assertions.assertNull(o);
    }

    private Supplier<ActivityVO> counting(AtomicInteger counter) {
        return () -> {
            counter.incrementAndGet();
            return sample();
        };
    }

    private ActivityVO sample() {
        ActivityVO vo = new ActivityVO();
        vo.setId(999001L);
        vo.setTitle("阳澄湖大闸蟹 8 只装");
        vo.setStatus(1);
        vo.setStartTime(START);
        vo.setEndTime(END);
        return vo;
    }

    /** 造一份"逻辑上已经过期"的缓存，模拟热点 key 到期的那一刻 */
    private String expiredWrapper(ActivityVO vo) throws Exception {
        CacheClient.CacheWrapper wrapper = new CacheClient.CacheWrapper();
        wrapper.setData(json.writeValueAsString(vo));
        wrapper.setExpireAt(System.currentTimeMillis() - 60_000);
        return json.writeValueAsString(wrapper);
    }
}
