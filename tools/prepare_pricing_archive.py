"""Prepare referenced 64-bucket public histories without repeating complete histories per frame."""
import argparse
import json
from pathlib import Path

from collect_pricing_market import validate
from pricing_archive import Blobs, bounded, encode, records, sha


def prepare(source, output, count=24):
    source, output = Path(source).resolve(), Path(output).absolute()
    if 'local-data' not in output.parts or output.exists() or not 1 <= count <= 32:
        raise ValueError('New private cohort and bounded initial catalogue required')
    manifest_bytes = bounded(source / 'manifest.json')
    manifest = json.loads(manifest_bytes)
    segmented = manifest.get('schemaVersion') == 3
    if segmented:
        from segmented_pricing_archive import view
        manifest, stream = view(source)
        manifest_bytes = encode(manifest)
    else:
        stream = iter(records(source))
    first = None
    failures = []
    for row in stream:
        if row['kind'] == 'CAPTURE':
            first = row
            break
        failures.append(row)
    if first is None:
        raise ValueError('No public observations')
    blobs = Blobs(source / 'blobs', readonly=True)
    seeds = manifest['seedFiles']
    if any(type(v['availableAtNs']) is not int or v['availableAtNs'] > first['availableAtNs'] for v in seeds.values()):
        raise ValueError('Seed history was not available before initial observation')
    series = blobs.get(seeds['series-cache.json.gz']['sha256'])
    if segmented:
        from segmented_pricing_archive import validate_seed
        validate_seed(series, seeds['series-cache.json.gz']['availableAtNs'])
    mapping = {}
    for item in blobs.get(seeds['item-mapping.json']['sha256']):
        if segmented and (type(item.get('id')) is not int or not 1 <= item['id'] <= 2**31 - 1
                or type(item.get('limit')) is not int or not 1 <= item['limit'] <= 2**31 - 1
                or type(item.get('members')) is not bool or not isinstance(item.get('name'), str)
                or len(item['name']) > 256):
            raise ValueError('Invalid hosted public mapping')
        if item['id'] in mapping:
            raise ValueError('Duplicate public mapping')
        mapping[item['id']] = item
    start = (first['availableAtNs'] + 999999999) // 1000000000
    histories = {}
    for entry in series['entries']:
        item, step = entry['key'].split('@')
        item = int(item)
        if step not in ('5m', '1h') or item not in mapping:
            continue
        if (item, step) in histories:
            raise ValueError('Duplicate seed history')
        seconds = 300 if step == '5m' else 3600
        bars = {}
        for bar in entry['data']:
            at = bar['timestamp']
            if at in bars:
                raise ValueError('Duplicate seed bucket')
            if at + seconds <= start:
                bars[at] = bar
        histories[item, step] = bars
    initial = []
    for item, metadata in mapping.items():
        short = histories.get((item, '5m'), {})
        if not metadata.get('limit') or len(short) < 48:
            continue
        turnover = sum((bar.get('lowPriceVolume', 0) + bar.get('highPriceVolume', 0))
                       * (bar.get('avgLowPrice') or 0) for bar in [short[t] for t in sorted(short)[-48:]])
        initial.append((turnover, item))
    selected = sorted(item for _, item in sorted(initial, reverse=True)[:count])
    if not selected:
        raise ValueError('No initial history-supported catalogue')
    initial_hourly = [item for item in selected if histories.get((item, '1h'))]
    histories = {key: value for key, value in histories.items() if key[0] in selected}
    output.mkdir()
    target = Blobs(output / 'blobs')
    frames, revisions, flows = [], [], set()

    def frame(row):
        feed = {key: row[key] for key in ('observedAt', 'fiveMinuteStartedAt', 'hourlyStartedAt')}
        if set(row['parts']) != {'latest', 'fiveMinute', 'hourly'}:
            raise ValueError('Unexpected public component')
        feed.update({key: blobs.get(value) for key, value in row['parts'].items()})
        now = (row['availableAtNs'] + 999999999) // 1000000000
        validate(feed, row['availableAtNs'] // 1000000000)
        if segmented:
            from segmented_pricing_archive import numeric_feed
            numeric_feed(feed)
        value = {'availableAt': now, **{key: feed[key] for key in ('observedAt', 'fiveMinuteStartedAt', 'hourlyStartedAt')},
                 'latest': {}, 'fiveMinute': {}, 'hourly': {}, 'shortHistory': {}, 'longHistory': {}, 'flows': []}
        for item in selected:
            key = str(item)
            if key in feed['latest']:
                value['latest'][key] = feed['latest'][key]
            for field, clock, step, seconds in [('fiveMinute', 'fiveMinuteStartedAt', '5m', 300), ('hourly', 'hourlyStartedAt', '1h', 3600)]:
                bucket, bar = feed[clock], feed[field].get(key)
                if bar is not None:
                    value[field][key] = bar
                if not bucket or bar is None:
                    continue
                history = histories.setdefault((item, step), {})
                changed = {'timestamp': bucket, **bar}
                if bucket in history and history[bucket] != changed:
                    revisions.append({'item': item, 'step': step, 'bucket': bucket, 'availableAt': now})
                history[bucket] = changed
                for old in sorted(history)[:-720]:
                    del history[old]
                if step == '5m' and bucket >= start and (item, bucket) not in flows:
                    low, high = bar.get('avgLowPrice'), bar.get('avgHighPrice')
                    if low and high and low <= high:
                        value['flows'].append({'itemId': item, 'bucketStart': bucket, 'bucketEnd': bucket + seconds,
                            'low': low, 'high': high, 'lowVolume': bar.get('lowPriceVolume', 0), 'highVolume': bar.get('highPriceVolume', 0)})
                        flows.add((item, bucket))
            for field, step, seconds in [('shortHistory', '5m', 300), ('longHistory', '1h', 3600)]:
                history = histories.get((item, step), {})
                groups = {}
                # Time-aligned chunks keep old chunks stable when the 720-bucket window moves.
                for at in sorted(history)[-720:]:
                    groups.setdefault(at // (64 * seconds), []).append(history[at])
                value[field][key] = [target.put(bars) for bars in groups.values()]
        if frames and now <= frames[-1]['availableAt']:
            raise ValueError('Second-resolution replay availability must increase')
        frames.append({'availableAt': now, 'sha256': target.put(value)})

    frame(first)
    for row in stream:
        if row['kind'] == 'FAILURE':
            failures.append(row)
        else:
            frame(row)
    end = frames[-1]['availableAt']
    if end - start < 1500 or len(frames) < 2:
        raise ValueError('Insufficient chronological smoke coverage')
    metadata = {'schemaVersion': '1', 'taxPolicy': 'GE_2025_05_29', 'risk': 'MODERATE', 'startAt': start, 'endAt': end,
                'initialCoins': 100000000, 'slots': 8, 'members': True, 'actionSeconds': 15, 'visitSeconds': 120,
                'minimumProfit': 10000, 'terminalHaircutBps': 1000, 'missedVisitIndices': [], 'restartAt': [],
                'sourceDescription': 'Recorded public availability; time-aligned immutable history chunks. '
                    'Actual asynchronous quote clocks; missing observations stay missing. Public flow simulations are not executions.',
                'mapping': {str(item): {'id': item, 'name': mapping[item]['name'], 'members': mapping[item]['members'], 'buyLimit': mapping[item]['limit']} for item in selected},
                'storage': {'version': 'CHUNKED_PUBLIC_REPLAY_V1', 'frames': frames}}
    raw = encode(metadata)
    with (output / 'input.json').open('xb') as stream:
        stream.write(raw)
    receipt = {'schemaVersion': 2, 'inputSha256': sha(raw), 'captureManifestSha256': sha(manifest_bytes),
               'availabilityEvidence': 'RECORDED_PUBLIC_AVAILABILITY', 'selectedItems': selected,
               'initialEligibleItems': len(initial),
               'initialHourlyHistoryItems': initial_hourly,
               'startAt': start, 'endAt': end, 'captureFailures': failures,
               'revisions': revisions, 'compressedPublicBytes': target.used,
               'scope': 'PUBLIC_SCENARIOS_NOT_EXECUTION_OR_PROMOTION'}
    if segmented:
        receipt['captureSchemaVersion'] = 3
        receipt['sealedPrefix'] = manifest
    (output / 'preparation-receipt.json').write_bytes(encode(receipt))
    return receipt


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('capture', type=Path)
    parser.add_argument('output', type=Path)
    parser.add_argument('--items', type=int, default=24)
    args = parser.parse_args()
    print(json.dumps(prepare(args.capture, args.output, args.items)))
