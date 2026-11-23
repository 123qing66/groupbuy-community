package com.groupbuy.service;

import com.groupbuy.common.BizException;
import com.groupbuy.dto.ActivityQuery;
import com.groupbuy.dto.ActivitySaveRequest;
import com.groupbuy.dto.ActivityStatusRequest;
import com.groupbuy.dto.ActivityVO;
import com.groupbuy.dto.PageResult;
import com.groupbuy.entity.Activity;
import com.groupbuy.entity.Goods;
import com.groupbuy.entity.Leader;
import com.groupbuy.entity.PickupPoint;
import com.groupbuy.entity.Stock;
import com.groupbuy.mapper.ActivityMapper;
import com.groupbuy.mapper.GoodsMapper;
import com.groupbuy.mapper.LeaderMapper;
import com.groupbuy.mapper.PickupPointMapper;
import com.groupbuy.mapper.StockMapper;
import com.groupbuy.redis.CacheClient;
import com.groupbuy.redis.StockRedisHelper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 活动域服务单测（A 负责，#3—#6 + #17/#20）。
 *
 * <p>纯 Mockito，不起 Spring 容器、不连 DB/Redis，CI 里一定能跑。
 * 覆盖各考核点：分页 / 缓存回源装载 / 开团初始化库存并预热 / 状态机 + 乐观锁。</p>
 */
@ExtendWith(MockitoExtension.class)
class ActivityServiceTest {

    @Mock private ActivityMapper activityMapper;
    @Mock private GoodsMapper goodsMapper;
    @Mock private StockMapper stockMapper;
    @Mock private PickupPointMapper pickupPointMapper;
    @Mock private LeaderMapper leaderMapper;
    @Mock private CacheClient cacheClient;
    @Mock private StockRedisHelper stockRedisHelper;

    @InjectMocks private ActivityService activityService;

    private Activity activity(Long id, int status, int version) {
        Activity a = new Activity();
        a.setId(id);
        a.setTitle("周末水果团");
        a.setLeaderId(3L);
        a.setPointId(1L);
        a.setTargetNum(50);
        a.setCurrentNum(12);
        a.setBuyLimit(2);
        a.setStatus(status);
        a.setVersion(version);
        a.setStartTime(LocalDateTime.of(2026, 10, 21, 10, 0));
        a.setEndTime(LocalDateTime.of(2026, 10, 23, 18, 0));
        return a;
    }

    /** 让缓存"未命中"：直接执行 loader，从而测到 loadDetail 的回源逻辑 */
    private void cacheMiss() {
        doAnswer(inv -> ((Supplier<?>) inv.getArgument(4)).get())
                .when(cacheClient).getWithLogicalExpire(
                        anyString(), eq(ActivityVO.class), anyLong(), any(TimeUnit.class), any());
    }

    /** 构造一个最小可开团请求 */
    private ActivitySaveRequest createReq(Long pointId) {
        ActivitySaveRequest req = new ActivitySaveRequest();
        req.setTitle("周末水果团");
        req.setPointId(pointId);
        req.setTargetNum(50);
        req.setBuyLimit(2);
        req.setStartTime(LocalDateTime.of(2026, 10, 21, 10, 0));
        req.setEndTime(LocalDateTime.of(2026, 10, 23, 18, 0));
        ActivitySaveRequest.GoodsItem gi = new ActivitySaveRequest.GoodsItem();
        gi.setName("泰国金枕榴莲");
        gi.setTotal(1000);
        gi.setGroupPrice(new BigDecimal("89.00"));
        req.setGoods(List.of(gi));
        return req;
    }

    // ==================== #3 列表 ====================

    @Test
    @DisplayName("#3 列表：分页参数与 VO 转换正确")
    void page() {
        ActivityQuery q = new ActivityQuery();
        q.setKeyword("水果");
        when(activityMapper.countList(q)).thenReturn(21L);
        when(activityMapper.selectList(q)).thenReturn(List.of(activity(1L, 1, 0)));

        PageResult<ActivityVO> r = activityService.page(q);

        assertEquals(21L, r.getTotal());
        assertEquals(3L, r.getPages());            // (21 + 10 - 1) / 10
        assertEquals(1, r.getList().size());
        assertEquals("周末水果团", r.getList().get(0).getTitle());
        assertNull(r.getList().get(0).getGoods()); // 列表接口不带商品
    }

    // ==================== #4 详情 ====================

    @Test
    @DisplayName("#4 详情：回源装载自提点 + 商品 + 可用库存")
    void detail() {
        cacheMiss();
        when(activityMapper.selectById(1L)).thenReturn(activity(1L, 1, 0));

        PickupPoint p = new PickupPoint();
        p.setId(1L);
        p.setName("阳光小区自提点");
        p.setAddress("xx路xx号");
        p.setStatus(1);
        when(pickupPointMapper.selectById(1L)).thenReturn(p);

        Goods g = new Goods();
        g.setId(11L);
        g.setName("泰国金枕榴莲");
        g.setGroupPrice(new BigDecimal("89.00"));
        when(goodsMapper.selectByActivityId(1L)).thenReturn(List.of(g));

        Stock s = new Stock();
        s.setAvailable(100);
        when(stockMapper.selectByGoodsId(11L)).thenReturn(s);

        ActivityVO vo = activityService.detail(1L);

        assertEquals("阳光小区自提点", vo.getPoint().getName());
        assertEquals(1, vo.getGoods().size());
        assertEquals(100, vo.getGoods().get(0).getAvailable());
        assertEquals(new BigDecimal("89.00"), vo.getGoods().get(0).getGroupPrice());
    }

    @Test
    @DisplayName("#4 详情：查不到 → 10001")
    void detailNotFound() {
        cacheMiss();
        when(activityMapper.selectById(99L)).thenReturn(null);

        BizException ex = assertThrows(BizException.class, () -> activityService.detail(99L));
        assertEquals(10001, ex.getCode());
    }

    // ==================== #5 开团 ====================

    @Test
    @DisplayName("#5 开团：写活动 + 批量商品 + 初始化库存 + 预热 Redis")
    void create() {
        when(leaderMapper.selectByUserId(100L)).thenReturn(leader());
        // 模拟 MyBatis 回填主键：活动 id 和商品 id 都要回填，库存才能用真实 goodsId
        doAnswer(inv -> {
            Activity a = inv.getArgument(0);
            a.setId(1L);
            return 1;
        }).when(activityMapper).insert(any(Activity.class));
        doAnswer(inv -> {
            List<Goods> gs = inv.getArgument(0);
            for (int i = 0; i < gs.size(); i++) {
                gs.get(i).setId((long) (11 + i));
            }
            return gs.size();
        }).when(goodsMapper).insertBatch(anyList());

        Long id = activityService.create(createReq(1L), 100L);
        assertEquals(1L, id);

        ArgumentCaptor<Activity> ac = ArgumentCaptor.forClass(Activity.class);
        verify(activityMapper).insert(ac.capture());
        assertEquals(3L, ac.getValue().getLeaderId()); // leader 表主键，不是 user id
        // 初始 status=0 由 ActivityMapper.xml 的 INSERT 写死（VALUES ..., 0, ...），
        // 纯 Mockito 不执行 SQL，所以不在实体上断言该字段。

        verify(stockMapper).init(11L, 1000);            // total = available
        verify(stockRedisHelper).preload(1L, 1000L);   // 不预热 B 的 Lua 会返回 -1
    }

    @Test
    @DisplayName("#5 开团：未绑定该自提点 → 10016，且不落库")
    void createPointNotBound() {
        when(leaderMapper.selectByUserId(100L)).thenReturn(leader());

        BizException ex = assertThrows(BizException.class,
                () -> activityService.create(createReq(2L), 100L));
        assertEquals(10016, ex.getCode());
        verifyNoInteractions(activityMapper);
    }

    @Test
    @DisplayName("#5 开团：非团长 → 403")
    void createNotLeader() {
        when(leaderMapper.selectByUserId(100L)).thenReturn(null);

        BizException ex = assertThrows(BizException.class,
                () -> activityService.create(createReq(1L), 100L));
        assertEquals(403, ex.getCode());
    }

    @Test
    @DisplayName("#5 开团：无商品 → 400")
    void createNoGoods() {
        ActivitySaveRequest req = createReq(1L);
        req.setGoods(List.of());

        BizException ex = assertThrows(BizException.class,
                () -> activityService.create(req, 100L));
        assertEquals(400, ex.getCode());
    }

    private Leader leader() {
        Leader leader = new Leader();
        leader.setId(3L);
        leader.setUserId(100L);
        leader.setPointId(1L);
        return leader;
    }

    // ==================== #6 状态机 ====================

    @Test
    @DisplayName("#6 状态变更：合法流转 + version 乐观锁 + 删缓存")
    void changeStatus() {
        when(activityMapper.selectById(1L)).thenReturn(activity(1L, 0, 3));
        when(activityMapper.updateStatus(1L, 1, 3)).thenReturn(1);

        ActivityStatusRequest req = new ActivityStatusRequest();
        req.setStatus(1);   // 待开始 → 进行中
        req.setVersion(3);

        Integer status = activityService.changeStatus(1L, req);

        assertEquals(1, status);
        verify(activityMapper).updateStatus(1L, 1, 3);
        verify(cacheClient).delete("activity:detail:1");
    }

    @Test
    @DisplayName("#6 状态变更：已成团是终态，非法流转 → 10014")
    void changeStatusIllegal() {
        when(activityMapper.selectById(1L)).thenReturn(activity(1L, 2, 3));

        ActivityStatusRequest req = new ActivityStatusRequest();
        req.setStatus(1);
        req.setVersion(3);

        BizException ex = assertThrows(BizException.class,
                () -> activityService.changeStatus(1L, req));
        assertEquals(10014, ex.getCode());
        verify(activityMapper, never()).updateStatus(anyLong(), anyInt(), anyInt());
    }

    @Test
    @DisplayName("#6 状态变更：乐观锁未命中 → 10014，且不删缓存")
    void changeStatusVersionMiss() {
        when(activityMapper.selectById(1L)).thenReturn(activity(1L, 0, 3));
        when(activityMapper.updateStatus(1L, 1, 3)).thenReturn(0);

        ActivityStatusRequest req = new ActivityStatusRequest();
        req.setStatus(1);
        req.setVersion(3);

        BizException ex = assertThrows(BizException.class,
                () -> activityService.changeStatus(1L, req));
        assertEquals(10014, ex.getCode());
        verify(cacheClient, never()).delete(anyString());
    }

    // ==================== #17 / #20 供其他模块调用 ====================

    @Test
    @DisplayName("#17 下架：进行中 → 已下架（D 调 A 的服务）")
    void offline() {
        when(activityMapper.selectById(1L)).thenReturn(activity(1L, 1, 2));
        when(activityMapper.updateStatus(1L, 4, 2)).thenReturn(1);

        activityService.offline(1L, "违规");

        verify(activityMapper).updateStatus(1L, 4, 2);
        verify(cacheClient).delete("activity:detail:1");
    }

    @Test
    @DisplayName("#20 支付成功：累加参团人数并删缓存")
    void incrCurrentNum() {
        when(activityMapper.incrCurrentNum(1L, 1)).thenReturn(1);

        activityService.incrCurrentNum(1L, 1);

        verify(activityMapper).incrCurrentNum(1L, 1);
        verify(cacheClient).delete("activity:detail:1");
    }
}
