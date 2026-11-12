# Read-only installation/task/health check. It never changes files, tasks or processes.
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$dataRoot = Join-Path $env:USERPROFILE '.runelite\osrs-flipping-friend'
Write-Host 'FLIPPING FRIEND - STATUS'
Write-Host "Managed console logs: $dataRoot\diagnostics\runtime-logs (8 MiB x 5 files per stream; three streams, 120 MiB total)."
$storageStatus = Join-Path $root 'local-data\.storage\status.json'
if (Test-Path -LiteralPath $storageStatus -PathType Leaf) {
    try {
        $storage = Get-Content -LiteralPath $storageStatus -Raw | ConvertFrom-Json
        $ageHours = ([DateTime]::UtcNow - [DateTimeOffset]::FromUnixTimeSeconds([long]$storage.checkedAt).UtcDateTime).TotalHours
        if ($storage.error -or $ageHours -gt 2 -or $ageHours -lt 0) { Write-Host 'Workspace storage: maintenance needs attention (error or stale report).' }
        else {
            Write-Host "Workspace storage: checked $([Math]::Round($ageHours, 1))h ago; stored $([Math]::Round($storage.report.storedBytes / 1GB, 2)) GiB; scratch $([Math]::Round($storage.report.scratchBytes / 1GB, 2)) GiB."
            foreach ($warning in @($storage.report.warnings) + @($storage.refusals | ForEach-Object { $_.reason })) {
                if ($warning) { Write-Host "Storage attention: $warning" }
            }
        }
    } catch { Write-Host 'Workspace storage: report unreadable; retained files were not changed by this status check.' }
} else { Write-Host 'Workspace storage: no maintenance report yet; normal Start enables the hourly worker when Python is available.' }
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
