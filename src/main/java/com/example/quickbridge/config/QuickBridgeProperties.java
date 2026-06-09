package com.example.quickbridge.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Strongly-typed configuration for QuickBridge, bound from {@code quickbridge.*}
 * properties in {@code application.properties}.
 *
 * <p>Defaults here mirror the documented values so the app runs sensibly even
 * with an empty properties file.</p>
 */
@ConfigurationProperties(prefix = "quickbridge")
public class QuickBridgeProperties {

    private final Host host = new Host();
    private final Storage storage = new Storage();
    private final Session session = new Session();
    private final File file = new File();
    private final Cleanup cleanup = new Cleanup();
    private final Browser browser = new Browser();
    private final Desktop desktop = new Desktop();

    public Host getHost() {
        return host;
    }

    public Storage getStorage() {
        return storage;
    }

    public Session getSession() {
        return session;
    }

    public File getFile() {
        return file;
    }

    public Cleanup getCleanup() {
        return cleanup;
    }

    public Browser getBrowser() {
        return browser;
    }

    public Desktop getDesktop() {
        return desktop;
    }

    /** The port advertised in the LAN URL (should match {@code server.port}). */
    public static class Host {
        private int port = 8080;

        public int getPort() {
            return port;
        }

        public void setPort(int port) {
            this.port = port;
        }
    }

    /** Where uploaded file bytes are written on the host computer's disk. */
    public static class Storage {
        private String dir = System.getProperty("user.home") + "/.quickbridge/sessions";

        public String getDir() {
            return dir;
        }

        public void setDir(String dir) {
            this.dir = dir;
        }
    }

    /** Session lifetime. */
    public static class Session {
        private long ttlMinutes = 30;

        public long getTtlMinutes() {
            return ttlMinutes;
        }

        public void setTtlMinutes(long ttlMinutes) {
            this.ttlMinutes = ttlMinutes;
        }
    }

    /**
     * Per-file and per-session upload limits.
     *
     * <p>Both limits support an "unlimited" sentinel of {@code <= 0}:
     * {@code maxFilesPerSession <= 0} disables the file-count cap entirely, and
     * {@code maxSizeMb <= 0} disables the application-level size check (Spring's
     * own multipart limit still applies as a backstop).</p>
     */
    public static class File {
        // Defaults tuned for LAN hosting on the user's own disk: no file-count
        // limit, and a generous 10GB per-file ceiling.
        private long maxSizeMb = 10240;        // 10 GB
        private int maxFilesPerSession = 0;    // 0 = unlimited

        public long getMaxSizeMb() {
            return maxSizeMb;
        }

        public void setMaxSizeMb(long maxSizeMb) {
            this.maxSizeMb = maxSizeMb;
        }

        /**
         * Max upload size in bytes, or {@code <= 0} when no application-level
         * limit is enforced (see {@link #hasSizeLimit()}).
         */
        public long getMaxSizeBytes() {
            return maxSizeMb <= 0 ? -1L : maxSizeMb * 1024L * 1024L;
        }

        /** True if an application-level per-file size limit is enforced. */
        public boolean hasSizeLimit() {
            return maxSizeMb > 0;
        }

        public int getMaxFilesPerSession() {
            return maxFilesPerSession;
        }

        public void setMaxFilesPerSession(int maxFilesPerSession) {
            this.maxFilesPerSession = maxFilesPerSession;
        }

        /** True if a per-session file-count limit is enforced. */
        public boolean hasFileCountLimit() {
            return maxFilesPerSession > 0;
        }
    }

    /** How often the cleanup sweep runs. */
    public static class Cleanup {
        private long fixedRateMs = 60_000;

        public long getFixedRateMs() {
            return fixedRateMs;
        }

        public void setFixedRateMs(long fixedRateMs) {
            this.fixedRateMs = fixedRateMs;
        }
    }

    /** Convenience: open the local UI in the default browser on startup. */
    public static class Browser {
        // Off by default so server/CI runs and tests don't pop a browser. The
        // packaged desktop window offers an "Open in browser" button instead.
        private boolean openOnStart = false;

        public boolean isOpenOnStart() {
            return openOnStart;
        }

        public void setOpenOnStart(boolean openOnStart) {
            this.openOnStart = openOnStart;
        }
    }

    /** Small Swing status/control window shown by the packaged desktop EXE. */
    public static class Desktop {
        // Off by default so server/CI/headless runs and tests never open a window.
        // The jpackage launcher passes --quickbridge.desktop.enabled=true.
        private boolean enabled = false;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }
}
