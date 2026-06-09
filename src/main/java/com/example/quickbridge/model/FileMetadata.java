package com.example.quickbridge.model;

import java.time.Instant;

/**
 * Client-facing description of a stored file.
 *
 * <p>Deliberately omits the on-disk {@code storagePath} — the physical location
 * of bytes is a server-internal detail that is never serialized to clients.</p>
 */
public record FileMetadata(
        String id,
        String originalFilename,
        String contentType,
        long size,
        Instant uploadedAt) {
}
