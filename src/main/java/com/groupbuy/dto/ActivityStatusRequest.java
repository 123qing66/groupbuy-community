package com.groupbuy.dto;

import lombok.Data;

/**
 * 活动状态变更入参（接口 #6，A 负责）。
 *
 * <p>version 用于乐观锁：{@code UPDATE ... WHERE id=? AND version=?}，
 * 影响行数为 0 说明被别人抢先改了，直接报 10014 让调用方重试。</p>
 */
@Data
public class ActivityStatusRequest {

    /** 目标状态 0/1/2/3/4 */
    private Integer status;

    /** 乐观锁版本号 */
    private Integer version;

    /** 下架原因（管理员下架时填） */
    private String reason;
}
