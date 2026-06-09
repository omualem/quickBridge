package com.example.quickbridge.controller;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;

import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.util.UriUtils;

import com.example.quickbridge.error.ApiException;
import com.example.quickbridge.error.ErrorCode;
import com.example.quickbridge.model.FileMetadata;
import com.example.quickbridge.model.StoredFileMetadata;
import com.example.quickbridge.service.DiskFileStorage;
import com.example.quickbridge.service.SessionService;
import com.example.quickbridge.websocket.WebSocketEventPublisher;

/**
 * REST endpoints for uploading, downloading and deleting files.
 *
 * <p>Uploaded bytes are streamed to the host computer's disk by
 * {@link DiskFileStorage}; only metadata lives in memory. Downloads stream the
 * file straight back off disk. Uploads broadcast FILE_ADDED and deletes broadcast
 * FILE_DELETED so every connected device updates its list in real time.</p>
 */
@RestController
@RequestMapping("/api/sessions/{code}/files")
public class FileController {

    private final SessionService sessionService;
    private final DiskFileStorage fileStorage;
    private final WebSocketEventPublisher eventPublisher;

    public FileController(SessionService sessionService,
                          DiskFileStorage fileStorage,
                          WebSocketEventPublisher eventPublisher) {
        this.sessionService = sessionService;
        this.fileStorage = fileStorage;
        this.eventPublisher = eventPublisher;
    }

    /** Multipart upload (field name: {@code file}); streams the bytes to disk. */
    @PostMapping
    public ApiResponse<FileMetadata> upload(@PathVariable String code,
                                            @RequestParam("file") MultipartFile file) {
        // Validate the session up front (also rejects bad codes).
        sessionService.requireSession(code);

        if (file == null || file.isEmpty()) {
            throw new ApiException(ErrorCode.EMPTY_FILE);
        }
        // Application-level size check is skipped when the limit is <= 0 (unlimited);
        // Spring's multipart limit still acts as a backstop.
        long maxBytes = sessionService.maxFileSizeBytes();
        if (maxBytes > 0 && file.getSize() > maxBytes) {
            throw new ApiException(ErrorCode.FILE_TOO_LARGE,
                    "File exceeds the maximum of " + (maxBytes / (1024 * 1024)) + " MB");
        }

        String fileId = UUID.randomUUID().toString();
        // Stream straight to disk — never buffer the whole file in our heap.
        Path stored = fileStorage.save(code, fileId, file);

        String name = (file.getOriginalFilename() == null || file.getOriginalFilename().isBlank())
                ? "file" : file.getOriginalFilename();
        String type = (file.getContentType() == null || file.getContentType().isBlank())
                ? "application/octet-stream" : file.getContentType();

        StoredFileMetadata metadata = new StoredFileMetadata(
                fileId, name, type, file.getSize(), Instant.now(), stored);

        FileMetadata view;
        try {
            view = sessionService.registerFile(code, metadata);
        } catch (ApiException ex) {
            // Session was full (or vanished) — don't leak the bytes we just wrote.
            fileStorage.delete(code, fileId);
            throw ex;
        }

        eventPublisher.fileAdded(code, view);
        return ApiResponse.ok(view);
    }

    /** Streams file bytes back from disk with correct content headers. */
    @GetMapping("/{fileId}")
    public ResponseEntity<Resource> download(@PathVariable String code,
                                             @PathVariable String fileId) {
        StoredFileMetadata file = sessionService.requireFile(code, fileId);
        Resource resource = fileStorage.openResource(code, fileId);

        // RFC 5987 encoding so filenames with spaces/unicode download correctly.
        String encoded = UriUtils.encode(file.originalFilename(), StandardCharsets.UTF_8);
        String disposition = "attachment; filename=\"" + file.originalFilename().replace("\"", "")
                + "\"; filename*=UTF-8''" + encoded;

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition)
                .contentType(MediaType.parseMediaType(file.contentType()))
                .contentLength(file.size())
                .body(resource);
    }

    /** Deletes a file from disk and memory, then broadcasts FILE_DELETED. */
    @DeleteMapping("/{fileId}")
    public ApiResponse<Object> delete(@PathVariable String code, @PathVariable String fileId) {
        sessionService.removeFile(code, fileId);
        fileStorage.delete(code, fileId);
        eventPublisher.fileDeleted(code, fileId);
        return ApiResponse.ok(null);
    }
}
