package com.groupbuy.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 下单出参（接口 #7，B 负责）。返回订单号和支付截止时间，前端据此倒计时。 */
@Data
public class OrderCreateResponse {

    /** 订单号，对外唯一凭证 */
    private String orderNo;

    /** 实付金额 */
    private BigDecimal payAmount;

    /** 支付超时时间，超时由定时任务置为已取消 */
    private LocalDateTime expireTime;
}
