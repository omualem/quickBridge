package com.example.quickbridge.desktop;

import java.awt.BorderLayout;
import java.awt.Desktop;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.net.URI;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.example.quickbridge.config.QuickBridgeProperties;
import com.example.quickbridge.model.HostInfo;
import com.example.quickbridge.service.HostInfoService;

/**
 * A small optional Swing status/control window for the packaged desktop EXE.
 *
 * <p>This is <strong>not</strong> the application UI — QuickBridge stays
 * browser-based. The window simply confirms the local server is running and
 * gives the host user quick actions: open the UI in a browser, copy the local /
 * LAN links to share with a phone, and stop the app cleanly.</p>
 *
 * <p>It is shown only when {@code quickbridge.desktop.enabled=true} (which the
 * jpackage launcher passes) and a desktop is available. In headless mode, CI,
 * tests, or plain server runs it does nothing. Swing is part of {@code
 * java.desktop}, so no extra dependency (and no JavaFX) is needed.</p>
 */
@Component
public class QuickBridgeDesktopWindow {

    private static final Logger log = LoggerFactory.getLogger(QuickBridgeDesktopWindow.class);

    private final QuickBridgeProperties properties;
    private final HostInfoService hostInfoService;
    private final ConfigurableApplicationContext context;

    public QuickBridgeDesktopWindow(QuickBridgeProperties properties,
                                    HostInfoService hostInfoService,
                                    ConfigurableApplicationContext context) {
        this.properties = properties;
        this.hostInfoService = hostInfoService;
        this.context = context;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void showIfEnabled() {
        if (!properties.getDesktop().isEnabled()) {
            return;
        }
        if (isHeadless()) {
            log.info("Desktop window requested but no display is available (headless) — skipping.");
            return;
        }

        // Resolve URLs off the EDT (network enumeration), then build the UI on it.
        String localUrl = "http://localhost:" + properties.getHost().getPort();
        String lanUrl = null;
        try {
            HostInfo info = hostInfoService.detect();
            lanUrl = info.hostUrl(); // may be null if no LAN IP was detected
        } catch (RuntimeException ex) {
            log.warn("Could not detect LAN info for the desktop window", ex);
        }
        String storageDir = properties.getStorage().getDir();

        final String fLan = lanUrl;
        SwingUtilities.invokeLater(() -> {
            try {
                buildAndShow(localUrl, fLan, storageDir);
            } catch (RuntimeException ex) {
                // A UI convenience must never take down the running server.
                log.warn("Failed to show the desktop window; the server keeps running.", ex);
            }
        });
    }

    private boolean isHeadless() {
        return Boolean.getBoolean("java.awt.headless") || GraphicsEnvironment.isHeadless();
    }

    // ---- Swing UI (all on the EDT) -------------------------------------

    private void buildAndShow(String localUrl, String lanUrl, String storageDir) {
        JFrame frame = new JFrame("QuickBridge");
        frame.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);

        JPanel root = new JPanel(new BorderLayout(0, 10));
        root.setBorder(BorderFactory.createEmptyBorder(14, 16, 14, 16));

        // --- Info area ---
        JPanel info = new JPanel();
        info.setLayout(new BoxLayout(info, BoxLayout.Y_AXIS));

        JLabel status = new JLabel("QuickBridge is running");
        status.setFont(status.getFont().deriveFont(Font.BOLD, status.getFont().getSize() + 3f));
        info.add(status);
        info.add(vgap());
        info.add(new JLabel("Local URL:  " + localUrl));
        info.add(new JLabel("LAN URL:    " + (lanUrl != null ? lanUrl : "not detected")));
        if (storageDir != null && !storageDir.isBlank()) {
            info.add(new JLabel("Files:      " + storageDir));
        }
        info.add(vgap());
        info.add(new JLabel("Open the LAN URL or scan the QR in a browser on your phone."));
        info.add(new JLabel("Keep this window open while transferring."));

        // --- Feedback line (copy results / errors) ---
        JLabel feedback = new JLabel(" ");
        feedback.setForeground(new java.awt.Color(0x3a, 0x7d, 0x44));

        // --- Buttons ---
        JButton openBtn = new JButton("Open in browser");
        JButton copyLanBtn = new JButton("Copy LAN link");
        JButton copyLocalBtn = new JButton("Copy local link");
        JButton stopBtn = new JButton("Stop QuickBridge");

        openBtn.addActionListener(e -> openInBrowser(localUrl, feedback));
        copyLocalBtn.addActionListener(e -> copyToClipboard(localUrl, "local", feedback));
        if (lanUrl != null) {
            copyLanBtn.addActionListener(e -> copyToClipboard(lanUrl, "LAN", feedback));
        } else {
            copyLanBtn.setEnabled(false); // no LAN URL to copy
            copyLanBtn.setToolTipText("No LAN IP detected");
        }
        stopBtn.addActionListener(e -> exitApplication(frame));

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        buttons.add(openBtn);
        buttons.add(copyLanBtn);
        buttons.add(copyLocalBtn);
        buttons.add(stopBtn);

        JPanel south = new JPanel(new BorderLayout(0, 6));
        south.add(feedback, BorderLayout.NORTH);
        south.add(buttons, BorderLayout.CENTER);

        root.add(info, BorderLayout.CENTER);
        root.add(south, BorderLayout.SOUTH);
        frame.setContentPane(root);

        // Closing the window stops the app (after confirmation), since this is a
        // desktop-host app and the window is the only foreground control.
        frame.addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent e) {
                confirmAndExit(frame);
            }
        });

        frame.setSize(460, 280);
        frame.setMinimumSize(new java.awt.Dimension(420, 260));
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
        frame.toFront();
        log.info("Desktop status window opened (local {}, LAN {}).",
                localUrl, lanUrl != null ? lanUrl : "not detected");
    }

    private JLabel vgap() {
        JLabel l = new JLabel(" ");
        l.setFont(l.getFont().deriveFont(4f));
        return l;
    }

    private void openInBrowser(String url, JLabel feedback) {
        try {
            if (Desktop.isDesktopSupported()
                    && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(URI.create(url));
                setFeedback(feedback, "Opened " + url, false);
            } else {
                setFeedback(feedback, "Open manually: " + url, true);
            }
        } catch (Exception ex) {
            log.warn("Could not open the browser from the desktop window", ex);
            setFeedback(feedback, "Could not open browser — open " + url + " manually", true);
        }
    }

    private void copyToClipboard(String text, String label, JLabel feedback) {
        try {
            Toolkit.getDefaultToolkit().getSystemClipboard()
                    .setContents(new StringSelection(text), null);
            setFeedback(feedback, "Copied " + label + " link", false);
        } catch (Exception ex) {
            log.warn("Could not copy to clipboard from the desktop window", ex);
            setFeedback(feedback, "Could not copy — " + text, true);
        }
    }

    private void setFeedback(JLabel feedback, String text, boolean error) {
        feedback.setForeground(error ? new java.awt.Color(0xb0, 0x2a, 0x37)
                : new java.awt.Color(0x3a, 0x7d, 0x44));
        feedback.setText(text);
    }

    private void confirmAndExit(JFrame frame) {
        int choice = JOptionPane.showConfirmDialog(frame,
                "Stop QuickBridge? Devices will no longer be able to connect.",
                "QuickBridge", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
        if (choice == JOptionPane.YES_OPTION) {
            exitApplication(frame);
        }
    }

    /** Cleanly shuts down Spring and the JVM, off the EDT so dispose can't block. */
    private void exitApplication(JFrame frame) {
        if (frame != null) {
            frame.dispose();
        }
        Thread shutdown = new Thread(() -> {
            try {
                int code = SpringApplication.exit(context, () -> 0);
                System.exit(code);
            } catch (RuntimeException ex) {
                log.warn("Error during shutdown; forcing exit.", ex);
                System.exit(0);
            }
        }, "quickbridge-shutdown");
        shutdown.setDaemon(false);
        shutdown.start();
    }
}
