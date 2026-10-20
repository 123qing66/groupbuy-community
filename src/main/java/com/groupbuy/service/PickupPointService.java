package com.groupbuy.service;

import com.groupbuy.common.BizException;
import com.groupbuy.common.ErrorCode;
import com.groupbuy.dto.PickupPointSaveRequest;
import com.groupbuy.dto.PickupPointVO;
import com.groupbuy.entity.PickupPoint;
import com.groupbuy.mapper.PickupPointMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 自提点服务（A 负责，接口 #11）。
 *
 * <p>删除一律逻辑删除（status 置 0），因为订单与核销码都引用了 point_id。</p>
 */
@Service
@RequiredArgsConstructor
public class PickupPointService {

    private final PickupPointMapper pickupPointMapper;

    public List<PickupPointVO> list(String keyword, Integer status) {
        List<PickupPoint> list = pickupPointMapper.selectList(keyword, status);
        List<PickupPointVO> voList = new ArrayList<>(list.size());
        for (PickupPoint p : list) {
            voList.add(toVO(p));
        }
        return voList;
    }

    public PickupPointVO detail(Long id) {
        PickupPoint p = pickupPointMapper.selectById(id);
        if (p == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "自提点不存在");
        }
        return toVO(p);
    }

    public Long create(PickupPointSaveRequest req) {
        PickupPoint p = new PickupPoint();
        p.setName(req.getName());
        p.setAddress(req.getAddress());
        p.setContact(req.getContact());
        p.setPhone(req.getPhone());
        p.setStatus(req.getStatus() == null ? 1 : req.getStatus());
        pickupPointMapper.insert(p);
        return p.getId();
    }

    public void update(Long id, PickupPointSaveRequest req) {
        PickupPoint p = new PickupPoint();
        p.setId(id);
        p.setName(req.getName());
        p.setAddress(req.getAddress());
        p.setContact(req.getContact());
        p.setPhone(req.getPhone());
        p.setStatus(req.getStatus());
        pickupPointMapper.update(p);
    }

    /** 停用（逻辑删除） */
    public void disable(Long id) {
        pickupPointMapper.disable(id);
    }

    private PickupPointVO toVO(PickupPoint p) {
        PickupPointVO vo = new PickupPointVO();
        vo.setId(p.getId());
        vo.setName(p.getName());
        vo.setAddress(p.getAddress());
        vo.setContact(p.getContact());
        vo.setPhone(p.getPhone());
        vo.setStatus(p.getStatus());
        vo.setCreateTime(p.getCreateTime());
        return vo;
    }
}
