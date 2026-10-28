"""Read a sealed v3 public archive prefix; interrupted tails stay explicit and untouched."""
import json
import re
from pathlib import Path

from pricing_archive import Blobs, ZERO, bounded, encode, sha


def view(root):
    root = Path(root)
    if any(p.is_symlink() for p in (root, *root.parents)):
        raise ValueError('Linked public archive')
    raw = bounded(root / 'manifest.json', 65536)
    manifest = json.loads(raw)
    if (manifest.get('schemaVersion') != 3 or manifest.get('evidence') != 'PUBLIC_MARKET_NOT_EXECUTIONS'
            or manifest.get('availabilityEvidence') != 'RECORDED_PUBLIC_AVAILABILITY'
            or manifest.get('intervalSeconds') != 60 or manifest.get('segmentSeconds') != 300
            or type(manifest.get('startedAtNs')) is not int or manifest['startedAtNs'] <= 0
            or type(manifest.get('durationSeconds')) is not int or not 1 <= manifest['durationSeconds'] <= 31 * 86400
            or manifest.get('stopAtNs') != manifest['startedAtNs'] + manifest['durationSeconds'] * 10**9
            or type(manifest.get('budgetBytes')) is not int or not 1024**2 <= manifest['budgetBytes'] <= 2 * 1024**3):
        raise ValueError('Incompatible bounded public archive')
    seeds_raw = bounded(root / 'seeds.json', 65536)
    seeds = json.loads(seeds_raw)
    if set(seeds.get('seedFiles', {})) != {'item-mapping.json', 'series-cache.json.gz'}:
        raise ValueError('Missing public seed provenance')
    for seed in seeds['seedFiles'].values():
        if (type(seed.get('availableAtNs')) is not int or seed['availableAtNs'] < manifest['startedAtNs']
                or not re.fullmatch('[0-9a-f]{64}', seed.get('sha256', ''))):
            raise ValueError('Invalid seed availability')
    directory = root / 'segments'
    if directory.is_symlink() or not directory.is_dir():
        raise ValueError('Missing public segments')
    sealed, incomplete = [], []
    for path in sorted(directory.iterdir()):
        if (path.is_symlink() or not path.is_dir()
                or not re.fullmatch('[0-9]{8}-[0-9a-f-]{36}', path.name)):
            raise ValueError('Invalid public segment path')
        if len(sealed) + len(incomplete) >= 20000:
            raise ValueError('Too many public segments')
        if not (path / 'seal.json').exists():
            incomplete.append(path.name)
            continue
        seal_raw = bounded(path / 'seal.json', 65536)
        seal = json.loads(seal_raw)
        sequence = seal.get('sequence')
        if (type(sequence) is not int or sequence < 0 or path.name[:8] != f'{sequence:08d}'
                or seal.get('schemaVersion') != 3 or seal.get('complete') is not True
                or type(seal.get('recordCount')) is not int or not 1 <= seal['recordCount'] <= 16):
            raise ValueError('Invalid public seal')
        sealed.append((sequence, path, seal, sha(seal_raw)))
    sealed.sort(key=lambda value: value[0])
    previous = ZERO
    for index, (sequence, _, seal, digest) in enumerate(sealed):
        if sequence != index or seal.get('previousSeal') != previous:
            raise ValueError('Missing, duplicated or changed public segment')
        previous = digest
    if not sealed:
        raise ValueError('No sealed public observations')
    # This immutable descriptor binds the selected prefix even while the writer continues.
    snapshot = {'schemaVersion': 3, 'rootManifestSha256': sha(raw), 'seedsSha256': sha(seeds_raw),
                'seedFiles': seeds['seedFiles'], 'sealedHead': previous,
                'sealedSegments': [{'directory': p.name, 'sha256': h} for _, p, _, h in sealed],
                'incompleteSegments': incomplete,
                'availabilityEvidence': 'RECORDED_PUBLIC_AVAILABILITY'}

    def rows():
        last = 0
        for _, path, seal, _ in sealed:
            head, first = ZERO, None
            for index in range(seal['recordCount']):
                payload = bounded(path / f'{index:08d}.json', 65536)
                row = json.loads(payload)
                at = row.get('availableAtNs') if row.get('kind') == 'CAPTURE' else row.get('recordedAtNs')
                if (row.get('kind') not in ('CAPTURE', 'FAILURE') or row.get('index') != index
                        or row.get('previous') != head or type(at) is not int or at <= last
                        or not manifest['startedAtNs'] <= at <= manifest['stopAtNs'] + 60 * 10**9
                        or type(row.get('requestAtNs')) is not int or not 0 < row['requestAtNs'] < at):
                    raise ValueError('Invalid segmented availability/record chain')
                if first is None:
                    first = at
                last, head = at, sha(payload)
                yield row
            if head != seal.get('head') or first != seal.get('firstAtNs') or last != seal.get('lastAtNs'):
                raise ValueError('Changed public segment head or clocks')
    return snapshot, rows()


def validate_seed(series, available_at_ns):
    """Reject numeric truncation, future buckets and ambiguous duplicate seed origins."""
    entries = series.get('entries')
    if not isinstance(entries, list) or len(entries) > 64:
        raise ValueError('Bounded hosted history required')
    seen = set()
    for entry in entries:
        key = entry.get('key', '')
        if not re.fullmatch('[1-9][0-9]*@(5m|1h)', key) or key in seen:
            raise ValueError('Invalid/duplicate hosted seed history')
        seen.add(key)
        seconds = 300 if key.endswith('@5m') else 3600
        at = entry.get('fetchedAt')
        if type(at) is not int or not 0 < at <= available_at_ns // 10**9:
            raise ValueError('Invalid hosted history availability')
        bars = entry.get('data')
        if not isinstance(bars, list) or len(bars) > 720:
            raise ValueError('Oversized hosted history')
        previous = 0
        for bar in bars:
            bucket = bar.get('timestamp')
            if type(bucket) is not int or bucket <= previous or bucket % seconds or bucket + seconds > at:
                raise ValueError('Incomplete/unordered hosted history')
            numeric_bar(bar)
            previous = bucket


def numeric_bar(bar):
    for field in ('avgLowPrice', 'avgHighPrice', 'lowPriceVolume', 'highPriceVolume'):
        value = bar.get(field)
        price = field.startswith('avg')
        if value is None and price:
            continue
        if (type(value) is not int or value < (1 if price else 0)
                or value > (2**31 - 1 if price else 2**63 - 1)):
            raise ValueError('Exact positive prices/nonnegative public volumes required')


def numeric_feed(feed):
    for row in feed['latest'].values():
        for field in ('low', 'high'):
            value = row.get(field)
            if value is not None and (type(value) is not int or not 1 <= value <= 2**31 - 1):
                raise ValueError('Exact positive public quote required')
    for name in ('fiveMinute', 'hourly'):
        for row in feed[name].values():
            numeric_bar(row)
