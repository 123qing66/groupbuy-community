package com.groupbuy.entity;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 退款单（对应 refund 表，C 负责）。
 *
 * <p>幂等核心：{@code order_no} 有唯一索引 {@code uk_order_no}，
 * 一单只退一次，定时任务重复扫到也不会二次退款。</p>
 */
@Data
public class Refund {

    private Long id;

    /** 退款单号（唯一索引 uk_refund_no） */
    private String refundNo;

    /** 订单号（唯一索引 uk_order_no，防重复退款） */
    private String orderNo;

    /** 原支付流水号 */
    private String payNo;

    /** 退款金额 */
    private BigDecimal amount;

    /** 退款原因 */
    private String reason;

    /** 0退款中 1已退款 2退款失败 */
    private Integer status;

    /** 退款完成时间 */
    private LocalDateTime refundTime;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
