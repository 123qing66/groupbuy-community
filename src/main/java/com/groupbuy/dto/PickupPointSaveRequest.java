package com.groupbuy.dto;

import lombok.Data;

/** 自提点新增/修改入参（接口 #11，A 负责）。 */
@Data
public class PickupPointSaveRequest {

    private String name;

    private String address;

    private String contact;

    private String phone;

    /** 1启用 0停用，新增时可不传（默认 1） */
    private Integer status;
}
