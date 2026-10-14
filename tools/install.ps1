# Compatibility entry point; never install the plugin without its matching companion.
$ErrorActionPreference = 'Stop'
& "$PSScriptRoot\apply-update.ps1" -ConfigureCompanionTask
