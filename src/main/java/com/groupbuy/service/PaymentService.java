package com.groupbuy.service;

import com.groupbuy.common.BizException;
import com.groupbuy.common.ErrorCode;
import com.groupbuy.dto.PayCallbackRequest;
import com.groupbuy.dto.PayCallbackResponse;
import com.groupbuy.entity.Order;
import com.groupbuy.entity.Payment;
import com.groupbuy.mapper.OrderMapper;
import com.groupbuy.mapper.PaymentMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 支付服务（C 负责，接口 #8 模拟支付回调）。
 *
 * <p>三步：验签（MD5）→ 幂等（trade_no 唯一索引）→ CAS 改状态（0→1）。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    /** 模拟密钥；生产环境应放环境变量，不进仓库 */
    private static final String SECRET = "groupbuy-mock-secret";

    private static final DateTimeFormatter PAY_NO_FMT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final OrderMapper orderMapper;
    private final PaymentMapper paymentMapper;
    private final ActivityService activityService;
    private final GroupJudgeService groupJudgeService;

    public PayCallbackResponse payCallback(Long orderId, PayCallbackRequest req) {
        // ① 验签：MD5(orderNo + tradeNo + amount + secret)，不一致直接报 10007
        String expected = md5(req.getOrderNo() + req.getTradeNo() + req.getAmount() + SECRET);
        if (!expected.equalsIgnoreCase(req.getSign())) {
            throw new BizException(ErrorCode.PAYMENT_SIGN_INVALID);
        }

        // ② 查订单，并校验路径里的 id 和订单号对得上
        Order order = orderMapper.selectByOrderNo(req.getOrderNo());
        if (order == null || !order.getId().equals(orderId)) {
            throw new BizException(ErrorCode.ORDER_NOT_FOUND);
        }

        // ③ 幂等：插入支付流水。撞 uk_trade_no 说明是重复回调
        Payment payment = new Payment();
        payment.setPayNo(newPayNo());
        payment.setOrderNo(req.getOrderNo());
        payment.setTradeNo(req.getTradeNo());
        payment.setAmount(req.getAmount());
        payment.setChannel("MOCK");
        payment.setStatus(1);
        payment.setSign(req.getSign());
        payment.setCallbackTime(LocalDateTime.now());
        try {
            paymentMapper.insert(payment);
        } catch (DuplicateKeyException e) {
            // 同一 trade_no 第二次回调：返回首次结果，不再重复入账/加人数
            log.info("支付回调重复，tradeNo={}", req.getTradeNo());
            return toResponse(orderMapper.selectByOrderNo(req.getOrderNo()));
        }

        // ④ CAS 改状态 0→1，写 pay_time。影响行数 0 = 订单已经不是待支付
        int rows = orderMapper.markPaid(req.getOrderNo());
        if (rows == 0) {
            throw new BizException(ErrorCode.ORDER_STATUS_INVALID, "订单不是待支付状态");
        }

        // ⑤ 参团人数 +1，然后触发成团判定（#20）
        activityService.incrCurrentNum(order.getActivityId(), 1);
        try {
            groupJudgeService.judgeIfFull(order.getActivityId());
        } catch (Exception e) {
            // 成团判定失败不影响支付结果，只记日志
            log.warn("成团判定失败（不影响支付结果）activityId={}", order.getActivityId(), e);
        }

        log.info("支付成功 orderNo={}, tradeNo={}", req.getOrderNo(), req.getTradeNo());
        // 重新查一次，拿到最新的状态和支付时间
        return toResponse(orderMapper.selectByOrderNo(req.getOrderNo()));
    }

    private PayCallbackResponse toResponse(Order order) {
        PayCallbackResponse resp = new PayCallbackResponse();
        resp.setOrderNo(order.getOrderNo());
        resp.setOrderStatus(order.getStatus());
        resp.setPayTime(order.getPayTime());
        return resp;
    }

    /** 本站支付流水号：PAY + 时间到秒 + 4 位随机数（uk_pay_no 唯一） */
    private String newPayNo() {
        return "PAY" + LocalDateTime.now().format(PAY_NO_FMT)
                + String.format("%04d", ThreadLocalRandom.current().nextInt(10000));
    }

    private String md5(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] bytes = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : bytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("MD5 不可用", e);
        }
    }
}
