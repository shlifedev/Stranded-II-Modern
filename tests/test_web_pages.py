"""Pages must contain exactly the checked build, never a local preview's extras."""
from contextlib import ExitStack
from pathlib import Path
from tempfile import TemporaryDirectory
from unittest import TestCase, mock
import os
import sys

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / 'scripts'))
import web_build
import web_pages
from versioning import make_info


class PagesPackage(TestCase):
    def setUp(self):
        self.stack = ExitStack()
        self.addCleanup(self.stack.close)
        self.build = Path(self.stack.enter_context(TemporaryDirectory()))
        self.site = self.build / 'site'
        self.site.mkdir()
        for name in web_build.SITE_FILES:
            (self.site / name).write_bytes(b'fixture')
        (self.site / 'index.html').write_text('<html lang="en"><script src="shell.js"></script></html>', encoding='utf8')
        web_build.write(self.site / 'version.json', make_info('1.1.0', 'local', 'a' * 40))
        (self.site / 'checks').mkdir()
        (self.site / 'checks/private.log').write_text('must not publish', encoding='utf8')
        (self.site / 'personal.sav').write_text('must not publish', encoding='utf8')
        self.stack.enter_context(mock.patch.multiple(web_build, BUILD=self.build, SITE=self.site))
        self.stack.enter_context(mock.patch.object(web_build, 'source_hashes', return_value={'game': 'source'}))
        self.stack.enter_context(mock.patch.object(web_build, 'implementation_hashes', return_value={'web': 'source'}))
        self.report = {'source_sha256': {'game': 'source'}, 'implementation_sha256': {'web': 'source'},
                       'files': {name: web_build.digest(self.site / name) for name in web_build.SITE_FILES}}
        self.write_reports()

    def write_reports(self):
        web_build.write(self.build / 'build-report.json', self.report)
        web_build.write(self.build / 'verification.json', {'web_check': 'passed', 'artifact_sha256': self.report['files']})

    def test_checked_files_only_and_consistent_cache_key(self):
        destination = web_pages.package()
        self.assertEqual({p.name for p in destination.iterdir()}, set(web_build.SITE_FILES))
        result = web_build.read(self.build / 'pages-report.json')
        self.assertTrue(result['cache_key'].startswith('a' * 40 + '-'))
        self.assertIn('shell.js?v=' + result['cache_key'], (destination / 'index.html').read_text('utf8'))
        for name in web_build.SITE_FILES:
            if name != 'index.html':
                self.assertEqual((destination / name).read_bytes(), (self.site / name).read_bytes())
        self.assertEqual(result['files'], {name: web_build.digest(destination / name) for name in web_build.SITE_FILES})
        self.assertIn('<script src="shell.js">', (self.site / 'index.html').read_text('utf8'))
        web_pages.package()
        self.assertEqual(result, web_build.read(self.build / 'pages-report.json'), 'packaging is repeatable')

    def test_rejects_failed_or_stale_verification(self):
        for verification in ({'web_check': 'failed', 'artifact_sha256': self.report['files']},
                             {'web_check': 'passed', 'artifact_sha256': {}}):
            web_build.write(self.build / 'verification.json', verification)
            with self.assertRaisesRegex(ValueError, 'check --platform web'):
                web_pages.package()
        self.assertFalse((self.build / 'pages').exists())

    def test_rejects_tampered_artifact_and_source(self):
        (self.site / 'game.wasm').write_bytes(b'changed')
        with self.assertRaisesRegex(ValueError, 'artifact changed'):
            web_pages.package()
        self.report['source_sha256'] = {'game': 'old source'}
        self.write_reports()
        with self.assertRaisesRegex(ValueError, 'stale'):
            web_pages.package()

    def test_manifest_cannot_add_player_data(self):
        self.report['files']['personal.sav'] = web_build.digest(self.site / 'personal.sav')
        self.write_reports()
        with self.assertRaisesRegex(ValueError, 'Unexpected files'):
            web_pages.package()

    def test_preserves_unexpected_destination_content(self):
        destination = self.build / 'pages'
        destination.mkdir()
        private = destination / 'personal.sav'
        private.write_bytes(b'user data')
        with self.assertRaisesRegex(ValueError, 'Unexpected Pages output'):
            web_pages.package()
        self.assertEqual(private.read_bytes(), b'user data')

    def test_rejects_existing_hardlinks_before_overwriting(self):
        destination = self.build / 'pages'
        destination.mkdir()
        os.link(self.site / 'game.js', destination / 'game.js')
        with self.assertRaisesRegex(ValueError, 'Unexpected Pages output'):
            web_pages.package()
        self.assertEqual((self.site / 'game.js').read_bytes(), b'fixture')
