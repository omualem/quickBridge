package com.example.quickbridge.model;

import java.nio.file.Path;
import java.time.Instant;

/**
 * Server-internal record for a file whose bytes live on the host computer's
 * disk.
 *
 * <p>{@link #storagePath} is the physical location under the configured storage
 * directory; the physical filename is the generated {@link #id}, never the
 * user-supplied {@link #originalFilename}. The path is internal only and is
 * stripped before anything is sent to a client via {@link #toClientView()}.</p>
 */
public record StoredFileMetadata(
        String id,
        String originalFilename,
        String contentType,
        long size,
        Instant uploadedAt,
        Path storagePath) {

    /** Returns the client-safe view (no on-disk path). */
    public FileMetadata toClientView() {
        return new FileMetadata(id, originalFilename, contentType, size, uploadedAt);
    }
}
