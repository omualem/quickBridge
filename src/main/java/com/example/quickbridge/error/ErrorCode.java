package com.example.quickbridge.error;

import org.springframework.http.HttpStatus;

/**
 * Stable, machine-readable error codes returned in the JSON error envelope.
 *
 * Each code carries the HTTP status it maps to so the
 * {@code GlobalExceptionHandler} can respond consistently.
 */
public enum ErrorCode {

    INVALID_CODE(HttpStatus.BAD_REQUEST, "Session code is invalid"),
    SESSION_NOT_FOUND(HttpStatus.NOT_FOUND, "Session not found or expired"),
    FILE_NOT_FOUND(HttpStatus.NOT_FOUND, "File not found"),
    FILE_TOO_LARGE(HttpStatus.PAYLOAD_TOO_LARGE, "File exceeds the maximum allowed size"),
    TOO_MANY_FILES(HttpStatus.BAD_REQUEST, "Session has reached the maximum number of files"),
    TEXT_TOO_LONG(HttpStatus.BAD_REQUEST, "Text exceeds the maximum allowed length"),
    EMPTY_FILE(HttpStatus.BAD_REQUEST, "Uploaded file is empty"),
    STORAGE_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to store or read the file"),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected server error");

    private final HttpStatus status;
    private final String defaultMessage;

    ErrorCode(HttpStatus status, String defaultMessage) {
        this.status = status;
        this.defaultMessage = defaultMessage;
    }

    public HttpStatus status() {
        return status;
    }

    public String defaultMessage() {
        return defaultMessage;
    }
}
