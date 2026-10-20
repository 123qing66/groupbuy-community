package com.groupbuy.dto;

import lombok.Data;

/**
 * 参团下单入参（接口 #7，B 负责，全系统最难的一个接口）。
 *
 * <p>服务端处理顺序固定三步：限流 → Redis 预扣 → DB CAS 落库，
 * 任一步失败都要回滚 Redis 预扣，详见 docs/api.md 第 7 条。</p>
 */
@Data
public class OrderCreateRequest {

    private Long activityId;

    private Long goodsId;

    /** 购买份数，需 ≤ activity.buy_limit */
    private Integer num;
}
