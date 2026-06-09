package com.example.quickbridge.model;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * A live transfer session shared by every device using the same code.
 *
 * <p>The session keeps the shared <em>text</em> and <em>file metadata</em> in
 * memory. It does <strong>not</strong> hold file bytes — those are written to
 * the host computer's disk by {@code DiskFileStorage}, and each
 * {@link StoredFileMetadata} only points at its on-disk location.</p>
 *
 * <h2>Concurrency</h2>
 * Multiple devices (and the cleanup thread) may touch a session at once, so:
 * <ul>
 *   <li>{@code text} and {@code lastActivityAt} are {@link AtomicReference}s;</li>
 *   <li>{@code files} is a {@link ConcurrentHashMap} keyed by file id.</li>
 * </ul>
 * The file map is never exposed directly; callers get defensive copies.
 */
public class TransferSession {

    private final String code;
    private final Instant createdAt;
    private final Duration ttl;

    private final AtomicReference<String> text = new AtomicReference<>("");
    private final AtomicReference<Instant> lastActivityAt;

    /** fileId -> on-disk file metadata. Bytes live on disk, not here. */
    private final Map<String, StoredFileMetadata> files = new ConcurrentHashMap<>();

    /** Guards the compound "check count then add" operation. */
    private final Object fileLock = new Object();

    public TransferSession(String code, Duration ttl) {
        this.code = code;
        this.ttl = ttl;
        this.createdAt = Instant.now();
        this.lastActivityAt = new AtomicReference<>(createdAt);
    }

    public String getCode() {
        return code;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public String getText() {
        return text.get();
    }

    /** Replaces the shared text and bumps the activity timestamp. */
    public void setText(String newText) {
        text.set(newText == null ? "" : newText);
        touch();
    }

    /** Marks the session active right now, resetting the inactivity timer. */
    public void touch() {
        lastActivityAt.set(Instant.now());
    }

    public Instant getLastActivityAt() {
        return lastActivityAt.get();
    }

    public Instant getExpiresAt() {
        return lastActivityAt.get().plus(ttl);
    }

    public boolean isExpired(Instant now) {
        return now.isAfter(getExpiresAt());
    }

    public int fileCount() {
        return files.size();
    }

    /**
     * Atomically registers file metadata if the per-session limit has not been
     * reached. A {@code maxFiles <= 0} means "unlimited" and never rejects.
     *
     * @return {@code true} if added, {@code false} if the session is already full.
     */
    public boolean addFileIfRoom(StoredFileMetadata file, int maxFiles) {
        synchronized (fileLock) {
            if (maxFiles > 0 && files.size() >= maxFiles) {
                return false;
            }
            files.put(file.id(), file);
        }
        touch();
        return true;
    }

    /** Returns the stored metadata (including disk path) or {@code null}. */
    public StoredFileMetadata getFile(String fileId) {
        return files.get(fileId);
    }

    /** Removes file metadata, returning what was removed (or {@code null}). */
    public StoredFileMetadata removeFile(String fileId) {
        StoredFileMetadata removed = files.remove(fileId);
        if (removed != null) {
            touch();
        }
        return removed;
    }

    /** Defensive copy of all stored file metadata (with disk paths). */
    public List<StoredFileMetadata> storedFiles() {
        return new ArrayList<>(files.values());
    }

    /** Client-safe file views (no disk paths). */
    public List<FileMetadata> fileViews() {
        List<FileMetadata> result = new ArrayList<>(files.size());
        for (StoredFileMetadata f : files.values()) {
            result.add(f.toClientView());
        }
        return result;
    }

    /** Immutable point-in-time view, sharing no mutable state with this session. */
    public SessionSnapshot snapshot() {
        return new SessionSnapshot(code, getText(), fileViews(), getExpiresAt());
    }
}
