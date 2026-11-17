package com.groupbuy.mapper;

import com.groupbuy.entity.OrderCreateOrder;
import org.apache.ibatis.annotations.Param;

/**
 * 下单专用 Mapper（B 负责，只建/改文件名带 OrderCreate 的文件）。
 *
 * <p>只做两件事：插入订单、按用户+活动查已有订单（幂等/限购用）。
 * 订单的查询、支付、核销、退款都是 C 的活，别在这里加方法。</p>
 */
public interface OrderCreateMapper {

    /**
     * 插入待支付订单。
     *
     * <p>依赖两条唯一索引兜底：
     * <ul>
     *   <li>uk_order_no         —— 订单号唯一</li>
     *   <li>uk_user_activity    —— 同用户同活动只能一单（限购 + 下单幂等）</li>
     * </ul>
     * 撞 uk_user_activity 时 Spring 会抛 DuplicateKeyException，Service 转成 10015。
     */
    int insertOrder(OrderCreateOrder order);

    /** 幂等命中时回查：同用户同活动已下过的那一单 */
    OrderCreateOrder selectByUserAndActivity(@Param("userId") Long userId,
                                             @Param("activityId") Long activityId);
}
