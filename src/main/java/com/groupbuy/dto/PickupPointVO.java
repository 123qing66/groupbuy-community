package com.groupbuy.dto;

import lombok.Data;

import java.time.LocalDateTime;

/** 自提点视图（接口 #11，A 负责）。也作为 ActivityVO.point 的内嵌对象。 */
@Data
public class PickupPointVO {

    private Long id;

    private String name;

    private String address;

    private String contact;

    private String phone;

    /** 1启用 0停用 */
    private Integer status;

    private LocalDateTime createTime;
}
