# Interactive first login only. Passwords go directly to SSH and are never saved or logged here.
[CmdletBinding()]
param([switch] $CheckOnly)
$ErrorActionPreference='Stop'
$pilotRoot=Join-Path (Split-Path -Parent $PSScriptRoot) 'local-data\hosted-pilot\private'
$pilotState=Get-Content -Raw (Join-Path $pilotRoot 'deployment.json') | ConvertFrom-Json
$pilotKnown=(Join-Path $pilotRoot 'known_hosts').Replace('\','/')
$pilotSshConfig=Join-Path $pilotRoot 'ssh-config'
$pilotIdentity=(Join-Path $pilotRoot 'pilot-client-ed25519').Replace('\','/')
$pilotSshSettings=@"
Host ff-private-pilot
    HostName $($pilotState.address)
    User root
    IdentityFile "$pilotIdentity"
    UserKnownHostsFile "$pilotKnown"
    StrictHostKeyChecking yes
    BatchMode yes
    IdentitiesOnly yes
"@
[IO.File]::WriteAllText($pilotSshConfig,$pilotSshSettings,(New-Object Text.UTF8Encoding($false)))
if ($CheckOnly) {
    $pilotCheck=& "$env:WINDIR\System32\OpenSSH\ssh.exe" -F $pilotSshConfig -G ff-private-pilot
    if ($LASTEXITCODE -ne 0 -or -not ($pilotCheck -match 'stricthostkeychecking true')) { throw 'SSH configuration verification failed.' }
    Write-Output 'Windows PowerShell SSH configuration verified; host checking remains strict.'
    return
}
Write-Output 'DigitalOcean requires the initial root password from its server-creation email.'
Write-Output 'Enter it at Current password, choose a new password, then type exit after login completes.'
Write-Output 'Do not paste any password into chat.'
& "$env:WINDIR\System32\OpenSSH\ssh.exe" -F $pilotSshConfig -tt ff-private-pilot
$pilotVerify=& "$env:WINDIR\System32\OpenSSH\ssh.exe" -F $pilotSshConfig ff-private-pilot 'printf BOOTSTRAP_VERIFIED' 2>&1
if ($LASTEXITCODE -ne 0 -or -not ($pilotVerify -match 'BOOTSTRAP_VERIFIED')) {
    Write-Output 'First-login verification did not pass. The server still requires its password change.'
    Write-Output 'The interactive change must report password updated successfully before you exit.'
    throw 'Hosting bootstrap is not yet complete; no password was saved by this script.'
}
Write-Output 'BOOTSTRAP VERIFIED. Codex can now finish the private deployment.'
