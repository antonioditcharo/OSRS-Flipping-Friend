[CmdletBinding()]
param([switch]$SkipBuild)
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
Set-Location $root
$stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$logDir = Join-Path $root 'build\verification'
New-Item -ItemType Directory -Force -Path $logDir | Out-Null
$log = Join-Path $logDir "supported-build-$stamp.log"
function Write-Log([string]$Message) { $Message | Tee-Object -FilePath $log -Append }
try {
    Write-Log "Supported build verification started: $(Get-Date -Format o)"
    foreach ($tool in @('git', 'java')) {
        if (-not (Get-Command $tool -ErrorAction SilentlyContinue)) { throw "Required tool not found: $tool" }
    }
    if (-not (Test-Path '.\gradlew.bat')) { throw 'Run from the repository root: gradlew.bat is missing.' }
    $expected = '460c7240afc4c10f747d37f46f789b3433d331cd'
    $head = (& git rev-parse HEAD).Trim()
    Write-Log "HEAD=$head"
    $status = @(& git status --short)
    if ($status) { Write-Log 'Working tree changes detected (allowed for package verification):'; $status | ForEach-Object { Write-Log $_ } }
    $missingClasses = @('LearningDaemon.java','ReplayMain.java','ModelTrainer.java','LearningMonitor.java')
    foreach ($name in $missingClasses) {
        if (Get-ChildItem -Recurse -File -Filter $name | Where-Object { $_.FullName -notmatch '[\\/]build[\\/]' }) { throw "Unexpected ghost entry-point source exists: $name" }
    }
    $forbiddenTaskRefs = 'daemonJar|replayJar|trainerJar|monitorJar|com\.flippingfriend\.daemon\.LearningDaemon|com\.flippingfriend\.companion\.(ReplayMain|ModelTrainer|LearningMonitor)'
    $buildRefs = Select-String -Path '.\build.gradle','.\companion\build.gradle' -Pattern $forbiddenTaskRefs
    if ($buildRefs) { throw "Ghost Gradle task or entry-point reference remains: $($buildRefs -join '; ')" }
    $scriptRefs = Select-String -Path '.\tools\apply-update.ps1','.\tools\pipeline-check.ps1' -Pattern 'daemonJar|replayJar|trainerJar|monitorJar'
    if ($scriptRefs) { throw "Supported pipeline still invokes a ghost task: $($scriptRefs -join '; ')" }
    if (-not $SkipBuild) {
        Write-Log 'Running Gradle task inventory.'
        & .\gradlew.bat tasks --all --console=plain 2>&1 | Tee-Object -FilePath $log -Append
        if ($LASTEXITCODE -ne 0) { throw "Gradle task inventory failed with exit code $LASTEXITCODE" }
        Write-Log 'Running Java tests and building supported artifacts.'
        & .\gradlew.bat test jar :companion:shadowJar --console=plain 2>&1 | Tee-Object -FilePath $log -Append
        if ($LASTEXITCODE -ne 0) { throw "Gradle verification failed with exit code $LASTEXITCODE" }
        foreach ($artifact in @('.\build\libs\osrs-flipping-friend.jar','.\companion\build\libs\flipping-friend-companion.jar')) {
            if (-not (Test-Path $artifact)) { throw "Expected artifact missing: $artifact" }
            Write-Log "Artifact present: $artifact"
        }
    } else { Write-Log 'Build execution skipped by explicit -SkipBuild switch; structural checks only.' }
    Write-Log 'PASS: supported build inventory verification completed.'
    Write-Host "PASS. Log: $log" -ForegroundColor Green
    exit 0
} catch {
    Write-Log "FAIL: $($_.Exception.Message)"
    Write-Host "FAIL. Log: $log" -ForegroundColor Red
    Write-Error $_
    exit 1
}
