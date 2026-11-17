package com.groupbuy.service;

import com.groupbuy.common.ActivityStatus;
import com.groupbuy.common.BizException;
import com.groupbuy.common.ErrorCode;
import com.groupbuy.common.OrderStatus;
import com.groupbuy.dto.ActivityStatusRequest;
import com.groupbuy.dto.ActivityVO;
import com.groupbuy.entity.Order;
import com.groupbuy.mapper.OrderMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 成团判定（C 负责，Issue #20）。
 *
 * <p>只判一次：靠 {@link ActivityService#changeStatus} 里的 version 乐观锁。
 * 并发下多个请求同时判成团，只有一个能把活动 1→2，其余乐观锁落空、静默跳过，
 * 保证"只发一次码"。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GroupJudgeService {

    private final ActivityService activityService;
    private final OrderMapper orderMapper;
    private final PickupCodeService pickupCodeService;

    /**
     * 支付人数 +1 后调用：判断是否成团。未成团或已被并发判定过，都静默返回（不影响支付结果）。
     */
    public void judgeIfFull(Long activityId) {
        ActivityVO vo = activityService.detail(activityId);

        // ① 不在进行中，或人数没达标 → 不动作
        if (vo.getStatus() == null
                || vo.getStatus() != ActivityStatus.ONGOING.getCode()
                || vo.getCurrentNum() == null
                || vo.getCurrentNum() < vo.getTargetNum()) {
            return;
        }

        // ② CAS 改成团（1→2），version 乐观锁保证只判一次
        ActivityStatusRequest req = new ActivityStatusRequest();
        req.setStatus(ActivityStatus.GROUP_SUCCESS.getCode());
        req.setVersion(vo.getVersion());
        try {
            activityService.changeStatus(activityId, req);
        } catch (BizException e) {
            if (e.getCode() == ErrorCode.ACTIVITY_STATUS_INVALID.getCode()) {
                // 乐观锁没命中 = 已被并发判过，静默返回
                log.info("成团判定已被并发处理，activityId={}", activityId);
                return;
            }
            throw e;
        }

        // ③ 已支付订单批量 1→2（已支付待成团 → 已成团待提货）
        orderMapper.updateStatusByActivity(activityId,
                OrderStatus.PAID.getCode(), OrderStatus.GROUP_SUCCESS.getCode());

        // ④ 给已成团订单生成核销码（一单一码，uk_order_id 兜底防重发）
        List<Order> orders = orderMapper.selectByActivity(activityId, OrderStatus.GROUP_SUCCESS.getCode());
        List<PickupCodeService.CodeSeed> seeds = orders.stream()
                .map(o -> {
                    PickupCodeService.CodeSeed s = new PickupCodeService.CodeSeed();
                    s.setOrderId(o.getId());
                    s.setOrderNo(o.getOrderNo());
                    s.setUserId(o.getUserId());
                    return s;
                })
                .collect(Collectors.toList());
        pickupCodeService.generateBatch(vo.getPointId(), seeds);

        log.info("成团成功 activityId={}, 发码 {} 个", activityId, seeds.size());
    }
}
