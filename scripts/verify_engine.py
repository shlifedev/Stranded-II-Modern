"""Native ABI/reference checks and optional actual GPU rendering, in isolated files."""
from pathlib import Path
import argparse
import json
import subprocess
import tempfile
import blitz3d_toolchain as engine


def execute(exe, output, name):
    with (output / (name + '.log')).open('w', encoding='utf8') as log:
        subprocess.run([str(exe)], cwd=output, stdout=log, stderr=subprocess.STDOUT,
                       check=True, timeout=60)


def check_abi(output):
    output.mkdir(parents=True, exist_ok=True)
    root = engine.ROOT
    source = (root / 'tests/web_semantics.bb').read_text('utf8')
    marker = 'FreeBank bank\nCloseFile output'
    if source.count(marker) != 1:
        raise ValueError('The semantics fixture footer changed.')
    source = source.replace(marker, (root / 'tests/macos_abi.bb').read_text('utf8') + '\n' + marker)
    fixture = output / 'abi.bb'
    fixture.write_text(source, encoding='utf8')
    exe = output / 'abi.exe'
    engine.compile_test(fixture, exe, cwd=output, graphics=False)
    execute(exe, output, 'abi')
    expected = bytes.fromhex((root / 'tests/macos_abi.golden.hex').read_text('ascii'))
    if (output / 'semantics.bin').read_bytes() != expected:
        raise AssertionError('Native output differs from the original Blitz3D 1.118 reference.')
    for name in ('macos_lifetime', 'windows_platform'):
        exe = output / (name + '.exe')
        engine.compile_test(root / 'tests' / (name + '.bb'), exe, cwd=output, graphics=False)
        execute(exe, output, name)
    return {'original_1_118_bytes': len(expected), 'type_lifetime': 'pass',
            'unicode_files_and_native_handles': 'pass'}


def check_input(output):
    """Feed this process's SDL queue; never send OS input or create a window."""
    engine.build_native(True)
    directory = engine.BUILD / 'windows-game/cmake'
    engine.run(['cmake', '--build', directory, '--target', 's2_input_check', '-j', '8'],
               env=engine.environment())
    engine.install_windows_runtime(directory)
    execute(directory / 's2_input_check.exe', output, 'input')
    return 'SDL keyboard, console text, slow mouse counts and sub-degree rotation passed (no window)'


def check_render(output):
    """Use the production scene and shaders with an offscreen D3D12 device."""
    engine.build_native(True)
    directory = engine.BUILD / 'windows-game/cmake'
    engine.run(['cmake', '--build', directory, '--target', 's2_render_check', '-j', '8'],
               env=engine.environment())
    engine.install_windows_runtime(directory)
    execute(directory / 's2_render_check.exe', output, 'render')
    return 'D3D12 materials, ordered transparency, instancing, mesh versions, fade, depth, frustum, six-view capture and terrain normal checks passed (no window)'


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--graphics', action='store_true')
    parser.add_argument('--input', action='store_true', help='Native SDL queue test without a window')
    parser.add_argument('--render', action='store_true', help='Offscreen D3D12 scene/shader test without a window')
    args = parser.parse_args()
    engine.build_compiler()
    engine.build_native(False)
    output = Path(tempfile.mkdtemp(prefix='engine-check-', dir=engine.ROOT / 'build'))
    report = {'abi': check_abi(output), 'gpu': 'not_run'}
    if args.input:
        report['input'] = check_input(output)
    if args.render:
        report['offscreen_gpu'] = check_render(output)
    if args.graphics:
        engine.build_native(True)
        exe = output / 'graphics.exe'
        engine.compile_test(engine.ROOT / 'tests/macos_graphics.bb', exe, cwd=output)
        execute(exe, output, 'graphics')
        exe = output / 'graphics_lifetime.exe'
        engine.compile_test(engine.ROOT / 'tests/native_graphics_lifetime.bb', exe, cwd=output)
        execute(exe, output, 'graphics_lifetime')
        report['gpu'] = 'D3D12 mesh, overlay, pixel readback and context lifetime passed'
    (output / 'report.json').write_text(json.dumps(report, indent=2) + '\n', encoding='utf8')
    print(json.dumps(report, indent=2))
    print('Reports:', output)


if __name__ == '__main__':
    main()
