package com.groupbuy.controller;

import com.groupbuy.common.R;
import com.groupbuy.dto.PayCallbackRequest;
import com.groupbuy.dto.PayCallbackResponse;
import com.groupbuy.service.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 支付接口（C 负责，#8 模拟支付回调）。
 */
@Tag(name = "支付")
@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    @Operation(summary = "#8 模拟支付回调（验签+幂等）",
            description = "验签 MD5(orderNo+tradeNo+amount+secret)；同一 tradeNo 重复回调返回首次结果")
    @PostMapping("/{id}/pay")
    public R<PayCallbackResponse> payCallback(@PathVariable Long id, @RequestBody PayCallbackRequest req) {
        return R.ok(paymentService.payCallback(id, req));
    }
}
