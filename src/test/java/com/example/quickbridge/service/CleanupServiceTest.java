package com.example.quickbridge.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import com.example.quickbridge.config.QuickBridgeProperties;
import com.example.quickbridge.model.StoredFileMetadata;
import com.example.quickbridge.model.TransferSession;
import com.example.quickbridge.websocket.WebSocketEventPublisher;

class CleanupServiceTest {

    @TempDir
    Path tempDir;

    private SessionService sessionService;
    private DiskFileStorage fileStorage;
    private WebSocketEventPublisher publisher;
    private CleanupService cleanup;

    @BeforeEach
    void setUp() {
        QuickBridgeProperties props = new QuickBridgeProperties();
        props.getStorage().setDir(tempDir.toString());
        sessionService = new SessionService(new CodeGenerator(), props);
        fileStorage = new DiskFileStorage(props);
        fileStorage.init();
        publisher = mock(WebSocketEventPublisher.class);
        cleanup = new CleanupService(sessionService, fileStorage, publisher);
    }

    @Test
    void removesExpiredSessionsAndKeepsFresh() {
        sessionService.createSession();
        sessionService.createSession();

        assertThat(cleanup.purgeExpired(Instant.now().plus(Duration.ofMinutes(1)))).isZero();
        assertThat(sessionService.activeSessionCount()).isEqualTo(2);

        assertThat(cleanup.purgeExpired(Instant.now().plus(Duration.ofMinutes(31)))).isEqualTo(2);
        assertThat(sessionService.activeSessionCount()).isZero();
    }

    @Test
    void deletesExpiredSessionDirectoryFromDisk() {
        TransferSession session = sessionService.createSession();
        String code = session.getCode();

        // Put a real file on disk for the session and register its metadata.
        Path saved = fileStorage.save(code, "file-1",
                new MockMultipartFile("file", "a.txt", "text/plain",
                        "data".getBytes(StandardCharsets.UTF_8)));
        sessionService.registerFile(code, new StoredFileMetadata(
                "file-1", "a.txt", "text/plain", 4, Instant.now(), saved));
        assertThat(Files.exists(saved)).isTrue();

        cleanup.purgeExpired(Instant.now().plus(Duration.ofMinutes(31)));

        assertThat(Files.exists(saved)).isFalse();
        assertThat(Files.exists(tempDir.resolve(code))).isFalse();
        verify(publisher).sessionExpired(code);
    }

    @Test
    void doesNotBroadcastWhenNothingExpired() {
        sessionService.createSession();
        cleanup.purgeExpired(Instant.now());
        org.mockito.Mockito.verifyNoInteractions(publisher);
    }
}
