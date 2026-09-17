# Installs the local-only companion so it is already running before RuneLite starts.
#
# This matters more than it sounds. The plugin used to rebuild its whole view of the market from the
# internet on every launch — every price in the game, then history for sixty items one request at a
# time — and refused to advise until it had. The companion holds all of that already, refreshed every
# sixty seconds and persisted across its own restarts, and hands it over the loopback in
# milliseconds. A companion that is already up is the difference between advising in seconds and
# advising in minutes.
#
# It uses a logon trigger, an hourly watchdog, and a startup trigger when this is run with administrator
# rights. Restart-on-failure alone gives up once its attempts are spent, but a repeating trigger
# cannot give up. If the process has died, the next trigger restarts it; if it is alive,
# MultipleInstances=IgnoreNew discards the duplicate.
#
# It can be removed with tools\uninstall-companion.ps1; no credentials or
# account data are passed to the task.
param([switch] $SkipBuild)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
Set-Location $root
$taskName = 'FlippingFriendCompanion'

Write-Host 'Flipping Friend - installing the companion'
Write-Host ''

if (-not $SkipBuild) {
    & "$root\gradlew.bat" :companion:shadowJar --console=plain
    if ($LASTEXITCODE -ne 0) { throw 'Could not build the companion.' }
}

$jar = Join-Path $root 'companion\build\libs\flipping-friend-companion.jar'
if (-not (Test-Path $jar)) { throw "Companion jar was not created at $jar" }

$java = Join-Path $env:LOCALAPPDATA 'RuneLite\jre\bin\javaw.exe'
if (-not (Test-Path $java)) {
    $java = 'javaw'
    Write-Host '  Using javaw from PATH (RuneLite runtime not found).' -ForegroundColor Yellow
}

$action = New-ScheduledTaskAction -Execute $java -Argument "-Xmx768m -jar `"$jar`"" -WorkingDirectory $root

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

# Restart it, and find it by the port rather than by its command line.
#
# Two faults lived in the six lines this replaces. The scan was Win32_Process filtered on
# CommandLine, and the companion normally runs from its scheduled task under an S4U token in session
# 0, where an ordinary user query gets a NULL CommandLine back. So the check saw nothing, the script
# started a second companion, that one died on "Address already in use", and this script printed
# "Started." over the top of it -- with the previous build still serving every recommendation.
#
# The second fault is what it did when the check worked: it left the running companion alone. That is
# right for avoiding a port race and wrong for everything else, because this script has just rebuilt
# the jar. Leaving the old process up means the build you just made is not the build that is running,
# which is the whole reason someone runs this.
#
# A listening socket cannot hide, and restarting through the task keeps ownership where it belongs.
function Get-CompanionPid {
    $conn = Get-NetTCPConnection -LocalPort 37777 -State Listen -ErrorAction SilentlyContinue |
        Select-Object -First 1
    if ($conn) { return [int] $conn.OwningProcess }
    return 0
}

Write-Host ''
$existing = Get-CompanionPid
if ($existing -ne 0) {
    Write-Host "  Stopping the companion already running (pid $existing)..."
    Stop-ScheduledTask -TaskName $taskName -ErrorAction SilentlyContinue
    Stop-Process -Id $existing -Force -ErrorAction SilentlyContinue
    $deadline = (Get-Date).AddSeconds(20)
    while ((Get-Date) -lt $deadline -and (Get-CompanionPid) -ne 0) { Start-Sleep -Milliseconds 500 }
    if ((Get-CompanionPid) -ne 0) {
        Write-Host '  Something is still on port 37777; the new build cannot start.' -ForegroundColor Red
        Write-Host '  Close it and re-run, or use .	oolspply-update.ps1.' -ForegroundColor Yellow
        exit 1
    }
}

Start-ScheduledTask -TaskName $taskName

# Do not claim it started until something is actually listening. The old script said "Started."
# whatever happened, including when the process it started had already exited.
$deadline = (Get-Date).AddSeconds(45)
$pidNow = 0
while ((Get-Date) -lt $deadline -and $pidNow -eq 0) {
    Start-Sleep -Seconds 2
    $pidNow = Get-CompanionPid
}

Write-Host ''
if ($pidNow -ne 0) {
    Write-Host "  Started (pid $pidNow)." -ForegroundColor Green
} else {
    Write-Host '  It did not come up within 45 seconds. Check:' -ForegroundColor Red
    Write-Host "  $env:USERPROFILE\.runelite\osrs-flipping-friend\companion\companion.log.err"
    exit 1
}

Write-Host ''
Write-Host '  This installs the companion only. To update the RuneLite plugin as well, run' -ForegroundColor Yellow
Write-Host '  .	oolspply-update.ps1 -- it copies the plugin jar into sideloaded-plugins,' -ForegroundColor Yellow
Write-Host '  which this script has never done.' -ForegroundColor Yellow
