package com.example.quickbridge.service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.example.quickbridge.config.QuickBridgeProperties;
import com.example.quickbridge.error.ApiException;
import com.example.quickbridge.error.ErrorCode;
import com.example.quickbridge.model.FileMetadata;
import com.example.quickbridge.model.SessionSnapshot;
import com.example.quickbridge.model.StoredFileMetadata;
import com.example.quickbridge.model.TransferSession;

/**
 * In-memory store and business logic for sessions.
 *
 * <p>Holds shared text and file <em>metadata</em> in a {@link ConcurrentHashMap}
 * keyed by session code. File <em>bytes</em> are never stored here — they live on
 * disk via {@code DiskFileStorage}, and each {@link StoredFileMetadata} only
 * references its on-disk path. There is no database.</p>
 */
@Service
public class SessionService {

    private static final Logger log = LoggerFactory.getLogger(SessionService.class);

    /** Whole-document text sync cap (characters). */
    public static final int MAX_TEXT_LENGTH = 100_000;

    /** code -> live session. Thread-safe; not exposed directly. */
    private final ConcurrentMap<String, TransferSession> sessions = new ConcurrentHashMap<>();

    private final CodeGenerator codeGenerator;
    private final Duration ttl;
    private final int maxFilesPerSession;
    private final long maxFileSizeBytes;

    public SessionService(CodeGenerator codeGenerator, QuickBridgeProperties properties) {
        this.codeGenerator = codeGenerator;
        this.ttl = Duration.ofMinutes(properties.getSession().getTtlMinutes());
        this.maxFilesPerSession = properties.getFile().getMaxFilesPerSession();
        this.maxFileSizeBytes = properties.getFile().getMaxSizeBytes();
    }

    // ---- Session lifecycle ---------------------------------------------

    /** Creates a new session with a unique code (insert is atomic). */
    public TransferSession createSession() {
        while (true) {
            String code = codeGenerator.generateUnique(sessions::containsKey);
            TransferSession session = new TransferSession(code, ttl);
            if (sessions.putIfAbsent(code, session) == null) {
                log.info("Session created: {}", code);
                return session;
            }
            // Rare race: another thread took this code — retry.
        }
    }

    /**
     * @throws ApiException INVALID_CODE if malformed, SESSION_NOT_FOUND if missing.
     */
    public TransferSession requireSession(String code) {
        if (!CodeGenerator.isValid(code)) {
            throw new ApiException(ErrorCode.INVALID_CODE);
        }
        TransferSession session = sessions.get(code);
        if (session == null) {
            throw new ApiException(ErrorCode.SESSION_NOT_FOUND);
        }
        return session;
    }

    public boolean exists(String code) {
        return CodeGenerator.isValid(code) && sessions.containsKey(code);
    }

    public SessionSnapshot snapshot(String code) {
        return requireSession(code).snapshot();
    }

    public void touch(String code) {
        TransferSession session = sessions.get(code);
        if (session != null) {
            session.touch();
        }
    }

    public int activeSessionCount() {
        return sessions.size();
    }

    // ---- Limits (exposed for controllers) ------------------------------

    public int maxFilesPerSession() {
        return maxFilesPerSession;
    }

    public long maxFileSizeBytes() {
        return maxFileSizeBytes;
    }

    // ---- Text -----------------------------------------------------------

    /**
     * Updates the shared text after validating length, refreshing activity.
     *
     * @return the stored text (normalized, never null)
     */
    public String updateText(String code, String text) {
        TransferSession session = requireSession(code);
        String value = text == null ? "" : text;
        if (value.length() > MAX_TEXT_LENGTH) {
            throw new ApiException(ErrorCode.TEXT_TOO_LONG,
                    "Text exceeds the maximum of " + MAX_TEXT_LENGTH + " characters");
        }
        session.setText(value);
        return value;
    }

    // ---- File metadata --------------------------------------------------

    /**
     * Registers already-stored file metadata against the session, enforcing the
     * per-session count limit atomically.
     *
     * @return the client-safe view of the registered file
     * @throws ApiException TOO_MANY_FILES if the session is already full
     */
    public FileMetadata registerFile(String code, StoredFileMetadata stored) {
        TransferSession session = requireSession(code);
        if (!session.addFileIfRoom(stored, maxFilesPerSession)) {
            throw new ApiException(ErrorCode.TOO_MANY_FILES,
                    "Session already holds the maximum of " + maxFilesPerSession + " files");
        }
        return stored.toClientView();
    }

    /** Looks up stored metadata (with disk path) for download. */
    public StoredFileMetadata requireFile(String code, String fileId) {
        TransferSession session = requireSession(code);
        StoredFileMetadata file = session.getFile(fileId);
        if (file == null) {
            throw new ApiException(ErrorCode.FILE_NOT_FOUND);
        }
        session.touch();
        return file;
    }

    /** Removes file metadata, returning the removed entry for disk cleanup. */
    public StoredFileMetadata removeFile(String code, String fileId) {
        TransferSession session = requireSession(code);
        StoredFileMetadata removed = session.removeFile(fileId);
        if (removed == null) {
            throw new ApiException(ErrorCode.FILE_NOT_FOUND);
        }
        return removed;
    }

    // ---- Cleanup --------------------------------------------------------

    /**
     * Removes every session past its TTL as of {@code now}. Parameterized on time
     * for deterministic testing. Disk directory deletion is the caller's job
     * (see {@code CleanupService}).
     *
     * @return the codes that were removed
     */
    public List<String> cleanupExpiredSessions(Instant now) {
        List<String> removed = new ArrayList<>();
        Iterator<Map.Entry<String, TransferSession>> it = sessions.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, TransferSession> entry = it.next();
            if (entry.getValue().isExpired(now)) {
                it.remove();
                removed.add(entry.getKey());
            }
        }
        return removed;
    }
}
