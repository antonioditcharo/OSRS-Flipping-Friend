"""Exact-source disposable retention; required evidence is never inferred from names/ages."""
import json
import os
from pathlib import Path
import shutil
import tempfile
import unittest
from unittest.mock import patch
from types import SimpleNamespace

from tools import storage_maintenance as storage
from tools import verify_pilot_backup as backup


class StorageRetentionTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(prefix='storage retention ')
        self.addCleanup(self.temp.cleanup)
        self.base = Path(self.temp.name).resolve()
        self.root = self.base / 'local-data'
        self.manager = storage.Storage(self.root)
        self.source = self.base / 'original-backup'
        self.source.mkdir()
        (self.source / 'backup-verification.json').write_text('{"status":"VERIFIED"}', encoding='utf-8')
        for name in ('osrs-flipping-friend.jar', 'flipping-friend-companion.jar', 'installation.json'):
            (self.source / name).write_bytes(b'original runtime')
        (self.source / 'accounts').mkdir()
        (self.source / 'accounts' / 'original.db').write_bytes(b'original financial evidence')
        self.copy = self.root / 'run' / 'restored'
        self.copy.parent.mkdir()
        self.clock = 1000000

    def registered_copy(self):
        with patch.object(storage.time, 'time', return_value=self.clock):
            backup.verify(self.source, self.copy, managed_root=self.root)
        return next(self.manager.registry.glob('*.json'))

    def test_only_expired_verified_copy_is_removed_and_original_proof_survives(self):
        self.registered_copy()
        outside = self.base / 'live.db'; outside.write_bytes(b'never touch live state')
        frozen = self.root / 'legacy-unassigned-restored.db'; frozen.write_bytes(b'unregistered evidence')
        self.manager.maintain(now=self.clock + storage.RESTORE_LIFETIME - 1)
        self.assertTrue(self.copy.exists())
        result = self.manager.maintain(now=self.clock + storage.RESTORE_LIFETIME)
        self.assertGreater(result['reclaimedBytes'], 0)
        self.assertFalse(self.copy.exists())
        self.assertTrue(self.copy.with_name(self.copy.name + '.restore-verification.json').is_file())
        self.assertEqual(b'original financial evidence', (self.source / 'accounts' / 'original.db').read_bytes())
        self.assertEqual(b'never touch live state', outside.read_bytes())
        self.assertEqual(b'unregistered evidence', frozen.read_bytes())
        self.assertFalse(list(self.manager.registry.glob('*.json')))

    def test_new_or_modified_files_freeze_cleanup_instead_of_being_discarded(self):
        self.registered_copy()
        extra = self.copy / 'KEEP'; extra.write_bytes(b'investigation still active')
        result = self.manager.maintain(now=self.clock + storage.RESTORE_LIFETIME)
        self.assertTrue(result['refusals']); self.assertTrue(extra.is_file())
        extra.unlink()
        (self.copy / 'accounts' / 'original.db').write_bytes(b'new prospective evidence')
        result = self.manager.maintain(now=self.clock + storage.RESTORE_LIFETIME)
        self.assertTrue(result['refusals']); self.assertTrue(self.copy.exists())

    def test_missing_or_changed_original_backup_prevents_deletion(self):
        self.registered_copy()
        (self.source / 'accounts' / 'original.db').write_bytes(b'different source')
        result = self.manager.maintain(now=self.clock + storage.RESTORE_LIFETIME)
        self.assertTrue(result['refusals']); self.assertTrue(self.copy.exists())

    def test_external_receipt_changes_prevent_deletion(self):
        self.registered_copy()
        self.copy.with_name(self.copy.name + '.restore-verification.json').write_bytes(b'changed proof')
        result = self.manager.maintain(now=self.clock + storage.RESTORE_LIFETIME)
        self.assertTrue(result['refusals']); self.assertTrue(self.copy.exists())

    def test_clock_rollback_never_ages_out_a_copy(self):
        self.registered_copy()
        self.manager.maintain(now=self.clock - 1000000)
        self.assertTrue(self.copy.exists())

    def test_interrupted_expiration_resumes_only_the_preverified_remaining_files(self):
        self.registered_copy()
        real_unlink = Path.unlink
        count = [0]
        def interrupt(path, *args, **kwargs):
            if self.copy in path.parents:
                count[0] += 1
                if count[0] == 2: raise OSError('synthetic interruption')
            return real_unlink(path, *args, **kwargs)
        with patch.object(Path, 'unlink', interrupt):
            result = self.manager.maintain(now=self.clock + storage.RESTORE_LIFETIME)
        self.assertTrue(result['refusals'])
        self.assertTrue(self.copy.exists())
        result = self.manager.maintain(now=self.clock + storage.RESTORE_LIFETIME + 1)
        self.assertFalse(result['refusals']); self.assertFalse(self.copy.exists())
        self.assertTrue((self.source / 'accounts' / 'original.db').exists())

    def test_linked_copy_or_parent_refuses_without_touching_outside_data(self):
        self.registered_copy()
        outside = self.base / 'outside'; outside.mkdir(); (outside / 'sentinel').write_bytes(b'kept')
        link = self.copy / 'outside-link'
        try: link.symlink_to(outside, target_is_directory=True)
        except OSError: self.skipTest('Symbolic link privilege unavailable')
        with self.assertRaisesRegex(ValueError, 'Linked'):
            self.manager.maintain(now=self.clock + storage.RESTORE_LIFETIME)
        self.assertEqual(b'kept', (outside / 'sentinel').read_bytes())

    def test_traversal_or_overlapping_registrations_are_rejected(self):
        self.registered_copy()
        with self.assertRaisesRegex(ValueError, 'Overlapping'):
            self.manager.register(self.copy.parent, 'sealed-evidence', {})
        with self.assertRaises(ValueError):
            self.manager.register(self.root / '..' / 'outside', 'sealed-evidence', {})
        record = next(self.manager.registry.glob('*.json'))
        value = json.loads(record.read_text()); value['files']['../../outside'] = 'a' * 64
        record.write_text(json.dumps(value), encoding='utf-8')
        with self.assertRaises(ValueError): self.manager.maintain(now=self.clock + storage.RESTORE_LIFETIME)
        self.assertTrue(self.copy.exists())

    def test_required_evidence_is_not_expired_at_any_age(self):
        capture = self.root / 'frozen'; capture.mkdir()
        file = capture / 'financial.db'; file.write_bytes(b'original financial rows')
        self.manager.register(capture, 'sealed-evidence', {'financial.db': storage.digest(file)}, now=self.clock)
        result = self.manager.maintain(now=self.clock + 1000000000)
        self.assertFalse(result['refusals']); self.assertEqual(b'original financial rows', file.read_bytes())

    def test_low_disk_or_quota_refuses_optional_copy_before_creation(self):
        with patch.object(storage.shutil, 'disk_usage', return_value=SimpleNamespace(free=storage.FREE_RESERVE - 1)):
            with self.assertRaisesRegex(ValueError, 'storage budget'):
                backup.verify(self.source, self.copy, managed_root=self.root)
        self.assertFalse(self.copy.exists())
        with patch.object(storage, 'WORKSPACE_BUDGET', 1):
            with self.assertRaisesRegex(ValueError, 'storage budget'): self.manager.admit(10)

    def test_optional_capture_quota_preserves_the_failed_raw_capture(self):
        capture = self.root / 'capture'; capture.mkdir(); log = capture / 'raw.log'; log.write_bytes(b'0123456789')
        with patch.object(storage, 'RUN_BUDGET', 5):
            with self.assertRaisesRegex(ValueError, 'retain the failed capture'): storage.check_run_budget(capture)
        self.assertEqual(b'0123456789', log.read_bytes())

    def test_manager_cannot_run_two_cleanup_passes_at_once(self):
        with self.manager.lock():
            with self.assertRaises(OSError):
                with self.manager.lock(): pass

    @unittest.skipUnless(os.name == 'nt', 'NTFS compression is Windows-specific')
    def test_closed_evidence_is_compressed_in_place_with_unchanged_hashes(self):
        capture = self.root / 'completed-capture'; capture.mkdir()
        file = capture / 'original.log'; file.write_bytes(b'bounded private evidence\n' * 100000)
        storage.subprocess.run(['compact.exe', '/U', '/I', '/Q', str(file)], check=True, capture_output=True)
        sha = storage.digest(file)
        self.manager.register(capture, 'sealed-evidence', {'original.log': sha}, now=self.clock)
        result = self.manager.maintain(now=self.clock + storage.RESTORE_LIFETIME)
        self.assertFalse(result['refusals'])
        self.assertEqual(1, len(result['compressedFiles']))
        self.assertEqual(sha, storage.digest(file))
        self.assertGreater(result['reclaimedBytes'], 0)

    def test_reusing_an_external_receipt_or_a_control_path_refuses_before_copying(self):
        receipt = self.copy.with_name(self.copy.name + '.restore-verification.json')
        receipt.write_bytes(b'original proof')
        with patch.object(backup.shutil, 'copytree') as copy:
            with self.assertRaisesRegex(ValueError, 'receipt already exists'):
                backup.verify(self.source, self.copy, managed_root=self.root)
            copy.assert_not_called()
        receipt.unlink()
        with self.assertRaisesRegex(ValueError, 'control'):
            backup.verify(self.source, self.manager.control / 'restore', managed_root=self.root)
        self.assertFalse(self.copy.exists())


if __name__ == '__main__': unittest.main()
