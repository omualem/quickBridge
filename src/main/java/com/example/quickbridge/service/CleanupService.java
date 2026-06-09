package com.example.quickbridge.service;

import java.time.Instant;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.example.quickbridge.websocket.WebSocketEventPublisher;

/**
 * Periodically evicts expired sessions and deletes the temp files they left on
 * disk.
 *
 * <p>Runs on the interval configured by {@code quickbridge.cleanup.fixed-rate-ms}.
 * For each expired session it drops the in-memory record, removes the session's
 * directory under the storage dir, and broadcasts SESSION_EXPIRED to any client
 * still listening.</p>
 */
@Service
public class CleanupService {

    private static final Logger log = LoggerFactory.getLogger(CleanupService.class);

    private final SessionService sessionService;
    private final DiskFileStorage fileStorage;
    private final WebSocketEventPublisher eventPublisher;

    public CleanupService(SessionService sessionService,
                          DiskFileStorage fileStorage,
                          WebSocketEventPublisher eventPublisher) {
        this.sessionService = sessionService;
        this.fileStorage = fileStorage;
        this.eventPublisher = eventPublisher;
    }

    @Scheduled(fixedRateString = "${quickbridge.cleanup.fixed-rate-ms}")
    public void scheduledCleanup() {
        purgeExpired(Instant.now());
    }

    /**
     * Removes sessions expired as of {@code now}, deletes their disk directories,
     * and broadcasts expiry. Package-visible and time-parameterized for testing.
     *
     * @return number of sessions removed
     */
    public int purgeExpired(Instant now) {
        List<String> removed = sessionService.cleanupExpiredSessions(now);
        for (String code : removed) {
            // Free the temp files this session wrote to disk.
            fileStorage.deleteSessionDirectory(code);
            try {
                eventPublisher.sessionExpired(code);
            } catch (RuntimeException ex) {
                log.warn("Failed to broadcast SESSION_EXPIRED for {}", code, ex);
            }
        }
        if (!removed.isEmpty()) {
            log.info("Cleanup removed {} expired session(s); {} active remain.",
                    removed.size(), sessionService.activeSessionCount());
        }
        return removed.size();
    }
}
