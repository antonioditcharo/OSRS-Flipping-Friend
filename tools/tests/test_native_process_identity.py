"""Read only the disposable test shell's own native command line; never stop a real process."""
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
if (-not $line.Contains($PSCommandPath)) { throw 'Exact script path missing from native command line.' }
try { $null=Get-NativeProcessCommandLine 0; throw 'Invalid identity was accepted.' }
catch { if ($_.Exception.Message -eq 'Invalid identity was accepted.') { throw } }
Write-Output 'native-identity-verified'
''', encoding="utf-8")
            result = subprocess.run([shell, "-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass", "-File", str(script)],
                                    capture_output=True, text=True, timeout=20)
            self.assertEqual(0, result.returncode, result.stdout + result.stderr)
            self.assertIn("native-identity-verified", result.stdout)
