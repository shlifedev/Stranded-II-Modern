"""Run the real Blitz version-file loader without opening a graphics window."""
from pathlib import Path
import blitz3d_toolchain as engine
import argparse
import json
import os
import subprocess


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--blitz', type=Path, help=argparse.SUPPRESS)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    output = args.output.resolve()
    (output / 'core').mkdir(parents=True, exist_ok=True)
    root = Path(__file__).resolve().parents[1]
    body = 'Global set_rootdir$=SystemProperty("APPDIR")\n'
    body += (root / 'src/engine/includes/modern_version.bb').read_text('utf8')
    body += '''
Local report=WriteFile(set_rootdir$+"version-result.txt")
modern_version_load()
WriteLine report,modern_version$
CloseFile report
End
'''
    source = output / 'version-test.bb'
    source.write_text(body, encoding='utf8')
    exe = output / 'version-test.exe'
    engine.compile_test(source, exe, cwd=output)
    cases = [(None, 'development'), ('1.1.0-main\n', '1.1.0-main'),
             ('extended-0.1.0-local\n', 'extended-0.1.0-local'),
             ('extended-0.1.0-dev.1\n', 'extended-0.1.0-dev.1'),
             ('1.1.1-dev.2\n', '1.1.1-dev.2'), ('1.2.0-local\n', '1.2.0-local'),
             ('', 'development'), ('invalid label!\n', 'development'), ('1' * 129, 'development')]
    for value, expected in cases:
        path = output / 'core/version.txt'
        if value is None:
            if path.exists(): path.unlink()
        else:
            path.write_text(value, encoding='ascii')
        subprocess.run([str(exe)], cwd=output, check=True, timeout=15,
                       creationflags=subprocess.CREATE_NO_WINDOW)
        actual = (output / 'version-result.txt').read_text('utf8').strip()
        if actual != expected:
            raise ValueError(f'Version loader: {value!r} returned {actual!r}, expected {expected!r}')
    (output / 'report.json').write_text(json.dumps({'version_loader': 'pass', 'cases': len(cases)}) + '\n', encoding='utf8')
    print('Blitz product version loader passed:', len(cases), 'cases')


if __name__ == '__main__':
    main()
