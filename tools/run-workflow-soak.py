"""Explicit persistent synthetic soak. Freezes classpaths; never opens player accounts."""
import argparse
import datetime as dt
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import time

GROUPS = {
    'client': ['com.flippingfriend.' + n for n in ['EditorGuidanceDeliveryTest', 'AdviceDeliveryBoundaryTest',
        'FullBoardSaleReservationWorkflowTest', 'companion.SaleGuidanceDeliveryTest',
        'companion.OfferEventAcknowledgementResponseTest', 'companion.AcceptedBuyPlanDeliveryTest',
        'companion.EntryDecisionOrchestrationTest', 'data.PublicMarketNumbersCompatibilityTest',
        'ui.GuidanceNativeTimingTest', 'ui.GuidancePaintContinuityTest']],
    'companion': ['com.flippingfriend.companion.' + n for n in ['PromptGuidanceLoopbackTest',
        'SalePolicyClientCanonicalRecoveryTest', 'SalePolicyReceiptWorkflowTest', 'BuyReplacementWorkflowTest',
        'ApiReportIsolationTest', 'CanonicalHistoryCacheTest', 'CurrentFrameBaselineTest', 'UnchangedFrameRecoveryTest',
        'EntryStopPauseTest', 'VerifiedCashRiskTest', 'BuyMaintenanceServiceAdapterTest']],
}

def digest(path):
    h = hashlib.sha256()
    with path.open('rb') as f:
        for block in iter(lambda: f.read(65536), b''): h.update(block)
    return h.hexdigest()

def summarize_worker(exit_code, rows, stdout):
    marker = 'COMPLETED_UNINTERRUPTED ' + str(len(rows))
    if exit_code != 0 or not rows or marker not in stdout.splitlines():
        raise ValueError('Worker exited without proving uninterrupted completion')
    if any(row['failures'] or row['fixtureTransportFailures'] for row in rows):
        raise ValueError('Worker contains retained test or transport failures')
    return {'exitCode': exit_code, 'groupRuns': len(rows), 'executedChecks': sum(r['tests'] for r in rows),
        'failures': sum(r['failures'] for r in rows), 'fixtureRequests': rows[-1]['fixtureRequests'],
        'fixtureTransportFailures': rows[-1]['fixtureTransportFailures'], 'completionMarkerVerified': True}

def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--classpaths', type=Path, required=True)
    p.add_argument('--output', type=Path, required=True)
    p.add_argument('--java', type=Path, required=True)
    p.add_argument('--javac', type=Path, required=True)
    p.add_argument('--seconds', type=int, default=3600)
    a = p.parse_args()
    if a.seconds <= 0: raise ValueError('Positive duration required')
    output = a.output.resolve()
    if 'local-data' not in output.parts: raise ValueError('Private local-data output required')
    output.mkdir(parents=True, exist_ok=False)
    root = Path(__file__).resolve().parent.parent
    cp = json.loads(a.classpaths.read_text(encoding='utf-8'))
    manifest = {'head': subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=root, text=True).strip(), 'files': {}}
    paths = {}
    for group in GROUPS:
        targets = []
        for entry in cp[group].split(os.pathsep):
            source = Path(entry)
            if not source.exists(): continue
            if source.is_symlink(): raise ValueError('Linked artifact')
            target = output / 'artifacts' / hashlib.sha256(str(source.resolve()).encode()).hexdigest()[:20]
            if not target.exists():
                if source.is_dir(): shutil.copytree(source, target)
                else:
                    target.mkdir(parents=True); shutil.copy2(source, target / source.name)
                for f in target.rglob('*'):
                    if f.is_symlink(): raise ValueError('Linked classpath file')
                    if f.is_file(): manifest['files'][str(f.relative_to(output))] = digest(f)
            targets.append(str(target if source.is_dir() else target / source.name))
        paths[group] = os.pathsep.join(targets)
    worker = output / 'worker'; worker.mkdir()
    source = root / 'tools/fixtures/WorkflowSoakWorker.java'
    subprocess.run([str(a.javac), '--release', '11', '-encoding', 'UTF-8', '-cp', paths['client'],
                    '-d', str(worker), str(source)], check=True, capture_output=True)
    manifest['workerSourceSha256'] = digest(source)
    manifest['workerClassSha256'] = digest(worker / 'WorkflowSoakWorker.class')
    (output / 'artifacts.json').write_text(json.dumps(manifest, indent=2), encoding='utf-8')
    receipt = {'startedAt': dt.datetime.now(dt.timezone.utc).isoformat(), 'complete': False,
        'acceptancePassed': False, 'durationRequestedSeconds': a.seconds, 'failures': [], 'workers': {},
        'scope': 'Synthetic workflows only; native rendering, player operations and live eligible ACK latency unmeasured',
        'artifactManifestSha256': digest(output / 'artifacts.json')}
    started = time.monotonic(); processes = {}; streams = []
    flags = (subprocess.CREATE_NO_WINDOW | subprocess.BELOW_NORMAL_PRIORITY_CLASS) if os.name == 'nt' else 0
    def save():
        receipt['elapsedSeconds'] = round(time.monotonic() - started, 3)
        pending = output / 'result.pending.json'
        pending.write_text(json.dumps(receipt, indent=2), encoding='utf-8'); pending.replace(output / 'result.json')
    try:
        for group, names in GROUPS.items():
            stdout = (output / (group + '.stdout.log')).open('w', encoding='utf-8'); streams.append(stdout)
            stderr = (output / (group + '.stderr.log')).open('w', encoding='utf-8'); streams.append(stderr)
            processes[group] = subprocess.Popen([str(a.java), '-Xmx512m', '-Djava.awt.headless=true',
                '-Djdk.httpclient.redirects.retrylimit=1', '-Djdk.httpclient.disableRetryConnect=true',
                '-Djdk.httpclient.connectionPoolSize=8',
                '-cp', str(worker) + os.pathsep + paths[group], 'WorkflowSoakWorker',
                str(output / (group + '.jsonl')), str(a.seconds), *names], cwd=root, stdout=stdout,
                stderr=stderr, creationflags=flags)
        next_sample = 0
        while any(proc.poll() is None for proc in processes.values()):
            for name, proc in processes.items():
                if proc.poll() not in (None, 0):
                    receipt['failures'].append({'worker': name, 'exitCode': proc.returncode}); save()
                    raise RuntimeError('Persistent worker failed; no retry')
            if time.monotonic() >= next_sample:
                sample = {'elapsedSeconds': round(time.monotonic() - started, 3)}
                try:
                    from workflow_soak_resources import snapshot
                    sample['host'] = snapshot([v.pid for v in processes.values()])
                except Exception as e:
                    sample['measurementError'] = str(e); receipt['failures'].append({'telemetry': str(e)})
                with (output / 'resources.jsonl').open('a', encoding='utf-8') as f: f.write(json.dumps(sample) + '\n')
                next_sample = time.monotonic() + 30
                save()
            if time.monotonic() - started > a.seconds + 240: raise TimeoutError('Worker exceeded bounded phase deadline')
            time.sleep(1)
        for name, proc in processes.items():
            rows = [json.loads(line) for line in (output / (name + '.jsonl')).read_text(encoding='utf-8').splitlines()]
            receipt['workers'][name] = summarize_worker(proc.returncode, rows,
                (output / (name + '.stdout.log')).read_text(encoding='utf-8', errors='backslashreplace'))
        receipt['complete'] = not receipt['failures']
        receipt['acceptancePassed'] = receipt['complete'] and a.seconds >= 3600 and time.monotonic() - started >= 3600
    except Exception as error:
        receipt['failures'].append({'harnessError': type(error).__name__, 'message': str(error)})
        raise
    finally:
        # Only owned synthetic helpers; never a game or companion service process.
        for proc in processes.values():
            if proc.poll() is None: proc.terminate(); proc.wait(timeout=30)
        for stream in streams: stream.close()
        save()
    if not receipt['complete']: raise RuntimeError('Soak failed; preserve result and all raw telemetry')

if __name__ == '__main__': main()
