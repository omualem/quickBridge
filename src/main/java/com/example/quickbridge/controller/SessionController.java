package com.example.quickbridge.controller;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.quickbridge.model.SessionSnapshot;
import com.example.quickbridge.model.TransferSession;
import com.example.quickbridge.service.SessionService;

/**
 * REST endpoints for creating sessions and reading their snapshots.
 */
@RestController
@RequestMapping("/api/sessions")
public class SessionController {

    private final SessionService sessionService;

    public SessionController(SessionService sessionService) {
        this.sessionService = sessionService;
    }

    /**
     * Creates a new session.
     *
     * @return {@code { code, url, expiresAt }} wrapped in the success envelope.
     */
    @PostMapping
    public ResponseEntity<ApiResponse<Map<String, Object>>> create() {
        TransferSession session = sessionService.createSession();
        Map<String, Object> data = Map.of(
                "code", session.getCode(),
                "url", "/" + session.getCode(),
                "expiresAt", session.getExpiresAt());
        return ResponseEntity.ok(ApiResponse.ok(data));
    }

    /**
     * Returns the current snapshot for a session (text + file metadata).
     * 404 if the session is missing or expired, 400 if the code is malformed.
     */
    @GetMapping("/{code}")
    public ApiResponse<SessionSnapshot> snapshot(@PathVariable String code) {
        return ApiResponse.ok(sessionService.snapshot(code));
    }
}
