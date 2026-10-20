package com.groupbuy.common;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 全局错误码。新增错误码统一加在这里，别散落在各人代码里。
 *
 * <p>约定：0 成功；4xx 沿用 HTTP 语义；1xxxx 为业务错误码。</p>
 */
@Getter
@AllArgsConstructor
public enum ErrorCode {

    SUCCESS(0, "ok"),

    PARAM_ERROR(400, "参数错误"),
    UNAUTHORIZED(401, "未登录或登录已过期"),
    FORBIDDEN(403, "无权限访问"),
    NOT_FOUND(404, "资源不存在"),
    TOO_MANY_REQUESTS(429, "请求过于频繁，请稍后再试"),
    SYSTEM_ERROR(500, "系统繁忙，请稍后再试"),

    /* ===== 业务错误码（各人按需补充） ===== */
    ACTIVITY_NOT_FOUND(10001, "团购活动不存在"),
    ACTIVITY_NOT_ONGOING(10002, "活动未在进行中"),
    STOCK_NOT_ENOUGH(10003, "库存不足"),
    PURCHASE_LIMIT_EXCEEDED(10004, "超出限购数量"),
    ORDER_NOT_FOUND(10005, "订单不存在"),
    ORDER_STATUS_INVALID(10006, "订单状态不合法"),
    PAYMENT_SIGN_INVALID(10007, "支付回调验签失败"),
    GROUP_NOT_FULL(10008, "拼团人数未达标"),
    PICKUP_CODE_INVALID(10009, "核销码无效"),
    PICKUP_CODE_USED(10010, "核销码已使用"),
    REFUND_FAILED(10011, "退款失败"),
    USER_ALREADY_EXISTS(10012, "用户名已存在"),
    BAD_CREDENTIALS(10013, "用户名或密码错误"),
    ACTIVITY_STATUS_INVALID(10014, "活动状态流转非法"),
    ORDER_ALREADY_EXISTS(10015, "订单已存在，请勿重复下单"),
    POINT_ALREADY_BOUND(10016, "该自提点已被其他团长绑定"),
    ;

    private final int code;
    private final String msg;
}
