package com.groupbuy.dto;

import lombok.Data;

/**
 * 团长绑定自提点入参（接口 #12，A 负责）。
 *
 * <p>一对一靠 {@code leader.uk_point_id} 唯一索引保证：
 * 一个自提点只能有一个团长，被抢先绑定时插入失败 → 返回 10016。</p>
 */
@Data
public class BindPointRequest {

    private Long pointId;
}
