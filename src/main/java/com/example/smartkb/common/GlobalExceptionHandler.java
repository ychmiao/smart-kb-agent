package com.example.smartkb.common;

import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.util.stream.Collectors;

/**
 * 全局异常处理器 —— 统一捕获并转换各类异常为 Result 响应格式。
 * <p>
 * 处理策略：
 * <ul>
 *   <li>业务异常（BusinessException）：直接返回错误码和消息</li>
 *   <li>参数校验异常（@Valid、@Validated）：提取字段错误信息</li>
 *   <li>上传超限、参数类型、请求体格式：返回清晰的客户端错误</li>
 *   <li>未预期异常：记录完整堆栈，返回通用"服务器内部错误"</li>
 * </ul>
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** 处理业务异常，直接透传错误码和业务描述 */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Result<Void>> handleBusinessException(BusinessException exception) {
        log.warn("Business exception: code={}, message={}", exception.getCode(), exception.getMessage());
        return ResponseEntity.badRequest()
                .body(Result.failure(exception.getCode(), exception.getMessage()));
    }

    /** 处理 @Valid 请求体验证失败，拼接所有字段错误 */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Result<Void>> handleMethodArgumentNotValidException(
            MethodArgumentNotValidException exception) {
        String message = exception.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .distinct()
                .collect(Collectors.joining("; "));
        return ResponseEntity.badRequest().body(Result.failure(ErrorCode.VALIDATION_FAILED, message));
    }

    /** 处理 @Validated 参数绑定失败（如 @RequestParam 校验） */
    @ExceptionHandler(BindException.class)
    public ResponseEntity<Result<Void>> handleBindException(BindException exception) {
        String message = exception.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .distinct()
                .collect(Collectors.joining("; "));
        return ResponseEntity.badRequest().body(Result.failure(ErrorCode.VALIDATION_FAILED, message));
    }

    /** 处理路径参数和查询参数的 ConstraintViolation 校验失败 */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Result<Void>> handleConstraintViolationException(
            ConstraintViolationException exception) {
        String message = exception.getConstraintViolations().stream()
                .map(violation -> violation.getMessage())
                .distinct()
                .collect(Collectors.joining("; "));
        return ResponseEntity.badRequest().body(Result.failure(ErrorCode.VALIDATION_FAILED, message));
    }

    /** 处理文件大小超限（全局配置 50MB） */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Result<Void>> handleMaxUploadSizeExceededException(
            MaxUploadSizeExceededException exception) {
        return ResponseEntity.badRequest().body(Result.failure(ErrorCode.FILE_TOO_LARGE, "文件大小不能超过 50MB"));
    }

    /** 处理路径参数类型转换失败（如 String → Long） */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Result<Void>> handleMethodArgumentTypeMismatchException(
            MethodArgumentTypeMismatchException exception) {
        return ResponseEntity.badRequest().body(Result.failure(ErrorCode.VALIDATION_FAILED,
                "参数格式不正确: " + exception.getName()));
    }

    /** 处理请求体为空或 JSON 格式错误 */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Result<Void>> handleHttpMessageNotReadableException(
            HttpMessageNotReadableException exception) {
        String detail = "请求体格式错误";
        if (exception.getCause() instanceof MismatchedInputException mie) {
            detail = "请求体 JSON 格式错误";
        }
        return ResponseEntity.badRequest().body(Result.failure(ErrorCode.VALIDATION_FAILED, detail));
    }

    /** 处理所有未捕获的运行时异常，返回通用服务器错误 */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result<Void>> handleException(Exception exception) {
        log.error("Unhandled exception", exception);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Result.failure(50000, "服务器内部错误"));
    }
}
