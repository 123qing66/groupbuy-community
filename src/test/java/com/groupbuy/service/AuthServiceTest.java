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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 登录 / 注册单元测试（D 自己的模块，#1 #2）。
 *
 * <p>纯 Mockito，不起 Spring、不连库，毫秒级跑完 —— CI 里必须一直绿。</p>
 *
 * <p>重点盯两个安全性质：</p>
 * <ol>
 *   <li><b>防用户名枚举</b>：用户不存在 / 被禁用 / 密码错，三种情况必须返回
 *       同一个错误码 10013，否则攻击者能靠错误码差异探测哪些用户名存在；</li>
 *   <li><b>防重注册两层</b>：countByUsername 快路径 + uk_username 唯一索引兜底，
 *       两层都要翻译成 10012，不能让 DuplicateKeyException 冒到接口层变成 500。</li>
 * </ol>
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserMapper userMapper;

    @Mock
    private JwtUtil jwtUtil;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private AuthService authService;

    // ==================== 登录 ====================

    @Test
    @DisplayName("登录：请求体为 null 抛 400")
    void login_nullRequest_shouldThrowParamError() {
        BizException e = assertThrows(BizException.class, () -> authService.login(null));
        assertEquals(ErrorCode.PARAM_ERROR.getCode(), e.getCode());
    }

    @Test
    @DisplayName("登录：用户名或密码为空白抛 400，且不查库")
    void login_blankField_shouldThrowParamError() {
        LoginRequest req = new LoginRequest();
        req.setUsername("   ");
        req.setPassword("123456");

        BizException e = assertThrows(BizException.class, () -> authService.login(req));
        assertEquals(ErrorCode.PARAM_ERROR.getCode(), e.getCode());
        verify(userMapper, never()).selectByUsername(anyString());
    }

    @Test
    @DisplayName("登录：用户不存在返回 10013，不暴露'用户不存在'")
    void login_userNotFound_shouldReturnBadCredentials() {
        LoginRequest req = buildLogin("nobody", "123456");
        when(userMapper.selectByUsername("nobody")).thenReturn(null);

        BizException e = assertThrows(BizException.class, () -> authService.login(req));
        assertEquals(ErrorCode.BAD_CREDENTIALS.getCode(), e.getCode());
        // 用户都没查到，就不该去比对密码
        verify(passwordEncoder, never()).matches(anyString(), anyString());
    }

    @Test
    @DisplayName("登录：账号被禁用返回 10013（与密码错同一个码）")
    void login_disabledUser_shouldReturnBadCredentials() {
        LoginRequest req = buildLogin("frozen", "123456");
        User user = buildUser(9L, "frozen", "hash", 0);
        when(userMapper.selectByUsername("frozen")).thenReturn(user);

        BizException e = assertThrows(BizException.class, () -> authService.login(req));
        assertEquals(ErrorCode.BAD_CREDENTIALS.getCode(), e.getCode());
    }

    @Test
    @DisplayName("登录：密码错误返回 10013")
    void login_wrongPassword_shouldReturnBadCredentials() {
        LoginRequest req = buildLogin("user1", "wrong-pwd");
        when(userMapper.selectByUsername("user1")).thenReturn(buildUser(2L, "user1", "hash", 1));
        when(passwordEncoder.matches("wrong-pwd", "hash")).thenReturn(false);

        BizException e = assertThrows(BizException.class, () -> authService.login(req));
        assertEquals(ErrorCode.BAD_CREDENTIALS.getCode(), e.getCode());
    }

    @Test
    @DisplayName("登录成功：签发 token，角色带进响应，username 前后空格被 trim")
    void login_success_shouldReturnTokenAndRoles() {
        LoginRequest req = buildLogin("  user1  ", "123456");
        when(userMapper.selectByUsername("user1")).thenReturn(buildUser(2L, "user1", "hash", 1));
        when(passwordEncoder.matches("123456", "hash")).thenReturn(true);
        when(userMapper.selectRolesByUserId(2L)).thenReturn(List.of(buildRole("ROLE_RESIDENT")));
        when(jwtUtil.create(2L, "user1", "ROLE_RESIDENT")).thenReturn("tk-abc");

        LoginResponse resp = authService.login(req);

        assertEquals("tk-abc", resp.getToken());
        assertEquals(2L, resp.getUserId());
        assertEquals("user1", resp.getUsername());
        assertEquals(List.of("ROLE_RESIDENT"), resp.getRoles());
        // 传给签发器的是 trim 后的用户名，避免 "user1 " 和 "user1" 被当成两个人
        verify(jwtUtil).create(2L, "user1", "ROLE_RESIDENT");
    }

    @Test
    @DisplayName("登录成功：没有任何角色时签发空角色串，不抛异常")
    void login_noRole_shouldNotFail() {
        LoginRequest req = buildLogin("bare", "123456");
        when(userMapper.selectByUsername("bare")).thenReturn(buildUser(5L, "bare", "hash", 1));
        when(passwordEncoder.matches("123456", "hash")).thenReturn(true);
        when(userMapper.selectRolesByUserId(5L)).thenReturn(List.of());
        when(jwtUtil.create(5L, "bare", "")).thenReturn("tk");

        LoginResponse resp = authService.login(req);

        assertTrue(resp.getRoles().isEmpty());
        assertEquals("tk", resp.getToken());
    }

    // ==================== 注册 ====================

    @Test
    @DisplayName("注册：用户名太短抛 400")
    void register_shortUsername_shouldThrowParamError() {
        RegisterRequest req = buildRegister("ab", "123456");

        BizException e = assertThrows(BizException.class, () -> authService.register(req));
        assertEquals(ErrorCode.PARAM_ERROR.getCode(), e.getCode());
        verify(userMapper, never()).insert(any());
    }

    @Test
    @DisplayName("注册：用户名超 20 位抛 400")
    void register_tooLongUsername_shouldThrowParamError() {
        RegisterRequest req = buildRegister("a".repeat(21), "123456");

        BizException e = assertThrows(BizException.class, () -> authService.register(req));
        assertEquals(ErrorCode.PARAM_ERROR.getCode(), e.getCode());
    }

    @Test
    @DisplayName("注册：密码少于 6 位抛 400")
    void register_shortPassword_shouldThrowParamError() {
        RegisterRequest req = buildRegister("newbie", "12345");

        BizException e = assertThrows(BizException.class, () -> authService.register(req));
        assertEquals(ErrorCode.PARAM_ERROR.getCode(), e.getCode());
        verify(userMapper, never()).countByUsername(anyString());
    }

    @Test
    @DisplayName("注册：用户名已存在（快路径）返回 10012")
    void register_duplicateUsername_shouldReturn10012() {
        RegisterRequest req = buildRegister("user1", "123456");
        when(userMapper.countByUsername("user1")).thenReturn(1);

        BizException e = assertThrows(BizException.class, () -> authService.register(req));
        assertEquals(ErrorCode.USER_ALREADY_EXISTS.getCode(), e.getCode());
        verify(userMapper, never()).insert(any());
    }

    @Test
    @DisplayName("注册：并发撞唯一索引时翻译成 10012，不冒成 500")
    void register_duplicateKeyException_shouldTranslateTo10012() {
        RegisterRequest req = buildRegister("racer", "123456");
        when(userMapper.countByUsername("racer")).thenReturn(0);
        when(userMapper.selectRoleByCode("ROLE_RESIDENT")).thenReturn(buildRole("ROLE_RESIDENT"));
        when(passwordEncoder.encode("123456")).thenReturn("hashed");
        when(userMapper.insert(any(User.class))).thenThrow(new DuplicateKeyException("uk_username"));

        BizException e = assertThrows(BizException.class, () -> authService.register(req));
        assertEquals(ErrorCode.USER_ALREADY_EXISTS.getCode(), e.getCode());
        verify(userMapper, never()).insertUserRole(anyLong(), anyLong());
    }

    @Test
    @DisplayName("注册：角色编码不存在抛 400，且不写库")
    void register_roleNotFound_shouldThrowParamError() {
        RegisterRequest req = buildRegister("newbie", "123456");
        req.setRoleCode("ROLE_GHOST");
        when(userMapper.countByUsername("newbie")).thenReturn(0);
        when(userMapper.selectRoleByCode("ROLE_GHOST")).thenReturn(null);

        BizException e = assertThrows(BizException.class, () -> authService.register(req));
        assertEquals(ErrorCode.PARAM_ERROR.getCode(), e.getCode());
        verify(userMapper, never()).insert(any());
    }

    @Test
    @DisplayName("注册成功：写 user + user_role，默认角色是居民，密码以密文入库")
    void register_success_shouldInsertUserAndRole() {
        RegisterRequest req = buildRegister("newbie", "123456");
        req.setRealName("王小明");
        when(userMapper.countByUsername("newbie")).thenReturn(0);
        when(userMapper.selectRoleByCode("ROLE_RESIDENT")).thenReturn(buildRole("ROLE_RESIDENT"));
        when(passwordEncoder.encode("123456")).thenReturn("$2a$10$hashed");
        // 模拟 MyBatis 主键回填
        when(userMapper.insert(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId(88L);
            return 1;
        });

        Map<String, Object> data = authService.register(req);

        assertEquals(88L, data.get("userId"));
        assertEquals("ROLE_RESIDENT", data.get("roleCode"));

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userMapper).insert(captor.capture());
        User saved = captor.getValue();
        assertEquals("newbie", saved.getUsername());
        assertEquals("$2a$10$hashed", saved.getPassword());   // 落库的必须是密文
        assertEquals(1, saved.getStatus());
        assertEquals("王小明", saved.getRealName());

        verify(userMapper).insertUserRole(88L, 3L);
    }

    // ==================== 当前用户 ====================

    @Test
    @DisplayName("当前用户：token 指向的用户已被删除返回 401")
    void currentUser_userGone_shouldReturnUnauthorized() {
        UserContext.set(2L, "ROLE_RESIDENT");
        when(userMapper.selectById(2L)).thenReturn(null);

        BizException e = assertThrows(BizException.class, () -> authService.currentUser());
        assertEquals(ErrorCode.UNAUTHORIZED.getCode(), e.getCode());
    }

    @Test
    @DisplayName("当前用户：/me 不重复签发 token（handler=null）")
    void currentUser_shouldNotIssueToken() {
        UserContext.set(2L, "ROLE_RESIDENT");
        when(userMapper.selectById(2L)).thenReturn(buildUser(2L, "user1", "hash", 1));
        when(userMapper.selectRolesByUserId(2L)).thenReturn(List.of(buildRole("ROLE_RESIDENT")));

        LoginResponse resp = authService.currentUser();

        assertNotNull(resp);
        assertEquals("user1", resp.getUsername());
        // /me 只是读取登录态，不该再签一个新 token
        assertNull(resp.getToken());
        verify(jwtUtil, never()).create(anyLong(), anyString(), anyString());
    }

    @AfterEach
    void clearContext() {
        UserContext.clear();
    }

    // ==================== 测试夹具 ====================

    private static LoginRequest buildLogin(String username, String password) {
        LoginRequest req = new LoginRequest();
        req.setUsername(username);
        req.setPassword(password);
        return req;
    }

    private static RegisterRequest buildRegister(String username, String password) {
        RegisterRequest req = new RegisterRequest();
        req.setUsername(username);
        req.setPassword(password);
        return req;
    }

    private static User buildUser(Long id, String username, String password, int status) {
        User user = new User();
        user.setId(id);
        user.setUsername(username);
        user.setPassword(password);
        user.setStatus(status);
        return user;
    }

    private static Role buildRole(String code) {
        Role role = new Role();
        role.setId(3L);
        role.setRoleCode(code);
        return role;
    }
}
