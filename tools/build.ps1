# Builds the plugin jar.
#
# The jar contains only this plugin's classes: everything it depends on (RuneLite, OkHttp, Gson,
# Guice) is already inside the client and is declared compileOnly, which is what the sideloading
# class loader expects.

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
Set-Location $root

Write-Host 'Building Flipping Friend...' -ForegroundColor Cyan
# Windows PowerShell 5.1 turns redirected native stderr (including javac's ordinary
# deprecation notes) into error records. Let Gradle's exit code decide success while
# retaining every diagnostic; keep the caller's terminating-error policy elsewhere.
$nativeErrorPreference = $ErrorActionPreference
try {
    $ErrorActionPreference = 'Continue'
    $global:LASTEXITCODE = -1
    & "$root\gradlew.bat" jar :companion:shadowJar --console=plain 2>&1 | ForEach-Object { $_.ToString() }
    $buildExit = $LASTEXITCODE
} finally { $ErrorActionPreference = $nativeErrorPreference }
if ($buildExit -ne 0) {
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
