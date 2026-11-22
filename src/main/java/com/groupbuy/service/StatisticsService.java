package com.groupbuy.service;

import com.groupbuy.common.BizException;
import com.groupbuy.common.ErrorCode;
import com.groupbuy.dto.GroupRateVO;
import com.groupbuy.dto.LeaderRankingRow;
import com.groupbuy.dto.LeaderRankingVO;
import com.groupbuy.dto.RefundReconciliationVO;
import com.groupbuy.mapper.StatisticsMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 后台统计服务（D 负责，Issue #23）。
 *
 * <p>这一层只做三件事：参数兜底、口径计算、格式化成契约要求的形状。
 * SQL 全部在 {@link StatisticsMapper} 里，别在这里拼字符串。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StatisticsService {

    /** 默认统计区间：近 30 天 */
    private static final int DEFAULT_RANGE_DAYS = 30;

    /** 排行默认 / 最大条数 */
    private static final int DEFAULT_RANKING_LIMIT = 10;
    private static final int MAX_RANKING_LIMIT = 100;

    private final StatisticsMapper statisticsMapper;

    // ==================== #13 成团率 ====================

    /**
     * 成团率统计。
     *
     * @param startDate 统计开始日期（含当天），null 表示近 30 天
     * @param endDate   统计结束日期（含当天），null 表示今天
     */
    public GroupRateVO groupRate(LocalDate startDate, LocalDate endDate) {
        LocalDate today = LocalDate.now();
        LocalDate from = startDate == null ? today.minusDays(DEFAULT_RANGE_DAYS) : startDate;
        LocalDate to = endDate == null ? today : endDate;
        if (from.isAfter(to)) {
            throw new BizException(ErrorCode.PARAM_ERROR, "startDate 不能晚于 endDate");
        }

        // 左闭右开：[from 00:00:00, to+1 00:00:00)，保证 to 当天的活动算进来
        LocalDateTime start = from.atStartOfDay();
        LocalDateTime end = to.plusDays(1).atStartOfDay();

        GroupRateVO vo = statisticsMapper.selectGroupRate(start, end);
        if (vo == null) {
            // 理论上 COUNT 聚合一定会返回一行，兜一层防止 NPE
            vo = new GroupRateVO();
            vo.setTotalActivity(0L);
            vo.setSuccessActivity(0L);
            vo.setFailedActivity(0L);
            vo.setOngoingActivity(0L);
        }
        vo.setGroupRate(formatRate(vo.getSuccessActivity(), vo.getTotalActivity()));
        return vo;
    }

    /**
     * 成团率 = 已成团 / 有效活动总数，保留两位小数并带百分号。
     *
     * <p>分母为 0 时返回 {@code "0.00%"}，不返回 null —— 前端拿到 null 还要做一次判断，
     * 而"没有活动"在语义上就是 0%，不存在"未知"的情况。</p>
     */
    private String formatRate(Long numerator, Long denominator) {
        long num = numerator == null ? 0L : numerator;
        long den = denominator == null ? 0L : denominator;
        if (den <= 0) {
            return "0.00%";
        }
        return String.format("%.2f%%", num * 100.0 / den);
    }

    // ==================== #14 退款对账 ====================

    /**
     * 退款对账：汇总 + 明细。
     *
     * <p>mismatchCount 由 SQL 直接算（要 JOIN 订单比金额），Java 侧不重复实现一遍，
     * 免得两边口径不一致。</p>
     */
    public RefundReconciliationVO refundReconciliation() {
        RefundReconciliationVO vo = statisticsMapper.selectRefundSummary();
        if (vo == null) {
            vo = new RefundReconciliationVO();
            vo.setRefundTotal(0L);
            vo.setRefundAmount(BigDecimal.ZERO);
            vo.setMismatchCount(0L);
        }
        vo.setDetails(statisticsMapper.selectRefundDetails());
        return vo;
    }

    // ==================== #15 团长业绩排行 ====================

    /**
     * 团长业绩排行，按 GMV 降序。
     *
     * @param limit 取前 N 名，默认 10；传 0 或负数没有意义，统一夹到 [1, 100]
     */
    public LeaderRankingVO leaderRanking(Integer limit) {
        int n = limit == null ? DEFAULT_RANKING_LIMIT : limit;
        n = Math.max(1, Math.min(n, MAX_RANKING_LIMIT));

        List<LeaderRankingRow> rows = statisticsMapper.selectLeaderRanking(n);
        List<LeaderRankingVO.Item> list = new ArrayList<>(rows.size());
        for (LeaderRankingRow row : rows) {
            LeaderRankingVO.Item item = new LeaderRankingVO.Item();
            item.setLeaderId(row.getLeaderId());
            item.setLeaderName(row.getLeaderName());
            item.setCommunity(row.getCommunity());
            item.setActivityCount(row.getActivityCount());
            item.setOrderCount(row.getOrderCount());
            item.setGmv(row.getGmv());
            item.setGroupRate(formatRate(row.getSuccessActivity(), row.getValidActivity()));
            list.add(item);
        }

        LeaderRankingVO vo = new LeaderRankingVO();
        vo.setList(list);
        return vo;
    }
}
