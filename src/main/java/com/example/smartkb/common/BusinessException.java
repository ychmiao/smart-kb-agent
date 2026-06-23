package com.example.smartkb.common;

import lombok.Getter;

/**
 * 业务异常 —— 携带业务错误码，由 GlobalExceptionHandler 统一处理。
 * <p>
 * 错误码按模块划分（40xxx 参数/权限类，50xxx 服务端类），
 * 集中定义于 {@link ErrorCode}。
 */
@Getter
public class BusinessException extends RuntimeException {

    /** 业务错误码 */
    private final int code;

    public BusinessException(int code, String message) {
        super(message);
        this.code = code;
    }

    public BusinessException(int code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }
}

