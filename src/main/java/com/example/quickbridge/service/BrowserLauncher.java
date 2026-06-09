package com.example.quickbridge.service;

import java.awt.Desktop;
import java.net.URI;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.example.quickbridge.config.QuickBridgeProperties;

/**
 * Optionally opens the local QuickBridge UI in the host computer's default
 * browser once the app has finished starting.
 *
 * <p>Enabled with {@code quickbridge.browser.open-on-start=true} (off by default,
 * so headless servers, CI and tests are never disturbed). The packaged desktop
 * launcher turns it on so double-clicking the EXE pops the UI automatically.</p>
 *
 * <p>It only attempts to open a browser when running with a desktop available
 * (not headless, {@link Desktop} supported with the BROWSE action). Any failure
 * is logged and swallowed — being unable to open a browser must never crash the
 * server.</p>
 */
@Component
public class BrowserLauncher {

    private static final Logger log = LoggerFactory.getLogger(BrowserLauncher.class);

    private final QuickBridgeProperties properties;

    public BrowserLauncher(QuickBridgeProperties properties) {
        this.properties = properties;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void openBrowserIfEnabled() {
        if (!properties.getBrowser().isOpenOnStart()) {
            return;
        }
        // localhost is always correct on the host machine itself.
        String url = "http://localhost:" + properties.getHost().getPort();
        try {
            if (!canOpenBrowser()) {
                log.info("Skipping browser open (no desktop available). Open {} manually.", url);
                return;
            }
            Desktop.getDesktop().browse(URI.create(url));
            log.info("Opened {} in the default browser.", url);
        } catch (Exception ex) {
            // Never let a UI convenience take down the server.
            log.warn("Could not open the browser automatically. Open {} manually.", url, ex);
        }
    }

    /** True if this JVM has a usable desktop that can browse to a URL. */
    private boolean canOpenBrowser() {
        if (Boolean.getBoolean("java.awt.headless") || java.awt.GraphicsEnvironment.isHeadless()) {
            return false;
        }
        return Desktop.isDesktopSupported()
                && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE);
    }
}
