package com.groupbuy.mapper;

import com.groupbuy.entity.Goods;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/** 商品 Mapper（A 负责）。开团时批量插入，活动详情时按活动查。 */
public interface GoodsMapper {

    int insert(Goods goods);

    /** 开团时批量插入该活动下的商品 */
    int insertBatch(@Param("list") List<Goods> list);

    /** 活动详情：一次性拉出该活动全部上架商品，走 idx_activity_status */
    List<Goods> selectByActivityId(@Param("activityId") Long activityId);
}
