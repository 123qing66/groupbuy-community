package com.groupbuy.mapper;

import com.groupbuy.dto.ActivityQuery;
import com.groupbuy.entity.Activity;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 活动 Mapper（A 负责，对应 Issue #6 #7）。
 *
 * <p>列表查询走 XML 里的动态 SQL（≥3 处 {@code <if>}），
 * 状态变更走 version 乐观锁，影响行数为 0 表示被并发抢先修改。</p>
 */
public interface ActivityMapper {

    /** 新增活动，主键回填到 {@code activity.id} */
    int insert(Activity activity);

    Activity selectById(@Param("id") Long id);

    /** 条件分页列表：keyword / status / leaderId / pointId 四个可空条件 */
    List<Activity> selectList(ActivityQuery query);

    /** 与 selectList 同条件的总数，用于算 pages */
    long countList(ActivityQuery query);

    /** 状态机转移：带 version 乐观锁，返回影响行数（0 = 被抢先修改） */
    int updateStatus(@Param("id") Long id,
                     @Param("status") Integer status,
                     @Param("version") Integer version);

    /** 支付成功后累加参团人数（C 的成团判定会调） */
    int incrCurrentNum(@Param("id") Long id, @Param("num") int num);
}
