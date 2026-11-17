package com.groupbuy.controller;

import com.groupbuy.common.R;
import com.groupbuy.dto.RegisterRequest;
import com.groupbuy.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** 用户注册（D 负责，接口 #2）。 */
@Tag(name = "用户", description = "D 负责：注册")
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final AuthService authService;

    @Operation(summary = "#2 注册", description = "写 user + user_role 两张表；用户名重复返回 10012")
    @PostMapping("/register")
    public R<Map<String, Object>> register(@RequestBody RegisterRequest req) {
        return R.ok(authService.register(req));
    }
}
