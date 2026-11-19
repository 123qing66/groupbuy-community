package com.groupbuy.mapper;

import com.groupbuy.dto.GroupRateVO;
import com.groupbuy.dto.RefundReconciliationVO;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 后台统计 Mapper（D 负责，Issue #23）。
 *
 * <p>只放聚合查询，不写单条读写 —— 单条读写各自归域（activity/order/refund 的 mapper）。</p>
 */
public interface StatisticsMapper {

    /**
     * 成团率统计：按活动创建时间落在 [start, end) 区间内，按 status 分桶。
     *
     * <p>已下架（status = 4）不参与统计，所以 SQL 里直接排掉，
     * 这样 {@code totalActivity} 天然就是"有效活动数"，成团率的分母不会被人为拉低。</p>
     *
     * @return 各状态计数；区间内没有活动时仍返回一行（COUNT 为 0），不会返回 null
     */
    GroupRateVO selectGroupRate(@Param("start") LocalDateTime start,
                                @Param("end") LocalDateTime end);

    /**
     * 退款汇总：退款单数、退款总额、以及"金额对不上"的条数。
     *
     * <p>mismatchCount 统计两类异常，都算对账失败：
     * ① 退款单金额 ≠ 订单实付金额；② 退款单找不到对应订单（孤儿单）。</p>
     */
    RefundReconciliationVO selectRefundSummary();

    /**
     * 退款明细，按退款时间倒序。
     *
     * <p>不分页：契约（docs/api.md #14）里 details 就是全量数组。
     * 演示库只有十几条，量级上来后要在这里加分页参数。</p>
     */
    List<RefundReconciliationVO.Item> selectRefundDetails();
}
