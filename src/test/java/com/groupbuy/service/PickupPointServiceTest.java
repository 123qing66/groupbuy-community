package com.groupbuy.service;

import com.groupbuy.common.BizException;
import com.groupbuy.dto.PickupPointSaveRequest;
import com.groupbuy.dto.PickupPointVO;
import com.groupbuy.entity.PickupPoint;
import com.groupbuy.mapper.PickupPointMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 自提点服务单测（A 负责，接口 #11）。纯 Mockito，不连数据库。
 */
@ExtendWith(MockitoExtension.class)
class PickupPointServiceTest {

    @Mock private PickupPointMapper pickupPointMapper;

    @InjectMocks private PickupPointService pickupPointService;

    private PickupPoint point(Long id, String name, int status) {
        PickupPoint p = new PickupPoint();
        p.setId(id);
        p.setName(name);
        p.setAddress("阳光路 1 号");
        p.setContact("王团长");
        p.setPhone("13900139000");
        p.setStatus(status);
        return p;
    }

    @Test
    @DisplayName("#11 列表：keyword/status 透传，实体转 VO")
    void list() {
        when(pickupPointMapper.selectList("阳光", 1))
                .thenReturn(List.of(point(1L, "阳光小区自提点", 1), point(2L, "阳光花园自提点", 1)));

        List<PickupPointVO> list = pickupPointService.list("阳光", 1);

        assertEquals(2, list.size());
        assertEquals("阳光小区自提点", list.get(0).getName());
        assertEquals("阳光路 1 号", list.get(0).getAddress());
        assertEquals(1, list.get(0).getStatus());
    }

    @Test
    @DisplayName("#11 详情：查不到 → 404")
    void detailNotFound() {
        when(pickupPointMapper.selectById(99L)).thenReturn(null);

        BizException ex = assertThrows(BizException.class, () -> pickupPointService.detail(99L));
        assertEquals(404, ex.getCode());
    }

    @Test
    @DisplayName("#11 新增：status 不传默认 1（启用）")
    void createDefaultStatus() {
        PickupPointSaveRequest req = new PickupPointSaveRequest();
        req.setName("新建自提点");
        req.setAddress("幸福路 2 号");

        pickupPointService.create(req);

        ArgumentCaptor<PickupPoint> cap = ArgumentCaptor.forClass(PickupPoint.class);
        verify(pickupPointMapper).insert(cap.capture());
        assertEquals("新建自提点", cap.getValue().getName());
        assertEquals(1, cap.getValue().getStatus());
    }

    @Test
    @DisplayName("#11 新增：显式传 status 按传入值")
    void createExplicitStatus() {
        PickupPointSaveRequest req = new PickupPointSaveRequest();
        req.setName("停用创建");
        req.setStatus(0);

        pickupPointService.create(req);

        ArgumentCaptor<PickupPoint> cap = ArgumentCaptor.forClass(PickupPoint.class);
        verify(pickupPointMapper).insert(cap.capture());
        assertEquals(0, cap.getValue().getStatus());
    }

    @Test
    @DisplayName("#11 修改：id 与字段一起透传")
    void update() {
        PickupPointSaveRequest req = new PickupPointSaveRequest();
        req.setName("改名");
        req.setAddress("新地址");
        req.setStatus(0);

        pickupPointService.update(7L, req);

        ArgumentCaptor<PickupPoint> cap = ArgumentCaptor.forClass(PickupPoint.class);
        verify(pickupPointMapper).update(cap.capture());
        assertEquals(7L, cap.getValue().getId());
        assertEquals("改名", cap.getValue().getName());
        assertEquals(0, cap.getValue().getStatus());
    }

    @Test
    @DisplayName("#11 停用：走逻辑删除 disable")
    void disable() {
        pickupPointService.disable(7L);
        verify(pickupPointMapper).disable(7L);
    }
}
