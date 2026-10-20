package com.groupbuy.entity;

import lombok.Data;

import java.time.LocalDateTime;

/** 自提点（对应 pickup_point 表，A 负责）。与团长是一对一关系。 */
@Data
public class PickupPoint {

    private Long id;

    private String name;

    private String address;

    private String contact;

    private String phone;

    /** 1启用 0停用 */
    private Integer status;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
