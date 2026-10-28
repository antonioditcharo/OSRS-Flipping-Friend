import importlib.util
import json
import os
from pathlib import Path
import stat
import tempfile
import unittest
from unittest.mock import patch

spec = importlib.util.spec_from_file_location('configure_public_archive', Path(__file__).resolve().parents[1] / 'hosting/configure_public_archive.py')
configurer = importlib.util.module_from_spec(spec)
spec.loader.exec_module(configurer)


class ConfigurePublicArchiveTest(unittest.TestCase):
    def test_enable_disable_preserve_existing_scopes_owner_mode_and_exact_backup(self):
        with tempfile.TemporaryDirectory() as temp:
            path = Path(temp) / 'server.json'
            original = b'{"devices":[{"accounts":["synthetic"],"tokenSha256":"unchanged"}],"dataDirectory":"synthetic-only"}\n'
            path.write_bytes(original); path.chmod(0o640); before = path.stat()
            receipt = configurer.configure(path, True)
            after = path.stat(); value = json.loads(path.read_bytes())
            self.assertEqual(stat.S_IMODE(before.st_mode), stat.S_IMODE(after.st_mode))
            self.assertEqual((before.st_uid, before.st_gid), (after.st_uid, after.st_gid))
            self.assertEqual(json.loads(original)['devices'], value['devices'])
            self.assertEqual(original, (path.parent / receipt['backup']).read_bytes())
            self.assertEqual(stat.S_IMODE(before.st_mode), stat.S_IMODE((path.parent / receipt['backup']).stat().st_mode))
            self.assertTrue(receipt['changed']); self.assertTrue(value['publicArchive']['enabled'])
            same = path.read_bytes(); configurer.configure(path, False)
            self.assertFalse(json.loads(path.read_bytes())['publicArchive']['enabled'])
            self.assertNotEqual(same, path.read_bytes())

    def test_idempotent_flag_does_not_rewrite_file_or_create_another_backup(self):
        with tempfile.TemporaryDirectory() as temp:
            path = Path(temp) / 'server.json'; path.write_text('{}')
            configurer.configure(path, True); before = path.stat(); contents = path.read_bytes()
            receipt = configurer.configure(path, True)
            self.assertFalse(receipt['changed']); self.assertEqual(contents, path.read_bytes())
            self.assertEqual(before.st_mtime_ns, path.stat().st_mtime_ns)
            self.assertEqual(1, len(list(path.parent.glob('*.backup'))))

    def test_switch_preserves_custom_bounds_and_unknown_settings(self):
        with tempfile.TemporaryDirectory() as temp:
            path = Path(temp) / 'server.json'
            settings = {'enabled': True, 'durationSeconds': 3600, 'budgetBytes': 1024**2, 'seedItems': 3, 'futureOption': 'retain'}
            path.write_text(json.dumps({'publicArchive': settings}))
            configurer.configure(path, False)
            settings['enabled'] = False
            self.assertEqual(settings, json.loads(path.read_bytes())['publicArchive'])

    def test_failed_permission_preservation_cannot_replace_the_live_configuration(self):
        with tempfile.TemporaryDirectory() as temp:
            path = Path(temp) / 'server.json'; path.write_text('{}')
            with patch.object(configurer, '_metadata', side_effect=PermissionError('fixture')):
                with self.assertRaises(PermissionError):
                    configurer.configure(path, True)
            self.assertEqual(b'{}', path.read_bytes())

    def test_concurrent_configuration_edit_is_retained_instead_of_overwritten(self):
        with tempfile.TemporaryDirectory() as temp:
            path = Path(temp) / 'server.json'; path.write_text('{}')
            write = configurer._write_new
            def changed(target, value, original):
                write(target, value, original)
                if target.suffix == '.pending':
                    path.write_text('{"external":"newer"}')
            with patch.object(configurer, '_write_new', side_effect=changed):
                with self.assertRaisesRegex(ValueError, 'concurrently'):
                    configurer.configure(path, True)
            self.assertEqual({'external': 'newer'}, json.loads(path.read_bytes()))

    def test_duplicate_fields_and_excess_budget_are_refused_before_writing(self):
        with tempfile.TemporaryDirectory() as temp:
            path = Path(temp) / 'server.json'; path.write_text('{"devices":[],"devices":[1]}')
            with self.assertRaises(ValueError):
                configurer.configure(path, True)
            with self.assertRaises(ValueError):
                configurer.configure(path, True, budget=3 * 1024**3)
            self.assertEqual([path.name], [p.name for p in path.parent.iterdir()])
