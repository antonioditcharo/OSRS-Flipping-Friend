# Compatibility entry point. Companion installation always updates both supported components.
param([switch] $SkipBuild)
$ErrorActionPreference = 'Stop'
& "$PSScriptRoot\apply-update.ps1" -ConfigureCompanionTask -SkipBuild:$SkipBuild
