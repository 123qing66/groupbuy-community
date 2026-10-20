package com.groupbuy.dto;

import lombok.Data;

import java.math.BigDecimal;

/** 成团率统计出参（接口 #13，D 负责）。 */
@Data
public class GroupRateVO {

    /** 活动总数 */
    private Long totalActivity;

    /** 已成团 */
    private Long successActivity;

    /** 已失败 */
    private Long failedActivity;

    /** 进行中 */
    private Long ongoingActivity;

    /** 成团率，保留两位小数，如 71.67 */
    private BigDecimal groupRate;
}
