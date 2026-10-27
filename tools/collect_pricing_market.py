"""Bounded public-only research capture with real availability clocks. Never posts account data."""
import argparse
import datetime
import gzip
import hashlib
import http.client
import json
from pathlib import Path
import shutil
import time

MAX_RESPONSE = 16 * 1024 * 1024
MAX_BUNDLE = 512 * 1024 * 1024


def digest(data):
    return hashlib.sha256(data).hexdigest()


def validate(feed, received_at):
    expected = {'observedAt', 'fiveMinuteStartedAt', 'hourlyStartedAt', 'latest', 'fiveMinute', 'hourly'}
    if not isinstance(feed, dict) or set(feed) != expected:
        raise ValueError('Unexpected public-feed contract')
    at = feed['observedAt']
    if type(at) is not int or at <= 0 or at > received_at:
        raise ValueError('Invalid public snapshot clock')
    for name, seconds in [('fiveMinuteStartedAt', 300), ('hourlyStartedAt', 3600)]:
        start = feed[name]
        if type(start) is not int or start < 0 or start and (start % seconds or start + seconds > at):
            raise ValueError('Incomplete aggregate publication clock')
    for name in ('latest', 'fiveMinute', 'hourly'):
        rows = feed[name]
        if not isinstance(rows, dict) or len(rows) > 20000 or any(not k.isdecimal() or int(k) <= 0 or not isinstance(v, dict) for k, v in rows.items()):
            raise ValueError('Invalid public item map')
    # Individual old/missing sides stay as observed. No invented quote times or averaged executions.
    for quote in feed['latest'].values():
        for field in ('lowTime', 'highTime'):
            clock = quote.get(field)
            if clock is not None and (type(clock) is not int or clock <= 0 or clock > at):
                raise ValueError('Future or malformed public trade time')


def fetch(token):
    connection = http.client.HTTPConnection('127.0.0.1', 37777, timeout=5)
    try:
        connection.request('GET', '/v1/market/snapshot', headers={'X-Flipping-Friend-Token': token})
        response = connection.getresponse()
        if response.status != 200:
            raise ValueError('Public read did not succeed')
        data = response.read(MAX_RESPONSE + 1)
        if len(data) > MAX_RESPONSE:
            raise ValueError('Public response exceeds capture budget')
        received = time.time_ns()
        feed = json.loads(data)
        validate(feed, received // 1_000_000_000)
        return data, received
    finally:
        connection.close()


def collect(output, seconds, interval, user_root=None):
    output = Path(output).resolve()
    if 'local-data' not in output.parts or output.exists() or not 1 <= seconds <= 31 * 86400 or not 60 <= interval <= 900:
        raise ValueError('New private directory and bounded capture duration/cadence required')
    user_root = Path(user_root) if user_root else Path.home() / '.runelite' / 'osrs-flipping-friend'
    config = user_root / 'companion' / 'companion.properties'
    if config.stat().st_size > 65536:
        raise ValueError('Invalid local credential configuration')
    token = next(line[6:].strip() for line in config.read_text(encoding='utf-8-sig').splitlines() if line.startswith('token='))
    if not token or '\r' in token or '\n' in token:
        raise ValueError('Invalid local credential')
    if shutil.disk_usage(output.parent).free < MAX_BUNDLE + 256 * 1024 * 1024:
        raise ValueError('Insufficient research-capture disk budget')
    output.mkdir()
    manifest = {'schemaVersion': 1, 'evidence': 'PUBLIC_MARKET_NOT_EXECUTIONS',
                'availabilityEvidence': 'RECORDED_PUBLIC_AVAILABILITY', 'intervalSeconds': interval,
                'startedAtUtc': datetime.datetime.now(datetime.timezone.utc).isoformat(),
                'captures': [], 'failures': [], 'seedFiles': {}, 'complete': False}
    used = 0
    for relative in ('companion/series-cache.json.gz', 'market-cache/item-mapping.json'):
        source = user_root / relative
        if source.is_file():
            if source.stat().st_size > MAX_BUNDLE // 2:
                raise ValueError('Public seed exceeds research budget')
            data = source.read_bytes()
            if len(data) > MAX_BUNDLE // 2:
                raise ValueError('Public seed exceeds research budget')
            target = output / source.name
            target.write_bytes(data)
            used += len(data)
            manifest['seedFiles'][source.name] = {'sha256': digest(data), 'bytes': len(data), 'availableAtNs': time.time_ns()}
    def save():
        temporary = output / 'manifest.pending.json'
        temporary.write_text(json.dumps(manifest, indent=2))
        temporary.replace(output / 'manifest.json')
    started = time.monotonic()
    try:
        while True:
            request_at = time.time_ns()
            try:
                data, received_at = fetch(token)
                compressed = gzip.compress(data, mtime=0)
                if used + len(compressed) > MAX_BUNDLE:
                    raise RuntimeError('Research bundle budget exhausted')
                name = f'public-{received_at}.json.gz'
                with (output / name).open('xb') as stream:
                    stream.write(compressed)
                used += len(compressed)
                manifest['captures'].append({'file': name, 'sha256': digest(compressed), 'payloadSha256': digest(data),
                    'requestAtNs': request_at, 'availableAtNs': received_at, 'bytes': len(compressed)})
            except RuntimeError:
                raise
            except Exception as error:
                manifest['failures'].append({'requestAtNs': request_at, 'recordedAtNs': time.time_ns(), 'kind': type(error).__name__})
            save()
            remaining = seconds - (time.monotonic() - started)
            if remaining <= 0:
                break
            time.sleep(min(interval, remaining))
        manifest['complete'] = True
    finally:
        manifest['elapsedSeconds'] = round(time.monotonic() - started, 3)
        manifest['totalBytes'] = used
        save()
    return manifest


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--seconds', type=int, default=3600)
    parser.add_argument('--interval', type=int, default=60)
    args = parser.parse_args()
    result = collect(args.output, args.seconds, args.interval)
    print(json.dumps({'captures': len(result['captures']), 'failures': len(result['failures']), 'complete': result['complete']}))
