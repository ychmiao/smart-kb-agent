package com.example.smartkb.common;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.Instant;

/**
 * 统一 API 响应模型。
 * <p>
 * 所有普通 HTTP 接口统一返回此类型，包含业务状态码、提示信息和数据。
 * SSE 流式接口不经过此包装，直接使用 text/event-stream 协议。
 *
 * @param <T> 响应数据类型
 */
@Getter
@AllArgsConstructor
public class Result<T> {

    /** 业务状态码，0 表示成功 */
    private final int code;
    /** 提示信息 */
    private final String message;
    /** 响应数据 */
    private final T data;
    /** 响应时间戳 */
    private final Instant timestamp;

    /** 返回成功响应，无数据体 */
    public static <T> Result<T> success() {
        return success(null);
    }

    /** 返回成功响应，附带数据 */
    public static <T> Result<T> success(T data) {
        return new Result<>(0, "success", data, Instant.now());
    }

    /** 返回失败响应，含错误码和描述 */
    public static <T> Result<T> failure(int code, String message) {
        return new Result<>(code, message, null, Instant.now());
    }
}

