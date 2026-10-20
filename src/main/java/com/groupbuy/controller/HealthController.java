package com.groupbuy.controller;

import com.groupbuy.common.R;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 健康检查：骨架是否跑起来、Knife4j 是否正常，看这个接口。
 *
 * <p>后续各人写接口时照这个模板加 {@code @Tag} 和 {@code @Operation}，
 * 文档页面才会自动出分组和说明（D 第 15 天做 Knife4j 分组要用到）。</p>
 */
@Tag(name = "健康检查")
@RestController
@RequestMapping("/api")
public class HealthController {

    @Operation(summary = "健康检查", description = "返回服务状态与当前时间")
    @GetMapping("/health")
    public R<Map<String, Object>> health() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("status", "UP");
        data.put("time", LocalDateTime.now().toString());
        return R.ok(data);
    }
}
