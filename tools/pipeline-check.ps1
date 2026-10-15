# Read-only verification of the supported build and account-scoped companion.
param(
    [switch] $SkipBuild,
    [switch] $SkipWalkForward,
    [string] $AccountId
)
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$dataRoot = Join-Path $env:USERPROFILE '.runelite\osrs-flipping-friend'
$companionDir = Join-Path $dataRoot 'companion'
$accountRoot = Join-Path $companionDir 'accounts'
$failed = $false
function Report([string]$Stage, [string]$Verdict, [string]$Detail) {
    Write-Host "$Verdict [$Stage] $Detail"
    if ($Verdict -eq 'FAIL') { $script:failed = $true }
}
if ($AccountId -and $AccountId -notmatch '^rs-[0-9a-f]{64}$') {
    throw 'AccountId must be an opaque rs- identifier from an account directory.'
}
if (-not $SkipBuild) {
    Push-Location $root
    try {
        & .\gradlew.bat test jar :companion:shadowJar --console=plain
        if ($LASTEXITCODE -ne 0) { throw 'Supported build failed.' }
    } finally { Pop-Location }
}
foreach ($artifact in @('build\libs\osrs-flipping-friend.jar','companion\build\libs\flipping-friend-companion.jar')) {
    $artifactPath = Join-Path $root $artifact
    if (Test-Path -LiteralPath $artifactPath) { Report 'Artifact' 'PASS' $artifact }
    else { Report 'Artifact' 'FAIL' "Missing $artifact" }
}
$properties = Join-Path $companionDir 'companion.properties'
if (-not (Test-Path -LiteralPath $properties)) {
    Report 'Companion' 'FAIL' 'Local token configuration is missing.'
    exit 1
}
$tokenLine = Get-Content -LiteralPath $properties | Where-Object { $_ -match '^token=' } | Select-Object -First 1
if (-not $tokenLine) { throw 'Local token configuration is invalid.' }
$headers = @{'X-Flipping-Friend-Token' = $tokenLine.Substring(6)}
$base = 'http://127.0.0.1:37777/v1/'
try {
    $response = Invoke-WebRequest -UseBasicParsing -Uri ($base + 'health') -Headers $headers -TimeoutSec 10
    $health = $response.Content | ConvertFrom-Json
    if ($response.Headers['X-Flipping-Friend-Protocol'] -ne 'accounts-v1') {
        Report 'Protocol' 'FAIL' 'The running companion needs the account-scoped build.'
    } else { Report 'Protocol' 'PASS' 'accounts-v1' }
    Report 'Market' $(if ($health.status -eq 'READY') { 'PASS' } else { 'WARN' }) $health.reason
} catch { Report 'Companion' 'FAIL' $_.Exception.Message }
$directories = if (Test-Path -LiteralPath $accountRoot) {
    @(Get-ChildItem -LiteralPath $accountRoot -Directory | Where-Object {
        $_.Name -match '^rs-[0-9a-f]{64}$' -and (-not $AccountId -or $_.Name -eq $AccountId)
    })
} else { @() }
if ($AccountId -and $directories.Count -eq 0) { Report 'Account' 'FAIL' 'Requested account database is absent.' }
if ($directories.Count -eq 0 -and -not $AccountId) { Report 'Accounts' 'INFO' 'No scoped account has been observed yet.' }
foreach ($directory in $directories) {
    $id = $directory.Name
    $database = Join-Path $directory.FullName 'flipping-friend.db'
    if (-not (Test-Path -LiteralPath $database)) { Report $id 'WARN' 'Account database is absent.'; continue }
    $accountHeaders = @{} + $headers
    $accountHeaders['X-Flipping-Friend-Account'] = $id
    try {
        $endpoint = $base + 'accounts/' + $id + '/'
        $response = Invoke-WebRequest -UseBasicParsing -Uri ($endpoint + 'action') -Headers $accountHeaders -TimeoutSec 10
        if ($response.Headers['X-Flipping-Friend-Account'] -ne $id) { throw 'Response account identity mismatch.' }
        $action = $response.Content | ConvertFrom-Json
        $plan = Invoke-RestMethod -Uri ($endpoint + 'portfolio/current') -Headers $accountHeaders -TimeoutSec 10
        $ledgerResponse = Invoke-WebRequest -UseBasicParsing -Uri ($endpoint + 'ledger/current') -Headers $accountHeaders -TimeoutSec 10
        $ledger = $ledgerResponse.Content | ConvertFrom-Json
        if ($ledgerResponse.Headers['X-Flipping-Friend-Account'] -ne $id -or $ledger.accountId -ne $id -or @('1', '2') -notcontains $ledger.schemaVersion) {
            throw 'Ledger scope or schema mismatch; update both supported JARs.'
        }
        if ($ledger.schemaVersion -eq '2' -and
            ($ledger.PSObject.Properties.Name -notcontains 'tradingPositions' -or
             $ledger.PSObject.Properties.Name -notcontains 'missedFlips')) {
            throw 'Ledger trade evidence is missing; update both supported JARs.'
        }
        Report $id 'PASS' "Ledger: $($ledger.sales) sale updates; $($ledger.unresolvedSales) unresolved; $($ledger.uncertainSales) profit ranges."
        $detail = if ($null -eq $plan) { 'No current plan; a fresh plugin snapshot may be required.' }
            else { "Plan: $($plan.status). $($plan.reason)" }
        Report $id 'PASS' "Action: $($action.type). $detail"
    } catch { Report $id 'FAIL' $_.Exception.Message }
}
Report 'Accounting' 'INFO' 'No synthetic snapshots, offers, or trades were posted by this check.'
if ($failed) { exit 1 }
exit 0
