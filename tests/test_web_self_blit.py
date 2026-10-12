"""Run the patched C++ methods against a CPU command-order/pixel model."""
from pathlib import Path
from tempfile import TemporaryDirectory
from unittest import TestCase
import os
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / 'scripts'))
import web_toolchain


class WebSelfBlit(TestCase):
    def test_actual_self_blits_keep_pixels_and_batch_water_snapshots(self):
        source, sdk = web_toolchain.paths()
        directory = source / 'src/modules/bb/graphics.webgpu'
        cpp = (directory / 'canvas.webgpu.cpp').read_text('utf8')
        header = (directory / 'canvas.webgpu.h').read_text('utf8')
        methods = cpp[cpp.index('void WebGPUCanvas::releaseBlitSnapshot()'):
                      cpp.index('void WebGPUCanvas::blitScaled(')]
        fields = header[header.index('\tWGPUTexture blit_texture=0;'):
                        header.index('\n\tstd::vector<unsigned char> hit_mask;')]
        fixture = (ROOT / 'tests/web_self_blit.cpp').read_text('utf8')
        fixture = fixture.replace('// SNAPSHOT_FIELDS', fields).replace('// SELF_BLIT_METHODS', methods)
        with TemporaryDirectory(prefix='self-blit-', dir=ROOT / 'build/web') as temporary:
            output = Path(temporary)
            (output / 'probe.cpp').write_text(fixture, encoding='utf8')
            env = web_toolchain.environment()
            compile_result = subprocess.run([sys.executable, str(sdk / 'upstream/emscripten/emcc.py'),
                str(output / 'probe.cpp'), '-std=c++17', '-O2', '-sDEFAULT_TO_CXX', '-sASSERTIONS=1', '-sENVIRONMENT=node',
                '-sEXIT_RUNTIME=1', '-o', str(output / 'probe.js')], env=env, text=True, capture_output=True)
            self.assertEqual(compile_result.returncode, 0, compile_result.stdout + compile_result.stderr)
            node = next((sdk / 'node').glob('*/node.exe' if os.name == 'nt' else '*/bin/node'))
            result = subprocess.run([str(node), str(output / 'probe.js')], text=True, capture_output=True, timeout=30)
            self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
            self.assertIn('2 snapshots per water update', result.stdout)
