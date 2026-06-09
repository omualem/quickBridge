package com.example.quickbridge.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.Resource;
import org.springframework.mock.web.MockMultipartFile;

import com.example.quickbridge.config.QuickBridgeProperties;
import com.example.quickbridge.error.ApiException;

class DiskFileStorageTest {

    @TempDir
    Path tempDir;

    private DiskFileStorage storage;

    @BeforeEach
    void setUp() {
        QuickBridgeProperties props = new QuickBridgeProperties();
        props.getStorage().setDir(tempDir.toString());
        storage = new DiskFileStorage(props);
        storage.init();
    }

    private MockMultipartFile upload(String content) {
        return new MockMultipartFile("file", "original-name.txt", "text/plain",
                content.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void savesUnderFileIdNotOriginalFilename() throws IOException {
        Path saved = storage.save("AB7KQ", "file-123", upload("hello"));

        // Physical file is named by the fileId, never the user-supplied name.
        assertThat(saved.getFileName().toString()).isEqualTo("file-123");
        assertThat(saved.toString()).doesNotContain("original-name");
        assertThat(Files.readString(saved)).isEqualTo("hello");
        // Stored under <base>/<sessionCode>/<fileId>.
        assertThat(saved.getParent().getFileName().toString()).isEqualTo("AB7KQ");
    }

    @Test
    void opensSavedFileAsResource() throws IOException {
        storage.save("AB7KQ", "file-1", upload("payload"));
        Resource resource = storage.openResource("AB7KQ", "file-1");

        assertThat(resource.exists()).isTrue();
        assertThat(new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8))
                .isEqualTo("payload");
    }

    @Test
    void openMissingFileThrowsFileNotFound() {
        assertThatThrownBy(() -> storage.openResource("AB7KQ", "nope"))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void deleteRemovesTheFile() {
        Path saved = storage.save("AB7KQ", "file-1", upload("x"));
        assertThat(Files.exists(saved)).isTrue();

        storage.delete("AB7KQ", "file-1");
        assertThat(Files.exists(saved)).isFalse();
    }

    @Test
    void deleteSessionDirectoryRemovesEverything() {
        storage.save("AB7KQ", "file-1", upload("a"));
        storage.save("AB7KQ", "file-2", upload("b"));
        Path sessionDir = tempDir.resolve("AB7KQ");
        assertThat(Files.exists(sessionDir)).isTrue();

        storage.deleteSessionDirectory("AB7KQ");
        assertThat(Files.exists(sessionDir)).isFalse();
    }

    // ---- Path-traversal prevention ----

    @Test
    void rejectsTraversalInSessionCode() {
        assertThatThrownBy(() -> storage.save("../evil", "file-1", upload("x")))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void rejectsTraversalInFileId() {
        assertThatThrownBy(() -> storage.save("AB7KQ", "../../etc/passwd", upload("x")))
                .isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> storage.openResource("AB7KQ", "../secret"))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void rejectsSeparatorsInComponents() {
        assertThatThrownBy(() -> storage.save("AB7KQ", "sub/dir", upload("x")))
                .isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> storage.save("AB\\KQ", "file-1", upload("x")))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void resolveWithinKeepsPathsInsideBase() {
        Path base = tempDir.resolve("base");
        Path ok = storage.resolveWithin(base, "child");
        assertThat(ok.startsWith(base)).isTrue();

        assertThatThrownBy(() -> storage.resolveWithin(base, ".."))
                .isInstanceOf(ApiException.class);
    }
}
