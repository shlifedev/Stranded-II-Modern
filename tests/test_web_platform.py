"""Platform boundaries: prevent accidentally shipping or changing player data."""
from pathlib import Path
from tempfile import TemporaryDirectory
from unittest import TestCase, mock
import json
import sys

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / 'scripts'))
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

    def test_web_setup_rejects_a_windows_runtime_as_resource_input(self):
        with self.assertRaisesRegex(ValueError, 'official dependency'):
            web_build.prepare_assets({'game': str(ROOT / 'build/runtime'), 'font': 'unused'})
