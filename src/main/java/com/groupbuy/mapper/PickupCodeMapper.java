package com.groupbuy.mapper;

import com.groupbuy.entity.PickupCode;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 核销码 Mapper（A 生成，C 核销）。
 *
 * <p>核销的幂等核心在 C 的 XML 之外：这里只提供生成与查询。
 * 核销用 {@code UPDATE ... WHERE code=? AND status=0}，影响行数 0 即已核销。</p>
 */
public interface PickupCodeMapper {

    int insert(PickupCode code);

    /** 成团时批量生成，一单一码 */
    int insertBatch(@Param("list") List<PickupCode> list);

    PickupCode selectByCode(@Param("code") String code);

    PickupCode selectByOrderId(@Param("orderId") Long orderId);

    /** 团长后台：某自提点下未核销的清单，走 idx_point_status */
    List<PickupCode> selectByPoint(@Param("pointId") Long pointId, @Param("status") Integer status);
}
