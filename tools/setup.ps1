# One-time setup, written to be run by double-clicking "1 - First Time Setup.bat".
#
# It does two things: makes the Jagex login usable outside the Jagex Launcher (only needed because
# RuneLite disables developer mode when its own launcher starts it), and builds and installs the
# plugin. It checks its own work at each step rather than assuming.

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
Set-Location $root

function Heading($text) {
    Write-Host ''
    Write-Host ('-' * 64) -ForegroundColor DarkGray
    Write-Host "  $text" -ForegroundColor Cyan
    Write-Host ('-' * 64) -ForegroundColor DarkGray
    Write-Host ''
}

Clear-Host
Write-Host ''
Write-Host '  FLIPPING FRIEND - FIRST TIME SETUP' -ForegroundColor Green
Write-Host ''
Write-Host '  This runs once. After it, starting the plugin is a single double-click.'
Write-Host ''

# ---------------------------------------------------------------- step 1: login

Heading 'STEP 1 of 3   How do you log in?'

Write-Host '  Most people these days use the Jagex Launcher - the app with the account'
Write-Host '  picker where you choose a character before playing.'
Write-Host ''
Write-Host '  Some older accounts still log in with just a username and password on the'
Write-Host '  RuneScape login screen.'
Write-Host ''
$jagex = Read-Host '  Do you use the Jagex Launcher? (y/n)'

$credentials = Join-Path $env:USERPROFILE '.runelite\credentials.properties'

if ($jagex -match '^[Yy]') {
    if (Test-Path $credentials) {
        Write-Host ''
        Write-Host '  Already done - your login is set up.' -ForegroundColor Green
    }
    else {
        Write-Host ''
        Write-Host '  RuneLite will not load custom plugins when the Jagex Launcher starts it.' -ForegroundColor Yellow
        Write-Host '  So we start it ourselves, and it needs your login saved once to do that.'
        Write-Host ''
        Write-Host '  A small RuneLite settings window is about to open.'
        Write-Host ''
        Write-Host '  In it, find the box labelled "Client arguments" and type exactly:'
        Write-Host ''
        Write-Host '      --insecure-write-credentials' -ForegroundColor White
        Write-Host ''
        Write-Host '  Then click Save.'
        Write-Host ''
        Read-Host '  Press Enter to open that window'

        $runelite = Join-Path $env:LOCALAPPDATA 'RuneLite\RuneLite.exe'
        if (Test-Path $runelite) {
            Start-Process -FilePath $runelite -ArgumentList '--configure' | Out-Null
        } else {
            Write-Host "  Could not find RuneLite.exe at $runelite" -ForegroundColor Red
            Write-Host '  Open it yourself and add the setting above.' -ForegroundColor Red
        }

        Write-Host ''
        Write-Host '  Now: open the Jagex Launcher, start RuneLite from it, and log in as normal.'
        Write-Host '  Once you are in the game you can close it again.'
        Write-Host ''
        Read-Host '  Press Enter once you have logged in and closed RuneLite'

        if (Test-Path $credentials) {
            Write-Host ''
            Write-Host '  Your login was saved.' -ForegroundColor Green
            Write-Host ''
            Write-Host '  Tidy-up: open the RuneLite settings window again and clear the' -ForegroundColor Yellow
            Write-Host '  "Client arguments" box, so it is not left switched on.' -ForegroundColor Yellow
        }
        else {
            Write-Host ''
            Write-Host '  Could not find the saved login yet.' -ForegroundColor Red
            Write-Host '  The plugin will still work, but you will only see the old login screen.'
            Write-Host '  Re-run this setup to try again.'
        }
    }
}
else {
    Write-Host ''
    Write-Host '  Nothing to do - your login already works this way.' -ForegroundColor Green
}

# --------------------------------------------------------------- step 2: build

Heading 'STEP 2 of 3   Building the plugin'

& "$PSScriptRoot\install.ps1"
if ($LASTEXITCODE -ne 0) {
    Write-Host ''
    Write-Host '  The build failed. Nothing was installed.' -ForegroundColor Red
    Read-Host '  Press Enter to close'
    exit 1
}

# ------------------------------------------------------------ step 3: shortcut

Heading 'STEP 3 of 3   Desktop shortcut'

$shortcut = Join-Path ([Environment]::GetFolderPath('Desktop')) 'Flipping Friend.lnk'
$launcher = Join-Path $root '2 - Start Flipping Friend.bat'

try {
    $shell = New-Object -ComObject WScript.Shell
    $link = $shell.CreateShortcut($shortcut)
    $link.TargetPath = $launcher
    $link.WorkingDirectory = $root
    $link.IconLocation = (Join-Path $env:LOCALAPPDATA 'RuneLite\RuneLite.exe')
    $link.Description = 'Start RuneLite with Flipping Friend'
    $link.Save()
    Write-Host '  Added "Flipping Friend" to your desktop.' -ForegroundColor Green
}
catch {
    Write-Host '  Could not create the shortcut, which is not a problem.' -ForegroundColor Yellow
    Write-Host "  Just use: $launcher"
}

Write-Host ''
Write-Host ('=' * 64) -ForegroundColor DarkGray
Write-Host ''
Write-Host '  SETUP COMPLETE' -ForegroundColor Green
Write-Host ''
Write-Host '  From now on, to play: double-click "Flipping Friend" on your desktop'
Write-Host '  (or "2 - Start Flipping Friend" in this folder).'
Write-Host ''
Write-Host '  When the game opens, log in, then click the gold coin icon on the'
Write-Host '  right-hand side bar.'
Write-Host ''
Read-Host '  Press Enter to close'
