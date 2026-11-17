package com.groupbuy.controller;

import com.groupbuy.common.R;
import com.groupbuy.common.UserContext;
import com.groupbuy.dto.OrderCreateRequest;
import com.groupbuy.dto.OrderCreateResponse;
import com.groupbuy.service.OrderCreateService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 参团下单接口（B 负责，#7）。
 *
 * <p>只暴露下单这一个 POST；订单查询/支付/核销在 C 的 Controller 里，
 * 两边路径前缀相同但方法不同，不会冲突。</p>
 */
@Tag(name = "参团下单")
@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderCreateController {

    private final OrderCreateService orderCreateService;

    @Operation(summary = "#7 参团下单", description = "限流 → 时间/限购校验 → Redis Lua 预扣 → DB CAS 落库")
    @PostMapping
    public R<OrderCreateResponse> create(@RequestBody OrderCreateRequest req) {
        return R.ok(orderCreateService.create(req, UserContext.getUserId()));
    }
}
