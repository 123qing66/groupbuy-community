package com.groupbuy.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 退款对账出参（接口 #14，D 负责）。
 *
 * <p>mismatchCount 是"对账"的意义所在：退款金额与订单实付金额对不上的条数，
 * 正常应为 0；不为 0 说明退款逻辑有问题，要在报告里说明。</p>
 */
@Data
public class RefundReconciliationVO {

    /** 退款单总数 */
    private Long refundTotal;

    /** 退款总金额 */
    private BigDecimal refundAmount;

    /** 金额不一致条数 */
    private Long mismatchCount;

    private List<Item> details;

    @Data
    public static class Item {

        private String refundNo;

        private String orderNo;

        private BigDecimal amount;

        /** 0退款中 1已退款 2退款失败 */
        private Integer status;

        private LocalDateTime refundTime;
    }
}
