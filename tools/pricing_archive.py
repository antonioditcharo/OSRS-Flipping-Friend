"""Immutable, bounded public capture segments. No account observations or financial writes."""
import gzip
import hashlib
import json
from pathlib import Path

MAX_PAYLOAD = 16 * 1024 * 1024
MAX_BYTES = 5 * 1024 * 1024 * 1024
MAX_RECORDS = 50000
ZERO = '0' * 64


def encode(value):
    return json.dumps(value, sort_keys=True, separators=(',', ':'), allow_nan=False).encode()


def sha(data):
    return hashlib.sha256(data).hexdigest()


def bounded(path, limit=MAX_PAYLOAD):
    path = Path(path)
    if path.is_symlink() or not path.is_file() or path.stat().st_size > limit:
        raise ValueError('Missing, linked or oversized public evidence')
    with path.open('rb') as stream:
        value = stream.read(limit + 1)
    if len(value) > limit:
        raise ValueError('Public evidence exceeds budget')
    return value


def unpack(data):
    import io
    with gzip.GzipFile(fileobj=io.BytesIO(data)) as stream:
        raw = stream.read(MAX_PAYLOAD + 1)
    if len(raw) > MAX_PAYLOAD:
        raise ValueError('Expanded public evidence exceeds budget')
    return raw


class Blobs:
    def __init__(self, root, budget=MAX_BYTES, readonly=False):
        self.root = Path(root)
        self.budget = budget
        self.used = 0
        self.known = set()
        self.readonly = readonly
        if self.root.exists() and self.root.is_symlink():
            raise ValueError('Linked public archive')
        if readonly:
            if not self.root.is_dir():
                raise ValueError('Missing public archive')
        else:
            self.root.mkdir(exist_ok=True)

    def put(self, value):
        if self.readonly:
            raise ValueError('Read-only public archive')
        raw = encode(value)
        if len(raw) > MAX_PAYLOAD:
            raise ValueError('Oversized public component')
        key = sha(raw)
        if key not in self.known:
            packed = gzip.compress(raw, mtime=0)
            if self.used + len(packed) > self.budget:
                raise RuntimeError('Public archive budget exhausted')
            path = self.root / (key + '.json.gz')
            if path.exists():
                if self.get(key) != value:
                    raise ValueError('Conflicting immutable public component')
            else:
                with path.open('xb') as stream:
                    stream.write(packed)
            self.used += len(packed)
            self.known.add(key)
        return key

    def get(self, key):
        if not isinstance(key, str) or len(key) != 64 or any(c not in '0123456789abcdef' for c in key):
            raise ValueError('Invalid public component identity')
        raw = unpack(bounded(self.root / (key + '.json.gz')))
        if sha(raw) != key:
            raise ValueError('Public component changed')
        return json.loads(raw)


class Segment:
    def __init__(self, root, interval, budget=MAX_BYTES):
        self.root = Path(root)
        self.root.mkdir()  # A restart always creates a new segment; interrupted evidence stays intact.
        (self.root / 'records').mkdir()
        self.blobs = Blobs(self.root / 'blobs', budget)
        self.budget, self.record_bytes = budget, 0
        self.manifest = {'schemaVersion': 2, 'evidence': 'PUBLIC_MARKET_NOT_EXECUTIONS',
                         'availabilityEvidence': 'RECORDED_PUBLIC_AVAILABILITY',
                         'intervalSeconds': interval, 'recordCount': 0, 'head': ZERO,
                         'seedFiles': {}, 'complete': False}
        self.save()

    def save(self):
        target = self.root / 'manifest.pending.json'
        target.write_bytes(encode(self.manifest))
        target.replace(self.root / 'manifest.json')

    def append(self, record):
        index = self.manifest['recordCount']
        if index >= MAX_RECORDS:
            raise RuntimeError('Public record budget exhausted')
        value = {**record, 'previous': self.manifest['head'], 'index': index}
        raw = encode(value)
        if len(raw) > 4096 or self.blobs.used + self.record_bytes + len(raw) + 65536 > self.budget:
            raise RuntimeError('Public record byte budget exhausted')
        with (self.root / 'records' / f'{index:08d}.json').open('xb') as stream:
            stream.write(raw)
        self.manifest['head'] = sha(raw)
        self.manifest['recordCount'] += 1
        self.record_bytes += len(raw)
        self.blobs.budget = self.budget - self.record_bytes - 65536
        self.save()

    def capture(self, feed, request, received):
        parts = {name: self.blobs.put(feed[name]) for name in ('latest', 'fiveMinute', 'hourly')}
        self.append({'kind': 'CAPTURE', 'requestAtNs': request, 'availableAtNs': received,
                     'observedAt': feed['observedAt'], 'fiveMinuteStartedAt': feed['fiveMinuteStartedAt'],
                     'hourlyStartedAt': feed['hourlyStartedAt'], 'parts': parts})


def records(root):
    """Validate the complete chain, including failures. Never infer missing observations."""
    root = Path(root)
    if root.is_symlink():
        raise ValueError('Linked capture segment')
    manifest = json.loads(bounded(root / 'manifest.json'))
    count = manifest.get('recordCount')
    if (manifest.get('schemaVersion') != 2 or manifest.get('complete') is not True
            or manifest.get('availabilityEvidence') != 'RECORDED_PUBLIC_AVAILABILITY'
            or type(count) is not int or not 1 <= count <= MAX_RECORDS
            or (root / 'records').is_symlink() or (root / 'blobs').is_symlink()):
        raise ValueError('Completed bounded public segment required')
    previous = ZERO
    last = 0
    for index in range(count):
        raw = bounded(root / 'records' / f'{index:08d}.json', 65536)
        row = json.loads(raw)
        if row.get('index') != index or row.get('previous') != previous:
            raise ValueError('Public capture chain changed')
        at = row.get('availableAtNs') if row.get('kind') == 'CAPTURE' else row.get('recordedAtNs')
        if (row.get('kind') not in ('CAPTURE', 'FAILURE') or type(at) is not int or at <= last
                or type(row.get('requestAtNs')) is not int or not 0 < row['requestAtNs'] < at):
            raise ValueError('Invalid public availability ordering')
        last, previous = at, sha(raw)
        yield row
    if previous != manifest['head']:
        raise ValueError('Public capture head changed')
