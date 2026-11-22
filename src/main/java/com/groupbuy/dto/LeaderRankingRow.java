package com.groupbuy.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 团长排行的内部行对象（D 负责，Issue #23）。
 *
 * <p>为什么不直接映射成 {@link LeaderRankingVO.Item}：
 * 成团率要在 Java 里统一算（复用 {@code StatisticsService#formatRate}），
 * 而算它需要 {@code successActivity / validActivity} 两个中间值。
 * 把这两个值塞进对外 VO 会污染接口契约（多出两个前端用不到的字段），
 * 所以单独用一个内部 DTO 承载，Service 转换时丢掉。</p>
 */
@Data
public class LeaderRankingRow {

    private Long leaderId;

    private String leaderName;

    private String community;

    /** 开团数（含已下架） */
    private Long activityCount;

    /** 成交订单数（status 1/2/3） */
    private Long orderCount;

    /** 成交总额 */
    private BigDecimal gmv;

    /** 已成团活动数（算成团率的分子） */
    private Long successActivity;

    /** 有效活动数（算成团率的分母，排除已下架） */
    private Long validActivity;
}
