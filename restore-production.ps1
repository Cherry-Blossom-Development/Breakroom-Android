<#
.SYNOPSIS
    Restores the Android app to the production environment after testing.

.DESCRIPTION
    Switches the app back to the production API, rebuilds, and reinstalls it on any
    connected emulator or device.  Run this after finishing a test session.

.PARAMETER StopContainers
    Also stop the Breakroom test Docker containers (saves memory when not testing).

.EXAMPLE
    .\restore-production.ps1
    .\restore-production.ps1 -StopContainers
#>
param(
    [switch]$StopContainers
)

$ErrorActionPreference = 'Stop'

# ─── Paths ────────────────────────────────────────────────────────────────────
$AndroidDir   = Split-Path -Parent $MyInvocation.MyCommand.Path
$BreakroomDir = Resolve-Path "$AndroidDir\..\Breakroom"
$AndroidSdk   = if     ($env:ANDROID_HOME)      { $env:ANDROID_HOME }
                elseif ($env:ANDROID_SDK_ROOT)   { $env:ANDROID_SDK_ROOT }
                else                             { "$env:LOCALAPPDATA\Android\Sdk" }
$AdbExe       = "$AndroidSdk\platform-tools\adb.exe"
$ApkBuilt     = "$AndroidDir\app\build\outputs\apk\debug\app-debug.apk"

# ─── Output helpers ───────────────────────────────────────────────────────────
function Write-Step([string]$msg) {
    Write-Host ""
    Write-Host "─── $msg" -ForegroundColor Cyan
}
function Write-OK([string]$msg)   { Write-Host "  [ OK ]  $msg" -ForegroundColor Green  }
function Write-Info([string]$msg) { Write-Host "  [ .. ]  $msg" -ForegroundColor Yellow }
function Write-Fail([string]$msg) { Write-Host "  [FAIL]  $msg" -ForegroundColor Red    }

# ──────────────────────────────────────────────────────────────────────────────
Write-Host ""
Write-Host "══════════════════════════════════════════" -ForegroundColor Cyan
Write-Host "   Restore Production Environment         " -ForegroundColor Cyan
Write-Host "══════════════════════════════════════════" -ForegroundColor Cyan

# ─── 1. Switch to production ──────────────────────────────────────────────────
Write-Step "1/3  Environment"

$activeProps     = "$AndroidDir\environments\active.properties"
$productionProps = "$AndroidDir\environments\production.properties"

$activeUrl     = (Get-Content $activeProps     -ErrorAction SilentlyContinue |
                  Where-Object { $_ -match "^BASE_URL=" }) -replace "^BASE_URL=", ""
$productionUrl = (Get-Content $productionProps -ErrorAction SilentlyContinue |
                  Where-Object { $_ -match "^BASE_URL=" }) -replace "^BASE_URL=", ""

if ($activeUrl -eq $productionUrl) {
    Write-OK "Already pointing at production ($activeUrl)"
    Write-Info "Rebuilding anyway to ensure a clean production APK..."
} else {
    Write-Info "Switching: '$activeUrl' -> '$productionUrl'"
    & "$AndroidDir\switch-env.ps1" production
    Write-OK "Switched to production"
}

# ─── 2. Build and install ─────────────────────────────────────────────────────
Write-Step "2/3  Build"

Write-Info "Building production APK (approx 90s)..."
Push-Location $AndroidDir
& .\gradlew.bat assembleDebug --rerun-tasks
$buildExit = $LASTEXITCODE
Pop-Location

if ($buildExit -ne 0) {
    Write-Fail "Gradle build failed (exit $buildExit)"
    exit 1
}
Write-OK "Build succeeded"

if (Test-Path $AdbExe) {
    $adbOut = & $AdbExe devices 2>&1
    $connectedDevice = $adbOut | Where-Object { $_ -match "^emulator-\d+\s+device$" -or $_ -match "^\w+\s+device$" }
    if ($connectedDevice) {
        Write-Info "Installing on connected device..."
        & $AdbExe install -r $ApkBuilt
        if ($LASTEXITCODE -eq 0) {
            Write-OK "APK installed ($($connectedDevice.Trim()))"
        } else {
            Write-Info "Install returned non-zero — device may need a manual reinstall"
        }
    } else {
        Write-Info "No device connected — skipping install (APK is at $ApkBuilt)"
    }
} else {
    Write-Info "ADB not found at expected path — skipping device install"
}

# ─── 3. Docker containers (optional) ─────────────────────────────────────────
Write-Step "3/3  Docker test containers"

if ($StopContainers) {
    Write-Info "Stopping Breakroom test containers..."
    Push-Location $BreakroomDir
    docker compose -f docker-compose.test.yml down
    $dcExit = $LASTEXITCODE
    Pop-Location
    if ($dcExit -eq 0) {
        Write-OK "Test containers stopped"
    } else {
        Write-Info "docker compose down returned $dcExit — containers may already be stopped"
    }
} else {
    Write-Info "Leaving test containers running  (pass -StopContainers to shut them down)"
}

# ─── Done ─────────────────────────────────────────────────────────────────────
Write-Host ""
Write-Host "══════════════════════════════════════════" -ForegroundColor Cyan
Write-Host "  Restored to production" -ForegroundColor Green
Write-Host "  API: $productionUrl" -ForegroundColor Green
Write-Host "══════════════════════════════════════════" -ForegroundColor Cyan
Write-Host ""
