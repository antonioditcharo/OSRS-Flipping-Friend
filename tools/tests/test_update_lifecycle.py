"""Execute the updater with isolated files and mocked Windows/process/network boundaries."""

import json
import hashlib
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest


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
    [pscustomobject]@{ ProcessId = 101; Name = 'java.exe'; CommandLine = 'java -jar flipping-friend-companion.jar' }
}
function Get-NetTCPConnection {
    [CmdletBinding()] param($LocalPort, $State)
    if ($scenario.opaquePort) { [pscustomobject]@{ OwningProcess = 999 } }
    elseif ($global:ffUpdateTestStarted -or $scenario.alreadyRunning) { [pscustomobject]@{ OwningProcess = 202 } }
}
function Stop-Process {
    [CmdletBinding()] param($Id, [switch]$Force)
    $global:ffUpdateTestEvents.Add('stop-process')
    $scenario.alreadyRunning = $false
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
    $global:ffUpdateTestStarted = $true
}
function Start-Sleep { param($Seconds, $Milliseconds) }
function Get-Date {
    if ($scenario.fastTime) { $global:ffUpdateTestClock++; return [DateTime]::UtcNow.AddSeconds(10 * $global:ffUpdateTestClock) }
    return [DateTime]::UtcNow
}
function Export-ScheduledTask {
    [CmdletBinding()] param($TaskName, $TaskPath)
    $global:ffUpdateTestEvents.Add('export-task')
    return ($global:ffUpdateTestTask | ConvertTo-Json -Depth 5)
}
function Disable-ScheduledTask {
    [CmdletBinding()] param($TaskName, $TaskPath)
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
    $protocol = if ($scenario.badHealth) { 'legacy-v0' } else { 'accounts-v1' }
    [pscustomobject]@{ Headers=@{ 'X-Flipping-Friend-Protocol'=$protocol }; Content='{"status":"READY"}' }
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
}

$failure = $null
try {
    $entry = if ($scenario.entry) { $scenario.entry } else { 'apply-update.ps1' }
    $options = @{ }
    if ($entry -eq 'apply-update.ps1') {
        $options.SkipBuild = (-not $scenario.buildRequested)
        if ($scenario.setup) { $options.ConfigureCompanionTask = $true }
        if ($scenario.startOnly) { $options.StartOnly = $true }
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
        shutil.copyfile(Path(__file__).resolve().parents[1] / "apply-update.ps1", tools / "apply-update.ps1")
        shutil.copyfile(Path(__file__).resolve().parents[1] / "start.ps1", tools / "start.ps1")
        shutil.copyfile(Path(__file__).resolve().parents[1] / "setup.ps1", tools / "setup.ps1")
        shutil.copyfile(Path(__file__).resolve().parents[1] / "status.ps1", tools / "status.ps1")
        for name in ("uninstall.ps1", "uninstall-companion.ps1"):
            shutil.copyfile(Path(__file__).resolve().parents[1] / name, tools / name)
        shutil.copyfile(Path(__file__).resolve().parents[1] / "register-companion-task.ps1", tools / "register-companion-task.ps1")
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
        self.scenario.update(startOnly=True, alreadyRunning=True)
        result = self.run_update()
        self.assertIsNone(result["error"], self.output)
        self.assertEqual([], result["events"], self.output)

    def test_status_is_read_only_when_healthy(self):
        self.scenario.update(entry="status.ps1", alreadyRunning=True)
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
