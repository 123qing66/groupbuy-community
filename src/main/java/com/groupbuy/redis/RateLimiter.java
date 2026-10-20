package com.groupbuy.redis;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.util.Collections;

/**
 * 令牌桶限流。超限时调用方抛 429（{@code ErrorCode.TOO_MANY_REQUESTS}）。
 *
 * <p>用法示例：</p>
 * <pre>{@code
 * if (!rateLimiter.tryAcquire("seckill:user:" + userId, 10, 5)) {
 *     throw new BizException(ErrorCode.TOO_MANY_REQUESTS);
 * }
 * }</pre>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RateLimiter {

    private static final String RL_KEY = "groupbuy:rl:";

    private final StringRedisTemplate stringRedisTemplate;

    private final DefaultRedisScript<Long> limitScript = new DefaultRedisScript<>();

    /** 初始化时把 Lua 脚本位置和返回类型绑定好 */
    @PostConstruct
    public void init() {
        limitScript.setLocation(new ClassPathResource("lua/rate_limit.lua"));
        limitScript.setResultType(Long.class);
    }

    /**
     * @param bizKey   业务标识，如 "seckill:user:12"
     * @param capacity 桶容量（瞬时最多放行多少请求）
     * @param rate     每秒补充的令牌数
     * @return true 放行 / false 拒绝
     */
    public boolean tryAcquire(String bizKey, int capacity, int rate) {
        Long r = stringRedisTemplate.execute(
                limitScript,
                Collections.singletonList(RL_KEY + bizKey),
                String.valueOf(capacity),
                String.valueOf(rate),
                "1",
                String.valueOf(System.currentTimeMillis()));
        boolean allowed = r != null && r == 1L;
        if (!allowed) {
            log.warn("[限流拒绝] key={}, capacity={}, rate={}", bizKey, capacity, rate);
        }
        return allowed;
    }
}
