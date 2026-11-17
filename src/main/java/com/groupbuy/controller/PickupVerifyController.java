package com.groupbuy.controller;

import com.groupbuy.common.R;
import com.groupbuy.common.UserContext;
import com.groupbuy.dto.VerifyResultVO;
import com.groupbuy.service.PickupVerifyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 核销接口（C 负责，#21，一次性幂等）。
 */
@Tag(name = "核销")
@RestController
@RequestMapping("/api/pickup-codes")
@RequiredArgsConstructor
public class PickupVerifyController {

    private final PickupVerifyService pickupVerifyService;

    @Operation(summary = "#10 团长核销（一次性幂等）",
            description = "同一个码连续核销 5 次，只有第 1 次成功，后 4 次返回 10010")
    @PostMapping("/{code}/verify")
    public R<VerifyResultVO> verify(@PathVariable String code) {
        // 团长身份与自提点归属校验由 D 的拦截器统一做，这里先取当前用户 id 当核销人
        Long verifierId = UserContext.getUserId();
        return R.ok(pickupVerifyService.verify(code, verifierId));
    }
}
