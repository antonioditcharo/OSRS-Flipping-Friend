[CmdletBinding()]
param([ValidateSet('quote','provision','deploy','status','tunnel','backup','spaces')][string] $Mode='quote')
$ErrorActionPreference='Stop'
$pilotRepo=Split-Path -Parent $PSScriptRoot
$pilotPath=Join-Path $pilotRepo 'local-data\hosted-pilot\private\digitalocean-token.dpapi'
if (-not (Test-Path -LiteralPath $pilotPath)) { throw 'Run configure-hosting.ps1 first.' }
$pilotSecure=ConvertTo-SecureString ([IO.File]::ReadAllText($pilotPath))
try {
    $env:FF_DO_TOKEN=[Net.NetworkCredential]::new('',$pilotSecure).Password
    & python (Join-Path $PSScriptRoot 'hosting\pilot.py') $Mode
    if ($LASTEXITCODE -ne 0) { throw "Hosted pilot $Mode failed (exit $LASTEXITCODE); local guidance is unchanged." }
} finally { Remove-Item Env:\FF_DO_TOKEN -ErrorAction SilentlyContinue }
