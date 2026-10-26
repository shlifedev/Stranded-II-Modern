"""Platform boundaries: prevent accidentally shipping or changing player data."""
from pathlib import Path
from tempfile import TemporaryDirectory
from unittest import TestCase, mock
import hashlib
import json
import sys

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / 'src/devtools'))
from editions import source_files
import web_build


class WebBoundaries(TestCase):
    def test_default_build_keeps_both_windows_include_graphs(self):
        for edition in ('classic', 'extended'):
            default = source_files(ROOT, edition)
            self.assertEqual(default, source_files(ROOT, edition, 'windows'))
            self.assertFalse(any('/platforms/web/' in path for path in default))
        web = source_files(ROOT, 'classic', 'web')
        self.assertTrue(set(source_files(ROOT)).issubset(web))
        with self.assertRaises(ValueError):
            source_files(ROOT, 'extended', 'web')

    def test_package_only_known_content_and_language_assets(self):
        with TemporaryDirectory() as directory:
            root = Path(directory)
            runtime = root / 'runtime'
            files = ('mods/Stranded II/maps/adventure/map01.s2', 'core/modern.ini',
                     'mods/Stranded II/sys/controls.cfg', 'core/locales/ko/glyphs.dat',
                     'core/locales/ko/0_0.bmp', 'core/locales/ko.tsv', 'licenses/license.txt')
            private = ('mods/Stranded II/saves/personal.sav', 'preserved/old.cfg',
                       'mods/Stranded II/screen_001.bmp', 'core/modern.log',
                       'core/locales/fonts-report.json', 'personal.cfg', 'StrandedII.exe')
            for name in files + private:
                path = runtime / name
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_text('fixture', encoding='utf8')
            (root / 'config').mkdir()
            (root / 'config/game-files.json').write_text(json.dumps({files[0]: '', files[2]: ''}), encoding='utf8')
            with mock.patch.multiple(web_build, ROOT=root, RUNTIME=runtime), \
                 mock.patch.object(web_build, 'languages', return_value={'ko': {}}), \
                 mock.patch.object(web_build, 'override_files', return_value={}):
                packed = {target.removeprefix('/game/') for _, target in web_build.asset_files()}
            self.assertEqual(packed, set(files))
            self.assertFalse(packed.intersection(private))

    def test_web_setup_rejects_generated_resources_as_input(self):
        for relative in ('build/runtime', 'build/extended/runtime', 'build/web/runtime',
                         'build/web/package', 'build/ci/deps/game/preserved'):
            with self.subTest(relative=relative), self.assertRaisesRegex(ValueError, 'official dependency'):
                web_build.prepare_assets({'game': str(ROOT / relative), 'font': 'unused'})

    def test_web_setup_accepts_verified_ci_inputs_without_copying_player_data(self):
        with TemporaryDirectory() as directory:
            root = Path(directory)
            game = root / 'build/ci/deps/game'
            runtime = root / 'build/web/runtime'
            resource = 'mods/Stranded II/gfx/test.bmp'
            contents = b'official resource'
            (game / resource).parent.mkdir(parents=True)
            (game / resource).write_bytes(contents)
            private = ('mods/Stranded II/saves/personal.sav', 'core/modern.ini')
            for name in private:
                path = game / name
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_text('private settings and saves', encoding='utf8')
            font = root / 'font.ttf'
            font.write_bytes(b'official font')
            (root / 'config').mkdir()
            (root / 'config/dependencies.json').write_text(json.dumps({'font_sha256': web_build.digest(font)}))
            (root / 'config/game-files.json').write_text(json.dumps({resource: hashlib.sha256(contents).hexdigest()}))
            (root / 'config/modern.ini').write_text('language=en\n')
            (root / 'licenses').mkdir()
            settings = {'game': str(game), 'font': str(font)}
            with mock.patch.multiple(web_build, ROOT=root, BUILD=root / 'build/web', RUNTIME=runtime), \
                 mock.patch.object(web_build, 'validate'), \
                 mock.patch.object(web_build, 'prepare', return_value=(None, {})), \
                 mock.patch.object(web_build, 'refresh', return_value={}), \
                 mock.patch.object(web_build, 'override_files', return_value={}):
                web_build.prepare_assets(settings)
                self.assertEqual((runtime / resource).read_bytes(), contents)
                self.assertFalse((runtime / private[0]).exists())
                self.assertEqual((runtime / 'core/modern.ini').read_text(), 'language=en\n')
                (game / resource).write_bytes(b'changed resource')
                with self.assertRaisesRegex(ValueError, 'Unexpected official resource'):
                    web_build.prepare_assets(settings)
                self.assertEqual((runtime / resource).read_bytes(), contents)
                (game / resource).write_bytes(contents)
                font.write_bytes(b'changed font')
                with self.assertRaisesRegex(ValueError, 'Unexpected font SHA-256'):
                    web_build.prepare_assets(settings)
