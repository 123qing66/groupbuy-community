package com.groupbuy.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 核销出参（接口 #10，C 负责，一次性幂等）。
 *
 * <p>幂等靠一条带条件的 UPDATE：{@code WHERE code=? AND status=0}，
 * 影响行数为 0 即已核销或码无效，返回 10010，不产生二次副作用。</p>
 */
@Data
public class VerifyResultVO {

    private String code;

    private String orderNo;

    private String goodsName;

    private Integer num;

    private LocalDateTime verifyTime;

    /** 核销人（团长）id */
    private Long verifierId;
}
