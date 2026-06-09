package com.example.quickbridge.model;

/**
 * Inbound STOMP message carrying a text update from a client.
 *
 * <p>Sent to {@code /app/sessions/{code}/text}. The {@code text} field holds the
 * full current contents of the shared textarea (whole-document sync for
 * simplicity).</p>
 */
public record TextUpdateMessage(String text) {
}
