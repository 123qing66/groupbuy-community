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
 * 库存 Redis 操作：预热 / 原子预扣 / 回滚。
 *
 * <p><b>为什么用 Lua：</b>{@code GET} 判断 + {@code DECRBY} 扣减两步之间如果有并发插入，
 * 就会出现超卖。Lua 脚本在 Redis 里是单线程原子执行的，所以能兜住并发。</p>
 *
 * <p>DB 那边还有一层 CAS 兜底（{@code UPDATE ... SET stock = stock - n WHERE stock >= n}），
 * 双保险，见 Issue #16。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StockRedisHelper {

    /** 库存 key 前缀 */
    private static final String STOCK_KEY = "groupbuy:stock:activity:";

    private final StringRedisTemplate stringRedisTemplate;

    private final DefaultRedisScript<Long> deductScript = new DefaultRedisScript<>();
    private final DefaultRedisScript<Long> rollbackScript = new DefaultRedisScript<>();

    /** 初始化时把 Lua 脚本位置和返回类型绑定好 */
    @PostConstruct
    public void init() {
        deductScript.setLocation(new ClassPathResource("lua/stock_deduct.lua"));
        deductScript.setResultType(Long.class);
        rollbackScript.setLocation(new ClassPathResource("lua/stock_rollback.lua"));
        rollbackScript.setResultType(Long.class);
    }

    public static String stockKey(Long activityId) {
        return STOCK_KEY + activityId;
    }

    /** 活动上线时预热库存（Day15 的 bonus #34 会做成异步落库） */
    public void preload(Long activityId, long stock) {
        stringRedisTemplate.opsForValue().set(stockKey(activityId), String.valueOf(stock));
        log.info("[库存预热] activityId={}, stock={}", activityId, stock);
    }

    /**
     * 原子预扣库存。
     *
     * @return 1 成功 / 0 库存不足 / -1 未预热
     */
    public long deduct(Long activityId, int num) {
        Long r = stringRedisTemplate.execute(
                deductScript,
                Collections.singletonList(stockKey(activityId)),
                String.valueOf(num));
        long result = r == null ? -1 : r;
        if (result == 1) {
            log.debug("[库存预扣成功] activityId={}, num={}", activityId, num);
        } else {
            log.warn("[库存预扣失败] activityId={}, num={}, result={}", activityId, num, result);
        }
        return result;
    }

    /** 回滚预扣 */
    public long rollback(Long activityId, int num) {
        Long r = stringRedisTemplate.execute(
                rollbackScript,
                Collections.singletonList(stockKey(activityId)),
                String.valueOf(num));
        return r == null ? 0 : r;
    }

    /** 剩余库存（用于展示和自检，不走 Lua） */
    public long remain(Long activityId) {
        String v = stringRedisTemplate.opsForValue().get(stockKey(activityId));
        return v == null ? -1L : Long.parseLong(v);
    }
}
