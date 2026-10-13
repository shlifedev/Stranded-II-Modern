"""Build the editable in-repository Blitz compiler and platform runtimes."""
from pathlib import Path
import hashlib
import json
import os
import platform
import shutil
import subprocess
import sys
import tarfile
import tempfile
import urllib.request
import zipfile
from functools import lru_cache

ROOT = Path(__file__).resolve().parents[1]
ENGINE = ROOT / 'src/blitz3d'
DEPS = ROOT / 'deps/blitz3d'
BUILD = ROOT / 'build/engine'


def config():
    return json.loads((ROOT / 'config/blitz3d-toolchain.json').read_text('utf8'))


def host():
    if platform.system() == 'Windows':
        return 'windows'
    if platform.system() == 'Darwin' and platform.machine() == 'arm64':
        return 'macos'
    raise ValueError('Native toolchains require Windows x64 or Apple Silicon macOS.')


def run(argv, **kwargs):
    print(' '.join(map(str, argv)), flush=True)
    command = list(map(str, argv))
    command[0] = shutil.which(command[0], path=kwargs.get('env', os.environ).get('PATH')) or command[0]
    subprocess.run(command, check=True, **kwargs)


def digest(path):
    with path.open('rb') as source:
        return hashlib.file_digest(source, 'sha256').hexdigest()


def sources():
    """Local engine edits are inputs, never a reason to restore upstream files."""
    paths = [p for p in ENGINE.rglob('*') if p.is_file() and '__pycache__' not in p.parts
             and p.suffix not in ('.pyc', '.pyo')]
    paths += [ROOT / 'config/blitz3d-toolchain.json', ROOT / 'src/native/qol_input.c', Path(__file__)]
    return {p.relative_to(ROOT).as_posix(): digest(p) for p in sorted(paths)}


def library(name):
    return DEPS / config()['libraries'][name]['directory']


def fetch(spec, archive_name):
    """Fetch immutable dependencies only, never engine sources."""
    DEPS.mkdir(parents=True, exist_ok=True)
    archive = DEPS / archive_name
    if not archive.exists():
        pending = archive.with_suffix(archive.suffix + '.download')
        print('Downloading:', spec['url'], flush=True)
        with urllib.request.urlopen(spec['url'], timeout=120) as source, pending.open('wb') as output:
            shutil.copyfileobj(source, output)
        if digest(pending) != spec['sha256']:
            raise ValueError(f'Dependency checksum mismatch: {pending}')
        pending.replace(archive)
    if digest(archive) != spec['sha256']:
        raise ValueError(f'Dependency checksum mismatch: {archive}')
    destination = DEPS / spec['directory']
    if destination.is_dir():
        return destination
    with tempfile.TemporaryDirectory(prefix='.unpack-', dir=DEPS) as temporary:
        folder = Path(temporary)
        if archive.name.endswith('.zip'):
            with zipfile.ZipFile(archive) as source:
                for entry in source.infolist():
                    target = folder / entry.filename
                    if not target.resolve().is_relative_to(folder.resolve()):
                        raise ValueError('Unsafe dependency archive path')
                    source.extract(entry, folder)
                    mode = entry.external_attr >> 16
                    if mode and os.name != 'nt':
                        target.chmod(mode)
        else:
            with tarfile.open(archive) as source:
                source.extractall(folder, filter='data')
        if spec.get('flat'):
            # Move contents, keeping TemporaryDirectory's own cleanup root.
            extracted = folder / '.payload'
            extracted.mkdir()
            for item in list(folder.iterdir()):
                if item != extracted:
                    item.rename(extracted / item.name)
        else:
            extracted = folder / spec.get('archive_directory', spec['directory'])
        if not extracted.is_dir():
            raise ValueError(f'Dependency archive did not contain {spec["directory"]}')
        extracted.rename(destination)
    return destination


def llvm():
    destination = DEPS / ('llvm-' + host())
    if destination.is_dir():
        return destination
    # Reuse the already prepared SDK during migration, never its engine sources.
    old = ROOT / ('deps/blitz3d-web' if host() == 'windows' else 'deps/blitz3d-macos') / 'blitz3d-ng/llvm'
    if (old / 'lib').is_dir() and (old / 'include').is_dir():
        return old
    return destination


def prepare_llvm():
    if (llvm() / 'lib').is_dir() and (llvm() / 'include').is_dir():
        return
    spec = dict(config()['llvm'][host()], directory='llvm-' + host(), archive_directory='llvm')
    destination = fetch(spec, 'llvm-' + host() + '.zip')
    if not (destination / 'lib').is_dir() or not (destination / 'include').is_dir():
        raise ValueError(f'Incomplete LLVM SDK: {destination}')


def dawn():
    return DEPS / config()['dawn'][host()]['directory']


def openal():
    return DEPS / config()['openal']['directory']


def prepare(graphics=True):
    for name, spec in config()['libraries'].items():
        fetch(spec, name + '.tar.gz')
    prepare_llvm()
    if graphics:
        fetch(config()['dawn'][host()], 'dawn-' + host() + '.tar.gz')
        if host() == 'windows':
            fetch(config()['openal'], 'openal-soft.zip')
            fetch(config()['dxc'], 'dxc.zip')


@lru_cache(maxsize=1)
def environment():
    env = dict(os.environ)
    if host() == 'windows':
        vswhere = Path(env.get('ProgramFiles(x86)', r'C:\Program Files (x86)')) / 'Microsoft Visual Studio/Installer/vswhere.exe'
        install = subprocess.check_output([str(vswhere), '-latest', '-products', '*', '-requires',
            'Microsoft.VisualStudio.Component.VC.Tools.x86.x64', '-property', 'installationPath'], text=True).strip()
        if not install:
            raise ValueError('Install Visual Studio C++ x64 tools, DIA SDK and Windows SDK.')
        vcvars = Path(install) / 'VC/Auxiliary/Build/vcvars64.bat'
        output = subprocess.check_output(f'call "{vcvars}" >nul && set', shell=True, text=True, errors='replace')
        for line in output.splitlines():
            key, sep, value = line.partition('=')
            if sep and key:
                env[key.upper()] = value
        cmake = Path(install) / 'Common7/IDE/CommonExtensions/Microsoft/CMake'
        env['PATH'] = str(cmake / 'CMake/bin') + os.pathsep + str(cmake / 'Ninja') + os.pathsep + env['PATH']
    env['LLVM_ROOT'] = str(llvm())
    return env


def dependency_file():
    BUILD.mkdir(parents=True, exist_ok=True)
    lines = []
    for name in config()['libraries']:
        key = name.upper().replace('-', '_').replace('.', '_')
        lines.append(f'set(S2_DEP_{key} "{library(name).as_posix()}")')
    path = BUILD / 'dependencies.cmake'
    text = '\n'.join(lines) + '\n'
    if not path.exists() or path.read_text('utf8') != text:
        path.write_text(text, encoding='utf8', newline='\n')
    return path


def output(target):
    return BUILD / target / 'toolchain'


def compiler():
    return output('compiler-' + host()) / 'bin' / ('blitzcc64.exe' if host() == 'windows' else 'blitzcc')


def configure(target, options, env=None):
    env = env or environment()
    directory = BUILD / target / 'cmake'
    run(['cmake', '-G', 'Ninja', '-S', ENGINE, '-B', directory,
        '-DCMAKE_POLICY_VERSION_MINIMUM=3.5', '-DBB_ENV=release',
        '-DS2_DEPENDENCIES=' + str(dependency_file()), '-DOUTPUT_PATH=' + str(output(target)),
        '-DPython3_EXECUTABLE=' + sys.executable, *options], env=env)
    return directory


def build_web(env):
    sdk = ROOT / 'deps/emsdk'
    directory = configure('web', ['-DBB_PLATFORM=emscripten', '-DARCH=webgpu',
        '-DBB_WEBGPU=ON', '-DBB_LEGACY32=OFF',
        '-DCMAKE_TOOLCHAIN_FILE=' + str(sdk / 'upstream/emscripten/cmake/Modules/Platform/Emscripten.cmake')], env)
    run(['cmake', '--build', directory, '--target', 'runtime.opengl.static', 'runtime.test.static', '-j', '8'], env=env)
    return output('web')


def macos_options():
    cfg = json.loads((ROOT / 'config/macos-toolchain.json').read_text('utf8'))
    return ['-DCMAKE_OSX_ARCHITECTURES=arm64',
            '-DCMAKE_OSX_DEPLOYMENT_TARGET=' + cfg['deployment_target'],
            '-DCMAKE_OSX_SYSROOT=' + subprocess.check_output(
                ['xcrun', '--sdk', 'macosx', '--show-sdk-path'], text=True).strip()]


def build_compiler():
    options = ['-DS2_COMPILER_ONLY=ON', '-DBB_LEGACY32=ON',
        '-DBB_PLATFORM=' + ('win64' if host() == 'windows' else 'macos'),
        '-DARCH=' + ('x86_64' if host() == 'windows' else 'arm64')]
    if host() == 'macos':
        options += macos_options()
    directory = configure('compiler-' + host(), options)
    run(['cmake', '--build', directory, '--target', 'blitzcc', '-j', '8'], env=environment())
    return compiler()


def build_native(graphics=True):
    target = host() + ('-game' if graphics else '-console')
    options = ['-DBB_PLATFORM=' + ('win64' if host() == 'windows' else 'macos'),
        '-DARCH=' + ('x86_64' if host() == 'windows' else 'arm64'), '-DBB_LEGACY32=ON',
        '-DS2_NATIVE_GAME=' + ('ON' if graphics else 'OFF'),
        '-DS2_CONSOLE_ONLY=' + ('OFF' if graphics else 'ON')]
    if graphics:
        options += ['-DDawn_DIR=' + str(dawn() / 'lib/cmake/Dawn'), '-DS2_DAWN_ROOT=' + str(dawn())]
    if host() == 'windows':
        options += ['-DOPENAL_INCLUDE_DIR=' + str(openal() / 'include'),
                    '-DOPENAL_LIBRARY=' + str(openal() / 'libs/Win64/OpenAL32.lib')]
    else:
        options += macos_options() + [
            '-DS2_MACOS_SERVICES=' + str(ROOT / 'src/native/macos/services.mm')]
    directory = configure(target, options)
    runtime = 's2' + host() if graphics else 's2console'
    run(['cmake', '--build', directory, '--target', 'runtime.' + runtime + '.static', '-j', '8'], env=environment())
    return output(target)


def compile(source, destination, graphics=False, cwd=None, env=None):
    target = host() + ('-game' if graphics else '-console')
    runtime = 's2' + host() if graphics else 's2console'
    values = dict(env or environment(), blitzpath=str(output(target)))
    run([compiler(), '-r', runtime, '-o', destination, source], cwd=cwd, env=values)


def windows_runtime_files():
    """App-local DLLs, so a standalone build does not require an installer."""
    env = environment()
    redist = Path(env['VCTOOLSREDISTDIR']) / 'x64'
    folders = sorted(redist.glob('Microsoft.VC*.CRT'))
    if not folders:
        raise ValueError('The Visual C++ x64 redistributable files are missing.')
    result = {p.name: p for p in folders[-1].glob('*.dll')}
    result['OpenAL32.dll'] = openal() / 'bin/Win64/soft_oal.dll'
    for name in ('dxcompiler.dll', 'dxil.dll'):
        result[name] = DEPS / config()['dxc']['directory'] / 'bin/x64' / name
    return result


def license_sources():
    result = {'Blitz3D-NG.txt': ENGINE / 'LICENSE.TXT',
              'WebGPU-port-MIT.txt': ENGINE / 'WEBGPU-LICENSE.txt'}
    for name, relative, output_name in (
        ('imgui', 'LICENSE.txt', 'Dear-ImGui.txt'), ('sdl', 'LICENSE.txt', 'SDL2.txt'), ('freetype2', 'docs/FTL.TXT', 'FreeType.txt'),
        ('ogg', 'COPYING', 'Ogg.txt'), ('vorbis', 'COPYING', 'Vorbis.txt'),
        ('minimp3', 'LICENSE', 'minimp3.txt'), ('zlib', 'README', 'zlib.txt')):
        result[output_name] = library(name) / relative
    result.update({p.name: p for p in (ROOT / 'src/native/macos/licenses').glob('*.txt')})
    if host() == 'windows':
        result['OpenAL-Soft.txt'] = openal() / 'COPYING'
        result['OpenAL-pffft.txt'] = openal() / 'LICENSE-pffft'
        for p in (DEPS / config()['dxc']['directory']).glob('LICENSE*'):
            result['DXC-' + p.name] = p
    return result


def install_windows_runtime(destination):
    destination.mkdir(parents=True, exist_ok=True)
    files = windows_runtime_files()
    for name, source in files.items():
        target = destination / name
        if not target.exists() or digest(target) != digest(source):
            shutil.copy2(source, target)
    for name, source in license_sources().items():
        target = destination / 'licenses' / name
        target.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(source, target)
    return {name: digest(destination / name) for name in files}


def compile_test(source, destination, cwd=None, graphics=True):
    compile(source, destination, graphics=graphics, cwd=cwd)
    if graphics:
        install_windows_runtime(Path(destination).parent)


if __name__ == '__main__':
    import argparse
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('command', choices=('prepare', 'compiler', 'console', 'game'))
    args = parser.parse_args()
    if args.command == 'prepare':
        prepare()
    elif args.command == 'compiler':
        build_compiler()
    else:
        build_native(args.command == 'game')
