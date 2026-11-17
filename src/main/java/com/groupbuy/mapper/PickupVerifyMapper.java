package com.groupbuy.mapper;

import org.apache.ibatis.annotations.Param;

/**
 * 核销 Mapper（C 负责，#21）。
 *
 * <p>pickup_code 表的生成/查询在 A 的 PickupCodeMapper，
 * 核销的 CAS 更新单独放这里，避免直接改 A 的 Mapper。</p>
 */
public interface PickupVerifyMapper {

    /**
     * 幂等核销：{@code WHERE code=? AND status=0}。
     *
     * @return 影响行数，1 = 首次核销成功，0 = 已核销（幂等，无副作用）
     */
    int verify(@Param("code") String code, @Param("verifierId") Long verifierId);
}
