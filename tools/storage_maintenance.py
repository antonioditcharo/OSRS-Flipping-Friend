"""Bounded, registered development storage. Never prunes player state or updater backups."""
import argparse
from contextlib import contextmanager
import ctypes
import functools
import hashlib
import json
import os
import re
from pathlib import Path
import shutil
import stat
import subprocess
import sys
import time

GIB = 1024 ** 3
FREE_RESERVE = 10 * GIB
WORKSPACE_BUDGET = 50 * GIB
SCRATCH_BUDGET = 5 * GIB
RESTORE_LIFETIME = 24 * 3600
RUN_BUDGET = GIB
COMPRESS_BUDGET = 2 * GIB
EXTENSIONS = {'.db', '.sqlite', '.json', '.jsonl', '.log', '.csv', '.xml', '.jfr'}


def plain(path):
    """Reject symlinks/junctions in every existing ancestor, without following them."""
    path = Path(os.path.abspath(path))
    for entry in (path, *path.parents):
        try:
            info = entry.lstat()
        except FileNotFoundError:
            continue
        if stat.S_ISLNK(info.st_mode) or getattr(info, 'st_file_attributes', 0) & 0x400:
            raise ValueError('Linked storage path refused')
    return path


def inside(path, root):
    path, root = plain(path), plain(root)
    path.relative_to(root)
    if path == root:
        raise ValueError('The storage root cannot itself be an artifact')
    return path


def files_under(root):
    root = plain(root)
    result = []
    for folder, directories, files in os.walk(root, followlinks=False):
        plain(folder)
        for name in directories + files:
            entry = Path(folder) / name
            info = entry.lstat()
            if stat.S_ISLNK(info.st_mode) or getattr(info, 'st_file_attributes', 0) & 0x400:
                raise ValueError('Linked storage path refused')
            if stat.S_ISREG(info.st_mode):
                result.append(entry)
    return result


def digest(path):
    path = plain(path)
    before = path.stat()
    value = hashlib.sha256()
    with path.open('rb') as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b''):
            value.update(block)
    after = path.stat()
    if (before.st_size, before.st_mtime_ns, before.st_ino) != (after.st_size, after.st_mtime_ns, after.st_ino):
        raise ValueError('Storage artifact changed during verification')
    return value.hexdigest()


def directories_under(root):
    return sorted(str(inside(Path(folder) / name, root).relative_to(root))
                  for folder, names, _ in os.walk(root, followlinks=False) for name in names)


def derived_sqlite_cache(name, hashes):
    parts = Path(name).parts
    return (len(parts) == 3 and parts[0] == 'accounts' and
            re.fullmatch(r'rs-[0-9a-f]{64}', parts[1]) is not None and
            parts[2] == 'flipping-friend.db-shm' and
            str(Path(name).with_name('flipping-friend.db')) in hashes)


@functools.lru_cache(maxsize=1)
def allocation_api():
    api = ctypes.WinDLL('kernel32', use_last_error=True).GetCompressedFileSizeW
    api.argtypes = [ctypes.c_wchar_p, ctypes.POINTER(ctypes.c_ulong)]
    api.restype = ctypes.c_ulong
    return api


def allocated(path):
    if os.name != 'nt':
        info = path.stat()
        return getattr(info, 'st_blocks', 0) * 512 or info.st_size
    api = allocation_api()
    high = ctypes.c_ulong()
    ctypes.set_last_error(0)
    low = api(str(path), ctypes.byref(high))
    if low == 0xffffffff and ctypes.get_last_error():
        raise ctypes.WinError(ctypes.get_last_error())
    return high.value << 32 | low


def atomic_json(path, value):
    path = plain(path)
    pending = plain(path.with_name(path.name + '.pending'))
    pending.write_text(json.dumps(value, indent=2), encoding='utf-8')
    pending.replace(path)


class Storage:
    def __init__(self, local_data):
        self.root = plain(local_data)
        if self.root.name != 'local-data':
            raise ValueError('Explicit private local-data root required')
        self.control = plain(self.root / '.storage')
        self.registry = plain(self.control / 'registrations')
        self.registry.mkdir(parents=True, exist_ok=True)

    @contextmanager
    def lock(self, name='maintenance.lock'):
        path = plain(self.control / name)
        with path.open('a+b') as stream:
            stream.seek(0)
            if stream.read(1) == b'':
                stream.write(b'0'); stream.flush()
            stream.seek(0)
            if os.name == 'nt':
                import msvcrt
                msvcrt.locking(stream.fileno(), msvcrt.LK_NBLCK, 1)
            else:
                import fcntl
                fcntl.flock(stream.fileno(), fcntl.LOCK_EX | fcntl.LOCK_NB)
            try:
                yield
            finally:
                stream.seek(0)
                if os.name == 'nt':
                    msvcrt.locking(stream.fileno(), msvcrt.LK_UNLCK, 1)
                else:
                    fcntl.flock(stream.fileno(), fcntl.LOCK_UN)

    def records(self):
        records = []
        for path in sorted(self.registry.glob('*.json')):
            plain(path)
            record = json.loads(path.read_text(encoding='utf-8'))
            if record.get('schema') != 1 or record.get('kind') not in ('verified-restore', 'sealed-evidence'):
                raise ValueError('Unknown storage registration; retained')
            artifact = inside(self.root / record['path'], self.root)
            if artifact == self.control or self.control in artifact.parents:
                raise ValueError('Control directory cannot be a managed artifact')
            if (not isinstance(record.get('sealedAt'), (int, float)) or
                    not isinstance(record.get('files'), dict) or
                    any(not isinstance(v, str) or len(v) != 64 or any(c not in '0123456789abcdef' for c in v)
                        for v in record['files'].values())):
                raise ValueError('Invalid sealed artifact metadata; retained')
            for name in record['files']:
                inside(artifact / name, artifact)
            for _, _, other in records:
                if artifact == other or artifact in other.parents or other in artifact.parents:
                    raise ValueError('Overlapping artifact registrations; retained')
            if record['kind'] == 'verified-restore':
                if (record.get('expiresAt') != record['sealedAt'] + RESTORE_LIFETIME or
                        not isinstance(record.get('source'), str) or not isinstance(record.get('receipt'), str)):
                    raise ValueError('Invalid disposable lifetime; retained')
                if (not isinstance(record.get('directories'), list) or
                        any(not derived_sqlite_cache(n, record['files']) for n in record.get('derivedCaches', []))):
                    raise ValueError('Invalid disposable directory/cache manifest; retained')
                for name in record['directories']: inside(artifact / name, artifact)
            records.append((path, record, artifact))
        return records

    def report(self):
        files = files_under(self.root)
        stored = sum(allocated(path) for path in files)
        scratch = sum(sum(allocated(p) for p in files_under(artifact))
                      for _, rec, artifact in self.records()
                      if rec['kind'] == 'verified-restore' and artifact.exists())
        free = shutil.disk_usage(self.root).free
        warnings = []
        if stored > WORKSPACE_BUDGET: warnings.append('PROTECTED_STORAGE_OVER_BUDGET')
        if scratch > SCRATCH_BUDGET: warnings.append('VERIFICATION_SCRATCH_OVER_BUDGET')
        if free < FREE_RESERVE: warnings.append('LOW_DISK_SPACE')
        return {'schema': 1, 'checkedAt': time.time(), 'files': len(files), 'storedBytes': stored,
                'logicalBytes': sum(path.stat().st_size for path in files), 'scratchBytes': scratch,
                'freeBytes': free, 'workspaceBudgetBytes': WORKSPACE_BUDGET,
                'scratchBudgetBytes': SCRATCH_BUDGET, 'freeReserveBytes': FREE_RESERVE,
                'warnings': warnings, 'protectedDataDeleted': False}

    def admit(self, requested_bytes, scratch=True):
        if requested_bytes < 0:
            raise ValueError('Nonnegative copy budget required')
        report = self.report()
        if (report['warnings'] or report['freeBytes'] < requested_bytes + FREE_RESERVE or
                report['storedBytes'] + requested_bytes > WORKSPACE_BUDGET or
                (scratch and report['scratchBytes'] + requested_bytes > SCRATCH_BUDGET)):
            raise ValueError('Optional capture/restore refused by storage budget; retained evidence and player state unchanged')

    def register(self, artifact, kind, hashes, source=None, receipt=None, now=None, derived_caches=()):
        artifact = self.validate_destination(artifact)

        stamp = time.time() if now is None else now
        if kind not in ('verified-restore', 'sealed-evidence'):
            raise ValueError('Unsupported artifact lifetime')
        record = {'schema': 1, 'kind': kind, 'path': str(artifact.relative_to(self.root)),
                  'sealedAt': stamp, 'files': hashes, 'directories': directories_under(artifact)}
        if kind == 'verified-restore':
            source = plain(source)
            proof = json.loads(plain(source / 'backup-verification.json').read_text(encoding='utf-8-sig'))
            if proof.get('status') != 'VERIFIED' or source == artifact or source in artifact.parents or artifact in source.parents:
                raise ValueError('An independent verified backup is required')
            receipt = inside(receipt, self.root)
            if receipt == artifact or artifact in receipt.parents:
                raise ValueError('Restoration receipt must survive outside the disposable copy')
            record.update(source=str(source), receipt=str(receipt.relative_to(self.root)),
                          receiptSha256=digest(receipt), expiresAt=stamp + RESTORE_LIFETIME,
                          derivedCaches=sorted(derived_caches))
            if any(not derived_sqlite_cache(n, hashes) for n in derived_caches):
                raise ValueError('Only an explicit account SQLite shared-memory index is derived cache')
            for name, sha in hashes.items():
                if name != 'restore-verification.json' and name not in derived_caches and digest(inside(source / name, source)) != sha:
                    raise ValueError('Disposable artifact is not an exact copy of the verified backup')
        if set(hashes) != {str(p.relative_to(artifact)) for p in files_under(artifact)}:
            raise ValueError('Registration must describe the complete sealed artifact')
        if any(digest(inside(artifact / name, artifact)) != sha for name, sha in hashes.items()):
            raise ValueError('Sealed artifact changed')
        identity = hashlib.sha256(os.path.normcase(record['path']).encode('utf-8')).hexdigest()
        target = plain(self.registry / (identity + '.json'))
        if target.exists():
            raise ValueError('Artifact is already registered; its lifetime cannot be reset')
        atomic_json(target, record)

    def validate_destination(self, artifact):
        artifact = inside(artifact, self.root)
        if self.control == artifact or self.control in artifact.parents:
            raise ValueError('Storage control files cannot be registered')
        for _, _, other in self.records():
            if artifact == other or artifact in other.parents or other in artifact.parents:
                raise ValueError('Overlapping artifact registration refused')
        return artifact

    def expire_restore(self, registration, record, artifact, now):
        if now < record['sealedAt'] or now < record['expiresAt']:
            return 0
        source = plain(record['source'])
        if source == artifact or source in artifact.parents or artifact in source.parents:
            raise ValueError('Source backup overlaps disposable artifact; retained')
        receipt = inside(self.root / record['receipt'], self.root)
        if receipt == artifact or artifact in receipt.parents:
            raise ValueError('Restoration receipt must remain outside the disposable copy')
        if digest(receipt) != record['receiptSha256']:
            raise ValueError('Preserved restoration receipt changed; disposable copy retained')
        proof = json.loads(plain(source / 'backup-verification.json').read_text(encoding='utf-8-sig'))
        if proof.get('status') != 'VERIFIED':
            raise ValueError('Source backup is no longer verified; disposable copy retained')
        expected = record['files']
        caches = set(record.get('derivedCaches', []))
        paths = files_under(artifact)
        present = {str(p.relative_to(artifact)) for p in paths}
        if (not present.issubset(expected) or
                (not record.get('cleanupStarted') and not (set(expected) - present).issubset(caches))):
            raise ValueError('Disposable copy has new/missing files; retained')
        present_dirs = set(directories_under(artifact))
        if (not present_dirs.issubset(record['directories']) or
                (not record.get('cleanupStarted') and present_dirs != set(record['directories']))):
            raise ValueError('Disposable copy has new/missing directories; retained')
        for name, sha in expected.items():
            target = inside(artifact / name, artifact)
            if name in present and name not in caches and digest(target) != sha:
                raise ValueError('Disposable copy changed; retained')
            if name != 'restore-verification.json' and name not in caches and digest(inside(source / name, source)) != sha:
                raise ValueError('Source backup differs; disposable copy retained')
        removed = sum(allocated(p) for p in paths)
        record['cleanupStarted'] = True
        atomic_json(registration, record)
        # Only exact, previously verified registered files; no recursive deletion or name heuristic.
        for path in paths:
            # Revalidate the named file at the destructive boundary, including after interrupted cleanup.
            name = str(path.relative_to(artifact))
            if name not in caches and digest(path) != expected[name]:
                raise ValueError('Disposable artifact changed during cleanup; remaining files retained')
            inside(path, artifact).unlink()
        directories = sorted((p for p in artifact.rglob('*') if p.is_dir()), key=lambda p: len(p.parts), reverse=True)
        for path in directories:
            inside(path, artifact).rmdir()
        if artifact.exists(): inside(artifact, self.root).rmdir()
        plain(registration).unlink()
        return removed

    def maintain(self, now=None):
        now = time.time() if now is None else now
        started = time.monotonic()
        with self.lock():
            result = {'schema': 1, 'checkedAt': now, 'reclaimedBytes': 0, 'compressedBytesRead': 0,
                      'compressedFiles': [], 'refusals': [], 'protectedDataDeleted': False}
            for path, record, artifact in self.records():
                try:
                    if record['kind'] == 'verified-restore':
                        result['reclaimedBytes'] += self.expire_restore(path, record, artifact, now)
                    elif now >= record['sealedAt'] + RESTORE_LIFETIME and now >= record['sealedAt']:
                        for name, expected in record['files'].items():
                            file = inside(artifact / name, artifact)
                            size = file.stat().st_size
                            if (os.name != 'nt' or file.suffix.lower() not in EXTENSIONS or size < 1024 * 1024 or
                                    result['compressedBytesRead'] + size > COMPRESS_BUDGET or
                                    len(result['compressedFiles']) >= 8 or time.monotonic() - started > 60 or
                                    file.stat().st_file_attributes & 0x800):
                                continue
                            if digest(file) != expected: raise ValueError('Sealed evidence changed; compression refused')
                            before = allocated(file)
                            process = subprocess.run(['compact.exe', '/C', '/I', '/Q', str(file)],
                                                     capture_output=True, timeout=180)
                            if process.returncode or digest(file) != expected:
                                raise ValueError('Compression verification failed; evidence retained for investigation')
                            result['compressedBytesRead'] += size
                            result['reclaimedBytes'] += before - allocated(file)
                            result['compressedFiles'].append({'path': str(file.relative_to(self.root)), 'sha256': expected})
                except (OSError, ValueError, KeyError, subprocess.TimeoutExpired) as error:
                    result['refusals'].append({'artifact': record['path'], 'reason': str(error)})
            result['report'] = self.report()
            atomic_json(self.control / 'status.json', result)
            return result


def check_run_budget(output):
    """Stop an optional writer before its raw evidence exceeds a cap; never discard failed evidence."""
    files = files_under(output)
    if sum(p.stat().st_size for p in files) > RUN_BUDGET or shutil.disk_usage(output).free < FREE_RESERVE:
        raise ValueError('Optional diagnostic capture exceeded its storage allowance; retain the failed capture')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--workspace', type=Path, required=True)
    actions = parser.add_mutually_exclusive_group()
    actions.add_argument('--apply', action='store_true')
    actions.add_argument('--watch', action='store_true')
    actions.add_argument('--seal', type=Path, help='Explicitly freeze completed evidence; never permits deletion')
    args = parser.parse_args()
    workspace = plain(args.workspace)
    if not (workspace / 'AGENTS.md').is_file() or not (workspace / 'build.gradle').is_file():
        parser.error('Existing project checkout required')
    storage = Storage(workspace / 'local-data')
    if args.seal:
        artifact = inside(args.seal, storage.root)
        if not artifact.is_dir(): parser.error('Existing evidence directory required')
        with storage.lock():
            check_run_budget(artifact)
            storage.register(artifact, 'sealed-evidence', {str(p.relative_to(artifact)): digest(p) for p in files_under(artifact)})
        print(json.dumps({'status': 'SEALED_FOR_LOSSLESS_COMPRESSION_ONLY', 'path': str(artifact)}))
        return
    if not args.watch:
        print(json.dumps(storage.maintain() if args.apply else storage.report()))
        return
    # Optional development worker, single per checkout; it never starts/stops any client or service.
    try:
        watcher = storage.lock('watcher.lock')
        watcher.__enter__()
    except OSError:
        return  # An existing checkout worker owns maintenance; no duplicate helper.
    try:
        while True:
            # Load the current checked-out tool each pass; updates need no stale-worker termination.
            result = subprocess.run([sys.executable, str(Path(__file__).resolve()), '--workspace',
                                     str(workspace), '--apply'], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL,
                                    creationflags=(subprocess.CREATE_NO_WINDOW | subprocess.BELOW_NORMAL_PRIORITY_CLASS)
                                    if os.name == 'nt' else 0)
            if result.returncode:
                atomic_json(storage.control / 'status.json', {'checkedAt': time.time(),
                            'error': 'Maintenance refused; inspect registrations and run the storage report',
                            'protectedDataDeleted': False})
            # Exit on checkout removal. No service/game process is inspected or stopped.
            for _ in range(360):
                time.sleep(10)
                if not (workspace / 'AGENTS.md').exists(): return
    finally:
        watcher.__exit__(None, None, None)


if __name__ == '__main__':
    main()
