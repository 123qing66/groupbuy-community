package com.groupbuy.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 提货核销码（对应 pickup_code 表，A 生成、C 核销）。
 *
 * <p>生成时机：成团判定成功时（活动 status → 2），由 C 调
 * {@code PickupCodeService.generateBatch(...)} 批量生成，一单一码。</p>
 */
@Data
public class PickupCode {

    private Long id;

    /** 核销码，格式 PCyyyyMMdd + 8 位随机字符 */
    private String code;

    private Long orderId;

    private String orderNo;

    private Long userId;

    private Long pointId;

    /** 0未核销 1已核销 */
    private Integer status;

    private LocalDateTime verifyTime;

    /** 核销人（团长）id */
    private Long verifierId;

    private LocalDateTime createTime;
}
