package com.campus.cloudisk.exception;

import com.campus.cloudisk.common.ErrorCode;
import lombok.Getter;

/**
 * 业务异常类
 * <p>
 * 用于在 Service 层抛出可预期的业务错误，由全局异常处理器统一捕获并转换为 {@link com.campus.cloudisk.common.Result}。
 * <br/>
 * 与 {@link RuntimeException} 的区别：
 * <ul>
 *   <li>BusinessException 表示"业务规则不满足"，如文件不存在、存储空间不足等</li>
 *   <li>RuntimeException 表示"代码逻辑错误"，如空指针、数组越界等</li>
 * </ul>
 * </p>
 *
 * 使用示例：
 * <pre>
 *   // 使用错误码枚举（推荐）
 *   throw new BusinessException(ErrorCode.FILE_NOT_FOUND);
 *
 *   // 使用错误码枚举 + 自定义消息（更具体的提示）
 *   throw new BusinessException(ErrorCode.FILE_NOT_FOUND, "文件 " + fileName + " 不存在");
 *
 *   // 使用自定义 code + message（临时场景）
 *   throw new BusinessException(400, "操作太频繁，请稍后再试");
 * </pre>
 *
 * @author campus-cloud
 * @since 2024-01-01
 */
@Getter
public class BusinessException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /** 业务错误码（对应 HTTP 响应体中的 code 字段） */
    private final int code;

    /** 错误描述（对应 HTTP 响应体中的 message 字段） */
    private final String message;

    // ===================================================================
    //  构造方法
    // ===================================================================

    /**
     * 通过错误码枚举构造（推荐方式）
     *
     * @param errorCode 错误码枚举
     */
    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.code    = errorCode.getCode();
        this.message = errorCode.getMessage();
    }

    /**
     * 通过错误码枚举 + 自定义消息构造
     * <p>当枚举的默认消息不够具体时使用，如提示具体文件名</p>
     *
     * @param errorCode 错误码枚举（提供 code）
     * @param message   自定义错误描述（覆盖枚举默认 message）
     */
    public BusinessException(ErrorCode errorCode, String message) {
        super(message);
        this.code    = errorCode.getCode();
        this.message = message;
    }

    /**
     * 通过自定义 code + message 构造
     *
     * @param code    错误码
     * @param message 错误描述
     */
    public BusinessException(int code, String message) {
        super(message);
        this.code    = code;
        this.message = message;
    }

    /**
     * 通过自定义 message 构造（code 固定为 400）
     *
     * @param message 错误描述
     */
    public BusinessException(String message) {
        super(message);
        this.code    = 400;
        this.message = message;
    }
}
