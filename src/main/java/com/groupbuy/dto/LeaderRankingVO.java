package com.groupbuy.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/** 团长业绩排行出参（接口 #15，D 负责）。 */
@Data
public class LeaderRankingVO {

    /** 已经按 GMV 降序排好，取前 N 名 */
    private List<Item> list;

    @Data
    public static class Item {

        private Long leaderId;

        private String leaderName;

        private String community;

        /** 开团数 */
        private Long activityCount;

        /** 成交订单数 */
        private Long orderCount;

        /** 成交总额 */
        private BigDecimal gmv;

        /** 该团长的成团率 */
        private BigDecimal groupRate;
    }
}
