"""Real ownership discovery must distinguish an exited enumeration from a live refusal."""
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest

SHELL = shutil.which("pwsh") or shutil.which("powershell")


@unittest.skipUnless(SHELL, "PowerShell required")
class CompanionOwnerRaceTest(unittest.TestCase):
    def run_case(self, case, missing_command=False):
        helper = Path(__file__).resolve().parents[1] / "process-companion.ps1"
        with tempfile.TemporaryDirectory(prefix="friend owner race ") as directory:
            script = Path(directory) / "owner.ps1"
            script.write_text("$ErrorActionPreference='Stop'\n. '" + str(helper).replace("'", "''") + "'\n" + r'''
$companionInstalled=Join-Path ([IO.Path]::GetTempPath()) 'friend owner fixture/flipping-friend-companion.jar'
$script:ownerQueries=0
function Normalize-AbsolutePath([string]$Path){return $Path}
function Get-NativeProcessCommandLine([int]$ProcessId){throw 'Exited process must not reach native query'}
function Stop-Process {throw 'Discovery must never stop a process'}
function Get-CompanionPid {if($env:FF_OWNER_CASE -eq 'unknown-listener'){return 4321};return 0}
function Get-CimInstance {
    [CmdletBinding()]param([string]$ClassName,[string]$Filter)
    if($ClassName -ne 'Win32_Process'){throw 'Wrong provider'}
    if($Filter){
        if($Filter -ne 'ProcessId=1234'){throw 'Unbounded follow-up lookup'}
        if($env:FF_OWNER_CASE -in @('exited','unknown-listener')){return @()}
        return [pscustomobject]@{ProcessId=1234;CreationDate=if($env:FF_OWNER_CASE -eq 'reused'){[DateTime]'2026-01-02'}else{[DateTime]'2026-01-01'}}
    }
    return [pscustomobject]@{ProcessId=1234;Name='java.exe';CreationDate=[DateTime]'2026-01-01';
        CommandLine=if($env:FF_OWNER_MISSING -eq 'yes'){$null}else{'java.exe -jar "'+$companionInstalled+'"'}}
}
function Invoke-CimMethod {
    [CmdletBinding()]param($InputObject,[string]$MethodName)
    if($InputObject.ProcessId -ne 1234 -or $MethodName -ne 'GetOwnerSid'){throw 'Wrong ownership query'}
    $script:ownerQueries++
    throw 'Fixture owner-query Not found'
}
$caught=$null;$matching=@()
try {$matching=@(Get-OwnedCompanionProcesses 'fixture-sid')}catch{$caught=$_.Exception.Message}
if($script:ownerQueries -ne 1){throw 'Fixture did not reach the real ownership query'}
if($env:FF_OWNER_CASE -eq 'exited') {
    if($caught -or $matching.Count){throw ('Absent process did not drain: '+$caught)}
} elseif($env:FF_OWNER_CASE -eq 'unknown-listener') {
    if(-not $caught -or $caught -notmatch 'unidentified'){throw 'Unknown listener refusal was bypassed'}
} elseif(-not $caught -or $caught -notmatch 'owner-query Not found') {throw 'Live/reused ownership refusal was bypassed'}
Write-Output 'owner-race-verified'
''', encoding="utf-8")
            result = subprocess.run([SHELL, "-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass", "-File", str(script)],
                env=dict(os.environ, FF_OWNER_CASE=case, FF_OWNER_MISSING="yes" if missing_command else "no"),
                capture_output=True, text=True, timeout=30)
            self.assertEqual(0, result.returncode, result.stdout + result.stderr)
            self.assertIn("owner-race-verified", result.stdout)

    def test_exited_process_requires_fresh_absence_for_both_identity_paths(self):
        for missing in (False, True):
            with self.subTest(missing_command=missing): self.run_case("exited", missing)

    def test_live_owner_failure_is_not_treated_as_exit(self):
        for missing in (False, True):
            with self.subTest(missing_command=missing): self.run_case("alive", missing)

    def test_reused_pid_retains_refusal(self):
        self.run_case("reused")

    def test_absent_process_does_not_bypass_unknown_listener(self):
        self.run_case("unknown-listener")
