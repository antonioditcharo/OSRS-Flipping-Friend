"""Run the real GET-only checker with disposable files and a simulated legacy HTML parser."""
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest


HARNESS = r"""
$ErrorActionPreference='Stop'
$fixture=$env:FF_PIPELINE_TEST_ROOT
$env:USERPROFILE=Join-Path $fixture 'profile'
$id='rs-'+('a'*64)
function Invoke-WebRequest {
    param($Uri,$Headers,$TimeoutSec,[switch]$UseBasicParsing)
    if(-not $UseBasicParsing) { throw [NullReferenceException]::new('Legacy HTML parser unavailable.') }
    if($Headers['X-Flipping-Friend-Token'] -ne 'isolated-fixture-token') { throw 'Missing fixture authorization.' }
    if($Uri -eq 'http://127.0.0.1:37777/v1/health') {
        return [pscustomobject]@{Headers=@{'X-Flipping-Friend-Protocol'='accounts-v1'};Content='{"status":"READY","reason":"Fixture market"}'}
    }
    if($Headers['X-Flipping-Friend-Account'] -ne $id) { throw 'Missing account scope.' }
    $scope=if($env:FF_PIPELINE_BAD_SCOPE -eq '1'){'wrong-account'}else{$id}
    if($Uri -eq "http://127.0.0.1:37777/v1/accounts/$id/action") {
        return [pscustomobject]@{Headers=@{'X-Flipping-Friend-Account'=$scope};Content='{"type":"WAIT"}'}
    }
    if($Uri -eq "http://127.0.0.1:37777/v1/accounts/$id/ledger/current") {
        return [pscustomobject]@{Headers=@{'X-Flipping-Friend-Account'=$scope};Content=(@{
            accountId=$id;schemaVersion='2';tradingPositions=@();missedFlips=@();sales=4;unresolvedSales=2;uncertainSales=0
        }|ConvertTo-Json -Compress)}
    }
    throw 'Unexpected request; this fixture permits known GET endpoints only.'
}
function Invoke-RestMethod {
    param($Uri,$Headers,$TimeoutSec)
    if($Uri -ne "http://127.0.0.1:37777/v1/accounts/$id/portfolio/current" -or $Headers['X-Flipping-Friend-Account'] -ne $id) {
        throw 'Unexpected portfolio request.'
    }
    return [pscustomobject]@{status='UNAVAILABLE';reason='Waiting for real login.'}
}
& (Join-Path $fixture 'checkout/tools/pipeline-check.ps1') -SkipBuild -SkipWalkForward
Write-Output "PIPELINE_TEST_EXIT=$LASTEXITCODE"
"""


class PipelineCheckTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.shell = os.environ.get('FF_UPDATE_TEST_SHELL') or shutil.which('powershell') or shutil.which('pwsh')
        if not cls.shell:
            raise RuntimeError('PowerShell is required for pipeline tests.')

    def setUp(self):
        temporary = tempfile.TemporaryDirectory(prefix='flipping friend pipeline ')
        self.addCleanup(temporary.cleanup)
        self.fixture = Path(temporary.name)
        script = self.fixture / 'checkout/tools/pipeline-check.ps1'
        script.parent.mkdir(parents=True)
        shutil.copyfile(Path(__file__).resolve().parents[1] / 'pipeline-check.ps1', script)
        for name in ('build/libs/osrs-flipping-friend.jar', 'companion/build/libs/flipping-friend-companion.jar'):
            p = self.fixture / 'checkout' / name
            p.parent.mkdir(parents=True, exist_ok=True)
            p.write_bytes(b'artifact fixture')
        self.data = self.fixture / 'profile/.runelite/osrs-flipping-friend/companion'
        database = self.data / 'accounts' / ('rs-' + 'a' * 64) / 'flipping-friend.db'
        database.parent.mkdir(parents=True)
        database.write_bytes(b'opaque fixture; never opened by checker')
        (self.data / 'companion.properties').write_text('token=isolated-fixture-token\n', encoding='utf-8')
        self.before = {p.relative_to(self.data): p.read_bytes() for p in self.data.rglob('*') if p.is_file()}
        (self.fixture / 'harness.ps1').write_text(HARNESS, encoding='utf-8')

    def check(self, bad_scope=False):
        result = subprocess.run([self.shell, '-NoProfile', '-NonInteractive', '-ExecutionPolicy', 'Bypass',
                                 '-File', str(self.fixture / 'harness.ps1')],
                                env=dict(os.environ, FF_PIPELINE_TEST_ROOT=str(self.fixture),
                                         FF_PIPELINE_BAD_SCOPE='1' if bad_scope else '0'),
                                capture_output=True, text=True, timeout=20)
        after = {p.relative_to(self.data): p.read_bytes() for p in self.data.rglob('*') if p.is_file()}
        self.assertEqual(self.before, after, 'Read-only checker changed fixture account files')
        return result.stdout + result.stderr

    def test_json_reads_work_without_legacy_html_engine_and_preserve_account(self):
        output = self.check()
        self.assertIn('PIPELINE_TEST_EXIT=0', output)
        self.assertIn('PASS [Protocol] accounts-v1', output)
        self.assertIn('Ledger: 4 sale updates; 2 unresolved', output)
        self.assertIn('No synthetic snapshots, offers, or trades', output)

    def test_response_scope_failure_still_fails_check(self):
        output = self.check(bad_scope=True)
        self.assertIn('PIPELINE_TEST_EXIT=1', output)
        self.assertIn('Response account identity mismatch.', output)
