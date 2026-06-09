package com.example.quickbridge.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import com.example.quickbridge.config.QuickBridgeProperties;
import com.example.quickbridge.error.ApiException;
import com.example.quickbridge.error.ErrorCode;

import jakarta.annotation.PostConstruct;

/**
 * Disk-backed file storage on the host computer.
 *
 * <p>Uploaded bytes are streamed straight to disk under the configured storage
 * directory — they are <strong>never</strong> held as a {@code byte[]} in
 * application memory. Each file is stored at
 * {@code <baseDir>/<sessionCode>/<fileId>}, where the physical filename is the
 * server-generated {@code fileId}, never the user-supplied original name.</p>
 *
 * <h2>Path-traversal safety</h2>
 * Both {@code sessionCode} and {@code fileId} are resolved against the base
 * directory and the result is verified to remain inside it; any component
 * containing a path separator or {@code ..} is rejected outright. Because
 * session codes come from the fixed code alphabet and file ids are generated
 * UUIDs, legitimate values always pass — this guards against a malicious client
 * crafting traversal input.
 */
@Service
public class DiskFileStorage {

    private static final Logger log = LoggerFactory.getLogger(DiskFileStorage.class);

    private final Path baseDir;

    public DiskFileStorage(QuickBridgeProperties properties) {
        this.baseDir = Path.of(properties.getStorage().getDir()).toAbsolutePath().normalize();
    }

    @PostConstruct
    public void init() {
        try {
            Files.createDirectories(baseDir);
            log.info("File storage directory: {}", baseDir);
        } catch (IOException e) {
            throw new IllegalStateException("Could not create storage directory: " + baseDir, e);
        }
    }

    /**
     * Streams an upload's bytes to {@code <baseDir>/<sessionCode>/<fileId>} and
     * returns the path. Uses {@link org.springframework.web.multipart.MultipartFile#transferTo}
     * so the (already disk-buffered) upload is moved into place rather than read
     * into memory.
     */
    public Path save(String sessionCode, String fileId,
                     org.springframework.web.multipart.MultipartFile file) {
        Path sessionDir = resolveWithin(baseDir, sessionCode);
        Path target = resolveWithin(sessionDir, fileId);
        try {
            Files.createDirectories(sessionDir);
            // transferTo moves the multipart temp file to disk without buffering
            // the whole payload in our heap.
            file.transferTo(target);
            return target;
        } catch (IOException | IllegalStateException e) {
            log.warn("Failed to store file for session {}", sessionCode, e);
            throw new ApiException(ErrorCode.STORAGE_ERROR);
        }
    }

    /**
     * Opens a stored file as a streamable {@link Resource}, or throws
     * {@code FILE_NOT_FOUND} if it is missing.
     */
    public Resource openResource(String sessionCode, String fileId) {
        Path target = resolveWithin(resolveWithin(baseDir, sessionCode), fileId);
        if (!Files.isReadable(target)) {
            throw new ApiException(ErrorCode.FILE_NOT_FOUND);
        }
        return new FileSystemResource(target);
    }

    /** Deletes a single stored file. Silent if it is already gone. */
    public void delete(String sessionCode, String fileId) {
        Path target = resolveWithin(resolveWithin(baseDir, sessionCode), fileId);
        try {
            Files.deleteIfExists(target);
        } catch (IOException e) {
            log.warn("Failed to delete file {} in session {}", fileId, sessionCode, e);
        }
    }

    /** Recursively deletes a session's entire storage directory. */
    public void deleteSessionDirectory(String sessionCode) {
        Path sessionDir = resolveWithin(baseDir, sessionCode);
        if (!Files.exists(sessionDir)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(sessionDir)) {
            walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException e) {
                    log.warn("Failed to delete {}", p, e);
                }
            });
        } catch (IOException e) {
            log.warn("Failed to delete session directory for {}", sessionCode, e);
        }
    }

    Path getBaseDir() {
        return baseDir;
    }

    /**
     * Resolves a single path component against {@code base} and verifies the
     * result stays within it. Rejects separators and {@code ..} segments.
     */
    Path resolveWithin(Path base, String component) {
        if (component == null || component.isBlank()
                || component.contains("/") || component.contains("\\")
                || component.contains("..")) {
            throw new ApiException(ErrorCode.INVALID_CODE, "Illegal path component");
        }
        Path resolved = base.resolve(component).normalize();
        if (!resolved.startsWith(base)) {
            throw new ApiException(ErrorCode.INVALID_CODE, "Path traversal detected");
        }
        return resolved;
    }
}
