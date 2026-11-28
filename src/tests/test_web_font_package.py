"""Mount a compacted font package using the pinned Emscripten filesystem."""
from pathlib import Path
from tempfile import TemporaryDirectory
from unittest import TestCase, mock
import os
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / 'src/devtools'))
import web_build
import web_toolchain


class WebFontPackage(TestCase):
    def test_duplicate_fonts_download_once_and_mount_as_independent_files(self):
        sdk = web_toolchain.paths()[1]
        env = web_toolchain.environment()
        node = next((sdk / 'node').glob('*/node.exe' if os.name == 'nt' else '*/bin/node'))
        with TemporaryDirectory() as directory:
            root = Path(directory)
            site = root / 'site'
            files = []
            for language, data in [('de', b'ABC'), ('en', b'ABC'), ('ja', b'XYZ'), ('ko', b'ABC')]:
                path = root / language / 'ui.ttf'
                path.parent.mkdir()
                path.write_bytes(data)
                files.append((path, f'/game/core/locales/{language}/ui.ttf'))
            with mock.patch.multiple(web_build, BUILD=root, SITE=site), \
                 mock.patch.object(web_build, 'asset_files', return_value=files), \
                 mock.patch('web_audio.prepare', return_value={}):
                web_build.pack_assets(env)
                self.assertEqual((site/'assets.data').stat().st_size, 6)
                files[-1][0].write_bytes(b'DEF')
                web_build.pack_assets(env)
                self.assertEqual((site/'assets.data').stat().st_size, 9)

            source = root/'probe.c'
            source.write_text('int main(void) { return 0; }', encoding='ascii')
            web_toolchain.run([sys.executable, sdk/'upstream/emscripten/emcc.py', source,
                '-sFORCE_FILESYSTEM=1', '-sENVIRONMENT=node',
                '-sEXPORTED_RUNTIME_METHODS=FS,FS_createPath,FS_createDataFile,addRunDependency,removeRunDependency',
                '--pre-js', ROOT/'src/tests/web_font_package.cjs', '--pre-js', site/'assets.js',
                '-o', site/'probe.js'], env=env)
            result = subprocess.run([str(node), 'probe.js'], cwd=site, capture_output=True, text=True, timeout=30)
            self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
            self.assertIn('Independent font files verified', result.stdout)
