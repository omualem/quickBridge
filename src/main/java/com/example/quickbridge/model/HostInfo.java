package com.example.quickbridge.model;

import java.util.List;

/**
 * LAN networking details for the host computer, returned by
 * {@code GET /api/host-info} so the frontend can build a shareable LAN URL and
 * QR code that other devices on the network can actually reach.
 *
 * @param localIp             best detected private IPv4, or {@code null} if none found
 * @param port                the port the app is advertised on
 * @param hostUrl             {@code http://<localIp>:<port>}, or {@code null} if no IP
 * @param candidates          all private IPv4 addresses detected (diagnostics)
 * @param warnings            human-readable issues (e.g. no LAN IP detected)
 * @param maxFileSizeMb       per-file size limit in MB ({@code <= 0} = unlimited)
 * @param maxFilesPerSession  per-session file-count limit ({@code <= 0} = unlimited)
 */
public record HostInfo(
        String localIp,
        int port,
        String hostUrl,
        List<String> candidates,
        List<String> warnings,
        long maxFileSizeMb,
        int maxFilesPerSession) {
}
