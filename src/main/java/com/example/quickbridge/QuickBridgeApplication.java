package com.example.quickbridge;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

import com.example.quickbridge.config.QuickBridgeProperties;

/**
 * Entry point for QuickBridge.
 *
 * <p>QuickBridge is a <strong>local LAN host application</strong>. You run it on
 * the computer that should host a session; that computer acts as the server,
 * binding to {@code 0.0.0.0} so other devices on the same Wi-Fi/LAN (phones,
 * tablets, laptops) can connect with a normal browser — no app install needed.</p>
 *
 * <p>Shared text syncs over WebSocket/STOMP; files are uploaded to and downloaded
 * from this computer over REST, with bytes stored temporarily on local disk (no
 * database). Sessions and their temp files are cleaned up automatically when they
 * expire.</p>
 *
 * <p>{@link EnableScheduling} activates the periodic cleanup job in
 * {@code service.CleanupService}.</p>
 */
@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties(QuickBridgeProperties.class)
public class QuickBridgeApplication {

    public static void main(String[] args) {
        SpringApplication app = new SpringApplication(QuickBridgeApplication.class);
        // Spring Boot defaults to headless mode, which would stop the optional
        // Swing desktop window (used by the packaged EXE) from ever opening. Run
        // non-headless so AWT/Swing can create a window when a display exists; on
        // a genuinely headless host the desktop window guards itself and is simply
        // skipped, so this is safe for plain server runs too.
        app.setHeadless(false);
        app.run(args);
    }
}
