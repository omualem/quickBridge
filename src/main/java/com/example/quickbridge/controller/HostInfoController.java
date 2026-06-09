package com.example.quickbridge.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.quickbridge.model.HostInfo;
import com.example.quickbridge.service.HostInfoService;

/**
 * Exposes the host computer's LAN networking details so the frontend can build a
 * share URL / QR code that other devices on the same network can reach (rather
 * than {@code localhost}, which only works on the host itself).
 */
@RestController
@RequestMapping("/api/host-info")
public class HostInfoController {

    private final HostInfoService hostInfoService;

    public HostInfoController(HostInfoService hostInfoService) {
        this.hostInfoService = hostInfoService;
    }

    @GetMapping
    public ApiResponse<HostInfo> hostInfo() {
        return ApiResponse.ok(hostInfoService.detect());
    }
}
