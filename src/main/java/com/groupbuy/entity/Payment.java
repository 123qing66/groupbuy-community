package com.groupbuy.entity;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 支付流水（对应 payment 表，C 负责）。
 *
 * <p>幂等核心：{@code trade_no} 有唯一索引 {@code uk_trade_no}，
 * 同一个第三方交易号回调两次，第二次插入直接失败 → 判定为重复回调，返回首次结果。
 * 这就是"防重复扣钱"的落点。</p>
 */
@Data
public class Payment {

    private Long id;

    /** 本站支付流水号（唯一索引 uk_pay_no） */
    private String payNo;

    /** 订单号（索引 idx_order_no） */
    private String orderNo;

    /** 第三方交易号，模拟支付回调时生成，幂等核心 */
    private String tradeNo;

    /** 支付金额 */
    private BigDecimal amount;

    /** 支付渠道，默认 MOCK */
    private String channel;

    /** 0待支付 1已支付 2已关闭 */
    private Integer status;

    /** 回调签名（模拟验签用） */
    private String sign;

    /** 回调时间 */
    private LocalDateTime callbackTime;

    private LocalDateTime createTime;
}
