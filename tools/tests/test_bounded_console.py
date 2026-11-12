import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest


class BoundedConsoleTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.shell = os.environ.get('FF_UPDATE_TEST_SHELL') or shutil.which('powershell') or shutil.which('pwsh')
        if not cls.shell: raise RuntimeError('PowerShell required for bounded console tests')

    def run_script(self, body, prepare=None):
        with tempfile.TemporaryDirectory(prefix='bounded console ') as temp:
            root = Path(temp).resolve()
            if prepare: prepare(root)
            helper = Path(__file__).resolve().parents[1] / 'bounded-console.ps1'
            script = root / 'test.ps1'
            script.write_text("$ErrorActionPreference='Stop'\n. '" + str(helper).replace("'", "''") + "'\n" + body,
                              encoding='utf-8')
            run = subprocess.run([self.shell, '-NoProfile', '-NonInteractive', '-File', str(script)],
                                 cwd=root, capture_output=True, text=True, timeout=30)
            self.assertEqual(0, run.returncode, run.stdout + run.stderr)
            return run.stdout

    def test_long_utf8_writes_and_restart_remain_bounded(self):
        output = self.run_script(r'''
$directory=Join-Path $PWD 'logs'
$log=Open-BoundedConsole $directory 256 2
Write-BoundedConsole $log ('x' * 20000)
Write-BoundedConsole $log ([string][char]0x2192)
Close-BoundedConsole $log
$log=Open-BoundedConsole $directory 256 2
Write-BoundedConsole $log 'last retained message'
Close-BoundedConsole $log
$files=@(Get-ChildItem -LiteralPath $directory -File)
if($files.Count -gt 3 -or @($files | Where-Object {$_.Length -gt 256}).Count) {throw 'Unbounded logs'}
if(-not ([IO.File]::ReadAllText((Join-Path $directory 'client-console.log')).Contains('last retained message'))) {throw 'Lost tail'}
Write-Output 'BOUNDED_UTF8_RESTART_PASS'
''')
        self.assertIn('BOUNDED_UTF8_RESTART_PASS', output)

    def test_unknown_log_file_is_preserved_before_any_writer_starts(self):
        output = self.run_script(r'''
$directory=Join-Path $PWD 'logs'; New-Item -ItemType Directory -Path $directory | Out-Null
$file=Join-Path $directory 'client-console.log'; [IO.File]::WriteAllText($file,'original financial sentinel')
$refused=$false
try{$log=Open-BoundedConsole $directory 256 2;Close-BoundedConsole $log}catch{$refused=$true}
if(-not $refused -or [IO.File]::ReadAllText($file) -cne 'original financial sentinel') {throw 'Unknown file was modified'}
Write-Output 'UNKNOWN_PRESERVED'
''')
        self.assertIn('UNKNOWN_PRESERVED', output)

    def test_rotation_failure_does_not_stop_the_caller_or_grow_its_log(self):
        output = self.run_script(r'''
$directory=Join-Path $PWD 'logs';$log=Open-BoundedConsole $directory 128 2
New-Item -ItemType Directory -Path (Join-Path $directory 'client-console.log.1') | Out-Null
Write-BoundedConsole $log ('x'*5000)
Write-BoundedConsole $log ('x'*5000)
if(-not $log.Failed -or (Get-Item (Join-Path $directory 'client-console.log')).Length -gt 128) {throw 'Failure not bounded'}
Close-BoundedConsole $log
Write-Output 'CALLER_SURVIVED'
''')
        self.assertIn('CALLER_SURVIVED', output)
        self.assertEqual(1, output.count('Client console logging degraded'))

    def test_even_a_dangling_link_is_refused_before_directory_creation(self):
        def prepare(root):
            try: (root / 'linked').symlink_to(root / 'not-created', target_is_directory=True)
            except OSError: self.skipTest('Symbolic link privilege unavailable')
        output = self.run_script(r'''
$link=Join-Path $PWD 'linked';$target=Join-Path $PWD 'not-created'
$refused=$false
try { Assert-PlainDiagnosticPath $link } catch { $refused=$true }
if(-not $refused -or (Test-Path -LiteralPath $target)) {throw 'Dangling link not refused'}
Write-Output 'DANGLING_LINK_REFUSED'
''', prepare=prepare)
        self.assertIn('DANGLING_LINK_REFUSED', output)


if __name__ == '__main__': unittest.main()
