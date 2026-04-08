package com.campus.cloudisk.exception;

import com.campus.cloudisk.common.ErrorCode;
import com.campus.cloudisk.common.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.NoHandlerFoundException;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 全局异常处理器
 * <p>
 * 使用 {@code @RestControllerAdvice} 拦截所有 Controller 层抛出的异常，
 * 统一转换为 {@link Result} 格式返回给前端，避免框架默认的错误页面或 HTML 响应。
 * <br/>
 * 处理优先级（从高到低）：
 * <ol>
 *   <li>{@link BusinessException} — 业务异常，返回自定义 code + message</li>
 *   <li>Spring 参数校验异常（{@link MethodArgumentNotValidException} 等）</li>
 *   <li>HTTP 协议级别异常（404、405 等）</li>
 *   <li>文件上传异常（超过大小限制）</li>
 *   <li>{@link Exception} — 兜底处理，返回 500</li>
 * </ol>
 * </p>
 *
 * @author campus-cloud
 * @since 2024-01-01
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    // ===================================================================
    //  1. 业务异常（最常见，由 Service 层主动抛出）
    // ===================================================================

    /**
     * 捕获业务异常 {@link BusinessException}
     * <p>
     * 这类异常是可预期的，日志级别用 WARN 而非 ERROR，
     * 避免正常的"文件不存在"等场景污染错误日志。
     * </p>
     */
    @ExceptionHandler(BusinessException.class)
    public Result<Void> handleBusinessException(BusinessException e) {
        log.warn("[业务异常] code={}, message={}", e.getCode(), e.getMessage());
        return Result.fail(e.getCode(), e.getMessage());
    }

    // ===================================================================
    //  2. 参数校验异常（@Valid / @Validated 触发）
    // ===================================================================

    /**
     * 捕获 @RequestBody + @Valid 触发的参数校验失败异常
     * <p>
     * 将所有字段错误信息拼接成友好提示，如："username: 不能为空; email: 邮箱格式不正确"
     * </p>
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleMethodArgumentNotValidException(MethodArgumentNotValidException e) {
        String message = buildValidationMessage(e.getBindingResult().getFieldErrors());
        log.warn("[参数校验失败] {}", message);
        return Result.fail(ErrorCode.PARAM_ERROR, message);
    }

    /**
     * 捕获 @ModelAttribute 或表单绑定触发的参数校验失败
     */
    @ExceptionHandler(BindException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleBindException(BindException e) {
        String message = buildValidationMessage(e.getFieldErrors());
        log.warn("[绑定参数校验失败] {}", message);
        return Result.fail(ErrorCode.PARAM_ERROR, message);
    }

    /**
     * 捕获缺少必填请求参数（@RequestParam 未提供时）
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleMissingParams(MissingServletRequestParameterException e) {
        String message = "缺少必填参数：" + e.getParameterName();
        log.warn("[缺少请求参数] {}", message);
        return Result.fail(ErrorCode.PARAM_ERROR, message);
    }

    // ===================================================================
    //  3. HTTP 协议级别异常
    // ===================================================================

    /**
     * 捕获 404 Not Found（请求路径不存在）
     * <p>
     * 需要在 application.yml 中配置：
     * {@code spring.mvc.throw-exception-if-no-handler-found=true}
     * {@code spring.web.resources.add-mappings=false}
     * 否则 Spring 会返回默认的白页错误。
     * </p>
     */
    @ExceptionHandler(NoHandlerFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public Result<Void> handleNoHandlerFound(NoHandlerFoundException e) {
        log.warn("[404] 路径不存在: {} {}", e.getHttpMethod(), e.getRequestURL());
        return Result.fail(404, "接口路径不存在：" + e.getRequestURL());
    }

    /**
     * 捕获 405 Method Not Allowed（HTTP 方法不匹配）
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    @ResponseStatus(HttpStatus.METHOD_NOT_ALLOWED)
    public Result<Void> handleMethodNotSupported(HttpRequestMethodNotSupportedException e) {
        log.warn("[405] 不支持的请求方法: {}", e.getMethod());
        return Result.fail(405, "不支持 " + e.getMethod() + " 请求方法");
    }

    // ===================================================================
    //  4. 文件上传异常
    // ===================================================================

    /**
     * 捕获文件上传超出大小限制
     * <p>
     * 大小限制在 application.yml 中配置：
     * {@code spring.servlet.multipart.max-file-size=500MB}
     * </p>
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleMaxUploadSizeExceeded(MaxUploadSizeExceededException e) {
        log.warn("[文件过大] {}", e.getMessage());
        return Result.fail(ErrorCode.FILE_SIZE_EXCEEDED, "文件大小超出限制，最大支持 500MB");
    }

    // ===================================================================
    //  5. 兜底异常处理（所有未被上面捕获的异常）
    // ===================================================================

    /**
     * 捕获所有未预期的异常（兜底）
     * <p>
     * 此类异常通常是代码 Bug，需要重点关注，日志级别为 ERROR。
     * 返回给前端的是通用 500 错误，不暴露内部异常堆栈（安全考虑）。
     * </p>
     */
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Result<Void> handleException(Exception e) {
        // 打印完整堆栈，便于排查问题
        log.error("[未知异常] {}: {}", e.getClass().getName(), e.getMessage(), e);
        return Result.fail(ErrorCode.INTERNAL_ERROR);
    }

    // ===================================================================
    //  私有辅助方法
    // ===================================================================

    /**
     * 将字段校验错误列表拼接为可读的错误消息
     * <p>
     * 示例输出："username: 不能为空; email: 邮箱格式不正确"
     * </p>
     *
     * @param fieldErrors 字段错误列表
     * @return 拼接后的错误字符串
     */
    private String buildValidationMessage(List<FieldError> fieldErrors) {
        return fieldErrors.stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .collect(Collectors.joining("; "));
    }
}
