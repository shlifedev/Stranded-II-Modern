"""Run the patched C++ methods against a CPU command-order/pixel model."""
from pathlib import Path
from tempfile import TemporaryDirectory
from unittest import TestCase
import os
import shutil
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / 'src/devtools'))
import web_toolchain


class WebSelfBlit(TestCase):
    def test_actual_self_blits_keep_pixels_and_batch_water_snapshots(self):
        source, sdk = web_toolchain.paths()
        directory = source / 'src/modules/bb/graphics.webgpu'
        cpp = (directory / 'canvas.webgpu.cpp').read_text('utf8')
        header = (directory / 'canvas.webgpu.h').read_text('utf8')
        methods = cpp[cpp.index('void WebGPUCanvas::releaseBlitSnapshot()'):
                      cpp.index('void WebGPUCanvas::blitScaled(')]
        if 'void WebGPUCanvas::applyAntialias(' in methods:
            start = methods.index('void WebGPUCanvas::applyAntialias(')
            end = methods.index('bool WebGPUCanvas::blitSelf(', start)
            methods = methods[:start] + methods[end:]
        fields = header[header.index('\tWGPUTexture blit_texture=0;'):
                        header.index('\n\tstd::vector<unsigned char> hit_mask;')]
        fixture = (ROOT / 'src/tests/web_self_blit.cpp').read_text('utf8')
        fixture = fixture.replace('// SNAPSHOT_FIELDS', fields).replace('// SELF_BLIT_METHODS', methods)
        with TemporaryDirectory(prefix='self-blit-') as temporary:
            output = Path(temporary)
            source_file = output / 'probe.cpp'
            source_file.write_text(fixture, encoding='utf8')
            emcc = sdk / 'upstream/emscripten/emcc.py'
            if emcc.is_file():
                js = output / 'probe.js'
                compiler = [sys.executable, str(emcc), str(source_file), '-std=c++17', '-O2',
                            '-sDEFAULT_TO_CXX', '-sASSERTIONS=1', '-sENVIRONMENT=node',
                            '-sEXIT_RUNTIME=1', '-o', str(js)]
                env = web_toolchain.environment()
                node = next((sdk / 'node').glob('*/node.exe' if os.name == 'nt' else '*/bin/node'))
                run_probe = [str(node), str(js)]
            else:
                executable = output / ('probe.exe' if os.name == 'nt' else 'probe')
                cxx = shutil.which('c++') or shutil.which('clang++') or shutil.which('g++')
                if cxx:
                    compiler = [cxx, '-std=c++17', '-O2', str(source_file), '-o', str(executable)]
                elif os.name == 'nt' and shutil.which('cl'):
                    compiler = ['cl', '/nologo', '/EHsc', '/std:c++17', '/O2', str(source_file),
                                '/Fe:' + str(executable)]
                else:
                    self.skipTest('The CPU self-blit fixture requires the web SDK or a native C++17 compiler')
                env = None
                run_probe = [str(executable)]

            compiled = subprocess.run(compiler, cwd=output, env=env, text=True, capture_output=True)
            self.assertEqual(compiled.returncode, 0, compiled.stdout + compiled.stderr)
            result = subprocess.run(run_probe, cwd=output, text=True, capture_output=True, timeout=30)
            self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
            self.assertIn('2 snapshots per water update', result.stdout)
