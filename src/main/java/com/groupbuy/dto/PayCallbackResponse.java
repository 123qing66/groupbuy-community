package com.groupbuy.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 支付回调响应（接口 #8，C 负责）。
 */
@Data
public class PayCallbackResponse {

    private String orderNo;

    /** 回调后的订单状态（首次回调为 1 已支付） */
    private Integer orderStatus;

    private LocalDateTime payTime;
}
