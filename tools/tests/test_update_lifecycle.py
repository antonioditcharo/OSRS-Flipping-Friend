"""Execute the updater with isolated files and mocked Windows/process/network boundaries."""

import json
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest


HARNESS = r"""
$ErrorActionPreference = 'Stop'
$fixture = $env:FF_UPDATE_TEST_ROOT
$scenario = Get-Content -Raw -LiteralPath (Join-Path $fixture 'scenario.json') | ConvertFrom-Json
$env:USERPROFILE = Join-Path $fixture 'profile'
$env:JAVA_HOME = Join-Path $fixture 'java'
if ($scenario.noJavaHome) { Remove-Item -LiteralPath Env:JAVA_HOME -ErrorAction SilentlyContinue }
$global:ffUpdateTestEvents = [System.Collections.Generic.List[string]]::new()
$global:ffUpdateTestStarted = $false

# Every command that can affect a real process, task or network connection is intercepted.
function Get-Command {
    [CmdletBinding()] param($Name)
    if ($Name -ne 'java') { throw 'Unexpected command lookup in test.' }
    [pscustomobject]@{ Source = (Join-Path $fixture 'java/bin/java.exe') }
}
function Get-ScheduledTask {
    [CmdletBinding()] param($TaskName, $TaskPath)
    if ($scenario.taskPresent) {
        [pscustomobject]@{
            TaskName = 'FlippingFriendCompanion'; TaskPath = '\'; State = $scenario.state
            Settings = [pscustomobject]@{ Enabled = ($scenario.state -ne 'Disabled') }
            Actions = @([pscustomobject]@{
                Execute = $scenario.executable
                Arguments = $scenario.arguments
                WorkingDirectory = $scenario.workingDirectory
            })
        }
    }
}
function Get-CimInstance {
    [CmdletBinding()] param($ClassName, $Filter)
    [pscustomobject]@{ ProcessId = 101; CommandLine = 'java -jar flipping-friend-companion.jar' }
}
function Get-NetTCPConnection {
    [CmdletBinding()] param($LocalPort, $State)
    if ($global:ffUpdateTestStarted) { [pscustomobject]@{ OwningProcess = 202 } }
}
function Stop-Process {
    [CmdletBinding()] param($Id, [switch]$Force)
    $global:ffUpdateTestEvents.Add('stop-process')
}
function Stop-ScheduledTask {
    [CmdletBinding()] param($TaskName, $TaskPath)
    $global:ffUpdateTestEvents.Add('stop-task')
}
function Start-ScheduledTask {
    [CmdletBinding()] param($TaskName, $TaskPath)
    $global:ffUpdateTestEvents.Add('start-task')
    if ($scenario.state -eq 'Disabled') { throw 'Mock scheduled task is disabled.' }
    $global:ffUpdateTestStarted = $true
}
function Start-Process {
    [CmdletBinding()] param($FilePath, $ArgumentList, $WindowStyle, $RedirectStandardOutput, $RedirectStandardError)
    $global:ffUpdateTestEvents.Add('start-process')
    $global:ffUpdateTestStarted = $true
}
function Start-Sleep { param($Seconds, $Milliseconds) }
function curl.exe { '{"status":"READY"}' }
function Copy-Item {
    [CmdletBinding()] param([Alias('LiteralPath')]$Path, $Destination, [switch]$Force)
    # Do not permit even a future updater change to copy outside this disposable fixture.
    foreach ($candidate in @($Path, $Destination)) {
        if (-not [IO.Path]::GetFullPath($candidate).StartsWith(
            $fixture + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase)) {
            throw 'Test attempted to access files outside its fixture.'
        }
    }
    $global:ffUpdateTestEvents.Add('copy-plugin')
    Microsoft.PowerShell.Management\Copy-Item -LiteralPath $Path -Destination $Destination -Force:$Force
}

$failure = $null
try { & (Join-Path $fixture 'checkout/tools/apply-update.ps1') -SkipBuild }
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
        self.fixture = Path(temporary.name)
        self.checkout = self.fixture / "checkout"
        self.plugin = self.checkout / "build/libs/osrs-flipping-friend.jar"
        self.companion = self.checkout / "companion/build/libs/flipping-friend-companion.jar"
        self.installed = self.fixture / "profile/.runelite/sideloaded-plugins/osrs-flipping-friend.jar"
        self.java = self.fixture / "java/bin/java.exe"
        self.properties = self.fixture / "profile/.runelite/osrs-flipping-friend/companion/companion.properties"
        for path, contents in (
            (self.plugin, b"new-plugin"), (self.companion, b"new-companion"),
            (self.installed, b"original-installed-plugin"), (self.java, b"not-an-executable"),
            (self.properties, b"token=synthetic-test-token\n"),
        ):
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_bytes(contents)
        tools = self.checkout / "tools"
        tools.mkdir()
        shutil.copyfile(Path(__file__).resolve().parents[1] / "apply-update.ps1", tools / "apply-update.ps1")
        (self.fixture / "harness.ps1").write_text(HARNESS, encoding="utf-8")
        self.scenario = {
            "taskPresent": True, "state": "Ready", "executable": str(self.java),
            "arguments": f'-Xmx768m -jar "{self.companion}"',
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


if __name__ == "__main__":
    unittest.main()
