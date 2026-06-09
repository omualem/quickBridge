package com.example.quickbridge.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.example.quickbridge.config.QuickBridgeProperties;
import com.example.quickbridge.error.ApiException;
import com.example.quickbridge.error.ErrorCode;
import com.example.quickbridge.model.FileMetadata;
import com.example.quickbridge.model.SessionSnapshot;
import com.example.quickbridge.model.StoredFileMetadata;
import com.example.quickbridge.model.TransferSession;

class SessionServiceTest {

    private SessionService service;

    @BeforeEach
    void setUp() {
        service = new SessionService(new CodeGenerator(), new QuickBridgeProperties());
    }

    private SessionService serviceWithMaxFiles(int maxFiles) {
        QuickBridgeProperties props = new QuickBridgeProperties();
        props.getFile().setMaxFilesPerSession(maxFiles);
        return new SessionService(new CodeGenerator(), props);
    }

    private StoredFileMetadata stored(String id, String name) {
        return new StoredFileMetadata(id, name, "application/octet-stream",
                123L, Instant.now(), Path.of("/tmp/quickbridge", id));
    }

    @Test
    void createsSessionWithValidUniqueCode() {
        TransferSession a = service.createSession();
        TransferSession b = service.createSession();

        assertThat(CodeGenerator.isValid(a.getCode())).isTrue();
        assertThat(a.getCode()).isNotEqualTo(b.getCode());
        assertThat(service.activeSessionCount()).isEqualTo(2);
    }

    @Test
    void rejectsInvalidCode() {
        assertThatThrownBy(() -> service.requireSession("bad!"))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getErrorCode()).isEqualTo(ErrorCode.INVALID_CODE));
    }

    @Test
    void returnsNotFoundForUnknownCode() {
        assertThatThrownBy(() -> service.requireSession("AB7KQ"))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getErrorCode()).isEqualTo(ErrorCode.SESSION_NOT_FOUND));
    }

    @Test
    void storesAndRetrievesText() {
        TransferSession session = service.createSession();
        service.updateText(session.getCode(), "hello world");
        assertThat(service.snapshot(session.getCode()).text()).isEqualTo("hello world");
    }

    @Test
    void rejectsTextOverMaxLength() {
        TransferSession session = service.createSession();
        String tooLong = "x".repeat(SessionService.MAX_TEXT_LENGTH + 1);
        assertThatThrownBy(() -> service.updateText(session.getCode(), tooLong))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getErrorCode()).isEqualTo(ErrorCode.TEXT_TOO_LONG));
    }

    @Test
    void registersFileMetadataAndSnapshotHidesStoragePath() {
        TransferSession session = service.createSession();
        FileMetadata view = service.registerFile(session.getCode(), stored("f1", "notes.pdf"));

        assertThat(view.originalFilename()).isEqualTo("notes.pdf");
        // The client-facing record has no storagePath component.
        boolean hasPathComponent = java.util.Arrays.stream(FileMetadata.class.getRecordComponents())
                .anyMatch(c -> c.getName().toLowerCase().contains("path"));
        assertThat(hasPathComponent).isFalse();

        SessionSnapshot snapshot = service.snapshot(session.getCode());
        assertThat(snapshot.files()).hasSize(1);
        assertThat(snapshot.files().get(0).id()).isEqualTo("f1");
    }

    @Test
    void requireFileReturnsStoredMetadataWithPath() {
        TransferSession session = service.createSession();
        service.registerFile(session.getCode(), stored("f1", "a.bin"));

        StoredFileMetadata found = service.requireFile(session.getCode(), "f1");
        assertThat(found.storagePath()).isEqualTo(Path.of("/tmp/quickbridge", "f1"));

        assertThatThrownBy(() -> service.requireFile(session.getCode(), "missing"))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getErrorCode()).isEqualTo(ErrorCode.FILE_NOT_FOUND));
    }

    @Test
    void enforcesMaxFilesPerSessionWhenPositive() {
        SessionService limited = serviceWithMaxFiles(2);
        TransferSession session = limited.createSession();

        limited.registerFile(session.getCode(), stored("f0", "f0.bin"));
        limited.registerFile(session.getCode(), stored("f1", "f1.bin"));
        // Third file is rejected.
        assertThatThrownBy(() -> limited.registerFile(session.getCode(), stored("f2", "f2.bin")))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getErrorCode()).isEqualTo(ErrorCode.TOO_MANY_FILES));
    }

    @Test
    void unlimitedFilesWhenMaxIsZero() {
        SessionService unlimited = serviceWithMaxFiles(0);
        TransferSession session = unlimited.createSession();

        // Far more than the old default of 10 — all must succeed.
        for (int i = 0; i < 25; i++) {
            unlimited.registerFile(session.getCode(), stored("f" + i, "f" + i + ".bin"));
        }
        assertThat(unlimited.snapshot(session.getCode()).files()).hasSize(25);
    }

    @Test
    void unlimitedFilesWhenMaxIsNegative() {
        SessionService unlimited = serviceWithMaxFiles(-1);
        TransferSession session = unlimited.createSession();

        for (int i = 0; i < 15; i++) {
            unlimited.registerFile(session.getCode(), stored("f" + i, "f" + i + ".bin"));
        }
        assertThat(unlimited.snapshot(session.getCode()).files()).hasSize(15);
    }

    @Test
    void removeFileReturnsRemovedMetadata() {
        TransferSession session = service.createSession();
        service.registerFile(session.getCode(), stored("f1", "a.bin"));

        StoredFileMetadata removed = service.removeFile(session.getCode(), "f1");
        assertThat(removed.id()).isEqualTo("f1");
        assertThat(service.snapshot(session.getCode()).files()).isEmpty();
    }

    @Test
    void cleanupReturnsExpiredCodes() {
        TransferSession session = service.createSession();
        assertThat(service.cleanupExpiredSessions(Instant.now().plusSeconds(60)))
                .isEmpty();
        assertThat(service.cleanupExpiredSessions(Instant.now().plus(java.time.Duration.ofMinutes(31))))
                .containsExactly(session.getCode());
        assertThat(service.activeSessionCount()).isZero();
    }
}
