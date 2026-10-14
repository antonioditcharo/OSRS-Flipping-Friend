# Removes the installed Flipping Friend runtime components.
# Companion data and saved RuneLite login credentials are preserved.
param([string] $ShortcutDirectory)

$ErrorActionPreference = 'Stop'
$pluginJar = Join-Path $env:USERPROFILE '.runelite\sideloaded-plugins\osrs-flipping-friend.jar'
$desktop = $ShortcutDirectory
if (-not $desktop) { $desktop = [Environment]::GetFolderPath('Desktop') }
if (-not $desktop) { $desktop = Join-Path $env:USERPROFILE 'Desktop' }
$shortcut = Join-Path $desktop 'Flipping Friend.lnk'
$companionData = Join-Path $env:USERPROFILE '.runelite\osrs-flipping-friend\companion'
$credentials = Join-Path $env:USERPROFILE '.runelite\credentials.properties'
$runtime = Join-Path $env:USERPROFILE '.runelite\osrs-flipping-friend\runtime'

Write-Host ''
Write-Host '  FLIPPING FRIEND - UNINSTALL' -ForegroundColor Cyan
Write-Host ''

& "$PSScriptRoot\uninstall-companion.ps1"

foreach ($artifact in @('flipping-friend-companion.jar','installation.json')) {
    $path = Join-Path $runtime $artifact
    if (Test-Path -LiteralPath $path -PathType Leaf) { Remove-Item -LiteralPath $path -Force }
}

Write-Host ''
if (Test-Path $pluginJar) {
    Remove-Item -LiteralPath $pluginJar -Force
    Write-Host 'Removed the installed RuneLite plugin.' -ForegroundColor Green
} else {
    Write-Host 'The RuneLite plugin was not installed; nothing to remove.'
}

if (Test-Path $shortcut) {
    Remove-Item -LiteralPath $shortcut -Force
    Write-Host 'Removed the desktop shortcut.' -ForegroundColor Green
} else {
    Write-Host 'The desktop shortcut was not present; nothing to remove.'
}

Write-Host ''
Write-Host '  UNINSTALL COMPLETE' -ForegroundColor Green
Write-Host ''
Write-Host 'Preserved companion data:'
Write-Host "  $companionData"
Write-Host ''
Write-Host 'Preserved RuneLite login credentials:'
Write-Host "  $credentials"
Write-Host ''
Write-Host 'Run tools\cleanup.ps1 separately if you also want to remove the saved login.'
Write-Host 'The source checkout, build outputs, RuneLite installation, and companion data were not deleted.'
Write-Host ''
