package com.groupbuy.redis;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Redis 工具箱自检：验证"200 并发 1000 库存不超卖"（正式压测在 #27，那里出报告证据）。
 *
 * <p>默认禁用（需要本地 Redis 7 起在 127.0.0.1:6379）。要跑的时候删掉 {@code @Disabled} 或
 * 直接在 IDE 里右键 Run。</p>
 */
@Disabled("需要本地 Redis，CI 里跳过")
@SpringBootTest
class RedisToolboxTest {

    @Autowired
    private StockRedisHelper stockRedisHelper;

    @Autowired
    private RateLimiter rateLimiter;

    @Test
    void concurrentDeduct_shouldNotOversell() throws Exception {
        long activityId = 999L;
        int total = 1000;
        int threads = 200;

        stockRedisHelper.preload(activityId, total);

        ExecutorService pool = Executors.newFixedThreadPool(32);
        CountDownLatch latch = new CountDownLatch(threads);
        AtomicInteger success = new AtomicInteger();

        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                try {
                    if (stockRedisHelper.deduct(activityId, 1) == 1) {
                        success.incrementAndGet();
                    }
                } finally {
                    latch.countDown();
                }
            });
        }
        latch.await();
        pool.shutdown();

        // 断言：成功次数 == 库存总量，且剩余为 0
        System.out.println("成功下单次数 = " + success.get());
        System.out.println("剩余库存 = " + stockRedisHelper.remain(activityId));
        if (success.get() != total || stockRedisHelper.remain(activityId) != 0) {
            throw new AssertionError("出现超卖或漏扣！");
        }
    }

    @Test
    void rateLimit_shouldRejectOverflow() {
        String key = "test:user:1";
        int passed = 0;
        for (int i = 0; i < 50; i++) {
            if (rateLimiter.tryAcquire(key, 10, 2)) {
                passed++;
            }
        }
        System.out.println("50 次请求放行 = " + passed + "（容量 10，应接近 10 而不是 50）");
        if (passed >= 50) {
            throw new AssertionError("限流没生效");
        }
    }
}
