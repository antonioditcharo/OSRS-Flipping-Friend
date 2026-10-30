"""Execute the updater with isolated files and mocked Windows/process/network boundaries."""

import json
import hashlib
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest
import zipfile


HARNESS = r"""
$ErrorActionPreference = 'Stop'
Import-Module Microsoft.PowerShell.Utility
$fixture = [IO.Path]::GetFullPath($env:FF_UPDATE_TEST_ROOT)
$scenario = Get-Content -Raw -LiteralPath (Join-Path $fixture 'scenario.json') | ConvertFrom-Json
$env:USERPROFILE = Join-Path $fixture 'profile'
$env:LOCALAPPDATA = Join-Path $fixture 'localappdata'
$env:JAVA_HOME = Join-Path $fixture 'java'
if ($scenario.noJavaHome) { Remove-Item -LiteralPath Env:JAVA_HOME -ErrorAction SilentlyContinue }
$global:ffUpdateTestEvents = [System.Collections.Generic.List[string]]::new()
$global:ffUpdateTestStarted = $false
$global:ffUpdateTestFixture = $fixture
$global:ffUpdateTestTask = $null
$global:ffUpdateTestClock = 0
$global:ffHealthProbes = 0
$global:ffHealthTimeouts = [System.Collections.Generic.List[int]]::new()
$global:ffHealthClockOrigin = [DateTime]::UtcNow
$global:ffProcessStopped = $false
$global:ffSchedulerRaceInjected = $false
$global:ffProcessArguments = @()
$env:USERNAME = 'fixture-user'

# Every command that can affect a real process, task or network connection is intercepted.
function Get-Command {
    [CmdletBinding()] param([string[]]$Name, [string[]]$Module, [switch]$ListImported, [switch]$All)
    if ($Name.Count -eq 1 -and $Name[0] -eq 'java' -and -not $Module) {
        [pscustomobject]@{ Source = (Join-Path $fixture 'java/bin/java.exe') }
    } else {
        # PowerShell 5.1 uses command lookup while loading its Get-FileHash function.
        # Only Java discovery is fake; preserve native utility-module lookup.
        Microsoft.PowerShell.Core\Get-Command @PSBoundParameters
    }
}
function Get-ScheduledTask {
    [CmdletBinding()] param($TaskName, $TaskPath)
    if (-not $global:ffUpdateTestTask -and $scenario.taskPresent) {
        $global:ffUpdateTestTask = [pscustomobject]@{
            TaskName = 'FlippingFriendCompanion'; TaskPath = '\'; State = $scenario.state
            Settings = [pscustomobject]@{ Enabled = ($scenario.state -ne 'Disabled') }
            Actions = @([pscustomobject]@{
                Execute = $scenario.executable
                Arguments = $scenario.arguments
                WorkingDirectory = $scenario.workingDirectory
            })
        }
    }
    if ($global:ffUpdateTestTask) { return $global:ffUpdateTestTask }
}
function Get-CimInstance {
    [CmdletBinding()] param($ClassName, $Filter)
    if ($scenario.gameRunning) { [pscustomobject]@{ ProcessId = 100; Name = 'RuneLite.exe'; CommandLine = 'RuneLite' } }
    if ($scenario.userProcess -or $scenario.processManifest) {
        if (-not $global:ffProcessStopped) {
            [pscustomobject]@{ ProcessId=202; Name='java.exe'; CreationDate='fixture-start'; CommandLine=$(if($scenario.maskedProcessLine){$null}else{'java.exe '+$scenario.arguments}) }
        }
    } else { [pscustomobject]@{ ProcessId = 101; Name = 'java.exe'; CommandLine = 'java -jar flipping-friend-companion.jar' } }
}
function Invoke-CimMethod {
    [CmdletBinding()] param($InputObject, $MethodName)
    [pscustomobject]@{ReturnValue=0;Sid=$(if($scenario.foreignProcessOwner){'foreign-sid'}else{'fixture-sid'})}
}
function Get-NetTCPConnection {
    [CmdletBinding()] param($LocalPort, $State)
    if ($scenario.listenerChangesAfterProbe -and $global:ffHealthProbes -ge $scenario.listenerChangesAfterProbe) { [pscustomobject]@{ OwningProcess = 303 } }
    elseif ($scenario.listenerDisappearsAfterProbe -and $global:ffHealthProbes -ge $scenario.listenerDisappearsAfterProbe) { return }
    elseif ($scenario.opaquePort) { [pscustomobject]@{ OwningProcess = 999 } }
    elseif ($global:ffUpdateTestStarted -or $scenario.alreadyRunning) { [pscustomobject]@{ OwningProcess = 202 } }
}
function Stop-Process {
    [CmdletBinding()] param($Id, [switch]$Force)
    if ($scenario.stopProcessDenied) { $global:ffUpdateTestEvents.Add('stop-process-denied'); throw 'Fixture stop access denied.' }
    $global:ffUpdateTestEvents.Add('stop-process')
    $scenario.alreadyRunning = $false
    $global:ffProcessStopped = $true
    $global:ffUpdateTestStarted = $false
}
function Stop-ScheduledTask {
    [CmdletBinding()] param($TaskName, $TaskPath)
    $global:ffUpdateTestEvents.Add('stop-task')
    $scenario.alreadyRunning = $false
    $global:ffUpdateTestStarted = $false
}
function Unregister-ScheduledTask {
    [CmdletBinding()] param($TaskName, $TaskPath, [switch]$Confirm)
    $global:ffUpdateTestEvents.Add('unregister-task')
    $global:ffUpdateTestTask = $null
    $scenario.taskPresent = $false
}
function Remove-Item {
    [CmdletBinding()] param([Alias('Path')]$LiteralPath, [switch]$Force)
    if (-not [IO.Path]::GetFullPath($LiteralPath).StartsWith($fixture + [IO.Path]::DirectorySeparatorChar,
        [StringComparison]::OrdinalIgnoreCase)) { throw 'Test attempted removal outside its fixture.' }
    $global:ffUpdateTestEvents.Add('remove-file')
    Microsoft.PowerShell.Management\Remove-Item -LiteralPath $LiteralPath -Force:$Force
}
function Start-ScheduledTask {
    [CmdletBinding()] param($TaskName, $TaskPath)
    $global:ffUpdateTestEvents.Add('start-task')
    if ($global:ffUpdateTestTask.State -eq 'Disabled') { throw 'Mock scheduled task is disabled.' }
    $global:ffUpdateTestStarted = $true
}
function Start-Process {
    [CmdletBinding()] param($FilePath, $ArgumentList, $WorkingDirectory, $WindowStyle, $RedirectStandardOutput, $RedirectStandardError)
    $global:ffUpdateTestEvents.Add('start-process')
    $global:ffProcessArguments = @($ArgumentList)
    $global:ffUpdateTestStarted = $true
}
function Start-Sleep {
    param($Seconds, $Milliseconds)
    if ($scenario.healthFastClock) { $global:ffUpdateTestClock += $Seconds + $Milliseconds / 1000.0 }
}
function Get-Date {
    if ($scenario.healthFastClock) { return $global:ffHealthClockOrigin.AddSeconds($global:ffUpdateTestClock) }
    if ($scenario.fastTime) { $global:ffUpdateTestClock++; return [DateTime]::UtcNow.AddSeconds(10 * $global:ffUpdateTestClock) }
    return [DateTime]::UtcNow
}
function Export-ScheduledTask {
    [CmdletBinding()] param($TaskName, $TaskPath)
    $global:ffUpdateTestEvents.Add('export-task')
    $action = $global:ffUpdateTestTask.Actions[0]
    $enabled = if ($global:ffUpdateTestTask.Settings.Enabled) { 'true' } else { 'false' }
    $execute = [Security.SecurityElement]::Escape($action.Execute)
    $arguments = [Security.SecurityElement]::Escape($action.Arguments)
    $working = [Security.SecurityElement]::Escape($action.WorkingDirectory)
    return "<Task xmlns=`"http://schemas.microsoft.com/windows/2004/02/mit/task`"><Principals><Principal><UserId>fixture-user</UserId></Principal></Principals><Settings><Enabled>$enabled</Enabled></Settings><Actions><Exec><Command>$execute</Command><Arguments>$arguments</Arguments><WorkingDirectory>$working</WorkingDirectory></Exec></Actions></Task>"
}
function Disable-ScheduledTask {
    [CmdletBinding()] param($TaskName, $TaskPath)
    if ($scenario.taskReadOnly) { throw 'Access is denied to the protected companion task.' }
    $global:ffUpdateTestEvents.Add('disable-task')
    $global:ffUpdateTestTask.State = 'Disabled'
    $global:ffUpdateTestTask.Settings.Enabled = $false
}
function Enable-ScheduledTask {
    [CmdletBinding()] param($TaskName, $TaskPath)
    $global:ffUpdateTestEvents.Add('enable-task')
    $global:ffUpdateTestTask.State = 'Ready'
    $global:ffUpdateTestTask.Settings.Enabled = $true
}
function New-ScheduledTaskAction {
    param($Execute, $Argument, $WorkingDirectory)
    [pscustomobject]@{ Execute=$Execute; Arguments=$Argument; WorkingDirectory=$WorkingDirectory }
}
function New-ScheduledTaskTrigger {
    param([switch]$AtLogOn, $User, [switch]$Once, $At, $RepetitionInterval)
    [pscustomobject]@{ User=$User }
}
function New-ScheduledTaskPrincipal {
    param($UserId, $LogonType, $RunLevel)
    [pscustomobject]@{ UserId=$UserId; LogonType=$LogonType }
}
function New-ScheduledTaskSettingsSet {
    param([switch]$Disable, [switch]$AllowStartIfOnBatteries, [switch]$DontStopIfGoingOnBatteries, [switch]$StartWhenAvailable,
        $RestartCount, $RestartInterval, $ExecutionTimeLimit, $MultipleInstances)
    [pscustomobject]@{ Enabled=(-not $Disable) }
}
function Register-ScheduledTask {
    [CmdletBinding()] param($TaskName, $TaskPath, $Action, $Trigger, $Principal, $Settings, [switch]$Force, $Description)
    $global:ffUpdateTestEvents.Add('register-task')
    if ($scenario.registrationNotVisible) {
        $global:ffUpdateTestTask = $null
        $scenario.taskPresent = $false
        return
    }
    $global:ffUpdateTestTask = [pscustomobject]@{
        TaskName=$TaskName; TaskPath=$TaskPath; State='Disabled'; Actions=@($Action); Settings=$Settings
    }
}
function Invoke-WebRequest {
    param([switch]$UseBasicParsing, $Uri, $Headers, $TimeoutSec)
    if ($Uri -eq 'https://static.runelite.net/bootstrap.json') {
        if ($scenario.releaseFails) { throw 'Synthetic release metadata connection failure.' }
        $version = if ($scenario.officialVersion) { $scenario.officialVersion } else { '1.13.2' }
        if ($scenario.releaseMalformed) { return [pscustomobject]@{ Content='{"version":"invalid","artifacts":[]}' } }
        return [pscustomobject]@{ Content=(@{version=$version;artifacts=@(@{name="client-$version.jar"})} | ConvertTo-Json -Compress -Depth 4) }
    }
    $global:ffHealthProbes++
    $global:ffHealthTimeouts.Add([int]$TimeoutSec)
    if ($scenario.healthProbeDelaySeconds) {
        $global:ffUpdateTestClock += $(if($scenario.ignoreHealthTimeout){$scenario.healthProbeDelaySeconds}else{[Math]::Min($TimeoutSec,$scenario.healthProbeDelaySeconds)})
    }
    if ($scenario.healthNeverResponds -or $global:ffHealthProbes -le $scenario.healthTransientFailures) { throw 'Synthetic health connection timeout.' }
    if ($scenario.healthHttpStatus) {
        $failure = [Exception]::new('Synthetic health HTTP failure.')
        $failure | Add-Member NoteProperty Response ([pscustomobject]@{StatusCode=$scenario.healthHttpStatus})
        throw $failure
    }
    if ($scenario.healthMalformed) { return [pscustomobject]@{Headers=@{'X-Flipping-Friend-Protocol'='accounts-v1'};Content='{'} }
    $protocol = if ($scenario.badHealth) { 'legacy-v0' } else { 'accounts-v1' }
    $healthStatus = if ($scenario.healthStatus) { $scenario.healthStatus } else { 'READY' }
    [pscustomobject]@{ Headers=@{ 'X-Flipping-Friend-Protocol'=$protocol }; Content=(@{status=$healthStatus} | ConvertTo-Json -Compress) }
}
function New-Object {
    param($ComObject)
    if ($ComObject -ne 'WScript.Shell') { throw 'Unexpected COM request.' }
    $shell = [pscustomobject]@{}
    $shell | Add-Member ScriptMethod CreateShortcut {
        param($path)
        $link = [pscustomobject]@{ TargetPath=''; WorkingDirectory=''; IconLocation=''; Description='' }
        $link | Add-Member ScriptMethod Save {
            $global:ffUpdateTestEvents.Add('save-shortcut')
            $this | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $global:ffUpdateTestFixture 'shortcut.json')
        }
        return $link
    }
    return $shell
}
function Clear-Host { }
function Read-Host {
    param($Prompt)
    $global:ffUpdateTestEvents.Add('prompt:' + $Prompt)
    if ($scenario.decline) { return 'no' }
    if ($scenario.changeTaskAfterReview) { $global:ffUpdateTestTask.Actions[0].WorkingDirectory = Join-Path $fixture 'foreign-checkout' }
    return 'INSTALL'
}
function curl.exe { '{"status":"READY"}' }
function Copy-Item {
    [CmdletBinding()] param([Alias('LiteralPath')]$Path, $Destination, [switch]$Force, [switch]$Recurse)
    # Do not permit even a future updater change to copy outside this disposable fixture.
    foreach ($candidate in @($Path, $Destination)) {
        if (-not [IO.Path]::GetFullPath($candidate).StartsWith(
            $fixture + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase)) {
            throw 'Test attempted to access files outside its fixture.'
        }
    }
    if ($Destination -match 'backups[\\/]') {
        if ($scenario.backupFails) { throw 'Synthetic backup copy failed.' }
        $global:ffUpdateTestEvents.Add('backup-file')
    }
    elseif ($Path -like '*osrs-flipping-friend.jar') { $global:ffUpdateTestEvents.Add('copy-plugin') }
    else {
        $global:ffUpdateTestEvents.Add('copy-companion')
        if ($scenario.failCompanionCopy) { throw 'Synthetic companion copy failed.' }
    }
    Microsoft.PowerShell.Management\Copy-Item -LiteralPath $Path -Destination $Destination -Force:$Force -Recurse:$Recurse
    if ($scenario.schedulerRace -and -not $global:ffSchedulerRaceInjected -and $Destination -like '*.stage-*' -and $Path -eq (Join-Path $fixture 'checkout/companion/build/libs/flipping-friend-companion.jar')) {
        $global:ffSchedulerRaceInjected=$true
        $global:ffProcessStopped=$false
        $scenario.alreadyRunning=$true
    }
    if ($scenario.tamperBackupJar -and $Destination -match 'backups[\\/].*osrs-flipping-friend.jar$') {
        $bytes = [IO.File]::ReadAllBytes($Destination); $bytes[0] = $bytes[0] -bxor 1
        [IO.File]::WriteAllBytes($Destination, $bytes)
    }
    if ($scenario.truncateBackupAccount -and $Destination -match 'backups[\\/].*[\\/]plugin-accounts$') {
        [IO.File]::WriteAllBytes((Join-Path $Destination 'fixture/offer-event-outbox.json'), [byte[]]@(1))
    }
}

$failure = $null
try {
    $entry = if ($scenario.entry) { $scenario.entry } else { 'apply-update.ps1' }
    $options = @{ }
    if ($entry -eq 'apply-update.ps1') {
        $options.SkipBuild = (-not $scenario.buildRequested)
        if ($scenario.setup) { $options.ConfigureCompanionTask = $true }
        if ($scenario.startOnly) { $options.StartOnly = $true }
        if ($scenario.unattended) { $options.Unattended = $true }
        if ($scenario.userProcess) { $options.UserProcess = $true }
        if ($scenario.resumeBackup) { $options.ResumeBackup = $scenario.resumeBackup }
    }
    if ($entry -eq 'setup.ps1') {
        $options.SkipBuild = $true
        $options.ShortcutDirectory = Join-Path $fixture 'profile/Desktop'
    }
    if ($entry -eq 'uninstall.ps1') { $options.ShortcutDirectory = Join-Path $fixture 'profile/Desktop' }
    & (Join-Path $fixture ('checkout/tools/' + $entry)) @options
}
catch { $failure = $_.Exception.Message }
Write-Output ('HARNESS_RESULT=' + (@{
    error = $failure; events = @($global:ffUpdateTestEvents)
    processArguments = @($global:ffProcessArguments)
    healthProbes = $global:ffHealthProbes; healthTimeouts = @($global:ffHealthTimeouts); elapsed = $global:ffUpdateTestClock
} | ConvertTo-Json -Compress))
"""


class UpdateLifecycleTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.shell = os.environ.get("FF_UPDATE_TEST_SHELL") or shutil.which("powershell") or shutil.which("pwsh")
        if cls.shell is None:
            raise RuntimeError("PowerShell is required for the updater behavior tests.")

    def setUp(self):
        temporary = tempfile.TemporaryDirectory(prefix="flipping friend update ")
        self.addCleanup(temporary.cleanup)
        # Windows hosted runners can expose TEMP through an 8.3 alias. PowerShell expands
        # script locations, so resolve the fixture first to compare the same physical paths.
        self.fixture = Path(temporary.name).resolve()
        self.checkout = self.fixture / "checkout"
        self.plugin = self.checkout / "build/libs/osrs-flipping-friend.jar"
        self.companion = self.checkout / "companion/build/libs/flipping-friend-companion.jar"
        self.installed = self.fixture / "profile/.runelite/sideloaded-plugins/osrs-flipping-friend.jar"
        self.java = self.fixture / "java/bin/java.exe"
        self.installed_companion = self.fixture / "profile/.runelite/osrs-flipping-friend/runtime/flipping-friend-companion.jar"
        self.manifest = self.installed_companion.parent / "installation.json"
        self.properties = self.fixture / "profile/.runelite/osrs-flipping-friend/companion/companion.properties"
        for path, contents in (
            (self.plugin, b"new-plugin"), (self.companion, b"new-companion"),
            (self.installed, b"original-installed-plugin"), (self.java, b"not-an-executable"),
            (self.installed_companion, b"original-installed-companion"),
            (self.java.parent / "javac.exe", b"not-an-executable"),
            (self.fixture / "profile/.runelite/credentials.properties", b"existing-login-sentinel"),
            (self.properties, b"token=synthetic-test-token\n"),
        ):
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_bytes(contents)
        self.manifest.write_text(json.dumps({"schemaVersion": 1, "checkout": str(self.checkout),
            "pluginSha256": hashlib.sha256(self.installed.read_bytes()).hexdigest(),
            "companionSha256": hashlib.sha256(self.installed_companion.read_bytes()).hexdigest()}), encoding="utf-8")
        tools = self.checkout / "tools"
        tools.mkdir()
        (self.checkout / "build.gradle").write_text("ext.runeLiteVersion = '1.13.2'\n", encoding="utf-8")
        shutil.copyfile(Path(__file__).resolve().parents[1] / "apply-update.ps1", tools / "apply-update.ps1")
        shutil.copyfile(Path(__file__).resolve().parents[1] / "start.ps1", tools / "start.ps1")
        shutil.copyfile(Path(__file__).resolve().parents[1] / "setup.ps1", tools / "setup.ps1")
        shutil.copyfile(Path(__file__).resolve().parents[1] / "status.ps1", tools / "status.ps1")
        for name in ("uninstall.ps1", "uninstall-companion.ps1"):
            shutil.copyfile(Path(__file__).resolve().parents[1] / name, tools / name)
        shutil.copyfile(Path(__file__).resolve().parents[1] / "register-companion-task.ps1", tools / "register-companion-task.ps1")
        shutil.copyfile(Path(__file__).resolve().parents[1] / "process-companion.ps1", tools / "process-companion.ps1")
        # Native identity reads are intercepted too: never inspect a real PID in lifecycle fixtures.
        with (tools / "process-companion.ps1").open("a", encoding="utf-8") as helper:
            helper.write(r'''
function Get-NativeProcessCommandLine([int] $ProcessId) {
    $global:ffUpdateTestEvents.Add('read-native-args')
    if ($scenario.nativeReadFails) { throw 'Fixture native query refused.' }
    if ($scenario.nativeArguments) { return 'java.exe ' + $scenario.nativeArguments }
    return 'java.exe ' + $scenario.arguments
}
function Stop-NativeVerifiedCompanion($Process) {
    $global:ffUpdateTestEvents.Add('native-stop')
    $scenario.alreadyRunning = $false
    $global:ffProcessStopped = $true
    $global:ffUpdateTestStarted = $false
}
''')
        # This suite checks lifecycle ordering; native task ACL behavior has its own Windows tests.
        (tools / "enable-user-updates.ps1").write_text(
            "param([switch]$CheckOnly,[switch]$RequireControl,[switch]$PassThru)\n"
            "if ($PassThru) { if ($scenario.foreignTaskPrincipal) { throw 'Task principal does not match' }; return [pscustomobject]@{Sid=[pscustomobject]@{Value='fixture-sid'};Xml='fixture-xml';Sddl='fixture-sddl'} }\n"
            "if ($CheckOnly) { if ($RequireControl -and $scenario.taskReadOnly) { throw 'Run the one-time task permission repair.' }; return }\n"
            "$global:ffUpdateTestEvents.Add('grant-task-access')\n", encoding="utf-8")
        (tools / "install.ps1").write_text("$global:ffUpdateTestEvents.Add('legacy-install')\n$global:LASTEXITCODE = 0\n", encoding="utf-8")
        (tools / "run-dev.ps1").write_text("$global:ffUpdateTestEvents.Add('launch-runelite')\n", encoding="utf-8")
        (tools / "build.ps1").write_text(
            "$global:ffUpdateTestEvents.Add('build')\n"
            "if ($env:JAVA_HOME -ne (Join-Path $global:ffUpdateTestFixture 'java')) { throw 'Build JDK not selected.' }\n"
            "$global:LASTEXITCODE = if ($scenario.buildFails) { 1 } else { 0 }\n", encoding="utf-8")
        (tools / "install-companion.ps1").write_text("param([switch]$SkipBuild)\n$global:ffUpdateTestEvents.Add('legacy-companion-install')\n", encoding="utf-8")
        (self.fixture / "harness.ps1").write_text(HARNESS, encoding="utf-8")
        self.scenario = {
            "taskPresent": True, "state": "Ready", "alreadyRunning": False, "executable": str(self.java),
            "arguments": f'-Xmx768m -jar "{self.installed_companion}"',
            "workingDirectory": str(self.checkout),
        }

    def run_update(self):
        (self.fixture / "scenario.json").write_text(json.dumps(self.scenario), encoding="utf-8")
        result = subprocess.run(
            [self.shell, "-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass",
             "-File", str(self.fixture / "harness.ps1")],
            env=dict(os.environ, FF_UPDATE_TEST_ROOT=str(self.fixture)),
            capture_output=True, text=True, timeout=20,
        )
        self.assertEqual(0, result.returncode, result.stdout + result.stderr)
        marker = "HARNESS_RESULT="
        lines = [line for line in result.stdout.splitlines() if line.startswith(marker)]
        self.assertEqual(1, len(lines), result.stdout + result.stderr)
        self.output = result.stdout + result.stderr
        return json.loads(lines[0][len(marker):])

    def assert_rejected_without_changes(self, message):
        result = self.run_update()
        self.assertIsNotNone(result["error"], self.output)
        self.assertIn(message, result["error"], self.output)
        self.assertEqual([], result["events"], self.output)
        self.assertEqual(b"original-installed-plugin", self.installed.read_bytes())
        self.assertNotIn("Update applied.", self.output)

    def test_new_official_release_blocks_update_before_task_backup_or_jar_changes(self):
        self.scenario.update(officialVersion="1.13.3", alreadyRunning=True)
        before = {p: p.read_bytes() for p in (self.installed, self.installed_companion, self.manifest)}
        self.assert_rejected_without_changes("source targets 1.13.2")
        self.assertEqual(before, {p: p.read_bytes() for p in before})

    def test_protected_read_only_task_is_rejected_before_backup_or_mutation(self):
        self.scenario.update(taskReadOnly=True, unattended=True, alreadyRunning=True)
        self.assert_rejected_without_changes("one-time task permission repair")
        self.assertFalse((self.manifest.parent.parent / "backups").exists())

    def process_candidate(self):
        self.scenario.update(taskReadOnly=True, unattended=True, alreadyRunning=True, userProcess=True)
        with zipfile.ZipFile(self.companion, 'w') as jar:
            jar.writestr('META-INF/MANIFEST.MF', 'Manifest-Version: 1.0\nFF-Lifecycle-Guard: 1\n\n')
            jar.writestr('com/flippingfriend/companion/CompanionLifecycle.class', b'fixture')

    def test_user_process_update_preserves_protected_task_without_approval(self):
        self.process_candidate()
        pending = self.make_pending_account_record()
        result = self.run_update()
        self.assertIsNone(result['error'], self.output)
        self.assertTrue(pending.exists())
        self.assertEqual(self.companion.read_bytes(), self.installed_companion.read_bytes())
        self.assertEqual(b'new-plugin', self.installed.read_bytes())
        self.assertIn('start-process', result['events'])
        for event in ['disable-task', 'stop-task', 'enable-task', 'start-task', 'grant-task-access', 'register-task']:
            self.assertNotIn(event, result['events'], self.output)
        self.assertFalse(any(e.startswith('prompt:') for e in result['events']))
        self.assertEqual(1, json.loads(self.manifest.read_text(encoding='utf-8-sig'))['processLifecycleVersion'])

    def test_direct_process_preserves_the_existing_configured_heap_limit(self):
        self.process_candidate()
        result = self.run_update()
        self.assertIsNone(result['error'], self.output)
        self.assertEqual(['-Xmx768m', '-jar', f'"{self.installed_companion}"'], result['processArguments'])
        self.assertNotIn('register-task', result['events'])
        self.assertNotIn('grant-task-access', result['events'])

    def test_direct_process_without_configured_heap_does_not_invent_a_limit(self):
        self.process_candidate()
        self.scenario['arguments'] = f'-jar "{self.installed_companion}"'
        result = self.run_update()
        self.assertIsNone(result['error'], self.output)
        self.assertEqual(['-jar', f'"{self.installed_companion}"'], result['processArguments'])

    def test_masked_cim_command_line_uses_verified_native_identity_without_task_changes(self):
        self.process_candidate()
        self.scenario['maskedProcessLine'] = True
        pending = self.make_pending_account_record()
        result = self.run_update()
        self.assertIsNone(result['error'], self.output)
        self.assertIn('read-native-args', result['events'])
        self.assertIn('stop-process', result['events'])
        self.assertIn('start-process', result['events'])
        self.assertEqual(b'pending-event-sentinel', pending.read_bytes())
        self.assertNotIn('disable-task', result['events'])
        self.assertNotIn('grant-task-access', result['events'])

    def test_native_stop_fallback_keeps_supported_backup_and_pair_ordering(self):
        self.process_candidate()
        self.scenario.update(maskedProcessLine=True, stopProcessDenied=True)
        pending = self.make_pending_account_record()
        result = self.run_update()
        self.assertIsNone(result['error'], self.output)
        self.assertIn('stop-process-denied', result['events'])
        self.assertIn('native-stop', result['events'])
        self.assertLess(result['events'].index('native-stop'), result['events'].index('copy-plugin'))
        self.assertIn('start-process', result['events'])
        self.assertNotIn('disable-task', result['events'])
        self.assertEqual(b'pending-event-sentinel', pending.read_bytes())

    def test_native_fallback_cannot_stop_foreign_unknown_or_wrong_jar_process(self):
        self.process_candidate()
        original = {p: p.read_bytes() for p in (self.installed, self.installed_companion, self.manifest)}
        for fields in [{'foreignProcessOwner': True}, {'nativeReadFails': True},
                       {'nativeArguments': '-jar "unrelated.jar"'}]:
            with self.subTest(fields=fields):
                self.scenario.update(maskedProcessLine=True, **fields)
                result = self.run_update()
                self.assertIsNotNone(result['error'], self.output)
                self.assertNotIn('stop-process', result['events'])
                self.assertNotIn('export-task', result['events'])
                if fields.get('foreignProcessOwner'):
                    self.assertNotIn('read-native-args', result['events'])
                self.assertEqual(original, {p: p.read_bytes() for p in original})
                for flag in fields:
                    self.scenario.pop(flag)

    def test_user_process_rejects_unprotected_candidate_before_stopping(self):
        self.scenario.update(taskReadOnly=True, unattended=True, userProcess=True)
        result = self.run_update()
        self.assertIn('lifecycle guard', result['error'], self.output)
        self.assertEqual([], result['events'], self.output)

    def test_user_process_account_backup_failure_restores_only_runtime_artifacts(self):
        self.process_candidate()
        original = {p: p.read_bytes() for p in [self.installed, self.installed_companion, self.manifest]}
        pending = self.make_pending_account_record()
        self.scenario['truncateBackupAccount'] = True
        result = self.run_update()
        self.assertIsNotNone(result['error'], self.output)
        self.assertIn('Backup verification failed', result['error'], self.output)
        self.assertEqual(original, {p: p.read_bytes() for p in original})
        self.assertEqual(b'pending-event-sentinel', pending.read_bytes())
        self.assertNotIn('start-process', result['events'], self.output)

    def test_user_process_drains_old_scheduler_race_before_account_backup(self):
        self.process_candidate()
        self.scenario['schedulerRace'] = True
        self.make_pending_account_record()
        result = self.run_update()
        self.assertIsNone(result['error'], self.output)
        self.assertEqual(2, result['events'].count('stop-process'), self.output)

    def test_user_process_rejects_foreign_process_or_task_before_backup(self):
        for flag in ['foreignProcessOwner', 'foreignTaskPrincipal']:
            with self.subTest(flag=flag):
                self.process_candidate()
                self.scenario[flag] = True
                result = self.run_update()
                self.assertIsNotNone(result['error'], self.output)
                self.assertEqual([], result['events'], self.output)
                self.scenario[flag] = False

    def test_process_lifecycle_manifest_reuses_direct_start_without_task_changes(self):
        self.process_candidate()
        self.assertIsNone(self.run_update()['error'], self.output)
        self.scenario.update(userProcess=False, processManifest=True, alreadyRunning=False, startOnly=True, unattended=False)
        result = self.run_update()
        self.assertIsNone(result['error'], self.output)
        self.assertEqual(['start-process'], result['events'], self.output)

    def test_process_lifecycle_manifest_reuses_normal_unattended_update(self):
        self.process_candidate()
        self.assertIsNone(self.run_update()['error'], self.output)
        self.scenario.update(userProcess=False, processManifest=True)
        result = self.run_update()
        self.assertIsNone(result['error'], self.output)
        self.assertNotIn('disable-task', result['events'], self.output)
        self.assertIn('start-process', result['events'], self.output)

    def test_process_lifecycle_update_wrapper_needs_no_install_reply(self):
        self.process_candidate()
        self.assertIsNone(self.run_update()['error'], self.output)
        self.scenario.update(userProcess=False, processManifest=True, unattended=False, decline=True)
        result = self.run_update()
        self.assertIsNone(result['error'], self.output)
        self.assertFalse(any(event.startswith('prompt:') for event in result['events']), self.output)

    def test_user_process_update_keeps_running_client_and_unknown_listener_guards(self):
        for flag in ['gameRunning', 'opaquePort']:
            with self.subTest(flag=flag):
                self.process_candidate()
                self.scenario[flag] = True
                result = self.run_update()
                self.assertIsNotNone(result['error'], self.output)
                self.assertEqual([], result['events'], self.output)
                self.scenario[flag] = False

    def test_registration_grants_own_user_access_before_installation_is_enabled(self):
        self.scenario.update(setup=True)
        result = self.run_update()
        self.assertIsNone(result["error"], self.output)
        self.assertIn("grant-task-access", result["events"], self.output)
        self.assertLess(result["events"].index("register-task"), result["events"].index("grant-task-access"))
        self.assertLess(result["events"].index("grant-task-access"), result["events"].index("copy-plugin"))

    def test_release_lookup_failure_leaves_the_running_installation_alone(self):
        self.scenario.update(releaseFails=True, alreadyRunning=True)
        self.assert_rejected_without_changes("Could not verify official RuneLite release")

    def test_invalid_release_metadata_cannot_install_any_pair(self):
        self.scenario["releaseMalformed"] = True
        self.assert_rejected_without_changes("RuneLite release metadata is invalid")

    def test_disabled_task_is_rejected_before_stopping_or_installing(self):
        self.scenario["state"] = "Disabled"
        self.assert_rejected_without_changes("disabled")

    def test_other_checkout_task_cannot_report_a_successful_update(self):
        historical = self.fixture / "historical checkout"
        self.scenario["workingDirectory"] = str(historical)
        self.scenario["arguments"] = f'-jar "{historical / "companion/build/libs/flipping-friend-companion.jar"}"'
        self.assert_rejected_without_changes("checkout")

    def test_other_jar_is_rejected_even_when_working_directory_matches(self):
        self.scenario["arguments"] = f'-jar "{self.fixture / "old-companion.jar"}"'
        self.assert_rejected_without_changes("JAR")

    def test_missing_companion_does_not_replace_installed_plugin(self):
        self.companion.unlink()
        result = self.run_update()
        self.assertIn("Companion jar not found", result["error"], self.output)
        self.assertNotIn("copy-plugin", result["events"], self.output)
        self.assertEqual(b"original-installed-plugin", self.installed.read_bytes())

    def test_matching_task_updates_plugin_and_starts_verified_task(self):
        result = self.run_update()
        self.assertIsNone(result["error"], self.output)
        self.assertEqual(b"new-plugin", self.installed.read_bytes())
        self.assertIn("start-task", result["events"])
        self.assertNotIn("start-process", result["events"])
        self.assertLess(result["events"].index("copy-plugin"), result["events"].index("start-task"))
        self.assertIn("Update applied.", self.output)

    def make_pending_account_record(self):
        pending = self.properties.parent.parent / "accounts/fixture/offer-event-outbox.json"
        pending.parent.mkdir(parents=True)
        pending.write_bytes(b"pending-event-sentinel")
        return pending

    def test_unattended_update_verifies_backup_preserves_pending_data_and_never_prompts(self):
        pending = self.make_pending_account_record()
        self.scenario.update(unattended=True, decline=True)
        result = self.run_update()
        self.assertIsNone(result["error"], self.output)
        self.assertFalse(any(e.startswith("prompt:") for e in result["events"]), self.output)
        backup = next((self.properties.parent.parent / "backups").iterdir())
        self.assertEqual(pending.read_bytes(), (backup / "plugin-accounts/fixture/offer-event-outbox.json").read_bytes())
        receipt = json.loads((backup / "backup-verification.json").read_text(encoding="utf-8-sig"))
        self.assertEqual("VERIFIED", receipt["status"])
        self.assertEqual(1, receipt["pluginAccountFiles"])
        self.assertEqual(b"pending-event-sentinel", pending.read_bytes())
        self.assertEqual(b"new-plugin", self.installed.read_bytes())
        self.assertEqual(b"new-companion", self.installed_companion.read_bytes())
        self.assertIn("Backup verified; continuing unattended paired update.", self.output)

    def interrupted_backup(self):
        self.make_pending_account_record()
        self.scenario.update(unattended=True, buildRequested=True, buildFails=True)
        result = self.run_update()
        self.assertIn("Build failed", result["error"], self.output)
        backup = next((self.properties.parent.parent / "backups").iterdir())
        self.scenario.update(state="Disabled", buildFails=False, resumeBackup=str(backup))
        return backup

    def test_verified_interruption_can_resume_without_enabling_before_both_copies(self):
        backup = self.interrupted_backup()
        before = {p: p.read_bytes() for p in backup.rglob("*") if p.is_file()}
        result = self.run_update()
        self.assertIsNone(result["error"], self.output)
        self.assertIn("Interrupted update proof verified", self.output)
        self.assertFalse(any(e.startswith("prompt:") for e in result["events"]))
        self.assertLess(result["events"].index("copy-companion"), result["events"].index("enable-task"))
        self.assertEqual(before, {p: p.read_bytes() for p in before})
        self.assertEqual(2, len(list(backup.parent.iterdir())))

    def test_resume_rejects_changed_pair_or_manifest_before_task_mutation(self):
        self.interrupted_backup()
        self.installed.write_bytes(b"changed-installation")
        result = self.run_update()
        self.assertIsNotNone(result["error"], self.output)
        self.assertNotIn("disable-task", result["events"])
        self.assertNotIn("enable-task", result["events"])
        self.assertNotIn("copy-plugin", result["events"])

    def test_resume_rejects_even_a_valid_new_pair_not_matching_original_backup(self):
        self.interrupted_backup()
        self.installed.write_bytes(b"another-plugin")
        manifest = json.loads(self.manifest.read_text(encoding="utf-8-sig"))
        manifest["pluginSha256"] = hashlib.sha256(self.installed.read_bytes()).hexdigest()
        self.manifest.write_text(json.dumps(manifest), encoding="utf-8")
        result = self.run_update()
        self.assertIn("unchanged installed pair/manifest", result["error"], self.output)
        self.assertNotIn("disable-task", result["events"])

    def test_resume_rejects_a_backup_moved_outside_this_installation(self):
        backup = self.interrupted_backup()
        foreign = self.fixture / "foreign-backups" / backup.name
        shutil.copytree(backup, foreign)
        self.scenario["resumeBackup"] = str(foreign)
        result = self.run_update()
        self.assertIn("this installation", result["error"], self.output)
        self.assertNotIn("disable-task", result["events"])

    def test_resume_requires_receipt_and_does_not_accept_an_enabled_current_task(self):
        backup = self.interrupted_backup()
        (backup / "backup-verification.json").unlink()
        result = self.run_update()
        self.assertIn("receipt", result["error"].lower(), self.output)
        self.assertNotIn("disable-task", result["events"])
        self.scenario["state"] = "Ready"
        result = self.run_update()
        self.assertIn("disabled", result["error"].lower(), self.output)
        self.assertEqual([], result["events"])

    def test_resume_rejects_changed_task_definition(self):
        backup = self.interrupted_backup()
        task = backup / "companion-task.xml"
        task.write_text(task.read_text(encoding="utf-8-sig").replace("fixture-user", "foreign-user"), encoding="utf-8")
        result = self.run_update()
        self.assertIn("task definition", result["error"], self.output)
        self.assertNotIn("disable-task", result["events"])
        self.assertNotIn("enable-task", result["events"])

    def test_resume_rejects_corrupt_backup_jar(self):
        backup = self.interrupted_backup()
        (backup / "osrs-flipping-friend.jar").write_bytes(b"corrupt")
        result = self.run_update()
        self.assertIn("backup", result["error"].lower(), self.output)
        self.assertNotIn("disable-task", result["events"])

    def test_resume_requires_unchanged_account_file_names_and_sizes(self):
        self.interrupted_backup()
        (self.properties.parent.parent / "accounts/fixture/offer-event-outbox.json").write_bytes(b"changed")
        result = self.run_update()
        self.assertIn("account", result["error"].lower(), self.output)
        self.assertNotIn("disable-task", result["events"])

    def test_resume_requires_verified_receipt_in_this_checkouts_backup_directory(self):
        backup = self.interrupted_backup()
        receipt = backup / "backup-verification.json"
        data = json.loads(receipt.read_text(encoding="utf-8-sig"))
        data["checkout"] = str(self.fixture / "foreign-checkout")
        receipt.write_text(json.dumps(data), encoding="utf-8")
        result = self.run_update()
        self.assertIn("receipt", result["error"].lower(), self.output)
        self.assertNotIn("disable-task", result["events"])

    def test_resume_rejects_previously_disabled_task_in_original_backup(self):
        backup = self.interrupted_backup()
        task = backup / "companion-task.xml"
        task.write_text(task.read_text(encoding="utf-8-sig").replace("<Enabled>true</Enabled>", "<Enabled>false</Enabled>"), encoding="utf-8")
        result = self.run_update()
        self.assertIn("enabled", result["error"].lower(), self.output)
        self.assertNotIn("disable-task", result["events"])

    def test_resume_keeps_running_game_guard(self):
        self.interrupted_backup()
        self.scenario["gameRunning"] = True
        result = self.run_update()
        self.assertIn("Close RuneLite", result["error"], self.output)
        self.assertNotIn("disable-task", result["events"])

    def test_resume_is_explicit_and_only_for_unattended_disabled_task(self):
        self.interrupted_backup()
        self.scenario["unattended"] = False
        result = self.run_update()
        self.assertIn("Unattended", result["error"], self.output)
        self.assertEqual([], result["events"])

    def test_unattended_update_rejects_same_size_backup_jar_corruption_before_installation(self):
        self.scenario.update(unattended=True, tamperBackupJar=True)
        before = self.manifest.read_bytes()
        result = self.run_update()
        self.assertIn("Backup verification failed", result["error"], self.output)
        self.assertNotIn("copy-plugin", result["events"])
        self.assertNotIn("start-task", result["events"])
        self.assertEqual(b"original-installed-plugin", self.installed.read_bytes())
        self.assertEqual(before, self.manifest.read_bytes())

    def test_unattended_update_rejects_incomplete_account_backup_without_editing_source(self):
        pending = self.make_pending_account_record()
        self.scenario.update(unattended=True, truncateBackupAccount=True)
        result = self.run_update()
        self.assertIn("Backup verification failed", result["error"], self.output)
        self.assertNotIn("copy-plugin", result["events"])
        self.assertEqual(b"pending-event-sentinel", pending.read_bytes())

    def test_unattended_requires_a_verified_installed_pair_before_stopping_or_backing_up(self):
        self.installed.write_bytes(b"changed-installation")
        self.scenario["unattended"] = True
        result = self.run_update()
        self.assertIn("Installed JAR pair", result["error"], self.output)
        self.assertEqual([], result["events"])
        self.assertEqual(b"changed-installation", self.installed.read_bytes())

    def test_unattended_is_only_for_routine_updates_with_an_existing_task(self):
        for mode in ("setup", "startOnly"):
            self.scenario.update(unattended=True, **{mode: True})
            result = self.run_update()
            self.assertIn("Unattended is only supported for routine updates", result["error"], self.output)
            self.assertEqual([], result["events"])
            self.scenario[mode] = False
        self.scenario["taskPresent"] = False
        result = self.run_update()
        self.assertIn("Unattended update requires the existing companion task", result["error"], self.output)
        self.assertEqual([], result["events"])

    def test_unattended_still_rejects_running_runelite_without_stopping_the_game(self):
        self.scenario.update(unattended=True, gameRunning=True)
        self.assert_rejected_without_changes("Close RuneLite")

    def test_no_task_uses_direct_companion_launch(self):
        self.scenario["taskPresent"] = False
        result = self.run_update()
        self.assertIsNone(result["error"], self.output)
        self.assertEqual(b"new-plugin", self.installed.read_bytes())
        self.assertIn("start-process", result["events"])
        self.assertNotIn("start-task", result["events"])
        self.assertIn("Update applied.", self.output)

    def test_java_on_path_works_without_java_home(self):
        self.scenario["noJavaHome"] = True
        result = self.run_update()
        self.assertIsNone(result["error"], self.output)
        self.assertEqual(b"new-plugin", self.installed.read_bytes())
        self.assertIn("start-task", result["events"])
        self.assertIn("Update applied.", self.output)

    def test_daily_start_does_not_rebuild_or_install(self):
        self.scenario["entry"] = "start.ps1"
        # Stub the paired runtime entry here to isolate the launcher's delegation contract.
        (self.checkout / "tools/apply-update.ps1").write_text(
            "param([switch]$StartOnly)\nif (-not $StartOnly) { throw 'Expected start-only.' }\n"
            "$global:ffUpdateTestEvents.Add('guarded-start')\n", encoding="utf-8")
        result = self.run_update()
        self.assertIsNone(result["error"], self.output)
        self.assertEqual(["guarded-start", "launch-runelite"], result["events"], self.output)

    def test_daily_start_cannot_launch_after_guard_rejects_installation(self):
        self.scenario["entry"] = "start.ps1"
        (self.checkout / "tools/apply-update.ps1").write_text(
            "param([switch]$StartOnly)\nthrow 'Installation requires re-setup.'\n", encoding="utf-8")
        result = self.run_update()
        self.assertIsNotNone(result["error"], self.output)
        self.assertNotIn("launch-runelite", result["events"], self.output)

    def test_update_requires_backup_review_before_installation(self):
        self.scenario["decline"] = True
        result = self.run_update()
        self.assertIsNotNone(result["error"], self.output)
        self.assertEqual(b"original-installed-plugin", self.installed.read_bytes())
        self.assertNotIn("copy-plugin", result["events"], self.output)
        self.assertNotIn("start-task", result["events"], self.output)

    def test_update_deploys_companion_outside_build_directory(self):
        result = self.run_update()
        self.assertIsNone(result["error"], self.output)
        installed_companion = self.fixture / "profile/.runelite/osrs-flipping-friend/runtime/flipping-friend-companion.jar"
        self.assertTrue(installed_companion.is_file(), self.output)
        self.assertEqual(b"new-companion", installed_companion.read_bytes())

    def test_setup_reuses_saved_login_and_updates_shortcut_after_paired_install(self):
        self.scenario["entry"] = "setup.ps1"
        result = self.run_update()
        self.assertIsNone(result["error"], self.output)
        self.assertFalse(any("How do you" in event or "Jagex Launcher" in event for event in result["events"]))
        self.assertNotIn("legacy-install", result["events"], self.output)
        self.assertIn("register-task", result["events"], self.output)
        shortcut = json.loads((self.fixture / "shortcut.json").read_text(encoding="utf-8-sig"))
        self.assertEqual(str(self.checkout), shortcut["WorkingDirectory"])
        self.assertEqual(b"existing-login-sentinel", (self.fixture / "profile/.runelite/credentials.properties").read_bytes())

    def test_re_setup_backs_up_old_disabled_task_and_jar_before_rebinding(self):
        old = self.fixture / "old checkout/companion.jar"
        old.parent.mkdir()
        old.write_bytes(b"old-companion-sentinel")
        self.scenario.update(setup=True, state="Disabled", workingDirectory=str(old.parent), arguments=f'-jar "{old}"')
        result = self.run_update()
        self.assertIsNone(result["error"], self.output)
        backups = list((self.fixture / "profile/.runelite/osrs-flipping-friend/backups").iterdir())
        self.assertEqual(1, len(backups))
        self.assertEqual(b"old-companion-sentinel", (backups[0] / "flipping-friend-companion.jar").read_bytes())
        review = next(i for i, event in enumerate(result["events"]) if event.startswith("prompt:"))
        self.assertLess(result["events"].index("backup-file"), review)
        self.assertLess(review, result["events"].index("register-task"))
        self.assertLess(result["events"].index("copy-companion"), result["events"].index("enable-task"))
        self.assertEqual(b"old-companion-sentinel", old.read_bytes())

    def test_re_setup_decline_does_not_rebind_or_replace(self):
        self.scenario.update(setup=True, state="Disabled", decline=True)
        result = self.run_update()
        self.assertIsNotNone(result["error"])
        self.assertNotIn("register-task", result["events"], self.output)
        self.assertNotIn("copy-plugin", result["events"], self.output)
        self.assertNotIn("save-shortcut", result["events"], self.output)

    def test_running_runelite_blocks_installation_without_stopping_game(self):
        self.scenario["gameRunning"] = True
        result = self.run_update()
        self.assertIn("Close RuneLite", result["error"], self.output)
        self.assertEqual([], result["events"], self.output)

    def test_scoped_backup_preserves_sources_and_never_assigns_legacy_database(self):
        companion_data = self.fixture / "profile/.runelite/osrs-flipping-friend/companion"
        account = companion_data / "accounts" / ("rs-" + "a" * 64)
        account.mkdir(parents=True)
        for name in ("flipping-friend.db", "flipping-friend.db-wal", "pending.json"):
            (account / name).write_bytes(b"scoped-sentinel-" + name.encode())
        legacy = companion_data / "flipping-friend.db"
        legacy.write_bytes(b"unassigned-legacy-sentinel")
        result = self.run_update()
        self.assertIsNone(result["error"], self.output)
        backup = next((companion_data.parent / "backups").iterdir())
        for path in account.iterdir():
            expected = b"scoped-sentinel-" + path.name.encode()
            self.assertEqual(expected, path.read_bytes())
            self.assertEqual(expected, (backup / "accounts" / account.name / path.name).read_bytes())
        self.assertEqual(b"unassigned-legacy-sentinel", legacy.read_bytes())
        self.assertFalse(any(path.name == "flipping-friend.db" and "accounts" not in path.parts for path in backup.rglob("*")))

    def test_plugin_account_records_are_backed_up_before_install_confirmation(self):
        data_root = self.properties.parent.parent
        plugin_accounts = data_root / "accounts"
        records = {
            "default/session.json": b"pre-login-session-sentinel",
            "rsprofile_fixture/positions.json": b"positions-sentinel",
            "rsprofile_fixture/offers.json": b"offers-sentinel",
            "rsprofile_fixture/journal.jsonl": b"journal-sentinel",
            "rsprofile_fixture/companion-event-outbox.json": b"pending-events-sentinel",
            "rsprofile_fixture/migration-parked/transactions.jsonl": b"parked-history-sentinel",
        }
        for relative, contents in records.items():
            path = plugin_accounts / relative
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_bytes(contents)
        self.scenario["decline"] = True
        result = self.run_update()
        self.assertIn("Installation cancelled", result["error"], self.output)
        backup = next((data_root / "backups").iterdir())
        for relative, contents in records.items():
            saved = backup / "plugin-accounts" / relative
            self.assertTrue(saved.is_file(), f"Missing plugin account backup: {relative}\n{self.output}")
            self.assertEqual(contents, saved.read_bytes())
            self.assertEqual(contents, (plugin_accounts / relative).read_bytes())
        review = next(i for i, event in enumerate(result["events"]) if event.startswith("prompt:"))
        self.assertTrue(all(i < review for i, event in enumerate(result["events"]) if event == "backup-file"))
        self.assertNotIn("copy-plugin", result["events"], self.output)
        self.assertNotIn("start-task", result["events"], self.output)

    def test_link_in_plugin_account_records_blocks_backup_and_confirmation(self):
        plugin_accounts = self.properties.parent.parent / "accounts"
        plugin_accounts.mkdir()
        target = self.fixture / "unassigned-records"
        target.mkdir()
        (target / "journal.jsonl").write_bytes(b"linked-history-sentinel")
        link = plugin_accounts / "rsprofile_link"
        if os.name == "nt":
            quoted_link = str(link).replace("'", "''")
            quoted_target = str(target).replace("'", "''")
            created = subprocess.run(
                [self.shell, "-NoProfile", "-NonInteractive", "-Command",
                 "$ErrorActionPreference = 'Stop'; New-Item -ItemType Junction "
                 f"-Path '{quoted_link}' -Value '{quoted_target}' | Out-Null"],
                capture_output=True, text=True, timeout=20,
            )
            self.assertEqual(0, created.returncode, created.stdout + created.stderr)
        else:
            link.symlink_to(target, target_is_directory=True)
        result = self.run_update()
        self.assertIn("Plugin account backup contains a link", result["error"], self.output)
        self.assertFalse(any(event.startswith("prompt:") for event in result["events"]))
        self.assertNotIn("copy-plugin", result["events"], self.output)
        self.assertNotIn("start-task", result["events"], self.output)
        backup = next((self.properties.parent.parent / "backups").iterdir())
        self.assertFalse((backup / "plugin-accounts").exists())
        self.assertEqual(b"linked-history-sentinel", (target / "journal.jsonl").read_bytes())

    def test_incomplete_copy_cannot_start_or_claim_success(self):
        self.scenario["failCompanionCopy"] = True
        result = self.run_update()
        self.assertIn("Synthetic companion copy failed", result["error"], self.output)
        self.assertNotIn("enable-task", result["events"], self.output)
        self.assertNotIn("start-task", result["events"], self.output)
        self.assertNotIn("Update applied.", self.output)
        self.scenario.update(failCompanionCopy=False, startOnly=True)
        result = self.run_update()
        self.assertIn("JAR pair", result["error"], self.output)
        self.assertEqual([], result["events"], self.output)

    def test_start_only_does_not_require_build_outputs_or_change_installed_pair(self):
        self.scenario["startOnly"] = True
        self.plugin.unlink()
        self.companion.unlink()
        before = self.manifest.read_bytes()
        result = self.run_update()
        self.assertIsNone(result["error"], self.output)
        self.assertEqual(["start-task"], result["events"], self.output)
        self.assertEqual(before, self.manifest.read_bytes())

    def test_foreign_health_protocol_does_not_claim_installation_success(self):
        self.scenario.update(badHealth=True, fastTime=True)
        result = self.run_update()
        self.assertIn("scoped health check", result["error"], self.output)
        self.assertNotIn("Update applied.", self.output)

    def test_build_selects_jdk_and_failure_keeps_installed_pair(self):
        self.scenario.update(buildRequested=True, buildFails=True, noJavaHome=True)
        result = self.run_update()
        self.assertIn("Build failed", result["error"], self.output)
        self.assertIn("build", result["events"], self.output)
        self.assertNotIn("copy-plugin", result["events"], self.output)
        self.assertEqual(b"original-installed-plugin", self.installed.read_bytes())

    def test_unknown_port_owner_is_not_killed_or_overwritten(self):
        self.scenario.update(opaquePort=True, fastTime=True)
        result = self.run_update()
        self.assertIn("still occupied", result["error"], self.output)
        self.assertNotIn("copy-plugin", result["events"], self.output)
        self.assertNotIn("start-task", result["events"], self.output)

    def test_task_changed_during_review_is_rejected_before_copy(self):
        self.scenario["changeTaskAfterReview"] = True
        result = self.run_update()
        self.assertIn("checkout", result["error"], self.output)
        self.assertNotIn("copy-plugin", result["events"], self.output)
        self.assertEqual(b"original-installed-plugin", self.installed.read_bytes())

    def test_start_reuses_healthy_listener_without_spawning_another_companion(self):
        self.scenario.update(startOnly=True, alreadyRunning=True, releaseFails=True)
        result = self.run_update()
        self.assertIsNone(result["error"], self.output)
        self.assertEqual([], result["events"], self.output)

    def test_start_waits_for_existing_listener_after_temporary_health_failures(self):
        self.scenario.update(startOnly=True, alreadyRunning=True, releaseFails=True,
                             healthTransientFailures=2, healthFastClock=True)
        result = self.run_update()
        self.assertIsNone(result["error"], self.output)
        self.assertEqual(3, result["healthProbes"], self.output)
        self.assertEqual([], result["events"], self.output)
        self.assertIn("without starting another copy", self.output)

    def test_existing_listener_timeout_is_bounded_without_starting_or_stopping(self):
        self.scenario.update(startOnly=True, alreadyRunning=True, healthNeverResponds=True,
                             healthFastClock=True, healthProbeDelaySeconds=8)
        result = self.run_update()
        self.assertIn("within 60s", result["error"], self.output)
        self.assertEqual(60, result["elapsed"], self.output)
        self.assertEqual(6, result["healthTimeouts"][-1], self.output)
        self.assertEqual([], result["events"], self.output)

    def test_existing_listener_bad_protocol_or_body_is_rejected_without_retry(self):
        for failure in ["badHealth", "healthMalformed"]:
            with self.subTest(failure=failure):
                self.scenario.update(startOnly=True, alreadyRunning=True, healthFastClock=True,
                                     badHealth=False, healthMalformed=False)
                self.scenario[failure] = True
                result = self.run_update()
                self.assertIn("scoped health check", result["error"], self.output)
                self.assertEqual(1, result["healthProbes"], self.output)
                self.assertEqual([], result["events"], self.output)

    def test_existing_listener_authentication_failure_is_not_retried(self):
        self.scenario.update(startOnly=True, alreadyRunning=True, healthFastClock=True, healthHttpStatus=401)
        result = self.run_update()
        self.assertIn("HTTP 401", result["error"], self.output)
        self.assertEqual(1, result["healthProbes"], self.output)
        self.assertEqual([], result["events"], self.output)

    def test_existing_listener_missing_credential_is_rejected_without_a_request(self):
        self.scenario.update(startOnly=True, alreadyRunning=True, healthFastClock=True)
        self.properties.unlink()
        result = self.run_update()
        self.assertIn("Missing companion credential", result["error"], self.output)
        self.assertEqual(0, result["healthProbes"], self.output)
        self.assertEqual([], result["events"], self.output)

    def test_existing_listener_degraded_market_still_allows_start_without_guard_bypass(self):
        self.scenario.update(startOnly=True, alreadyRunning=True, healthStatus="DEGRADED")
        result = self.run_update()
        self.assertIsNone(result["error"], self.output)
        self.assertEqual(1, result["healthProbes"], self.output)
        self.assertEqual([], result["events"], self.output)

    def test_existing_listener_server_failure_retries_but_never_starts_a_second_copy(self):
        self.scenario.update(startOnly=True, alreadyRunning=True, healthFastClock=True,
                             healthHttpStatus=503, healthProbeDelaySeconds=8)
        result = self.run_update()
        self.assertIn("within 60s", result["error"], self.output)
        self.assertIn("HTTP 503", result["error"], self.output)
        self.assertGreater(result["healthProbes"], 1, self.output)
        self.assertEqual([], result["events"], self.output)

    def test_changed_or_disappeared_listener_aborts_even_if_health_succeeded(self):
        for failure in ["listenerChangesAfterProbe", "listenerDisappearsAfterProbe"]:
            with self.subTest(failure=failure):
                self.scenario.update(startOnly=True, alreadyRunning=True, healthFastClock=True,
                                     listenerChangesAfterProbe=0, listenerDisappearsAfterProbe=0)
                self.scenario[failure] = 1
                result = self.run_update()
                self.assertIn("listener changed", result["error"], self.output)
                self.assertEqual([], result["events"], self.output)

    def test_health_returning_after_deadline_cannot_claim_readiness(self):
        self.scenario.update(startOnly=True, alreadyRunning=True, healthFastClock=True,
                             healthProbeDelaySeconds=65, ignoreHealthTimeout=True)
        result = self.run_update()
        self.assertIn("within 60s", result["error"], self.output)
        self.assertNotIn("Companion already ready", self.output)
        self.assertEqual([], result["events"], self.output)

    def test_status_is_read_only_when_healthy(self):
        self.scenario.update(entry="status.ps1", alreadyRunning=True, releaseFails=True)
        result = self.run_update()
        self.assertIsNone(result["error"], self.output)
        self.assertEqual([], result["events"], self.output)
        self.assertIn("PAIR VERIFIED", self.output)

    def test_status_never_claims_readiness_for_disabled_task(self):
        self.scenario.update(entry="status.ps1", state="Disabled", alreadyRunning=True)
        result = self.run_update()
        self.assertEqual([], result["events"], self.output)
        self.assertIn("NOT READY", self.output)
        self.assertNotIn("Overall status: PAIR VERIFIED", self.output)

    def test_backup_failure_never_installs_or_reconfigures(self):
        self.scenario.update(setup=True, backupFails=True)
        result = self.run_update()
        self.assertIn("Synthetic backup copy failed", result["error"], self.output)
        self.assertNotIn("copy-plugin", result["events"], self.output)
        self.assertNotIn("register-task", result["events"], self.output)
        self.assertFalse(any(event.startswith("prompt:") for event in result["events"]))

    def test_installation_lock_blocks_another_installer_before_process_changes(self):
        with (self.manifest.parent / "installation.lock").open("w") as lock:
            if os.name != "nt":
                import fcntl
                fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
            result = self.run_update()
        self.assertIsNotNone(result["error"], self.output)
        self.assertEqual([], result["events"], self.output)
        self.assertEqual(b"original-installed-plugin", self.installed.read_bytes())

    def test_uninstall_removes_installed_pair_and_keeps_data_backups_and_login(self):
        companion_data = self.properties.parent
        legacy = companion_data / "flipping-friend.db"
        legacy.write_bytes(b"legacy-sentinel")
        backup = companion_data.parent / "backups/sentinel/old.jar"
        backup.parent.mkdir(parents=True)
        backup.write_bytes(b"backup-sentinel")
        self.scenario["entry"] = "uninstall.ps1"
        result = self.run_update()
        self.assertIsNone(result["error"], self.output)
        self.assertFalse(self.installed.exists())
        self.assertFalse(self.installed_companion.exists())
        self.assertFalse(self.manifest.exists())
        self.assertEqual(b"legacy-sentinel", legacy.read_bytes())
        self.assertEqual(b"backup-sentinel", backup.read_bytes())
        self.assertEqual(b"existing-login-sentinel", (self.fixture / "profile/.runelite/credentials.properties").read_bytes())

    def test_uninstall_never_removes_jars_when_unknown_listener_remains(self):
        self.scenario.update(entry="uninstall.ps1", opaquePort=True)
        result = self.run_update()
        self.assertIsNotNone(result["error"], self.output)
        self.assertTrue(self.installed.is_file())
        self.assertTrue(self.installed_companion.is_file())
        self.assertNotIn("remove-file", result["events"], self.output)

    def test_setup_requires_observable_registered_task_before_replacing_jars(self):
        self.scenario.update(setup=True, registrationNotVisible=True)
        result = self.run_update()
        self.assertIsNotNone(result["error"], self.output)
        self.assertNotIn("copy-plugin", result["events"], self.output)
        self.assertNotIn("Update applied.", self.output)


if __name__ == "__main__":
    unittest.main()
