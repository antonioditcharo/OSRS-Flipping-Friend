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

# The companion decides every buy. Without it the plugin falls back to the older built-in engine and
# quietly does worse, with nothing on screen to say why — so it is started here rather than being
# left as a separate thing to remember.
$running = Get-CimInstance Win32_Process -Filter "Name='java.exe'" -ErrorAction SilentlyContinue |
    Where-Object { $_.CommandLine -like '*flipping-friend-companion*' }

if ($running) {
    Write-Host '  Market companion already running.' -ForegroundColor Green
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
            # The same flags the scheduled task uses. This path inherited the JVM default, which is a
            # quarter of the machine's RAM and therefore large enough by accident -- which is why the
            # heap ceiling was only ever hit by the scheduled task, and never once in testing.
            Start-Process -FilePath $javaExe `
                -ArgumentList '-Xmx512m', '-XX:+ExitOnOutOfMemoryError', '-jar', "`"$companionJar`"" `
                -RedirectStandardOutput $log -RedirectStandardError "$log.err" -WindowStyle Hidden
            Write-Host '  Market companion started.' -ForegroundColor Green
        }
        else {
            Write-Host '  Could not find Java, so the companion did not start.' -ForegroundColor Yellow
            Write-Host '  The plugin will still work but will use its older, weaker engine.' -ForegroundColor Yellow
        }
    }
    else {
        Write-Host '  Companion build failed; the plugin will use its older engine.' -ForegroundColor Yellow
    }
}

Write-Host ''
Write-Host '  Starting the game. This window can be minimised but not closed.' -ForegroundColor DarkGray
Write-Host ''
Write-Host '  Once you are logged in, click the gold coin icon in the side bar.' -ForegroundColor Cyan
Write-Host ''

& "$PSScriptRoot\run-dev.ps1"
