"""Opt-in Blitz3D player collision tests in a disposable runtime.

Uses real terrain, rock models and collision/physics functions. No GUI input
is sent; synthetic time steps do not measure rendered FPS or visual smoothness.
"""
from pathlib import Path
import blitz3d_toolchain as engine
import argparse
import json
import os
import shutil
import subprocess
import tempfile

from editions import Edition, NAMES
from verify_timing import function


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--edition', choices=NAMES, default='classic')
    args = parser.parse_args()
    root = Path(__file__).resolve().parents[2]
    profile = Edition(args.edition)
    out = Path(tempfile.mkdtemp(prefix='collision-smoke-', dir=profile.build_dir(root)))
    runtime = out / 'runtime'
    shutil.copytree(profile.runtime(root), runtime,
                    ignore=shutil.ignore_patterns('preserved', 'saves', '*.log'))
    (runtime / 'mods/Stranded II/saves').mkdir()
    (runtime / 'core/modern.ini').write_text(
        'fps=144\ndisplay=windowed\nresolution=custom\n'
        'render_width=800\nrender_height=600\nlanguage=en\ndiagnostics=0\n',
        encoding='utf8')
    source = out / 'src'
    shutil.copytree(root / 'src/engine', source)
    timing = source / 'includes/modern_runtime.bb'
    text = timing.read_text('utf8')
    text = text.replace(function(text, 'modern_focus'),
                        'Function modern_focus()\n Return 0\nEnd Function')
    timing.write_text(text, encoding='utf8')
    init = source / 'includes/engine_init.bb'
    init.write_text(init.read_text('utf8').replace('\nunrealsoftware()\n', '\n'), encoding='utf8')
    shutil.copyfile(root / 'src/tests/collision_scenarios.bb', source / 'includes/engine_loop.bb')
    exe = runtime / 'CollisionSmoke.exe'
    print('Isolated runtime:', runtime, flush=True)
    engine.compile_test(source / profile.entry, exe, cwd=source)
    startup = subprocess.STARTUPINFO()
    startup.dwFlags |= subprocess.STARTF_USESHOWWINDOW
    startup.wShowWindow = 0
    subprocess.run([str(exe)], cwd=runtime, startupinfo=startup,
                   creationflags=subprocess.CREATE_NO_WINDOW, timeout=90, check=True)
    result = (runtime / 'collision-smoke.txt').read_text('utf8')
    passed = 'FAILURES 0' in result and '\nFAIL ' not in '\n' + result
    checks = sum(line.startswith('CHECK ') for line in result.splitlines())
    print(f'{checks} collision checks: {"PASS" if passed else "FAIL"}')
    print('Details:', runtime / 'collision-smoke.txt')
    (out / 'report.json').write_text(json.dumps({
        'edition': args.edition, 'actual_blitz_collision_tests': 'pass' if passed else 'fail',
        'checks': checks,
        'time_steps': ['60 Hz', '120 Hz', '144 Hz', '100 ms'],
        'visual_ui_and_reported_location': 'requires user verification',
    }, indent=2) + '\n', encoding='utf8')
    if not passed:
        print(result)
        raise AssertionError(f'Collision tests failed: {out}')


if __name__ == '__main__':
    main()
