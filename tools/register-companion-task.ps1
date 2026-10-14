# Internal configuration step. Called only after backup review by the paired installer.
param([Parameter(Mandatory=$true)][string]$Java,
      [Parameter(Mandatory=$true)][string]$Jar,
      [Parameter(Mandatory=$true)][string]$Checkout)
$ErrorActionPreference = 'Stop'
$taskName = 'FlippingFriendCompanion'
$guiJava = Join-Path (Split-Path -Parent $Java) 'javaw.exe'
if (Test-Path -LiteralPath $guiJava -PathType Leaf) { $Java = $guiJava }
$user = $env:USERNAME
if ($env:USERDOMAIN) { $user = $env:USERDOMAIN + '\' + $user }
$action = New-ScheduledTaskAction -Execute $Java -Argument "-Xmx768m -jar `"$Jar`"" -WorkingDirectory $Checkout
$logon = New-ScheduledTaskTrigger -AtLogOn -User $user
$watchdog = New-ScheduledTaskTrigger -Once -At (Get-Date).Date -RepetitionInterval (New-TimeSpan -Hours 1)
$principal = New-ScheduledTaskPrincipal -UserId $user -LogonType Interactive -RunLevel Limited
$settings = New-ScheduledTaskSettingsSet -Disable -AllowStartIfOnBatteries -DontStopIfGoingOnBatteries `
    -StartWhenAvailable -RestartCount 99 -RestartInterval (New-TimeSpan -Minutes 5) `
    -ExecutionTimeLimit ([TimeSpan]::Zero) -MultipleInstances IgnoreNew
Register-ScheduledTask -TaskName $taskName -TaskPath '\' -Action $action -Trigger @($logon,$watchdog) `
    -Principal $principal -Settings $settings -Force -Description 'Flipping Friend paired local companion' | Out-Null
Write-Host 'Companion task configured for this installation (starts after both JARs are installed).'
