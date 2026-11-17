package com.groupbuy.service;

import com.groupbuy.common.BizException;
import com.groupbuy.common.ErrorCode;
import com.groupbuy.common.OrderStatus;
import com.groupbuy.dto.VerifyResultVO;
import com.groupbuy.entity.Order;
import com.groupbuy.entity.PickupCode;
import com.groupbuy.mapper.OrderMapper;
import com.groupbuy.mapper.PickupVerifyMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 核销服务（C 负责，#21，一次性幂等）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PickupVerifyService {

    private final PickupCodeService pickupCodeService;
    private final PickupVerifyMapper pickupVerifyMapper;
    private final OrderMapper orderMapper;

    public VerifyResultVO verify(String code, Long verifierId) {
        // ① 查码（用 A 的 PickupCodeService），不存在报 10009
        PickupCode pc = pickupCodeService.getByCode(code);
        if (pc == null) {
            throw new BizException(ErrorCode.PICKUP_CODE_INVALID);
        }

        // ② CAS 核销：WHERE status=0，影响行数 0 = 已核销
        int rows = pickupVerifyMapper.verify(code, verifierId);
        if (rows == 0) {
            throw new BizException(ErrorCode.PICKUP_CODE_USED);
        }

        // ③ 核销成功：订单 2 → 3（已提货）
        orderMapper.updateStatus(pc.getOrderNo(),
                OrderStatus.GROUP_SUCCESS.getCode(), OrderStatus.PICKED_UP.getCode());

        // ④ 组装响应
        Order order = orderMapper.selectByOrderNo(pc.getOrderNo());
        VerifyResultVO vo = new VerifyResultVO();
        vo.setCode(code);
        vo.setOrderNo(pc.getOrderNo());
        vo.setGoodsName(orderMapper.selectGoodsNameByOrderNo(pc.getOrderNo()));
        vo.setNum(order == null ? null : order.getNum());
        vo.setVerifyTime(LocalDateTime.now());
        vo.setVerifierId(verifierId);

        log.info("核销成功 code={}, orderNo={}", code, pc.getOrderNo());
        return vo;
    }
}
