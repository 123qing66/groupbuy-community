package com.groupbuy.mapper;

import com.groupbuy.dto.ActivityExportRow;
import com.groupbuy.dto.GroupRateVO;
import com.groupbuy.dto.LeaderRankingRow;
import com.groupbuy.dto.OrderExportRow;
import com.groupbuy.dto.RefundExportRow;
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

    /**
     * 团长业绩排行：按 GMV 降序取前 {@code limit} 名。
     *
     * <p>关联口径（docs/http/06-admin.http #15 特别强调过）：
     * activity.leader_id 存的是 <b>leader 表主键</b>，不是 user_id。
     * 团长姓名要绕一层 user.real_name 才有。</p>
     *
     * <p>成交口径：order_info.status IN (1,2,3) —— 已支付 / 已成团 / 已提货。
     * 待支付(0)、已取消(4)、已退款(5) 都不算成交，否则 GMV 会被未付款的单撑虚。</p>
     */
    List<LeaderRankingRow> selectLeaderRanking(@Param("limit") int limit);

    // ==================== #16 Excel 导出（游标分页，避免一次 select 全表） ====================

    /**
     * 活动明细导出，按主键游标分页。
     *
     * <p>用 {@code id > afterId} 而不是 {@code LIMIT offset, size}：后者越翻越慢
     * （要扫掉前 offset 行），前者每页都走主键索引。首次调用传 {@code afterId = 0}。</p>
     */
    List<ActivityExportRow> selectExportActivities(@Param("afterId") long afterId,
                                                   @Param("limit") int limit);

    /** 订单明细导出，游标分页口径同上。 */
    List<OrderExportRow> selectExportOrders(@Param("afterId") long afterId,
                                            @Param("limit") int limit);

    /** 退款明细导出，游标分页口径同上。 */
    List<RefundExportRow> selectExportRefunds(@Param("afterId") long afterId,
                                              @Param("limit") int limit);
}
