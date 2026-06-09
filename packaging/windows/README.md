# QuickBridge — Windows packaging

These scripts bundle QuickBridge **and a trimmed Java 21 runtime** into a Windows
app you can run on another PC **without installing Java or Maven**. They use
**`jpackage`** and **`jlink`**, which ship with JDK 21.

## How the runtime is bundled (and why)

The scripts build a custom runtime with **`jlink`** into `dist\jre-quickbridge`
and hand it to jpackage via **`--runtime-image`**. This reliably produces
`runtime\bin\java.exe` inside the app — the default jpackage runtime generation
can omit it on some JDK layouts, which causes the dreaded
**"Failed to launch JVM"**. The jlink runtime only includes the modules
QuickBridge needs (`java.base`, `java.desktop` for the Swing window, `java.naming`,
`java.management`, `java.net.http`, `java.security.jgss`, `java.instrument`,
`java.logging`, `jdk.unsupported`).

## Build machine requirements

- **Windows** + a full **JDK 21 with jmods** (Temurin/Adoptium etc.) — provides
  both `jpackage` and `jlink`. Set `JAVA_HOME` to it, or have them on `PATH`.
  (A JRE is not enough; jlink needs the JDK's `jmods`.)
- For the **installer** only: the **WiX Toolset v3** (`light.exe` / `candle.exe`)
  on `PATH` — jpackage uses it to produce a Windows `.exe` installer.
  Download: <https://wixtoolset.org/>. The **app-image** does NOT need WiX.

## Target machine requirements

- **None beyond Windows.** The Java runtime is bundled inside the package, so the
  end user does **not** need Java (or Maven).

---

## Build a portable app-image (recommended, no WiX)

```powershell
powershell -ExecutionPolicy Bypass -File .\packaging\windows\build-app-image.ps1
```

Output:

```
dist\windows-app-image\QuickBridge\QuickBridge.exe
dist\windows-app-image\QuickBridge\runtime\bin\java.exe   <- bundled JVM
```

This is **not** a single standalone EXE — it's a **portable folder** containing
`QuickBridge.exe`, the bundled `runtime\` (Java), and the app jar. Keep the whole
`QuickBridge` folder together; to move it to another PC, copy the entire folder.
The user runs **`QuickBridge.exe`**.

The script verifies `runtime\bin\java.exe` exists and prints `java -version` at
the end, so a broken runtime fails the build instead of shipping.

The launcher starts the server bound to `0.0.0.0:8080`, shows the small desktop
status window (`--quickbridge.desktop.enabled=true`), and does **not** auto-open a
browser (`--quickbridge.browser.open-on-start=false`) — use the window's
**Open in browser** button.

### Normal (GUI) vs debug-console build

By default the app-image is a **GUI app with no console window**: launching
`QuickBridge.exe` shows the small desktop window, and Spring Boot's logs are not
visible to the user (and are turned down to `WARN` for the root logger so they
have nowhere to spam). This is the normal, ship-to-users build.

For troubleshooting, pass **`-DebugConsole`** to attach a console window showing
the live server logs at full verbosity:

```powershell
# Normal: GUI app, no console (what you ship)
powershell -ExecutionPolicy Bypass -File .\packaging\windows\build-app-image.ps1

# Debug: same app but with a console window + verbose logs
powershell -ExecutionPolicy Bypass -File .\packaging\windows\build-app-image.ps1 -DebugConsole
```

> The Swing window only appears because `main()` calls `app.setHeadless(false)`
> and the launcher passes `-Djava.awt.headless=false`. Spring Boot otherwise
> defaults to headless mode, which is what previously caused the
> "no display is available (headless) — skipping" log and a missing window.

---

## Build an installer

```powershell
powershell -ExecutionPolicy Bypass -File .\packaging\windows\build-installer.ps1
```

Output (name includes the version):

```
dist\windows-installer\QuickBridge-0.0.1.exe
```

- The installer is **one EXE** for distribution. After the user installs it, it
  creates an **app folder containing the bundled runtime** (same layout as the
  app-image) — so the installed app needs no system Java either.
- Like the app-image, the installed app is a **GUI app with no console** by
  default; pass **`-DebugConsole`** to build an installer whose app shows a
  console with verbose logs.
- Requires the **WiX Toolset** (see above); jpackage uses it for `.exe`/`.msi`.
- The script passes `--win-menu` and `--win-shortcut`, so the installer can add
  **Start Menu** and **Desktop** shortcuts (and `--win-dir-chooser` lets the user
  pick the install location).
- **Windows SmartScreen** may warn that the app is from an unknown publisher
  because it is unsigned. For production-quality distribution, **code-sign** the
  installer and the bundled EXE.

---

## Running it

1. Run **`QuickBridge.exe`** (portable folder) or launch QuickBridge from the
   Start Menu / Desktop shortcut (installer).
2. **No black console window appears** (normal build). The server starts on
   `http://localhost:8080` in the background, and a **small QuickBridge desktop
   window** appears showing:
   - **Status: running**, the **local URL** and the **LAN URL** (if detected), and
     the storage folder.
   - Buttons: **Open in browser**, **Copy LAN link**, **Copy local link**, and
     **Stop QuickBridge**.
   - (If no LAN IP is detected, the LAN line shows "not detected" and **Copy LAN
     link** is disabled.)
3. On the host PC, the UI is at `http://localhost:8080`.
4. On a **phone on the same Wi-Fi/LAN**, scan the QR in the UI or open
   `http://<host-lan-ip>:8080`. The phone installs nothing — it's just a browser.
   (`localhost` only works on the host itself.)
5. **Keep the QuickBridge window/app running** while transferring — it is the
   server. Closing the window asks to **Stop QuickBridge?** and then exits cleanly.

### Firewall

The **first time** you run it, Windows may pop a **"Windows Defender Firewall"**
prompt. **Allow access on Private networks** so phones on your Wi-Fi can connect.

If you dismissed it or phones still can't connect, add the rule once in an
**elevated (Admin)** prompt:

```bat
netsh advfirewall firewall add rule name="QuickBridge 8080" dir=in action=allow protocol=TCP localport=8080
```
