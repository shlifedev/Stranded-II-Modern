"""Check archive trust boundaries and exclusion of player data from releases."""
from pathlib import Path
from types import SimpleNamespace
from unittest.mock import patch
import contextlib
import importlib.util
import io
import json
import shutil
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
        self.put(self.root / 'config/modern.ini', b'fps=144\nlanguage=en\n')
        self.put(self.runtime / 'StrandedII.exe', b'MZbuilt executable')
        self.put(self.runtime / 'OpenAL32.dll', b'MZnative input bridge')
        self.put(self.root / 'src/native/qol_input.c', b'native source')
        self.engine_sources = lambda: {'src/native/qol_input.c': ci.digest((self.root / 'src/native/qol_input.c').read_bytes())}
        self.runtime_files = {'OpenAL32.dll': ci.digest(b'MZnative input bridge')}
        self.put(self.root / 'dep/OpenAL32.dll', b'MZnative input bridge')
        for patcher in (patch.object(ci.engine, 'sources', side_effect=self.engine_sources),
                        patch.object(ci.engine, 'windows_runtime_files', return_value={'OpenAL32.dll': self.root / 'dep/OpenAL32.dll'}),
                        patch.object(ci.engine, 'license_sources', return_value={})):
            patcher.start()
            self.addCleanup(patcher.stop)
        from versioning import make_info
        info = make_info('1.1.0', 'local')
        self.put(self.runtime / 'core/version.json', json.dumps(info).encode())
        self.put(self.runtime / 'core/version.txt', b'1.1.0-local\n')
        self.put(self.root / 'src/engine/StrandedII.bb', b'; fixture\n')
        self.put(self.root / 'build/build-report.json', json.dumps(dict(
            info, engine_sha256=self.engine_sources(), runtime_files=self.runtime_files, exe_sha256=ci.digest(b'MZbuilt executable'), source_sha256={'src/engine/StrandedII.bb':ci.digest(b'; fixture\n')})).encode())
        self.put(self.runtime / 'mods/Stranded II/sys/ko_aliases.dat', b'aliases')
        for code in ('en', 'ko'):
            self.put(self.root / f'locales/{code}.json',
                     json.dumps({'name': code}).encode())
            self.put(self.root / f'locales/{code}.tsv', b'ui.language\tLanguage\n')
            self.put(self.runtime / f'core/locales/{code}.tsv', b'ui.language\tLanguage\n')
            self.put(self.runtime / f'core/locales/{code}/glyphs.dat',
                     struct.pack('<4sIII', b'FNT2', 1, 20, 19) + bytes(28))
            self.put(self.runtime / f'core/locales/{code}/ui.ttf',b'UI font')
            self.put(self.runtime / f'core/locales/{code}/fallback.ttf',b'Unicode fallback font')
            for style in range(7):
                self.put(self.runtime / f'core/locales/{code}/{style}_0.bmp', b'BMfont')
        from language_pack import registry
        from locales import languages
        self.put(self.runtime / 'core/locales/languages.dat', registry(languages(self.root)))

    @staticmethod
    def put(path, data):
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_bytes(data)

    def assert_player_entrypoint(self, members, edition):
        from editions import Edition
        profile = Edition(edition)
        self.assertEqual(
            [name for name in members if Path(name).suffix.lower() == '.exe'],
            [f'{profile.archive_root}/{profile.executable}'])
        self.assertFalse(any(Path(name).suffix.lower() in ('.cmd', '.bat', '.ps1')
                             for name in members))
        readme = members[f'{profile.archive_root}/README.txt'].decode('utf8')
        self.assertIn(f'double-click {profile.executable}', readme)
        self.assertIn(f'{profile.executable}를 더블클릭', readme)
        self.assertNotIn('Play.cmd', readme)
        self.assertIn('Settings > Graphics', readme)
        self.assertIn('Settings > QOL', readme)
        build = json.loads(members[f'{profile.archive_root}/build-info.json'])
        self.assertEqual(build['runtime_files']['OpenAL32.dll'],
                         ci.digest(members[f'{profile.archive_root}/OpenAL32.dll']))
        self.assertEqual(build['engine_sha256'], self.engine_sources())


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
        values = dict(line.split('=', 1) for line in outputs.read_text('utf8').splitlines())
        self.assertTrue(Path(values['game']).is_absolute())
        self.assertTrue(Path(values['game']).samefile(destination / 'game'))
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
        for name in ('core/modern.ini', 'core/modern.ini.bak', 'core/modern.log', 'core/development.flag',
                     'preserved/private.txt', 'mods/Stranded II/saves/player.sav',
                     'mods/Stranded II/maps/custom.s2', 'mods/Stranded II/screen_00001.bmp',
                     'mods/Stranded II/sys/settings.cfg', 'core/locales/fonts-report.json',
                     'core/locales/en/preview.png', 'mods/Stranded II/sys/gfx/ko_0_0.bmp',
                     'Play.cmd', 'debug.bat', 'setup.ps1', 'StrandedII-old.exe'):
            self.put(self.runtime / name, b'private or obsolete')
        members = ci.release_members(self.runtime, self.game)
        self.assertEqual(members['StrandedII/core/modern.ini'], b'fps=144\nlanguage=en\n')
        self.assertNotIn('StrandedII/mods/Stranded II/sys/settings.cfg', members)
        self.assertEqual(members['StrandedII/mods/Stranded II/sys/controls.cfg'], b'original controls')
        self.assertNotIn('StrandedII/core/development.flag',members)
        self.assertEqual(members['StrandedII/core/locales/ko/ui.ttf'],b'UI font')
        self.assertEqual(members['StrandedII/core/locales/ko/fallback.ttf'],b'Unicode fallback font')
        self.assertEqual(members['StrandedII/licenses/LICENSE.txt'], b'original license\r\n')
        self.assert_player_entrypoint(members, 'classic')
        self.assertTrue(members['StrandedII/README.txt'].startswith(b'Stranded II\n'))
        self.assertNotIn('StrandedII/StrandedII-Extended.exe', members)
        self.assertFalse(any(data == b'private or obsolete' for data in members.values()))
        self.assertEqual((self.runtime / 'core/modern.ini').read_bytes(), b'private or obsolete')

    def test_incomplete_or_stale_language_pack_prevents_package_creation(self):
        output = self.root / 'release.zip'
        args = SimpleNamespace(runtime=self.runtime, game=self.game, output=output, version_info=None)
        self.put(self.runtime / 'core/locales/en.tsv', b'old catalog')
        with self.assertRaisesRegex(ValueError, 'stale'):
            ci.package(args)
        self.assertFalse(output.exists())
        self.put(self.runtime / 'core/locales/en.tsv', b'ui.language\tLanguage\n')
        (self.runtime / 'core/locales/en/6_0.bmp').unlink()
        with self.assertRaisesRegex(ValueError, 'Missing file'):
            ci.package(args)
        self.assertFalse(output.exists())

    def test_stale_version_source_or_executable_is_rejected(self):
        self.put(self.runtime / 'core/version.txt', b'1.1.99-main\n')
        with self.assertRaisesRegex(ValueError, 'version label'):
            ci.release_members(self.runtime, self.game)
        self.put(self.runtime / 'core/version.txt', b'1.1.0-local\n')
        self.put(self.root / 'src/engine/StrandedII.bb', b'changed source')
        with self.assertRaisesRegex(ValueError, 'build report is stale'):
            ci.release_members(self.runtime, self.game)
        self.put(self.root / 'src/engine/StrandedII.bb', b'; fixture\n')
        self.put(self.runtime / 'StrandedII.exe', b'MZold executable')
        with self.assertRaisesRegex(ValueError, 'build report is stale'):
            ci.release_members(self.runtime, self.game)

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

    def test_native_input_bridge_must_match_current_source_and_edition(self):
        for target, value, error in (
                (self.runtime / 'OpenAL32.dll', None, 'Missing'),
                (self.runtime / 'OpenAL32.dll', b'not a DLL', 'not a Windows DLL'),
                (self.runtime / 'OpenAL32.dll', b'MZoutdated', 'Native runtime build report is stale'),
                (self.root / 'src/native/qol_input.c', b'changed source', 'Embedded engine build report is stale')):
            with self.subTest(target=target, value=value):
                before = target.read_bytes()
                if value is None:
                    target.unlink()
                else:
                    target.write_bytes(value)
                with self.assertRaisesRegex(ValueError, error):
                    ci.release_members(self.runtime, self.game)
                target.write_bytes(before)
        report_path = self.root / 'build/build-report.json'
        report = json.loads(report_path.read_text('utf8'))
        report['engine_sha256'] = {}
        report_path.write_text(json.dumps(report), encoding='utf8')
        with self.assertRaisesRegex(ValueError, 'Embedded engine build report is stale'):
            ci.release_members(self.runtime, self.game)

    def test_extended_package_changes_leave_classic_package_unchanged(self):
        from editions import Edition, source_hashes
        from versioning import make_info
        baseline = ci.release_members(self.runtime, self.game)
        profile = Edition('extended')
        runtime = profile.runtime(self.root)
        shutil.copytree(self.runtime, runtime)
        self.put(runtime / profile.executable, b'MZbuilt executable')
        self.put(runtime / 'OpenAL32.dll', b'MZnative input bridge')
        self.put(self.root / profile.defaults, b'fps=60\nlanguage=ko\n')
        self.put(self.root / 'src/engine' / profile.entry, b'; extended entry\n')
        content = 'mods/Stranded II/sys/items_extended.inf'
        self.put(self.root / 'resources/editions/extended/overrides' / content, b'new content')
        self.put(runtime / content, b'new content')
        info = make_info('0.1.0', 'local', edition='extended')
        self.put(runtime / 'core/version.json', json.dumps(info).encode())
        self.put(runtime / 'core/version.txt', (info['display_version'] + '\n').encode())
        self.put(profile.build_dir(self.root) / 'build-report.json', json.dumps(dict(
            info, engine_sha256=self.engine_sources(), runtime_files=self.runtime_files, exe_sha256=ci.digest(b'MZbuilt executable'),
            source_sha256=source_hashes(self.root, 'extended'))).encode())
        members = ci.release_members(runtime, self.game, 'extended')
        self.assertIn('StrandedII-Extended/StrandedII-Extended.exe', members)
        self.assertEqual(members['StrandedII-Extended/OpenAL32.dll'], b'MZnative input bridge')
        self.assertEqual(baseline['StrandedII/OpenAL32.dll'], b'MZnative input bridge')
        self.assertNotIn('StrandedII-Extended/StrandedII.exe', members)
        self.assert_player_entrypoint(members, 'extended')
        self.assertTrue(members['StrandedII-Extended/README.txt'].startswith(b'Stranded II - Extended\n'))
        self.assertIn('StrandedII-Extended/' + content, members)
        self.assertEqual(members['StrandedII-Extended/core/modern.ini'], b'fps=60\nlanguage=ko\n')
        self.assertEqual(ci.release_members(self.runtime, self.game), baseline)
        with self.assertRaisesRegex(ValueError, 'another edition'):
            ci.release_members(runtime, self.game, 'classic')
        with self.assertRaisesRegex(ValueError, 'another edition'):
            ci.release_members(self.runtime, self.game, 'extended')
        self.put(runtime / content, b'outdated')
        with self.assertRaisesRegex(ValueError, 'override is stale'):
            ci.release_members(runtime, self.game, 'extended')


if __name__ == '__main__':
    unittest.main()
