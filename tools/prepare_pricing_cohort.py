"""Build an immutable replay input from recorded public availability, preserving actual quote clocks."""
import argparse
import gzip
import hashlib
import json
from pathlib import Path

MAX_FILE = 256 * 1024 * 1024


def read(path, expected):
    if path.is_symlink() or not path.is_file() or path.stat().st_size > MAX_FILE:
        raise ValueError('Invalid frozen public file')
    data = path.read_bytes()
    if hashlib.sha256(data).hexdigest() != expected:
        raise ValueError('Frozen public file changed')
    return data


def prepare(capture, output, count=24):
    capture, output = Path(capture).resolve(), Path(output).resolve()
    if 'local-data' not in output.parts or output.exists() or not 1 <= count <= 32:
        raise ValueError('New private output and bounded initial catalogue required')
    manifest = json.loads((capture / 'manifest.json').read_text())
    if manifest.get('schemaVersion') != 1 or manifest.get('complete') is not True or manifest.get('availabilityEvidence') != 'RECORDED_PUBLIC_AVAILABILITY':
        raise ValueError('Completed public-availability capture required')
    rows = manifest['captures']
    if len(rows) < 2 or any(row['availableAtNs'] <= row['requestAtNs'] for row in rows):
        raise ValueError('Recorded request/availability clocks required')
    if any(a['availableAtNs'] >= b['availableAtNs'] for a, b in zip(rows, rows[1:])):
        raise ValueError('Capture availability must increase')
    seeds = manifest['seedFiles']
    series = json.loads(gzip.decompress(read(capture / 'series-cache.json.gz', seeds['series-cache.json.gz']['sha256'])))
    mapping = {item['id']: item for item in json.loads(read(capture / 'item-mapping.json', seeds['item-mapping.json']['sha256']))}
    if any(seed['availableAtNs'] > rows[0]['availableAtNs'] for seed in seeds.values()):
        raise ValueError('Warm history was not available at the first capture')
    start = (rows[0]['availableAtNs'] + 999999999) // 1000000000
    histories = {}
    for entry in series['entries']:
        item, step = entry['key'].split('@')
        item = int(item)
        if step not in ('5m', '1h') or item not in mapping:
            continue
        seconds = 300 if step == '5m' else 3600
        buckets = {bar['timestamp']: bar for bar in entry['data'] if bar['timestamp'] + seconds <= start}
        if (item, step) in histories:
            raise ValueError('Duplicate seed item/resolution')
        histories[item, step] = buckets
    initial = []
    for item, metadata in mapping.items():
        short = histories.get((item, '5m'), {})
        if not metadata.get('limit') or len(short) < 48 or not histories.get((item, '1h')):
            continue
        prefix = [short[t] for t in sorted(short)[-48:]]
        # Fixed selection uses only the initial prefix, never later return or forecast performance.
        turnover = sum((bar.get('lowPriceVolume', 0) + bar.get('highPriceVolume', 0)) * (bar.get('avgLowPrice') or 0) for bar in prefix)
        initial.append((turnover, item))
    selected = sorted(item for _, item in sorted(initial, reverse=True)[:count])
    if not selected:
        raise ValueError('No initial history-supported catalogue available')
    frames, flows, revisions = [], set(), []
    for row in rows:
        name = row['file']
        if Path(name).name != name:
            raise ValueError('Capture path escapes its bundle')
        payload = gzip.decompress(read(capture / name, row['sha256']))
        if len(payload) > MAX_FILE or hashlib.sha256(payload).hexdigest() != row['payloadSha256']:
            raise ValueError('Public payload changed')
        feed = json.loads(payload)
        now = (row['availableAtNs'] + 999999999) // 1000000000
        if feed['observedAt'] > now:
            raise ValueError('Future public observation')
        frame = {'availableAt': now, 'observedAt': feed['observedAt'], 'fiveMinuteStartedAt': feed['fiveMinuteStartedAt'],
                 'hourlyStartedAt': feed['hourlyStartedAt'], 'latest': {}, 'fiveMinute': {}, 'hourly': {},
                 'shortHistory': {}, 'longHistory': {}, 'flows': []}
        for item in selected:
            key = str(item)
            if key in feed['latest']:
                frame['latest'][key] = feed['latest'][key]
            for field, clock, step, seconds in [('fiveMinute', 'fiveMinuteStartedAt', '5m', 300), ('hourly', 'hourlyStartedAt', '1h', 3600)]:
                bucket = feed[clock]
                value = feed[field].get(key)
                if value is not None:
                    frame[field][key] = value
                if not bucket or value is None:
                    continue
                if bucket + seconds > feed['observedAt']:
                    raise ValueError('Incomplete published bucket')
                bar = {'timestamp': bucket, **value}
                history = histories.setdefault((item, step), {})
                if bucket in history and history[bucket] != bar:
                    revisions.append({'item': item, 'step': step, 'bucket': bucket, 'availableAt': now})
                history[bucket] = bar
                if step == '5m' and bucket >= start and (item, bucket) not in flows:
                    low, high = bar.get('avgLowPrice'), bar.get('avgHighPrice')
                    if low and high and low <= high:
                        frame['flows'].append({'itemId': item, 'bucketStart': bucket, 'bucketEnd': bucket + seconds,
                            'low': low, 'high': high, 'lowVolume': bar.get('lowPriceVolume', 0), 'highVolume': bar.get('highPriceVolume', 0)})
                        flows.add((item, bucket))
            for field, step in [('shortHistory', '5m'), ('longHistory', '1h')]:
                history = histories.get((item, step), {})
                frame[field][key] = [history[t] for t in sorted(history)[-720:]]
        frames.append(frame)
    end = frames[-1]['availableAt']
    if end - start < 1500:
        raise ValueError('Capture is too short even for a chronological smoke cohort')
    result = {'schemaVersion': '1', 'taxPolicy': 'GE_2025_05_29', 'risk': 'MODERATE', 'startAt': start, 'endAt': end,
              'initialCoins': 100000000, 'slots': 8, 'members': True, 'actionSeconds': 15, 'visitSeconds': 120,
              'minimumProfit': 10000, 'terminalHaircutBps': 1000, 'missedVisitIndices': [], 'restartAt': [],
              'sourceDescription': 'Fresh public snapshots with recorded first availability and actual asynchronous latest trade clocks. '
                  'Initial cached history becomes available at the recorded seed capture. Public bucket volume-share fills remain simulations. '
                  'Seeded catalogue selected by initial turnover only; unsupported catalogue/cadence segments retain the verified baseline.',
              'mapping': {str(item): {'id': item, 'name': mapping[item]['name'], 'members': mapping[item]['members'], 'buyLimit': mapping[item]['limit']} for item in selected},
              'frames': frames}
    data = json.dumps(result, separators=(',', ':')).encode()
    if len(data) > MAX_FILE:
        raise ValueError('Cohort exceeds explicit evaluator budget')
    with output.open('xb') as stream:
        stream.write(data)
    receipt = {'schemaVersion': 1, 'inputSha256': hashlib.sha256(data).hexdigest(),
               'captureManifestSha256': hashlib.sha256((capture / 'manifest.json').read_bytes()).hexdigest(),
               'availabilityEvidence': 'RECORDED_PUBLIC_AVAILABILITY', 'selectedItems': selected, 'initialEligibleItems': len(initial),
               'startAt': start, 'endAt': end, 'captureFailures': manifest['failures'], 'revisions': revisions,
               'scope': 'PUBLIC_SCENARIOS_NOT_EXECUTION_OR_PROMOTION'}
    output.with_suffix('.receipt.json').write_text(json.dumps(receipt, indent=2))
    return receipt


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('capture', type=Path)
    parser.add_argument('output', type=Path)
    parser.add_argument('--items', type=int, default=24)
    args = parser.parse_args()
    receipt = prepare(args.capture, args.output, args.items)
    print(json.dumps({'items': len(receipt['selectedItems']), 'startAt': receipt['startAt'], 'endAt': receipt['endAt']}))
