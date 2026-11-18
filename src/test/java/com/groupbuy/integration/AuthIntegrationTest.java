package com.groupbuy.integration;

import com.groupbuy.dto.LoginRequest;
import com.groupbuy.dto.RegisterRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 登录权限集成测试（D 的模块，#1 #2 #26）。
 *
 * <p>覆盖"注册 → 登录 → 带 token 访问"这条完整链路，以及三个拒绝分支
 * （重复注册、密码错、没带 token）。这些是前端联调时最常踩的坑，
 * 用真库跑一遍比看代码靠谱。</p>
 *
 * <p>注意：本类会自备角色数据（见 {@link #ensureBaseRoles()}）。CI 里的
 * data.sql 目前还是占位，如果不自备，注册用例在 CI 上必然红。</p>
 */
class AuthIntegrationTest extends BaseIntegrationTest {

    private static final String PASSWORD = "123456";

    private String username;

    @BeforeEach
    void setUp() {
        ensureBaseRoles();
        // 用户名带时间戳，避免和历史数据撞 uk_username
        username = "it_" + (System.nanoTime() % 1_000_000);
    }

    @Test
    @DisplayName("注册 → 登录 → /me：一条完整链路走通，token 能换回当前用户")
    void register_login_me_shouldWork() throws Exception {
        // 1. 注册
        RegisterRequest reg = new RegisterRequest();
        reg.setUsername(username);
        reg.setPassword(PASSWORD);
        reg.setRealName("集成测试用户");

        mockMvc.perform(post("/api/users/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(reg)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.roleCode").value("ROLE_RESIDENT"));

        // 2. 登录拿 token
        String body = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(buildLogin(username, PASSWORD))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.token").isNotEmpty())
                .andExpect(jsonPath("$.data.roles[0]").value("ROLE_RESIDENT"))
                .andReturn().getResponse().getContentAsString();
        String token = objectMapper.readTree(body).path("data").path("token").asText();

        // 3. 带 token 访问 /me
        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.username").value(username))
                .andExpect(jsonPath("$.data.realName").value("集成测试用户"));
    }

    @Test
    @DisplayName("重复注册同一个用户名返回 10012（不是 500）")
    void register_duplicate_shouldReturn10012() throws Exception {
        RegisterRequest reg = new RegisterRequest();
        reg.setUsername(username);
        reg.setPassword(PASSWORD);

        mockMvc.perform(post("/api/users/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(reg)))
                .andExpect(jsonPath("$.code").value(0));

        mockMvc.perform(post("/api/users/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(reg)))
                .andExpect(jsonPath("$.code").value(10012));
    }

    @Test
    @DisplayName("密码错误返回 10013，且不返回 token")
    void login_wrongPassword_shouldReturn10013() throws Exception {
        RegisterRequest reg = new RegisterRequest();
        reg.setUsername(username);
        reg.setPassword(PASSWORD);
        mockMvc.perform(post("/api/users/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(reg)))
                .andExpect(jsonPath("$.code").value(0));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(buildLogin(username, "wrong-password"))))
                .andExpect(jsonPath("$.code").value(10013))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    @DisplayName("不带 token 访问 /api/auth/me 返回 401（拦截器生效）")
    void meWithoutToken_shouldReturn401() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(jsonPath("$.code").value(401));
    }

    @Test
    @DisplayName("伪造 token 访问受保护接口返回 401")
    void fakeToken_shouldReturn401() throws Exception {
        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer a.b.c"))
                .andExpect(jsonPath("$.code").value(401));
    }

    // ==================== 夹具 ====================

    private LoginRequest buildLogin(String name, String pwd) {
        LoginRequest req = new LoginRequest();
        req.setUsername(name);
        req.setPassword(pwd);
        return req;
    }

    /**
     * 兜底角色数据：CI 的 data.sql 还是占位文件，不带角色的话注册会返回
     * "角色不存在"。放在测试里自备，本地和 CI 都能跑。
     */
    private void ensureBaseRoles() {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM role WHERE role_code = 'ROLE_RESIDENT'", Integer.class);
        if (count == null || count == 0) {
            jdbcTemplate.update("INSERT INTO role (role_code, role_name) VALUES ('ROLE_RESIDENT', '居民')");
        }
    }
}
