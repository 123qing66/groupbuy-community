package com.groupbuy.mapper;

import com.groupbuy.entity.Role;
import com.groupbuy.entity.User;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 用户 / 角色 Mapper（D 负责，接口 #1 登录、#2 注册）。
 *
 * <p>注册防重是"先查后插 + 唯一索引兜底"两步：
 * 先 {@code countByUsername} 快路径拦截，真并发下由 {@code uk_username}
 * 唯一索引抛 DuplicateKeyException，在 AuthService 里翻译成 10012。</p>
 */
public interface UserMapper {

    /** 登录主查询：走 uk_username 索引 */
    User selectByUsername(@Param("username") String username);

    User selectById(@Param("id") Long id);

    /** 注册查重 */
    int countByUsername(@Param("username") String username);

    /** 插入用户，主键回填到 user.id */
    int insert(User user);

    /** 按 user_role 关联查角色列表（登录时组装 roles 返回给前端） */
    List<Role> selectRolesByUserId(@Param("userId") Long userId);

    /** 注册时把角色编码换成角色 id */
    Role selectRoleByCode(@Param("roleCode") String roleCode);

    /** 授予角色，uk_user_role 防重复授权 */
    int insertUserRole(@Param("userId") Long userId, @Param("roleId") Long roleId);
}
