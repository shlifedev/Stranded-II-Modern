"""Language font downloads must not install corrupt data or replace custom fonts."""
from pathlib import Path
import hashlib
import io
import json
import sys
import tempfile
import unittest
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'devtools'))
from bundled_fonts import prepare


class BundledFontTests(unittest.TestCase):
    def setUp(self):
        temporary = tempfile.TemporaryDirectory()
        self.addCleanup(temporary.cleanup)
        self.root = Path(temporary.name)
        (self.root / 'config').mkdir()
        (self.root / 'licenses').mkdir()
        self.font = b'verified font bytes'
        self.license = self.root / 'licenses/font.txt'
        self.license.write_bytes(b'font license')
        self.notice = self.root / 'licenses/notice.txt'
        self.notice.write_bytes(b'font attribution')
        self.relative = 'deps/fonts/Chinese.ttf'
        self.target = self.root / self.relative
        spec = {'url': 'https://example.invalid/pinned-font.ttf',
                'sha256': hashlib.sha256(self.font).hexdigest(),
                'license': 'licenses/font.txt',
                'license_sha256': hashlib.sha256(self.license.read_bytes()).hexdigest(),
                'notice': 'licenses/notice.txt',
                'notice_sha256': hashlib.sha256(self.notice.read_bytes()).hexdigest()}
        (self.root / 'config/dependencies.json').write_text(
            json.dumps({'language_fonts': {self.relative: spec}}))
        self.packs = {'zh-cn': {'name': '简体中文', 'font': self.relative}}

    def test_verified_download_and_offline_cache(self):
        with patch('bundled_fonts.urllib.request.urlopen', return_value=io.BytesIO(self.font)) as download:
            self.assertEqual(prepare(self.root, self.packs), [self.license, self.notice])
            download.assert_called_once()
        with patch('bundled_fonts.urllib.request.urlopen') as download:
            self.assertEqual(prepare(self.root, self.packs), [self.license, self.notice])
            download.assert_not_called()
        self.assertEqual(self.target.read_bytes(), self.font)

    def test_corrupt_download_never_installed(self):
        with patch('bundled_fonts.urllib.request.urlopen', return_value=io.BytesIO(b'truncated')):
            with self.assertRaisesRegex(ValueError, 'font checksum mismatch'):
                prepare(self.root, self.packs)
        self.assertFalse(self.target.exists())
        self.assertEqual(list(self.target.parent.iterdir()), [])

    def test_modified_cache_preserved_and_rejected(self):
        self.target.parent.mkdir(parents=True)
        self.target.write_bytes(b'local modification')
        with patch('bundled_fonts.urllib.request.urlopen') as download:
            with self.assertRaisesRegex(ValueError, 'font checksum mismatch'):
                prepare(self.root, self.packs)
            download.assert_not_called()
        self.assertEqual(self.target.read_bytes(), b'local modification')

    def test_license_checked_before_download(self):
        self.license.write_bytes(b'changed license')
        with patch('bundled_fonts.urllib.request.urlopen') as download:
            with self.assertRaisesRegex(ValueError, 'license checksum mismatch'):
                prepare(self.root, self.packs)
            download.assert_not_called()
        self.assertFalse(self.target.exists())

    def test_custom_fonts_do_not_trigger_bundled_downloads(self):
        self.packs['zh-cn']['font'] = 'deps/fonts/Custom.ttf'
        with patch('bundled_fonts.urllib.request.urlopen') as download:
            self.assertEqual(prepare(self.root, self.packs), [])
            download.assert_not_called()
        self.assertFalse(self.target.exists())
