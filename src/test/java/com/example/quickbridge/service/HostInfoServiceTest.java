package com.example.quickbridge.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.example.quickbridge.config.QuickBridgeProperties;
import com.example.quickbridge.model.HostInfo;

class HostInfoServiceTest {

    private HostInfoService service() {
        return new HostInfoService(new QuickBridgeProperties());
    }

    @Test
    void recognisesPrivateIpv4Ranges() {
        assertThat(HostInfoService.isPrivateIpv4("192.168.1.135")).isTrue();
        assertThat(HostInfoService.isPrivateIpv4("10.0.0.5")).isTrue();
        assertThat(HostInfoService.isPrivateIpv4("172.16.0.1")).isTrue();
        assertThat(HostInfoService.isPrivateIpv4("172.31.255.254")).isTrue();
    }

    @Test
    void rejectsNonPrivateOrMalformedIpv4() {
        assertThat(HostInfoService.isPrivateIpv4("8.8.8.8")).isFalse();
        assertThat(HostInfoService.isPrivateIpv4("172.15.0.1")).isFalse(); // just below range
        assertThat(HostInfoService.isPrivateIpv4("172.32.0.1")).isFalse(); // just above range
        assertThat(HostInfoService.isPrivateIpv4("169.254.1.1")).isFalse(); // link-local
        assertThat(HostInfoService.isPrivateIpv4("not-an-ip")).isFalse();
        assertThat(HostInfoService.isPrivateIpv4("999.1.1.1")).isFalse();
        assertThat(HostInfoService.isPrivateIpv4(null)).isFalse();
    }

    @Test
    void flagsVirtualAndVpnAdapterNames() {
        assertThat(HostInfoService.isBlockedAdapterName("vEthernet (Default Switch)")).isTrue();
        assertThat(HostInfoService.isBlockedAdapterName("Docker Bridge")).isTrue();
        assertThat(HostInfoService.isBlockedAdapterName("VMware Network Adapter VMnet8")).isTrue();
        assertThat(HostInfoService.isBlockedAdapterName("ZeroTier One")).isTrue();
        assertThat(HostInfoService.isBlockedAdapterName("NordLynx")).isTrue();
        assertThat(HostInfoService.isBlockedAdapterName("Tailscale")).isTrue();

        assertThat(HostInfoService.isBlockedAdapterName("Wi-Fi")).isFalse();
        assertThat(HostInfoService.isBlockedAdapterName("Ethernet")).isFalse();
        assertThat(HostInfoService.isBlockedAdapterName(null)).isFalse();
    }

    @Test
    void prefers192Then10Then172() {
        assertThat(HostInfoService.selectBest(List.of("10.0.0.5", "192.168.1.10", "172.16.0.1")))
                .isEqualTo("192.168.1.10");
        assertThat(HostInfoService.selectBest(List.of("172.16.0.1", "10.0.0.5")))
                .isEqualTo("10.0.0.5");
        assertThat(HostInfoService.selectBest(List.of("172.20.1.1")))
                .isEqualTo("172.20.1.1");
        assertThat(HostInfoService.selectBest(List.of())).isNull();
    }

    @Test
    void detectReturnsConsistentHostInfo() {
        // Runs against the real machine; we can't assert a specific IP, but whatever
        // it returns must be internally consistent and only private IPv4 candidates.
        HostInfo info = service().detect();

        assertThat(info.port()).isEqualTo(8080);
        // Defaults are surfaced for the UI: 10GB per file, no file-count limit.
        assertThat(info.maxFileSizeMb()).isEqualTo(10240);
        assertThat(info.maxFilesPerSession()).isEqualTo(0);
        assertThat(info.candidates()).allSatisfy(ip ->
                assertThat(HostInfoService.isPrivateIpv4(ip)).isTrue());

        if (info.localIp() != null) {
            assertThat(HostInfoService.isPrivateIpv4(info.localIp())).isTrue();
            assertThat(info.hostUrl()).isEqualTo("http://" + info.localIp() + ":8080");
            assertThat(info.candidates()).contains(info.localIp());
        } else {
            assertThat(info.hostUrl()).isNull();
            assertThat(info.warnings()).isNotEmpty();
        }
    }
}
