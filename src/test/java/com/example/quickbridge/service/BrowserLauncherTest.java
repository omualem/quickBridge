package com.example.quickbridge.service;

import static org.assertj.core.api.Assertions.assertThatCode;

import org.junit.jupiter.api.Test;

import com.example.quickbridge.config.QuickBridgeProperties;

class BrowserLauncherTest {

    @Test
    void doesNothingWhenDisabled() {
        QuickBridgeProperties props = new QuickBridgeProperties();
        // Disabled by default — must be a clean no-op (the common test/server path).
        assertThatCode(() -> new BrowserLauncher(props).openBrowserIfEnabled())
                .doesNotThrowAnyException();
    }

    @Test
    void enabledButHeadlessDoesNotThrowOrOpenBrowser() {
        String prev = System.getProperty("java.awt.headless");
        // Force headless so canOpenBrowser() short-circuits and we never actually
        // launch a browser during the test run.
        System.setProperty("java.awt.headless", "true");
        try {
            QuickBridgeProperties props = new QuickBridgeProperties();
            props.getBrowser().setOpenOnStart(true);
            assertThatCode(() -> new BrowserLauncher(props).openBrowserIfEnabled())
                    .doesNotThrowAnyException();
        } finally {
            if (prev == null) {
                System.clearProperty("java.awt.headless");
            } else {
                System.setProperty("java.awt.headless", prev);
            }
        }
    }
}
