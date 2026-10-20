package com.groupbuy.config;

import com.groupbuy.common.BizException;
import com.groupbuy.common.ErrorCode;
import com.groupbuy.common.R;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

/**
 * 全局异常处理。三类：业务异常（不打堆栈）、参数校验异常、兜底系统异常（打堆栈）。
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** 业务异常 */
    @ExceptionHandler(BizException.class)
    public R<Void> handleBizException(BizException e) {
        return R.fail(e.getCode(), e.getMessage());
    }

    /** @RequestBody + @Valid 校验失败 */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public R<Void> handleValidException(MethodArgumentNotValidException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getField() + f.getDefaultMessage())
                .collect(Collectors.joining("; "));
        return R.fail(ErrorCode.PARAM_ERROR.getCode(), msg);
    }

    /** 表单绑定校验失败 */
    @ExceptionHandler(BindException.class)
    public R<Void> handleBindException(BindException e) {
        String msg = e.getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("; "));
        return R.fail(ErrorCode.PARAM_ERROR.getCode(), msg);
    }

    /** 兜底：未知异常，打日志，对外只给通用提示 */
    @ExceptionHandler(Exception.class)
    public R<Void> handleException(Exception e) {
        log.error("[系统异常]", e);
        return R.fail(ErrorCode.SYSTEM_ERROR.getCode(), ErrorCode.SYSTEM_ERROR.getMsg());
    }
}
