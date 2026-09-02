# Runs the local portfolio companion in the foreground. It only reads public market data and
# accepts observations from the local RuneLite plugin; it never controls the game client.
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
Set-Location $root

& "$root\gradlew.bat" :companion:run --console=plain
