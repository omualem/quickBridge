# QuickBridge runner for Windows PowerShell
#
# Builds the Spring Boot app (if needed) and runs it bound to 0.0.0.0 so other
# devices on the same Wi-Fi/LAN can reach this computer. Prints the local and LAN
# URLs, and offers the firewall command if the inbound rule is missing.
#
# This script does NOT require / request administrator rights. Adding the
# firewall rule (if needed) is a one-time command you run yourself in an elevated
# prompt.

$ErrorActionPreference = "Stop"

$ProjectDir = "C:\Projects\quickBridge"
$Port = 8080

Write-Host "== QuickBridge runner ==" -ForegroundColor Cyan

if (-not (Test-Path $ProjectDir)) {
    Write-Host "Project directory not found: $ProjectDir" -ForegroundColor Red
    exit 1
}
Set-Location $ProjectDir

Write-Host "`nChecking Java..." -ForegroundColor Cyan
java -version

if (-not (Test-Path ".\mvnw.cmd")) {
    Write-Host "`nMaven Wrapper not found: .\mvnw.cmd" -ForegroundColor Red
    exit 1
}

# --- Build only if we don't already have a runnable jar -------------------
$Jar = Get-ChildItem -Path ".\target" -Filter "quickbridge-*.jar" -ErrorAction SilentlyContinue |
    Where-Object { $_.Name -notlike "*.original" } |
    Sort-Object LastWriteTime -Descending |
    Select-Object -First 1

if (-not $Jar) {
    Write-Host "`nNo jar found - building project..." -ForegroundColor Cyan
    .\mvnw.cmd clean package -DskipTests
    if ($LASTEXITCODE -ne 0) {
        Write-Host "`nBuild failed." -ForegroundColor Red
        exit $LASTEXITCODE
    }
    $Jar = Get-ChildItem -Path ".\target" -Filter "quickbridge-*.jar" |
        Where-Object { $_.Name -notlike "*.original" } |
        Sort-Object LastWriteTime -Descending |
        Select-Object -First 1
}

if (-not $Jar) {
    Write-Host "`nNo jar file found in target/." -ForegroundColor Red
    exit 1
}

# --- Detect a LAN IPv4 address (skip loopback / virtual / VPN adapters) ----
$LanIp = $null
try {
    $LanIp = Get-NetIPAddress -AddressFamily IPv4 -ErrorAction Stop |
        Where-Object {
            $_.IPAddress -match '^(192\.168\.|10\.|172\.(1[6-9]|2[0-9]|3[01])\.)' -and
            $_.InterfaceAlias -notmatch 'Loopback|vEthernet|VMware|VirtualBox|VBox|Hyper-V|Docker|WSL|Tailscale|ZeroTier|VPN|Nord'
        } |
        Sort-Object InterfaceMetric |
        Select-Object -First 1 -ExpandProperty IPAddress
} catch {
    $LanIp = $null
}

# --- Check the Windows firewall inbound rule (informational only) ----------
$ruleExists = $false
try {
    $rule = Get-NetFirewallRule -DisplayName "QuickBridge $Port" -ErrorAction Stop
    if ($rule) { $ruleExists = $true }
} catch {
    $ruleExists = $false
}

if (-not $ruleExists) {
    Write-Host "`nWindows firewall rule 'QuickBridge $Port' was not found." -ForegroundColor Yellow
    Write-Host "If phones on your Wi-Fi can't connect, run this ONCE in an elevated (Admin) prompt:" -ForegroundColor Yellow
    Write-Host ('netsh advfirewall firewall add rule name="QuickBridge {0}" dir=in action=allow protocol=TCP localport={0}' -f $Port) -ForegroundColor White
}

# --- Run -------------------------------------------------------------------
Write-Host "`nStarting QuickBridge..." -ForegroundColor Green
Write-Host ("Local URL: http://localhost:{0}" -f $Port)
if ($LanIp) {
    Write-Host ("LAN URL:   http://{0}:{1}   <-- open this on your phone" -f $LanIp, $Port) -ForegroundColor Green
} else {
    Write-Host "LAN URL:   (could not auto-detect a LAN IP - check 'ipconfig')" -ForegroundColor Yellow
}
Write-Host "Keep this window open while using QuickBridge. Press Ctrl+C to stop.`n"

java -jar $Jar.FullName --server.address=0.0.0.0 --server.port=$Port
