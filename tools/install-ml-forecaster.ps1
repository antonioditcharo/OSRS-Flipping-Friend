# Installs the background LSTM Forecaster so it starts with Windows.
#
# Remove it any time with tools\uninstall-ml-forecaster.ps1.

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
Set-Location $root

$taskName = 'FlippingFriendLstmForecaster'

Write-Host ''
Write-Host '  FLIPPING FRIEND - LSTM FORECASTER' -ForegroundColor Green
Write-Host ''
Write-Host '  This runs the Python ML API in the background.'
Write-Host ''

$mlDir = Join-Path $root 'ml-forecaster'
$pythonEnv = Join-Path $mlDir 'venv\Scripts\python.exe'

if (-not (Test-Path $pythonEnv)) {
    Write-Host "  Python environment not found at $pythonEnv" -ForegroundColor Red
    Write-Host "  Please set up the ml-forecaster first." -ForegroundColor Red
    Read-Host '  Press Enter to close'
    exit 1
}

$action = New-ScheduledTaskAction -Execute $pythonEnv `
    -Argument "-m uvicorn api:app --host 0.0.0.0 --port 8000" -WorkingDirectory $mlDir

$elevated = ([Security.Principal.WindowsPrincipal] `
    [Security.Principal.WindowsIdentity]::GetCurrent()).IsInRole(
        [Security.Principal.WindowsBuiltInRole]::Administrator)

$atLogon = New-ScheduledTaskTrigger -AtLogOn -User $env:USERNAME

if ($elevated) {
    $atStartup = New-ScheduledTaskTrigger -AtStartup
    $atStartup.Delay = 'PT2M'
    $trigger = @($atStartup, $atLogon)
    $principal = New-ScheduledTaskPrincipal -UserId $env:USERNAME -LogonType S4U -RunLevel Limited
} else {
    $trigger = @($atLogon)
    $principal = $null
}

$settings = New-ScheduledTaskSettingsSet -AllowStartIfOnBatteries -DontStopIfGoingOnBatteries `
    -StartWhenAvailable -RestartCount 99 -RestartInterval (New-TimeSpan -Minutes 5) `
    -ExecutionTimeLimit ([TimeSpan]::Zero) -MultipleInstances IgnoreNew -Hidden

try {
    Unregister-ScheduledTask -TaskName $taskName -Confirm:$false -ErrorAction SilentlyContinue
    if ($principal) {
        Register-ScheduledTask -TaskName $taskName -Action $action -Trigger $trigger `
            -Settings $settings -Principal $principal `
            -Description 'Flipping Friend LSTM Forecaster' | Out-Null
        Write-Host '  Installed. Runs at startup and keeps running whether or not you are logged in.' -ForegroundColor Green
    } else {
        Register-ScheduledTask -TaskName $taskName -Action $action -Trigger $trigger `
            -Settings $settings `
            -Description 'Flipping Friend LSTM Forecaster' | Out-Null
        Write-Host '  Installed. Runs from when you log in.' -ForegroundColor Green
    }
} catch {
    Write-Host "  Could not register the scheduled task: $($_.Exception.Message)" -ForegroundColor Red
    Read-Host '  Press Enter to close'
    exit 1
}

Write-Host ''
$now = Read-Host '  Start it now as well? (y/n)'
if ($now -match '^[Yy]') {
    Start-ScheduledTask -TaskName $taskName
    Write-Host '  Started.' -ForegroundColor Green
}

Write-Host ''
Read-Host '  Press Enter to close'
