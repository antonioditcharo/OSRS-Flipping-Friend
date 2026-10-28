"""Change only the optional public archive flag; retain configuration ownership and permissions."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import stat
import uuid


def _pairs(pairs):
    value = {}
    for key, item in pairs:
        if key in value:
            raise ValueError('Ambiguous existing configuration')
        value[key] = item
    return value


def _metadata(path, original):
    if hasattr(os, 'chown'):
        os.chown(path, original.st_uid, original.st_gid)
    os.chmod(path, stat.S_IMODE(original.st_mode))


def _write_new(path, value, original):
    descriptor = os.open(path, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
    with os.fdopen(descriptor, 'wb') as stream:
        stream.write(value)
        stream.flush()
        _metadata(path, original)
        os.fsync(stream.fileno())


def configure(path, enabled, duration=None, budget=None, seed_items=None):
    path = Path(path).absolute()
    if any(p.is_symlink() or getattr(p.lstat(), 'st_file_attributes', 0) & 0x400 for p in (path, *path.parents)) or not path.is_file():
        raise ValueError('Existing non-linked configuration required')
    original = path.stat()
    if original.st_size > 1024**2:
        raise ValueError('Configuration exceeds budget')
    before = path.read_bytes()
    config = json.loads(before, object_pairs_hook=_pairs)
    if not isinstance(config, dict):
        raise ValueError('Existing configuration object required')
    existing = config.get('publicArchive') or {}
    if not isinstance(existing, dict):
        raise ValueError('Existing archive settings must be an object')
    duration = existing.get('durationSeconds', 954000) if duration is None else duration
    budget = existing.get('budgetBytes', 2 * 1024**3) if budget is None else budget
    seed_items = existing.get('seedItems', 32) if seed_items is None else seed_items
    if (type(enabled) is not bool or type(duration) is not int or not 1 <= duration <= 31 * 86400
            or type(budget) is not int or not 1024**2 <= budget <= 2 * 1024**3
            or type(seed_items) is not int or not 1 <= seed_items <= 32):
        raise ValueError('Bounded public archive settings required')
    proposed = {**existing, 'enabled': enabled, 'durationSeconds': duration, 'budgetBytes': budget, 'seedItems': seed_items}
    digest = hashlib.sha256(before).hexdigest()
    if config.get('publicArchive') == proposed:
        return {'changed': False, 'beforeSha256': digest, 'afterSha256': digest, 'enabled': enabled}
    config['publicArchive'] = proposed
    after = (json.dumps(config, sort_keys=True, indent=2, allow_nan=False) + '\n').encode()
    identity = uuid.uuid4().hex
    backup = path.with_name(path.name + '.archive-' + identity + '.backup')
    pending = path.with_name(path.name + '.archive-' + identity + '.pending')
    _write_new(backup, before, original)
    _write_new(pending, after, original)
    current = path.stat()
    if ((current.st_dev, current.st_ino, current.st_size, current.st_mtime_ns, current.st_ctime_ns, current.st_mode, current.st_uid, current.st_gid)
            != (original.st_dev, original.st_ino, original.st_size, original.st_mtime_ns, original.st_ctime_ns, original.st_mode, original.st_uid, original.st_gid)
            or path.is_symlink() or path.read_bytes() != before):
        raise ValueError('Configuration changed concurrently; original and private pending copies retained')
    os.replace(pending, path)
    return {'changed': True, 'beforeSha256': digest, 'afterSha256': hashlib.sha256(after).hexdigest(),
            'backup': backup.name, 'enabled': enabled}


if __name__ == '__main__':
    if os.name != 'posix':
        raise SystemExit('Run this helper on the Linux pilot through the existing encrypted connection')
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('configuration', type=Path)
    switch = parser.add_mutually_exclusive_group(required=True)
    switch.add_argument('--enable', action='store_true')
    switch.add_argument('--disable', action='store_true')
    args = parser.parse_args()
    print(json.dumps(configure(args.configuration, args.enable)))
