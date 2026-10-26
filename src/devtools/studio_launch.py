"""Prepare dependencies and the Extended engine before launching Studio."""
from pathlib import Path
import argparse
import importlib.metadata
import json
import os
import shutil
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[2]


def run(arguments, **kwargs):
    print('>', ' '.join(map(str, arguments)), flush=True)
    return subprocess.run(list(map(str, arguments)), cwd=ROOT, check=True, **kwargs)


def python_environment(arguments):
    if sys.version_info < (3, 12):
        raise ValueError('Studio requires Python 3.12 or newer.')
    environment = ROOT / '.venv'
    if os.environ.get('S2_STUDIO_PYTHON') or Path(sys.prefix).resolve() == environment.resolve():
        return None
    python = environment / ('Scripts/python.exe' if os.name == 'nt' else 'bin/python')
    if not python.is_file():
        run([sys.executable, '-m', 'venv', environment])
    env = dict(os.environ, S2_STUDIO_PYTHON=str(python))
    return run([python, '-X', 'utf8', Path(__file__), *arguments], env=env).returncode


def python_dependencies():
    requirements = ROOT / 'requirements.txt'
    for line in requirements.read_text('utf8').splitlines():
        if not line.strip() or line.startswith('#'):
            continue
        name, version = line.split('==', 1)
        try:
            installed = importlib.metadata.version(name)
        except importlib.metadata.PackageNotFoundError:
            installed = None
        if installed != version:
            run([sys.executable, '-m', 'pip', 'install', '--disable-pip-version-check', '-r', requirements])
            break
    # Windows builds discover CMake/Ninja in the installed MSVC tools.
    if os.name != 'nt':
        missing = [name for name in ('cmake', 'ninja') if not shutil.which(name)]
        if missing:
            run([sys.executable, '-m', 'pip', 'install', '--disable-pip-version-check', *missing])


def node_dependencies_ready():
    studio = ROOT / 'src/studio'
    suffix = '.cmd' if os.name == 'nt' else ''
    if not all((studio / 'node_modules/.bin' / (name + suffix)).is_file()
               for name in ('electron', 'tsc', 'vite')):
        return False
    try:
        package = json.loads((studio / 'package.json').read_text('utf8'))
        wanted = json.loads((studio / 'package-lock.json').read_text('utf8'))['packages']
        installed = json.loads((studio / 'node_modules/.package-lock.json').read_text('utf8'))['packages']
        for key in ('dependencies', 'devDependencies'):
            if package.get(key, {}) != wanted[''].get(key, {}):
                return False
        for path, entry in wanted.items():
            if not path or (entry.get('optional') and path not in installed):
                continue
            if path not in installed or any(entry.get(key) != installed[path].get(key)
                                            for key in ('version', 'resolved', 'integrity')):
                return False
        electron = studio / 'node_modules/electron'
        return (electron / 'dist' / (electron / 'path.txt').read_text('utf8').strip()).is_file()
    except (OSError, ValueError, KeyError):
        return False


def resource_inputs(game=None, font=None):
    saved = []
    for name in ('build/studio/dependencies.local.json', 'build/web/dependencies.local.json',
                 'build/dependencies.local.json'):
        path = ROOT / name
        if path.is_file():
            saved.append(json.loads(path.read_text('utf8')))
    defaults = {'game': ROOT / 'deps/game', 'font': ROOT / 'deps/fonts/NotoSansKR[wght].ttf'}
    result = {}
    for key, explicit in (('game', game), ('font', font)):
        candidates = [explicit] if explicit is not None else [s[key] for s in saved if s.get(key)] + [defaults[key]]
        for candidate in candidates:
            path = Path(candidate)
            if not path.is_absolute():
                path = ROOT / path
            exists = path.is_dir() if key == 'game' else path.is_file()
            if exists:
                result[key] = path.resolve()
                break
        else:
            raise ValueError(f'Missing {key} input. Place it at {defaults[key]} or pass --{key} PATH to the launcher.')
    return result


def prepare(npm, inputs):
    python_dependencies()
    if not node_dependencies_ready():
        run([npm, '--prefix', 'src/studio', 'ci'])
    cli = [sys.executable, '-X', 'utf8', ROOT / 'src/devtools/studio.py', 'engine']
    # Refresh resources and rebuild on every launch, so changed catalogs, assets,
    # engine sources and partially removed build output are all picked up.
    run([*cli, 'setup', '--game', inputs['game'], '--font', inputs['font']])
    run([*cli, 'build'])
    run([*cli, 'check'])


def main(arguments=None):
    arguments = sys.argv[1:] if arguments is None else arguments
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--game', type=Path, help='Official game resources; defaults to saved setup paths or deps/game')
    parser.add_argument('--font', type=Path, help='Pinned font; defaults to saved setup paths or deps/fonts')
    parser.add_argument('--prepare-only', action='store_true', help='Prepare and build everything without opening the app')
    parser.add_argument('app_args', nargs=argparse.REMAINDER)
    args = parser.parse_args(arguments)
    try:
        result = python_environment(arguments)
        if result is not None:
            return result
        os.environ['S2_STUDIO_PYTHON'] = sys.executable
        os.environ['PATH'] = str(Path(sys.executable).parent) + os.pathsep + os.environ.get('PATH', '')
        node, npm = shutil.which('node'), shutil.which('npm.cmd' if os.name == 'nt' else 'npm')
        if not node or not npm:
            raise ValueError('Studio requires Node.js 22.12 or newer with npm on PATH.')
        version = subprocess.check_output([node, '--version'], text=True).strip().lstrip('v')
        if tuple(map(int, version.split('.')[:2])) < (22, 12):
            raise ValueError('Studio requires Node.js 22.12 or newer.')
        prepare(npm, resource_inputs(args.game, args.font))
        if args.prepare_only:
            run([npm, '--prefix', 'src/studio', 'run', 'build'])
            print('Studio is ready. Engine verification and UI build passed.', flush=True)
        else:
            app_args = args.app_args[1:] if args.app_args[:1] == ['--'] else args.app_args
            run([npm, '--prefix', 'src/studio', 'start', '--', *app_args])
        return 0
    except subprocess.CalledProcessError as exc:
        print(f'Studio stopped: a preparation or launch command failed (exit {exc.returncode}).', file=sys.stderr)
        return exc.returncode
    except (OSError, ValueError) as exc:
        print(f'Studio could not start: {exc}', file=sys.stderr)
        return 1


if __name__ == '__main__':
    raise SystemExit(main())
