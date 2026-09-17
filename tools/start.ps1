# Everyday launcher, run by double-clicking "2 - Start Flipping Friend.bat".
#
# Rebuilds and reinstalls before launching, so editing the plugin never needs a separate step and
# there is no way to end up running a stale jar without realising.

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
Set-Location $root

Write-Host ''
Write-Host '  FLIPPING FRIEND' -ForegroundColor Green
Write-Host ''

$credentials = Join-Path $env:USERPROFILE '.runelite\credentials.properties'
$installed = Join-Path $env:USERPROFILE '.runelite\sideloaded-plugins\osrs-flipping-friend.jar'

if (-not (Test-Path $installed) -and -not (Test-Path $credentials)) {
    Write-Host '  It looks like you have not run setup yet.' -ForegroundColor Yellow
    Write-Host '  Close this and double-click "1 - First Time Setup" first.' -ForegroundColor Yellow
    Write-Host ''
    $go = Read-Host '  Or press Enter to carry on anyway (n to quit)'
    if ($go -match '^[Nn]') { exit 0 }
}

Write-Host '  Updating the plugin...' -ForegroundColor DarkGray
& "$PSScriptRoot\install.ps1" | Out-Null
if ($LASTEXITCODE -ne 0) {
    Write-Host ''
    Write-Host '  Build failed - see the errors above.' -ForegroundColor Red
    Read-Host '  Press Enter to close'
    exit 1
}
Write-Host '  Plugin up to date.' -ForegroundColor Green

# The companion owns new-buy portfolio planning. If the companion is temporarily unavailable, the
# plugin retains a limited built-in recovery path, but new-buy planning is less capable. Start the
# companion here so the supported planner is normally available before RuneLite opens.
# By the port, not the command line. The companion normally runs from its scheduled task under an
# S4U token in session 0, where an ordinary Win32_Process query gets a NULL CommandLine back -- so
# this scan reported "not running" about a companion that was serving perfectly well, and the branch
# below then started a second one to die on the port.
$listening = Get-NetTCPConnection -LocalPort 37777 -State Listen -ErrorAction SilentlyContinue |
    Select-Object -First 1
$running = if ($listening) { $listening.OwningProcess } else { $null }

if ($running) {
    Write-Host "  Market companion already running (pid $running)." -ForegroundColor Green
}
else {
    $companionJar = Join-Path $root 'companion\build\libs\flipping-friend-companion.jar'
    if (-not (Test-Path $companionJar)) {
        Write-Host '  Building the market companion (first run only)...' -ForegroundColor DarkGray
        & "$root\gradlew.bat" :companion:shadowJar --console=plain -q
    }

    if (Test-Path $companionJar) {
        $javaExe = Join-Path $env:JAVA_HOME 'bin\java.exe'
        if (-not (Test-Path $javaExe)) { $javaExe = (Get-Command java -ErrorAction SilentlyContinue).Source }
        if (-not $javaExe) { $javaExe = 'C:\Program Files\Java\jdk-23\bin\java.exe' }

        if (Test-Path $javaExe) {
            $log = Join-Path $env:USERPROFILE '.runelite\osrs-flipping-friend\companion\companion.log'
            New-Item -ItemType Directory -Force -Path (Split-Path $log) | Out-Null
            Start-Process -FilePath $javaExe -ArgumentList '-jar', "`"$companionJar`"" `
                -RedirectStandardOutput $log -RedirectStandardError "$log.err" -WindowStyle Hidden
            Write-Host '  Market companion started.' -ForegroundColor Green
        }
        else {
            Write-Host '  Could not find Java, so the companion did not start.' -ForegroundColor Yellow
            Write-Host '  The plugin will open in limited recovery mode without companion planning.' -ForegroundColor Yellow
        }
    }
    else {
        Write-Host '  Companion build not found; starting RuneLite in limited recovery mode.' -ForegroundColor Yellow
    }
}

Write-Host ''
Write-Host '  Starting the game. This window can be minimised but not closed.' -ForegroundColor DarkGray
Write-Host ''
Write-Host '  Once you are logged in, click the gold coin icon in the side bar.' -ForegroundColor Cyan
Write-Host ''

& "$PSScriptRoot\run-dev.ps1"
