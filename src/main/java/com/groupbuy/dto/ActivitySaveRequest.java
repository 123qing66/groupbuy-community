package com.groupbuy.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** 开团入参（接口 #5，A 负责）。开团时同步初始化 stock。 */
@Data
public class ActivitySaveRequest {

    private String title;

    private Long pointId;

    /** 成团目标人数 */
    private Integer targetNum;

    /** 每人限购份数 */
    private Integer buyLimit;

    private LocalDateTime startTime;

    private LocalDateTime endTime;

    /** 活动下的商品（至少一个） */
    private List<GoodsItem> goods;

    /** 开团时一并提交的商品与初始库存 */
    @Data
    public static class GoodsItem {

        private String name;

        private String cover;

        private BigDecimal originPrice;

        private BigDecimal groupPrice;

        /** 初始总库存，写入 stock.total 与 stock.available */
        private Integer total;
    }
}
