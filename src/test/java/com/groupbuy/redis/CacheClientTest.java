package com.groupbuy.redis;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 缓存三板斧单测（A 负责，#4 活动详情的核心考核点）。
 *
 * <p>纯 Mockito 模拟 {@link StringRedisTemplate}，不连 Redis，CI 可跑。
 * 验证：① 空值缓存防穿透 ② 逻辑过期互斥锁防击穿 ③ TTL 抖动防雪崩。</p>
 */
@ExtendWith(MockitoExtension.class)
class CacheClientTest {

    @Mock private StringRedisTemplate redisTemplate;
    @Mock private ValueOperations<String, String> valueOperations;

    private final ObjectMapper mapper = new ObjectMapper();

    /** 让 opsForValue() 返回我们的 mock */
    private void stubOps() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    /** 造一个逻辑过期的缓存 JSON：data 是内层对象 json，expireAt 是逻辑过期时间戳 */
    private String wrapperJson(String dataJson, long expireAt) throws Exception {
        CacheClient.CacheWrapper w = new CacheClient.CacheWrapper();
        w.setData(dataJson);
        w.setExpireAt(expireAt);
        return mapper.writeValueAsString(w);
    }

    private static class Demo {
        public String name;
        public Demo() { }
        Demo(String name) { this.name = name; }
    }

    // ==================== 基础 Cache-Aside + 空值缓存（防穿透） ====================

    @Test
    @DisplayName("防穿透：命中 __EMPTY__ 直接返回 null，不回源")
    void getEmptyHit() {
        stubOps();
        when(valueOperations.get("k")).thenReturn("__EMPTY__");

        Demo result = new CacheClient(redisTemplate)
                .get("k", Demo.class, 10, TimeUnit.MINUTES, () -> new Demo("回源了"));

        assertNull(result);
    }

    @Test
    @DisplayName("防穿透：回源为 null 时缓存 __EMPTY__，TTL 为 2 分钟")
    void getNullThenCacheEmpty() {
        stubOps();
        when(valueOperations.get("k")).thenReturn(null);
        CacheClient cache = new CacheClient(redisTemplate);

        Demo result = cache.get("k", Demo.class, 10, TimeUnit.MINUTES, () -> null);

        assertNull(result);
        verify(valueOperations).set("k", "__EMPTY__", 2, TimeUnit.MINUTES);
    }

    @Test
    @DisplayName("基础回源：缓存缺失时回源并写回，返回数据")
    void getThenWriteBack() {
        stubOps();
        when(valueOperations.get("k")).thenReturn(null);
        CacheClient cache = new CacheClient(redisTemplate);

        Demo result = cache.get("k", Demo.class, 10, TimeUnit.MINUTES, () -> new Demo("fresh"));

        assertEquals("fresh", result.name);
        verify(valueOperations).set(eq("k"), anyString(), anyLong(), eq(TimeUnit.MILLISECONDS));
    }

    // ==================== 逻辑过期（防击穿） ====================

    @Test
    @DisplayName("逻辑过期未到：直接返回缓存值，不回源、不抢锁")
    void logicalNotExpired() throws Exception {
        stubOps();
        String json = wrapperJson("{\"name\":\"cached\"}", System.currentTimeMillis() + 600_000);
        when(valueOperations.get("k")).thenReturn(json);
        CacheClient cache = new CacheClient(redisTemplate);

        Demo result = cache.getWithLogicalExpire(
                "k", Demo.class, 30, TimeUnit.MINUTES, () -> new Demo("回源了"));

        assertEquals("cached", result.name);
        verify(valueOperations, never()).setIfAbsent(anyString(), anyString(), anyLong(), any(TimeUnit.class));
    }

    @Test
    @DisplayName("逻辑过期已到 + 抢到锁：回源重建、写回并释放锁")
    void logicalExpiredRebuild() throws Exception {
        stubOps();
        String json = wrapperJson("{\"name\":\"old\"}", System.currentTimeMillis() - 1000);
        when(valueOperations.get("k")).thenReturn(json);
        when(valueOperations.setIfAbsent(eq("k:rebuild:lock"), eq("1"), anyLong(), eq(TimeUnit.SECONDS)))
                .thenReturn(true);
        CacheClient cache = new CacheClient(redisTemplate);

        Demo result = cache.getWithLogicalExpire(
                "k", Demo.class, 30, TimeUnit.MINUTES, () -> new Demo("fresh"));

        assertEquals("fresh", result.name);
        verify(redisTemplate).delete("k:rebuild:lock");
    }

    @Test
    @DisplayName("逻辑过期已到 + 没抢到锁：返回旧值兜底，绝不回源（防击穿关键）")
    void logicalExpiredNoLockReturnOld() throws Exception {
        stubOps();
        String json = wrapperJson("{\"name\":\"old\"}", System.currentTimeMillis() - 1000);
        when(valueOperations.get("k")).thenReturn(json);
        when(valueOperations.setIfAbsent(eq("k:rebuild:lock"), eq("1"), anyLong(), eq(TimeUnit.SECONDS)))
                .thenReturn(false);
        CacheClient cache = new CacheClient(redisTemplate);

        Demo result = cache.getWithLogicalExpire(
                "k", Demo.class, 30, TimeUnit.MINUTES, () -> new Demo("不该回源"));

        assertEquals("old", result.name);
        verify(redisTemplate, never()).delete(anyString());
    }

    @Test
    @DisplayName("逻辑过期路径也防穿透：缓存缺省时回源为 null → 缓存 __EMPTY__")
    void logicalMissNullCachesEmpty() {
        stubOps();
        when(valueOperations.get("k")).thenReturn(null);
        CacheClient cache = new CacheClient(redisTemplate);

        Demo result = cache.getWithLogicalExpire(
                "k", Demo.class, 30, TimeUnit.MINUTES, () -> null);

        assertNull(result);
        verify(valueOperations).set("k", "__EMPTY__", 2, TimeUnit.MINUTES);
    }

    // ==================== TTL 抖动（防雪崩） ====================

    @Test
    @DisplayName("防雪崩：写入 TTL 在 base±10% 范围内抖动，不是固定值")
    void ttlHasJitter() {
        stubOps();
        CacheClient cache = new CacheClient(redisTemplate);
        long base = 600_000L; // 10 分钟

        // 采样多次，验证既有正抖动也有负抖动（概率上 30 次几乎必然覆盖两侧）
        boolean sawPlus = false;
        boolean sawMinus = false;
        for (int i = 0; i < 30; i++) {
            cache.set("k" + i, new Demo("x"), 10, TimeUnit.MINUTES);
        }
        org.mockito.ArgumentCaptor<Long> ttlCap = org.mockito.ArgumentCaptor.forClass(Long.class);
        verify(valueOperations, times(30)).set(anyString(), anyString(), ttlCap.capture(), eq(TimeUnit.MILLISECONDS));
        for (long ttl : ttlCap.getAllValues()) {
            assertTrue(ttl >= base - base * 0.1 && ttl <= base + base * 0.1 + 1,
                    "TTL 超出 ±10% 抖动范围: " + ttl);
            if (ttl > base) sawPlus = true;
            if (ttl < base) sawMinus = true;
        }
        assertTrue(sawPlus && sawMinus, "应同时出现正负抖动，实际 sawPlus=" + sawPlus + " sawMinus=" + sawMinus);
    }
}
