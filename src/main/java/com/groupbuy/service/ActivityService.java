package com.groupbuy.service;

import com.groupbuy.common.ActivityStatus;
import com.groupbuy.common.BizException;
import com.groupbuy.common.ErrorCode;
import com.groupbuy.dto.ActivityQuery;
import com.groupbuy.dto.ActivitySaveRequest;
import com.groupbuy.dto.ActivityStatusRequest;
import com.groupbuy.dto.ActivityVO;
import com.groupbuy.dto.PageResult;
import com.groupbuy.dto.PickupPointVO;
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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 活动域服务（A 负责，对应 Issue #6 #7 #18）。
 *
 * <p>三个考核点都在这里：</p>
 * <ul>
 *   <li>{@link #page} —— 动态 SQL 分页（≥3 处 {@code <if>}）</li>
 *   <li>{@link #detail} —— Cache-Aside + 逻辑过期防击穿（压测：DB 回源 ≤1 次）</li>
 *   <li>{@link #changeStatus} —— 状态机 + version 乐观锁，非法流转报 10014</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ActivityService {

    /** 活动详情缓存前缀 */
    private static final String CACHE_KEY = "activity:detail:";

    /** 逻辑过期时间：30 分钟 */
    private static final long CACHE_TTL_MINUTES = 30;

    private final ActivityMapper activityMapper;
    private final GoodsMapper goodsMapper;
    private final StockMapper stockMapper;
    private final PickupPointMapper pickupPointMapper;
    private final LeaderMapper leaderMapper;
    private final CacheClient cacheClient;
    private final StockRedisHelper stockRedisHelper;

    // ==================== #3 活动列表（分页 + 动态条件） ====================

    public PageResult<ActivityVO> page(ActivityQuery query) {
        long total = activityMapper.countList(query);
        List<Activity> list = activityMapper.selectList(query);
        List<ActivityVO> voList = new ArrayList<>(list.size());
        for (Activity a : list) {
            voList.add(toSimpleVO(a));
        }
        return PageResult.of(total, query.size(), voList);
    }

    // ==================== #4 活动详情（带缓存保护） ====================

    /**
     * 活动详情。走 {@link CacheClient#getWithLogicalExpire}：
     * 空值缓存防穿透、逻辑过期 + 互斥锁防击穿、TTL 抖动防雪崩。
     *
     * <p>压测验收（#28）：热点 key 过期瞬间 100 并发，DB 回源次数 ≤ 1。</p>
     */
    public ActivityVO detail(Long id) {
        ActivityVO vo = cacheClient.getWithLogicalExpire(
                CACHE_KEY + id, ActivityVO.class, CACHE_TTL_MINUTES, TimeUnit.MINUTES,
                () -> loadDetail(id));
        // 空值缓存已经在 CacheClient 里做了（防穿透），这里只负责把"查不到"翻译成 10001
        if (vo == null) {
            throw new BizException(ErrorCode.ACTIVITY_NOT_FOUND);
        }
        return vo;
    }

    /** 回源：查活动 + 商品 + 库存 + 自提点 */
    private ActivityVO loadDetail(Long id) {
        Activity activity = activityMapper.selectById(id);
        if (activity == null) {
            return null;
        }
        ActivityVO vo = toSimpleVO(activity);

        PickupPoint point = pickupPointMapper.selectById(activity.getPointId());
        if (point != null) {
            PickupPointVO pvo = new PickupPointVO();
            pvo.setId(point.getId());
            pvo.setName(point.getName());
            pvo.setAddress(point.getAddress());
            pvo.setContact(point.getContact());
            pvo.setPhone(point.getPhone());
            pvo.setStatus(point.getStatus());
            vo.setPoint(pvo);
        }

        List<Goods> goodsList = goodsMapper.selectByActivityId(id);
        List<ActivityVO.GoodsVO> goodsVOList = new ArrayList<>();
        for (Goods g : goodsList) {
            ActivityVO.GoodsVO gvo = new ActivityVO.GoodsVO();
            gvo.setId(g.getId());
            gvo.setName(g.getName());
            gvo.setCover(g.getCover());
            gvo.setOriginPrice(g.getOriginPrice());
            gvo.setGroupPrice(g.getGroupPrice());
            Stock stock = stockMapper.selectByGoodsId(g.getId());
            gvo.setAvailable(stock == null ? 0 : stock.getAvailable());
            goodsVOList.add(gvo);
        }
        vo.setGoods(goodsVOList);
        return vo;
    }

    // ==================== #5 团长开团 ====================

    /**
     * 开团：写活动 → 批量写商品 → 逐个初始化库存 → 预热 Redis。
     *
     * <p>Redis 预热这一步很关键：不预热的话 B 的下单 Lua 会返回 -1（未预热）。</p>
     */
    @Transactional(rollbackFor = Exception.class)
    public Long create(ActivitySaveRequest req, Long userId) {
        if (req.getGoods() == null || req.getGoods().isEmpty()) {
            throw new BizException(ErrorCode.PARAM_ERROR, "至少需要一个商品");
        }
        if (req.getTargetNum() == null || req.getTargetNum() <= 0) {
            throw new BizException(ErrorCode.PARAM_ERROR, "成团目标人数必须大于 0");
        }
        if (req.getStartTime() != null && req.getEndTime() != null
                && !req.getStartTime().isBefore(req.getEndTime())) {
            throw new BizException(ErrorCode.PARAM_ERROR, "开始时间必须早于结束时间");
        }

        // 开团人必须是团长，且只能用他自己绑定的自提点（契约 #5：否则报 10016）
        Leader leader = leaderMapper.selectByUserId(userId);
        if (leader == null) {
            throw new BizException(ErrorCode.FORBIDDEN, "当前用户不是团长，无法开团");
        }
        if (req.getPointId() == null || !req.getPointId().equals(leader.getPointId())) {
            throw new BizException(ErrorCode.POINT_ALREADY_BOUND,
                    "请先绑定该自提点再开团（当前绑定自提点：" + leader.getPointId() + "）");
        }

        Activity activity = new Activity();
        activity.setTitle(req.getTitle());
        // activity.leader_id 存 leader 表主键，与 #12 绑定接口、#15 排行榜口径一致
        activity.setLeaderId(leader.getId());
        activity.setPointId(req.getPointId());
        activity.setTargetNum(req.getTargetNum());
        activity.setBuyLimit(req.getBuyLimit() == null ? 1 : req.getBuyLimit());
        activity.setStartTime(req.getStartTime());
        activity.setEndTime(req.getEndTime());
        activityMapper.insert(activity);

        List<Goods> goodsList = new ArrayList<>();
        for (ActivitySaveRequest.GoodsItem item : req.getGoods()) {
            Goods g = new Goods();
            g.setActivityId(activity.getId());
            g.setName(item.getName());
            g.setCover(item.getCover());
            g.setOriginPrice(item.getOriginPrice());
            g.setGroupPrice(item.getGroupPrice());
            goodsList.add(g);
        }
        goodsMapper.insertBatch(goodsList);

        // 初始化库存：total = available，locked = 0
        int totalStock = 0;
        for (int i = 0; i < goodsList.size(); i++) {
            int total = itemTotal(req, i);
            stockMapper.init(goodsList.get(i).getId(), total);
            totalStock += total;
        }
        // 预热 Redis（按活动维度，B 的 Lua 用它）
        stockRedisHelper.preload(activity.getId(), totalStock);

        log.info("开团成功 activityId={}, 商品数={}, 初始库存={}",
                activity.getId(), goodsList.size(), totalStock);
        return activity.getId();
    }

    /** 取某个商品在请求里对应的初始库存 */
    private int itemTotal(ActivitySaveRequest req, int index) {
        ActivitySaveRequest.GoodsItem item = req.getGoods().get(index);
        return item.getTotal() == null ? 0 : item.getTotal();
    }

    // ==================== #6 状态机 ====================

    /**
     * 状态变更：校验转移合法性 → version 乐观锁更新 → 删缓存。
     *
     * @return 变更后的状态
     */
    @Transactional(rollbackFor = Exception.class)
    public Integer changeStatus(Long id, ActivityStatusRequest req) {
        Activity activity = activityMapper.selectById(id);
        if (activity == null) {
            throw new BizException(ErrorCode.ACTIVITY_NOT_FOUND);
        }
        ActivityStatus from = ActivityStatus.of(activity.getStatus());
        if (from == null) {
            throw new BizException(ErrorCode.PARAM_ERROR, "活动当前状态异常，无法变更");
        }
        ActivityStatus to = ActivityStatus.of(req.getStatus());
        from.assertTransferTo(to);

        int rows = activityMapper.updateStatus(id, to.getCode(), req.getVersion());
        if (rows == 0) {
            // 乐观锁没命中：有人抢先改了，让调用方重试
            throw new BizException(ErrorCode.ACTIVITY_STATUS_INVALID, "活动已被其他请求修改，请刷新重试");
        }
        cacheClient.delete(CACHE_KEY + id);
        return to.getCode();
    }

    /** 到达开始时间：待开始 → 进行中（定时任务或首次访问时惰性触发） */
    public int startIfNeeded(Long id) {
        Activity activity = activityMapper.selectById(id);
        if (activity == null
                || activity.getStatus() == null
                || activity.getStatus() != ActivityStatus.WAIT_START.getCode()) {
            return 0;
        }
        int rows = activityMapper.updateStatus(id, ActivityStatus.ONGOING.getCode(), activity.getVersion());
        if (rows > 0) {
            cacheClient.delete(CACHE_KEY + id);
        }
        return rows;
    }

    /**
     * 管理员下架（#17，D 调这个方法，不要直接写 SQL 改 activity 表）。
     */
    @Transactional(rollbackFor = Exception.class)
    public void offline(Long id, String reason) {
        Activity activity = activityMapper.selectById(id);
        if (activity == null) {
            throw new BizException(ErrorCode.ACTIVITY_NOT_FOUND);
        }
        ActivityStatus from = ActivityStatus.of(activity.getStatus());
        from.assertTransferTo(ActivityStatus.OFFLINE);
        int rows = activityMapper.updateStatus(id, ActivityStatus.OFFLINE.getCode(), activity.getVersion());
        if (rows == 0) {
            throw new BizException(ErrorCode.ACTIVITY_STATUS_INVALID, "活动已被其他请求修改");
        }
        cacheClient.delete(CACHE_KEY + id);
        log.info("活动下架 id={}, reason={}", id, reason);
    }

    /** 支付成功后累加参团人数（C 的支付回调调用） */
    public void incrCurrentNum(Long id, int num) {
        activityMapper.incrCurrentNum(id, num);
        cacheClient.delete(CACHE_KEY + id);
    }

    private ActivityVO toSimpleVO(Activity a) {
        ActivityVO vo = new ActivityVO();
        vo.setId(a.getId());
        vo.setTitle(a.getTitle());
        vo.setLeaderId(a.getLeaderId());
        vo.setPointId(a.getPointId());
        vo.setTargetNum(a.getTargetNum());
        vo.setCurrentNum(a.getCurrentNum());
        vo.setBuyLimit(a.getBuyLimit());
        vo.setStatus(a.getStatus());
        vo.setStartTime(a.getStartTime());
        vo.setEndTime(a.getEndTime());
        vo.setVersion(a.getVersion());
        return vo;
    }
}
