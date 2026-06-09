package com.example.quickbridge.websocket;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import com.example.quickbridge.model.FileMetadata;
import com.example.quickbridge.model.SocketEvent;

/**
 * Central helper for broadcasting {@link SocketEvent}s to a session's STOMP
 * topic ({@code /topic/sessions/{code}}).
 *
 * <p>Keeping all broadcasts here means controllers and services don't depend on
 * messaging details and every event uses a consistent envelope.</p>
 */
@Component
public class WebSocketEventPublisher {

    private final SimpMessagingTemplate messagingTemplate;

    public WebSocketEventPublisher(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    private String topic(String code) {
        return "/topic/sessions/" + code;
    }

    public void textUpdated(String code, String text) {
        messagingTemplate.convertAndSend(topic(code), SocketEvent.textUpdated(code, text));
    }

    public void fileAdded(String code, FileMetadata file) {
        messagingTemplate.convertAndSend(topic(code), SocketEvent.fileAdded(code, file));
    }

    public void fileDeleted(String code, String fileId) {
        messagingTemplate.convertAndSend(topic(code), SocketEvent.fileDeleted(code, fileId));
    }

    public void sessionExpired(String code) {
        messagingTemplate.convertAndSend(topic(code), SocketEvent.sessionExpired(code));
    }

    /** Broadcasts an ERROR event to the session topic (e.g. a failed text sync). */
    public void errorToSession(String code, String errorCode, String message) {
        messagingTemplate.convertAndSend(topic(code), SocketEvent.error(code, errorCode, message));
    }
}
