# Removes the LSTM Forecaster background task.
$ErrorActionPreference = 'Stop'
$taskName = 'FlippingFriendLstmForecaster'

Write-Host ''
Write-Host '  FLIPPING FRIEND - REMOVE LSTM FORECASTER' -ForegroundColor Yellow
Write-Host ''

try {
    Unregister-ScheduledTask -TaskName $taskName -Confirm:$false -ErrorAction Stop
    Write-Host '  Removed.' -ForegroundColor Green
} catch {
    Write-Host "  Task '$taskName' was not found — nothing to remove." -ForegroundColor DarkGray
}

Write-Host ''
Read-Host '  Press Enter to close'
