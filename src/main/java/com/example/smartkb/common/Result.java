package com.example.smartkb.common;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.Instant;

@Getter
@AllArgsConstructor
public class Result<T> {

    private final int code;
    private final String message;
    private final T data;
    private final Instant timestamp;

    public static <T> Result<T> success() {
        return success(null);
    }

    public static <T> Result<T> success(T data) {
        return new Result<>(0, "success", data, Instant.now());
    }

    public static <T> Result<T> failure(int code, String message) {
        return new Result<>(code, message, null, Instant.now());
    }
}

