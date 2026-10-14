# Stops the local portfolio companion and removes it from startup. Legacy JSON is retained as a
# recoverable backup; delete the companion directory separately only when it is no longer needed.

$ErrorActionPreference = 'Stop'
$taskName = 'FlippingFriendCompanion'

$task = Get-ScheduledTask -TaskPath '\' -ErrorAction Stop | Where-Object { $_.TaskName -eq $taskName }
if ($task) {
    Stop-ScheduledTask -TaskName $taskName -TaskPath '\' -ErrorAction Stop
    Unregister-ScheduledTask -TaskName $taskName -TaskPath '\' -Confirm:$false
    Write-Host 'Removed the local portfolio companion task.' -ForegroundColor Green
} else {
    Write-Host 'It was not installed; nothing to remove.'
}

# Catch a copy started by hand as well, so "uninstall" really means stopped.
Get-CimInstance Win32_Process -Filter "Name='javaw.exe' OR Name='java.exe'" -ErrorAction SilentlyContinue |
    Where-Object { $_.CommandLine -like '*flipping-friend-companion*' } |
    ForEach-Object {
        Stop-Process -Id $_.ProcessId -Force -ErrorAction Stop
        Write-Host "Stopped running companion (pid $($_.ProcessId))." -ForegroundColor Green
    }

if (Get-NetTCPConnection -LocalPort 37777 -State Listen -ErrorAction SilentlyContinue) {
    throw 'Port 37777 is still occupied. Installed JARs were not removed; identify the remaining process read-only.'
}

$companion = Join-Path $env:USERPROFILE '.runelite\osrs-flipping-friend\companion'
Write-Host ''
Write-Host 'Companion database and legacy backup are kept at:'
Write-Host "  $companion"
Write-Host 'Account and unassigned legacy databases are preserved; do not edit or remove them during re-setup.'
