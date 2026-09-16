# Walks the whole Flipping Friend pipeline and reports on each stage.
#
# The reason this exists: the unit suite can be entirely green while the running system is broken, and
# on this project it was. A save failed on every single cycle for hours -- the daemon printed the error
# faithfully into a console nobody was watching -- and the learner quietly recorded fourteen hours of
# trades with no failures in them, which the model then trained on. Nothing in the test suite could
# have caught that, because none of it looks at the machine actually running.
#
# So this checks the artefacts and the processes, not the code: files that should be fresh, counters
# that should not climb, data that should contain failures as well as successes, and a companion that
# should answer.
#
# Run it after any deploy. It changes nothing except posting throwaway account snapshots to the local
# companion, which RuneLite overwrites on its next tick.

param(
    [switch] $SkipBuild,
    [switch] $SkipWalkForward
)

$ErrorActionPreference = 'Continue'
$root = Split-Path -Parent $PSScriptRoot
$dataRoot = Join-Path $env:USERPROFILE '.runelite\osrs-flipping-friend'
$learning = Join-Path $dataRoot 'market-cache\learning'
$companionDir = Join-Path $dataRoot 'companion'

$script:pass = 0
$script:fail = 0
$script:warn = 0

function Report {
    param([string] $Stage, [string] $Verdict, [string] $Detail)
    $colour = switch ($Verdict) {
        'PASS' { 'Green' }
        'FAIL' { 'Red' }
        'WARN' { 'Yellow' }
        default { 'Gray' }
    }
    switch ($Verdict) {
        'PASS' { $script:pass++ }
        'FAIL' { $script:fail++ }
        'WARN' { $script:warn++ }
    }
    Write-Host ("{0,-6}" -f $Verdict) -ForegroundColor $colour -NoNewline
    Write-Host ("{0,-26} {1}" -f $Stage, $Detail)
}

function Find-Java {
    foreach ($candidate in @(
        (Join-Path $env:JAVA_HOME 'bin\java.exe'),
        (Get-Command java -ErrorAction SilentlyContinue).Source,
        'C:\Program Files\Java\jdk-23\bin\java.exe')) {
        if ($candidate -and (Test-Path $candidate)) { return $candidate }
    }
    return $null
}

Write-Host ''
Write-Host 'Flipping Friend - pipeline check' -ForegroundColor Cyan
Write-Host ("Started {0}" -f (Get-Date -Format 'yyyy-MM-dd HH:mm:ss'))
Write-Host ''

# ---------------------------------------------------------------- build and tests

if ($SkipBuild) {
    Report 'Build' 'SKIP' 'skipped by request'
} else {
    Push-Location $root
    & .\gradlew.bat jar daemonJar :companion:shadowJar :companion:monitorJar `
        :companion:replayJar :companion:trainerJar --console=plain -q 2>&1 | Out-Null
    $built = $LASTEXITCODE -eq 0
    Pop-Location
    if ($built) { Report 'Build' 'PASS' 'every jar task succeeded' }
    else { Report 'Build' 'FAIL' 'a jar task failed - run gradlew directly to see it' }
}

Push-Location $root
& .\gradlew.bat test --console=plain -q 2>&1 | Out-Null
$testsGreen = $LASTEXITCODE -eq 0
$total = 0; $failed = 0
Get-ChildItem -Path $root -Recurse -Filter '*.xml' -ErrorAction SilentlyContinue |
    Where-Object { $_.FullName -like '*build\test-results\test\*' } |
    ForEach-Object {
        $head = (Get-Content $_.FullName -TotalCount 4) -join ' '
        if ($head -match 'tests="(\d+)"') { $total += [int]$Matches[1] }
        if ($head -match 'failures="(\d+)"') { $failed += [int]$Matches[1] }
        if ($head -match 'errors="(\d+)"') { $failed += [int]$Matches[1] }
    }
Pop-Location
if ($testsGreen -and $failed -eq 0) { Report 'Unit suite' 'PASS' "$total tests, 0 failing" }
else { Report 'Unit suite' 'FAIL' "$total tests, $failed failing" }

# ---------------------------------------------------------------- hygiene

$strays = @()
foreach ($dir in @($learning, $companionDir)) {
    if (Test-Path $dir) {
        $strays += Get-ChildItem -Path $dir -Filter '*.tmp' -ErrorAction SilentlyContinue
    }
}
if ($strays.Count -eq 0) {
    Report 'Hygiene' 'PASS' 'no stray .tmp beside any data file'
} else {
    # A .tmp left behind means an atomic replace failed and the real file is stale. That is the exact
    # shape of the bug that cost fourteen hours of learner data.
    Report 'Hygiene' 'FAIL' ("stale temp files, so a save is failing: " + ($strays.Name -join ', '))
}

$daemonLog = Join-Path $dataRoot 'market-cache\daemon.log'
if (Test-Path $daemonLog) {
    # All three ways a save can fail, because the first hunt for this missed one of them and the
    # answer was sitting in the log the whole time: a single unserialisable value ("Infinity is not a
    # valid double") aborted the write before the rename was ever reached, so neither of the other two
    # messages appeared and the file simply stopped changing.
    # Only this run. The log is appended across restarts, so a fault that has since been fixed would
    # otherwise fail this check for ever and teach you to ignore it.
    $lines = Get-Content $daemonLog
    $lastStart = 0
    for ($i = 0; $i -lt $lines.Count; $i++) {
        if ($lines[$i] -like '*Flipping Friend learning daemon*') { $lastStart = $i }
    }
    $current = $lines[$lastStart..($lines.Count - 1)]
    $saveFailures = @($current | Where-Object {
        $_ -like '*SAVE FAILED*' -or $_ -like '*could not write*' -or $_ -like '*could not be replaced*' })
    if ($saveFailures.Count -eq 0) {
        Report 'Daemon saves' 'PASS' 'no save failures logged'
    } else {
        $last = "$($saveFailures[-1])".Trim()
        Report 'Daemon saves' 'FAIL' "$($saveFailures.Count) this run, last: $last"
    }
} else {
    Report 'Daemon saves' 'FAIL' 'no daemon log at all - it is running blind again'
}

# ---------------------------------------------------------------- daemon

$daemon = Get-CimInstance Win32_Process -Filter "Name='java.exe' or Name='javaw.exe'" -ErrorAction SilentlyContinue |
    Where-Object { $_.CommandLine -like '*flipping-friend-daemon*' }
if ($daemon) { Report 'Daemon process' 'PASS' ("running, pid " + $daemon.ProcessId) }
else { Report 'Daemon process' 'FAIL' 'not running' }

if (Test-Path $daemonLog) {
    $cycles = Select-String -Path $daemonLog -Pattern 'watching (\d+), resolved (\d+), opened (\d+), dropped (\d+)'
    if ($cycles.Count -gt 0) {
        $last = $cycles[-1]
        $age = [int]((Get-Date) - (Get-Item $daemonLog).LastWriteTime).TotalMinutes
        $detail = "last cycle {0} min ago: {1}" -f $age, $last.Matches[0].Value
        # A cycle is ~5.5 minutes; twice that means it has stopped.
        if ($age -le 12) { Report 'Daemon cycling' 'PASS' $detail }
        else { Report 'Daemon cycling' 'FAIL' $detail }

        # Drops are trades discarded unjudged. They are not random -- only a trade that failed ever
        # reaches its deadline -- so a climbing count means failures are being deleted from the
        # training set, which is what made everything look 100% successful.
        $recent = $cycles | Select-Object -Last 6
        $counts = @($recent | ForEach-Object { [int]$_.Matches[0].Groups[4].Value })
        # The counter is per-process, so a restart resets it to zero. Comparing first to last across a
        # restart reads as a large fall and would hide a genuine climb after it, so only the run since
        # the last reset counts.
        $start = 0
        for ($i = 1; $i -lt $counts.Count; $i++) { if ($counts[$i] -lt $counts[$i - 1]) { $start = $i } }
        $window = $counts[$start..($counts.Count - 1)]
        $climb = $window[-1] - $window[0]
        $span = $window.Count
        if ($span -lt 2) { Report 'Daemon drops' 'INFO' "only $span cycle since the last restart - not enough to judge" }
        elseif ($climb -le 2) { Report 'Daemon drops' 'PASS' "stable over $span cycles since restart (+$climb)" }
        else { Report 'Daemon drops' 'FAIL' "climbing +$climb over $span cycles - failures are being discarded" }
    } else {
        Report 'Daemon cycling' 'FAIL' 'no cycle lines in the log'
    }
}

foreach ($name in @('daemon-open.json', 'daemon-series.json', 'daemon-settled.json')) {
    $file = Join-Path $learning $name
    if (-not (Test-Path $file)) { Report "File $name" 'FAIL' 'missing'; continue }
    $age = [int]((Get-Date) - (Get-Item $file).LastWriteTime).TotalMinutes
    $size = '{0:N1} MB' -f ((Get-Item $file).Length / 1MB)
    if ($age -le 12) { Report "File $name" 'PASS' "$size, written $age min ago" }
    else { Report "File $name" 'FAIL' "$size, but $age min stale - the save is not landing" }
}

# ---------------------------------------------------------------- learner data

$java = Find-Java
$settled = Join-Path $learning 'daemon-settled.json'
if ((Test-Path $settled) -and (Get-Command python -ErrorAction SilentlyContinue)) {
    $probe = @'
import json, sys, time, collections
path, = sys.argv[1:]
now = int(time.time())
try:
    rows = json.load(open(path, encoding="utf-8"))
except Exception as ex:
    print("PARSE_FAIL", ex); raise SystemExit
# Only cohorts older than the four-hour watch window have finished answering. Grading anything
# younger counts the winners and none of the losers, because a trade that fills resolves early and
# one that fails resolves exactly at its deadline.
closed = [r for r in rows if 0 < r.get("openedAt", 0) <= now - 4*3600]
recent = [r for r in closed if r.get("openedAt", 0) >= now - 24*3600]
pool = recent if len(recent) >= 20 else closed[-500:]
c = collections.Counter(r.get("outcome") for r in pool)
bought = c.get("COMPLETED", 0) + c.get("STUCK", 0)
rate = (100.0 * c.get("COMPLETED", 0) / bought) if bought else -1
failures = c.get("STUCK", 0) + c.get("NEVER_BOUGHT", 0)
print("OK|%d|%d|%d|%d|%.1f" % (len(rows), len(pool), failures, bought, rate))
'@
    $probeFile = Join-Path $env:TEMP 'ff-settled-probe.py'
    Set-Content -Path $probeFile -Value $probe -Encoding utf8
    $out = & python $probeFile $settled 2>&1
    if ($out -like 'OK|*') {
        $parts = ($out -split '\|')
        $records = [int]$parts[1]; $graded = [int]$parts[2]
        $failures = [int]$parts[3]; $rate = [double]$parts[5]
        Report 'Learner data' 'PASS' "$records records parse, $graded in the closed cohort"
        # The canary. A closed cohort with no failures at all is not a good result, it is the
        # corruption returning: every trade that did not fill has been discarded.
        if ($failures -gt 0) {
            Report 'Learner failures' 'PASS' "$failures failures present, completion $rate%"
        } else {
            Report 'Learner failures' 'FAIL' 'a closed cohort with zero failures - drops are eating them again'
        }
    } else {
        Report 'Learner data' 'FAIL' "could not parse: $out"
    }
} else {
    Report 'Learner data' 'WARN' 'skipped (needs python and the settled file)'
}

# ---------------------------------------------------------------- companion

$props = Join-Path $companionDir 'companion.properties'
$token = $null
if (Test-Path $props) {
    $token = (Get-Content $props | Where-Object { $_ -like 'token=*' }) -replace '^token=', ''
}

function Invoke-Companion {
    param([string] $Path, [string] $Body)
    $headers = @{ 'X-Flipping-Friend-Token' = $token }
    try {
        if ($Body) {
            return Invoke-RestMethod -Uri "http://127.0.0.1:37777/v1/$Path" -Method Post `
                -Headers $headers -ContentType 'application/json' -Body $Body -TimeoutSec 10
        }
        return Invoke-RestMethod -Uri "http://127.0.0.1:37777/v1/$Path" -Headers $headers -TimeoutSec 10
    } catch { return $null }
}

function New-Snapshot {
    param([int] $FreeSlots, [bool] $SellOnly, [string] $Id)
    $now = [int][double]::Parse((Get-Date -UFormat %s))
    return "{`"correlationId`":`"$Id`",`"observedAt`":$now,`"spendableCoins`":46000000," +
           "`"committedCoins`":0,`"freeSlots`":$FreeSlots,`"totalSlots`":3,`"members`":false," +
           "`"bankSeen`":false,`"markedSessionDrawdown`":0,`"riskAppetite`":`"HIGH`"," +
           "`"committedByItem`":{},`"minProfitPerFlip`":5000,`"sellOnly`":$($SellOnly.ToString().ToLower())}"
}

$health = if ($token) { Invoke-Companion -Path 'health' } else { $null }
if (-not $health) {
    Report 'Companion' 'FAIL' 'not answering on loopback 37777'
} else {
    Report 'Companion' 'PASS' "status $($health.status), model v$($health.modelVersion)"
    # A cold cache vetoes every candidate with "waiting for price history", so a plan check run now
    # would fail for a reason that is not a fault.
    $cacheCold = $false
    if ($health.reason -match 'Studying items: (\d+) of (\d+)') {
        if ([int]$Matches[1] -eq 0) {
            $cacheCold = $true
            Report 'Series cache' 'WARN' 'cold - every candidate is vetoed until it warms, so the plan checks below cannot mean anything yet'
        } else {
            Report 'Series cache' 'PASS' "$($Matches[1]) of $($Matches[2]) items have history"
        }
    }

    $ids = @{}
    foreach ($case in @(@{ n = 'free'; s = 3; so = $false }, @{ n = 'busy'; s = 0; so = $false }, @{ n = 'sellonly'; s = 3; so = $true })) {
        $id = "pipecheck-$($case.n)-$(Get-Random)"
        $ids[$case.n] = $id
        Invoke-Companion -Path 'events/account-state' -Body (New-Snapshot -FreeSlots $case.s -SellOnly $case.so -Id $id) | Out-Null
        Start-Sleep -Seconds 8
    }

    $planProbe = @'
import sqlite3, json, sys
db, = sys.argv[1:]
con = sqlite3.connect("file:%s?mode=ro" % db, uri=True)
found = {}
for created, payload in con.execute(
        "select created_at, payload from portfolio_plan order by created_at desc limit 400"):
    d = json.loads(payload)
    cid = d.get("correlationId", "")
    if cid.startswith("pipecheck-") and cid not in found:
        diag = d.get("diagnostics") or {}
        vetoes = diag.get("vetoCounts") or {}
        # The dominant veto, so an empty plan explains itself instead of leaving somebody to go and
        # ask the companion by hand. A cold series cache and a genuinely dead market look identical
        # from the outside and are not remotely the same thing.
        top = max(vetoes.items(), key=lambda kv: kv[1])[0][:44] if vetoes else "-"
        found[cid] = (d.get("status"), d.get("reason") or "", len(d.get("allocations") or []),
                      d.get("boardGpPerSlotHour", 0), top, diag.get("tacticsGenerated", 0),
                      len(d.get("bench") or []), diag.get("slotsTotal", 0))
for cid, v in found.items():
    print("%s|%s|%s|%d|%.0f|%s|%d|%d|%d" % (cid, v[0], v[1][:60], v[2], v[3], v[4], v[5], v[6], v[7]))
'@
    $planFile = Join-Path $env:TEMP 'ff-plan-probe.py'
    Set-Content -Path $planFile -Value $planProbe -Encoding utf8
    $planOut = & python $planFile (Join-Path $companionDir 'flipping-friend.db') 2>&1

    $plans = @{}
    foreach ($line in $planOut) {
        $p = "$line" -split '\|'
        if ($p.Count -ge 9) {
            $plans[$p[0]] = @{ status = $p[1]; reason = $p[2]; allocs = [int]$p[3]
                               board = [double]$p[4]; veto = $p[5]; tactics = [int]$p[6]
                               bench = [int]$p[7]; slots = [int]$p[8] }
        }
    }

    $free = $plans[$ids['free']]
    $busy = $plans[$ids['busy']]
    $sellOnly = $plans[$ids['sellonly']]

    if ($free -and $free.status -eq 'READY' -and $free.allocs -gt 0) {
        Report 'Plan (slots free)' 'PASS' "READY, $($free.allocs) offered, board $($free.board)"
    } elseif ($free) {
        # Empty is not automatically wrong: after a restart the series cache is cold and every
        # candidate is vetoed for want of history, which looks exactly like a dead market unless the
        # funnel is shown.
        Report 'Plan (slots free)' 'WARN' `
            "$($free.status) - $($free.tactics) tactics survived, dominant veto: $($free.veto)"
    } else {
        Report 'Plan (slots free)' 'FAIL' 'no plan produced for the posted snapshot'
    }

    # The change that made the "is it getting better" chart mean anything: a full board still ranks a
    # board, so the rate exists even when nothing can be placed.
    if ($busy -and $busy.status -eq 'UNAVAILABLE' -and $busy.reason -match 'slot') {
        if ($busy.board -gt 0) {
            Report 'Plan (slots busy)' 'PASS' "refused by name, still rates the board at $($busy.board)"
        } elseif ($cacheCold -or $busy.veto -like '*price history*') {
            # No candidates means no board, which is correct rather than broken. The health line only
            # says "0 of 90" while the cache is completely empty, but a partly warm cache can still
            # leave every shortlisted item without history -- and the funnel says so directly, which
            # is the signal worth trusting. Failing during a known warm-up is how a check teaches you
            # to ignore it.
            Report 'Plan (slots busy)' 'WARN' "no board rate yet - still warming: $($busy.veto)"
        } else {
            Report 'Plan (slots busy)' 'FAIL' 'refused with no board rate - the metric goes blank again'
        }
    } elseif ($busy) {
        Report 'Plan (slots busy)' 'WARN' "$($busy.status): $($busy.reason)"
    } else {
        Report 'Plan (slots busy)' 'FAIL' 'no plan produced'
    }

    # The bench: the queue of trades behind what can be placed right now.
    #
    # These were the same list, so the portfolio shortened as slots filled and emptied completely at
    # zero free slots -- which is exactly when knowing what is next is worth most, and it is also
    # what made the learning monitor read "no plan at all" for long stretches. A full board was
    # already being ranked every cycle and thrown away. This stage exists because that regression is
    # invisible: a short list looks like a quiet market.
    $benched = @($free, $busy) | Where-Object { $_ -ne $null -and $_.tactics -gt 0 }
    if ($benched.Count -eq 0) {
        Report 'Plan bench' 'WARN' 'no plan had candidates to queue - still warming'
    } else {
        $short = $benched | Where-Object { $_.bench -lt $_.slots }
        if ($short) {
            $worst = $short[0]
            Report 'Plan bench' 'FAIL' `
                "only $($worst.bench) queued against $($worst.slots) slots - the bench is capped again"
        } else {
            $busiest = $benched | Sort-Object { $_.allocs } | Select-Object -First 1
            Report 'Plan bench' 'PASS' `
                "$($busiest.bench) queued on $($busiest.slots) slots with $($busiest.allocs) placeable"
        }
    }

    if ($sellOnly -and $sellOnly.reason -match 'ell-only') {
        Report 'Sell-only mode' 'PASS' 'refused by name rather than blaming the slots'
    } elseif ($sellOnly) {
        Report 'Sell-only mode' 'FAIL' "wrong reason: $($sellOnly.reason)"
    } else {
        Report 'Sell-only mode' 'FAIL' 'no plan produced'
    }
}

# ---------------------------------------------------------------- databases

$dbProbe = @'
import sqlite3, sys, os
for path in sys.argv[1:]:
    if not os.path.exists(path):
        continue
    con = sqlite3.connect("file:%s?mode=ro" % path, uri=True)
    tables = [r[0] for r in con.execute("select name from sqlite_master where type='table'").fetchall()]
    biggest = []
    for t in tables:
        n = con.execute("select count(*) from %s" % t).fetchone()[0]
        try:
            b = con.execute("select sum(length(cast(payload as blob))) from %s" % t).fetchone()[0] or 0
        except Exception:
            b = 0
        if b > 5e6:
            biggest.append("%s %.0fMB" % (t, b / 1e6))
    wal = path + "-wal"
    walsize = os.path.getsize(wal) / 1e6 if os.path.exists(wal) else 0
    print("%s|%.1f|%.1f|%s" % (os.path.basename(path), os.path.getsize(path) / 1e6, walsize,
                               ", ".join(biggest) if biggest else "-"))
'@
$dbFile = Join-Path $env:TEMP 'ff-db-probe.py'
Set-Content -Path $dbFile -Value $dbProbe -Encoding utf8
$dbOut = & python $dbFile (Join-Path $companionDir 'flipping-friend.db') (Join-Path $companionDir 'replay.db') 2>&1
foreach ($line in $dbOut) {
    $p = "$line" -split '\|'
    if ($p.Count -ge 4) {
        $detail = "{0} MB, WAL {1} MB, large tables: {2}" -f $p[1], $p[2], $p[3]
        Report ("DB " + $p[0]) 'INFO' $detail
    }
}

# ---------------------------------------------------------------- walk-forward

if ($SkipWalkForward) {
    Report 'Walk-forward' 'SKIP' 'skipped by request'
} elseif ($java) {
    $replayJar = Join-Path $root 'companion\build\libs\flipping-friend-replay.jar'
    if (Test-Path $replayJar) {
        $replay = & $java -jar $replayJar 90 40000000 5m 2>&1
        $summary = ($replay | Select-String 'gates passed') -join ''
        $gateFails = ($replay | Select-String '^FAIL').Count
        # Reported, never enforced. The same code gave 1 of 3 folds and 3 of 3 on consecutive runs;
        # a pass/fail verdict on one run would teach you to ignore this whole report.
        if ($gateFails -eq 0) { Report 'Walk-forward' 'PASS' $summary.Trim() }
        else { Report 'Walk-forward' 'WARN' ($summary.Trim() + ' - one run settles nothing, variance here is large') }
    } else {
        Report 'Walk-forward' 'WARN' 'replay jar not built'
    }
} else {
    Report 'Walk-forward' 'WARN' 'no java found'
}

# ---------------------------------------------------------------- the plugin leg

# RuneLite reads side-loaded plugins at startup only, so a deploy since it launched means it is
# running old code -- and every check above would still pass, because they exercise the companion and
# the daemon. Comparing the installed jar against the client's start time is the one way to tell from
# outside, and it is worth telling: most of a day's work here has been plugin-side.
$installed = Join-Path $env:USERPROFILE '.runelite\sideloaded-plugins\osrs-flipping-friend.jar'
$client = Get-CimInstance Win32_Process -Filter "Name='java.exe' or Name='javaw.exe'" -ErrorAction SilentlyContinue |
    Where-Object { $_.CommandLine -like '*runelite*' -and $_.CommandLine -notlike '*flipping-friend-*' } |
    Select-Object -First 1

if (-not (Test-Path $installed)) {
    Report 'Plugin installed' 'FAIL' 'no side-loaded jar - the plugin is not deployed at all'
} elseif (-not $client) {
    Report 'Plugin in RuneLite' 'WARN' 'RuneLite is not running, so the plugin leg is idle'
} else {
    $jarTime = (Get-Item $installed).LastWriteTime
    $startTime = $client.CreationDate
    if ($startTime -gt $jarTime) {
        Report 'Plugin in RuneLite' 'PASS' ("running code from {0:HH:mm}, client started {1:HH:mm}" -f $jarTime, $startTime)
    } else {
        Report 'Plugin in RuneLite' 'FAIL' ("jar rebuilt {0:HH:mm} but client started {1:HH:mm} - restart RuneLite" -f $jarTime, $startTime)
    }
}

# The plugin only proves itself by talking. A recent account snapshot means it is loaded, logged in and
# posting; without one the checks above tested the companion against nothing but this script.
if ($token) {
    $accProbe = @'
import sqlite3, sys, time, json
db, = sys.argv[1:]
con = sqlite3.connect("file:%s?mode=ro" % db, uri=True)
row = con.execute("select observed_at, payload from event_log where event_type='ACCOUNT_STATE' "
                  "order by observed_at desc limit 1").fetchone()
if not row:
    print("NONE")
else:
    d = json.loads(row[1])
    # sellOnly only exists on builds from this session, so its presence dates the running plugin.
    print("OK|%d|%s|%s|%s" % (int(time.time()) - row[0], "sellOnly" in d,
                              d.get("freeSlots"), d.get("totalSlots")))
'@
    $accFile = Join-Path $env:TEMP 'ff-account-probe.py'
    Set-Content -Path $accFile -Value $accProbe -Encoding utf8
    $acc = & python $accFile (Join-Path $companionDir 'flipping-friend.db') 2>&1
    $ap = "$acc" -split '\|'
    if ($ap[0] -eq 'OK') {
        $age = [int]$ap[1]
        $detail = "last snapshot {0}s ago, {1} of {2} slots free" -f $age, $ap[3], $ap[4]
        if ($age -le 120 -and $ap[2] -eq 'True') { Report 'Plugin posting' 'PASS' $detail }
        elseif ($age -le 120) { Report 'Plugin posting' 'FAIL' "$detail - but sending an old payload shape" }
        else { Report 'Plugin posting' 'WARN' "$detail - stale, so RuneLite is closed or logged out" }
    } else {
        Report 'Plugin posting' 'WARN' 'no account snapshot has ever arrived'
    }
}


# ---------------------------------------------------------------- dropped trades
#
# A sale that settles with nothing behind it is a trade that has vanished: no journal row, no held
# item, no profit recorded, and until now no message anywhere. 90 Rune nails sold for 70,200 gp and
# left no trace of having been traded at all -- the position holding their cost had been deleted by a
# reconcile pass hours earlier, so the journal write bailed on "no known cost basis" and returned.
#
# The offer ledger keeps the evidence: pendingUnbackedQuantity is exactly how many units were sold
# that the book could not account for. This stage exists because that number was sitting on disk the
# whole time and nothing ever looked at it.
$accountsDir = Join-Path $dataRoot 'accounts'
$offerFiles = @()
if (Test-Path $accountsDir) {
    $offerFiles = @(Get-ChildItem -Path $accountsDir -Filter 'offers.json' -Recurse -ErrorAction SilentlyContinue)
}
if ($offerFiles.Count -eq 0) {
    Report 'Dropped trades' 'WARN' 'no offer ledger on disk yet - the plugin has not traded here'
} else {
    $unbacked = @()
    foreach ($file in $offerFiles) {
        try { $offers = Get-Content $file.FullName -Raw | ConvertFrom-Json } catch { continue }
        foreach ($offer in @($offers)) {
            if ($offer.pendingUnbackedQuantity -gt 0) {
                $unbacked += "{0} x{1}" -f $offer.itemName, $offer.pendingUnbackedQuantity
            }
        }
    }
    if ($unbacked.Count -eq 0) {
        Report 'Dropped trades' 'PASS' 'every settled sale had a cost basis behind it'
    } else {
        Report 'Dropped trades' 'FAIL' `
            ("sales with no cost basis, so these flips are missing from the journal: " +
             ($unbacked -join ', '))
    }
}

Write-Host ''
Write-Host ("{0} passed, {1} failed, {2} warnings" -f $script:pass, $script:fail, $script:warn) `
    -ForegroundColor $(if ($script:fail -gt 0) { 'Red' } else { 'Green' })
Write-Host ''
if ($script:fail -gt 0) { exit 1 }
