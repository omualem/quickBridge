package com.example.quickbridge.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;

import com.example.quickbridge.config.QuickBridgeProperties;
import com.example.quickbridge.error.ApiException;
import com.example.quickbridge.error.ErrorCode;
import com.example.quickbridge.model.FileMetadata;
import com.example.quickbridge.model.TransferSession;
import com.example.quickbridge.service.CodeGenerator;
import com.example.quickbridge.service.DiskFileStorage;
import com.example.quickbridge.service.SessionService;
import com.example.quickbridge.websocket.WebSocketEventPublisher;

/**
 * Exercises the upload → download → delete flow against real services with a
 * temp storage dir (no web layer), confirming bytes round-trip through disk.
 */
class FileControllerTest {

    @TempDir
    Path tempDir;

    private SessionService sessionService;
    private DiskFileStorage fileStorage;
    private WebSocketEventPublisher publisher;
    private FileController controller;
    private String code;

    @BeforeEach
    void setUp() {
        QuickBridgeProperties props = new QuickBridgeProperties();
        props.getStorage().setDir(tempDir.toString());
        sessionService = new SessionService(new CodeGenerator(), props);
        fileStorage = new DiskFileStorage(props);
        fileStorage.init();
        publisher = mock(WebSocketEventPublisher.class);
        controller = new FileController(sessionService, fileStorage, publisher);

        TransferSession session = sessionService.createSession();
        code = session.getCode();
    }

    private MockMultipartFile file(String name, String content) {
        return new MockMultipartFile("file", name, "text/plain",
                content.getBytes(StandardCharsets.UTF_8));
    }

    /** Builds an independent controller + fresh session with custom file limits. */
    private record Harness(FileController controller, SessionService sessionService, String code) {
    }

    private Harness harness(long maxSizeMb, int maxFilesPerSession) {
        QuickBridgeProperties props = new QuickBridgeProperties();
        props.getStorage().setDir(tempDir.toString());
        props.getFile().setMaxSizeMb(maxSizeMb);
        props.getFile().setMaxFilesPerSession(maxFilesPerSession);
        SessionService ss = new SessionService(new CodeGenerator(), props);
        DiskFileStorage fs = new DiskFileStorage(props);
        fs.init();
        FileController fc = new FileController(ss, fs, mock(WebSocketEventPublisher.class));
        return new Harness(fc, ss, ss.createSession().getCode());
    }

    @Test
    void uploadStoresOnDiskAndBroadcasts() {
        FileMetadata meta = controller.upload(code, file("notes.txt", "hello")).data();

        assertThat(meta.originalFilename()).isEqualTo("notes.txt");
        assertThat(meta.size()).isEqualTo(5);
        // Bytes are on disk under <base>/<code>/<fileId>, named by id not filename.
        assertThat(tempDir.resolve(code).resolve(meta.id())).exists();
        verify(publisher).fileAdded(code, meta);
    }

    @Test
    void downloadStreamsBytesBackFromDisk() throws Exception {
        FileMetadata meta = controller.upload(code, file("a.txt", "payload")).data();

        ResponseEntity<Resource> response = controller.download(code, meta.id());
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getHeaders().getFirst("Content-Disposition")).contains("a.txt");

        String body = new String(response.getBody().getInputStream().readAllBytes(),
                StandardCharsets.UTF_8);
        assertThat(body).isEqualTo("payload");
    }

    @Test
    void deleteRemovesFileFromDiskAndBroadcasts() {
        FileMetadata meta = controller.upload(code, file("a.txt", "x")).data();
        Path onDisk = tempDir.resolve(code).resolve(meta.id());
        assertThat(onDisk).exists();

        controller.delete(code, meta.id());

        assertThat(onDisk).doesNotExist();
        verify(publisher).fileDeleted(code, meta.id());
        assertThatThrownBy(() -> controller.download(code, meta.id()))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void rejectsEmptyUpload() {
        assertThatThrownBy(() -> controller.upload(code, file("empty.txt", "")))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getErrorCode()).isEqualTo(ErrorCode.EMPTY_FILE));
    }

    @Test
    void uploadToUnknownSessionRejected() {
        assertThatThrownBy(() -> controller.upload("AB7KQ", file("a.txt", "x")))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getErrorCode()).isEqualTo(ErrorCode.SESSION_NOT_FOUND));
    }

    @Test
    void rejectsOversizedFileWhenSizeLimitPositive() {
        Harness h = harness(1, 0); // 1 MB per-file limit, unlimited count
        byte[] twoMb = new byte[2 * 1024 * 1024];
        MockMultipartFile big = new MockMultipartFile("file", "big.bin",
                "application/octet-stream", twoMb);

        assertThatThrownBy(() -> h.controller().upload(h.code(), big))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getErrorCode()).isEqualTo(ErrorCode.FILE_TOO_LARGE));
    }

    @Test
    void skipsApplicationSizeCheckWhenLimitDisabled() {
        // max-size-mb=0 -> getMaxSizeBytes() is negative, so without the skip a
        // 5-byte file (> -1) would be rejected. Asserting success proves the skip.
        Harness h = harness(0, 0);
        FileMetadata meta = h.controller().upload(h.code(), file("a.txt", "hello")).data();
        assertThat(meta.size()).isEqualTo(5);
    }

    @Test
    void unlimitedFileCountAllowsManyUploads() {
        Harness h = harness(10240, 0); // unlimited count
        for (int i = 0; i < 15; i++) {
            h.controller().upload(h.code(), file("f" + i + ".txt", "data-" + i));
        }
        assertThat(h.sessionService().snapshot(h.code()).files()).hasSize(15);
    }
}
