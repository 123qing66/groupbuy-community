package com.groupbuy.service;

import com.groupbuy.common.BizException;
import com.groupbuy.common.ErrorCode;
import com.groupbuy.common.JwtUtil;
import com.groupbuy.common.UserContext;
import com.groupbuy.dto.LoginRequest;
import com.groupbuy.dto.LoginResponse;
import com.groupbuy.dto.RegisterRequest;
import com.groupbuy.entity.Role;
import com.groupbuy.entity.User;
import com.groupbuy.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 登录 / 注册（D 负责，接口 #1 #2）。
 *
 * <p>登录三步：按 username 查用户 → BCrypt 比对密码 → 签发 JWT。
 * 用户不存在和密码错误**返回同一个错误码 10013**，避免被拿来枚举用户名。</p>
 */
@Service
@RequiredArgsConstructor
public class AuthService {

    private static final String DEFAULT_ROLE = "ROLE_RESIDENT";

    private final UserMapper userMapper;
    private final JwtUtil jwtUtil;
    private final PasswordEncoder passwordEncoder;

    /** 登录：返回 token + 用户基本信息 + 角色 */
    public LoginResponse login(LoginRequest req) {
        if (req == null || !StringUtils.hasText(req.getUsername()) || !StringUtils.hasText(req.getPassword())) {
            throw new BizException(ErrorCode.PARAM_ERROR, "用户名和密码不能为空");
        }
        User user = userMapper.selectByUsername(req.getUsername().trim());
        // 用户不存在、被禁用、密码错误，统一 10013
        if (user == null || user.getStatus() == null || user.getStatus() != 1
                || !passwordEncoder.matches(req.getPassword(), user.getPassword())) {
            throw new BizException(ErrorCode.BAD_CREDENTIALS);
        }
        List<String> roles = roleCodes(user.getId());
        return buildResponse(user, roles, jwtUtil.create(user.getId(), user.getUsername(), join(roles)));
    }

    /**
     * 注册：写 user + user_role 两张表，事务保证一起成功或一起回滚。
     *
     * <p>防重两层：先 countByUsername 快路径；真并发下由 uk_username
     * 唯一索引抛 DuplicateKeyException，翻译成 10012。</p>
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> register(RegisterRequest req) {
        if (req == null || !StringUtils.hasText(req.getUsername()) || !StringUtils.hasText(req.getPassword())) {
            throw new BizException(ErrorCode.PARAM_ERROR, "用户名和密码不能为空");
        }
        String username = req.getUsername().trim();
        if (username.length() < 3 || username.length() > 20) {
            throw new BizException(ErrorCode.PARAM_ERROR, "用户名长度需为 3-20 位");
        }
        if (req.getPassword().length() < 6) {
            throw new BizException(ErrorCode.PARAM_ERROR, "密码至少 6 位");
        }
        if (userMapper.countByUsername(username) > 0) {
            throw new BizException(ErrorCode.USER_ALREADY_EXISTS);
        }

        String roleCode = StringUtils.hasText(req.getRoleCode()) ? req.getRoleCode() : DEFAULT_ROLE;
        Role role = userMapper.selectRoleByCode(roleCode);
        if (role == null) {
            throw new BizException(ErrorCode.PARAM_ERROR, "角色不存在：" + roleCode);
        }

        User user = new User();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(req.getPassword()));
        user.setRealName(req.getRealName());
        user.setPhone(req.getPhone());
        user.setStatus(1);
        try {
            userMapper.insert(user);
        } catch (DuplicateKeyException e) {
            // 并发下唯一索引兜底
            throw new BizException(ErrorCode.USER_ALREADY_EXISTS);
        }
        userMapper.insertUserRole(user.getId(), role.getId());

        Map<String, Object> data = new HashMap<>(2);
        data.put("userId", user.getId());
        data.put("roleCode", roleCode);
        return data;
    }

    /** 当前登录用户（/api/auth/me），用于前端刷新页面后恢复登录态 */
    public LoginResponse currentUser() {
        Long userId = UserContext.getUserId();
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        return buildResponse(user, roleCodes(userId), null);
    }

    private List<String> roleCodes(Long userId) {
        List<Role> roles = userMapper.selectRolesByUserId(userId);
        List<String> codes = new ArrayList<>();
        if (roles != null) {
            for (Role r : roles) {
                codes.add(r.getRoleCode());
            }
        }
        return codes;
    }

    private static String join(List<String> roles) {
        return roles.isEmpty() ? "" : String.join(",", roles);
    }

    private static LoginResponse buildResponse(User user, List<String> roles, String token) {
        LoginResponse resp = new LoginResponse();
        resp.setToken(token);
        resp.setUserId(user.getId());
        resp.setUsername(user.getUsername());
        resp.setRealName(user.getRealName());
        resp.setRoles(roles);
        return resp;
    }
}
