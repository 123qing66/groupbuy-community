package com.groupbuy.service;

import com.groupbuy.common.BizException;
import com.groupbuy.dto.BindPointRequest;
import com.groupbuy.entity.Leader;
import com.groupbuy.entity.PickupPoint;
import com.groupbuy.mapper.LeaderMapper;
import com.groupbuy.mapper.PickupPointMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 团长服务单测（A 负责，接口 #12）。覆盖一对一绑定的三种失败分支与并发兜底。
 */
@ExtendWith(MockitoExtension.class)
class LeaderServiceTest {

    @Mock private LeaderMapper leaderMapper;
    @Mock private PickupPointMapper pickupPointMapper;

    @InjectMocks private LeaderService leaderService;

    private Leader leader(Long id, Long pointId) {
        Leader l = new Leader();
        l.setId(id);
        l.setUserId(100L + id);
        l.setPointId(pointId);
        return l;
    }

    private BindPointRequest req(Long pointId) {
        BindPointRequest r = new BindPointRequest();
        r.setPointId(pointId);
        return r;
    }

    @Test
    @DisplayName("#12 绑定成功：先查后改，一对一")
    void bindSuccess() {
        when(leaderMapper.selectById(3L)).thenReturn(leader(3L, null));
        when(pickupPointMapper.selectById(1L)).thenReturn(new PickupPoint());
        when(leaderMapper.selectByPointId(1L)).thenReturn(null);

        leaderService.bindPoint(3L, req(1L));

        verify(leaderMapper).bindPoint(3L, 1L);
    }

    @Test
    @DisplayName("#12 重复绑定自己已占用的点：允许（幂等，不改数据也无妨）")
    void bindSamePointToSameLeader() {
        when(leaderMapper.selectById(3L)).thenReturn(leader(3L, null));
        when(pickupPointMapper.selectById(1L)).thenReturn(new PickupPoint());
        when(leaderMapper.selectByPointId(1L)).thenReturn(leader(3L, 1L));

        leaderService.bindPoint(3L, req(1L));

        verify(leaderMapper).bindPoint(3L, 1L);
    }

    @Test
    @DisplayName("#12 自提点已被别的团长绑定 → 10016")
    void bindTakenByOther() {
        when(leaderMapper.selectById(3L)).thenReturn(leader(3L, null));
        when(pickupPointMapper.selectById(1L)).thenReturn(new PickupPoint());
        when(leaderMapper.selectByPointId(1L)).thenReturn(leader(9L, 1L));

        BizException ex = assertThrows(BizException.class,
                () -> leaderService.bindPoint(3L, req(1L)));
        assertEquals(10016, ex.getCode());
        verify(leaderMapper, never()).bindPoint(anyLong(), anyLong());
    }

    @Test
    @DisplayName("#12 并发兜底： uk_point_id 唯一索引冲突 → 10016")
    void bindDuplicateKey() {
        when(leaderMapper.selectById(3L)).thenReturn(leader(3L, null));
        when(pickupPointMapper.selectById(1L)).thenReturn(new PickupPoint());
        when(leaderMapper.selectByPointId(1L)).thenReturn(null);
        doThrow(new DuplicateKeyException("uk_point_id"))
                .when(leaderMapper).bindPoint(3L, 1L);

        BizException ex = assertThrows(BizException.class,
                () -> leaderService.bindPoint(3L, req(1L)));
        assertEquals(10016, ex.getCode());
    }

    @Test
    @DisplayName("#12 团长不存在 → 404")
    void bindLeaderNotFound() {
        when(leaderMapper.selectById(3L)).thenReturn(null);

        BizException ex = assertThrows(BizException.class,
                () -> leaderService.bindPoint(3L, req(1L)));
        assertEquals(404, ex.getCode());
    }

    @Test
    @DisplayName("#12 自提点不存在 → 404")
    void bindPointNotFound() {
        when(leaderMapper.selectById(3L)).thenReturn(leader(3L, null));
        when(pickupPointMapper.selectById(1L)).thenReturn(null);

        BizException ex = assertThrows(BizException.class,
                () -> leaderService.bindPoint(3L, req(1L)));
        assertEquals(404, ex.getCode());
    }

    @Test
    @DisplayName("#12 按 userId 查团长")
    void getByUserId() {
        Leader expected = leader(3L, 1L);
        when(leaderMapper.selectByUserId(103L)).thenReturn(expected);
        assertEquals(expected, leaderService.getByUserId(103L));
    }
}
