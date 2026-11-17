package com.groupbuy.controller;

import com.groupbuy.common.R;
import com.groupbuy.dto.LoginRequest;
import com.groupbuy.dto.LoginResponse;
import com.groupbuy.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 登录鉴权（D 负责，接口 #1）。
 *
 * <p>登录成功后前端把 token 存起来，之后每个请求带
 * {@code Authorization: Bearer <token>}，由 {@link com.groupbuy.config.AuthInterceptor} 校验。</p>
 */
@Tag(name = "登录鉴权", description = "D 负责：登录、登出、当前用户")
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @Operation(summary = "#1 登录", description = "BCrypt 比对密码，成功后签发 JWT（有效期见 groupbuy.jwt.expire-hours）")
    @PostMapping("/login")
    public R<LoginResponse> login(@RequestBody LoginRequest req) {
        return R.ok(authService.login(req));
    }

    @Operation(summary = "当前登录用户", description = "刷新页面后用 token 换回用户信息，需带 Authorization 头")
    @GetMapping("/me")
    public R<LoginResponse> me() {
        return R.ok(authService.currentUser());
    }

    @Operation(summary = "登出", description = "JWT 无状态，服务端不存 token，前端丢弃即可；要真正的强制下线需加 Redis 黑名单（进阶选做）")
    @PostMapping("/logout")
    public R<Map<String, Object>> logout() {
        return R.ok(Map.of("msg", "已登出，请前端丢弃 token"));
    }
}
