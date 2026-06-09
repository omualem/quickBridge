package com.example.quickbridge.websocket;

import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;

import com.example.quickbridge.error.ApiException;
import com.example.quickbridge.model.TextUpdateMessage;
import com.example.quickbridge.service.SessionService;

/**
 * Handles inbound STOMP text-sync messages.
 *
 * <p>A client sends the full textarea contents to
 * {@code /app/sessions/{code}/text}. We validate the session, enforce the length
 * limit, store the text in memory, refresh activity, and fan the result back out
 * to {@code /topic/sessions/{code}} as a TEXT_UPDATED event so every other device
 * updates in real time.</p>
 */
@Controller
public class SessionSocketController {

    private final SessionService sessionService;
    private final WebSocketEventPublisher eventPublisher;

    public SessionSocketController(SessionService sessionService,
                                   WebSocketEventPublisher eventPublisher) {
        this.sessionService = sessionService;
        this.eventPublisher = eventPublisher;
    }

    @MessageMapping("/sessions/{code}/text")
    public void onTextUpdate(@DestinationVariable String code,
                             @Payload TextUpdateMessage message) {
        try {
            String stored = sessionService.updateText(code, message.text());
            eventPublisher.textUpdated(code, stored);
        } catch (ApiException ex) {
            // Surface the failure to the session topic so the UI can show
            // "session not found / text too long" without a REST round-trip.
            eventPublisher.errorToSession(code, ex.getErrorCode().name(), ex.getMessage());
        }
    }
}
