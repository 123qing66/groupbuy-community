package com.groupbuy.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 订单视图（接口 #9，C 负责）。
 *
 * <p>pickupCode 仅在 status=2（已成团待提货）时非空，核销码由 A 在成团时生成。</p>
 */
@Data
public class OrderVO {

    private Long id;

    private String orderNo;

    private Long userId;

    private Long activityId;

    /** 冗余展示用：活动标题 */
    private String activityTitle;

    private Long goodsId;

    private String goodsName;

    private Integer num;

    private BigDecimal payAmount;

    /** 0待支付 1已支付 2已成团 3已提货 4已取消 5已退款 */
    private Integer status;

    /** 核销码，未成团为 null */
    private String pickupCode;

    /** 自提点名称 */
    private String pointName;

    private LocalDateTime createTime;

    private LocalDateTime payTime;

    private LocalDateTime expireTime;
}
