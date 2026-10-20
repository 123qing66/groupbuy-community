package com.groupbuy.common;

import com.groupbuy.common.ErrorCode;
import lombok.Data;

import java.io.Serializable;

/**
 * 统一响应体。全组所有 Controller 必须返回这个类型。
 *
 * <pre>
 * {
 *   "code": 0,
 *   "msg": "ok",
 *   "data": { ... }
 * }
 * </pre>
 */
@Data
public class R<T> implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 0 表示成功，非 0 见 {@link ErrorCode} */
    private int code;

    private String msg;

    private T data;

    public static <T> R<T> ok() {
        return ok(null);
    }

    public static <T> R<T> ok(T data) {
        R<T> r = new R<>();
        r.setCode(ErrorCode.SUCCESS.getCode());
        r.setMsg(ErrorCode.SUCCESS.getMsg());
        r.setData(data);
        return r;
    }

    public static <T> R<T> fail(ErrorCode errorCode) {
        return fail(errorCode.getCode(), errorCode.getMsg());
    }

    public static <T> R<T> fail(ErrorCode errorCode, String detail) {
        return fail(errorCode.getCode(), errorCode.getMsg() + "：" + detail);
    }

    public static <T> R<T> fail(int code, String msg) {
        R<T> r = new R<>();
        r.setCode(code);
        r.setMsg(msg);
        return r;
    }

    public boolean isSuccess() {
        return this.code == ErrorCode.SUCCESS.getCode();
    }
}
