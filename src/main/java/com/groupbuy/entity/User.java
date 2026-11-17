package com.groupbuy.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户实体（D 负责，对应 user 表）。
 *
 * <p>安全约定：{@code password} 字段存的永远是 BCrypt 哈希，绝不明文。
 * 登录时用 {@code BCryptPasswordEncoder.matches(明文, 哈希)} 比对，不解密。</p>
 */
@Data
public class User {

    private Long id;

    /** 登录名，唯一（uk_username） */
    private String username;

    /** BCrypt 哈希，长度固定 60 */
    private String password;

    private String realName;

    private String phone;

    /** 1 正常 0 禁用 */
    private Integer status;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
