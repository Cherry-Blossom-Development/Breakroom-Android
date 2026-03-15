# switch-env.ps1
# Switches the Android debug build environment.
#
# Usage:
#   .\switch-env.ps1 local
#   .\switch-env.ps1 production
#
# After switching, rebuild the app:
#   .\gradlew.bat assembleDebug --rerun-tasks

param(
    [Parameter(Mandatory = $true, Position = 0)]
    [string]$Environment
)

$envDir    = Join-Path $PSScriptRoot "environments"
$source    = Join-Path $envDir "$Environment.properties"
$active    = Join-Path $envDir "active.properties"

if (-not (Test-Path $source)) {
    Write-Host "Error: No config found for environment '$Environment'." -ForegroundColor Red
    Write-Host ""
    Write-Host "Available environments:" -ForegroundColor Yellow
    Get-ChildItem -Path $envDir -Filter "*.properties" |
        Where-Object { $_.Name -ne "active.properties" } |
        ForEach-Object { Write-Host "  $($_.BaseName)" }
    exit 1
}

Copy-Item -Path $source -Destination $active -Force

# Extract and display the BASE_URL for confirmation
$url = (Get-Content $source | Where-Object { $_ -match "^BASE_URL=" }) -replace "^BASE_URL=", ""

Write-Host ""
Write-Host "Switched to: $Environment" -ForegroundColor Green
Write-Host "BASE_URL:    $url"         -ForegroundColor Cyan
Write-Host ""
Write-Host "Now rebuild to apply:" -ForegroundColor Yellow
Write-Host "  .\gradlew.bat assembleDebug --rerun-tasks"
Write-Host ""
