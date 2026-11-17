package com.groupbuy.entity;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 订单（对应 order_info 表）。
 *
 * <p>分工：B 下单时 insert 创建（status=0 待支付），C 负责后续状态流转：
 * 支付回调 0→1、成团 1→2、核销 2→3、退款 1→5、超时取消 0→4。</p>
 *
 * <p>表名叫 order_info 而不是 order，是因为 order 是 MySQL 保留字；
 * Java 类名不受影响，仍叫 Order。</p>
 */
@Data
public class Order {

    private Long id;

    /** 订单号，对外暴露的唯一凭证（唯一索引 uk_order_no） */
    private String orderNo;

    /** 下单用户 id */
    private Long userId;

    /** 活动 id */
    private Long activityId;

    /** 商品 id */
    private Long goodsId;

    /** 购买份数 */
    private Integer num;

    /** 实付金额 */
    private BigDecimal payAmount;

    /** 0待支付 1已支付待成团 2已成团待提货 3已提货 4已取消 5已退款 */
    private Integer status;

    /** 支付超时时间（超时未支付自动取消） */
    private LocalDateTime expireTime;

    /** 支付时间 */
    private LocalDateTime payTime;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
