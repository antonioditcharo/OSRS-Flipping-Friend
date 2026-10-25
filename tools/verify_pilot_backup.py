"""Verify a supported, quiescent installation backup through a disposable restoration copy."""
from contextlib import closing
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


def verify(source, destination):
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
    result = {'status': 'RESTORE_VERIFIED', 'source': str(source), 'restored': str(destination),
              'files': hashes, 'accountDatabasesChecked': checked, 'runtimeOnlyRollback': True}
    (destination / 'restore-verification.json').write_text(json.dumps(result, indent=2))
    return result


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('source', type=Path)
    parser.add_argument('destination', type=Path)
    arguments = parser.parse_args()
    allowed=Path(__file__).resolve().parents[1]/'local-data'
    if allowed.resolve() not in arguments.destination.resolve().parents:
        parser.error('Disposable restoration must be inside this repository local-data directory')
    result = verify(arguments.source, arguments.destination)
    print(json.dumps({'status': result['status'], 'filesVerified': len(result['files']),
                      'accountDatabasesChecked': result['accountDatabasesChecked']}))
