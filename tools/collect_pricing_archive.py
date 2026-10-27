"""Passive local public-feed capture into bounded immutable segments; never touches accounts."""
import argparse
import datetime
import json
import os
from pathlib import Path
import shutil
import time

from collect_pricing_market import fetch
from pricing_archive import Blobs, MAX_BYTES, Segment, bounded, unpack


def background_priority():
    if os.name == 'nt':
        import ctypes
        kernel = ctypes.WinDLL('kernel32', use_last_error=True)
        kernel.GetCurrentProcess.restype = ctypes.c_void_p
        kernel.SetPriorityClass.argtypes = (ctypes.c_void_p, ctypes.c_uint32)
        kernel.SetPriorityClass.restype = ctypes.c_int
        if not kernel.SetPriorityClass(kernel.GetCurrentProcess(), 0x4000):
            raise OSError('Cannot set public capture background priority')
    elif hasattr(os, 'nice'):
        os.nice(10)


def collect(output, seconds, interval=60, user_root=None, budget=2 * 1024**3):
    output = Path(output).absolute()
    if ('local-data' not in output.parts or output.exists() or not 1 <= seconds <= 31 * 86400
            or not 60 <= interval <= 900 or not 1024 * 1024 <= budget <= MAX_BYTES):
        raise ValueError('New private segment and bounded duration, cadence and budget required')
    # Windows short names can resolve to a different spelling without being links. Inspect
    # the actual ancestors for reparse points before canonicalizing, rather than compare text.
    if not output.parent.is_dir() or any(parent.is_symlink() or getattr(parent.lstat(), 'st_file_attributes', 0) & 0x400
            for parent in (output.parent, *output.parent.parents)):
        raise ValueError('Capture parent must exist without links')
    output = output.resolve()
    if shutil.disk_usage(output.parent).free < budget + 256 * 1024**2:
        raise ValueError('Insufficient bounded capture space')
    user_root = Path(user_root) if user_root else Path.home() / '.runelite/osrs-flipping-friend'
    config = bounded(user_root / 'companion/companion.properties', 65536).decode('utf-8-sig')
    token = next(line[6:].strip() for line in config.splitlines() if line.startswith('token='))
    if not token or '\r' in token or '\n' in token:
        raise ValueError('Invalid local credential')
    segment = Segment(output, interval, budget)
    segment.manifest['startedAtUtc'] = datetime.datetime.now(datetime.timezone.utc).isoformat()
    started = time.monotonic()
    try:
        for relative in ('companion/series-cache.json.gz', 'market-cache/item-mapping.json'):
            source = user_root / relative
            if source.is_file():
                raw = bounded(source)
                value = json.loads(unpack(raw) if source.suffix == '.gz' else raw)
                key = segment.blobs.put(value)
                segment.manifest['seedFiles'][source.name] = {'sha256': key, 'availableAtNs': time.time_ns()}
        segment.save()
        while True:
            request = time.time_ns()
            try:
                if shutil.disk_usage(output).free < 256 * 1024**2:
                    raise RuntimeError('Public capture disk reserve reached')
                data, received = fetch(token)
                segment.capture(json.loads(data), request, received)
            except RuntimeError:
                raise
            except Exception as error:
                segment.append({'kind': 'FAILURE', 'requestAtNs': request,
                                'recordedAtNs': time.time_ns(), 'errorKind': type(error).__name__})
            remaining = seconds - (time.monotonic() - started)
            if remaining <= 0:
                break
            time.sleep(min(interval, remaining))
        segment.manifest['complete'] = True
    finally:
        segment.manifest['elapsedSeconds'] = round(time.monotonic() - started, 3)
        segment.manifest['compressedPublicBytes'] = segment.blobs.used
        segment.save()
    return segment.manifest


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--seconds', type=int, default=10 * 86400)
    parser.add_argument('--interval', type=int, default=60)
    parser.add_argument('--budget-mib', type=int, default=2048)
    args = parser.parse_args()
    background_priority()
    result = collect(args.output, args.seconds, args.interval, budget=args.budget_mib * 1024**2)
    print(json.dumps({key: result[key] for key in ('recordCount', 'complete', 'elapsedSeconds', 'compressedPublicBytes')}))
