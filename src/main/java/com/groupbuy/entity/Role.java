package com.groupbuy.entity;

import lombok.Data;

import java.time.LocalDateTime;

/** 角色实体（D 负责，对应 role 表）。roleCode 如 ROLE_LEADER / ROLE_RESIDENT / ROLE_ADMIN。 */
@Data
public class Role {

    private Long id;

    private String roleCode;

    private String roleName;

    private LocalDateTime createTime;
}
