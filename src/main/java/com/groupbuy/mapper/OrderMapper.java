package com.groupbuy.mapper;

import com.groupbuy.entity.Order;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 订单 Mapper（B 下单时 insert，C 负责后续状态流转）。
 *
 * <p>所有状态流转都走 CAS：{@code WHERE status = 原状态}，返回影响行数，
 * 0 表示订单已被并发改成别的状态，调用方据此做幂等（返回首次结果，不改数据）。</p>
 */
public interface OrderMapper {

    Order selectById(@Param("id") Long id);

    Order selectByOrderNo(@Param("orderNo") String orderNo);

    /**
     * 通用状态流转（CAS）：只允许 fromStatus → toStatus。
     *
     * @return 影响行数，0 = 订单状态已经不是 fromStatus（幂等/并发兜底）
     */
    int updateStatus(@Param("orderNo") String orderNo,
                     @Param("fromStatus") Integer fromStatus,
                     @Param("toStatus") Integer toStatus);

    /** 支付成功：0 → 1 并写支付时间（一条 SQL 原子完成，供支付回调用） */
    int markPaid(@Param("orderNo") String orderNo);

    /** 按活动 + 状态查订单（成团发码用，走 idx_activity_status） */
    List<Order> selectByActivity(@Param("activityId") Long activityId,
                                 @Param("status") Integer status);

    /** 按活动批量流转状态（成团：1→2），影响行数 = 命中订单数 */
    int updateStatusByActivity(@Param("activityId") Long activityId,
                               @Param("fromStatus") Integer fromStatus,
                               @Param("toStatus") Integer toStatus);
}
