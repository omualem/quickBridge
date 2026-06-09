package com.example.quickbridge.controller;

/**
 * Standard success envelope: {@code { "success": true, "data": ... }}.
 *
 * Error responses use a different shape produced by
 * {@code GlobalExceptionHandler}.
 */
public record ApiResponse<T>(boolean success, T data) {

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, data);
    }
}
