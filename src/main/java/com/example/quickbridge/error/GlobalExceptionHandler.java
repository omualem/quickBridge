package com.example.quickbridge.error;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

/**
 * Translates exceptions into the project's standard JSON error envelope:
 *
 * <pre>
 * { "success": false, "error": { "code": "SESSION_NOT_FOUND", "message": "..." } }
 * </pre>
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<Object> handleApiException(ApiException ex) {
        return build(ex.getErrorCode(), ex.getMessage());
    }

    /**
     * Spring's multipart layer rejects oversize uploads before our controller
     * runs, so map that to the same clear FILE_TOO_LARGE response.
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Object> handleMaxUpload(MaxUploadSizeExceededException ex) {
        return build(ErrorCode.FILE_TOO_LARGE, ErrorCode.FILE_TOO_LARGE.defaultMessage());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Object> handleUnexpected(Exception ex) {
        return build(ErrorCode.INTERNAL_ERROR, ErrorCode.INTERNAL_ERROR.defaultMessage());
    }

    private ResponseEntity<Object> build(ErrorCode code, String message) {
        Map<String, Object> body = Map.of(
                "success", false,
                "error", Map.of(
                        "code", code.name(),
                        "message", message));
        return ResponseEntity.status(code.status()).body(body);
    }
}
