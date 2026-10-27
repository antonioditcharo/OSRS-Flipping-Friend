import gzip
import importlib.util
import json
from pathlib import Path
import sys
import tempfile
import unittest
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import pricing_archive as archive
import prepare_pricing_archive as prepare
import collect_pricing_archive as collector

spec = importlib.util.spec_from_file_location('legacy_prepare_test', Path(__file__).with_name('test_prepare_pricing_cohort.py'))
legacy = importlib.util.module_from_spec(spec)
spec.loader.exec_module(legacy)


class PricingArchiveTest(unittest.TestCase):
    def make(self, root):
        source, manifest = legacy.PreparePricingCohortTest().make(root)
        segment = archive.Segment(root / 'segment', 60)
        for name, value in manifest['seedFiles'].items():
            raw = (source / name).read_bytes()
            seed = json.loads(gzip.decompress(raw) if name.endswith('.gz') else raw)
            segment.manifest['seedFiles'][name] = {'sha256': segment.blobs.put(seed), 'availableAtNs': value['availableAtNs']}
        for row in manifest['captures']:
            segment.capture(json.loads(gzip.decompress((source / row['file']).read_bytes())), row['requestAtNs'], row['availableAtNs'])
        segment.manifest['complete'] = True
        segment.save()
        return source, segment

    def test_referenced_history_matches_legacy_frame_for_frame_without_quote_or_flow_invention(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp) / 'local-data'
            root.mkdir()
            source, segment = self.make(root)
            legacy.prepare.prepare(source, root / 'legacy.json', 1)
            receipt = prepare.prepare(segment.root, root / 'compact', 1)
            old = json.loads((root / 'legacy.json').read_text())
            compact = json.loads((root / 'compact/input.json').read_text())
            blobs = archive.Blobs(root / 'compact/blobs', readonly=True)
            for expected, ref in zip(old['frames'], compact['storage']['frames']):
                frame = blobs.get(ref['sha256'])
                for key in ('shortHistory', 'longHistory'):
                    frame[key] = {item: [bar for chunk in chunks for bar in blobs.get(chunk)] for item, chunks in frame[key].items()}
                self.assertEqual(expected, frame)
            self.assertEqual('RECORDED_PUBLIC_AVAILABILITY', receipt['availabilityEvidence'])
            self.assertEqual([], receipt['captureFailures'])
            with self.assertRaises(ValueError):
                prepare.prepare(segment.root, root / 'compact', 1)

    def test_chain_changes_and_incomplete_segments_never_become_completed_evidence(self):
        for mutation in ('head', 'row', 'incomplete', 'blob'):
            with tempfile.TemporaryDirectory() as temp:
                root = Path(temp) / 'local-data'
                root.mkdir()
                _, segment = self.make(root)
                if mutation == 'head':
                    segment.manifest['head'] = 'f' * 64
                    segment.save()
                elif mutation == 'row':
                    path = segment.root / 'records/00000000.json'
                    value = json.loads(path.read_text());value['observedAt'] += 1;path.write_bytes(archive.encode(value))
                elif mutation == 'blob':
                    row = next(archive.records(segment.root))
                    (segment.root / 'blobs' / (row['parts']['latest'] + '.json.gz')).write_bytes(gzip.compress(b'{}'))
                else:
                    segment.manifest['complete'] = False
                    segment.save()
                with self.assertRaises(ValueError):
                    prepare.prepare(segment.root, root / 'bad', 1)

    def test_short_history_catalogue_does_not_require_or_invent_an_hourly_seed(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp) / 'local-data';root.mkdir()
            _, segment = self.make(root)
            seed = segment.manifest['seedFiles']['series-cache.json.gz']
            value = segment.blobs.get(seed['sha256'])
            value['entries'] = [row for row in value['entries'] if row['key'].endswith('@5m')]
            seed['sha256'] = segment.blobs.put(value);segment.save()
            receipt = prepare.prepare(segment.root, root / 'short-only', 1)
            self.assertEqual([], receipt['initialHourlyHistoryItems'])
            metadata = json.loads((root / 'short-only/input.json').read_text())
            blobs = archive.Blobs(root / 'short-only/blobs', readonly=True)
            self.assertEqual({'1': []}, blobs.get(metadata['storage']['frames'][0]['sha256'])['longHistory'])

    def test_equal_public_components_reuse_storage_but_keep_occurrences_and_failures(self):
        with tempfile.TemporaryDirectory() as temp:
            segment = archive.Segment(Path(temp) / 'new', 60)
            feed = {'observedAt': 1800, 'fiveMinuteStartedAt': 1500, 'hourlyStartedAt': 0,
                    'latest': {'1': {'low': 100, 'lowTime': 1700}}, 'fiveMinute': {}, 'hourly': {}}
            segment.capture(feed, 1800_000000000, 1801_000000000)
            used = segment.blobs.used
            segment.append({'kind': 'FAILURE', 'requestAtNs': 1810_000000000, 'recordedAtNs': 1811_000000000, 'errorKind': 'TimeoutError'})
            segment.capture(feed, 1860_000000000, 1861_000000000)
            self.assertEqual(used, segment.blobs.used)
            segment.manifest['complete'] = True;segment.save()
            rows = list(archive.records(segment.root))
            self.assertEqual(['CAPTURE', 'FAILURE', 'CAPTURE'], [row['kind'] for row in rows])
            self.assertNotEqual(rows[0]['availableAtNs'], rows[2]['availableAtNs'])
            with self.assertRaises(FileExistsError):
                archive.Segment(segment.root, 60)

    def test_blob_traversal_corruption_and_expansion_are_bounded(self):
        with tempfile.TemporaryDirectory() as temp:
            blobs = archive.Blobs(Path(temp) / 'blobs')
            with self.assertRaises(ValueError):
                blobs.get('../private')
            key = blobs.put({'public': 1})
            (blobs.root / (key + '.json.gz')).write_bytes(gzip.compress(b'changed'))
            with self.assertRaises(ValueError):
                blobs.get(key)
            with self.assertRaises(ValueError):
                archive.unpack(gzip.compress(b'x' * (archive.MAX_PAYLOAD + 1)))

    def test_collector_retains_failures_and_never_marks_budget_exhaustion_complete(self):
        for exhausted in (False, True):
            with tempfile.TemporaryDirectory() as temp:
                root = Path(temp) / 'local-data';root.mkdir()
                user = root / 'user';(user / 'companion').mkdir(parents=True)
                (user / 'companion/companion.properties').write_text('token=fixture-only\n')
                feed = {'observedAt': 1800, 'fiveMinuteStartedAt': 1500, 'hourlyStartedAt': 0,
                        'latest': {}, 'fiveMinute': {}, 'hourly': {}}
                output = root / 'capture'
                failure = RuntimeError('budget') if exhausted else TimeoutError('no response')
                with patch.object(collector, 'fetch', side_effect=[failure, (json.dumps(feed).encode(), 1803_000000000)]), \
                        patch.object(collector.time, 'monotonic', side_effect=[0, 0, 1, 1]), \
                        patch.object(collector.time, 'time_ns', side_effect=[1800_000000000, 1801_000000000, 1802_000000000]), \
                        patch.object(collector.time, 'sleep'):
                    if exhausted:
                        with self.assertRaises(RuntimeError):
                            collector.collect(output, 1, user_root=user, budget=1024**2)
                        self.assertFalse(json.loads((output / 'manifest.json').read_text())['complete'])
                    else:
                        result = collector.collect(output, 1, user_root=user, budget=1024**2)
                        self.assertTrue(result['complete'])
                        self.assertEqual(['FAILURE', 'CAPTURE'], [r['kind'] for r in archive.records(output)])


if __name__ == '__main__':
    unittest.main()
