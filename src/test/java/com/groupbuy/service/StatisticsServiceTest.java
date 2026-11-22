package com.groupbuy.service;

import com.groupbuy.common.BizException;
import com.groupbuy.common.ErrorCode;
import com.groupbuy.dto.GroupRateVO;
import com.groupbuy.dto.LeaderRankingRow;
import com.groupbuy.dto.LeaderRankingVO;
import com.groupbuy.dto.RefundReconciliationVO;
import com.groupbuy.mapper.StatisticsMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link StatisticsService} 单元测试（D 负责，Issue #23）。
 *
 * <p>这一层可用 Mockito 单独测，不需要 Spring 上下文：Service 的职责是
 * "参数兜底 + 口径计算 + 格式化"，SQL 正确性归 Mapper 和造数实测，两边分开验。</p>
 */
@ExtendWith(MockitoExtension.class)
class StatisticsServiceTest {

    @Mock
    private StatisticsMapper statisticsMapper;

    @InjectMocks
    private StatisticsService statisticsService;

    // ==================== #13 成团率 ====================

    @Test
    @DisplayName("成团率：1 成团 / 1 失败 / 1 进行中 / 分母 4 → 25.00%")
    void groupRateShouldFormatPercentage() {
        GroupRateVO row = new GroupRateVO();
        row.setTotalActivity(4L);
        row.setSuccessActivity(1L);
        row.setFailedActivity(1L);
        row.setOngoingActivity(1L);
        when(statisticsMapper.selectGroupRate(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(row);

        GroupRateVO vo = statisticsService.groupRate(
                LocalDate.of(2026, 10, 1), LocalDate.of(2026, 11, 30));

        assertEquals(4L, vo.getTotalActivity());
        assertEquals("25.00%", vo.getGroupRate());
    }

    @Test
    @DisplayName("成团率：时间区间左闭右开，endDate 当天要算进来")
    void groupRateShouldUseHalfOpenInterval() {
        GroupRateVO row = new GroupRateVO();
        row.setTotalActivity(0L);
        row.setSuccessActivity(0L);
        when(statisticsMapper.selectGroupRate(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(row);

        statisticsService.groupRate(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31));

        ArgumentCaptor<LocalDateTime> captor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(statisticsMapper).selectGroupRate(captor.capture(), captor.capture());
        List<LocalDateTime> args = captor.getAllValues();

        assertEquals(LocalDateTime.of(2026, 10, 1, 0, 0), args.get(0));
        // 10-31 当天 23:59 的活动必须落在区间内，所以上界取 11-01 00:00
        assertEquals(LocalDateTime.of(2026, 11, 1, 0, 0), args.get(1));
    }

    @Test
    @DisplayName("成团率：不传日期默认近 30 天到今天")
    void groupRateShouldDefaultToLast30Days() {
        GroupRateVO row = new GroupRateVO();
        row.setTotalActivity(0L);
        row.setSuccessActivity(0L);
        when(statisticsMapper.selectGroupRate(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(row);

        LocalDate today = LocalDate.now();
        statisticsService.groupRate(null, null);

        ArgumentCaptor<LocalDateTime> captor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(statisticsMapper).selectGroupRate(captor.capture(), captor.capture());
        List<LocalDateTime> args = captor.getAllValues();

        assertEquals(today.minusDays(30).atStartOfDay(), args.get(0));
        assertEquals(today.plusDays(1).atStartOfDay(), args.get(1));
    }

    @Test
    @DisplayName("成团率：分母为 0 返回 0.00% 而不是 null")
    void groupRateShouldReturnZeroWhenNoActivity() {
        GroupRateVO row = new GroupRateVO();
        row.setTotalActivity(0L);
        row.setSuccessActivity(0L);
        row.setFailedActivity(0L);
        row.setOngoingActivity(0L);
        when(statisticsMapper.selectGroupRate(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(row);

        GroupRateVO vo = statisticsService.groupRate(null, null);

        assertEquals("0.00%", vo.getGroupRate());
    }

    @Test
    @DisplayName("成团率：Mapper 返回 null 时兜底为全 0，不抛 NPE")
    void groupRateShouldFallbackWhenMapperReturnsNull() {
        when(statisticsMapper.selectGroupRate(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(null);

        GroupRateVO vo = statisticsService.groupRate(null, null);

        assertNotNull(vo);
        assertEquals(0L, vo.getTotalActivity());
        assertEquals("0.00%", vo.getGroupRate());
    }

    @Test
    @DisplayName("成团率：startDate 晚于 endDate 抛参数错误")
    void groupRateShouldRejectReversedRange() {
        BizException ex = assertThrows(BizException.class,
                () -> statisticsService.groupRate(
                        LocalDate.of(2026, 11, 30), LocalDate.of(2026, 10, 1)));

        assertEquals(ErrorCode.PARAM_ERROR.getCode(), ex.getCode());
    }

    // ==================== #14 退款对账 ====================

    @Test
    @DisplayName("退款对账：汇总与明细都要装配进 VO")
    void refundReconciliationShouldAttachDetails() {
        RefundReconciliationVO summary = new RefundReconciliationVO();
        summary.setRefundTotal(4L);
        summary.setRefundAmount(new BigDecimal("44.70"));
        summary.setMismatchCount(2L);

        RefundReconciliationVO.Item item = new RefundReconciliationVO.Item();
        item.setRefundNo("RF-TEST-001");
        item.setOrderNo("GB-0001");
        item.setAmount(new BigDecimal("9.90"));

        when(statisticsMapper.selectRefundSummary()).thenReturn(summary);
        when(statisticsMapper.selectRefundDetails()).thenReturn(Collections.singletonList(item));

        RefundReconciliationVO vo = statisticsService.refundReconciliation();

        assertEquals(4L, vo.getRefundTotal());
        assertEquals(new BigDecimal("44.70"), vo.getRefundAmount());
        assertEquals(2L, vo.getMismatchCount());
        assertEquals(1, vo.getDetails().size());
        assertEquals("RF-TEST-001", vo.getDetails().get(0).getRefundNo());
    }

    @Test
    @DisplayName("退款对账：无退款记录时返回 0 与空明细")
    void refundReconciliationShouldFallbackWhenEmpty() {
        when(statisticsMapper.selectRefundSummary()).thenReturn(null);
        when(statisticsMapper.selectRefundDetails()).thenReturn(Collections.emptyList());

        RefundReconciliationVO vo = statisticsService.refundReconciliation();

        assertEquals(0L, vo.getRefundTotal());
        assertEquals(BigDecimal.ZERO, vo.getRefundAmount());
        assertEquals(0L, vo.getMismatchCount());
        assertTrue(vo.getDetails().isEmpty());
    }

    // ==================== #15 团长排行 ====================

    @Test
    @DisplayName("团长排行：成团率按 已成团/有效活动 计算并带百分号")
    void leaderRankingShouldComputeGroupRateFromRow() {
        LeaderRankingRow full = row(1L, "张团长", 2L, 2L, "20.00");
        LeaderRankingRow half = row(2L, "李小明", 1L, 2L, "10.00");

        when(statisticsMapper.selectLeaderRanking(10)).thenReturn(List.of(full, half));

        LeaderRankingVO vo = statisticsService.leaderRanking(null);

        assertEquals(2, vo.getList().size());
        assertEquals("100.00%", vo.getList().get(0).getGroupRate());
        assertEquals("50.00%", vo.getList().get(1).getGroupRate());
        // 中间值不能泄漏到对外 VO
        assertEquals(1L, vo.getList().get(0).getLeaderId());
        assertEquals("张团长", vo.getList().get(0).getLeaderName());
        assertEquals(new BigDecimal("20.00"), vo.getList().get(0).getGmv());
    }

    @Test
    @DisplayName("团长排行：limit 为空取 10，0 夹到 1，超上限夹到 100")
    void leaderRankingShouldClampLimit() {
        when(statisticsMapper.selectLeaderRanking(org.mockito.ArgumentMatchers.anyInt()))
                .thenReturn(Collections.emptyList());

        statisticsService.leaderRanking(null);
        verify(statisticsMapper).selectLeaderRanking(eq(10));

        // 0 和负数都夹到 1，两次调用累计 2 次
        statisticsService.leaderRanking(0);
        statisticsService.leaderRanking(-5);
        verify(statisticsMapper, times(2)).selectLeaderRanking(eq(1));

        statisticsService.leaderRanking(999);
        verify(statisticsMapper).selectLeaderRanking(eq(100));
    }

    private LeaderRankingRow row(Long id, String name, Long success, Long valid, String gmv) {
        LeaderRankingRow r = new LeaderRankingRow();
        r.setLeaderId(id);
        r.setLeaderName(name);
        r.setCommunity("测试小区");
        r.setActivityCount(valid);
        r.setOrderCount(1L);
        r.setGmv(new BigDecimal(gmv));
        r.setSuccessActivity(success);
        r.setValidActivity(valid);
        return r;
    }
}
