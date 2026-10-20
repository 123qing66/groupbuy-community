package com.groupbuy.controller;

import com.groupbuy.common.R;
import com.groupbuy.dto.PickupPointSaveRequest;
import com.groupbuy.dto.PickupPointVO;
import com.groupbuy.service.PickupPointService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 自提点接口（A 负责，#11）。删除走逻辑删除（status 置 0）。
 */
@Tag(name = "自提点")
@RestController
@RequestMapping("/api/pickup-points")
@RequiredArgsConstructor
public class PickupPointController {

    private final PickupPointService pickupPointService;

    @Operation(summary = "#11 自提点列表", description = "keyword 模糊匹配名称；只查启用项传 status=1")
    @GetMapping
    public R<List<PickupPointVO>> list(@RequestParam(required = false) String keyword,
                                       @RequestParam(required = false) Integer status) {
        return R.ok(pickupPointService.list(keyword, status));
    }

    @Operation(summary = "#11 自提点详情")
    @GetMapping("/{id}")
    public R<PickupPointVO> detail(@PathVariable Long id) {
        return R.ok(pickupPointService.detail(id));
    }

    @Operation(summary = "#11 新增自提点", description = "管理员权限，D 的拦截器接上后生效")
    @PostMapping
    public R<Map<String, Object>> create(@RequestBody PickupPointSaveRequest req) {
        return R.ok(Map.of("id", pickupPointService.create(req)));
    }

    @Operation(summary = "#11 修改自提点")
    @PutMapping("/{id}")
    public R<Void> update(@PathVariable Long id, @RequestBody PickupPointSaveRequest req) {
        pickupPointService.update(id, req);
        return R.ok();
    }

    @Operation(summary = "#11 停用自提点", description = "逻辑删除，订单与核销码仍引用该 id")
    @DeleteMapping("/{id}")
    public R<Void> disable(@PathVariable Long id) {
        pickupPointService.disable(id);
        return R.ok();
    }
}
