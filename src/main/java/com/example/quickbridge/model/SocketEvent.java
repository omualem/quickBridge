package com.example.quickbridge.model;

/**
 * Generic event broadcast to all clients subscribed to a session topic
 * ({@code /topic/sessions/{code}}).
 *
 * @param type    one of TEXT_UPDATED, FILE_ADDED, FILE_DELETED, SESSION_EXPIRED,
 *                ERROR
 * @param code    the session code the event belongs to
 * @param payload event-specific body (a text holder, file metadata, a file id,
 *                an error, or null)
 */
public record SocketEvent(String type, String code, Object payload) {

    public static final String TEXT_UPDATED = "TEXT_UPDATED";
    public static final String FILE_ADDED = "FILE_ADDED";
    public static final String FILE_DELETED = "FILE_DELETED";
    public static final String SESSION_EXPIRED = "SESSION_EXPIRED";
    public static final String ERROR = "ERROR";

    public static SocketEvent textUpdated(String code, String text) {
        return new SocketEvent(TEXT_UPDATED, code, new TextPayload(text));
    }

    public static SocketEvent fileAdded(String code, FileMetadata file) {
        return new SocketEvent(FILE_ADDED, code, file);
    }

    public static SocketEvent fileDeleted(String code, String fileId) {
        return new SocketEvent(FILE_DELETED, code, new FileIdPayload(fileId));
    }

    public static SocketEvent sessionExpired(String code) {
        return new SocketEvent(SESSION_EXPIRED, code, null);
    }

    public static SocketEvent error(String code, String errorCode, String message) {
        return new SocketEvent(ERROR, code, new ErrorPayload(errorCode, message));
    }

    /** Payload for TEXT_UPDATED events: { "text": "..." }. */
    public record TextPayload(String text) {
    }

    /** Payload for FILE_DELETED events: { "id": "..." }. */
    public record FileIdPayload(String id) {
    }

    /** Payload for ERROR events: { "code": "...", "message": "..." }. */
    public record ErrorPayload(String code, String message) {
    }
}
