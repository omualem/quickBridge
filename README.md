<img width="1886" height="899" alt="3" src="https://github.com/user-attachments/assets/6d8bf70e-9ae7-42b7-a1e3-50881c9ec78d" /><img width="1179" height="2334" alt="2" src="https://github.com/user-attachments/assets/862babf7-0af3-4f9b-befe-08020f3f21a7" /># QuickBridge

A lightweight **local LAN host** app for moving text and files between your
devices. Run it on one computer; open it from your phone, tablet or another
laptop in a normal browser — **no app to install** on the other device.

---

## 1. Project overview

- QuickBridge lets devices **on the same local network** exchange **text** and
  **files**.
- The **host computer** runs the app and acts as the server.
- Other devices connect with a **normal browser** (Chrome, Safari, …).
- **No installation** is needed on the phone/tablet.
- It is designed for **LAN usage**, not public-internet deployment. (No accounts,
  no auth — anyone on your network who has the code can join a session.)
- No VPS, no database, no WebRTC, no cloud.

---

## 2. How it works

- A **Spring Boot** app runs on the host computer.
- It listens on **`0.0.0.0:8080`** so every device on the LAN can reach it.
- The **web UI is served by Spring Boot** itself (static files).
- A phone connects to **`http://<host-lan-ip>:8080`** or **scans the QR code**
  shown on the session page (the QR encodes the LAN URL, not `localhost`).
- **Shared text** is synchronized in real time over **WebSocket/STOMP**.
- **Files** are uploaded to the host computer via **HTTP multipart upload**.
- File **bytes are stored temporarily on the host computer's disk**, not in RAM,
  and are streamed to/from disk.
- Other devices **download** files from the host computer over HTTP.
- A background **cleanup job deletes** a session's temporary files when it
  expires.

```
   Phone browser                Host PC running QuickBridge              Other browser
  (Chrome/Safari)   ───────►   (Spring Boot, disk storage)   ◄───────   (laptop/tablet)
                    HTTP + WebSocket over LAN              HTTP + WebSocket over LAN

  Text  : browser ⇄ host  (WebSocket/STOMP, in-memory per session)
  Files : browser → host (upload to disk) → other browser (download from disk)
```

### Using the UI

- **Create session** (or **Join** with a 5-character code).
- **Shared text**: type in the textarea — it syncs to the other device instantly.
  Use the **Copy text** button to copy the current shared text to your clipboard.
- **Files**: **Choose file → Upload**; everyone sees it and can **Download** or
  **Delete** it.
- **Share**: the session page shows the **LAN URL**, a **Copy link** button, and a
  **QR code** to open the same session on another device.
<img width="442" height="268" alt="image" src="https://github.com/user-attachments/assets/4eeb9fc9-048d-4289-8978-2580e5ef2557" />
<img width="1898" height="903" alt="1" src="https://github.com/user-attachments/assets/f30a23f6-e72a-4c05-a3fd-48ad88457155" />
<img width="1179" height="2334" alt="2" src="https://github.com/user-attachments/assets/15682991-4c22-4cfb-9bd8-800669c00d48" />
<img width="1886" height="899" alt="3" src="https://github.com/user-attachments/assets/a050561f-dc4c-4ad3-800e-075311ae4048" />
---

## 3. Storage model

| Data | Where it lives | Lifetime |
|---|---|---|
| Shared text | In memory, per session | Until session expiry / restart |
| File metadata | In memory, per session | Until session expiry / restart |
| File **bytes** | **On disk**, under `quickbridge.storage.dir` | Until deleted or session expiry |

- **No database. No permanent storage.**
- Files are deleted when **manually removed** or when the **session expires**
  (the cleanup job removes the session directory from disk).
- **Default file count: unlimited.**
- **Default max file size: 10 GB.**
- ⚠️ Large files need **enough free disk space** on the host computer (under the
  storage directory) for the whole upload.

---

## 4. Requirements for development

- **JDK 21** is required to build and run from source.
- **Maven is not required** — the project ships the **Maven Wrapper**
  (`mvnw` / `mvnw.cmd`), which downloads the right Maven automatically.
- Examples below use **Windows PowerShell**. (On macOS/Linux use `./mvnw …`.)

> Check Java: `java -version` should report **21**. If not, set `JAVA_HOME` to a
> JDK 21 install.

---

## 5. Run from source

```powershell
cd C:\Projects\quickBridge
.\mvnw.cmd test
.\mvnw.cmd spring-boot:run
```

It binds to `0.0.0.0:8080` by default (set in `application.properties`).

### Or build and run the JAR

```powershell
.\mvnw.cmd clean package
java -jar .\target\quickbridge-0.0.1-SNAPSHOT.jar --server.address=0.0.0.0 --server.port=8080
```

The actual JAR filename may differ — find it with:

```powershell
dir .\target\*.jar
```

---

## 6. Run helper script (Windows)

```powershell
powershell -ExecutionPolicy Bypass -File .\run-quickbridge.ps1
```

It:

- builds the JAR if one isn't present,
- detects your **LAN IP**,
- prints the **local URL** and **LAN URL**,
- checks for the Windows Firewall rule and **prints firewall guidance** if it's
  missing (the script itself does not require admin),
- starts the app on **`0.0.0.0:8080`**.

---

## 7. Access from your phone

1. The host computer and the phone must be on the **same Wi-Fi/LAN**.
2. On the **host PC**, open the UI at **`http://localhost:8080`**.
3. On the **phone**, **scan the QR** on the session page, or open the LAN URL:
   **`http://<host-lan-ip>:8080`**.
4. **`localhost` does not work from the phone** — to the phone, `localhost` means
   the phone itself. Always use the host's LAN IP (or the QR).
5. If the phone still can't connect, **allow Windows Firewall inbound TCP 8080**.

Firewall command (run once in an **elevated / Administrator** prompt):

```bat
netsh advfirewall firewall add rule name="QuickBridge 8080" dir=in action=allow protocol=TCP localport=8080
```

Remove it later with:

```bat
netsh advfirewall firewall delete rule name="QuickBridge 8080"
```

---

## 8. Configuration

Set in `src/main/resources/application.properties` or pass as `--flags`:

| Property | Default | Meaning |
|---|---|---|
| `server.address` | `0.0.0.0` | bind to all interfaces (LAN-reachable) |
| `server.port` | `8080` | HTTP port |
| `quickbridge.host.port` | `8080` | port advertised in the LAN URL/QR |
| `quickbridge.storage.dir` | `${user.home}/.quickbridge/sessions` | temp file storage dir |
| `quickbridge.session.ttl-minutes` | `30` | session inactivity TTL |
| `quickbridge.file.max-size-mb` | `10240` | max upload size in MB (`<= 0` = no app limit) |
| `quickbridge.file.max-files-per-session` | `0` | per-session file cap (`0` = unlimited) |
| `quickbridge.cleanup.fixed-rate-ms` | `60000` | cleanup sweep interval |
| `quickbridge.browser.open-on-start` | `false` | open the local UI in the default browser on startup |
| `quickbridge.desktop.enabled` | `false` | show the small Swing status/control window (the packaged EXE sets this `true`) |

Notes:

- **`max-files-per-session=0` means unlimited** file count. Any value `> 0`
  enforces that cap.
- **`max-size-mb <= 0` disables application-level size validation.** Spring's
  multipart limits still apply as a backstop, so keep them consistent:
  ```properties
  spring.servlet.multipart.max-file-size=10240MB
  spring.servlet.multipart.max-request-size=10240MB
  ```
- **`spring.servlet.multipart.file-size-threshold=0`** keeps uploads **spooled to
  disk**, never buffered in the JVM heap — important for large files.
- File bytes are always on disk (never `byte[]` in memory); restarting the server
  clears the in-memory session list by design.

---

## 9. Windows EXE packaging

**Goal:** ship QuickBridge as a Windows app that runs on another Windows machine
**without the user installing Java or Maven**.

The approach uses **`jpackage`** + **`jlink`** (both ship with **JDK 21**):

- **`jlink`** builds a trimmed Java 21 runtime into `dist\jre-quickbridge`, and
  jpackage bundles it via **`--runtime-image`**. This guarantees the app ships
  `runtime\bin\java.exe` — fixing the **"Failed to launch JVM"** error that
  happens when the runtime is missing.
- Target users need **no Java**.
- First build an **app-image** (a portable folder); then optionally build an
  **installer**.

When you run the packaged **`QuickBridge.exe`** (normal build):

1. **No black console window** appears — it's a GUI app. The local server starts
   in the background on `0.0.0.0:8080`, and **server logs are hidden** from the
   user (root logger turned down to `WARN`).
2. A **small QuickBridge desktop window** appears (Swing) showing the **status**,
   the **local URL** and **LAN URL**, the storage folder, and buttons to
   **Open in browser**, **Copy LAN link**, **Copy local link**, and **Stop
   QuickBridge**. It does **not** auto-open a browser — click *Open in browser*.
3. The phone installs nothing — it connects through its browser by scanning the
   **QR** or opening the **LAN URL**.
4. On first run, **Windows Firewall may prompt** — **allow Private networks** so
   phones on your Wi-Fi can reach the host.

> The window appears because `main()` runs non-headless (`app.setHeadless(false)`)
> and the launcher passes `-Djava.awt.headless=false`; Spring Boot otherwise
> defaults to headless mode (the old cause of the missing window). For
> troubleshooting, build with **`-DebugConsole`** to get a console + verbose logs.

Ready-made scripts live in [`packaging/windows/`](packaging/windows/) — see
[`packaging/windows/README.md`](packaging/windows/README.md) for full details.

### Build Windows portable app-image

```powershell
powershell -ExecutionPolicy Bypass -File .\packaging\windows\build-app-image.ps1
```

Output:

```
dist\windows-app-image\QuickBridge\QuickBridge.exe
dist\windows-app-image\QuickBridge\runtime\bin\java.exe   (bundled JVM)
```

- This is **not** a single standalone EXE. It is a **portable folder** containing
  `QuickBridge.exe`, the bundled `runtime\` (Java), and the app jar.
- **Keep the whole folder together** — to move it to another PC, copy the entire
  `QuickBridge` folder.
- The script verifies `runtime\bin\java.exe` exists (and prints `java -version`)
  before declaring success.
- Build machine needs a full **JDK 21 with jmods** (jlink needs them); the app
  image does **not** need WiX.
- **No console by default.** Add **`-DebugConsole`** for a console + verbose logs:
  ```powershell
  powershell -ExecutionPolicy Bypass -File .\packaging\windows\build-app-image.ps1 -DebugConsole
  ```

### Build Windows installer

```powershell
powershell -ExecutionPolicy Bypass -File .\packaging\windows\build-installer.ps1
```

Output (name includes the version):

```
dist\windows-installer\QuickBridge-0.0.1.exe
```

- The installer is **one EXE** for distribution; after installation it creates an
  **app folder containing the bundled runtime**, so the installed app needs no
  system Java either.
- It can create **Start Menu** and **Desktop** shortcuts.
- jpackage needs the **WiX Toolset v3** on `PATH` to build Windows `.exe`/`.msi`
  installers (the app-image does **not**). Get it from <https://wixtoolset.org/>.
- **Windows SmartScreen** may warn for unsigned apps; **code signing** is needed
  for production-quality distribution.

---

## 10. Testing

```powershell
.\mvnw.cmd test
```

- The **Copy text** button is a frontend-only feature (no backend tests needed).
- The optional **browser-open** and **desktop window** features are **off by
  default** and short-circuit on headless/CI runs, so they never disturb tests;
  both have focused unit tests that assert the disabled and headless paths do
  nothing and never throw.
- The packaging scripts are PowerShell and are not part of the automated test
  run (they are syntax/parse-clean and were verified by building a runnable
  app-image).

---

## Project structure

```
mvnw, mvnw.cmd               # Maven Wrapper launchers (no system Maven needed)
run-quickbridge.ps1          # Windows build+run helper (LAN IP + firewall hints)
packaging/windows/           # jpackage scripts: app-image + installer (+ README)
pom.xml
src/main/java/com/example/quickbridge/
  QuickBridgeApplication.java
  config/      WebSocketConfig, WebMvcConfig, QuickBridgeProperties
  controller/  SessionController, FileController, HostInfoController,
               PageController, ApiResponse
  websocket/   SessionSocketController, WebSocketEventPublisher
  service/     SessionService, DiskFileStorage, HostInfoService,
               CodeGenerator, CleanupService, BrowserLauncher
  desktop/     QuickBridgeDesktopWindow   # optional Swing status/control window
  model/       TransferSession, StoredFileMetadata, FileMetadata,
               SessionSnapshot, TextUpdateMessage, SocketEvent, HostInfo
  error/       ApiException, ErrorCode, GlobalExceptionHandler
src/main/resources/static/   index.html, app.js, styles.css
  vendor/    qrcode.js   # locally vendored QR generator (no runtime CDN)
```

---

## HTTP API (reference)

All success responses use `{ "success": true, "data": ... }`; errors use
`{ "success": false, "error": { "code": "...", "message": "..." } }`.

| Method & path | Purpose |
|---|---|
| `POST /api/sessions` | Create a session → `{ code, url, expiresAt }` |
| `GET /api/sessions/{code}` | Snapshot: text + file metadata (no disk paths) |
| `POST /api/sessions/{code}/files` | Multipart upload (field `file`) → streams to disk |
| `GET /api/sessions/{code}/files/{fileId}` | Stream the file back from disk |
| `DELETE /api/sessions/{code}/files/{fileId}` | Delete file from disk + metadata |
| `GET /api/host-info` | LAN URL details + file limits for the UI |

WebSocket (STOMP over SockJS): connect `/ws`, send text to
`/app/sessions/{code}/text`, subscribe to `/topic/sessions/{code}`. Broadcast
events: `TEXT_UPDATED`, `FILE_ADDED`, `FILE_DELETED`, `SESSION_EXPIRED`, `ERROR`.

---

## Future improvements

- Optional password per session.
- Resumable / chunked uploads for very large files.
- mDNS/Bonjour name (e.g. `quickbridge.local`) so you don't need the IP.
- Code-signed installers.
