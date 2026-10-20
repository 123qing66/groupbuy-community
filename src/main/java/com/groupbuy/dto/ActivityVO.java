package com.groupbuy.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 活动详情出参（接口 #4，A 负责）。
 *
 * <p>注意：这个对象是活动详情缓存的落点，会被 {@code CacheClient} 序列化进 Redis，
 * 字段改动要考虑缓存兼容（改字段时记得让缓存自然过期或清一次）。</p>
 */
@Data
public class ActivityVO {

    private Long id;

    private String title;

    private Long leaderId;

    private Long pointId;

    /** 成团目标人数 */
    private Integer targetNum;

    /** 当前已参团人数 */
    private Integer currentNum;

    /** 每人限购 */
    private Integer buyLimit;

    /** 0待开始 1进行中 2已成团 3已失败 4已下架 */
    private Integer status;

    private LocalDateTime startTime;

    private LocalDateTime endTime;

    /** 乐观锁版本号，前端改状态时原样带回 */
    private Integer version;

    /** 自提点信息（详情接口才返回，列表接口为 null） */
    private PickupPointVO point;

    /** 商品与可用库存（详情接口才返回） */
    private List<GoodsVO> goods;

    /** 商品视图 */
    @Data
    public static class GoodsVO {

        private Long id;

        private String name;

        private String cover;

        private BigDecimal originPrice;

        private BigDecimal groupPrice;

        /** 可用库存，来自 stock.available */
        private Integer available;
    }
}
