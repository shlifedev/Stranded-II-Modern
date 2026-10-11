"""Pinned, private Blitz3D NG / WebGPU tools. Never modifies the Windows toolchain."""
from pathlib import Path
import importlib.util
import json
import os
import subprocess
import sys


ROOT = Path(__file__).resolve().parents[1]


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
    return ROOT / 'deps/blitz3d-web', ROOT / 'deps/emsdk'


def upstream():
    source, _ = paths()
    spec = importlib.util.spec_from_file_location('s2_web_upstream', source / 'build.py')
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


def environment():
    """Discover installed MSVC/CMake without modifying the machine's PATH."""
    _, sdk = paths()
    env = dict(os.environ)
    if os.name == 'nt':
        vswhere = Path(env.get('ProgramFiles(x86)', r'C:\Program Files (x86)')) / 'Microsoft Visual Studio/Installer/vswhere.exe'
        if not vswhere.is_file():
            raise ValueError('Web builds need the MSVC C++ build tools and Windows SDK.')
        install = subprocess.check_output([str(vswhere), '-latest', '-products', '*', '-requires',
                                          'Microsoft.VisualStudio.Component.VC.Tools.x86.x64',
                                          '-property', 'installationPath'], text=True).strip()
        if not install:
            raise ValueError('Install the MSVC C++ build tools and Windows SDK.')
        vcvars = Path(install) / 'VC/Auxiliary/Build/vcvars64.bat'
        command = f'call "{vcvars}" >nul && set'
        output = subprocess.check_output(command, shell=True, text=True, errors='replace')
        for line in output.splitlines():
            key, separator, value = line.partition('=')
            if separator and key:
                env[key.upper()] = value
        toolroot = Path(install) / 'Common7/IDE/CommonExtensions/Microsoft/CMake'
        directories = [toolroot / 'CMake/bin', toolroot / 'Ninja']
    else:
        directories = []
    directories += [sdk / 'upstream/emscripten', sdk]
    env['PATH'] = os.pathsep.join(map(str, directories)) + os.pathsep + env.get('PATH', '')
    env['EMSDK'] = str(sdk)
    env['EM_CONFIG'] = str(sdk / '.emscripten')
    env['EMSDK_PYTHON'] = sys.executable
    return env


def apply_patches():
    source, _ = paths()
    for patch in sorted((ROOT / 'patches/blitz3d-web').glob('*.patch')):
        applied = subprocess.run(['git', 'apply', '--reverse', '--check', str(patch)],
                                 cwd=source, capture_output=True)
        if applied.returncode == 0:
            continue
        run(['git', 'apply', '--check', patch], cwd=source)
        run(['git', 'apply', patch], cwd=source)


def prepare():
    cfg = config()
    source, sdk = paths()
    checkout(cfg['repository'], cfg['commit'], source, cfg['engine_directory'])
    checkout(cfg['emsdk_repository'], cfg['emsdk_commit'], sdk)
    run([sys.executable, sdk / 'emsdk.py', 'install', cfg['emscripten']], cwd=sdk)
    run([sys.executable, sdk / 'emsdk.py', 'activate', cfg['emscripten']], cwd=sdk)
    # The pinned upstream helper fetches its pinned submodules and checks LLVM's
    # archive SHA-256. It only prepares this private dependency checkout.
    run([sys.executable, '-X', 'utf8', source / 'build.py', 'deps', 'llvm'], cwd=source)
    apply_patches()

    from web_audio import tools
    tools()


def build():
    source, sdk = paths()
    if not (sdk / '.emscripten').is_file():
        raise ValueError('Run setup --platform web to prepare the web SDK.')
    cfg = config()
    checkout(cfg['repository'], cfg['commit'], source, cfg['engine_directory'])
    checkout(cfg['emsdk_repository'], cfg['emsdk_commit'], sdk)
    apply_patches()
    env = environment()
    run([sys.executable, '-X', 'utf8', source / 'build.py', 'blitzcc', 'runtime'], cwd=source, env=env)
    return env
