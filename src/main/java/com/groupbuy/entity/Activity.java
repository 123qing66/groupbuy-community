package com.groupbuy.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 团购活动（对应 activity 表，A 负责）。
 *
 * <p>状态见 {@link com.groupbuy.common.ActivityStatus}；并发转移靠 version 乐观锁。</p>
 */
@Data
public class Activity {

    private Long id;

    private String title;

    /** 开团团长 id */
    private Long leaderId;

    /** 自提点 id */
    private Long pointId;

    /** 成团目标人数 */
    private Integer targetNum;

    /** 当前已参团（已支付）人数 */
    private Integer currentNum;

    /** 每人限购份数 */
    private Integer buyLimit;

    /** 0待开始 1进行中 2已成团 3已失败 4已下架 */
    private Integer status;

    private LocalDateTime startTime;

    private LocalDateTime endTime;

    /** 乐观锁版本号 */
    private Integer version;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
