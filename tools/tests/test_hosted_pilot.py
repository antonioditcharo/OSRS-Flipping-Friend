from contextlib import closing
import importlib.util
import json
from pathlib import Path
import sqlite3
import tempfile
import unittest
from unittest.mock import patch


ROOT = Path(__file__).resolve().parents[2]


def load(name, path):
    spec = importlib.util.spec_from_file_location(name, path)
    result = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(result)
    return result


pilot = load('pilot_hosting_tools', ROOT / 'tools/hosting/pilot.py')
backup = load('pilot_verify_backup', ROOT / 'tools/verify_pilot_backup.py')
hosted_backup = load('pilot_remote_backup', ROOT / 'tools/hosting/pilot-backup.py')


class HostedPilotToolsTest(unittest.TestCase):
    def test_quote_reserves_headroom_and_refuses_an_unapproved_price(self):
        with tempfile.TemporaryDirectory() as temporary:
            private = Path(temporary) / 'private'
            size = {'slug': 's-2vcpu-4gb', 'available': True, 'regions': ['sfo2'], 'price_monthly': 24}
            with patch.object(pilot, 'PRIVATE', private), patch.object(pilot, 'api', return_value={'sizes': [size]}) as api:
                self.assertEqual(36.2, pilot.quote()['baseMonthly'])
                self.assertEqual(1, api.call_count)
                size['price_monthly'] = 40
                with self.assertRaises(RuntimeError):
                    pilot.quote()
                self.assertEqual(2, api.call_count)

    def test_sqlite_snapshot_never_reverts_trades_recorded_after_the_snapshot(self):
        with tempfile.TemporaryDirectory() as temporary:
            source, frozen = Path(temporary) / 'live.db', Path(temporary) / 'frozen.db'
            with closing(sqlite3.connect(source)) as connection, connection:
                connection.execute('CREATE TABLE trades (id TEXT PRIMARY KEY, cost INTEGER)')
                connection.execute("INSERT INTO trades VALUES('original',101)")
            hosted_backup.snapshot(source, frozen)
            with closing(sqlite3.connect(source)) as connection, connection:
                connection.execute("INSERT INTO trades VALUES('new-fill',202)")
            with closing(sqlite3.connect(source)) as connection, connection:
                self.assertEqual(2, connection.execute('SELECT COUNT(*) FROM trades').fetchone()[0])
            with closing(sqlite3.connect(frozen)) as connection:
                self.assertEqual(1, connection.execute('SELECT COUNT(*) FROM trades').fetchone()[0])

    def test_backup_restore_verifies_bytes_and_never_overwrites_an_existing_destination(self):
        with tempfile.TemporaryDirectory() as temporary:
            source = Path(temporary) / 'backup'
            source.mkdir()
            (source / 'backup-verification.json').write_text(json.dumps({'status': 'VERIFIED'}))
            for name in ('osrs-flipping-friend.jar', 'flipping-friend-companion.jar', 'installation.json'):
                (source / name).write_text('preserved-' + name)
            destination = Path(temporary) / 'restore'
            result = backup.verify(source, destination)
            self.assertEqual('RESTORE_VERIFIED', result['status'])
            self.assertEqual(4, len(result['files']))
            with self.assertRaises(ValueError):
                backup.verify(source, destination)


if __name__ == '__main__':
    unittest.main()
