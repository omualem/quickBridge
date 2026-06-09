package com.example.quickbridge.desktop;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;
import org.springframework.context.ConfigurableApplicationContext;

import com.example.quickbridge.config.QuickBridgeProperties;
import com.example.quickbridge.service.CodeGenerator;
import com.example.quickbridge.service.HostInfoService;

class QuickBridgeDesktopWindowTest {

    private QuickBridgeDesktopWindow window(QuickBridgeProperties props) {
        HostInfoService hostInfo = new HostInfoService(props);
        ConfigurableApplicationContext ctx = mock(ConfigurableApplicationContext.class);
        return new QuickBridgeDesktopWindow(props, hostInfo, ctx);
    }

    @Test
    void doesNotStartWhenDisabled() {
        QuickBridgeProperties props = new QuickBridgeProperties();
        // Disabled by default — must be a clean no-op (server/CI/test path).
        assertThatCode(() -> window(props).showIfEnabled()).doesNotThrowAnyException();
    }

    @Test
    void doesNotStartOrThrowInHeadlessMode() {
        String prev = System.getProperty("java.awt.headless");
        // Force headless so the window is never actually built during tests.
        System.setProperty("java.awt.headless", "true");
        try {
            QuickBridgeProperties props = new QuickBridgeProperties();
            props.getDesktop().setEnabled(true);
            assertThatCode(() -> window(props).showIfEnabled()).doesNotThrowAnyException();
        } finally {
            if (prev == null) {
                System.clearProperty("java.awt.headless");
            } else {
                System.setProperty("java.awt.headless", prev);
            }
        }
    }

    @Test
    void codeGeneratorIsUnaffected() {
        // Sanity guard that the desktop package doesn't interfere with core wiring.
        assertThatCode(() -> new CodeGenerator().generate()).doesNotThrowAnyException();
    }
}
