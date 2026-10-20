package com.groupbuy.common;

import lombok.Getter;

/**
 * 业务异常：主动抛出、会被全局处理器转成 {@link R}，不打堆栈、不报警。
 */
@Getter
public class BizException extends RuntimeException {

    private final int code;

    public BizException(ErrorCode errorCode) {
        super(errorCode.getMsg());
        this.code = errorCode.getCode();
    }

    public BizException(ErrorCode errorCode, String detail) {
        super(errorCode.getMsg() + "：" + detail);
        this.code = errorCode.getCode();
    }

    public BizException(int code, String msg) {
        super(msg);
        this.code = code;
    }
}
