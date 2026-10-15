"""Disk image payload, mount cleanup, and a real macOS installation-copy round trip."""
from pathlib import Path
from tempfile import TemporaryDirectory
from unittest import TestCase, mock, skipIf, skipUnless
import os
import subprocess
import sys

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'scripts'))
import macos_dmg as dmg
from macos_validation import tree_records


@skipIf(sys.platform == 'win32', 'Unix symlink fixtures')
class DiskImages(TestCase):
    def setUp(self):
        self.temp = TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.stage = self.root / 'stage'
        self.app = self.stage / 'Stranded II.app'
        executable = self.app / 'Contents/MacOS/StrandedII'
        executable.parent.mkdir(parents=True)
        executable.write_bytes(b'fixture executable')
        executable.chmod(0o755)
        for name in ('README-en.txt', 'README-ko.txt', 'verification-summary.json'):
            (self.stage / name).write_text('한글 test\n', encoding='utf8')
        (self.stage / 'Applications').symlink_to('/Applications')

    def test_only_applications_shortcut_is_allowed(self):
        self.assertEqual(dmg.payload_records(self.stage)['Applications']['target'], '/Applications')
        (self.stage / 'Applications').unlink()
        (self.stage / 'Applications').symlink_to('/tmp')
        with self.assertRaisesRegex(ValueError, 'shortcut'):
            dmg.payload_records(self.stage)
        (self.stage / 'Applications').unlink()
        (self.stage / 'Applications').symlink_to('/Applications')
        (self.app / 'external').symlink_to('/tmp')
        with self.assertRaisesRegex(ValueError, 'Unsupported app member'):
            dmg.payload_records(self.stage)

    def test_extra_payload_and_missing_instructions_are_rejected(self):
        extra = self.stage / 'personal-save.sav'
        extra.write_bytes(b'private')
        with self.assertRaisesRegex(ValueError, 'unexpected'):
            dmg.payload_records(self.stage)
        extra.unlink()
        (self.stage / 'README-ko.txt').unlink()
        with self.assertRaisesRegex(ValueError, 'Missing'):
            dmg.payload_records(self.stage)

    def test_mount_always_detaches_after_validation_failure(self):
        calls = []
        with mock.patch.object(dmg.sys, 'platform', 'darwin'), \
             mock.patch.object(dmg.subprocess, 'run', side_effect=lambda args, **kw: calls.append(args)):
            with self.assertRaisesRegex(ValueError, 'fixture'):
                with dmg.mounted_image(self.root / 'test.dmg'):
                    raise ValueError('fixture validation failed')
        self.assertEqual([call[1] for call in calls], ['verify', 'attach', 'detach'])
        self.assertIn('-readonly', calls[1])
        self.assertIn('-nobrowse', calls[1])

    @skipUnless(sys.platform == 'darwin', 'hdiutil requires macOS')
    def test_real_dmg_preserves_payload_permissions_and_installation_copy(self):
        image = self.root / '한글 Image.dmg'
        expected = dmg.write_image(image, self.stage)
        with dmg.mounted_image(image) as mounted:
            self.assertTrue(os.statvfs(mounted).f_flag & os.ST_RDONLY)
            self.assertEqual(dmg.payload_records(mounted), expected)
            installed = self.root / '응용 프로그램 Test/Stranded II.app'
            subprocess.run(['ditto', str(mounted / self.app.name), str(installed)], check=True)
            self.assertEqual(tree_records(installed), tree_records(self.app))
        self.assertFalse(mounted.exists())


if __name__ == '__main__':
    import unittest
    unittest.main()
