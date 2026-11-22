package com.groupbuy.config;

import com.groupbuy.common.BizException;
import com.groupbuy.common.ErrorCode;
import com.groupbuy.common.JwtUtil;
import com.groupbuy.common.UserContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 登录拦截器单元测试（D 自己的模块，#1 的下游）。
 *
 * <p>这里不用 mock 掉 JwtUtil，而是用<b>真</b>的签发器造 token —— 拦截器的价值
 * 就在于"真 token 能过、假 token 过不去"，把签名逻辑 mock 掉等于什么都没验证。</p>
 *
 * <p>另外专门锁一条容易被忽略的事故：{@code afterCompletion} 必须清 ThreadLocal，
 * 否则 Tomcat 复用线程时会把上一个用户的身份串给下一个请求（越权事故）。</p>
 */
class AuthInterceptorTest {

    private static final String SECRET = "unit-test-secret-0123456789";

    private JwtUtil jwtUtil;
    private AuthInterceptor interceptor;

    @BeforeEach
    void setUp() {
        jwtUtil = new JwtUtil(SECRET, 1);
        interceptor = new AuthInterceptor(jwtUtil);
    }

    @AfterEach
    void tearDown() {
        // ThreadLocal 在同一个测试线程里会跨用例残留，必须清
        UserContext.clear();
    }

    @Test
    @DisplayName("白名单：POST /api/auth/login 不需要 token")
    void login_shouldPassWithoutToken() {
        assertTrue(interceptor.preHandle(request("POST", "/api/auth/login"), new MockHttpServletResponse(), null));
    }

    @Test
    @DisplayName("白名单：GET /api/activities 免登录（居民要能先浏览再登录）")
    void browseActivities_shouldPassWithoutToken() {
        assertTrue(interceptor.preHandle(request("GET", "/api/activities"), new MockHttpServletResponse(), null));
        assertTrue(interceptor.preHandle(request("GET", "/api/activities/12"), new MockHttpServletResponse(), null));
        assertTrue(interceptor.preHandle(request("GET", "/api/pickup-points"), new MockHttpServletResponse(), null));
    }

    @Test
    @DisplayName("浏览器预检 OPTIONS 直接放行")
    void options_shouldPass() {
        assertTrue(interceptor.preHandle(request("OPTIONS", "/api/orders"), new MockHttpServletResponse(), null));
    }

    @Test
    @DisplayName("受保护接口没带 Authorization 头 → 401")
    void missingHeader_shouldThrow401() {
        BizException e = assertThrows(BizException.class,
                () -> interceptor.preHandle(request("POST", "/api/orders"), new MockHttpServletResponse(), null));

        assertEquals(ErrorCode.UNAUTHORIZED.getCode(), e.getCode());
        // 注意：不能断言 getUserId() == null —— UserContext 在未登录时会兜底返回
        // DEFAULT_USER_ID（1），这是骨架期的演示逻辑，见本类末尾的说明。
        assertEquals("", UserContext.getRoles(), "鉴权失败不能残留任何角色");
    }

    @Test
    @DisplayName("Authorization 不是 Bearer 格式 → 401")
    void wrongScheme_shouldThrow401() {
        MockHttpServletRequest req = request("POST", "/api/orders");
        req.addHeader("Authorization", "Token abc.def.ghi");

        BizException e = assertThrows(BizException.class,
                () -> interceptor.preHandle(req, new MockHttpServletResponse(), null));

        assertEquals(ErrorCode.UNAUTHORIZED.getCode(), e.getCode());
    }

    @Test
    @DisplayName("token 被篡改（签名不符）→ 401，不能在验签前解析出用户")
    void tamperedToken_shouldThrow401() {
        String token = jwtUtil.create(2L, "user1", "ROLE_RESIDENT");
        String tampered = token.substring(0, token.length() - 2) + "xx";
        MockHttpServletRequest req = request("POST", "/api/orders");
        req.addHeader("Authorization", "Bearer " + tampered);

        BizException e = assertThrows(BizException.class,
                () -> interceptor.preHandle(req, new MockHttpServletResponse(), null));

        assertEquals(ErrorCode.UNAUTHORIZED.getCode(), e.getCode());
        assertEquals("", UserContext.getRoles(), "验签失败时不能让任何角色生效");
    }

    @Test
    @DisplayName("合法 token：放行，并把 userId / roles 写进 UserContext")
    void validToken_shouldPopulateUserContext() {
        String token = jwtUtil.create(2L, "user1", "ROLE_RESIDENT,ROLE_LEADER");
        MockHttpServletRequest req = request("POST", "/api/orders");
        req.addHeader("Authorization", "Bearer " + token);

        assertTrue(interceptor.preHandle(req, new MockHttpServletResponse(), null));
        assertEquals(2L, UserContext.getUserId());
        assertTrue(UserContext.hasRole("ROLE_LEADER"));
        assertTrue(UserContext.hasRole("ROLE_RESIDENT"));
    }

    @Test
    @DisplayName("请求结束必须清 ThreadLocal —— 防线程复用串号")
    void afterCompletion_shouldClearContext() {
        String token = jwtUtil.create(2L, "user1", "ROLE_RESIDENT");
        MockHttpServletRequest req = request("POST", "/api/orders");
        req.addHeader("Authorization", "Bearer " + token);
        interceptor.preHandle(req, new MockHttpServletResponse(), null);
        assertEquals(2L, UserContext.getUserId());

        interceptor.afterCompletion(req, new MockHttpServletResponse(), null, null);

        assertEquals("", UserContext.getRoles(), "线程归还线程池前必须清掉角色");
        assertFalse(UserContext.hasRole("ROLE_RESIDENT"));
    }

    /**
     * 已知隐患（不修，只登记，避免这个 PR 顺手改生产代码）：
     * {@code UserContext.getUserId()} 在 ThreadLocal 为空时返回演示值 1，
     * 而不是 null。好处是骨架期 A/C 的接口没接鉴权也能联调；
     * 代价是<b>任何漏配拦截器的 /api 路径都会静默以 userId=1（管理员）身份执行</b>。
     *
     * <p>这条用例把当前行为钉死：一旦有人把兜底改掉，这里会红，提醒同步评估影响面。
     * 建议单独开一个 Issue 把兜底去掉，改成未登录返回 401。</p>
     */
    @Test
    @DisplayName("已知隐患：未登录时 getUserId() 兜底返回 1（骨架期演示逻辑，需单独修复）")
    void unauthenticatedUserId_fallsBackToDemoUser() {
        UserContext.clear();
        assertEquals(1L, UserContext.getUserId());
        assertFalse(UserContext.hasRole("ROLE_ADMIN"), "兜底只给 id，不给任何角色权限");
    }

    private static MockHttpServletRequest request(String method, String uri) {
        return new MockHttpServletRequest(method, uri);
    }
}
