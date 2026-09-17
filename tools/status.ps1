# Reports the installation and health of the supported Flipping Friend components.
# This script is read-only. It does not install, start, stop, update, or delete anything.

$ErrorActionPreference = 'Continue'

$pluginJar = Join-Path $env:USERPROFILE '.runelite\sideloaded-plugins\osrs-flipping-friend.jar'
$companionJar = Join-Path $PSScriptRoot '..\companion\build\libs\flipping-friend-companion.jar'
$properties = Join-Path $env:USERPROFILE '.runelite\osrs-flipping-friend\companion\companion.properties'
$taskName = 'FlippingFriendCompanion'

Write-Host ''
Write-Host '  FLIPPING FRIEND - STATUS' -ForegroundColor Cyan
Write-Host ''

$pluginInstalled = Test-Path $pluginJar
$companionBuilt = Test-Path $companionJar
$propertiesPresent = Test-Path $properties

if ($pluginInstalled) {
    Write-Host '  Plugin          installed' -ForegroundColor Green
} else {
    Write-Host '  Plugin          not installed' -ForegroundColor Yellow
}

if ($companionBuilt) {
    Write-Host '  Companion jar   present' -ForegroundColor Green
} else {
    Write-Host '  Companion jar   missing' -ForegroundColor Yellow
}

$task = Get-ScheduledTask -TaskName $taskName -ErrorAction SilentlyContinue
if ($task) {
    Write-Host "  Companion task  $($task.State)" -ForegroundColor Green
} else {
    Write-Host '  Companion task  not installed' -ForegroundColor Yellow
}

$connection = Get-NetTCPConnection -LocalPort 37777 -State Listen -ErrorAction SilentlyContinue |
    Select-Object -First 1

$pidNow = 0
if ($connection) {
    $pidNow = [int]$connection.OwningProcess
    Write-Host "  Port 37777      listening, pid $pidNow" -ForegroundColor Green
} else {
    Write-Host '  Port 37777      not listening' -ForegroundColor Yellow
}

if ($propertiesPresent) {
    Write-Host '  Credential      present' -ForegroundColor Green
} else {
    Write-Host '  Credential      missing' -ForegroundColor Yellow
}

$health = $null
if ($pidNow -ne 0 -and $propertiesPresent) {
    $match = Select-String -Path $properties -Pattern '^token=(.+)$' -ErrorAction SilentlyContinue
    if ($match) {
        $token = $match.Matches[0].Groups[1].Value.Trim()
        try {
            $headers = @{ 'X-Flipping-Friend-Token' = $token }
            $health = Invoke-RestMethod -Uri 'http://127.0.0.1:37777/v1/health' `
                -Headers $headers -TimeoutSec 8
        } catch {
            $health = $null
        }
    }
}

Write-Host ''
if ($health -and $health.status -eq 'READY') {
    Write-Host '  Overall status  READY' -ForegroundColor Green
    Write-Host "  Detail          $($health.reason)"
    Write-Host "  Model version   $($health.modelVersion)"
} elseif ($health) {
    Write-Host "  Overall status  WARMING OR DEGRADED ($($health.status))" -ForegroundColor Yellow
    Write-Host "  Detail          $($health.reason)"
    Write-Host "  Model version   $($health.modelVersion)"
} elseif ($pidNow -ne 0) {
    Write-Host '  Overall status  RUNNING BUT HEALTH UNAVAILABLE' -ForegroundColor Yellow
    Write-Host '  The process is listening, but its authenticated health check did not answer.'
} elseif ($pluginInstalled -or $companionBuilt -or $task) {
    Write-Host '  Overall status  INSTALLED BUT NOT RUNNING' -ForegroundColor Yellow
    Write-Host '  Run 2 - Start Flipping Friend to start the supported components.'
} else {
    Write-Host '  Overall status  SETUP INCOMPLETE' -ForegroundColor Red
    Write-Host '  Run 1 - First Time Setup before starting Flipping Friend.'
}

Write-Host ''
