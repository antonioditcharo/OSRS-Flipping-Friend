# Builds the plugin and copies it into RuneLite's sideloaded-plugins folder.
#
# That folder is only read when the client is started in developer mode, which is what
# tools\run-dev.ps1 does. Dropping the jar into ~/.runelite/plugins instead would not work: that
# directory is the Plugin Hub's own cache and its contents are checked against a signed manifest.

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot

& "$PSScriptRoot\build.ps1"
if ($LASTEXITCODE -ne 0) { exit 1 }

$target = Join-Path $env:USERPROFILE '.runelite\sideloaded-plugins'
if (-not (Test-Path $target)) {
    New-Item -ItemType Directory -Force -Path $target | Out-Null
    Write-Host "Created $target"
}

$jar = Join-Path $root 'build\libs\osrs-flipping-friend.jar'
Copy-Item -Path $jar -Destination $target -Force

Write-Host "Installed to $target\osrs-flipping-friend.jar" -ForegroundColor Green
Write-Host 'Start the client with tools\run-dev.ps1 to load it.'
