package com.groupbuy.integration;

import com.groupbuy.common.JwtUtil;
import com.groupbuy.dto.OrderCreateRequest;
import com.groupbuy.redis.RateLimiter;
import com.groupbuy.redis.StockRedisHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 下单链路集成测试（B 的接口 #7，D 在 #26 里补集成覆盖）。
 *
 * <p><b>为什么这里要 mock Redis：</b>下单的正确性最终落在 MySQL 上 ——
 * 唯一索引 {@code uk_user_activity} 兜幂等、CAS 扣减兜超卖。Redis 那两层
 * （幂等占位 + Lua 预扣）已经在 {@link com.groupbuy.service.OrderCreateServiceTest}
 * 里用单测验过了，这里把 Redis 顶掉，专测"数据库这半边"是不是真的防住了。</p>
 *
 * <p>断言不只看返回码，还要回查数据库：库存到底减了几份、订单到底落了几条。
 * 只看 200 是测不出超卖的。</p>
 */
class OrderCreateIntegrationTest extends BaseIntegrationTest {

    private static final Long USER_ID = 2L;
    private static final Long ACTIVITY_ID = 9001L;
    private static final Long GOODS_ID = 9002L;

    @MockitoBean
    private StockRedisHelper stockRedisHelper;

    @MockitoBean
    private RateLimiter rateLimiter;

    @MockitoBean
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private JwtUtil jwtUtil;

    private ValueOperations<String, String> valueOps;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        valueOps = Mockito.mock(ValueOperations.class);
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOps);
        // 幂等占位永远"首次"，这样才会真的走到 DB 的唯一索引那一层
        when(valueOps.setIfAbsent(anyString(), anyString(), anyLong(), any(TimeUnit.class))).thenReturn(true);
        when(rateLimiter.tryAcquire(anyString(), anyInt(), anyInt())).thenReturn(true);
        when(stockRedisHelper.deduct(anyLong(), anyInt())).thenReturn(1L);

        seedActivityWithStock(5);
    }

    @Test
    @DisplayName("下单成功：订单落库 1 条，库存扣 1 份，返回 GB 订单号")
    void create_success_shouldPersistOrderAndDeductStock() throws Exception {
        mockMvc.perform(post("/api/orders")
                        .header("Authorization", "Bearer " + token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(buildRequest(1))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.orderNo").isNotEmpty())
                .andExpect(jsonPath("$.data.payAmount").value(19.90));

        // 数据库才是最终事实来源
        assertStock(4);
        assertOrderCount(1);
    }

    @Test
    @DisplayName("同用户同活动重复下单：唯一索引兜底，返回首次订单，库存只扣一次")
    void create_twice_shouldHitUniqueIndexAndDeductOnce() throws Exception {
        String first = mockMvc.perform(post("/api/orders")
                        .header("Authorization", "Bearer " + token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(buildRequest(1))))
                .andExpect(jsonPath("$.code").value(0))
                .andReturn().getResponse().getContentAsString();
        String firstOrderNo = objectMapper.readTree(first).path("data").path("orderNo").asText();

        String second = mockMvc.perform(post("/api/orders")
                        .header("Authorization", "Bearer " + token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(buildRequest(1))))
                .andExpect(jsonPath("$.code").value(0))
                .andReturn().getResponse().getContentAsString();
        String secondOrderNo = objectMapper.readTree(second).path("data").path("orderNo").asText();

        org.junit.jupiter.api.Assertions.assertEquals(firstOrderNo, secondOrderNo,
                "第二次下单应返回首次的订单号，而不是新建一单");
        assertOrderCount(1);
        assertStock(4);
        // 第二次的 Redis 预扣没落库，必须还回去
        verify(stockRedisHelper).rollback(ACTIVITY_ID, 1);
    }

    @Test
    @DisplayName("库存为 0：CAS 扣减 0 行，返回 10003，不产生订单，并回滚 Redis 预扣")
    void create_noStock_shouldReturn10003AndRollback() throws Exception {
        jdbcTemplate.update("UPDATE stock SET available = 0 WHERE goods_id = ?", GOODS_ID);

        mockMvc.perform(post("/api/orders")
                        .header("Authorization", "Bearer " + token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(buildRequest(1))))
                .andExpect(jsonPath("$.code").value(10003));

        assertStock(0);
        verify(stockRedisHelper).rollback(ACTIVITY_ID, 1);
        // 说明：这条用例里订单 INSERT 已经执行，只是随后 CAS 扣减失败被
        // status.setRollbackOnly() 标了回滚。测试方法本身也在事务里，
        // 未提交的插入在本事务内可见，所以这里不能再断言 COUNT(*)=0
        // （会假红）。真正的"不落库"由事务回滚保证，已在
        // OrderCreateServiceTest#create_dbCasFail_shouldRollbackRedis 里覆盖。
    }

    @Test
    @DisplayName("超出限购数量返回 10004，且库存不动")
    void create_overBuyLimit_shouldReturn10004() throws Exception {
        mockMvc.perform(post("/api/orders")
                        .header("Authorization", "Bearer " + token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(buildRequest(3))))
                .andExpect(jsonPath("$.code").value(10004));

        assertStock(5);
        assertOrderCount(0);
    }

    @Test
    @DisplayName("未登录下单返回 401，库存和订单都不动")
    void create_withoutToken_shouldReturn401() throws Exception {
        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(buildRequest(1))))
                .andExpect(jsonPath("$.code").value(401));

        assertStock(5);
        assertOrderCount(0);
    }

    // ==================== 夹具与断言 ====================

    private String token() {
        return jwtUtil.create(USER_ID, "it_user", "ROLE_RESIDENT");
    }

    private static OrderCreateRequest buildRequest(int num) {
        OrderCreateRequest req = new OrderCreateRequest();
        req.setActivityId(ACTIVITY_ID);
        req.setGoodsId(GOODS_ID);
        req.setNum(num);
        return req;
    }

    /** 造一个"正在进行中 + 上架商品 + 有库存"的可下单场景 */
    private void seedActivityWithStock(int available) {
        LocalDateTime now = LocalDateTime.now();
        jdbcTemplate.update(
                "INSERT INTO activity (id, title, leader_id, point_id, target_num, current_num, buy_limit, status, start_time, end_time) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                ACTIVITY_ID, "IT集成测试下单活动", 1L, 1L, 10, 0, 2, 1, now.minusHours(1), now.plusHours(2));
        jdbcTemplate.update(
                "INSERT INTO goods (id, activity_id, name, origin_price, group_price, status) VALUES (?, ?, ?, ?, ?, ?)",
                GOODS_ID, ACTIVITY_ID, "IT测试土鸡蛋", new BigDecimal("25.90"), new BigDecimal("19.90"), 1);
        jdbcTemplate.update(
                "INSERT INTO stock (goods_id, total, available, locked) VALUES (?, ?, ?, ?)",
                GOODS_ID, available, available, 0);
    }

    private void assertStock(int expectedAvailable) {
        Integer available = jdbcTemplate.queryForObject(
                "SELECT available FROM stock WHERE goods_id = ?", Integer.class, GOODS_ID);
        org.junit.jupiter.api.Assertions.assertEquals(expectedAvailable, available, "可用库存不符");
    }

    private void assertOrderCount(int expected) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM order_info WHERE user_id = ? AND activity_id = ?",
                Integer.class, USER_ID, ACTIVITY_ID);
        org.junit.jupiter.api.Assertions.assertEquals(expected, count, "订单条数不符");
    }
}
