/* QuickBridge frontend — local LAN host app.
 *
 * One static page serves both the landing view and the session view. The path
 * decides which: "/" shows landing, "/{CODE}" auto-joins that session.
 *
 * Architecture: this computer is the SERVER. Other devices on the same Wi-Fi/LAN
 * reach it via its LAN IP. Real-time text sync uses STOMP over SockJS; files are
 * uploaded to / downloaded from this computer over REST (bytes live on the host's
 * disk, not in the browser).
 *
 *   - subscribe to /topic/sessions/{code}
 *   - send text to    /app/sessions/{code}/text
 *   - REST handles session creation and file upload/download/delete
 *   - GET /api/host-info gives the LAN URL used for the QR/share link
 */
(function () {
    "use strict";

    const CODE_RE = /^[A-HJ-NP-Z2-9]{5}$/;
    const MAX_TEXT = 100000;
    const TEXT_DEBOUNCE_MS = 200;
    // Fallback per-file ceiling used only if the host limits can't be fetched.
    const DEFAULT_MAX_FILE_MB = 10240; // 10GB

    // --- DOM refs ---
    const els = {
        landing: document.getElementById("landing"),
        session: document.getElementById("session"),
        connection: document.getElementById("connection"),
        createBtn: document.getElementById("createBtn"),
        joinForm: document.getElementById("joinForm"),
        joinInput: document.getElementById("joinInput"),
        landingError: document.getElementById("landingError"),
        codeLabel: document.getElementById("codeLabel"),
        shareUrl: document.getElementById("shareUrl"),
        copyBtn: document.getElementById("copyBtn"),
        lanWarning: document.getElementById("lanWarning"),
        qrcode: document.getElementById("qrcode"),
        textArea: document.getElementById("textArea"),
        charCount: document.getElementById("charCount"),
        textStatus: document.getElementById("textStatus"),
        copyTextBtn: document.getElementById("copyTextBtn"),
        copyTextStatus: document.getElementById("copyTextStatus"),
        fileInput: document.getElementById("fileInput"),
        uploadBtn: document.getElementById("uploadBtn"),
        uploadRow: document.getElementById("uploadRow"),
        uploadName: document.getElementById("uploadName"),
        uploadProgressWrap: document.getElementById("uploadProgressWrap"),
        uploadProgress: document.getElementById("uploadProgress"),
        uploadStatus: document.getElementById("uploadStatus"),
        fileList: document.getElementById("fileList"),
        fileCount: document.getElementById("fileCount"),
        emptyFiles: document.getElementById("emptyFiles"),
        limitInfo: document.getElementById("limitInfo"),
        sessionError: document.getElementById("sessionError"),
    };

    let stompClient = null;
    let currentCode = null;
    let hostInfo = null; // { localIp, port, hostUrl, candidates, warnings }
    let pendingFile = null;
    // Guards the textarea against echoing a remote update straight back to the server.
    let applyingRemote = false;
    let textTimer = null;

    // ---------------- Helpers ----------------

    function showLanding() {
        els.session.classList.add("hidden");
        els.landing.classList.remove("hidden");
    }

    function showSession() {
        els.landing.classList.add("hidden");
        els.session.classList.remove("hidden");
    }

    function setConnection(state, label) {
        els.connection.textContent = label;
        els.connection.className = "status status--" + state;
    }

    function showError(el, msg) {
        el.textContent = msg;
        el.classList.remove("hidden");
    }

    function clearError(el) {
        el.textContent = "";
        el.classList.add("hidden");
    }

    function formatSize(bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + " KB";
        return (bytes / (1024 * 1024)).toFixed(1) + " MB";
    }

    function isLocalhostOrigin() {
        const h = window.location.hostname;
        return h === "localhost" || h === "127.0.0.1" || h === "::1";
    }

    // ---- File limits (driven by the host's configuration) ----

    function maxFileMb() {
        if (!hostInfo) return DEFAULT_MAX_FILE_MB;        // host-info unavailable
        return hostInfo.maxFileSizeMb;                    // may be <= 0 (unlimited)
    }

    // Client-side pre-upload ceiling in bytes; Infinity means "no app-level limit".
    function clientMaxBytes() {
        const mb = maxFileMb();
        if (mb <= 0) return Infinity;
        return mb * 1024 * 1024;
    }

    function formatMb(mb) {
        if (mb % 1024 === 0) return (mb / 1024) + "GB";
        if (mb >= 1024) return (mb / 1024).toFixed(1) + "GB";
        return mb + "MB";
    }

    // Human-readable size limit. Never claims truly infinite size.
    function sizeLimitLabel() {
        const mb = maxFileMb();
        if (mb <= 0) return "Maximum file size: limited only by free disk space on the host";
        return "Maximum file size: " + formatMb(mb);
    }

    // Human-readable file-count limit.
    function countLimitLabel() {
        const max = hostInfo ? hostInfo.maxFilesPerSession : 0;
        return (max > 0) ? ("Up to " + max + " files per session") : "No file count limit";
    }

    function renderLimitInfo() {
        if (els.limitInfo) els.limitInfo.textContent = countLimitLabel() + " · " + sizeLimitLabel();
    }

    async function api(method, url, body) {
        const opts = { method, headers: {} };
        if (body instanceof FormData) {
            opts.body = body;
        } else if (body !== undefined) {
            opts.headers["Content-Type"] = "application/json";
            opts.body = JSON.stringify(body);
        }
        const res = await fetch(url, opts);
        let json = null;
        try { json = await res.json(); } catch (_) { /* e.g. 204 No Content */ }
        if (!res.ok || (json && json.success === false)) {
            const message = json && json.error ? json.error.message : "Request failed (" + res.status + ")";
            throw new Error(message);
        }
        return json ? json.data : null;
    }

    // ---------------- Host info (LAN URL) ----------------

    async function loadHostInfo() {
        try {
            hostInfo = await api("GET", "/api/host-info");
        } catch (_) {
            hostInfo = null; // fall back to window.location.origin
        }
    }

    // Build the shareable session URL. Prefer the detected LAN URL so the QR/link
    // works from other devices; otherwise fall back to the current origin.
    function shareLink(code) {
        if (hostInfo && hostInfo.hostUrl) {
            return hostInfo.hostUrl + "/" + code;
        }
        return window.location.origin + "/" + code;
    }

    // Decide what (if any) warning to show under the share link.
    function lanWarningMessage() {
        if (!hostInfo || !hostInfo.hostUrl) {
            const serverWarn = hostInfo && hostInfo.warnings && hostInfo.warnings.length
                ? hostInfo.warnings[0]
                : "Could not detect this computer's LAN IP.";
            return serverWarn + " Using this page's address instead — it may only work on this computer.";
        }
        if (isLocalhostOrigin()) {
            return "localhost works only on this computer. Scan the LAN QR or open the LAN URL from your phone.";
        }
        return null;
    }

    // ---------------- Landing actions ----------------

    async function createSession() {
        clearError(els.landingError);
        els.createBtn.disabled = true;
        try {
            const data = await api("POST", "/api/sessions");
            history.pushState({}, "", data.url);
            await joinSession(data.code);
        } catch (e) {
            showError(els.landingError, e.message);
        } finally {
            els.createBtn.disabled = false;
        }
    }

    els.createBtn.addEventListener("click", createSession);

    els.joinForm.addEventListener("submit", function (e) {
        e.preventDefault();
        clearError(els.landingError);
        const code = els.joinInput.value.trim().toUpperCase();
        if (!CODE_RE.test(code)) {
            showError(els.landingError, "Enter a valid 5-character code.");
            return;
        }
        history.pushState({}, "", "/" + code);
        joinSession(code);
    });

    // ---------------- Session ----------------

    async function joinSession(code) {
        currentCode = code;
        try {
            const snapshot = await api("GET", "/api/sessions/" + code);
            renderSession(snapshot);
            connectSocket(code);
        } catch (e) {
            showError(els.landingError, e.message || "Session not found or expired");
            showLanding();
            history.replaceState({}, "", "/");
        }
    }

    function renderSession(snapshot) {
        showSession();
        clearError(els.sessionError);
        els.codeLabel.textContent = snapshot.code;
        const link = shareLink(snapshot.code);
        els.shareUrl.textContent = link;
        els.shareUrl.href = link;
        renderQr(link);

        const warn = lanWarningMessage();
        if (warn) {
            showError(els.lanWarning, warn);
        } else {
            clearError(els.lanWarning);
        }

        applyingRemote = true;
        els.textArea.value = snapshot.text || "";
        applyingRemote = false;
        updateCharCount();

        renderFiles(snapshot.files || []);
        renderLimitInfo();
        resetUploadRow();
    }

    function updateCharCount() {
        const len = els.textArea.value.length;
        els.charCount.textContent = len.toLocaleString() + " / 100,000";
        // The Copy text button is disabled when there's nothing to copy.
        if (els.copyTextBtn) els.copyTextBtn.disabled = len === 0;
    }

    // Renders a QR code (scalable SVG) pointing at the LAN session URL.
    function renderQr(url) {
        if (!els.qrcode) return;
        if (typeof qrcode === "undefined") {
            els.qrcode.classList.add("hidden");
            return;
        }
        try {
            const qr = qrcode(0, "M");
            qr.addData(url);
            qr.make();
            els.qrcode.innerHTML = qr.createSvgTag({ cellSize: 4, margin: 2, scalable: true });
            els.qrcode.classList.remove("hidden");
        } catch (_) {
            els.qrcode.classList.add("hidden");
        }
    }

    // ---------------- WebSocket ----------------

    function connectSocket(code) {
        if (stompClient) {
            try { stompClient.disconnect(); } catch (_) {}
        }
        setConnection("idle", "connecting…");
        const socket = new SockJS("/ws");
        stompClient = Stomp.over(socket);
        stompClient.debug = null; // silence verbose frame logging

        stompClient.connect({}, function () {
            setConnection("ok", "connected");
            stompClient.subscribe("/topic/sessions/" + code, function (frame) {
                handleEvent(JSON.parse(frame.body));
            });
        }, function () {
            setConnection("bad", "disconnected");
            if (currentCode === code) {
                setTimeout(function () { connectSocket(code); }, 2000);
            }
        });

        socket.onclose = function () {
            setConnection("bad", "disconnected");
        };
    }

    function handleEvent(event) {
        switch (event.type) {
            case "TEXT_UPDATED":
                if (event.payload && event.payload.text !== els.textArea.value) {
                    applyingRemote = true;
                    const pos = els.textArea.selectionStart;
                    els.textArea.value = event.payload.text;
                    try { els.textArea.setSelectionRange(pos, pos); } catch (_) {}
                    applyingRemote = false;
                    updateCharCount();
                }
                break;
            case "FILE_ADDED":
            case "FILE_DELETED":
                // Simplest correct approach: re-pull the authoritative snapshot.
                refreshFiles();
                break;
            case "SESSION_EXPIRED":
                setConnection("bad", "expired");
                showError(els.sessionError, "This session has expired. Its files were deleted from this computer.");
                break;
            case "ERROR":
                if (event.payload) showError(els.sessionError, event.payload.message);
                break;
            default:
                break;
        }
    }

    // ---------------- Text sync (debounced) ----------------

    els.textArea.addEventListener("input", function () {
        updateCharCount();
        if (applyingRemote) return;
        els.textStatus.textContent = "typing…";
        clearTimeout(textTimer);
        textTimer = setTimeout(sendText, TEXT_DEBOUNCE_MS);
    });

    function sendText() {
        if (!stompClient || !stompClient.connected || !currentCode) return;
        const text = els.textArea.value.slice(0, MAX_TEXT);
        stompClient.send("/app/sessions/" + currentCode + "/text", {}, JSON.stringify({ text: text }));
        els.textStatus.textContent = "synced";
        setTimeout(function () {
            if (els.textStatus.textContent === "synced") els.textStatus.textContent = "";
        }, 1200);
    }

    // ---------------- Copy text (client-only) ----------------
    //
    // Copies the current shared text to the clipboard. This is purely local: it
    // does NOT send a WebSocket message, touch the server, or affect text sync.

    els.copyTextBtn.addEventListener("click", function () {
        const text = els.textArea.value;
        if (text.length === 0) return; // button is disabled, but guard anyway
        copyToClipboard(text);
    });

    function copyToClipboard(text) {
        // Prefer the modern async Clipboard API (needs a secure context).
        if (navigator.clipboard && navigator.clipboard.writeText) {
            navigator.clipboard.writeText(text)
                .then(function () { copyFeedback(true); })
                .catch(function () {
                    // Some browsers reject over plain http on a LAN IP — fall back.
                    if (!legacyCopy(text)) copyFeedback(false);
                    else copyFeedback(true);
                });
            return;
        }
        copyFeedback(legacyCopy(text));
    }

    // Fallback for browsers without the Clipboard API (e.g. http on older Safari).
    function legacyCopy(text) {
        const ta = els.textArea;
        const prevStart = ta.selectionStart;
        const prevEnd = ta.selectionEnd;
        let ok = false;
        try {
            ta.focus();
            ta.select();
            ok = document.execCommand("copy");
        } catch (_) {
            ok = false;
        }
        // Restore the previous caret/selection so we don't disturb the user.
        try { ta.setSelectionRange(prevStart, prevEnd); } catch (_) {}
        return ok;
    }

    function copyFeedback(ok) {
        const el = els.copyTextStatus;
        el.textContent = ok ? "Copied" : "Failed to copy";
        el.classList.toggle("copy-status--ok", ok);
        el.classList.toggle("copy-status--bad", !ok);
        setTimeout(function () {
            el.textContent = "";
            el.classList.remove("copy-status--ok", "copy-status--bad");
        }, 2000);
    }

    // ---------------- Files ----------------

    async function refreshFiles() {
        if (!currentCode) return;
        try {
            const snapshot = await api("GET", "/api/sessions/" + currentCode);
            renderFiles(snapshot.files || []);
        } catch (_) { /* session may have just expired */ }
    }

    function renderFiles(files) {
        els.fileList.innerHTML = "";
        const max = hostInfo ? hostInfo.maxFilesPerSession : 0;
        els.fileCount.textContent = (max > 0)
            ? "(" + files.length + "/" + max + ")"
            : "(" + files.length + ")";
        els.emptyFiles.classList.toggle("hidden", files.length > 0);

        files.forEach(function (f) {
            const li = document.createElement("li");
            li.className = "file-item";

            const name = document.createElement("span");
            name.className = "fi-name";
            name.textContent = f.originalFilename;
            name.title = f.originalFilename;

            const size = document.createElement("span");
            size.className = "fi-size";
            size.textContent = formatSize(f.size);

            const dl = document.createElement("a");
            dl.className = "icon-btn";
            dl.href = "/api/sessions/" + currentCode + "/files/" + f.id;
            dl.title = "Download";
            dl.textContent = "⬇";
            dl.setAttribute("download", f.originalFilename);

            const del = document.createElement("button");
            del.className = "icon-btn icon-btn--danger";
            del.title = "Delete";
            del.textContent = "🗑";
            del.addEventListener("click", function () { deleteFile(f.id); });

            li.append(name, size, dl, del);
            els.fileList.appendChild(li);
        });
    }

    // Pick a file, then upload on the explicit Upload button click.
    els.fileInput.addEventListener("change", function () {
        const file = els.fileInput.files[0];
        clearError(els.sessionError);
        if (!file) { resetUploadRow(); return; }

        const limit = clientMaxBytes();
        if (limit !== Infinity && file.size > limit) {
            showError(els.sessionError, "File exceeds the " + formatMb(maxFileMb()) + " limit.");
            resetUploadRow();
            return;
        }
        pendingFile = file;
        els.uploadName.textContent = file.name + "  (" + formatSize(file.size) + ")";
        els.uploadRow.classList.remove("hidden");
        els.uploadProgressWrap.classList.add("hidden");
        els.uploadProgress.style.width = "0%";
        els.uploadStatus.textContent = "";
        els.uploadBtn.disabled = false;
    });

    els.uploadBtn.addEventListener("click", function () {
        if (pendingFile) uploadFile(pendingFile);
    });

    // Upload via XHR so we can show progress for large files. The bytes stream to
    // the host computer and are written to its disk.
    function uploadFile(file) {
        clearError(els.sessionError);
        els.uploadBtn.disabled = true;
        els.fileInput.disabled = true;
        els.uploadProgressWrap.classList.remove("hidden");
        els.uploadStatus.textContent = "uploading…";

        const form = new FormData();
        form.append("file", file);

        const xhr = new XMLHttpRequest();
        xhr.open("POST", "/api/sessions/" + currentCode + "/files");

        xhr.upload.onprogress = function (e) {
            if (e.lengthComputable) {
                els.uploadProgress.style.width = ((e.loaded / e.total) * 100).toFixed(1) + "%";
            }
        };

        xhr.onload = function () {
            els.fileInput.disabled = false;
            let json = null;
            try { json = JSON.parse(xhr.responseText); } catch (_) {}
            if (xhr.status >= 200 && xhr.status < 300 && json && json.success) {
                els.uploadStatus.textContent = "✓ uploaded";
                // FILE_ADDED broadcast refreshes the list on every device.
                setTimeout(resetUploadRow, 800);
            } else {
                const msg = json && json.error ? json.error.message : "Upload failed (" + xhr.status + ")";
                showError(els.sessionError, msg);
                els.uploadStatus.textContent = "✕ failed";
                els.uploadBtn.disabled = false;
            }
        };

        xhr.onerror = function () {
            els.fileInput.disabled = false;
            showError(els.sessionError, "Upload failed — is the host still running?");
            els.uploadStatus.textContent = "✕ failed";
            els.uploadBtn.disabled = false;
        };

        xhr.send(form);
    }

    function resetUploadRow() {
        pendingFile = null;
        els.fileInput.value = "";
        els.fileInput.disabled = false;
        els.uploadBtn.disabled = true;
        els.uploadRow.classList.add("hidden");
        els.uploadProgress.style.width = "0%";
        els.uploadProgressWrap.classList.add("hidden");
        els.uploadStatus.textContent = "";
        els.uploadName.textContent = "";
    }

    async function deleteFile(fileId) {
        clearError(els.sessionError);
        try {
            await api("DELETE", "/api/sessions/" + currentCode + "/files/" + fileId);
        } catch (e) {
            showError(els.sessionError, e.message);
        }
    }

    // ---------------- Share link ----------------

    els.copyBtn.addEventListener("click", async function () {
        const link = shareLink(currentCode);
        try {
            await navigator.clipboard.writeText(link);
            els.copyBtn.textContent = "Copied!";
            setTimeout(function () { els.copyBtn.textContent = "Copy link"; }, 1500);
        } catch (_) {
            showError(els.sessionError, "Copy failed — link: " + link);
        }
    });

    // ---------------- Boot ----------------

    async function boot() {
        // Fetch the LAN URL first so the QR/share link is correct on first render.
        await loadHostInfo();
        const path = window.location.pathname.replace(/^\//, "").toUpperCase();
        if (CODE_RE.test(path)) {
            joinSession(path);
        } else {
            showLanding();
        }
    }

    window.addEventListener("popstate", boot);
    boot();
})();
