# Compatibility launcher for the verified installed pair. Developer source runs use Gradle explicitly.
$ErrorActionPreference = 'Stop'
& "$PSScriptRoot\apply-update.ps1" -StartOnly
