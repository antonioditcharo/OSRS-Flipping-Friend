"""Exercise the real stop-loop control flow with disposable process-provider responses."""
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest


SHELL = shutil.which("pwsh") or shutil.which("powershell")


@unittest.skipUnless(SHELL, "PowerShell required for the supported lifecycle")
class CompanionStopRaceTest(unittest.TestCase):
    def run_case(self, case):
        helper = Path(__file__).resolve().parents[1] / "process-companion.ps1"
        with tempfile.TemporaryDirectory(prefix="friend stop race ") as temporary:
            script = Path(temporary) / "stop race.ps1"
            script.write_text("$ErrorActionPreference='Stop'\n. '" + str(helper).replace("'", "''") + "'\n" + r'''
$script:ownedReads=0; $script:nativeAttempts=0; $script:normalAttempts=0; $script:drains=0
$script:original=[pscustomobject]@{ProcessId=1987654321;CreationDate=[DateTime]'2026-01-01';VerifiedCommandLine='fixture-only'}
if ($env:FF_STOP_RACE_CASE -eq 'exited-after-deadline') {
    $script:clockReads=0
    function Get-Date {
        $script:clockReads++
        if ($script:clockReads -eq 1) { return [DateTime]'2026-01-01T00:00:00' }
        return [DateTime]'2026-01-01T00:00:21'
    }
}
function Get-OwnedCompanionProcesses([string]$ExpectedSid) {
    if ($ExpectedSid -ne 'fixture-sid') { throw 'Wrong ownership scope.' }
    $script:ownedReads++
    if ($script:ownedReads -le 3) { return $script:original }
    if ($env:FF_STOP_RACE_CASE -eq 'unknown-listener') { throw 'Port belongs to an unidentified process.' }
    return @()
}
function Stop-Process {
    [CmdletBinding()] param([int]$Id,[switch]$Force)
    if ($Id -ne 1987654321) { throw 'Unexpected fixture identity.' }
    $script:normalAttempts++; throw 'Fixture normal stop refused.'
}
function Stop-NativeVerifiedCompanion($Process) {
    if ($Process -ne $script:original) { throw 'Original proof was lost.' }
    $script:nativeAttempts++; throw 'Fixture native access denied.'
}
function Get-CimInstance {
    [CmdletBinding()] param([string]$ClassName,[string]$Filter)
    if ($ClassName -ne 'Win32_Process' -or $Filter -ne 'ProcessId=1987654321') { throw 'Unbounded fixture lookup.' }
    if ($env:FF_STOP_RACE_CASE -in @('exited','exited-after-deadline','unknown-listener')) { return @() }
    # Even a reused or unidentifiable PID is present: native failure must still stop installation.
    $created=if ($env:FF_STOP_RACE_CASE -eq 'alive') {[DateTime]'2026-01-01'} else {[DateTime]'2026-01-02'}
    return [pscustomobject]@{ProcessId=1987654321;CreationDate=$created}
}
function Wait-PortFree { $script:drains++ }
$caught=$null
try { Stop-OwnedCompanionProcesses 'fixture-sid' } catch { $caught=$_.Exception.Message }
if ($script:normalAttempts -ne 1 -or $script:nativeAttempts -ne 1) { throw 'Unexpected stop attempts.' }
if ($env:FF_STOP_RACE_CASE -in @('exited','exited-after-deadline')) {
    if ($caught -or $script:drains -ne 1 -or $script:ownedReads -ne 4) { throw ('Exited identity did not drain: '+$caught) }
} else {
    $expected=if ($env:FF_STOP_RACE_CASE -eq 'unknown-listener') {'unidentified'} else {'native access denied'}
    if (-not $caught -or $caught -notmatch $expected -or $script:drains -ne 0) { throw ('Live/unknown process refusal was bypassed: '+$caught) }
}
Write-Output 'stop-race-verified'
''', encoding="utf-8")
            result = subprocess.run([SHELL, "-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass", "-File", str(script)],
                                    env=dict(os.environ, FF_STOP_RACE_CASE=case), capture_output=True,
                                    text=True, timeout=30)
            self.assertEqual(0, result.returncode, result.stdout + result.stderr)
            self.assertIn("stop-race-verified", result.stdout)

    def test_native_denial_after_verified_exit_still_requires_a_complete_drain(self):
        self.run_case("exited")

    def test_access_denial_for_a_remaining_process_refuses_installation(self):
        self.run_case("alive")

    def test_verified_exit_at_the_drain_deadline_requires_a_final_ownership_and_listener_check(self):
        self.run_case("exited-after-deadline")

    def test_reused_or_unidentifiable_present_pid_cannot_be_treated_as_exited(self):
        self.run_case("reused")

    def test_exited_target_does_not_hide_an_unidentified_listener(self):
        self.run_case("unknown-listener")
