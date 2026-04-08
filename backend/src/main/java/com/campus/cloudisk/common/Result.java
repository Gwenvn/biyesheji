package com.campus.cloudisk.common;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;

import java.io.Serializable;

/**
 * 统一 API 响应结构
 * <p>
 * 所有 Controller 返回值统一使用此类包装，前端通过 code 字段判断请求是否成功。
 * <br/>
 * 约定：
 * <ul>
 *   <li>200  — 成功</li>
 *   <li>400  — 请求参数错误（业务异常）</li>
 *   <li>401  — 未登录 / Token 失效</li>
 *   <li>403  — 无权限</li>
 *   <li>404  — 资源不存在</li>
 *   <li>500  — 服务器内部错误</li>
 * </ul>
 * </p>
 *
 * 使用示例：
 * <pre>
 *   // 成功（无数据）
 *   return Result.success();
 *
 *   // 成功（带数据）
 *   return Result.success(userDTO);
 *
 *   // 成功（自定义消息）
 *   return Result.success("上传成功", fileInfo);
 *
 *   // 失败（使用错误码枚举）
 *   return Result.fail(ErrorCode.FILE_NOT_FOUND);
 *
 *   // 失败（自定义消息）
 *   return Result.fail(404, "文件不存在");
 * </pre>
 *
 * @param <T> data 字段的数据类型
 * @author campus-cloud
 * @since 2024-01-01
 */
@Getter
// data 为 null 时不序列化到 JSON（减少响应体大小）
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Result<T> implements Serializable {

    private static final long serialVersionUID = 1L;

    /** HTTP 业务状态码（200=成功，其他见 ErrorCode） */
    private final int code;

    /** 提示信息（成功时为 "success"，失败时为错误描述） */
    private final String message;

    /** 响应数据（失败时为 null，成功时为业务数据） */
    private final T data;

    // ===== 私有构造方法，通过静态工厂方法创建 =====
    private Result(int code, String message, T data) {
        this.code    = code;
        this.message = message;
        this.data    = data;
    }

    // ===================================================================
    //  成功响应工厂方法
    // ===================================================================

    /**
     * 成功（无数据）
     * 适用场景：删除、更新等不需要返回数据的操作
     */
    public static Result<Void> success() {
        return new Result<>(200, "success", null);
    }

    /**
     * 成功（带数据）
     * 适用场景：查询、创建等需要返回业务数据的操作
     *
     * @param data 响应数据
     */
    public static <T> Result<T> success(T data) {
        return new Result<>(200, "success", data);
    }

    /**
     * 成功（自定义消息 + 数据）
     * 适用场景：需要提示具体操作结果的场景（如"上传成功，共3个文件"）
     *
     * @param message 提示消息
     * @param data    响应数据
     */
    public static <T> Result<T> success(String message, T data) {
        return new Result<>(200, message, data);
    }

    // ===================================================================
    //  失败响应工厂方法
    // ===================================================================

    /**
     * 失败（使用错误码枚举，推荐方式）
     * 适用场景：已定义错误码的标准业务异常
     *
     * @param errorCode 错误码枚举（来自 {@link ErrorCode}）
     */
    public static <T> Result<T> fail(ErrorCode errorCode) {
        return new Result<>(errorCode.getCode(), errorCode.getMessage(), null);
    }

    /**
     * 失败（使用错误码枚举 + 自定义消息覆盖）
     * 适用场景：标准错误码但需要更具体的提示（如"文件 xxx.pdf 不存在"）
     *
     * @param errorCode 错误码枚举
     * @param message   自定义消息（覆盖枚举默认消息）
     */
    public static <T> Result<T> fail(ErrorCode errorCode, String message) {
        return new Result<>(errorCode.getCode(), message, null);
    }

    /**
     * 失败（自定义 code + message）
     * 适用场景：无对应枚举的临时错误
     *
     * @param code    错误码
     * @param message 错误描述
     */
    public static <T> Result<T> fail(int code, String message) {
        return new Result<>(code, message, null);
    }

    /**
     * 失败（仅消息，code 固定为 500）
     * 适用场景：通用服务端错误
     *
     * @param message 错误描述
     */
    public static <T> Result<T> fail(String message) {
        return new Result<>(500, message, null);
    }

    // ===================================================================
    //  辅助判断方法
    // ===================================================================

    /**
     * 判断请求是否成功（code == 200）
     * 适用于 Feign 调用结果判断
     */
    public boolean isSuccess() {
        return this.code == 200;
    }

    @Override
    public String toString() {
        return "Result{code=" + code + ", message='" + message + "', data=" + data + '}';
    }
}
