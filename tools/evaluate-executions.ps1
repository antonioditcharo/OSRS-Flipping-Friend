param(
    [ValidateSet('Freeze', 'Verify', 'Compare')][string]$Mode = 'Freeze',
    [string]$AccountId,
    [string]$Capture,
    [string]$Validation,
    [string]$Database
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path -Parent $PSScriptRoot
$companionJar = Join-Path $repoRoot 'companion\build\libs\flipping-friend-companion.jar'
if (-not (Test-Path -LiteralPath $companionJar -PathType Leaf)) {
    throw 'Build the tested companion JAR first. This command never installs or starts the service.'
}
$javaCommand = if ($env:JAVA_HOME -and (Test-Path -LiteralPath (Join-Path $env:JAVA_HOME 'bin\java.exe'))) {
    Join-Path $env:JAVA_HOME 'bin\java.exe'
} else { (Get-Command java -ErrorAction Stop).Source }
$mainClass = 'com.flippingfriend.companion.ExecutionEvaluationMain'

switch ($Mode) {
    'Freeze' {
        if ($AccountId -notmatch '^rs-[0-9a-f]{64}$') { throw 'Specify the exact scoped AccountId; ownership is never inferred.' }
        if (-not $Database) {
            $accountRoot = Join-Path $env:USERPROFILE '.runelite\osrs-flipping-friend\companion\accounts'
            $Database = Join-Path (Join-Path $accountRoot $AccountId) 'flipping-friend.db'
        }
        if (-not $Capture) {
            $captureName = 'execution-' + (Get-Date -Format 'yyyyMMdd-HHmmss') + '-' + [guid]::NewGuid().ToString('N').Substring(0, 8)
            $Capture = Join-Path (Join-Path $repoRoot 'local-data\execution-evaluation') $captureName
        }
        $revision = & git -C $repoRoot rev-parse HEAD
        if ($LASTEXITCODE -ne 0) { throw 'Cannot identify source revision.' }
        & $javaCommand -cp $companionJar $mainClass freeze $Database $AccountId $Capture $revision
        if ($LASTEXITCODE -ne 0) { throw "Read-only evaluation capture failed (exit $LASTEXITCODE)." }
        Write-Output "Private capture: $Capture"
    }
    'Verify' {
        if (-not $Capture) { throw 'Specify -Capture.' }
        & $javaCommand -cp $companionJar $mainClass verify $Capture
        if ($LASTEXITCODE -ne 0) { throw "Capture verification failed (exit $LASTEXITCODE)." }
    }
    'Compare' {
        if (-not $Capture -or -not $Validation) { throw 'Specify training -Capture and later -Validation.' }
        & $javaCommand -cp $companionJar $mainClass compare $Capture $Validation
        if ($LASTEXITCODE -ne 0) { throw "Chronological comparison failed (exit $LASTEXITCODE)." }
    }
}
