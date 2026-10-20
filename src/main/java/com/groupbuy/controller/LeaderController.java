package com.groupbuy.controller;

import com.groupbuy.common.R;
import com.groupbuy.dto.BindPointRequest;
import com.groupbuy.service.LeaderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 团长接口（A 负责，#12）。
 *
 * <p>核销码的生成入口在 {@link com.groupbuy.service.PickupCodeService}，
 * 由 C 在成团时调用，不走 HTTP。</p>
 */
@Tag(name = "团长")
@RestController
@RequestMapping("/api/leaders")
@RequiredArgsConstructor
public class LeaderController {

    private final LeaderService leaderService;

    @Operation(summary = "#12 团长绑定自提点",
            description = "一对一：已被别的团长绑定时报 10016（uk_point_id 唯一索引兜底）")
    @PutMapping("/{id}/bind-point")
    public R<Void> bindPoint(@PathVariable Long id, @RequestBody BindPointRequest req) {
        leaderService.bindPoint(id, req);
        return R.ok();
    }
}
