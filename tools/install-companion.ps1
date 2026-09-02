# Installs the local-only companion so it is already running before RuneLite starts.
#
# This matters more than it sounds. The plugin used to rebuild its whole view of the market from the
# internet on every launch — every price in the game, then history for sixty items one request at a
# time — and refused to advise until it had. The companion holds all of that already, refreshed every
# sixty seconds and persisted across its own restarts, and hands it over the loopback in
# milliseconds. A companion that is already up is the difference between advising in seconds and
# advising in minutes.
#
# So it gets the same treatment as the learning daemon: a logon trigger, an hourly watchdog, and a
# startup trigger when this is run with administrator rights. Restart-on-failure alone gives up once
# its attempts are spent, and a repeating trigger cannot give up — if the process has died the next
# fire restarts it, and if it is alive MultipleInstances=IgnoreNew discards the duplicate.
#
# It can be removed by deleting the scheduled task named FlippingFriendCompanion; no credentials or
# account data are passed to the task.
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
Set-Location $root
$taskName = 'FlippingFriendCompanion'

Write-Host 'Flipping Friend - installing the companion'
Write-Host ''

& "$root\gradlew.bat" :companion:shadowJar --console=plain
if ($LASTEXITCODE -ne 0) { throw 'Could not build the companion.' }

$jar = Join-Path $root 'companion\build\libs\flipping-friend-companion.jar'
if (-not (Test-Path $jar)) { throw "Companion jar was not created at $jar" }

$java = Join-Path $env:LOCALAPPDATA 'RuneLite\jre\bin\javaw.exe'
if (-not (Test-Path $java)) {
    $java = 'javaw'
    Write-Host '  Using javaw from PATH (RuneLite runtime not found).' -ForegroundColor Yellow
}

$action = New-ScheduledTaskAction -Execute $java -Argument "-Xmx192m -jar `"$jar`"" -WorkingDirectory $root

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
            -Description 'Flipping Friend local portfolio companion' | Out-Null
        Write-Host '  Installed. Runs at startup and keeps running whether or not you are' -ForegroundColor Green
        Write-Host '  logged in, checked hourly.' -ForegroundColor Green
    } else {
        Register-ScheduledTask -TaskName $taskName -Action $action -Trigger $trigger `
            -Settings $settings `
            -Description 'Flipping Friend local portfolio companion' | Out-Null
        Write-Host '  Installed. Runs from when you log in, checked hourly.' -ForegroundColor Green
        Write-Host ''
        Write-Host '  For it to run before you log in and after you log out, re-run this' -ForegroundColor Yellow
        Write-Host '  script as administrator. Everything else works either way.' -ForegroundColor Yellow
    }
} catch {
    Write-Host "  Could not register the scheduled task: $($_.Exception.Message)" -ForegroundColor Red
    Write-Host '  You can still run it by hand with: .\tools\run-companion.ps1' -ForegroundColor Yellow
    exit 1
}

# Only when nothing is already serving on the port. apply-update.ps1 starts its own copy, and a
# second one would lose the race for 37777 and die reporting a port clash that means nothing.
$running = Get-CimInstance Win32_Process -Filter "Name='java.exe' or Name='javaw.exe'" -ErrorAction SilentlyContinue |
    Where-Object { $_.CommandLine -like '*flipping-friend-companion*' }
if ($running) {
    Write-Host ''
    Write-Host "  Already running (pid $($running.ProcessId)); leaving it alone." -ForegroundColor Green
} else {
    Start-ScheduledTask -TaskName $taskName
    Write-Host ''
    Write-Host '  Started.' -ForegroundColor Green
}
