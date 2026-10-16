# One-time, app-scoped task repair. Run elevated only if this existing task requires it.
[CmdletBinding()]
param([switch] $CheckOnly, [switch] $RequireControl)
$ErrorActionPreference = 'Stop'
. "$PSScriptRoot\task-user-access.ps1"
$root = Split-Path -Parent $PSScriptRoot
$data = Join-Path $env:USERPROFILE '.runelite\osrs-flipping-friend'
$jar = Join-Path $data 'runtime\flipping-friend-companion.jar'
$service = New-Object -ComObject 'Schedule.Service'
$service.Connect()
$task = $service.GetFolder('\').GetTask('FlippingFriendCompanion')
$caller = Get-CompanionTaskCaller
if ($CheckOnly) {
    $access = Get-CompanionTaskAccessSnapshot $task $root $jar $caller
    if ($RequireControl -and -not $access.Ready -and -not $caller.IsAdministrator) {
        throw 'Run the one-time task permission repair: tools\enable-user-updates.ps1 as administrator. Routine updates then run normally.'
    }
    Write-Host "Task runs as this user without elevation. Normal-user maintenance ready: $($access.Ready)."
    return
}
Set-CompanionTaskUserAccess $task $root $jar (Join-Path $data 'backups') $caller
