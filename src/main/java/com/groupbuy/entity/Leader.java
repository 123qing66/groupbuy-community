package com.groupbuy.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 团长（对应 leader 表，A 负责）。
 *
 * <p>一个 user 只能当一次团长（{@code uk_user_id}），
 * 一个自提点也只能有一个团长（{@code uk_point_id}）。</p>
 */
@Data
public class Leader {

    private Long id;

    private Long userId;

    /** 绑定的自提点 id，未绑定为 null */
    private Long pointId;

    /** 所在小区 */
    private String community;

    /** 1在职 0停用 */
    private Integer status;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
