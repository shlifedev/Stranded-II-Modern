"""Check archive trust boundaries and exclusion of player data from releases."""
from pathlib import Path
from types import SimpleNamespace
from unittest.mock import patch
import contextlib
import importlib.util
import io
import json
import stat
import struct
import sys
import tempfile
import unittest
import zipfile

SCRIPTS = Path(__file__).resolve().parents[1] / 'scripts'
sys.path.insert(0, str(SCRIPTS))
spec = importlib.util.spec_from_file_location('ci', SCRIPTS / 'ci.py')
ci = importlib.util.module_from_spec(spec)
spec.loader.exec_module(ci)


class CIPackagingTests(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory()
        self.addCleanup(self.directory.cleanup)
        self.root = Path(self.directory.name)
        self.root_patch = patch.object(ci, 'ROOT', self.root)
        self.root_patch.start()
        self.addCleanup(self.root_patch.stop)
        self.game = self.root / 'input/game'
        self.blitz = self.root / 'input/blitz'
        self.font = self.root / 'input/font.ttf'
        self.runtime = self.root / 'runtime'
        self.put(self.font, b'font')
        game_files = {'mods/Stranded II/gfx/tree.bmp': b'original game resource',
                      'mods/Stranded II/sys/controls.cfg': b'original controls'}
        blitz_files = {'bin/blitzcc.exe': b'MZcompiler', 'bin/runtime.dll': b'runtime'}
        for base, files in ((self.game, game_files), (self.blitz, blitz_files),
                            (self.runtime, game_files)):
            for name, data in files.items():
                self.put(base / name, data)
        for name, files in (('game-files.json', game_files),
                            ('toolchain-files.json', blitz_files)):
            self.put(self.root / 'config' / name,
                     json.dumps({p: ci.digest(data) for p, data in files.items()}).encode())
        self.put(self.root / 'config/dependencies.json', json.dumps({
            'font_sha256': ci.digest(b'font'), 'game_version': '1.0.0.1'}).encode())
        self.put(self.root / 'licenses/LICENSE.txt', b'original license\r\n')
        self.put(self.root / 'config/modern.ini', b'fps=144\nlanguage=ko\n')
        self.put(self.runtime / 'StrandedII.exe', b'MZbuilt executable')
        self.put(self.runtime / 'mods/Stranded II/sys/ko_aliases.dat', b'aliases')
        for code in ('en', 'ko'):
            self.put(self.root / f'locales/{code}.json',
                     json.dumps({'name': code}).encode())
            self.put(self.root / f'locales/{code}.tsv', b'ui.language\tLanguage\n')
            self.put(self.runtime / f'core/locales/{code}.tsv', b'ui.language\tLanguage\n')
            self.put(self.runtime / f'core/locales/{code}/glyphs.dat',
                     struct.pack('<4sIII', b'FNT2', 1, 20, 19) + bytes(28))
            for style in range(7):
                self.put(self.runtime / f'core/locales/{code}/{style}_0.bmp', b'BMfont')
        from language_pack import registry
        from locales import languages
        self.put(self.runtime / 'core/locales/languages.dat', registry(languages(self.root)))

    @staticmethod
    def put(path, data):
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_bytes(data)

    def bundle(self):
        archive = self.root / 'bundle.zip'
        checksum = ci.write_archive(archive, ci.dependency_members(
            self.game, self.blitz, self.font))
        self.put(self.root / 'config/ci-dependencies.json', json.dumps({
            'release': 'ci-deps-v1', 'asset': 'bundle.zip', 'sha256': checksum}).encode())
        return archive

    def test_bundle_is_repeatable_and_contains_only_validated_inputs(self):
        first = self.bundle()
        second = self.root / 'bundle-again.zip'
        ci.write_archive(second, ci.dependency_members(self.game, self.blitz, self.font))
        self.assertEqual(first.read_bytes(), second.read_bytes())
        with zipfile.ZipFile(first) as archive:
            self.assertIn('licenses/LICENSE.txt', archive.namelist())
            self.assertNotIn('game/StrandedII.exe', archive.namelist())
            self.assertEqual(archive.read('licenses/LICENSE.txt'), b'original license\r\n')

    def test_modified_input_is_rejected_before_bundle_creation(self):
        self.put(self.game / 'mods/Stranded II/gfx/tree.bmp', b'modified resource')
        with self.assertRaisesRegex(ValueError, 'SHA-256'):
            ci.dependency_members(self.game, self.blitz, self.font)

    def test_dependency_archive_is_verified_and_exports_paths(self):
        archive = self.bundle()
        destination = self.root / 'prepared'
        outputs = self.root / 'github-output'
        with contextlib.redirect_stdout(io.StringIO()):
            ci.dependencies(SimpleNamespace(archive=archive, destination=destination,
                                            github_output=outputs))
        self.assertEqual((destination / 'fonts/NotoSansKR.ttf').read_bytes(), b'font')
        self.assertIn(f'game={destination / "game"}\n', outputs.read_text('utf8'))
        with self.assertRaisesRegex(ValueError, 'new dependency directory'):
            ci.dependencies(SimpleNamespace(archive=archive, destination=destination,
                                            github_output=None))

    def test_corrupted_archive_is_rejected_before_extraction(self):
        archive = self.bundle()
        archive.write_bytes(archive.read_bytes() + b'tampered')
        destination = self.root / 'prepared'
        with self.assertRaisesRegex(ValueError, 'SHA-256'):
            ci.dependencies(SimpleNamespace(archive=archive, destination=destination,
                                            github_output=None))
        self.assertFalse(destination.exists())

    def test_archive_traversal_windows_aliases_and_links_are_rejected(self):
        for name in ('../escaped', 'game/../../escaped', r'game\..\escaped',
                     '/absolute', 'C:/absolute', 'game/file:stream', 'game/name. '):
            with self.subTest(name=name):
                archive = self.root / 'unsafe.zip'
                with zipfile.ZipFile(archive, 'w') as output:
                    output.writestr(name, b'bad')
                with self.assertRaisesRegex(ValueError, 'Unsafe'):
                    ci.extract_archive(archive, self.root / 'prepared')
        archive = self.root / 'symlink.zip'
        with zipfile.ZipFile(archive, 'w') as output:
            link = zipfile.ZipInfo('link')
            link.external_attr = (stat.S_IFLNK | 0o777) << 16
            output.writestr(link, '../escaped')
        with self.assertRaisesRegex(ValueError, 'Unsafe'):
            ci.extract_archive(archive, self.root / 'prepared')

    def test_case_insensitive_duplicate_is_rejected_before_writing(self):
        archive = self.root / 'duplicate.zip'
        with zipfile.ZipFile(archive, 'w') as output:
            output.writestr('game/FILE', b'first')
            output.writestr('game/file', b'second')
        destination = self.root / 'prepared'
        with self.assertRaisesRegex(ValueError, 'Duplicate'):
            ci.extract_archive(archive, destination)
        self.assertFalse(destination.exists())

    def test_release_uses_defaults_and_excludes_player_data_and_reports(self):
        self.put(self.runtime / 'mods/Stranded II/sys/controls.cfg', b'personal bindings')
        for name in ('core/modern.ini', 'core/modern.ini.bak', 'core/modern.log',
                     'preserved/private.txt', 'mods/Stranded II/saves/player.sav',
                     'mods/Stranded II/maps/custom.s2', 'mods/Stranded II/screen_00001.bmp',
                     'mods/Stranded II/sys/settings.cfg', 'core/locales/fonts-report.json',
                     'core/locales/en/preview.png', 'mods/Stranded II/sys/gfx/ko_0_0.bmp'):
            self.put(self.runtime / name, b'private or obsolete')
        members = ci.release_members(self.runtime, self.game)
        self.assertEqual(members['StrandedII/core/modern.ini'], b'fps=144\nlanguage=ko\n')
        self.assertEqual(members['StrandedII/mods/Stranded II/sys/controls.cfg'], b'original controls')
        self.assertEqual(members['StrandedII/licenses/LICENSE.txt'], b'original license\r\n')
        self.assertIn(b'cd /d "%~dp0"', members['StrandedII/Play.cmd'])
        self.assertFalse(any(data == b'private or obsolete' for data in members.values()))
        self.assertEqual((self.runtime / 'core/modern.ini').read_bytes(), b'private or obsolete')

    def test_incomplete_or_stale_language_pack_prevents_package_creation(self):
        output = self.root / 'release.zip'
        args = SimpleNamespace(runtime=self.runtime, game=self.game, output=output)
        self.put(self.runtime / 'core/locales/en.tsv', b'old catalog')
        with self.assertRaisesRegex(ValueError, 'stale'):
            ci.package(args)
        self.assertFalse(output.exists())
        self.put(self.runtime / 'core/locales/en.tsv', b'ui.language\tLanguage\n')
        (self.runtime / 'core/locales/en/6_0.bmp').unlink()
        with self.assertRaisesRegex(ValueError, 'Missing file'):
            ci.package(args)
        self.assertFalse(output.exists())

    def test_missing_licenses_or_truncated_font_metadata_cannot_be_packaged(self):
        license_path = self.root / 'licenses/LICENSE.txt'
        license_path.unlink()
        with self.assertRaisesRegex(ValueError, 'Missing licenses'):
            ci.release_members(self.runtime, self.game)
        self.put(license_path, b'original license\r\n')
        self.put(self.runtime / 'core/locales/en/glyphs.dat',
                 struct.pack('<4sIII', b'FNT2', 1, 20, 19))
        with self.assertRaisesRegex(ValueError, 'Invalid font metadata'):
            ci.release_members(self.runtime, self.game)


if __name__ == '__main__':
    unittest.main()
