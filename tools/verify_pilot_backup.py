"""Verify a supported, quiescent installation backup through a disposable restoration copy."""
from contextlib import closing, nullcontext
import argparse
import hashlib
import json
from pathlib import Path
import shutil
import sqlite3


def digest(path):
    result = hashlib.sha256()
    with path.open('rb') as source:
        for block in iter(lambda: source.read(1024 * 1024), b''):
            result.update(block)
    return result.hexdigest()


def verify(source, destination, managed_root=None):
    try:
        from storage_maintenance import Storage, plain
    except ModuleNotFoundError:
        from tools.storage_maintenance import Storage, plain
    plain(source); plain(destination)
    storage = Storage(managed_root) if managed_root is not None else None
    # Serialize admission, copying and registration against both other restores and cleanup.
    with storage.lock() if storage is not None else nullcontext():
        return _verify(source, destination, storage)


def _verify(source, destination, storage):
    source, destination = Path(source).resolve(), Path(destination).resolve()
    receipt = json.loads((source / 'backup-verification.json').read_text(encoding='utf-8-sig'))
    if receipt.get('status') != 'VERIFIED' or destination.exists() or source == destination:
        raise ValueError('An existing VERIFIED backup and a new disposable destination are required')
    for required in ('osrs-flipping-friend.jar', 'flipping-friend-companion.jar', 'installation.json'):
        if not (source / required).is_file():
            raise ValueError('Verified runtime pair is incomplete')
    files = [p for p in source.rglob('*') if p.is_file()]
    if any(p.is_symlink() or getattr(p.lstat(), 'st_file_attributes', 0) & 0x400 for p in source.rglob('*')):
        raise ValueError('Backup links are not accepted')
    # Budget uncompressed bytes even when the source uses transparent NTFS compression.
    # A failed disposable copy must not fill the runtime volume or leave a partial destination.
    volume = destination.parent
    while not volume.exists():
        if volume == volume.parent:
            raise ValueError('Disposable restoration volume is unavailable')
        volume = volume.parent
    required = sum(path.stat().st_size for path in files) + 768 * 1024 * 1024
    if shutil.disk_usage(volume).free < required:
        raise ValueError('Insufficient disposable restore space; backup and destination are unchanged')
    if storage is not None:
        storage.validate_destination(destination)
        if destination.with_name(destination.name + '.restore-verification.json').exists():
            raise ValueError('External restoration receipt already exists; no disposable copy created')
        if (source / 'restore-verification.json').exists():
            raise ValueError('Source contains reserved restoration metadata; no disposable copy created')
        storage.admit(sum(path.stat().st_size for path in files))
    hashes = {str(p.relative_to(source)): digest(p) for p in files}
    shutil.copytree(source, destination)
    for relative, expected in hashes.items():
        if digest(destination / relative) != expected or digest(source / relative) != expected:
            raise ValueError('Backup changed or restoration differs')
    checked = 0
    # Inspect only restored, explicitly scoped account stores. Legacy ownership remains unassigned.
    import re
    for database in (destination / 'accounts').glob('rs-*/flipping-friend.db'):
        if not re.fullmatch('rs-[0-9a-f]{64}', database.parent.name):
            continue
        with closing(sqlite3.connect(database.as_uri() + '?mode=ro', uri=True)) as connection:
            if connection.execute('PRAGMA integrity_check').fetchone()[0] != 'ok':
                raise ValueError('Restored account database failed integrity verification')
        checked += 1
    try:
        from storage_maintenance import derived_sqlite_cache, files_under
    except ModuleNotFoundError:
        from tools.storage_maintenance import derived_sqlite_cache, files_under
    derived = [str(p.relative_to(destination)) for p in files_under(destination)
               if derived_sqlite_cache(str(p.relative_to(destination)), hashes)]
    # SQLite read marks can change its derived SHM index even on a read-only connection.
    # Recheck every durable file after integrity validation; DB/WAL bytes are never exempt.
    for name, expected in hashes.items():
        if name not in derived and (digest(destination / name) != expected or digest(source / name) != expected):
            raise ValueError('Durable backup bytes changed during integrity verification')
    result = {'status': 'RESTORE_VERIFIED', 'source': str(source), 'restored': str(destination),
              'files': hashes, 'accountDatabasesChecked': checked, 'runtimeOnlyRollback': True}
    if derived: result['derivedSharedMemoryCaches'] = derived
    (destination / 'restore-verification.json').write_text(json.dumps(result, indent=2), encoding='utf-8')
    if storage is not None:
        # Keep proof outside the scratch copy so expiration cannot erase the restoration receipt.
        external = destination.with_name(destination.name + '.restore-verification.json')
        if external.exists():
            raise ValueError('External restoration receipt already exists; copy retained')
        external.write_text(json.dumps(result, indent=2), encoding='utf-8')
        owned = dict(hashes)
        for name in derived: owned[name] = digest(destination / name)
        owned['restore-verification.json'] = digest(destination / 'restore-verification.json')
        storage.register(destination, 'verified-restore', owned, source=source, receipt=external, derived_caches=derived)
    return result


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('source', type=Path)
    parser.add_argument('destination', type=Path)
    arguments = parser.parse_args()
    allowed=Path(__file__).resolve().parents[1]/'local-data'
    if allowed.resolve() not in arguments.destination.resolve().parents:
        parser.error('Disposable restoration must be inside this repository local-data directory')
    result = verify(arguments.source, arguments.destination, managed_root=allowed)
    print(json.dumps({'status': result['status'], 'filesVerified': len(result['files']),
                      'accountDatabasesChecked': result['accountDatabasesChecked']}))
