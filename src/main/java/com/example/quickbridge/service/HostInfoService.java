package com.example.quickbridge.service;

import java.net.InetAddress;
import java.net.Inet4Address;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.example.quickbridge.config.QuickBridgeProperties;
import com.example.quickbridge.model.HostInfo;

/**
 * Detects a good LAN IPv4 address for the host computer so the UI can build a
 * share URL / QR code that other devices on the same network can reach.
 *
 * <p>The messy I/O (enumerating {@link NetworkInterface}s) is isolated in
 * {@link #detect()}, while the decision logic — what counts as a private IPv4,
 * which adapter names to skip, how to rank candidates — lives in small static
 * helpers that are unit-testable without touching real network hardware.</p>
 */
@Service
public class HostInfoService {

    private static final Logger log = LoggerFactory.getLogger(HostInfoService.class);

    /**
     * Substrings of adapter names that usually indicate a virtual / VPN / container
     * interface we should not advertise as the LAN address.
     */
    static final List<String> BLOCKED_ADAPTER_HINTS = List.of(
            "docker", "vmware", "virtualbox", "vbox", "wsl", "hyper-v", "hyperv",
            "nord", "vpn", "tailscale", "zerotier", "veth", "utun", "tun", "tap");

    private final int port;
    private final long maxFileSizeMb;
    private final int maxFilesPerSession;

    public HostInfoService(QuickBridgeProperties properties) {
        this.port = properties.getHost().getPort();
        this.maxFileSizeMb = properties.getFile().getMaxSizeMb();
        this.maxFilesPerSession = properties.getFile().getMaxFilesPerSession();
    }

    /**
     * Inspects the host's network interfaces and returns the best LAN URL info.
     * Falls back to a warning (and null IP/URL) if nothing suitable is found.
     */
    public HostInfo detect() {
        List<String> candidates = new ArrayList<>();
        try {
            Enumeration<NetworkInterface> nics = NetworkInterface.getNetworkInterfaces();
            while (nics != null && nics.hasMoreElements()) {
                NetworkInterface nic = nics.nextElement();
                if (!isUsableInterface(nic)) {
                    continue;
                }
                Enumeration<InetAddress> addrs = nic.getInetAddresses();
                while (addrs.hasMoreElements()) {
                    InetAddress addr = addrs.nextElement();
                    // IPv4 only for the MVP; skip loopback just in case.
                    if (addr instanceof Inet4Address && !addr.isLoopbackAddress()) {
                        String ip = addr.getHostAddress();
                        if (isPrivateIpv4(ip) && !candidates.contains(ip)) {
                            candidates.add(ip);
                        }
                    }
                }
            }
        } catch (SocketException e) {
            log.warn("Could not enumerate network interfaces", e);
        }

        String best = selectBest(candidates);
        List<String> warnings = new ArrayList<>();
        if (best == null) {
            warnings.add("Could not detect LAN IP. Open this app using the host computer "
                    + "IP address manually.");
            return new HostInfo(null, port, null, candidates, warnings,
                    maxFileSizeMb, maxFilesPerSession);
        }
        String hostUrl = "http://" + best + ":" + port;
        return new HostInfo(best, port, hostUrl, candidates, warnings,
                maxFileSizeMb, maxFilesPerSession);
    }

    // ---- Unit-testable decision helpers --------------------------------

    /** True if the interface is up, non-loopback, non-virtual and not a known VPN/container. */
    boolean isUsableInterface(NetworkInterface nic) {
        try {
            if (nic.isLoopback() || !nic.isUp() || nic.isVirtual()) {
                return false;
            }
        } catch (SocketException e) {
            return false;
        }
        return !isBlockedAdapterName(nic.getName())
                && !isBlockedAdapterName(nic.getDisplayName());
    }

    /** True if {@code name} looks like a virtual/VPN/container adapter to skip. */
    static boolean isBlockedAdapterName(String name) {
        if (name == null) {
            return false;
        }
        String lower = name.toLowerCase(Locale.ROOT);
        for (String hint : BLOCKED_ADAPTER_HINTS) {
            if (lower.contains(hint)) {
                return true;
            }
        }
        return false;
    }

    /** True for RFC 1918 private IPv4 ranges (192.168/16, 10/8, 172.16/12). */
    static boolean isPrivateIpv4(String ip) {
        if (ip == null) {
            return false;
        }
        String[] parts = ip.split("\\.");
        if (parts.length != 4) {
            return false;
        }
        int[] o = new int[4];
        for (int i = 0; i < 4; i++) {
            try {
                o[i] = Integer.parseInt(parts[i]);
            } catch (NumberFormatException e) {
                return false;
            }
            if (o[i] < 0 || o[i] > 255) {
                return false;
            }
        }
        if (o[0] == 192 && o[1] == 168) {
            return true;
        }
        if (o[0] == 10) {
            return true;
        }
        return o[0] == 172 && o[1] >= 16 && o[1] <= 31;
    }

    /**
     * Picks the most "home network"-looking address: prefer 192.168.x.x, then
     * 10.x.x.x, then 172.16–31.x.x. Returns {@code null} for an empty list.
     */
    static String selectBest(List<String> candidates) {
        return candidates.stream()
                .min(Comparator.comparingInt(HostInfoService::privateRank))
                .orElse(null);
    }

    /** Lower rank = more preferred. */
    static int privateRank(String ip) {
        if (ip.startsWith("192.168.")) {
            return 0;
        }
        if (ip.startsWith("10.")) {
            return 1;
        }
        if (isPrivateIpv4(ip)) {
            return 2; // 172.16–31 range
        }
        return 3;
    }
}
