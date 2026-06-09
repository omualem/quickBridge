package com.example.quickbridge.model;

import java.time.Instant;
import java.util.List;

/**
 * Immutable point-in-time view of a session, safe to serialize to clients.
 *
 * <p>Carries the shared text and client-safe {@link FileMetadata} (no on-disk
 * paths). Produced via defensive copy from {@link TransferSession}.</p>
 */
public record SessionSnapshot(
        String code,
        String text,
        List<FileMetadata> files,
        Instant expiresAt) {
}
