import json
from pathlib import Path
import sys
import tempfile
import unittest
import uuid

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from pricing_archive import Blobs, ZERO, bounded, encode, sha
import segmented_pricing_archive as segmented
import prepare_pricing_archive as prepare


class SegmentedPricingArchiveTest(unittest.TestCase):
    def replace_part(self, source, index, field, value, blobs):
        paths = sorted((source / 'segments').iterdir())
        previous = ZERO
        for i, path in enumerate(paths):
            row = json.loads((path / '00000000.json').read_bytes())
            if i == index:
                row['parts'][field] = blobs.put(value)
                (path / '00000000.json').write_bytes(encode(row))
            seal = json.loads((path / 'seal.json').read_bytes())
            seal.update(previousSeal=previous, head=sha((path / '00000000.json').read_bytes()))
            raw = encode(seal); (path / 'seal.json').write_bytes(raw); previous = sha(raw)

    def make(self, root):
        root.mkdir()
        (root / 'segments').mkdir()
        blobs = Blobs(root / 'blobs')
        start = 1800000000
        manifest = {'schemaVersion': 3, 'evidence': 'PUBLIC_MARKET_NOT_EXECUTIONS',
                    'availabilityEvidence': 'RECORDED_PUBLIC_AVAILABILITY', 'intervalSeconds': 60,
                    'segmentSeconds': 300, 'budgetBytes': 1024**2, 'startedAtNs': (start - 10) * 10**9,
                    'durationSeconds': 3600, 'stopAtNs': (start - 10 + 3600) * 10**9}
        (root / 'manifest.json').write_bytes(encode(manifest))
        def bar(at):
            return {'timestamp': at, 'avgLowPrice': 100, 'avgHighPrice': 130,
                    'lowPriceVolume': 1000, 'highPriceVolume': 1000}
        series = {'entries': [{'key': '1@5m', 'fetchedAt': start - 1,
                              'data': [bar(start - i * 300) for i in range(49, 1, -1)]}]}
        mapping = [{'id': 1, 'name': 'Synthetic public item', 'members': True, 'limit': 1000}]
        seeds = {'seedFiles': {name: {'sha256': blobs.put(value), 'availableAtNs': start * 10**9 - 5}
                              for name, value in [('item-mapping.json', mapping), ('series-cache.json.gz', series)]}}
        (root / 'seeds.json').write_bytes(encode(seeds))
        previous = ZERO
        for index, offset in enumerate((0, 900, 1800)):
            now = start + offset
            feed = {'observedAt': now, 'fiveMinuteStartedAt': now - 300, 'hourlyStartedAt': start - 3600,
                    'latest': {'1': {'low': 101, 'lowTime': now - 100, 'high': 131, 'highTime': now - 10}},
                    'fiveMinute': {'1': {k: v for k, v in bar(now - 300).items() if k != 'timestamp'}}, 'hourly': {}}
            path = root / 'segments' / (f'{index:08d}-' + str(uuid.uuid4()))
            path.mkdir()
            row = {'kind': 'CAPTURE', 'index': 0, 'previous': ZERO, 'requestAtNs': now * 10**9 - 1,
                   'availableAtNs': now * 10**9,
                   **{k: feed[k] for k in ('observedAt', 'fiveMinuteStartedAt', 'hourlyStartedAt')},
                   'parts': {k: blobs.put(feed[k]) for k in ('latest', 'fiveMinute', 'hourly')}}
            raw = encode(row)
            (path / '00000000.json').write_bytes(raw)
            seal = {'schemaVersion': 3, 'sequence': index, 'previousSeal': previous, 'recordCount': 1,
                    'complete': True, 'head': sha(raw), 'firstAtNs': row['availableAtNs'], 'lastAtNs': row['availableAtNs']}
            raw = encode(seal)
            (path / 'seal.json').write_bytes(raw)
            previous = sha(raw)
        return blobs

    def test_sealed_prefix_prepares_exact_quotes_and_reports_interrupted_tail_without_completing_it(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp) / 'local-data'; root.mkdir()
            source = root / 'capture'; self.make(source)
            tail = source / 'segments' / ('00000003-' + str(uuid.uuid4())); tail.mkdir()
            (tail / '00000000.json').write_bytes(b'original interrupted evidence')
            receipt = prepare.prepare(source, root / 'prepared', 1)
            self.assertEqual([tail.name], receipt['sealedPrefix']['incompleteSegments'])
            self.assertEqual(3, len(receipt['sealedPrefix']['sealedSegments']))
            self.assertFalse((tail / 'seal.json').exists())
            self.assertEqual(b'original interrupted evidence', (tail / '00000000.json').read_bytes())
            data = json.loads((root / 'prepared/input.json').read_bytes())
            blobs = Blobs(root / 'prepared/blobs', readonly=True)
            first = blobs.get(data['storage']['frames'][0]['sha256'])
            self.assertEqual(101, first['latest']['1']['low'])
            self.assertEqual(1799999900, first['latest']['1']['lowTime'])
            self.assertEqual([], receipt['initialHourlyHistoryItems'])
            self.assertEqual({'1': []}, first['longHistory'])

    def test_missing_duplicate_changed_segments_and_record_clocks_cannot_qualify(self):
        for mutation in ('missing', 'duplicate', 'seal', 'row', 'clock', 'future_seed'):
            with self.subTest(mutation=mutation), tempfile.TemporaryDirectory() as temp:
                root = Path(temp) / 'local-data'; root.mkdir()
                source = root / 'capture'; self.make(source)
                paths = sorted((source / 'segments').iterdir())
                if mutation == 'missing':
                    (paths[1] / 'seal.json').unlink()
                elif mutation == 'duplicate':
                    copy = source / 'segments' / ('00000001-' + str(uuid.uuid4())); copy.mkdir()
                    (copy / 'seal.json').write_bytes((paths[1] / 'seal.json').read_bytes())
                elif mutation in ('row', 'clock'):
                    row = json.loads((paths[1] / '00000000.json').read_bytes())
                    row['observedAt' if mutation == 'row' else 'availableAtNs'] += 1
                    (paths[1] / '00000000.json').write_bytes(encode(row))
                elif mutation == 'future_seed':
                    path = source / 'seeds.json'; seed = json.loads(path.read_bytes())
                    seed['seedFiles']['series-cache.json.gz']['availableAtNs'] += 100
                    path.write_bytes(encode(seed))
                else:
                    path = paths[0] / 'seal.json'; seal = json.loads(path.read_bytes()); seal['head'] = 'f' * 64
                    path.write_bytes(encode(seal))
                with self.assertRaises(ValueError):
                    prepare.prepare(source, root / 'bad', 1)
                self.assertFalse((root / 'bad/preparation-receipt.json').exists())

    def test_immutable_descriptor_binds_prefix_while_new_tail_is_not_silently_included(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp) / 'archive'; self.make(root)
            snapshot, rows = segmented.view(root)
            digest = sha(encode(snapshot))
            tail = root / 'segments' / ('00000003-' + str(uuid.uuid4())); tail.mkdir()
            self.assertEqual(3, len(list(rows)))
            self.assertEqual(digest, sha(encode(snapshot)))
            later, _ = segmented.view(root)
            self.assertNotEqual(digest, sha(encode(later)))

    def test_decimal_overflow_volume_and_future_seed_cannot_be_truncated_into_replay(self):
        for field, value in [('avgLowPrice', 1.5), ('avgHighPrice', 2**63), ('lowPriceVolume', -1), ('highPriceVolume', 2**63)]:
            row = {'avgLowPrice': 100, 'avgHighPrice': 120, 'lowPriceVolume': 1, 'highPriceVolume': 1}
            row[field] = value
            with self.assertRaises(ValueError):
                segmented.numeric_bar(row)
        with self.assertRaises(ValueError):
            segmented.validate_seed({'entries': [{'key': '1@5m', 'fetchedAt': 1800000000,
                'data': [{'timestamp': 1800000000, 'avgLowPrice': 100, 'avgHighPrice': 120,
                          'lowPriceVolume': 1, 'highPriceVolume': 1}]}]}, 1800000000 * 10**9)
        with self.assertRaises(ValueError):
            segmented.numeric_feed({'latest': {'1': {'low': 101.5}}, 'fiveMinute': {}, 'hourly': {}})
        high = {'latest': {'1': {'low': 8770000000}}, 'fiveMinute': {}, 'hourly': {}}
        segmented.numeric_feed(high)
        self.assertFalse(segmented.replay_supported(high['latest']['1']))
        self.assertEqual(8770000000, high['latest']['1']['low'])

    def test_large_raw_prices_are_preserved_but_initially_unsupported_items_are_not_narrowed(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp) / 'local-data'; root.mkdir(); source = root / 'archive'; blobs = self.make(source)
            seed = json.loads((source / 'seeds.json').read_bytes())
            series_ref = seed['seedFiles']['series-cache.json.gz']; series = blobs.get(series_ref['sha256'])
            copy = json.loads(json.dumps(series['entries'][0])); copy['key'] = '2@5m'
            for bar in copy['data']:
                bar['avgLowPrice'] = 8770000000; bar['avgHighPrice'] = 8865000000
            series['entries'].append(copy); series_ref['sha256'] = blobs.put(series)
            ref = seed['seedFiles']['item-mapping.json']; mapping = blobs.get(ref['sha256'])
            mapping.append({'id': 2, 'name': 'Unsupported synthetic price', 'members': True, 'limit': 100})
            ref['sha256'] = blobs.put(mapping); (source / 'seeds.json').write_bytes(encode(seed))
            receipt = prepare.prepare(source, root / 'replay', 2)
            self.assertEqual([1], receipt['selectedItems']); self.assertEqual([2], receipt['unsupportedInitialItems'])
            self.assertEqual(8770000000, blobs.get(series_ref['sha256'])['entries'][1]['data'][0]['avgLowPrice'])

    def test_later_crossing_of_model_bounds_refuses_cohort_without_retrospective_item_selection(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp) / 'local-data'; root.mkdir(); source = root / 'archive'; blobs = self.make(source)
            changed = {'1': {'low': 8770000000, 'lowTime': 1800000899, 'high': 8865000000, 'highTime': 1800000899}}
            self.replace_part(source, 1, 'latest', changed, blobs)
            with self.assertRaisesRegex(ValueError, 'crossed supported replay'):
                prepare.prepare(source, root / 'unsupported', 1)
            self.assertFalse((root / 'unsupported/preparation-receipt.json').exists())


if __name__ == '__main__':
    unittest.main()
