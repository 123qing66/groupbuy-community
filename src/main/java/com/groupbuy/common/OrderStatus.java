package com.groupbuy.common;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * 订单状态机（C 负责，对应 Issue #19）。
 *
 * <p>显式转移表：只允许表里的流转，其余一律抛
 * {@link ErrorCode#ORDER_STATUS_INVALID}（10006），
 * 避免"待支付直接变已提货"这类脏数据。</p>
 *
 * <pre>
 *   0 待支付 ──支付成功──▶ 1 已支付待成团 ──人数达标──▶ 2 已成团待提货 ──核销──▶ 3 已提货（终态）
 *       │                        │
 *       │                        └──到期未成团──▶ 5 已退款
 *       └──超时未支付──▶ 4 已取消（终态）
 * </pre>
 */
@Getter
@AllArgsConstructor
public enum OrderStatus {

    WAIT_PAY(0, "待支付"),
    PAID(1, "已支付待成团"),
    GROUP_SUCCESS(2, "已成团待提货"),
    PICKED_UP(3, "已提货"),
    CANCELED(4, "已取消"),
    REFUNDED(5, "已退款"),
    ;

    private final int code;
    private final String desc;

    /** 允许的转移：key = 当前状态 */
    private static final Map<OrderStatus, Set<OrderStatus>> TRANSFER = new HashMap<>();

    static {
        TRANSFER.put(WAIT_PAY, Set.of(PAID, CANCELED));
        TRANSFER.put(PAID, Set.of(GROUP_SUCCESS, REFUNDED));
        TRANSFER.put(GROUP_SUCCESS, Set.of(PICKED_UP));
        // 已提货、已取消、已退款都是终态，不可再变
        TRANSFER.put(PICKED_UP, Set.of());
        TRANSFER.put(CANCELED, Set.of());
        TRANSFER.put(REFUNDED, Set.of());
    }

    public static OrderStatus of(Integer code) {
        if (code == null) {
            return null;
        }
        for (OrderStatus s : values()) {
            if (s.code == code) {
                return s;
            }
        }
        throw new BizException(ErrorCode.PARAM_ERROR, "未知的订单状态 " + code);
    }

    /** 能否从当前状态流转到目标状态 */
    public boolean canTransferTo(OrderStatus target) {
        if (target == null) {
            return false;
        }
        return TRANSFER.getOrDefault(this, Set.of()).contains(target);
    }

    /** 校验，非法流转直接抛业务异常 */
    public void assertTransferTo(OrderStatus target) {
        if (!canTransferTo(target)) {
            throw new BizException(ErrorCode.ORDER_STATUS_INVALID,
                    this.desc + " → " + (target == null ? "null" : target.desc));
        }
    }
}
