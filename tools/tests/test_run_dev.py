"""Exercise the real developer launcher with disposable jars and no game/network execution."""

import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest
import zipfile


HARNESS = r"""
$ErrorActionPreference = 'Stop'
$fixture = [IO.Path]::GetFullPath($env:FF_LAUNCH_TEST_ROOT)
$scenario = Get-Content -LiteralPath (Join-Path $fixture 'scenario.json') -Raw | ConvertFrom-Json
$env:USERPROFILE = Join-Path $fixture 'profile'
$env:LOCALAPPDATA = Join-Path $fixture 'localappdata'
$env:PROCESSOR_ARCHITECTURE = 'AMD64'
$env:PROCESSOR_ARCHITEW6432 = ''
$global:ffLaunchArguments = @()
$global:ffLaunchRequests = @()
function java {
    $global:ffLaunchArguments = @($args | ForEach-Object { [string]$_ })
    $global:LASTEXITCODE = 0
}
function Invoke-WebRequest {
    param($Uri, $OutFile, [switch]$UseBasicParsing, $TimeoutSec)
    $global:ffLaunchRequests += [string]$Uri
    if ($Uri -eq 'https://static.runelite.net/bootstrap.json' -and -not $OutFile) {
        if ($scenario.bootstrapFails) { throw 'Synthetic bootstrap connection failure.' }
        return [pscustomobject]@{ Content = (Get-Content -LiteralPath (Join-Path $fixture 'bootstrap.json') -Raw) }
    }
    throw 'Unexpected download in isolated launcher test.'
}
$failure = $null
try { & (Join-Path $fixture 'checkout/tools/run-dev.ps1') }
catch { $failure = $_.Exception.Message }
Write-Output ('LAUNCH_TEST_RESULT=' + (@{
    error = $failure; arguments = @($global:ffLaunchArguments); requests = @($global:ffLaunchRequests)
} | ConvertTo-Json -Compress -Depth 5))
"""


class RunDevTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.shell = os.environ.get("FF_UPDATE_TEST_SHELL") or shutil.which("powershell") or shutil.which("pwsh")
        if not cls.shell:
            raise RuntimeError("PowerShell is required for the launcher tests.")

    def setUp(self):
        temporary = tempfile.TemporaryDirectory(prefix="flipping friend launch ")
        self.addCleanup(temporary.cleanup)
        self.fixture = Path(temporary.name).resolve()
        self.checkout = self.fixture / "checkout"
        tools = self.checkout / "tools"
        tools.mkdir(parents=True)
        shutil.copyfile(Path(__file__).resolve().parents[1] / "run-dev.ps1", tools / "run-dev.ps1")
        self.repository = self.fixture / "profile/.runelite/repository2"
        self.repository.mkdir(parents=True)
        self.bootstrap = {"version": "1.13.1", "artifacts": []}
        for name in (
            "client-1.13.1.jar", "injected-client-1.13.1.jar", "runelite-api-1.13.1-runtime.jar",
            "support-2.jar",
        ):
            self.add_artifact(name)
        self.add_artifact("native-windows.jar", platform=[{"name": "win", "arch": "amd64"}])
        self.add_artifact("native-linux.jar", platform=[{"name": "linux", "arch": "amd64"}])
        for name in ("client-1.12.39.jar", "runelite-api-1.12.39-runtime.jar", "support-1.jar"):
            (self.repository / name).write_bytes(b"stale-cached-jar")
        (self.checkout / "build.gradle").write_text("ext.runeLiteVersion = '1.13.1'\n", encoding="utf-8")
        self.plugin = self.fixture / "profile/.runelite/sideloaded-plugins/osrs-flipping-friend.jar"
        self.write_plugin("1.13.1")
        extras = self.checkout / "build/runtime-extras"
        extras.mkdir(parents=True)
        (extras / "runelite-api-1.13.1.jar").write_bytes(b"matching-full-api-fixture")
        credentials = self.fixture / "profile/.runelite/credentials.properties"
        credentials.write_bytes(b"login-sentinel-never-read")
        (self.fixture / "harness.ps1").write_text(HARNESS, encoding="utf-8")
        self.scenario = {}

    def add_artifact(self, name, platform=None):
        contents = ("current-" + name).encode()
        (self.repository / name).write_bytes(contents)
        artifact = {"name": name, "hash": hashlib.sha256(contents).hexdigest(), "size": len(contents)}
        if platform:
            artifact["platform"] = platform
        self.bootstrap["artifacts"].append(artifact)

    def write_plugin(self, api_version):
        self.plugin.parent.mkdir(parents=True, exist_ok=True)
        text = "Manifest-Version: 1.0\r\n"
        if api_version:
            text += f"RuneLite-Api-Version: {api_version}\r\n"
        with zipfile.ZipFile(self.plugin, "w") as jar:
            jar.writestr("META-INF/MANIFEST.MF", text + "\r\n")

    def run_launcher(self):
        (self.fixture / "bootstrap.json").write_text(json.dumps(self.bootstrap), encoding="utf-8")
        (self.fixture / "scenario.json").write_text(json.dumps(self.scenario), encoding="utf-8")
        result = subprocess.run(
            [self.shell, "-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass",
             "-File", str(self.fixture / "harness.ps1")],
            env=dict(os.environ, FF_LAUNCH_TEST_ROOT=str(self.fixture)),
            capture_output=True, text=True, timeout=20,
        )
        self.assertEqual(0, result.returncode, result.stdout + result.stderr)
        self.output = result.stdout + result.stderr
        marker = "LAUNCH_TEST_RESULT="
        matches = [line[len(marker):] for line in result.stdout.splitlines() if line.startswith(marker)]
        self.assertEqual(1, len(matches), self.output)
        return json.loads(matches[0])

    def assert_no_launch(self, message):
        result = self.run_launcher()
        self.assertIsNotNone(result["error"], self.output)
        self.assertIn(message, result["error"], self.output)
        self.assertEqual([], result["arguments"], self.output)
        return result

    def test_classpath_uses_only_current_platform_artifacts_and_matching_full_api(self):
        before = {path.name: path.read_bytes() for path in self.repository.iterdir()}
        result = self.run_launcher()
        self.assertIsNone(result["error"], self.output)
        arguments = result["arguments"]
        classpath = arguments[arguments.index("-cp") + 1].split(";")
        expected = {str(self.repository / artifact["name"]) for artifact in self.bootstrap["artifacts"]
                    if artifact["name"] != "native-linux.jar"}
        expected.add(str(self.checkout / "build/runtime-extras/runelite-api-1.13.1.jar"))
        self.assertEqual(expected, set(classpath), self.output)
        self.assertEqual(len(expected), len(classpath))
        self.assertIn("--developer-mode", arguments)
        self.assertIn("-ea", arguments)
        self.assertEqual(before, {path.name: path.read_bytes() for path in self.repository.iterdir()})
        self.assertEqual(b"login-sentinel-never-read",
                         (self.fixture / "profile/.runelite/credentials.properties").read_bytes())

    def test_source_api_mismatch_stops_instead_of_warn_and_launch(self):
        (self.checkout / "build.gradle").write_text("ext.runeLiteVersion = '1.12.39'\n", encoding="utf-8")
        self.assert_no_launch("source targets")

    def test_installed_plugin_api_mismatch_stops_before_launch(self):
        self.write_plugin("1.12.39")
        self.assert_no_launch("installed plugin targets")

    def test_missing_installed_api_metadata_requires_paired_update(self):
        self.write_plugin(None)
        self.assert_no_launch("API version metadata")

    def test_changed_artifact_hash_stops_without_modifying_cache(self):
        path = self.repository / "runelite-api-1.13.1-runtime.jar"
        path.write_bytes(b"changed-jar")
        self.assert_no_launch("does not match")
        self.assertEqual(b"changed-jar", path.read_bytes())

    def test_missing_current_api_does_not_fall_back_to_old_cached_api(self):
        (self.repository / "runelite-api-1.13.1-runtime.jar").unlink()
        self.assert_no_launch("missing")

    def test_bootstrap_failure_never_launches_from_unfiltered_cache(self):
        self.scenario["bootstrapFails"] = True
        self.assert_no_launch("bootstrap")

    def test_manifest_cannot_select_another_file_outside_repository(self):
        self.bootstrap["artifacts"].append({"name": "../credentials.properties", "hash": "a" * 64, "size": 1})
        self.assert_no_launch("artifact name")


if __name__ == "__main__":
    unittest.main()
