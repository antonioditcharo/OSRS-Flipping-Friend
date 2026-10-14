# Fresh and returning-user setup share one backed-up paired installer.
param([switch] $SkipBuild, [string] $ShortcutDirectory)
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
Write-Host 'FLIPPING FRIEND - SETUP / RE-SETUP'
Write-Host 'Close RuneLite first. Existing account data and saved login are preserved.'
$credentials = Join-Path $env:USERPROFILE '.runelite\credentials.properties'
if (Test-Path -LiteralPath $credentials -PathType Leaf) {
    Write-Host 'Existing saved login found; reusing it. Login validity will be checked when you log in.'
} elseif ((Read-Host 'Do you use the Jagex Launcher? (y/n)') -match '^[Yy]') {
    Write-Host 'Open RuneLite configuration and set Client arguments to --insecure-write-credentials.'
    Write-Host 'Then launch RuneLite through Jagex, log in yourself, and close RuneLite.'
    $runelite = Join-Path $env:LOCALAPPDATA 'RuneLite\RuneLite.exe'
    if (Test-Path -LiteralPath $runelite -PathType Leaf) {
        Start-Process -FilePath $runelite -ArgumentList '--configure'
    } else { Write-Host 'Open RuneLite configuration yourself; its launcher was not found here.' }
    Read-Host 'Press Enter after logging in and closing RuneLite' | Out-Null
    if (-not (Test-Path -LiteralPath $credentials -PathType Leaf)) {
        throw 'Saved login is still absent. Finish the manual login setup before installing.'
    }
    Write-Host 'Clear --insecure-write-credentials from Client arguments after saving the login.'
    Read-Host 'Press Enter after clearing that setting and closing the configuration window' | Out-Null
}
& "$PSScriptRoot\apply-update.ps1" -ConfigureCompanionTask -SkipBuild:$SkipBuild

if (-not $ShortcutDirectory) { $ShortcutDirectory = [Environment]::GetFolderPath('Desktop') }
if (-not $ShortcutDirectory) { $ShortcutDirectory = Join-Path $env:USERPROFILE 'Desktop' }
New-Item -ItemType Directory -Force -Path $ShortcutDirectory | Out-Null
$shortcut = Join-Path $ShortcutDirectory 'Flipping Friend.lnk'
# Preserve a prior shortcut alongside the JAR/account backup before retargeting it.
$manifest = Get-Content -Raw -LiteralPath (Join-Path $env:USERPROFILE '.runelite\osrs-flipping-friend\runtime\installation.json') | ConvertFrom-Json
if (Test-Path -LiteralPath $shortcut -PathType Leaf) {
    Copy-Item -LiteralPath $shortcut -Destination (Join-Path $manifest.backupDirectory 'Flipping Friend.lnk') -Force
}
$shell = New-Object -ComObject WScript.Shell
$link = $shell.CreateShortcut($shortcut)
$link.TargetPath = Join-Path $root '2 - Start Flipping Friend.bat'
$link.WorkingDirectory = $root
$link.IconLocation = Join-Path $env:LOCALAPPDATA 'RuneLite\RuneLite.exe'
$link.Description = 'Start the verified Flipping Friend installation'
$link.Save()
Write-Host "SETUP COMPLETE. Desktop shortcut now uses: $root"
Write-Host 'Run 2 - Start Flipping Friend, log in yourself, then open the coin panel.'
Write-Host 'Run 3 - Status for a read-only health check. Account verification still requires login.'
