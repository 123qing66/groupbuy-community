package com.groupbuy.mapper;

import com.groupbuy.entity.Leader;
import org.apache.ibatis.annotations.Param;

/**
 * 团长 Mapper（A 负责，接口 #12）。
 *
 * <p>绑定自提点是"先查再改 + 唯一索引兜底"两步：
 * 先 {@code selectByPointId} 判断有没有被别人占了，
 * 真并发下由 {@code uk_point_id} 唯一索引拦截（DuplicateKeyException → 10016）。</p>
 */
public interface LeaderMapper {

    int insert(Leader leader);

    Leader selectById(@Param("id") Long id);

    Leader selectByUserId(@Param("userId") Long userId);

    /** 查该自提点是否已被别的团长绑定 */
    Leader selectByPointId(@Param("pointId") Long pointId);

    int bindPoint(@Param("id") Long id, @Param("pointId") Long pointId);
}
