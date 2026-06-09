# Builds a portable Windows app-image for QuickBridge using jpackage.
#
# Output: dist\windows-app-image\QuickBridge\QuickBridge.exe
#
# The result is a PORTABLE FOLDER (not a single standalone EXE): QuickBridge.exe
# plus a bundled Java runtime (under runtime\) and supporting files. Keep the
# whole folder together and run QuickBridge.exe. The target machine does NOT need
# Java or Maven installed.
#
# A trimmed Java 21 runtime is built explicitly with jlink and handed to jpackage
# via --runtime-image, which reliably bundles runtime\bin\java.exe (the default
# jpackage runtime generation can fail/omit it on some JDK layouts).
#
# Build requirements: Windows + JDK 21 with jmods (jpackage + jlink ship with the
# JDK).
#
# By default this produces a GUI app with NO console window (a small Swing status
# window opens instead). Pass -DebugConsole to attach a console showing live
# server logs for troubleshooting.
#
#   ...\build-app-image.ps1                -> GUI app, no console (normal)
#   ...\build-app-image.ps1 -DebugConsole  -> console window with logs (debug)

param(
    [switch]$DebugConsole
)

$ErrorActionPreference = "Stop"

# --- Resolve repo root (this script lives in packaging\windows) ------------
$RepoRoot = (Get-Item $PSScriptRoot).Parent.Parent.FullName
Set-Location $RepoRoot
Write-Host "== QuickBridge: build Windows app-image ==" -ForegroundColor Cyan
Write-Host "Repo root: $RepoRoot"

$Version    = "0.0.1"
$OutDir     = Join-Path $RepoRoot "dist\windows-app-image"
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

# --- Build the Spring Boot jar ---------------------------------------------
Write-Host "`nBuilding jar (mvnw clean package)..." -ForegroundColor Cyan
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
# jpackage copies EVERYTHING from --input into the app, so we isolate the jar.
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

# --- Run jpackage (app-image, bundling our jlink runtime) ------------------
# --runtime-image guarantees the app-image ships runtime\bin\java.exe.
# --arguments are Spring Boot program args; --java-options are JVM options.
# -Djava.awt.headless=false is belt-and-suspenders with app.setHeadless(false)
# in main(), so the Swing desktop window can open.
# The jar's manifest Main-Class (Spring Boot JarLauncher) is used, so we don't
# hard-code a loader class that could change between Boot versions.
$jpArgs = @(
    "--type", "app-image",
    "--name", "QuickBridge",
    "--vendor", "QuickBridge",
    "--app-version", $Version,
    "--input", $StageDir,
    "--main-jar", $Jar.Name,
    "--runtime-image", $RuntimeDir,
    "--dest", $OutDir,
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
    # Attach a console window showing live server logs for troubleshooting.
    Write-Host "DebugConsole: console window + verbose logs enabled." -ForegroundColor Yellow
    $jpArgs += @("--win-console")
} else {
    # Normal GUI app: no console, and keep logs quiet (they have nowhere visible
    # to go anyway). WARN root still surfaces real problems; our own logs at INFO.
    $jpArgs += @(
        "--arguments", "--logging.level.root=WARN",
        "--arguments", "--logging.level.com.example.quickbridge=INFO"
    )
}

Write-Host "`nRunning jpackage..." -ForegroundColor Cyan
& $JPackage @jpArgs
if ($LASTEXITCODE -ne 0) {
    Write-Host "jpackage failed." -ForegroundColor Red
    exit $LASTEXITCODE
}

# --- Verify the bundled runtime made it into the app-image -----------------
$BundledJava = Join-Path $OutDir "QuickBridge\runtime\bin\java.exe"
if (-not (Test-Path $BundledJava)) {
    Write-Host "Bundled runtime missing: $BundledJava" -ForegroundColor Red
    exit 1
}
Write-Host "`nVerifying bundled runtime..." -ForegroundColor Cyan
& $BundledJava -version

$Exe = Join-Path $OutDir "QuickBridge\QuickBridge.exe"
$mode = if ($DebugConsole) { "DEBUG (console + logs)" } else { "GUI (no console)" }
Write-Host "`nDone." -ForegroundColor Green
Write-Host "Mode:                $mode"
Write-Host "Portable app folder: $(Join-Path $OutDir 'QuickBridge')"
Write-Host "Bundled runtime:     $BundledJava"
Write-Host "Run it with:         $Exe"
Write-Host "Keep the whole 'QuickBridge' folder together when copying to another PC." -ForegroundColor Yellow
