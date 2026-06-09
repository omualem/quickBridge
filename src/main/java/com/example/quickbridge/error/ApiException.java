package com.example.quickbridge.error;

/**
 * Application-level exception carrying a stable {@link ErrorCode}.
 *
 * Thrown by the service/controller layers and translated into the standard JSON
 * error envelope by {@code GlobalExceptionHandler}.
 */
public class ApiException extends RuntimeException {

    private final ErrorCode errorCode;

    public ApiException(ErrorCode errorCode) {
        this(errorCode, errorCode.defaultMessage());
    }

    public ApiException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }
}
