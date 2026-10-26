"""Web target of the editable shared Blitz3D engine."""
from pathlib import Path
from types import SimpleNamespace
import blitz3d_toolchain as engine
import json
import os
import subprocess
import sys


ROOT = Path(__file__).resolve().parents[2]


def config():
    return json.loads((ROOT / 'config/web-toolchain.json').read_text('utf8'))


def run(argv, **kwargs):
    print(' '.join(map(str, argv)), flush=True)
    subprocess.run(list(map(str, argv)), check=True, **kwargs)


def checkout(url, commit, destination, sparse=None):
    if not destination.exists():
        run(['git', 'clone', '--filter=blob:none', '--no-checkout', url, destination])
        if sparse:
            run(['git', 'sparse-checkout', 'set', sparse], cwd=destination)
        run(['git', 'checkout', commit], cwd=destination)
    actual = subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=destination, text=True).strip()
    if actual != commit:
        raise ValueError(f'{destination} is at {actual}; expected pinned commit {commit}. Use another dependency directory.')


def paths():
    # Emscripten's Python and Node loaders must use the same SDK path. Node
    # resolves junctions/symlinks; leaving Python on the alias can prevent JS
    # libraries such as IDBFS from being recognized and included in the build.
    return engine.ENGINE, (ROOT / 'deps/emsdk').resolve()


def upstream():
    # Compatibility layout for the game/Studio build orchestrators.
    return SimpleNamespace(NG=engine.ENGINE, BLITZCC=engine.compiler(), RUNTIME_OUT=engine.output('web'))


def environment():
    env = engine.environment()
    _, sdk = paths()
    env['PATH'] = str(sdk / 'upstream/emscripten') + os.pathsep + str(sdk) + os.pathsep + env['PATH']
    env['EMSDK'] = str(sdk)
    env['EM_CONFIG'] = str(sdk / '.emscripten')
    env['EMSDK_PYTHON'] = sys.executable
    return env


def prepare():
    cfg = config()
    _, sdk = paths()
    checkout(cfg['emsdk_repository'], cfg['emsdk_commit'], sdk)
    run([sys.executable, sdk / 'emsdk.py', 'install', cfg['emscripten']], cwd=sdk)
    run([sys.executable, sdk / 'emsdk.py', 'activate', cfg['emscripten']], cwd=sdk)
    engine.prepare(graphics=False)
    from web_audio import tools
    tools()


def build():
    if not (paths()[1] / '.emscripten').is_file():
        raise ValueError('Run setup --platform web to prepare Emscripten.')
    engine.build_compiler()
    env = environment()
    engine.build_web(env)
    return env
