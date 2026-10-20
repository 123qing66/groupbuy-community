package com.groupbuy.dto;

import lombok.Data;

/** 登录入参（接口 #1，D 负责）。 */
@Data
public class LoginRequest {

    /** 登录名 */
    private String username;

    /** 明文密码，传输用 HTTPS；存储一律 BCrypt */
    private String password;
}
