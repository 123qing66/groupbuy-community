package com.groupbuy.redis;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;
import java.util.function.Supplier;

/**
 * 缓存工具：Cache-Aside + 三板斧。
 *
 * <ul>
 *   <li><b>防穿透</b>：查不到就往 Redis 里塞一个短 TTL 的空值（{@link #EMPTY}），
 *       避免恶意/不存在的 id 每次都打到 DB</li>
 *   <li><b>防击穿</b>：热点 key 用逻辑过期（把过期时间存进 value 里），
 *       过期后只让拿到互斥锁的那一个线程回源重建，其余线程先返回旧数据</li>
 *   <li><b>防雪崩</b>：TTL 加随机抖动（±10%），避免同一时刻大批 key 一起失效</li>
 * </ul>
 *
 * <p>用法（活动详情 #18 会这么用）：</p>
 * <pre>{@code
 * ActivityVO vo = cacheClient.getWithLogicalExpire(
 *         "activity:" + id, ActivityVO.class, 30, TimeUnit.MINUTES,
 *         () -> activityMapper.selectDetail(id));
 * }</pre>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CacheClient {

    /** 空值占位：区分"确定没有"和"没缓存" */
    private static final String EMPTY = "__EMPTY__";

    /** 逻辑过期的互斥锁后缀 */
    private static final String LOCK_SUFFIX = ":rebuild:lock";

    /** TTL 抖动比例：±10% */
    private static final double JITTER = 0.1;
    private static final long   JITTER_MAX_MS = 60_000L;

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    // ==================== 基础 Cache-Aside（带空值缓存） ====================

    /**
     * 查缓存，没有就回源，并把结果写回缓存；回源为 null 时缓存空值。
     *
     * @param ttl 基础过期时间，实际写入会带随机抖动
     */
    public <T> T get(String key, Class<T> type, long ttl, TimeUnit unit, Supplier<T> loader) {
        String json = stringRedisTemplate.opsForValue().get(key);

        if (json != null) {
            if (EMPTY.equals(json)) {
                return null;                       // 命中空值：不回源
            }
            return read(json, type);
        }

        T data = loader.get();                     // 回源
        if (data == null) {
            // 防穿透：空值也缓存，TTL 给短一点（2 分钟）
            stringRedisTemplate.opsForValue().set(key, EMPTY, 2, TimeUnit.MINUTES);
            return null;
        }
        set(key, data, ttl, unit);
        return data;
    }

    /** 写缓存，TTL 带抖动 */
    public void set(String key, Object value, long ttl, TimeUnit unit) {
        try {
            String json = objectMapper.writeValueAsString(value);
            long millis = unit.toMillis(ttl);
            stringRedisTemplate.opsForValue()
                    .set(key, json, millis + jitter(millis), TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            log.error("[缓存写入失败] key={}", key, e);
        }
    }

    public void delete(String key) {
        stringRedisTemplate.delete(key);
    }

    /** 回源计数用：判断 key 是否还存在（压测②验证"DB 回源 ≤1 次"会用到） */
    public boolean exists(String key) {
        return Boolean.TRUE.equals(stringRedisTemplate.hasKey(key));
    }

    // ==================== 逻辑过期（防击穿） ====================

    /**
     * 逻辑过期查询：过期后只有一个线程回源重建，其余线程拿旧值先返回，DB 不会被打爆。
     */
    public <T> T getWithLogicalExpire(String key, Class<T> type,
                                      long ttl, TimeUnit unit, Supplier<T> loader) {
        String json = stringRedisTemplate.opsForValue().get(key);

        // 1. 缓存里根本没有 → 同步回源，写一份逻辑过期包装
        if (json == null) {
            T data = loader.get();
            if (data == null) {
                stringRedisTemplate.opsForValue().set(key, EMPTY, 2, TimeUnit.MINUTES);
                return null;
            }
            writeLogical(key, data, ttl, unit);
            return data;
        }

        if (EMPTY.equals(json)) {
            return null;
        }

        // 2. 有缓存：看逻辑过期时间
        try {
            CacheWrapper wrapper = objectMapper.readValue(json, CacheWrapper.class);
            if (wrapper.getExpireAt() > System.currentTimeMillis()) {
                // 未过期，直接返回
                return read(wrapper.getData(), type);
            }

            // 3. 已过期：抢锁重建
            String lockKey = key + LOCK_SUFFIX;
            Boolean locked = stringRedisTemplate.opsForValue()
                    .setIfAbsent(lockKey, "1", 10, TimeUnit.SECONDS);
            if (Boolean.TRUE.equals(locked)) {
                try {
                    T fresh = loader.get();
                    if (fresh != null) {
                        writeLogical(key, fresh, ttl, unit);
                        return fresh;
                    }
                } finally {
                    stringRedisTemplate.delete(lockKey);
                }
            }
            // 没抢到锁：返回旧数据，绝不回源（这就是防击穿的关键）
            return read(wrapper.getData(), type);
        } catch (Exception e) {
            log.error("[逻辑过期缓存解析失败] key={}", key, e);
            return null;
        }
    }

    private void writeLogical(String key, Object data, long ttl, TimeUnit unit) {
        try {
            CacheWrapper wrapper = new CacheWrapper();
            wrapper.setData(objectMapper.writeValueAsString(data));
            wrapper.setExpireAt(System.currentTimeMillis() + unit.toMillis(ttl));
            String json = objectMapper.writeValueAsString(wrapper);
            // 物理 TTL 给逻辑 TTL 的两倍，保证过期后旧数据还在，能兜住并发
            stringRedisTemplate.opsForValue()
                    .set(key, json, unit.toMillis(ttl) * 2 + jitter(unit.toMillis(ttl)),
                            TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            log.error("[逻辑过期写入失败] key={}", key, e);
        }
    }

    // ==================== 工具 ====================

    /** TTL 抖动：基础值 ±10%，最多抖 60 秒 */
    private long jitter(long baseMillis) {
        long j = (long) (baseMillis * JITTER);
        if (j > JITTER_MAX_MS) {
            j = JITTER_MAX_MS;
        }
        long delta = (long) ((Math.random() * 2 - 1) * j);
        return delta;
    }

    private <T> T read(String json, Class<T> type) {
        try {
            return objectMapper.readValue(json, type);
        } catch (Exception e) {
            log.error("[缓存反序列化失败] type={}", type.getSimpleName(), e);
            return null;
        }
    }

    /** 逻辑过期包装：data 是真正数据的 json 串，expireAt 是逻辑过期时间戳 */
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class CacheWrapper {
        private String data;
        private long expireAt;
    }

    /** 需要复杂泛型时用这个重载 */
    public <T> T readValue(String json, TypeReference<T> ref) {
        try {
            return objectMapper.readValue(json, ref);
        } catch (Exception e) {
            log.error("[缓存反序列化失败]", e);
            return null;
        }
    }

    /** 让出 CPU 一下，防止自旋抢锁时空转 */
    @SuppressWarnings("unused")
    private static void park(long nanos) {
        LockSupport.parkNanos(nanos);
    }
}
