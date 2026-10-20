package com.groupbuy.mapper;

import com.groupbuy.entity.PickupPoint;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/** 自提点 Mapper（A 负责，接口 #11）。删除一律逻辑删除（status 置 0）。 */
public interface PickupPointMapper {

    int insert(PickupPoint point);

    int update(PickupPoint point);

    PickupPoint selectById(@Param("id") Long id);

    /** 列表：keyword 可空、status 可空，只查启用项时传 status=1 */
    List<PickupPoint> selectList(@Param("keyword") String keyword,
                                 @Param("status") Integer status);

    /** 逻辑删除（停用） */
    int disable(@Param("id") Long id);
}
