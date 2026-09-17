# Removes the installed Flipping Friend runtime components.
# Companion data and saved RuneLite login credentials are preserved.

$ErrorActionPreference = 'Stop'
$pluginJar = Join-Path $env:USERPROFILE '.runelite\sideloaded-plugins\osrs-flipping-friend.jar'
$shortcut = Join-Path ([Environment]::GetFolderPath('Desktop')) 'Flipping Friend.lnk'
$companionData = Join-Path $env:USERPROFILE '.runelite\osrs-flipping-friend\companion'
$credentials = Join-Path $env:USERPROFILE '.runelite\credentials.properties'

Write-Host ''
Write-Host '  FLIPPING FRIEND - UNINSTALL' -ForegroundColor Cyan
Write-Host ''

& "$PSScriptRoot\uninstall-companion.ps1"

Write-Host ''
if (Test-Path $pluginJar) {
    Remove-Item $pluginJar -Force
    Write-Host 'Removed the installed RuneLite plugin.' -ForegroundColor Green
} else {
    Write-Host 'The RuneLite plugin was not installed; nothing to remove.'
}

if (Test-Path $shortcut) {
    Remove-Item $shortcut -Force
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
