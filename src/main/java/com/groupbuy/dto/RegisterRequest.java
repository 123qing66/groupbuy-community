package com.groupbuy.dto;

import lombok.Data;

/** 注册入参（接口 #2，D 负责）。roleCode 默认 ROLE_RESIDENT。 */
@Data
public class RegisterRequest {

    private String username;

    private String password;

    private String realName;

    private String phone;

    /** ROLE_RESIDENT / ROLE_LEADER / ROLE_ADMIN */
    private String roleCode;
}
