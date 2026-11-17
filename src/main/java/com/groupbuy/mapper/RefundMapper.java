package com.groupbuy.mapper;

import com.groupbuy.entity.Refund;
import org.apache.ibatis.annotations.Param;

/**
 * 退款单 Mapper（C 负责，#22）。
 *
 * <p>幂等核心：{@code insert} 撞唯一索引 {@code uk_order_no} 会抛
 * {@code DuplicateKeyException}，调用方捕获后跳过，保证一单只退一次。</p>
 */
public interface RefundMapper {

    int insert(Refund refund);

    Refund selectByOrderNo(@Param("orderNo") String orderNo);
}
