# Builds a Windows installer (.exe) for QuickBridge using jpackage.
#
# Output: dist\windows-installer\QuickBridge-<version>.exe
#   (jpackage names it from the app name + version, e.g. QuickBridge-0.0.1.exe)
#
# The installer bundles a trimmed Java runtime (built with jlink), so the target
# machine does NOT need Java or Maven. After installation it creates an app folder
# containing the runtime; it can also add Start Menu and Desktop shortcuts.
#
# Build requirements:
#   - Windows + JDK 21 with jmods (jpackage + jlink ship with the JDK)
#   - WiX Toolset 3.x on PATH (light.exe / candle.exe). jpackage uses WiX to build
#     Windows .exe/.msi installers. Without it, installer generation fails - use
#     build-app-image.ps1 instead, which needs no WiX.
#     Download: https://wixtoolset.org/  (WiX v3)
#
# By default the installed app is a GUI app with NO console window (a small Swing
# status window opens instead). Pass -DebugConsole to build an installer whose app
# attaches a console showing live server logs for troubleshooting.

param(
    [switch]$DebugConsole
)

$ErrorActionPreference = "Stop"

# --- Resolve repo root (this script lives in packaging\windows) ------------
$RepoRoot = (Get-Item $PSScriptRoot).Parent.Parent.FullName
Set-Location $RepoRoot
Write-Host "== QuickBridge: build Windows installer ==" -ForegroundColor Cyan
Write-Host "Repo root: $RepoRoot"

$Version    = "0.0.1"
$OutDir     = Join-Path $RepoRoot "dist\windows-installer"
$StageDir   = Join-Path $RepoRoot "target\jpackage-input"
$RuntimeDir = Join-Path $RepoRoot "dist\jre-quickbridge"

# --- Locate JDK tools (prefer JAVA_HOME, fall back to PATH) -----------------
function Resolve-JdkTool([string]$name) {
    if ($env:JAVA_HOME) {
        $candidate = Join-Path $env:JAVA_HOME ("bin\" + $name + ".exe")
        if (Test-Path $candidate) { return $candidate }
    }
    return $name # rely on PATH
}
$JPackage = Resolve-JdkTool "jpackage"
$JLink    = Resolve-JdkTool "jlink"

try { & $JPackage --version | Out-Null } catch {
    Write-Host "jpackage was not found. Install JDK 21 and set JAVA_HOME to it." -ForegroundColor Red
    exit 1
}
try { & $JLink --version | Out-Null } catch {
    Write-Host "jlink was not found. Use a full JDK 21 (with jmods), not a JRE, and set JAVA_HOME to it." -ForegroundColor Red
    exit 1
}

# --- Friendly check for WiX (jpackage needs it for .exe installers) --------
$wixFound = $false
foreach ($tool in @("light.exe", "candle.exe")) {
    if (Get-Command $tool -ErrorAction SilentlyContinue) { $wixFound = $true }
}
if (-not $wixFound) {
    Write-Host "WiX Toolset (light.exe/candle.exe) was not found on PATH." -ForegroundColor Yellow
    Write-Host "jpackage needs WiX v3 to build a Windows .exe installer." -ForegroundColor Yellow
    Write-Host "Install it from https://wixtoolset.org/ or run build-app-image.ps1 (no WiX needed)." -ForegroundColor Yellow
    Write-Host "Continuing anyway - jpackage will report the precise error if WiX is missing.`n"
}

# --- Build the Spring Boot jar ---------------------------------------------
Write-Host "Building jar (mvnw clean package)..." -ForegroundColor Cyan
& .\mvnw.cmd clean package
if ($LASTEXITCODE -ne 0) {
    Write-Host "Maven build failed." -ForegroundColor Red
    exit $LASTEXITCODE
}

$Jar = Get-ChildItem -Path (Join-Path $RepoRoot "target") -Filter "quickbridge-*.jar" |
    Where-Object { $_.Name -notlike "*.original" } |
    Sort-Object LastWriteTime -Descending |
    Select-Object -First 1
if (-not $Jar) {
    Write-Host "No quickbridge-*.jar found under target\." -ForegroundColor Red
    exit 1
}
Write-Host "Using jar: $($Jar.Name)"

# --- Stage a clean input dir holding only the jar --------------------------
if (Test-Path $StageDir) { Remove-Item -Recurse -Force $StageDir }
New-Item -ItemType Directory -Force -Path $StageDir | Out-Null
Copy-Item $Jar.FullName -Destination $StageDir

# --- Build a trimmed Java 21 runtime with jlink ----------------------------
# jlink requires the output dir to NOT exist.
if (Test-Path $RuntimeDir) { Remove-Item -Recurse -Force $RuntimeDir }
$Modules = "java.base,java.desktop,java.logging,java.management,java.naming,java.net.http,java.security.jgss,java.instrument,jdk.unsupported"
Write-Host "`nBuilding custom runtime with jlink..." -ForegroundColor Cyan
& $JLink `
    --add-modules $Modules `
    --strip-debug `
    --no-header-files `
    --no-man-pages `
    --compress=2 `
    --output $RuntimeDir
if ($LASTEXITCODE -ne 0) {
    Write-Host "jlink failed." -ForegroundColor Red
    exit $LASTEXITCODE
}
if (-not (Test-Path (Join-Path $RuntimeDir "bin\java.exe"))) {
    Write-Host "jlink did not produce bin\java.exe." -ForegroundColor Red
    exit 1
}

# --- Clean previous output -------------------------------------------------
if (Test-Path $OutDir) { Remove-Item -Recurse -Force $OutDir }
New-Item -ItemType Directory -Force -Path $OutDir | Out-Null

# --- Run jpackage (exe installer, bundling our jlink runtime) --------------
# --runtime-image bundles our trimmed Java; --arguments are Spring Boot program
# args; --java-options are JVM options. -Djava.awt.headless=false pairs with
# app.setHeadless(false) in main() so the Swing desktop window can open. By
# default the installed app is a GUI app (no console); -DebugConsole adds one.
$jpArgs = @(
    "--type", "exe",
    "--name", "QuickBridge",
    "--vendor", "QuickBridge",
    "--app-version", $Version,
    "--input", $StageDir,
    "--main-jar", $Jar.Name,
    "--runtime-image", $RuntimeDir,
    "--dest", $OutDir,
    "--win-shortcut",
    "--win-menu",
    "--win-dir-chooser",
    "--java-options", "-Djava.awt.headless=false",
    "--java-options", "-Dfile.encoding=UTF-8",
    "--java-options", "-Dsun.stdout.encoding=UTF-8",
    "--java-options", "-Dsun.stderr.encoding=UTF-8",
    "--arguments", "--server.address=0.0.0.0",
    "--arguments", "--server.port=8080",
    "--arguments", "--quickbridge.desktop.enabled=true",
    "--arguments", "--quickbridge.browser.open-on-start=false"
)

if ($DebugConsole) {
    Write-Host "DebugConsole: console window + verbose logs enabled." -ForegroundColor Yellow
    $jpArgs += @("--win-console")
} else {
    $jpArgs += @(
        "--arguments", "--logging.level.root=WARN",
        "--arguments", "--logging.level.com.example.quickbridge=INFO"
    )
}

Write-Host "`nRunning jpackage..." -ForegroundColor Cyan
& $JPackage @jpArgs
if ($LASTEXITCODE -ne 0) {
    Write-Host "jpackage failed (WiX missing? see note above)." -ForegroundColor Red
    exit $LASTEXITCODE
}

$Installer = Get-ChildItem -Path $OutDir -Filter "QuickBridge*.exe" -ErrorAction SilentlyContinue |
    Select-Object -First 1
Write-Host "`nDone." -ForegroundColor Green
if ($Installer) {
    Write-Host "Installer: $($Installer.FullName)"
} else {
    Write-Host "Installer written under: $OutDir"
}
Write-Host "Note: Windows SmartScreen may warn for unsigned installers; code signing is" -ForegroundColor Yellow
Write-Host "      needed for production-quality distribution." -ForegroundColor Yellow
