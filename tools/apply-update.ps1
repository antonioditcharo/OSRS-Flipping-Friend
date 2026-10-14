# One paired lifecycle for setup, re-setup, update and start. Never operates game input.
param([switch] $SkipBuild, [switch] $ConfigureCompanionTask, [switch] $StartOnly, [switch] $CheckOnly)
$ErrorActionPreference = 'Stop'
if ($StartOnly -and $ConfigureCompanionTask) { throw 'Start cannot configure an installation.' }
if ($CheckOnly -and ($StartOnly -or $ConfigureCompanionTask)) { throw 'Check-only cannot start or configure an installation.' }
$root = Split-Path -Parent $PSScriptRoot
$taskName = 'FlippingFriendCompanion'
$dataRoot = Join-Path $env:USERPROFILE '.runelite\osrs-flipping-friend'
$runtime = Join-Path $dataRoot 'runtime'
$logDir = Join-Path $dataRoot 'companion'
$built = Join-Path $root 'build\libs\osrs-flipping-friend.jar'
$companionBuilt = Join-Path $root 'companion\build\libs\flipping-friend-companion.jar'
$installed = Join-Path $env:USERPROFILE '.runelite\sideloaded-plugins\osrs-flipping-friend.jar'
$companionInstalled = Join-Path $runtime 'flipping-friend-companion.jar'
$manifestPath = Join-Path $runtime 'installation.json'

function Normalize-AbsolutePath([string] $path) {
    if (-not $path -or -not [IO.Path]::IsPathRooted($path)) { throw 'Task checkout and JAR paths must be absolute.' }
    return [IO.Path]::GetFullPath($path).TrimEnd([char[]]@('\', '/'))
}
function Find-Java {
    $candidates = @()
    if ($env:JAVA_HOME) { $candidates += Join-Path $env:JAVA_HOME 'bin\java.exe' }
    $candidates += @((Get-Command java -ErrorAction SilentlyContinue).Source,
        'C:\Program Files\Java\jdk-23\bin\java.exe')
    foreach ($candidate in $candidates) {
        if ($candidate -and (Test-Path -LiteralPath $candidate -PathType Leaf)) { return $candidate }
    }
    throw 'No Java runtime found. Set JAVA_HOME or put java on PATH.'
}
function Get-CompanionTask {
    $tasks = @(Get-ScheduledTask -TaskPath '\' -ErrorAction Stop | Where-Object { $_.TaskName -eq $taskName })
    if ($tasks.Count -gt 1) { throw 'Multiple root companion tasks found.' }
    if ($tasks.Count -eq 1) { return $tasks[0] }
    return $null
}
function Get-TaskJar($candidate) {
    $actions = @($candidate.Actions)
    if ($actions.Count -ne 1) { throw 'Companion task must have exactly one Java action.' }
    $action = $actions[0]
    $executable = Normalize-AbsolutePath $action.Execute
    if ([IO.Path]::GetFileName($executable) -notmatch '^javaw?\.exe$' -or
        -not (Test-Path -LiteralPath $executable -PathType Leaf)) { throw 'Companion task must use an existing Java executable.' }
    if ($action.Arguments -notmatch '^\s*(?:-Xmx[1-9][0-9]*[kKmMgG]\s+)?-jar\s+(?:"(?<quoted>[^"]+)"|(?<bare>\S+))\s*$') {
        throw 'Companion task has unsupported Java/JAR arguments. Review its data location before re-setup.'
    }
    $jar = if ($Matches['quoted']) { $Matches['quoted'] } else { $Matches['bare'] }
    $jar = Normalize-AbsolutePath $jar
    if ([IO.Path]::GetExtension($jar) -ne '.jar') { throw 'Companion task does not reference a JAR file.' }
    return $jar
}
function Assert-Task($candidate, [switch] $AllowDisabled) {
    if (-not $candidate) { return }
    if (-not $AllowDisabled -and ($candidate.State -eq 'Disabled' -or $candidate.Settings.Enabled -eq $false)) {
        throw 'Companion task is disabled. Run 1b - Re-setup Existing Installation.'
    }
    if ((Normalize-AbsolutePath $candidate.Actions[0].WorkingDirectory) -ne (Normalize-AbsolutePath $root)) {
        throw 'Companion task points to a different checkout. Run 1b - Re-setup Existing Installation.'
    }
    if ((Get-TaskJar $candidate) -ne (Normalize-AbsolutePath $companionInstalled)) {
        throw 'Companion task points to a different JAR. Run 1b - Re-setup Existing Installation.'
    }
}
function Get-ArtifactSha256([string] $path) {
    # Only installed JARs are hashed; account/database files are never opened here.
    $stream = [IO.File]::OpenRead($path)
    $hasher = [Security.Cryptography.SHA256]::Create()
    try { return [BitConverter]::ToString($hasher.ComputeHash($stream)).Replace('-', '') }
    finally { $hasher.Dispose(); $stream.Dispose() }
}
function Assert-InstalledPair {
    if (-not (Test-Path -LiteralPath $manifestPath -PathType Leaf)) { throw 'Installation requires setup or re-setup. Run 1 or 1b in this folder.' }
    $manifest = Get-Content -Raw -LiteralPath $manifestPath | ConvertFrom-Json
    if ($manifest.schemaVersion -ne 1 -or
        (Normalize-AbsolutePath $manifest.checkout) -ne (Normalize-AbsolutePath $root)) { throw 'Installation belongs to another checkout. Run 1b - Re-setup Existing Installation.' }
    foreach ($entry in @(@($installed, $manifest.pluginSha256), @($companionInstalled, $manifest.companionSha256))) {
        if (-not (Test-Path -LiteralPath $entry[0] -PathType Leaf) -or
            (Get-ArtifactSha256 $entry[0]) -ne $entry[1]) {
            throw 'Installed JAR pair is missing or changed. Run 4 - Update (or 1b for an older installation).'
        }
    }
}
function Get-CompanionPid {
    $connection = Get-NetTCPConnection -LocalPort 37777 -State Listen -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($connection) { return [int]$connection.OwningProcess }
    return 0
}
function Wait-PortFree {
    $deadline = (Get-Date).AddSeconds(20)
    while ((Get-Date) -lt $deadline) {
        if ((Get-CompanionPid) -eq 0) { return }
        Start-Sleep -Milliseconds 500
    }
    throw 'Port 37777 is still occupied; no JARs were replaced.'
}
function Test-Health {
    $properties = Join-Path $logDir 'companion.properties'
    if (-not (Test-Path -LiteralPath $properties -PathType Leaf)) { return $false }
    $line = Get-Content -LiteralPath $properties | Where-Object { $_ -match '^token=' } | Select-Object -First 1
    if (-not $line) { return $false }
    try {
        $reply = Invoke-WebRequest -UseBasicParsing -Uri 'http://127.0.0.1:37777/v1/health' `
            -Headers @{ 'X-Flipping-Friend-Token' = $line.Substring(6) } -TimeoutSec 8
        $health = $reply.Content | ConvertFrom-Json
        return ($reply.Headers['X-Flipping-Friend-Protocol'] -eq 'accounts-v1' -and
            $health.status -in @('READY', 'DEGRADED'))
    } catch { return $false }
}
function Start-Companion($candidate, [string] $java) {
    Assert-InstalledPair
    Assert-Task $candidate
    if ((Get-CompanionPid) -ne 0) {
        if (Test-Health) { Write-Host 'Companion already ready.'; return }
        throw 'The existing listener did not pass the scoped health check. Run 3 - Status; do not start a second copy.'
    }
    New-Item -ItemType Directory -Force -Path $logDir | Out-Null
    if ($candidate) { Start-ScheduledTask -TaskName $taskName -TaskPath '\' }
    else {
        Start-Process -FilePath $java -ArgumentList '-jar', "`"$companionInstalled`"" `
            -WorkingDirectory $root -WindowStyle Hidden `
            -RedirectStandardOutput (Join-Path $logDir 'companion.log') `
            -RedirectStandardError (Join-Path $logDir 'companion.log.err')
    }
    $deadline = (Get-Date).AddSeconds(60)
    while ((Get-Date) -lt $deadline) {
        if ((Get-CompanionPid) -ne 0 -and (Test-Health)) { Write-Host 'Companion ready (accounts-v1).'; return }
        Start-Sleep -Seconds 3
    }
    if ($candidate) { throw 'Companion did not pass its scoped health check within 60s. Run 3 - Status and inspect the companion task Last Run Result in Task Scheduler.' }
    throw "Companion did not pass its scoped health check within 60s. See $logDir\companion.log.err"
}

$task = Get-CompanionTask
if (-not $ConfigureCompanionTask) { Assert-Task $task }
elseif ($task) { $oldCompanion = Get-TaskJar $task }
if ($CheckOnly) {
    Assert-InstalledPair
    if ((Get-CompanionPid) -eq 0) { throw 'Installed pair verified, but companion is not running. Run 2 - Start.' }
    if (-not (Test-Health)) { throw 'Installed pair verified, but scoped health is unavailable. Run the pipeline check before trading.' }
    Write-Host 'Installed pair verified; companion responds with accounts-v1 (READY or DEGRADED).'
    return
}
$java = Find-Java
if ($StartOnly) {
    Assert-InstalledPair
    $startLock = [IO.File]::Open((Join-Path $runtime 'installation.lock'), 'OpenOrCreate', 'ReadWrite', 'None')
    try { Start-Companion (Get-CompanionTask) $java } finally { $startLock.Dispose() }
    return
}

# Never close RuneLite or send game input. The player closes every client before installation.
$processes = @(Get-CimInstance Win32_Process -ErrorAction Stop)
if ($processes | Where-Object { $_.Name -eq 'RuneLite.exe' -or $_.CommandLine -match 'net\.runelite\.client\.RuneLite' }) {
    throw 'Close RuneLite before setup or updating. No game process was stopped.'
}
if ($SkipBuild) {
    if (-not (Test-Path -LiteralPath $built -PathType Leaf)) { throw "Plugin jar not found at $built" }
    if (-not (Test-Path -LiteralPath $companionBuilt -PathType Leaf)) { throw "Companion jar not found at $companionBuilt" }
}

New-Item -ItemType Directory -Force -Path $runtime | Out-Null
# An exclusive file handle serializes installers from different checkout folders for this user.
$lock = [IO.File]::Open((Join-Path $runtime 'installation.lock'), 'OpenOrCreate', 'ReadWrite', 'None')
try {
    # Re-read after acquiring the lock: another checkout may have reconfigured the task meanwhile.
    $task = Get-CompanionTask
    if (-not $ConfigureCompanionTask) { Assert-Task $task }
    $oldCompanion = if ($task) { Get-TaskJar $task } else { $companionInstalled }
    $backup = Join-Path $dataRoot ('backups\' + (Get-Date).ToString('yyyyMMdd-HHmmss') + '-' + [Guid]::NewGuid().ToString('N').Substring(0,8))
    New-Item -ItemType Directory -Path $backup | Out-Null
    if ($task) {
        Export-ScheduledTask -TaskName $taskName -TaskPath '\' | Set-Content -LiteralPath (Join-Path $backup 'companion-task.xml') -Encoding UTF8
        Disable-ScheduledTask -TaskName $taskName -TaskPath '\' | Out-Null
        Stop-ScheduledTask -TaskName $taskName -TaskPath '\'
    }
    # Also stop a visible companion launched by hand; an unidentified listener is left alone.
    foreach ($process in $processes) {
        if ($process.CommandLine -like '*flipping-friend-companion*') { Stop-Process -Id $process.ProcessId -Force -ErrorAction Stop }
    }
    Wait-PortFree
    if (-not $oldCompanion) { $oldCompanion = $companionInstalled }
    foreach ($entry in @(@($installed, 'osrs-flipping-friend.jar'), @($oldCompanion, 'flipping-friend-companion.jar'), @($manifestPath, 'installation.json'))) {
        if (Test-Path -LiteralPath $entry[0] -PathType Leaf) {
            Copy-Item -LiteralPath $entry[0] -Destination (Join-Path $backup $entry[1]) -Force
        } else { Write-Host "Backup source absent: $($entry[0])" }
    }
    $accounts = Join-Path $logDir 'accounts'
    foreach ($directory in @($logDir, $accounts)) {
        if ((Test-Path -LiteralPath $directory) -and
            ((Get-Item -LiteralPath $directory -Force).Attributes -band [IO.FileAttributes]::ReparsePoint)) {
            throw 'Account data location contains a link; inspect it read-only before installing.'
        }
    }
    if (Test-Path -LiteralPath $accounts -PathType Container) {
        foreach ($account in @(Get-ChildItem -LiteralPath $accounts -Directory -Force)) {
            if ($account.Name -notmatch '^rs-[0-9a-f]{64}$') { continue }
            $entries = @($account) + @(Get-ChildItem -LiteralPath $account.FullName -Recurse -Force)
            if ($entries | Where-Object { $_.Attributes -band [IO.FileAttributes]::ReparsePoint }) { throw 'Account backup contains a link; inspect it read-only before installing.' }
            $destination = Join-Path $backup 'accounts'
            New-Item -ItemType Directory -Force -Path $destination | Out-Null
            Copy-Item -LiteralPath $account.FullName -Destination $destination -Recurse -Force
        }
    } else { Write-Host 'No scoped accounts directory observed. Legacy databases are left untouched and unassigned.' }
    Write-Host "Backup complete: $backup"
    Get-ChildItem -LiteralPath $backup -File -Recurse | ForEach-Object { Write-Host "$($_.FullName)  $($_.Length) bytes" }
    Write-Host 'The companion is stopped. Existing task remains disabled until installation completes.'
    if ((Read-Host 'Review the backup above. Type INSTALL to install both JARs') -cne 'INSTALL') {
        throw 'Installation cancelled. JARs and account data were not changed; companion remains stopped.'
    }
    if (-not $SkipBuild) {
        # Select an installed JDK for the supported builder: jar :companion:shadowJar.
        $jdkCandidates = @($env:JAVA_HOME, (Split-Path -Parent (Split-Path -Parent $java)), 'C:\Program Files\Java\jdk-23')
        $jdk = $jdkCandidates | Where-Object { $_ -and (Test-Path -LiteralPath (Join-Path $_ 'bin\javac.exe') -PathType Leaf) } | Select-Object -First 1
        if (-not $jdk) { throw 'Building requires an installed JDK. Installed JARs were not replaced.' }
        $env:JAVA_HOME = $jdk
        & "$PSScriptRoot\build.ps1"
        if ($LASTEXITCODE -ne 0) { throw "Build failed with exit code $LASTEXITCODE; installed JARs were not replaced." }
    }
    if (-not (Test-Path -LiteralPath $built -PathType Leaf)) { throw "Plugin jar not found at $built" }
    if (-not (Test-Path -LiteralPath $companionBuilt -PathType Leaf)) { throw "Companion jar not found at $companionBuilt" }
    if ($ConfigureCompanionTask) {
        & "$PSScriptRoot\register-companion-task.ps1" -Java $java -Jar $companionInstalled -Checkout $root
    }
    $task = Get-CompanionTask
    if ($ConfigureCompanionTask -and -not $task) {
        throw 'Companion task registration is not observable. Installed JARs were not replaced; inspect Task Scheduler before retrying.'
    }
    Assert-Task $task -AllowDisabled
    New-Item -ItemType Directory -Force -Path (Split-Path $installed) | Out-Null
    Copy-Item -LiteralPath $built -Destination $installed -Force
    Copy-Item -LiteralPath $companionBuilt -Destination $companionInstalled -Force
    @{ schemaVersion = 1; checkout = $root; backupDirectory = $backup; installedAtUtc = [DateTime]::UtcNow.ToString('o')
        pluginSha256 = Get-ArtifactSha256 $installed
        companionSha256 = Get-ArtifactSha256 $companionInstalled
    } | ConvertTo-Json | Set-Content -LiteralPath $manifestPath -Encoding UTF8
    Assert-InstalledPair
    if ($task) { Enable-ScheduledTask -TaskName $taskName -TaskPath '\' | Out-Null }
    $task = Get-CompanionTask
    Start-Companion $task $java
    Write-Host 'Update applied. Both installed JARs verified. Restart RuneLite to pick up plugin changes.'
} finally { $lock.Dispose() }
