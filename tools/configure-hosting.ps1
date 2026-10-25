# Stores the pilot credential with current-user Windows DPAPI; never prints it.
[CmdletBinding()]
param([switch] $Replace)
$ErrorActionPreference = 'Stop'
$pilotRoot = Join-Path (Split-Path -Parent $PSScriptRoot) 'local-data\hosted-pilot\private'
[IO.Directory]::CreateDirectory($pilotRoot) | Out-Null
$pilotSecret = Read-Host 'DigitalOcean API token (hidden; never paste it into chat)' -AsSecureString
if ($pilotSecret.Length -lt 20) { throw 'No usable API token was supplied.' }
$pilotEncrypted = ConvertFrom-SecureString -SecureString $pilotSecret
$pilotPath = Join-Path $pilotRoot 'digitalocean-token.dpapi'
if ((Test-Path -LiteralPath $pilotPath) -and -not $Replace) { throw 'A saved credential already exists; use -Replace only to replace its scopes.' }
$pilotTemporary = Join-Path $pilotRoot ([guid]::NewGuid().ToString('N') + '.tmp')
[IO.File]::WriteAllText($pilotTemporary, $pilotEncrypted)
if (Test-Path -LiteralPath $pilotPath) {
    [IO.File]::Replace($pilotTemporary, $pilotPath, ($pilotPath + '.' + (Get-Date -Format 'yyyyMMddHHmmss') + '.previous'))
} else { [IO.File]::Move($pilotTemporary, $pilotPath) }
Write-Output 'Saved with Windows current-user encryption. No server has been purchased or started.'
Write-Output 'Tell Codex that the hosting credential is ready. Do not share the token.'
