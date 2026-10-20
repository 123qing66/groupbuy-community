package com.groupbuy.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 库存（对应 stock 表，A 建表，B 写扣减）。
 *
 * <p>单独拆表是为了让"扣库存"这一行成为唯一热点行：
 * Redis 预扣与 DB CAS 都落在这里，压测取证（验收要点①）时定位清晰。</p>
 *
 * <p>不变式：{@code total = available + locked}。</p>
 */
@Data
public class Stock {

    private Long id;

    private Long goodsId;

    /** 总库存 */
    private Integer total;

    /** 可用库存（可扣） */
    private Integer available;

    /** 已锁定（预扣未支付） */
    private Integer locked;

    /** 乐观锁版本号 */
    private Integer version;

    private LocalDateTime updateTime;
}
