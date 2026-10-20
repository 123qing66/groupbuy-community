package com.groupbuy.common;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 骨架冒烟测试：不依赖 Spring 上下文和数据库，保证 CI 能跑绿。
 *
 * <p>后面各人写测试时，模块单测放 src/test/java/com/groupbuy/模块名/ 下，命名 XxxTest。</p>
 */
class RTest {

    @Test
    void ok_shouldReturnCodeZero() {
        R<String> r = R.ok("hello");
        assertEquals(0, r.getCode());
        assertEquals("hello", r.getData());
        assertTrue(r.isSuccess());
    }

    @Test
    void fail_shouldReturnErrorCode() {
        R<Void> r = R.fail(ErrorCode.STOCK_NOT_ENOUGH);
        assertEquals(ErrorCode.STOCK_NOT_ENOUGH.getCode(), r.getCode());
        assertEquals("库存不足", r.getMsg());
        assertFalse(r.isSuccess());
    }

    @Test
    void bizException_shouldCarryCode() {
        BizException e = new BizException(ErrorCode.PARAM_ERROR);
        assertEquals(400, e.getCode());
        assertEquals("参数错误", e.getMessage());
    }
}
