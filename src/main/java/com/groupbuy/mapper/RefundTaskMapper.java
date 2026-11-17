package com.groupbuy.mapper;

import com.groupbuy.entity.Activity;

import java.util.List;

/**
 * 退款定时任务查询 Mapper（C 负责，#22）。
 *
 * <p>只读查询活动表，找"进行中但已到期"的活动。单独建 Mapper，
 * 避免改 A 的 ActivityMapper。</p>
 */
public interface RefundTaskMapper {

    /** 查 status=1（进行中）且已过 end_time 的活动，用于到期置失败 + 退款 */
    List<Activity> selectExpiredOngoing();
}
