package com.groupbuy.entity;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 商品（对应 goods 表，A 负责）。一个活动下可以有多个商品。 */
@Data
public class Goods {

    private Long id;

    private Long activityId;

    private String name;

    private String cover;

    private BigDecimal originPrice;

    private BigDecimal groupPrice;

    /** 1上架 0下架 */
    private Integer status;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
