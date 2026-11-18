package com.groupbuy.service;

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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 下单主链路单元测试（B 的接口 #7，D 在 #26 里补测试）。
 *
 * <p>这个 Service 是整个项目并发正确性的核心，坏一个分支就是超卖或者
 * "扣了库存没订单"。所以用例按<b>五步顺序</b>逐个覆盖，并额外断言
 * <b>回滚纪律</b>：只要 Redis 预扣发生过、后面任何一步失败，必须调 rollback。</p>
 *
 * <p>纯 Mockito：Redis、限流、事务管理器全部打桩，只有业务判断是真的。</p>
 */
@ExtendWith(MockitoExtension.class)
class OrderCreateServiceTest {

    private static final Long USER_ID = 1L;
    private static final Long ACTIVITY_ID = 7L;
    private static final Long GOODS_ID = 70L;
    private static final String IDEM_KEY = "groupbuy:order:idem:1:7";

    @Mock
    private ActivityMapper activityMapper;
    @Mock
    private GoodsMapper goodsMapper;
    @Mock
    private StockMapper stockMapper;
    @Mock
    private OrderCreateMapper orderCreateMapper;
    @Mock
    private StockRedisHelper stockRedisHelper;
    @Mock
    private RateLimiter rateLimiter;
    @Mock
    private StringRedisTemplate stringRedisTemplate;
    @Mock
    private ValueOperations<String, String> valueOps;
    @Mock
    private PlatformTransactionManager transactionManager;
    @Mock
    private TransactionStatus transactionStatus;

    @InjectMocks
    private OrderCreateService orderCreateService;

    @BeforeEach
    void setUp() {
        // 这两个桩几乎每个用例都要用，但不是每个用例都走到，用 lenient 避免严格模式误报
        lenient().when(stringRedisTemplate.opsForValue()).thenReturn(valueOps);
        lenient().when(transactionManager.getTransaction(any())).thenReturn(transactionStatus);
    }

    // ==================== 第 0 步：参数校验 ====================

    @Test
    @DisplayName("参数校验：请求体为 null 直接 400，连幂等键都不占")
    void create_nullRequest_shouldThrowParamError() {
        BizException e = assertThrows(BizException.class,
                () -> orderCreateService.create(null, USER_ID));

        assertEquals(ErrorCode.PARAM_ERROR.getCode(), e.getCode());
        verify(stringRedisTemplate, never()).opsForValue();
    }

    @Test
    @DisplayName("参数校验：购买数量为 0 抛 400，不碰 Redis 也不碰库")
    void create_zeroNum_shouldThrowParamError() {
        OrderCreateRequest req = buildRequest(0);

        BizException e = assertThrows(BizException.class,
                () -> orderCreateService.create(req, USER_ID));

        assertEquals(ErrorCode.PARAM_ERROR.getCode(), e.getCode());
        verify(stockRedisHelper, never()).deduct(anyLong(), anyInt());
        verify(activityMapper, never()).selectById(anyLong());
    }

    // ==================== 第 1 步：幂等 ====================

    @Test
    @DisplayName("幂等命中：返回首次订单，不再限流、不再扣库存")
    void create_idempotentHit_shouldReturnFirstOrder() {
        when(valueOps.setIfAbsent(eq(IDEM_KEY), anyString(), anyLong(), any(TimeUnit.class)))
                .thenReturn(false);
        OrderCreateOrder exist = buildExistOrder("GB202611180001");
        when(orderCreateMapper.selectByUserAndActivity(USER_ID, ACTIVITY_ID)).thenReturn(exist);

        OrderCreateResponse resp = orderCreateService.create(buildRequest(1), USER_ID);

        assertEquals("GB202611180001", resp.getOrderNo());
        verify(rateLimiter, never()).tryAcquire(anyString(), anyInt(), anyInt());
        verify(stockRedisHelper, never()).deduct(anyLong(), anyInt());
        verify(stockRedisHelper, never()).rollback(anyLong(), anyInt());
    }

    @Test
    @DisplayName("幂等占位还在但订单查不到：回查 10 次后按 10015 拒绝，不放行第二次扣减")
    void create_idempotentKeyWithoutOrder_shouldRejectAfterPolling() {
        when(valueOps.setIfAbsent(eq(IDEM_KEY), anyString(), anyLong(), any(TimeUnit.class)))
                .thenReturn(false);
        when(orderCreateMapper.selectByUserAndActivity(USER_ID, ACTIVITY_ID)).thenReturn(null);

        BizException e = assertThrows(BizException.class,
                () -> orderCreateService.create(buildRequest(1), USER_ID));

        assertEquals(ErrorCode.ORDER_ALREADY_EXISTS.getCode(), e.getCode());
        // 10 次 × 100ms 的轮询窗口，等不到首次结果就拒绝
        verify(orderCreateMapper, times(10)).selectByUserAndActivity(USER_ID, ACTIVITY_ID);
        verify(stockRedisHelper, never()).deduct(anyLong(), anyInt());
    }

    // ==================== 第 2 步：限流 ====================

    @Test
    @DisplayName("限流：令牌桶没票返回 429，绝不进入扣库存，且放行重试（删幂等键）")
    void create_rateLimited_shouldThrow429AndReleaseIdemKey() {
        givenIdemLocked();
        when(rateLimiter.tryAcquire("order:" + USER_ID, 10, 5)).thenReturn(false);

        BizException e = assertThrows(BizException.class,
                () -> orderCreateService.create(buildRequest(1), USER_ID));

        assertEquals(ErrorCode.TOO_MANY_REQUESTS.getCode(), e.getCode());
        verify(stockRedisHelper, never()).deduct(anyLong(), anyInt());
        // 失败必须删幂等键，否则用户被卡 30 分钟
        verify(stringRedisTemplate).delete(IDEM_KEY);
    }

    // ==================== 第 3 步：业务规则（在花钱之前做完）====================

    @Test
    @DisplayName("活动不存在返回 10001，不扣库存")
    void create_activityNotFound_shouldReturn10001() {
        givenIdemLocked();
        givenRatePassed();
        when(activityMapper.selectById(ACTIVITY_ID)).thenReturn(null);

        BizException e = assertThrows(BizException.class,
                () -> orderCreateService.create(buildRequest(1), USER_ID));

        assertEquals(ErrorCode.ACTIVITY_NOT_FOUND.getCode(), e.getCode());
        verify(stockRedisHelper, never()).deduct(anyLong(), anyInt());
    }

    @Test
    @DisplayName("活动还没开始返回 10002，不扣库存")
    void create_activityNotStarted_shouldReturn10002() {
        givenIdemLocked();
        givenRatePassed();
        Activity activity = buildActivity(1);
        activity.setStartTime(LocalDateTime.now().plusHours(1));
        when(activityMapper.selectById(ACTIVITY_ID)).thenReturn(activity);

        BizException e = assertThrows(BizException.class,
                () -> orderCreateService.create(buildRequest(1), USER_ID));

        assertEquals(ErrorCode.ACTIVITY_NOT_ONGOING.getCode(), e.getCode());
        verify(stockRedisHelper, never()).deduct(anyLong(), anyInt());
    }

    @Test
    @DisplayName("活动状态不是'进行中'返回 10002")
    void create_activityWrongStatus_shouldReturn10002() {
        givenIdemLocked();
        givenRatePassed();
        when(activityMapper.selectById(ACTIVITY_ID)).thenReturn(buildActivity(2));  // 已成团

        BizException e = assertThrows(BizException.class,
                () -> orderCreateService.create(buildRequest(1), USER_ID));

        assertEquals(ErrorCode.ACTIVITY_NOT_ONGOING.getCode(), e.getCode());
    }

    @Test
    @DisplayName("商品不属于该活动（或已下架）抛 400，不扣库存")
    void create_goodsNotInActivity_shouldThrowParamError() {
        givenIdemLocked();
        givenRatePassed();
        when(activityMapper.selectById(ACTIVITY_ID)).thenReturn(buildActivity(1));
        when(goodsMapper.selectByActivityId(ACTIVITY_ID)).thenReturn(List.of(buildGoods(999L, 1)));

        BizException e = assertThrows(BizException.class,
                () -> orderCreateService.create(buildRequest(1), USER_ID));

        assertEquals(ErrorCode.PARAM_ERROR.getCode(), e.getCode());
        verify(stockRedisHelper, never()).deduct(anyLong(), anyInt());
    }

    @Test
    @DisplayName("超出限购数量返回 10004，且不扣库存（省钱之前就挡掉）")
    void create_overBuyLimit_shouldReturn10004() {
        givenIdemLocked();
        givenRatePassed();
        Activity activity = buildActivity(1);
        activity.setBuyLimit(2);
        when(activityMapper.selectById(ACTIVITY_ID)).thenReturn(activity);
        when(goodsMapper.selectByActivityId(ACTIVITY_ID)).thenReturn(List.of(buildGoods(GOODS_ID, 1)));

        BizException e = assertThrows(BizException.class,
                () -> orderCreateService.create(buildRequest(3), USER_ID));

        assertEquals(ErrorCode.PURCHASE_LIMIT_EXCEEDED.getCode(), e.getCode());
        verify(stockRedisHelper, never()).deduct(anyLong(), anyInt());
    }

    // ==================== 第 4 步：Redis 预扣 ====================

    @Test
    @DisplayName("Redis 预扣失败（库存不足/未预热）返回 10003，此时不该回滚（还没扣过）")
    void create_redisDeductFail_shouldReturn10003WithoutRollback() {
        givenIdemLocked();
        givenRatePassed();
        givenActivityAndGoodsOk();
        when(stockRedisHelper.deduct(ACTIVITY_ID, 1)).thenReturn(0L);

        BizException e = assertThrows(BizException.class,
                () -> orderCreateService.create(buildRequest(1), USER_ID));

        assertEquals(ErrorCode.STOCK_NOT_ENOUGH.getCode(), e.getCode());
        verify(stockRedisHelper, never()).rollback(anyLong(), anyInt());
        verify(orderCreateMapper, never()).insertOrder(any());
    }

    @Test
    @DisplayName("未预热（Lua 返回 -1）同样按库存不足快速失败")
    void create_redisNotPreloaded_shouldReturn10003() {
        givenIdemLocked();
        givenRatePassed();
        givenActivityAndGoodsOk();
        when(stockRedisHelper.deduct(ACTIVITY_ID, 1)).thenReturn(-1L);

        BizException e = assertThrows(BizException.class,
                () -> orderCreateService.create(buildRequest(1), USER_ID));

        assertEquals(ErrorCode.STOCK_NOT_ENOUGH.getCode(), e.getCode());
        verify(stockRedisHelper, never()).rollback(anyLong(), anyInt());
    }

    // ==================== 第 5 步：DB 落库 ====================

    @Test
    @DisplayName("成功：订单号 GB 开头、金额=团购价×数量、15 分钟超时，且幂等键写回订单号")
    void create_success_shouldPersistAndWriteBackIdemKey() {
        givenIdemLocked();
        givenRatePassed();
        givenActivityAndGoodsOk();
        when(stockRedisHelper.deduct(ACTIVITY_ID, 2)).thenReturn(1L);
        when(stockMapper.casDeduct(GOODS_ID, 2)).thenReturn(1);

        LocalDateTime before = LocalDateTime.now();
        OrderCreateResponse resp = orderCreateService.create(buildRequest(2), USER_ID);

        assertNotNull(resp.getOrderNo());
        assertTrue(resp.getOrderNo().startsWith("GB"), "订单号应以 GB 开头：" + resp.getOrderNo());
        assertEquals(22, resp.getOrderNo().length(), "GB + 14 位时间 + 6 位随机数");
        assertEquals(0, new BigDecimal("39.80").compareTo(resp.getPayAmount()));
        // 支付超时 15 分钟，允许几秒误差
        assertTrue(resp.getExpireTime().isAfter(before.plusMinutes(14)));
        assertTrue(resp.getExpireTime().isBefore(before.plusMinutes(16)));

        verify(stockMapper).casDeduct(GOODS_ID, 2);
        // 成功后把订单号写回幂等键，后续重复请求直接返回这一单
        verify(valueOps).set(eq(IDEM_KEY), eq(resp.getOrderNo()), eq(30L), eq(TimeUnit.MINUTES));
        verify(stockRedisHelper, never()).rollback(anyLong(), anyInt());
    }

    @Test
    @DisplayName("DB CAS 扣减 0 行：回滚 Redis 预扣并返回 10003（防'扣了没订单'）")
    void create_dbCasFail_shouldRollbackRedis() {
        givenIdemLocked();
        givenRatePassed();
        givenActivityAndGoodsOk();
        when(stockRedisHelper.deduct(ACTIVITY_ID, 2)).thenReturn(1L);
        when(stockMapper.casDeduct(GOODS_ID, 2)).thenReturn(0);

        BizException e = assertThrows(BizException.class,
                () -> orderCreateService.create(buildRequest(2), USER_ID));

        assertEquals(ErrorCode.STOCK_NOT_ENOUGH.getCode(), e.getCode());
        verify(stockRedisHelper).rollback(ACTIVITY_ID, 2);
        verify(stringRedisTemplate).delete(IDEM_KEY);
    }

    @Test
    @DisplayName("撞唯一索引 uk_user_activity：回滚本次预扣，返回首次订单而不是报错")
    void create_duplicateKey_shouldReturnFirstOrderAndRollback() {
        givenIdemLocked();
        givenRatePassed();
        givenActivityAndGoodsOk();
        when(stockRedisHelper.deduct(ACTIVITY_ID, 1)).thenReturn(1L);
        when(orderCreateMapper.insertOrder(any(OrderCreateOrder.class)))
                .thenThrow(new DuplicateKeyException("uk_user_activity"));
        OrderCreateOrder exist = buildExistOrder("GB202611180007");
        when(orderCreateMapper.selectByUserAndActivity(USER_ID, ACTIVITY_ID)).thenReturn(exist);

        OrderCreateResponse resp = orderCreateService.create(buildRequest(1), USER_ID);

        assertEquals("GB202611180007", resp.getOrderNo());
        // 本次预扣没落库，必须还回去
        verify(stockRedisHelper).rollback(ACTIVITY_ID, 1);
        verify(stockMapper, never()).casDeduct(anyLong(), anyInt());
    }

    @Test
    @DisplayName("意外异常：回滚预扣 + 删幂等键 + 翻译成 500，不把堆栈抛给前端")
    void create_unexpectedException_shouldRollbackAndWrap500() {
        givenIdemLocked();
        givenRatePassed();
        givenActivityAndGoodsOk();
        when(stockRedisHelper.deduct(ACTIVITY_ID, 1)).thenReturn(1L);
        when(orderCreateMapper.insertOrder(any(OrderCreateOrder.class)))
                .thenThrow(new IllegalStateException("连接池炸了"));

        BizException e = assertThrows(BizException.class,
                () -> orderCreateService.create(buildRequest(1), USER_ID));

        assertEquals(ErrorCode.SYSTEM_ERROR.getCode(), e.getCode());
        verify(stockRedisHelper).rollback(ACTIVITY_ID, 1);
        verify(stringRedisTemplate).delete(IDEM_KEY);
    }

    // ==================== 测试夹具 ====================

    private void givenIdemLocked() {
        when(valueOps.setIfAbsent(eq(IDEM_KEY), anyString(), anyLong(), any(TimeUnit.class)))
                .thenReturn(true);
    }

    private void givenRatePassed() {
        when(rateLimiter.tryAcquire(anyString(), anyInt(), anyInt())).thenReturn(true);
    }

    private void givenActivityAndGoodsOk() {
        when(activityMapper.selectById(ACTIVITY_ID)).thenReturn(buildActivity(1));
        when(goodsMapper.selectByActivityId(ACTIVITY_ID)).thenReturn(List.of(buildGoods(GOODS_ID, 1)));
    }

    private static OrderCreateRequest buildRequest(int num) {
        OrderCreateRequest req = new OrderCreateRequest();
        req.setActivityId(ACTIVITY_ID);
        req.setGoodsId(GOODS_ID);
        req.setNum(num);
        return req;
    }

    private static Activity buildActivity(int status) {
        Activity activity = new Activity();
        activity.setId(ACTIVITY_ID);
        activity.setTitle("阳光小区土鸡蛋");
        activity.setStatus(status);
        activity.setBuyLimit(5);
        activity.setStartTime(LocalDateTime.now().minusHours(1));
        activity.setEndTime(LocalDateTime.now().plusHours(1));
        return activity;
    }

    private static Goods buildGoods(Long goodsId, int status) {
        Goods goods = new Goods();
        goods.setId(goodsId);
        goods.setActivityId(ACTIVITY_ID);
        goods.setName("土鸡蛋 30 枚");
        goods.setGroupPrice(new BigDecimal("19.90"));
        goods.setStatus(status);
        return goods;
    }

    private static OrderCreateOrder buildExistOrder(String orderNo) {
        OrderCreateOrder order = new OrderCreateOrder();
        order.setOrderNo(orderNo);
        order.setPayAmount(new BigDecimal("19.90"));
        order.setExpireTime(LocalDateTime.now().plusMinutes(10));
        return order;
    }
}
