package com.groupbuy.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 模拟支付回调入参（接口 #8，C 负责）。
 *
 * <p>幂等落点：{@code payment.uk_trade_no} 或 {@code idempotent_record(biz_type='PAY_CALLBACK', biz_key=trade_no)}，
 * 同一 trade_no 重复回调必须返回首次结果，不得重复加钱、重复加人数。</p>
 */
@Data
public class PayCallbackRequest {

    private String orderNo;

    /** 第三方交易号（模拟） */
    private String tradeNo;

    private BigDecimal amount;

    /** 模拟签名：MD5(orderNo + tradeNo + amount + secret) */
    private String sign;
}
