# Everyday launch: validate the installed pair and companion, then open RuneLite. No build/install.
$ErrorActionPreference = 'Stop'
Write-Host 'FLIPPING FRIEND - START'
& "$PSScriptRoot\apply-update.ps1" -StartOnly
# Optional pilot transport has its own lease/reconnect loop; failure leaves local guidance ready.
$pilotControl=Join-Path (Split-Path -Parent $PSScriptRoot) 'local-data\hosted-pilot\private\tunnel.enabled'
if (Test-Path -LiteralPath $pilotControl) {
    try {
        $pilotPython=(Get-Command python -ErrorAction Stop).Source
        $pilotScript=Join-Path $PSScriptRoot 'hosting\pilot-tunnel.py'
        Start-Process -FilePath $pilotPython -ArgumentList ('"{0}"' -f $pilotScript) -WindowStyle Hidden
    } catch { Write-Host 'Private service offline; local guidance remains available.' }
}
Write-Host 'Starting RuneLite. Log in yourself, then open the Flipping Friend coin panel.'
& "$PSScriptRoot\run-dev.ps1"
