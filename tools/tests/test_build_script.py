"""Exercise the real builder with a native Gradle stand-in, including PS 5.1 logging."""

import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest


class BuildScriptTest(unittest.TestCase):
    def run_builder(self, exit_code=0, jar=True, wrapper=True):
        shell = os.environ.get("FF_UPDATE_TEST_SHELL") or shutil.which("powershell") or shutil.which("pwsh")
        self.assertIsNotNone(shell, "PowerShell is required")
        with tempfile.TemporaryDirectory(prefix="flipping friend native build ") as temporary:
            root = Path(temporary).resolve()
            (root / "tools").mkdir()
            shutil.copyfile(Path(__file__).resolve().parents[1] / "build.ps1", root / "tools/build.ps1")
            if jar:
                target = root / "build/libs/osrs-flipping-friend.jar"
                target.parent.mkdir(parents=True)
                target.write_bytes(b"fixture-artifact")
            gradle = root / "gradlew.bat"
            gradle.write_text(
                (f"@echo off\r\necho Note: Some input files use or override a deprecated API. 1>&2\r\nexit /b {exit_code}\r\n"
                 if os.name == "nt" else
                 f"#!/bin/sh\necho 'Note: Some input files use or override a deprecated API.' >&2\nexit {exit_code}\n"),
                encoding="utf-8")
            gradle.chmod(0o755)
            if not wrapper:
                gradle.unlink()
            (root / "harness.ps1").write_text(
                "$ErrorActionPreference = 'Stop'\n"
                "$global:LASTEXITCODE = 0\n"
                "& (Join-Path $PSScriptRoot 'tools/build.ps1') *> (Join-Path $PSScriptRoot 'build.log')\n"
                "if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }\n"
                "if ($ErrorActionPreference -ne 'Stop') { throw 'Caller error policy changed.' }\n"
                "Write-Output 'BUILDER_RETURNED'\n", encoding="utf-8")
            result = subprocess.run([shell, "-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass",
                                     "-File", str(root / "harness.ps1")],
                                    capture_output=True, text=True, timeout=20)
            return result, (root / "build.log").read_text(encoding="utf-16" if os.name == "nt" and
                        (root / "build.log").read_bytes().startswith(b"\xff\xfe") else "utf-8-sig")

    def test_logged_native_warning_does_not_abort_successful_build(self):
        result, log = self.run_builder()
        self.assertEqual(0, result.returncode, result.stdout + result.stderr + log)
        self.assertIn("BUILDER_RETURNED", result.stdout)
        self.assertIn("deprecated API", log)
        self.assertIn("Built ", log)

    def test_native_failure_with_same_warning_remains_a_failed_build(self):
        result, log = self.run_builder(exit_code=7)
        self.assertNotEqual(0, result.returncode, result.stdout + result.stderr + log)
        self.assertIn("Build failed.", log)
        self.assertNotIn("Built ", log)

    def test_missing_artifact_after_zero_exit_remains_a_failed_build(self):
        result, log = self.run_builder(jar=False)
        self.assertNotEqual(0, result.returncode, result.stdout + result.stderr + log)
        self.assertIn("Expected jar not found", log)
        self.assertNotIn("Built ", log)

    def test_missing_native_wrapper_cannot_reuse_a_previous_zero_exit(self):
        result, log = self.run_builder(wrapper=False)
        self.assertNotEqual(0, result.returncode, result.stdout + result.stderr + log)
        self.assertNotIn("Built ", log)


if __name__ == "__main__":
    unittest.main()
