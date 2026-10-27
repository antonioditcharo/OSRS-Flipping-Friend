import importlib.util
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

spec = importlib.util.spec_from_file_location('collect_pricing_market', Path(__file__).resolve().parents[1] / 'collect_pricing_market.py')
capture = importlib.util.module_from_spec(spec)
spec.loader.exec_module(capture)


class PricingCaptureTest(unittest.TestCase):
    def feed(self):
        return {'observedAt': 1800, 'fiveMinuteStartedAt': 1500, 'hourlyStartedAt': 0,
                'latest': {'1': {'low': 100, 'lowTime': 1700}}, 'fiveMinute': {}, 'hourly': {}}

    def test_asynchronous_missing_and_old_sides_remain_original(self):
        value = self.feed()
        capture.validate(value, 1801)
        self.assertNotIn('high', value['latest']['1'])
        self.assertEqual(1700, value['latest']['1']['lowTime'])

    def test_future_wrong_clock_and_extra_private_fields_are_rejected(self):
        for change in ({'observedAt': 1802}, {'fiveMinuteStartedAt': 1600}, {'hourlyStartedAt': True}, {'accountId': 'private'}):
            value = self.feed()
            value.update(change)
            with self.assertRaises(ValueError):
                capture.validate(value, 1801)
        value = self.feed()
        value['latest']['1']['lowTime'] = 1900
        with self.assertRaises(ValueError):
            capture.validate(value, 1801)

    def test_capture_is_new_bounded_and_never_follows_redirects(self):
        with tempfile.TemporaryDirectory() as folder:
            with self.assertRaises(ValueError):
                capture.collect(Path(folder) / 'outside', 1, 60)
        connection = unittest.mock.MagicMock()
        connection.getresponse.return_value.status = 302
        with patch.object(capture.http.client, 'HTTPConnection', return_value=connection):
            with self.assertRaises(ValueError):
                capture.fetch('test-token')
        self.assertEqual(1, connection.request.call_count)
        connection.close.assert_called_once()


if __name__ == '__main__':
    unittest.main()
