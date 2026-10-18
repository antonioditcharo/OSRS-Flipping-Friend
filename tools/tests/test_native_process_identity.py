"""Query this disposable shell and control only a child created by the native fixture."""
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest


@unittest.skipUnless(os.name == "nt", "Native Windows process query")
class NativeProcessIdentityTest(unittest.TestCase):
    def test_reads_own_exact_unicode_argument_and_rejects_invalid_identity(self):
        shell = shutil.which("powershell") or shutil.which("pwsh")
        self.assertIsNotNone(shell)
        helper = Path(__file__).resolve().parents[1] / "process-companion.ps1"
        with tempfile.TemporaryDirectory(prefix="friend \u03a9 identity ") as temporary:
            script = Path(temporary) / "native query.ps1"
            script.write_text("$ErrorActionPreference='Stop'\n. '" + str(helper).replace("'", "''") + "'\n" + r'''
$line=Get-NativeProcessCommandLine ([Diagnostics.Process]::GetCurrentProcess().Id)
if (-not $line.Contains($env:FF_NATIVE_SCRIPT_ARGUMENT)) { throw 'Exact script path missing from native command line.' }
try { $null=Get-NativeProcessCommandLine 0; throw 'Invalid identity was accepted.' }
catch { if ($_.Exception.Message -eq 'Invalid identity was accepted.') { throw } }
Write-Output 'native-identity-verified'
''', encoding="utf-8")
            result = subprocess.run([shell, "-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass", "-File", str(script)],
                                    env=dict(os.environ, FF_NATIVE_SCRIPT_ARGUMENT=str(script)),
                                    capture_output=True, text=True, timeout=20)
            self.assertEqual(0, result.returncode, result.stdout + result.stderr)
            self.assertIn("native-identity-verified", result.stdout)

    def test_native_stop_requires_same_creation_and_arguments_on_the_held_child_handle(self):
        shell = shutil.which("powershell") or shutil.which("pwsh")
        helper = Path(__file__).resolve().parents[1] / "process-companion.ps1"
        with tempfile.TemporaryDirectory(prefix="friend native stop ") as temporary:
            script = Path(temporary) / "native stop.ps1"
            (Path(temporary) / "owned child.ps1").write_text("Start-Sleep -Seconds 30\n", encoding="utf-8")
            script.write_text("$ErrorActionPreference='Stop'\n. '" + str(helper).replace("'", "''") + "'\n" + r'''
$childScript=Join-Path $PSScriptRoot 'owned child.ps1'
$child=Start-Process -FilePath (Get-Process -Id $PID).Path -ArgumentList @('-NoProfile','-NonInteractive','-File',('"'+$childScript+'"')) -WindowStyle Hidden -PassThru
try {
    $line=Get-NativeProcessCommandLine $child.Id
    $metadata=Get-CimInstance Win32_Process -Filter ("ProcessId="+$child.Id)
    $created=$metadata.CreationDate.ToUniversalTime().ToFileTimeUtc()
    foreach($bad in @(@(($created+10000000),$line),@($created,'different command'))) {
        $refused=$false
        try { [FlippingFriend.ProcessIdentityQuery]::StopVerified($child.Id,[long]$bad[0],[string]$bad[1]) }
        catch { $refused=$true }
        if (-not $refused -or $child.HasExited) { throw 'A mismatched child proof was accepted.' }
    }
    Stop-NativeVerifiedCompanion ([pscustomobject]@{ProcessId=$child.Id;CreationDate=$metadata.CreationDate;VerifiedCommandLine=$line})
    if (-not $child.WaitForExit(5000)) { throw 'Verified fixture child did not exit.' }
    Write-Output 'native-stop-verified'
} finally {
    if (-not $child.HasExited) { $child.Kill(); $null=$child.WaitForExit(5000) }
    $child.Dispose()
}
''', encoding="utf-8")
            result = subprocess.run([shell, "-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass", "-File", str(script)],
                                    capture_output=True, text=True, timeout=20)
            self.assertEqual(0, result.returncode, result.stdout + result.stderr)
            self.assertIn("native-stop-verified", result.stdout)
