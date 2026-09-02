# Builds the plugin jar.
#
# The jar contains only this plugin's classes: everything it depends on (RuneLite, OkHttp, Gson,
# Guice) is already inside the client and is declared compileOnly, which is what the sideloading
# class loader expects.

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
Set-Location $root

Write-Host 'Building Flipping Friend...' -ForegroundColor Cyan
& "$root\gradlew.bat" jar --console=plain
if ($LASTEXITCODE -ne 0) {
    Write-Host 'Build failed.' -ForegroundColor Red
    exit 1
}

$jar = Join-Path $root 'build\libs\osrs-flipping-friend.jar'
if (-not (Test-Path $jar)) {
    Write-Host "Expected jar not found at $jar" -ForegroundColor Red
    exit 1
}

$size = [math]::Round((Get-Item $jar).Length / 1KB, 1)
Write-Host "Built $jar ($size KB)" -ForegroundColor Green
