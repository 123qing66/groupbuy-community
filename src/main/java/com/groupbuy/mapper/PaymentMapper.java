package com.groupbuy.mapper;

import com.groupbuy.entity.Payment;
import org.apache.ibatis.annotations.Param;

/**
 * 支付流水 Mapper（C 负责）。
 *
 * <p>幂等核心：{@code insert} 撞唯一索引 {@code uk_trade_no} 会抛
 * {@code DuplicateKeyException}，调用方捕获后判定为重复回调，返回首次结果。</p>
 */
public interface PaymentMapper {

    int insert(Payment payment);

    Payment selectByTradeNo(@Param("tradeNo") String tradeNo);

    Payment selectByOrderNo(@Param("orderNo") String orderNo);
}
