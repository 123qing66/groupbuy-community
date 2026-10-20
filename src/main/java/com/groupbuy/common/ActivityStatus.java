package com.groupbuy.common;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * 活动状态机（A 负责，对应 Issue #7）。
 *
 * <p>显式转移表：只允许表里的流转，其余一律抛
 * {@link ErrorCode#ACTIVITY_STATUS_INVALID}（10014），
 * 避免"已失败的活动又被置成已成团"这类脏数据。</p>
 *
 * <pre>
 *   0 待开始 ──到点──▶ 1 进行中 ──人数达标──▶ 2 已成团（终态）
 *                        │
 *                        └──到期未达标──▶ 3 已失败
 *   1 / 2 ──管理员下架──▶ 4 已下架
 * </pre>
 */
@Getter
@AllArgsConstructor
public enum ActivityStatus {

    WAIT_START(0, "待开始"),
    ONGOING(1, "进行中"),
    GROUP_SUCCESS(2, "已成团"),
    GROUP_FAILED(3, "已失败"),
    OFFLINE(4, "已下架"),
    ;

    private final int code;
    private final String desc;

    /** 允许的转移：key = 当前状态 */
    private static final Map<ActivityStatus, Set<ActivityStatus>> TRANSFER = new HashMap<>();

    static {
        TRANSFER.put(WAIT_START, Set.of(ONGOING, OFFLINE));
        TRANSFER.put(ONGOING, Set.of(GROUP_SUCCESS, GROUP_FAILED, OFFLINE));
        TRANSFER.put(GROUP_SUCCESS, Set.of(OFFLINE));
        // 已失败、已下架都是终态，不可再变
        TRANSFER.put(GROUP_FAILED, Set.of());
        TRANSFER.put(OFFLINE, Set.of());
    }

    public static ActivityStatus of(Integer code) {
        if (code == null) {
            return null;
        }
        for (ActivityStatus s : values()) {
            if (s.code == code) {
                return s;
            }
        }
        throw new BizException(ErrorCode.PARAM_ERROR, "未知的活动状态 " + code);
    }

    /** 能否从当前状态流转到目标状态 */
    public boolean canTransferTo(ActivityStatus target) {
        if (target == null) {
            return false;
        }
        return TRANSFER.getOrDefault(this, Set.of()).contains(target);
    }

    /** 校验，非法流转直接抛业务异常 */
    public void assertTransferTo(ActivityStatus target) {
        if (!canTransferTo(target)) {
            throw new BizException(ErrorCode.ACTIVITY_STATUS_INVALID,
                    this.desc + " → " + (target == null ? "null" : target.desc));
        }
    }
}
