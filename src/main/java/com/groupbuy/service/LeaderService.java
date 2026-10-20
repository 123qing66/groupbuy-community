package com.groupbuy.service;

import com.groupbuy.common.BizException;
import com.groupbuy.common.ErrorCode;
import com.groupbuy.dto.BindPointRequest;
import com.groupbuy.entity.Leader;
import com.groupbuy.entity.PickupPoint;
import com.groupbuy.mapper.LeaderMapper;
import com.groupbuy.mapper.PickupPointMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 团长服务（A 负责，接口 #12）。
 *
 * <p>自提点与团长是<b>一对一</b>：先查有没有被别人占了，
 * 真并发下由 {@code uk_point_id} 唯一索引兜底（DuplicateKeyException → 10016）。</p>
 */
@Service
@RequiredArgsConstructor
public class LeaderService {

    private final LeaderMapper leaderMapper;
    private final PickupPointMapper pickupPointMapper;

    /**
     * 绑定自提点。
     */
    @Transactional(rollbackFor = Exception.class)
    public void bindPoint(Long leaderId, BindPointRequest req) {
        Leader leader = leaderMapper.selectById(leaderId);
        if (leader == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "团长不存在");
        }
        PickupPoint point = pickupPointMapper.selectById(req.getPointId());
        if (point == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "自提点不存在");
        }

        // 先查：该自提点是否已被别的团长绑定
        Leader exist = leaderMapper.selectByPointId(req.getPointId());
        if (exist != null && !exist.getId().equals(leaderId)) {
            throw new BizException(ErrorCode.POINT_ALREADY_BOUND);
        }

        try {
            leaderMapper.bindPoint(leaderId, req.getPointId());
        } catch (DuplicateKeyException e) {
            // 并发下唯一索引兜底
            throw new BizException(ErrorCode.POINT_ALREADY_BOUND);
        }
    }

    public Leader getByUserId(Long userId) {
        return leaderMapper.selectByUserId(userId);
    }
}
