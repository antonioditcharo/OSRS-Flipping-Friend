import gzip
import hashlib
import importlib.util
import json
from pathlib import Path
import tempfile
import unittest

spec = importlib.util.spec_from_file_location('prepare_pricing_cohort', Path(__file__).resolve().parents[1] / 'prepare_pricing_cohort.py')
prepare = importlib.util.module_from_spec(spec)
spec.loader.exec_module(prepare)


class PreparePricingCohortTest(unittest.TestCase):
    def make(self, root):
        start = 1800000000
        source = root / 'capture'
        source.mkdir()
        def bar(at):
            return {'timestamp': at, 'avgLowPrice': 100, 'avgHighPrice': 130, 'lowPriceVolume': 1000, 'highPriceVolume': 1000}
        series = {'entries': [{'key': '1@5m', 'data': [bar(start - i * 300) for i in range(48, 0, -1)]},
                              {'key': '1@1h', 'data': [bar(start - 3600)]}]}
        seed = {'series-cache.json.gz': gzip.compress(json.dumps(series).encode()),
                'item-mapping.json': json.dumps([{'id': 1, 'name': 'Public item', 'members': True, 'limit': 1000}]).encode()}
        manifest = {'schemaVersion': 1, 'complete': True, 'availabilityEvidence': 'RECORDED_PUBLIC_AVAILABILITY',
                    'seedFiles': {}, 'captures': [], 'failures': []}
        for name, data in seed.items():
            (source / name).write_bytes(data)
            manifest['seedFiles'][name] = {'sha256': hashlib.sha256(data).hexdigest(), 'availableAtNs': start * 1000000000 - 5}
        for offset in (0, 1800):
            now = start + offset
            value = bar(now - 300)
            value.pop('timestamp')
            feed = {'observedAt': now, 'fiveMinuteStartedAt': now - 300, 'hourlyStartedAt': start - 3600,
                    'latest': {'1': {'low': 101, 'lowTime': now - 100, 'high': 131, 'highTime': now - 10}},
                    'fiveMinute': {'1': value}, 'hourly': {}}
            payload = json.dumps(feed).encode()
            compressed = gzip.compress(payload)
            name = f'{offset}.json.gz'
            (source / name).write_bytes(compressed)
            manifest['captures'].append({'file': name, 'sha256': hashlib.sha256(compressed).hexdigest(),
                'payloadSha256': hashlib.sha256(payload).hexdigest(), 'availableAtNs': now * 1000000000, 'requestAtNs': now * 1000000000 - 1})
        (source / 'manifest.json').write_text(json.dumps(manifest))
        return source, manifest

    def test_real_quote_and_availability_clocks_survive_without_bucket_average_substitution(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp) / 'local-data'
            root.mkdir()
            source, _ = self.make(root)
            output = root / 'input.json'
            receipt = prepare.prepare(source, output, 1)
            data = json.loads(output.read_text())
            self.assertEqual(1800000000, data['startAt'])
            quote = data['frames'][0]['latest']['1']
            self.assertEqual({'low': 101, 'lowTime': 1799999900, 'high': 131, 'highTime': 1799999990}, quote)
            self.assertEqual([], data['frames'][0]['flows'])
            self.assertEqual(1, len(data['frames'][1]['flows']))
            self.assertEqual(1800000000 + 1500, data['frames'][1]['flows'][0]['bucketStart'])
            self.assertEqual('RECORDED_PUBLIC_AVAILABILITY', receipt['availabilityEvidence'])
            with self.assertRaises(ValueError):
                prepare.prepare(source, output, 1)

    def test_changed_seed_or_history_arriving_after_first_capture_is_rejected(self):
        for mutation in ('clock', 'hash'):
            with tempfile.TemporaryDirectory() as temp:
                root = Path(temp) / 'local-data'
                root.mkdir()
                source, manifest = self.make(root)
                if mutation == 'clock':
                    manifest['seedFiles']['series-cache.json.gz']['availableAtNs'] += 100
                    (source / 'manifest.json').write_text(json.dumps(manifest))
                else:
                    (source / 'series-cache.json.gz').write_bytes(b'changed')
                with self.assertRaises(ValueError):
                    prepare.prepare(source, root / 'input.json', 1)


if __name__ == '__main__':
    unittest.main()
