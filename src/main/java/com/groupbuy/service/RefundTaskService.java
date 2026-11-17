package com.groupbuy.service;

import com.groupbuy.common.ActivityStatus;
import com.groupbuy.common.BizException;
import com.groupbuy.common.ErrorCode;
import com.groupbuy.common.OrderStatus;
import com.groupbuy.dto.ActivityStatusRequest;
import com.groupbuy.entity.Activity;
import com.groupbuy.entity.Order;
import com.groupbuy.entity.Refund;
import com.groupbuy.mapper.OrderMapper;
import com.groupbuy.mapper.RefundMapper;
import com.groupbuy.mapper.RefundTaskMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 退款定时任务（C 负责，#22）。
 *
 * <p>每分钟扫描一次：进行中但已到期的活动 → 置失败 → 该活动已支付订单批量退款。
 * 防重复退款靠 {@code refund.uk_order_no} 唯一索引 + 订单状态 CAS。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RefundTaskService {

    private static final DateTimeFormatter REFUND_NO_FMT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final RefundTaskMapper refundTaskMapper;
    private final OrderMapper orderMapper;
    private final RefundMapper refundMapper;
    private final ActivityService activityService;

    /** 每分钟扫描一次到期未成团的活动 */
    @Scheduled(fixedDelay = 60_000)
    public void scanAndRefund() {
        List<Activity> expired = refundTaskMapper.selectExpiredOngoing();
        if (expired.isEmpty()) {
            return;
        }
        log.info("发现 {} 个到期未成团活动，开始退款", expired.size());
        for (Activity act : expired) {
            refundOneActivity(act);
        }
    }

    private void refundOneActivity(Activity act) {
        // ① 活动置失败 1→3（乐观锁，只判一次）
        ActivityStatusRequest req = new ActivityStatusRequest();
        req.setStatus(ActivityStatus.GROUP_FAILED.getCode());
        req.setVersion(act.getVersion());
        try {
            activityService.changeStatus(act.getId(), req);
        } catch (BizException e) {
            // 已被并发处理过，跳过
            log.info("活动已处理过，跳过 activityId={}", act.getId());
            return;
        }

        // ② 查该活动所有已支付订单，逐个退款
        List<Order> orders = orderMapper.selectByActivity(act.getId(), OrderStatus.PAID.getCode());
        for (Order o : orders) {
            refundOneOrder(o);
        }
        log.info("活动退款完成 activityId={}, 退款 {} 单", act.getId(), orders.size());
    }

    private void refundOneOrder(Order o) {
        // 订单 1→5（CAS，防止并发重复退）
        int rows = orderMapper.updateStatus(o.getOrderNo(),
                OrderStatus.PAID.getCode(), OrderStatus.REFUNDED.getCode());
        if (rows == 0) {
            return;
        }

        // 写退款记录，撞 uk_order_no 说明已退过，跳过
        Refund refund = new Refund();
        refund.setRefundNo(newRefundNo());
        refund.setOrderNo(o.getOrderNo());
        refund.setAmount(o.getPayAmount());
        refund.setReason("到期未成团，自动退款");
        refund.setStatus(1);
        refund.setRefundTime(LocalDateTime.now());
        try {
            refundMapper.insert(refund);
        } catch (DuplicateKeyException e) {
            log.info("退款记录已存在，跳过 orderNo={}", o.getOrderNo());
        }
    }

    private String newRefundNo() {
        return "RF" + LocalDateTime.now().format(REFUND_NO_FMT)
                + String.format("%04d", ThreadLocalRandom.current().nextInt(10000));
    }
}
