param(
    [ValidateSet('Audit', 'Verify')][string]$Mode = 'Audit',
    [Parameter(Mandatory = $true)][string]$Capture,
    [string]$Report
)
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path -Parent $PSScriptRoot
$companionJar = Join-Path $repoRoot 'companion\build\libs\flipping-friend-companion.jar'
if (-not (Test-Path -LiteralPath $companionJar -PathType Leaf)) { throw 'Build the tested companion JAR first.' }
if (-not (Test-Path -LiteralPath $Capture -PathType Container)) { throw 'Capture must be an existing verified frozen-capture directory.' }
if (-not $Report) {
    if ($Mode -eq 'Verify') { throw 'Verify requires the existing Report directory.' }
    $name = 'audit-' + (Get-Date -Format 'yyyyMMdd-HHmmss') + '-' + [guid]::NewGuid().ToString('N').Substring(0, 8)
    $Report = Join-Path (Join-Path $repoRoot 'local-data\trade-audits') $name
}
$javaCommand = if ($env:JAVA_HOME -and (Test-Path -LiteralPath (Join-Path $env:JAVA_HOME 'bin\java.exe'))) {
    Join-Path $env:JAVA_HOME 'bin\java.exe'
} else { (Get-Command java -ErrorAction Stop).Source }
$previousPreference = $ErrorActionPreference
try {
    # Native stderr warnings are retained; the process exit code determines success on PS 5.1.
    $ErrorActionPreference = 'Continue'
    $global:LASTEXITCODE = 1
    & $javaCommand -cp $companionJar com.flippingfriend.companion.TradeEvidenceAuditMain $Mode.ToLowerInvariant() $Capture $Report
    $auditExit = $LASTEXITCODE
} finally { $ErrorActionPreference = $previousPreference }
if ($auditExit -ne 0) { throw "Offline trade audit failed (exit $auditExit)." }
Write-Output "Private audit: $Report"
