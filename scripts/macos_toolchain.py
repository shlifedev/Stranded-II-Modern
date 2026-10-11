"""Private ARM64 Blitz compiler and compatibility runtime; no player data."""
from pathlib import Path
import hashlib
import json
import os
import platform
import subprocess
import sys
import tarfile
import tempfile
import urllib.request

from web_toolchain import checkout, run

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / 'deps/blitz3d-macos'
ENGINE = SOURCE / 'blitz3d-ng'
BUILD = ROOT / 'build/macos'
TOOLS = BUILD / 'toolchain'


def config():
    return json.loads((ROOT / 'config/macos-toolchain.json').read_text('utf8'))


def require_host():
    if platform.system() != 'Darwin' or platform.machine() != 'arm64':
        raise ValueError('Native Mac builds currently require an Apple Silicon Mac.')


def environment_report():
    """Record the actual build tools, without machine-specific paths."""
    commands = {
        'macos': ['sw_vers', '-productVersion'], 'sdk': ['xcrun', '--sdk', 'macosx', '--show-sdk-version'],
        'clang': ['xcrun', 'clang', '--version'], 'cmake': ['cmake', '--version'],
        'ninja': ['ninja', '--version'], 'ffmpeg': ['ffmpeg', '-version'],
    }
    result = {'architecture': platform.machine(), 'python': platform.python_version()}
    for name, command in commands.items():
        result[name] = subprocess.check_output(command, text=True).splitlines()[0]
    return result


def apply_patches():
    """Resolve overlapping patches in a temporary index; preserve unknown edits."""
    patches = [p for group in ('blitz3d-web', 'blitz3d-macos')
               for p in sorted((ROOT / 'patches' / group).glob('*.patch'))]
    with tempfile.TemporaryDirectory(prefix='s2-native-patches-') as temporary:
        env = dict(os.environ, GIT_INDEX_FILE=str(Path(temporary) / 'index'))
        def git(*args, **kwargs):
            return subprocess.check_output(['git', *map(str, args)], cwd=SOURCE,
                                           env=env, **kwargs)
        git('read-tree', 'HEAD')
        allowed, expected = {}, {}
        for patch in patches:
            names = [line.split('\t', 2)[2] for line in
                     git('apply', '--numstat', patch).decode().splitlines()]
            for name in names:
                if name not in allowed:
                    original = subprocess.run(['git', 'show', ':' + name],
                                              cwd=SOURCE, env=env, capture_output=True)
                    allowed[name] = {original.stdout if original.returncode == 0 else None}
            git('apply', '--cached', patch)
            for name in names:
                expected[name] = git('show', ':' + name)
                allowed[name].add(expected[name])
        pending = []
        for name, content in expected.items():
            path = SOURCE / name
            if path.is_symlink() or not path.resolve().is_relative_to(SOURCE.resolve()):
                raise ValueError(f'Unsafe toolchain patch target: {path}')
            current = path.read_bytes() if path.exists() else None
            if current not in allowed[name]:
                raise ValueError(f'Preserving local toolchain edits: {path}. Reconcile them before building.')
            if current != content:
                pending.append((path, content))
        for path, content in pending:
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_bytes(content)


def dawn():
    return ROOT / 'deps/dawn-macos' / config()['dawn']['directory']


def prepare_dawn():
    cfg = config()['dawn']
    archive = ROOT / 'deps/dawn-macos.tar.gz'
    if not archive.is_file():
        candidate = archive.with_suffix('.download')
        with urllib.request.urlopen(cfg['url']) as source, candidate.open('wb') as output:
            import shutil
            shutil.copyfileobj(source, output)
        if hashlib.sha256(candidate.read_bytes()).hexdigest() != cfg['sha256']:
            raise ValueError('Dawn archive SHA-256 mismatch.')
        candidate.replace(archive)
    if hashlib.sha256(archive.read_bytes()).hexdigest() != cfg['sha256']:
        raise ValueError(f'Dawn archive SHA-256 mismatch: {archive}')
    if not (dawn() / 'lib/libwebgpu_dawn.a').is_file():
        with tarfile.open(archive) as source:
            source.extractall(archive.parent / 'dawn-macos', filter='data')


def prepare():
    require_host()
    cfg = config()
    checkout(cfg['repository'], cfg['commit'], SOURCE, cfg['engine_directory'])
    run([sys.executable, SOURCE / 'build.py', 'deps', 'llvm'], cwd=SOURCE)
    apply_patches()
    prepare_dawn()


def build(graphics=False):
    require_host()
    cfg = config()
    checkout(cfg['repository'], cfg['commit'], SOURCE, cfg['engine_directory'])
    apply_patches()
    sdk = subprocess.check_output(['xcrun', '--sdk', 'macosx', '--show-sdk-path'], text=True).strip()
    directory = BUILD / ('game-cmake' if graphics else 'cmake')
    output = BUILD / 'game-toolchain' if graphics else TOOLS
    options = (['-DS2_NATIVE_GAME=ON', '-DS2_CONSOLE_ONLY=OFF',
                '-DS2_MACOS_SERVICES=' + str(ROOT / 'src/native/macos/services.mm'),
                '-DDawn_DIR=' + str(dawn() / 'lib/cmake/Dawn'), '-DS2_DAWN_ROOT=' + str(dawn())]
               if graphics else ['-DS2_CONSOLE_ONLY=ON', '-DS2_NATIVE_GAME=OFF'])
    run(['cmake', '-G', 'Ninja', '-S', ENGINE, '-B', directory,
         '-DCMAKE_POLICY_VERSION_MINIMUM=3.5', '-DBB_PLATFORM=macos', '-DARCH=arm64',
         '-DBB_ENV=release', '-DBB_LEGACY32=ON', *options,
         '-DCMAKE_C_FLAGS=-Dfdopen=fdopen', '-DCMAKE_OSX_ARCHITECTURES=arm64',
         '-DCMAKE_OSX_DEPLOYMENT_TARGET=' + cfg['deployment_target'],
         '-DCMAKE_OSX_SYSROOT=' + sdk, '-DOUTPUT_PATH=' + str(output)])
    target = 'runtime.s2macos.static' if graphics else 'runtime.s2console.static'
    run(['cmake', '--build', directory, '--target', 'blitzcc', target,
         '-j', str(min(os.cpu_count() or 4, 8))])
    return output / 'bin/blitzcc'


if __name__ == '__main__':
    import argparse
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('command', choices=('prepare', 'build'))
    parser.add_argument('--graphics', action='store_true', help='Build the Metal runtime')
    args = parser.parse_args()
    try:
        build(args.graphics) if args.command == 'build' else prepare()
    except (ValueError, OSError, subprocess.CalledProcessError) as exc:
        parser.exit(1, f'Error: {exc}\n')
