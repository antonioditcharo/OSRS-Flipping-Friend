# Read-only installation/task/health check. It never changes files, tasks or processes.
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$dataRoot = Join-Path $env:USERPROFILE '.runelite\osrs-flipping-friend'
Write-Host 'FLIPPING FRIEND - STATUS'
foreach ($entry in @(
    @('Plugin', (Join-Path $env:USERPROFILE '.runelite\sideloaded-plugins\osrs-flipping-friend.jar')),
    @('Installed companion', (Join-Path $dataRoot 'runtime\flipping-friend-companion.jar')),
    @('Pair manifest', (Join-Path $dataRoot 'runtime\installation.json')),
    @('Companion credential', (Join-Path $dataRoot 'companion\companion.properties')),
    @('Saved RuneLite login', (Join-Path $env:USERPROFILE '.runelite\credentials.properties'))
)) {
    Write-Host "$($entry[0]): $(if (Test-Path -LiteralPath $entry[1] -PathType Leaf) { 'present' } else { 'absent' })"
}
try {
    $task = Get-ScheduledTask -TaskPath '\' -ErrorAction Stop | Where-Object { $_.TaskName -eq 'FlippingFriendCompanion' }
    if ($task) {
        Write-Host "Companion task: $($task.State)"
        Write-Host "Task checkout: $($task.Actions[0].WorkingDirectory)"
    } else { Write-Host 'Companion task: absent (direct launch is supported)' }
    & "$PSScriptRoot\apply-update.ps1" -CheckOnly
    Write-Host 'Overall status: PAIR VERIFIED / COMPANION RESPONDING'
    Write-Host 'The read-only pipeline check after login reports account coverage and market freshness.'
} catch {
    Write-Host "Overall status: NOT READY / SETUP INCOMPLETE. $($_.Exception.Message)" -ForegroundColor Yellow
    exit 1
}
