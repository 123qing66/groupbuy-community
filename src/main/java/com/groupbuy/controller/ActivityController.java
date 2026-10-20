package com.groupbuy.controller;

import com.groupbuy.common.R;
import com.groupbuy.common.UserContext;
import com.groupbuy.dto.ActivityQuery;
import com.groupbuy.dto.ActivitySaveRequest;
import com.groupbuy.dto.ActivityStatusRequest;
import com.groupbuy.dto.ActivityVO;
import com.groupbuy.dto.PageResult;
import com.groupbuy.service.ActivityService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 活动接口（A 负责）：#3 列表、#4 详情、#5 开团、#6 状态变更。
 *
 * <p>契约见 docs/api.md，字段以那里为准，改之前先开 Issue。</p>
 */
@Tag(name = "团购活动")
@RestController
@RequestMapping("/api/activities")
@RequiredArgsConstructor
public class ActivityController {

    private final ActivityService activityService;

    @Operation(summary = "#3 活动列表", description = "分页 + 关键词/状态/团长/自提点四个动态条件")
    @GetMapping
    public R<PageResult<ActivityVO>> page(ActivityQuery query) {
        return R.ok(activityService.page(query));
    }

    @Operation(summary = "#4 活动详情", description = "走 Cache-Aside + 逻辑过期，防穿透/击穿/雪崩")
    @GetMapping("/{id}")
    public R<ActivityVO> detail(@PathVariable Long id) {
        return R.ok(activityService.detail(id));
    }

    @Operation(summary = "#5 团长开团", description = "写活动 + 批量写商品 + 初始化库存 + 预热 Redis")
    @PostMapping
    public R<Map<String, Object>> create(@RequestBody ActivitySaveRequest req) {
        Long activityId = activityService.create(req, UserContext.getUserId());
        return R.ok(Map.of("activityId", activityId));
    }

    @Operation(summary = "#6 活动状态变更", description = "状态机校验 + version 乐观锁，非法流转报 10014")
    @PutMapping("/{id}/status")
    public R<Map<String, Object>> changeStatus(@PathVariable Long id,
                                               @RequestBody ActivityStatusRequest req) {
        Integer status = activityService.changeStatus(id, req);
        return R.ok(Map.of("id", id, "status", status));
    }
}
