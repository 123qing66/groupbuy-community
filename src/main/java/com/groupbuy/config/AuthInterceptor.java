package com.groupbuy.config;

import com.groupbuy.common.BizException;
import com.groupbuy.common.ErrorCode;
import com.groupbuy.common.JwtUtil;
import com.groupbuy.common.UserContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.List;

/**
 * 登录拦截器（D 负责，接口 #1 的下游）。
 *
 * <p>职责就三件：
 * <ol>
 *   <li>从 {@code Authorization: Bearer <token>} 里取 token；</li>
 *   <li>校验通过后把 userId / roles 塞进 {@link UserContext}，
 *       这样 A、B、C 的所有 Service 直接 {@code UserContext.getUserId()} 就能拿到当前用户，
 *       不用在每个 Controller 参数里传来传去；</li>
 *   <li>{@code afterCompletion} 里**必须 clear** —— Tomcat 线程池复用线程，
 *       不清会出现"上一个用户的信息串到下一个请求"的事故。</li>
 * </ol>
 *
 * <p>哪些路径不拦：登录、注册、健康检查，以及**活动列表/详情/自提点的 GET**
 * （居民没登录也要能浏览团购，这是业务要求，不是偷懒）。</p>
 */
@Component
@RequiredArgsConstructor
public class AuthInterceptor implements HandlerInterceptor {

    /** 完全免鉴权（任何方法） */
    private static final List<String> PUBLIC_PATHS = List.of(
            "/api/auth/login",
            "/api/auth/register",
            "/api/users/register",
            "/api/pay/callback",
            "/api/health",
            "/api/ping"
    );

    /** 仅 GET 免鉴权：浏览侧接口 */
    private static final List<String> PUBLIC_GET_PATTERNS = List.of(
            "/api/activities",
            "/api/activities/*",
            "/api/pickup-points",
            "/api/pickup-points/*"
    );

    private final JwtUtil jwtUtil;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String path = request.getRequestURI();
        String method = request.getMethod();

        if (PUBLIC_PATHS.contains(path)) {
            return true;
        }
        // 浏览器预检请求直接放行
        if ("OPTIONS".equals(method)) {
            return true;
        }
        if ("GET".equals(method)) {
            for (String pattern : PUBLIC_GET_PATTERNS) {
                if (pathMatcher.match(pattern, path)) {
                    return true;
                }
            }
        }

        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "缺少 Authorization 头");
        }
        JwtUtil.Claims claims = jwtUtil.parse(header.substring(7).trim());
        UserContext.set(claims.getUserId(), claims.getRoles());
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        // 关键：线程归还线程池前必须清掉，否则串号
        UserContext.clear();
    }
}
