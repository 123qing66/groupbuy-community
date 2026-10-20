package com.groupbuy.dto;

import lombok.Data;

/**
 * 活动列表查询条件（接口 #3，A 负责）。
 *
 * <p>这里每个可空字段都要在 Mapper 的 XML 里对应一个 {@code <if>} 分支，
 * 用来落实「≥3 处动态 SQL」的考核点。</p>
 */
@Data
public class ActivityQuery {

    /** 标题模糊匹配 */
    private String keyword;

    /** 0待开始 1进行中 2已成团 3已失败 4已下架 */
    private Integer status;

    /** 团长后台查"我开的活动" */
    private Long leaderId;

    /** 按自提点筛选 */
    private Long pointId;

    /** 从 1 开始，默认 1 */
    private Integer pageNum = 1;

    /** 默认 10，最大 100 */
    private Integer pageSize = 10;

    /** 转成 MyBatis 的 offset，避免各人在 XML 里各写各的算法 */
    public int offset() {
        int p = (pageNum == null || pageNum < 1) ? 1 : pageNum;
        int s = (pageSize == null || pageSize < 1) ? 10 : pageSize;
        return (p - 1) * s;
    }

    public int size() {
        return (pageSize == null || pageSize < 1) ? 10 : Math.min(pageSize, 100);
    }
}
