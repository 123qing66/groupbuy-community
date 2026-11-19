package com.groupbuy.mapper;

import com.groupbuy.dto.GroupRateVO;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;

/**
 * 后台统计 Mapper（D 负责，Issue #23）。
 *
 * <p>只放聚合查询，不写单条读写 —— 单条读写各自归域（activity/order/refund 的 mapper）。</p>
 */
public interface StatisticsMapper {

    /**
     * 成团率统计：按活动创建时间落在 [start, end) 区间内，按 status 分桶。
     *
     * <p>已下架（status = 4）不参与统计，所以 SQL 里直接排掉，
     * 这样 {@code totalActivity} 天然就是"有效活动数"，成团率的分母不会被人为拉低。</p>
     *
     * @return 各状态计数；区间内没有活动时仍返回一行（COUNT 为 0），不会返回 null
     */
    GroupRateVO selectGroupRate(@Param("start") LocalDateTime start,
                                @Param("end") LocalDateTime end);
}
