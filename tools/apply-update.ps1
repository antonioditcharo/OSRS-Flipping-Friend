# Applies a rebuilt version of Flipping Friend safely.
#
# The reason this exists: replacing a jar underneath a running JVM does not fail loudly, it fails
# later and somewhere else. Java opens a jar once and reads classes out of it lazily, so overwriting
# the file leaves every class that has not been loaded yet unreachable. The companion ran for ninety
# minutes after such a swap before OkHttp needed one more Kotlin class, threw NoClassDefFoundError on
# a thread nobody was watching, and stopped ingesting market data entirely — while still answering
# health checks and still describing itself as ready.
#
# So: stop first, swap second, start third, and do not claim success until the thing answers.

param(
    [switch] $SkipBuild
)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$taskName = 'FlippingFriendCompanion'
$built = Join-Path $root 'build\libs\osrs-flipping-friend.jar'
$companionBuilt = Join-Path $root 'companion\build\libs\flipping-friend-companion.jar'

function Normalize-AbsolutePath([string] $path) {
    if (-not $path -or -not [IO.Path]::IsPathRooted($path)) {
        throw 'Scheduled companion task must use absolute checkout and JAR paths.'
    }
    return [IO.Path]::GetFullPath($path).TrimEnd([char[]]@('\', '/'))
}

function Get-ValidatedCompanionTask {
    # Enumerate the root task folder so absence is normal, but an unreadable task store fails closed.
    $tasks = @(Get-ScheduledTask -TaskPath '\' -ErrorAction Stop |
        Where-Object { $_.TaskName -eq $taskName })
    if ($tasks.Count -eq 0) { return $null }
    if ($tasks.Count -ne 1) { throw 'Multiple companion tasks found; update was not applied.' }
    $candidate = $tasks[0]
    if ($candidate.State -eq 'Disabled' -or $candidate.Settings.Enabled -eq $false) {
        throw 'The companion scheduled task is disabled; update was not applied. Review its configuration before updating.'
    }
    $actions = @($candidate.Actions)
    if ($actions.Count -ne 1) { throw 'Companion task must have exactly one Java action; update was not applied.' }
    $action = $actions[0]
    if ((Normalize-AbsolutePath $action.WorkingDirectory) -ne (Normalize-AbsolutePath $root)) {
        throw "Companion task points to a different checkout; update was not applied. Expected: $root"
    }
    # Accept the supported installer action (optional heap limit, then one quoted or bare JAR).
    # Extra application arguments could change the data location and are deliberately rejected.
    if ($action.Arguments -notmatch '^\s*(?:-Xmx[1-9][0-9]*[kKmMgG]\s+)?-jar\s+(?:"(?<quoted>[^"]+)"|(?<bare>\S+))\s*$') {
        throw 'Companion task has unsupported Java/JAR arguments; update was not applied.'
    }
    $jar = if ($Matches['quoted']) { $Matches['quoted'] } else { $Matches['bare'] }
    if ((Normalize-AbsolutePath $jar) -ne (Normalize-AbsolutePath $companionBuilt)) {
        throw "Companion task points to a different JAR; update was not applied. Expected: $companionBuilt"
    }
    $executable = Normalize-AbsolutePath $action.Execute
    if ([IO.Path]::GetFileName($executable) -notmatch '^javaw?\.exe$' -or
        -not (Test-Path -LiteralPath $executable -PathType Leaf)) {
        throw 'Companion task must use an existing Java executable; update was not applied.'
    }
    return $candidate
}

function Find-Java {
    $candidates = @()
    if ($env:JAVA_HOME) { $candidates += Join-Path $env:JAVA_HOME 'bin\java.exe' }
    $candidates += @(
        (Get-Command java -ErrorAction SilentlyContinue).Source,
        'C:\Program Files\Java\jdk-23\bin\java.exe'
    )
    foreach ($candidate in $candidates) {
        if ($candidate -and (Test-Path $candidate)) { return $candidate }
    }
    throw 'No Java runtime found. Set JAVA_HOME or put java on the PATH.'
}

# Whoever is actually holding the companion's port, whether or not we can read their command line.
#
# The command-line scan below is blind to the one process that matters most. The companion is
# normally started by its scheduled task, which runs under an S4U token in session 0, and
# Win32_Process returns a NULL CommandLine for a process an ordinary user query is not entitled to
# inspect. So the scan finds nothing, this script concludes nothing is running, starts a second
# companion, and that one dies with "Address already in use" while the old build carries on serving.
#
# The health check afterwards then passes -- because the old companion answers it -- and the script
# reports "Update applied." That is the worst shape a deployment failure can take: the fix is built,
# the plugin is copied, the script says it worked, and the process making the decisions is the one
# from before. It happened, and the only trace was a BindException in companion.log.err.
#
# A listening socket cannot hide. Find it by the port.
function Get-CompanionPid {
    $conn = Get-NetTCPConnection -LocalPort 37777 -State Listen -ErrorAction SilentlyContinue |
        Select-Object -First 1
    if ($conn) { return [int] $conn.OwningProcess }
    return 0
}

function Wait-PortFree([int] $seconds = 20) {
    $deadline = (Get-Date).AddSeconds($seconds)
    while ((Get-Date) -lt $deadline) {
        if ((Get-CompanionPid) -eq 0) { return $true }
        Start-Sleep -Milliseconds 500
    }
    return $false
}

function Stop-Ours([string] $match, [string] $label) {
    $found = Get-CimInstance Win32_Process -Filter "Name='java.exe' or Name='javaw.exe'" -ErrorAction SilentlyContinue |
        Where-Object { $_.CommandLine -like "*$match*" }
    foreach ($process in $found) {
        Write-Host "  stopping $label (pid $($process.ProcessId))"
        Stop-Process -Id $process.ProcessId -Force -ErrorAction SilentlyContinue
    }
    if ($found) { Start-Sleep -Seconds 3 }
    return [bool]$found
}

$java = Find-Java
# Reject a disabled or foreign-checkout task before stopping processes, building, or copying files.
$task = Get-ValidatedCompanionTask
Write-Host 'Flipping Friend - applying update'
Write-Host ''

# 1. Stop everything that holds a jar open, BEFORE any of them are rewritten.
Write-Host 'Stopping running components...'
$hadCompanion = Stop-Ours 'flipping-friend-companion' 'companion'

# And again by port, for the copy the scan cannot see. The scheduled task has to be stopped too, or
# it simply restarts the old jar underneath us.
$portPid = Get-CompanionPid
if ($portPid -ne 0) {
    Write-Host "  stopping companion holding port 37777 (pid $portPid)"
    Stop-ScheduledTask -TaskName 'FlippingFriendCompanion' -ErrorAction SilentlyContinue
    Stop-Process -Id $portPid -Force -ErrorAction SilentlyContinue
    $hadCompanion = $true
}
if (-not (Wait-PortFree)) {
    throw 'Something is still listening on 37777. The new companion cannot start while it is there.'
}

# 2. Build with nothing holding the outputs open.
if (-not $SkipBuild) {
    Write-Host ''
    Write-Host 'Building...'
    Push-Location $root
    try {
        & .\gradlew.bat jar :companion:shadowJar --console=plain -q
        if ($LASTEXITCODE -ne 0) { throw "Build failed with exit code $LASTEXITCODE." }
    }
    finally { Pop-Location }
    Write-Host '  build ok'
}

# 3. Require both artifacts and recheck task configuration before replacing the installed plugin.
if (-not (Test-Path -LiteralPath $built -PathType Leaf)) { throw "Plugin jar not found at $built" }
if (-not (Test-Path -LiteralPath $companionBuilt -PathType Leaf)) { throw "Companion jar not found at $companionBuilt" }
$task = Get-ValidatedCompanionTask
$installed = Join-Path $env:USERPROFILE '.runelite\sideloaded-plugins\osrs-flipping-friend.jar'
New-Item -ItemType Directory -Force -Path (Split-Path $installed) | Out-Null
Copy-Item -LiteralPath $built -Destination $installed -Force
Write-Host "  plugin installed ($((Get-Item $installed).Length) bytes)"

# 4. Start the companion on the new build.
$logDir = Join-Path $env:USERPROFILE '.runelite\osrs-flipping-friend\companion'
New-Item -ItemType Directory -Force -Path $logDir | Out-Null

Write-Host ''
Write-Host 'Starting companion...'
# Through the scheduled task where there is one, so the thing that owns the companion the
# rest of the time is the thing that started it. A bare process here is restarted by the
# task's hourly watchdog anyway, and then there are two of them racing for the port.
if ($task) {
    Start-ScheduledTask -TaskName $taskName -TaskPath '\'
} else {
    Start-Process -FilePath $java `
        -ArgumentList '-jar', "`"$companionBuilt`"" `
        -WindowStyle Hidden `
        -RedirectStandardOutput (Join-Path $logDir 'companion.log') `
        -RedirectStandardError (Join-Path $logDir 'companion.log.err')
}

# 5. Do not report success until it answers. A process that started is not a process that works.
$properties = Join-Path $logDir 'companion.properties'
$deadline = (Get-Date).AddSeconds(60)
$healthy = $false
Write-Host ''
Write-Host 'Waiting for the companion to answer...'
while ((Get-Date) -lt $deadline) {
    Start-Sleep -Seconds 3
    if (-not (Test-Path $properties)) { continue }
    $match = Select-String -Path $properties -Pattern '^token=(.+)$' -ErrorAction SilentlyContinue
    if (-not $match) { continue }
    $token = $match.Matches[0].Groups[1].Value.Trim()
    try {
        $reply = & curl.exe -s -m 8 -H "X-Flipping-Friend-Token: $token" 'http://127.0.0.1:37777/v1/health'
        if ($reply -and $reply -match '"status"\s*:\s*"(\w+)"') {
            $status = $Matches[1]
            Write-Host "  health: $status"
            # DEGRADED straight after a restart is expected - the market cache is cold - so answering
            # at all is the bar here. Ingestion freshness is the watchdog's job from now on.
            $healthy = $true
            break
        }
    }
    catch { }
}

Write-Host ''
if ($healthy) {
    Write-Host "Update applied. Companion pid $(Get-CompanionPid)." -ForegroundColor Green
} else {
    Write-Host 'Companion did not answer within 60s. Check:' -ForegroundColor Yellow
    Write-Host "  $logDir\companion.log.err"
    exit 1
}
Write-Host ''
Write-Host 'RuneLite loads side-loaded plugins at startup only - restart it to pick up plugin changes.'
