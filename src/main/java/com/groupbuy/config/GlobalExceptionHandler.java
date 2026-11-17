package com.groupbuy.config;

import com.groupbuy.common.BizException;
import com.groupbuy.common.ErrorCode;
import com.groupbuy.common.R;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

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

    /**
     * JSON 反序列化失败：时间格式写错、字段类型不对等。
     * 这类是调用方的锅，返回 400 + 明确提示，别让前端看到 500（联调时能省一堆时间）。
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public R<Void> handleNotReadable(HttpMessageNotReadableException e) {
        log.warn("[参数解析失败] {}", e.getMessage());
        return R.fail(ErrorCode.PARAM_ERROR.getCode(),
                "请求参数格式不正确，时间请用 yyyy-MM-dd HH:mm:ss");
    }

    /**
     * 路径不存在（接口还没实现，或前端把 URL 拼错了）。
     * 不接这个的话会被下面的兜底捕获成 500，联调时极容易误判成"服务炸了"。
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public R<Void> handleNoResource(NoResourceFoundException e) {
        log.warn("[接口不存在] {} {}", e.getHttpMethod(), e.getResourcePath());
        return R.fail(ErrorCode.NOT_FOUND.getCode(),
                "接口不存在：" + e.getHttpMethod() + " /" + e.getResourcePath());
    }

    /** 兜底：未知异常，打日志，对外只给通用提示 */
    @ExceptionHandler(Exception.class)
    public R<Void> handleException(Exception e) {
        log.error("[系统异常]", e);
        return R.fail(ErrorCode.SYSTEM_ERROR.getCode(), ErrorCode.SYSTEM_ERROR.getMsg());
    }
}
