package com.groupbuy.dto;

import lombok.Data;

import java.util.List;

/** 登录出参（接口 #1，D 负责）。token 走 JWT，前端后续请求带在 Authorization 头里。 */
@Data
public class LoginResponse {

    /** JWT 令牌 */
    private String token;

    private Long userId;

    private String username;

    private String realName;

    /** 角色编码列表，如 ["ROLE_RESIDENT"] */
    private List<String> roles;
}
