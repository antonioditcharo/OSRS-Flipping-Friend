# Installs the background learning daemon so it starts with Windows.
#
# The daemon watches the Grand Exchange market and works out how trades would have turned out,
# including the ones the plugin decided against. It never touches the game, never reads your account,
# and never places anything — it is an HTTP client talking to the public price API and doing
# arithmetic. Everything it learns lands in a file the plugin reads.
#
# Remove it any time with tools\uninstall-daemon.ps1.

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
Set-Location $root

$taskName = 'FlippingFriendLearning'

Write-Host ''
Write-Host '  FLIPPING FRIEND - BACKGROUND LEARNING' -ForegroundColor Green
Write-Host ''
Write-Host '  This runs a small program in the background that studies the Grand Exchange'
Write-Host '  and learns which trades work. It does not play the game and it does not'
Write-Host '  touch your account.'
Write-Host ''

Write-Host '  Building...' -ForegroundColor DarkGray
& "$root\gradlew.bat" daemonJar --console=plain | Out-Null
if ($LASTEXITCODE -ne 0) {
    Write-Host '  Build failed.' -ForegroundColor Red
    Read-Host '  Press Enter to close'
    exit 1
}

$jar = Join-Path $root 'build\libs\flipping-friend-daemon.jar'
if (-not (Test-Path $jar)) {
    Write-Host "  Expected jar not found at $jar" -ForegroundColor Red
    exit 1
}

# Prefer RuneLite's bundled runtime so this does not depend on a system-wide Java install.
$java = Join-Path $env:LOCALAPPDATA 'RuneLite\jre\bin\javaw.exe'
if (-not (Test-Path $java)) {
    $java = 'javaw'
    Write-Host '  Using javaw from PATH (RuneLite runtime not found).' -ForegroundColor Yellow
}

# -Xmx96m keeps it genuinely small; it holds price history for a couple of dozen items at a time.
$action = New-ScheduledTaskAction -Execute $java `
    -Argument "-Xmx96m -jar `"$jar`"" -WorkingDirectory $root
# Two levels of "always on", because the stronger one needs administrator rights and the weaker one
# should still be offered rather than failing.
#
#   Elevated      - runs at startup, before anyone logs in, and keeps running after logout. This is
#                   what "always on regardless" actually requires: an interactive task simply waits
#                   at the login screen doing nothing.
#   Not elevated  - runs from logon onwards, which covers the machine being awake and in use.
#
# Both get an hourly watchdog. Restart-on-failure alone gives up after its attempts are spent, which
# over weeks means the learner eventually stops for good and nothing anywhere says so. A repeating
# trigger cannot give up: if the process has died the next fire restarts it, and if it is alive
# MultipleInstances=IgnoreNew discards the duplicate.
$elevated = ([Security.Principal.WindowsPrincipal] `
    [Security.Principal.WindowsIdentity]::GetCurrent()).IsInRole(
        [Security.Principal.WindowsBuiltInRole]::Administrator)

$atLogon = New-ScheduledTaskTrigger -AtLogOn -User $env:USERNAME
$watchdog = New-ScheduledTaskTrigger -Once -At (Get-Date).Date `
    -RepetitionInterval (New-TimeSpan -Hours 1)

if ($elevated) {
    $atStartup = New-ScheduledTaskTrigger -AtStartup
    $atStartup.Delay = 'PT2M'   # let the network come up first
    $trigger = @($atStartup, $atLogon, $watchdog)
    # S4U asks Windows for a token without storing a password, which is what lets the startup
    # trigger mean anything.
    $principal = New-ScheduledTaskPrincipal -UserId $env:USERNAME -LogonType S4U -RunLevel Limited
} else {
    $trigger = @($atLogon, $watchdog)
    $principal = $null
}

$settings = New-ScheduledTaskSettingsSet -AllowStartIfOnBatteries -DontStopIfGoingOnBatteries `
    -StartWhenAvailable -RestartCount 99 -RestartInterval (New-TimeSpan -Minutes 5) `
    -ExecutionTimeLimit ([TimeSpan]::Zero) -MultipleInstances IgnoreNew

try {
    Unregister-ScheduledTask -TaskName $taskName -Confirm:$false -ErrorAction SilentlyContinue
    if ($principal) {
        Register-ScheduledTask -TaskName $taskName -Action $action -Trigger $trigger `
            -Settings $settings -Principal $principal `
            -Description 'Flipping Friend background market learning' | Out-Null
        Write-Host '  Installed. Runs at startup and keeps running whether or not you are' -ForegroundColor Green
        Write-Host '  logged in, checked hourly.' -ForegroundColor Green
    } else {
        Register-ScheduledTask -TaskName $taskName -Action $action -Trigger $trigger `
            -Settings $settings `
            -Description 'Flipping Friend background market learning' | Out-Null
        Write-Host '  Installed. Runs from when you log in, checked hourly.' -ForegroundColor Green
        Write-Host ''
        Write-Host '  For it to run before you log in and after you log out, re-run this' -ForegroundColor Yellow
        Write-Host '  script as administrator. Everything else works either way.' -ForegroundColor Yellow
    }
} catch {
    Write-Host "  Could not register the scheduled task: $($_.Exception.Message)" -ForegroundColor Red
    Write-Host '  You can still run it by hand with: .\gradlew.bat daemon' -ForegroundColor Yellow
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
Write-Host '  What it uses:' -ForegroundColor Cyan
Write-Host '    Memory   under 100 MB'
Write-Host '    Network  a few requests a minute to the OSRS Wiki price API'
Write-Host '    Disk     a few MB in .runelite\osrs-flipping-friend\market-cache\learning'
Write-Host ''
Write-Host '  It runs whether or not the game is open, and whether or not you are logged in.'
Write-Host '  The Learning section of the plugin panel shows what it has worked out.'
Write-Host '  Remove it any time with tools\uninstall-daemon.ps1.'
Write-Host ''
Read-Host '  Press Enter to close'
