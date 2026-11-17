package com.groupbuy.entity;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 订单实体（B 负责下单，只写 order_info 表）。
 *
 * <p>刻意不叫 {@code Order}：C 在 order-status 分支建的是支付/核销侧的
 * {@code com.groupbuy.entity.Order}，两个分支合并时类名不冲突，各管各的表字段。
 *
 * <p>状态：0待支付 1已支付待成团 2已成团待提货 3已提货 4已取消 5已退款。
 */
@Data
public class OrderCreateOrder {

    private Long id;

    /** 订单号，对外唯一凭证 */
    private String orderNo;

    private Long userId;

    private Long activityId;

    private Long goodsId;

    private Integer num;

    /** 实付金额，BigDecimal，禁止 double */
    private BigDecimal payAmount;

    /** 0待支付 1已支付 2已成团 3已提货 4已取消 5已退款 */
    private Integer status;

    /** 支付截止时间，超时由 C 的定时任务取消 */
    private LocalDateTime expireTime;

    private LocalDateTime payTime;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
