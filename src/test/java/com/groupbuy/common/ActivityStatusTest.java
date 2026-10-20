package com.groupbuy.common;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 活动状态机单测（A 负责，对应 Issue #7 + #26）。
 *
 * <p>纯逻辑，不依赖数据库和 Redis，CI 里一定能跑。</p>
 */
class ActivityStatusTest {

    @Test
    @DisplayName("待开始 → 进行中：允许")
    void waitStartToOngoing() {
        assertTrue(ActivityStatus.WAIT_START.canTransferTo(ActivityStatus.ONGOING));
    }

    @Test
    @DisplayName("进行中 → 已成团 / 已失败 / 已下架：允许")
    void ongoingTransfers() {
        assertTrue(ActivityStatus.ONGOING.canTransferTo(ActivityStatus.GROUP_SUCCESS));
        assertTrue(ActivityStatus.ONGOING.canTransferTo(ActivityStatus.GROUP_FAILED));
        assertTrue(ActivityStatus.ONGOING.canTransferTo(ActivityStatus.OFFLINE));
    }

    @Test
    @DisplayName("已成团是终态，不能再变回进行中（脏数据防线）")
    void successIsFinal() {
        assertFalse(ActivityStatus.GROUP_SUCCESS.canTransferTo(ActivityStatus.ONGOING));
        assertFalse(ActivityStatus.GROUP_FAILED.canTransferTo(ActivityStatus.GROUP_SUCCESS));
        assertFalse(ActivityStatus.OFFLINE.canTransferTo(ActivityStatus.ONGOING));
    }

    @Test
    @DisplayName("待开始不能直接跳到已成团")
    void waitStartCannotJumpToSuccess() {
        assertFalse(ActivityStatus.WAIT_START.canTransferTo(ActivityStatus.GROUP_SUCCESS));
    }

    @Test
    @DisplayName("非法流转抛 10014")
    void illegalTransferThrows() {
        BizException ex = assertThrows(BizException.class,
                () -> ActivityStatus.GROUP_SUCCESS.assertTransferTo(ActivityStatus.ONGOING));
        assertTrue(ex.getMessage().contains("10014") || ex.getMessage().contains("流转非法"));
    }

    @Test
    @DisplayName("按 code 反查枚举")
    void ofCode() {
        assertTrue(ActivityStatus.of(1) == ActivityStatus.ONGOING);
        assertTrue(ActivityStatus.of(4) == ActivityStatus.OFFLINE);
    }
}
