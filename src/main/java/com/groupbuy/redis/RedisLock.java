package com.groupbuy.redis;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * 简易分布式锁（成团判定 #20、定时任务防重入用）。
 *
 * <p>特性：SET NX EX 原子加锁；value 用 UUID 标识持有者；释放时走 Lua 比对 value，
 * 防止"锁过期后误删别人的锁"。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RedisLock {

    private static final String LOCK_PREFIX = "groupbuy:lock:";
    private static final ThreadLocal<String> HOLDER = new ThreadLocal<>();

    private final StringRedisTemplate stringRedisTemplate;

    private final DefaultRedisScript<Long> releaseScript = new DefaultRedisScript<>();

    /** 初始化时把 Lua 脚本位置和返回类型绑定好 */
    @PostConstruct
    public void init() {
        releaseScript.setLocation(new ClassPathResource("lua/lock_release.lua"));
        releaseScript.setResultType(Long.class);
    }

    /** 加锁，返回是否成功 */
    public boolean tryLock(String bizKey, long ttl, TimeUnit unit) {
        String value = UUID.randomUUID().toString();
        Boolean ok = stringRedisTemplate.opsForValue()
                .setIfAbsent(LOCK_PREFIX + bizKey, value, ttl, unit);
        if (Boolean.TRUE.equals(ok)) {
            HOLDER.set(value);
            return true;
        }
        return false;
    }

    /** 释放锁（只释放自己持有的） */
    public void unlock(String bizKey) {
        String value = HOLDER.get();
        if (value == null) {
            return;
        }
        try {
            stringRedisTemplate.execute(releaseScript,
                    Collections.singletonList(LOCK_PREFIX + bizKey), value);
        } finally {
            HOLDER.remove();
        }
    }

    /** 带锁执行 */
    public void runWithLock(String bizKey, long ttl, TimeUnit unit, Runnable task) {
        if (!tryLock(bizKey, ttl, unit)) {
            log.warn("[获取锁失败，跳过执行] key={}", bizKey);
            return;
        }
        try {
            task.run();
        } finally {
            unlock(bizKey);
        }
    }
}
