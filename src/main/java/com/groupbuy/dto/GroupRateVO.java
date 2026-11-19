package com.groupbuy.dto;

import lombok.Data;

/** 成团率统计出参（接口 #13，D 负责）。 */
@Data
public class GroupRateVO {

    /** 活动总数（不含已下架） */
    private Long totalActivity;

    /** 已成团 */
    private Long successActivity;

    /** 已失败 */
    private Long failedActivity;

    /** 进行中 */
    private Long ongoingActivity;

    /**
     * 成团率，形如 {@code "71.67%"}。
     *
     * <p>用 String 而不是 BigDecimal：契约（docs/api.md #13、docs/http/06-admin.http）
     * 规定出参就是带百分号的字符串。返回裸数字的话前端还得自己拼 {@code %}，
     * 两边只要有一边漏了就会出现 "71.67" 与 "71.67%" 不一致，索性在后端定死格式。</p>
     */
    private String groupRate;
}
