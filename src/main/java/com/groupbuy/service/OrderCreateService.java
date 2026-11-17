package com.groupbuy.service;

import com.groupbuy.common.ActivityStatus;
import com.groupbuy.common.BizException;
import com.groupbuy.common.ErrorCode;
import com.groupbuy.dto.OrderCreateRequest;
import com.groupbuy.dto.OrderCreateResponse;
import com.groupbuy.entity.Activity;
import com.groupbuy.entity.Goods;
import com.groupbuy.entity.OrderCreateOrder;
import com.groupbuy.mapper.ActivityMapper;
import com.groupbuy.mapper.GoodsMapper;
import com.groupbuy.mapper.OrderCreateMapper;
import com.groupbuy.mapper.StockMapper;
import com.groupbuy.redis.RateLimiter;
import com.groupbuy.redis.StockRedisHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

/**
 * 参团下单（B 负责，接口 #7 / Issue #16）。
 *
 * <p>固定五步，顺序不能换：</p>
 * <ol>
 *   <li>幂等前置：Redis 原子占位，同用户同活动只放行一次下单</li>
 *   <li>令牌桶限流（429）—— 挡住突发流量，不让它打到 Redis/DB</li>
 *   <li>时间窗 + 状态 + 限购校验 —— 业务规则在花钱扣库存之前做完</li>
 *   <li>Redis Lua 原子预扣 —— 挡住 99% 的并发，杜绝超卖</li>
 *   <li>DB 事务落库：INSERT 订单（uk_user_activity 兜幂等）+ CAS 扣库存</li>
 * </ol>
 *
 * <p><b>幂等两层</b>：Redis 键挡住狂点/网络重试的重复请求（直接返回首次结果）；
 * DB 唯一索引 uk_user_activity 兜底 Redis 键过期后的重复下单。
 * 两层都命中时只生成一单，且绝不重复扣库存。</p>
 *
 * <p><b>回滚纪律</b>：Redis 预扣不在 DB 事务里，第 5 步任何失败都必须手动 rollback，
 * 否则库存会被"扣了却没订单"慢慢吃掉。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderCreateService {

    /** 支付超时：15 分钟，超时由 C 的定时任务取消订单并回滚库存 */
    private static final int PAY_EXPIRE_MINUTES = 15;

    /** 令牌桶：瞬时容量 10，每秒补 5 个（与 docs/api.md 示例一致） */
    private static final int RATE_CAPACITY = 10;
    private static final int RATE_PER_SECOND = 5;

    /** 下单幂等键：userId + activityId 唯一，TTL 覆盖整个支付窗口 */
    private static final String IDEM_PREFIX = "groupbuy:order:idem:";
    private static final int IDEM_TTL_MINUTES = 30;

    /** 幂等未命中但正在处理时，最多回查 DB 1 秒（10 次 × 100ms） */
    private static final int IDEM_POLL_TIMES = 10;
    private static final long IDEM_POLL_INTERVAL_MS = 100L;

    private static final DateTimeFormatter ORDER_NO_FMT =
            DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final ActivityMapper activityMapper;
    private final GoodsMapper goodsMapper;
    private final StockMapper stockMapper;
    private final OrderCreateMapper orderCreateMapper;
    private final StockRedisHelper stockRedisHelper;
    private final RateLimiter rateLimiter;
    private final StringRedisTemplate stringRedisTemplate;
    private final PlatformTransactionManager transactionManager;

    public OrderCreateResponse create(OrderCreateRequest req, Long userId) {
        // ---------- 0. 参数校验 ----------
        if (req == null || req.getActivityId() == null || req.getGoodsId() == null
                || req.getNum() == null || req.getNum() < 1) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        Long activityId = req.getActivityId();
        Long goodsId = req.getGoodsId();
        int num = req.getNum();

        // ---------- 1. 幂等前置：SETNX 原子占位，抢不到就回查首次结果 ----------
        String idemKey = IDEM_PREFIX + userId + ":" + activityId;
        Boolean first = stringRedisTemplate.opsForValue()
                .setIfAbsent(idemKey, "PROCESSING", IDEM_TTL_MINUTES, TimeUnit.MINUTES);
        if (!Boolean.TRUE.equals(first)) {
            OrderCreateOrder exist = waitAndFind(userId, activityId);
            if (exist != null) {
                log.info("[幂等命中，返回首次结果] userId={}, activityId={}, orderNo={}",
                        userId, activityId, exist.getOrderNo());
                return toResponse(exist);
            }
            // 占位键在但订单没查到（上次请求还没落库就挂了）→ 阻止本次，让重试走正常流程
            throw new BizException(ErrorCode.ORDER_ALREADY_EXISTS);
        }

        boolean deducted = false;   // 标记 Redis 预扣是否已发生，决定 catch 里要不要回滚
        try {
            // ---------- 2. 限流：超限直接 429，绝不进入扣库存 ----------
            if (!rateLimiter.tryAcquire("order:" + userId, RATE_CAPACITY, RATE_PER_SECOND)) {
                throw new BizException(ErrorCode.TOO_MANY_REQUESTS);
            }

            // ---------- 3. 活动时间窗 + 状态 + 商品 + 限购 ----------
            Activity activity = activityMapper.selectById(activityId);
            if (activity == null) {
                throw new BizException(ErrorCode.ACTIVITY_NOT_FOUND);
            }
            LocalDateTime now = LocalDateTime.now();
            if (activity.getStatus() == null
                    || activity.getStatus() != ActivityStatus.ONGOING.getCode()
                    || now.isBefore(activity.getStartTime())
                    || now.isAfter(activity.getEndTime())) {
                throw new BizException(ErrorCode.ACTIVITY_NOT_ONGOING);
            }

            Goods goods = findGoods(activityId, goodsId);
            if (goods == null) {
                throw new BizException(ErrorCode.PARAM_ERROR, "商品不属于该活动或已下架");
            }

            int buyLimit = Objects.requireNonNullElse(activity.getBuyLimit(), 1);
            if (num > buyLimit) {
                throw new BizException(ErrorCode.PURCHASE_LIMIT_EXCEEDED);
            }

            // ---------- 4. Redis Lua 原子预扣 ----------
            // 返回 1 成功 / 0 库存不足 / -1 未预热，非 1 一律按库存不足快速失败。
            long deductedResult = stockRedisHelper.deduct(activityId, num);
            if (deductedResult != 1) {
                throw new BizException(ErrorCode.STOCK_NOT_ENOUGH);
            }
            deducted = true;

            // ---------- 5. DB 事务落库（CAS + 唯一键）----------
            OrderCreateResponse resp = persistOrder(userId, activity, goods, num);
            // 成功：把订单号写回幂等键，后续重复请求直接返回这一单
            stringRedisTemplate.opsForValue()
                    .set(idemKey, resp.getOrderNo(), IDEM_TTL_MINUTES, TimeUnit.MINUTES);
            return resp;

        } catch (BizException e) {
            // DB 唯一键冲突：说明 Redis 键过期后又被重复提交，回查并以首次结果返回
            if (e.getCode() == ErrorCode.ORDER_ALREADY_EXISTS.getCode()) {
                OrderCreateOrder exist = orderCreateMapper.selectByUserAndActivity(userId, activityId);
                if (exist != null) {
                    if (deducted) {
                        stockRedisHelper.rollback(activityId, num);  // 本次预扣没落库，还回去
                    }
                    log.info("[唯一索引兜幂等，返回首次结果] userId={}, activityId={}, orderNo={}",
                            userId, activityId, exist.getOrderNo());
                    return toResponse(exist);
                }
            }
            if (deducted) {
                stockRedisHelper.rollback(activityId, num);
            }
            stringRedisTemplate.delete(idemKey);   // 失败要放行重试，否则用户被卡 30 分钟
            throw e;
        } catch (Exception e) {
            if (deducted) {
                stockRedisHelper.rollback(activityId, num);
            }
            stringRedisTemplate.delete(idemKey);
            log.error("[下单异常] userId={}, activityId={}, goodsId={}, num={}",
                    userId, activityId, goodsId, num, e);
            throw new BizException(ErrorCode.SYSTEM_ERROR);
        }
    }

    /** DB 部分单独一个事务：INSERT 订单 + CAS 扣库存，同成同败 */
    private OrderCreateResponse persistOrder(Long userId, Activity activity, Goods goods, int num) {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        return tx.execute(status -> {
            OrderCreateOrder order = new OrderCreateOrder();
            order.setOrderNo(generateOrderNo());
            order.setUserId(userId);
            order.setActivityId(activity.getId());
            order.setGoodsId(goods.getId());
            order.setNum(num);
            order.setPayAmount(goods.getGroupPrice().multiply(BigDecimal.valueOf(num)));
            order.setStatus(0);                                   // 待支付
            order.setExpireTime(LocalDateTime.now().plusMinutes(PAY_EXPIRE_MINUTES));

            try {
                orderCreateMapper.insertOrder(order);
            } catch (DuplicateKeyException e) {
                // 撞 uk_user_activity：Redis 幂等键失效后的最后一道防线
                log.warn("[重复下单被唯一索引拦截] userId={}, activityId={}", userId, activity.getId());
                status.setRollbackOnly();
                throw new BizException(ErrorCode.ORDER_ALREADY_EXISTS);
            }

            // 最后一道防线：available >= num 由数据库原子判断，0 行更新说明真没库存了
            int rows = stockMapper.casDeduct(goods.getId(), num);
            if (rows != 1) {
                log.warn("[DB CAS 扣减失败] goodsId={}, num={}", goods.getId(), num);
                status.setRollbackOnly();
                throw new BizException(ErrorCode.STOCK_NOT_ENOUGH);
            }

            OrderCreateResponse resp = new OrderCreateResponse();
            resp.setOrderNo(order.getOrderNo());
            resp.setPayAmount(order.getPayAmount());
            resp.setExpireTime(order.getExpireTime());
            log.info("[下单成功] orderNo={}, userId={}, activityId={}, num={}",
                    order.getOrderNo(), userId, activity.getId(), num);
            return resp;
        });
    }

    /** 幂等未命中时短暂轮询：首次请求可能还在写库，等它一下再回查 */
    private OrderCreateOrder waitAndFind(Long userId, Long activityId) {
        for (int i = 0; i < IDEM_POLL_TIMES; i++) {
            OrderCreateOrder exist = orderCreateMapper.selectByUserAndActivity(userId, activityId);
            if (exist != null) {
                return exist;
            }
            try {
                Thread.sleep(IDEM_POLL_INTERVAL_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        return null;
    }

    private OrderCreateResponse toResponse(OrderCreateOrder order) {
        OrderCreateResponse resp = new OrderCreateResponse();
        resp.setOrderNo(order.getOrderNo());
        resp.setPayAmount(order.getPayAmount());
        resp.setExpireTime(order.getExpireTime());
        return resp;
    }

    /** 从活动下的商品里找本次下单的商品（必须是上架的） */
    private Goods findGoods(Long activityId, Long goodsId) {
        List<Goods> goodsList = goodsMapper.selectByActivityId(activityId);
        if (goodsList == null) {
            return null;
        }
        return goodsList.stream()
                .filter(g -> goodsId.equals(g.getId()) && Integer.valueOf(1).equals(g.getStatus()))
                .findFirst()
                .orElse(null);
    }

    /** 订单号：GB + 秒级时间 + 6 位随机数，全局唯一且可按时间排序 */
    private String generateOrderNo() {
        return "GB" + LocalDateTime.now().format(ORDER_NO_FMT)
                + String.format("%06d", ThreadLocalRandom.current().nextInt(1_000_000));
    }
}
