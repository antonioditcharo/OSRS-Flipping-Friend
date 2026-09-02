# Opens the learning monitor window.
#
# Read-only by construction: it opens the companion's database in read-only mode and only ever GETs
# from the local API. Nothing it does can disturb the learner, the companion, or the game, so it is
# safe to leave open indefinitely.

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
Set-Location $root

$uiOut = Join-Path $root 'dashboard-ui\out'
$apiPublic = Join-Path $root 'dashboard-api\public'

if (-not (Test-Path $uiOut)) {
    Write-Host '  Building the dashboard UI (first run only)...' -ForegroundColor DarkGray
    Set-Location "$root\dashboard-ui"
    npm install
    npm run build
}

if (-not (Test-Path $apiPublic)) {
    Write-Host '  Copying static assets...' -ForegroundColor DarkGray
    Copy-Item -Path "$uiOut" -Destination "$apiPublic" -Recurse -Force
}

Write-Host '  Installing API dependencies...' -ForegroundColor DarkGray
Set-Location "$root\dashboard-api"
npm install --silent

Write-Host '  Installing Desktop Monitor dependencies...' -ForegroundColor DarkGray
Set-Location "$root\dashboard-desktop"
npm install --silent

Write-Host '  Launching Monitor...' -ForegroundColor DarkCyan
Start-Process npm.cmd -ArgumentList 'start' -WorkingDirectory "$root\dashboard-desktop"
