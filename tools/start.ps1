# Everyday launch: validate the installed pair and companion, then open RuneLite. No build/install.
$ErrorActionPreference = 'Stop'
Write-Host 'FLIPPING FRIEND - START'
& "$PSScriptRoot\apply-update.ps1" -StartOnly
Write-Host 'Starting RuneLite. Log in yourself, then open the Flipping Friend coin panel.'
& "$PSScriptRoot\run-dev.ps1"
