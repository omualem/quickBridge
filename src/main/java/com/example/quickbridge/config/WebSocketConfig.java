package com.example.quickbridge.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * STOMP-over-WebSocket configuration.
 *
 * <ul>
 *   <li>Clients connect to {@code /ws} (SockJS fallback enabled for browsers
 *       behind proxies that block raw WebSockets).</li>
 *   <li>Messages bound for the server use the {@code /app} prefix, e.g.
 *       {@code /app/sessions/{code}/text}.</li>
 *   <li>The simple in-memory broker fans out to {@code /topic/**}
 *       subscriptions, e.g. {@code /topic/sessions/{code}}.</li>
 * </ul>
 *
 * The broker is in-memory only — consistent with the "no external
 * infrastructure" MVP constraint (no Redis/RabbitMQ).
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic");
        registry.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*")
                .withSockJS();
    }
}
