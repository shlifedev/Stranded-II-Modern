"""Experimental native ARM64/Metal build in an isolated development runtime."""
from pathlib import Path
import hashlib
import json
import os
import plistlib
import shutil
import subprocess
import wave

from editions import source_hashes, override_files
from locales import prepare, validate, languages
from language_pack import refresh
from versioning import git, local_info, validate_info
import macos_toolchain

ROOT = Path(__file__).resolve().parents[1]
BUILD = ROOT / 'build/macos'
RUNTIME = BUILD / 'runtime'


def default_ini():
    return (ROOT / 'config/modern.ini').read_text('utf8').replace(
        'display=borderless', 'display=windowed').replace('resolution=desktop', 'resolution=custom')


def read(path):
    return json.loads(path.read_text('utf8'))


def write(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2) + '\n', encoding='utf8')


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def inputs():
    path = BUILD / 'dependencies.local.json'
    if not path.is_file():
        raise ValueError('Run setup --platform macos --game PATH --font PATH first.')
    return read(path)


def prepare_assets(settings):
    game, font = Path(settings['game']), Path(settings['font'])
    if game.resolve().is_relative_to((ROOT / 'build').resolve()):
        raise ValueError('Native assets must come from the official dependency, not a development runtime.')
    if digest(font) != read(ROOT / 'config/dependencies.json')['font_sha256']:
        raise ValueError('Unexpected font SHA-256.')
    files = read(ROOT / 'config/game-files.json')
    for relative, expected in files.items():
        if digest(game / relative) != expected:
            raise ValueError(f'Unexpected official resource: {relative}')
    validate(ROOT, 'classic')
    for relative in files:
        path = Path(relative)
        if path.suffix.lower() in ('.exe', '.dll') or 'saves' in path.parts:
            continue
        target = RUNTIME / path
        if target.exists() and (target.suffix == '.cfg' or target.name == 'settings.inf'):
            continue
        target.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(game / relative, target)
    (RUNTIME / 'mods/Stranded II/saves').mkdir(parents=True, exist_ok=True)
    (RUNTIME / 'core').mkdir(exist_ok=True)
    if not (RUNTIME / 'core/modern.ini').exists():
        # Keep this experiment windowed and bounded on high-resolution screens.
        (RUNTIME / 'core/modern.ini').write_text(default_ini(), encoding='utf8')
    _, report = prepare(ROOT, game, RUNTIME, 'classic')
    for relative, source in override_files(ROOT, 'classic').items():
        target = RUNTIME / relative
        target.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(source, target)
    report.update(refresh(ROOT, RUNTIME, font, 'classic'))
    report['converted_audio'] = prepare_audio(game, files)
    shutil.copytree(ROOT / 'licenses', RUNTIME / 'licenses', dirs_exist_ok=True)
    write(BUILD / 'setup-report.json', report)


def prepare_audio(game, files):
    from web_audio import supported_wav
    candidates = [name for name in files if Path(name).suffix.lower() == '.wav'
                  and not supported_wav(game / name)]
    ffmpeg, ffprobe = shutil.which('ffmpeg'), shutil.which('ffprobe')
    if candidates and (not ffmpeg or not ffprobe):
        raise ValueError('Native audio preparation requires ffmpeg and ffprobe on PATH.')
    records = {}
    for name in candidates:
        source, target = game / name, RUNTIME / name
        info = json.loads(subprocess.check_output([ffprobe, '-v', 'error', '-select_streams', 'a:0',
            '-show_entries', 'stream=sample_rate,channels', '-of', 'json', str(source)], text=True))['streams'][0]
        temporary = target.with_suffix('.wav.tmp')
        subprocess.run([ffmpeg, '-nostdin', '-hide_banner', '-loglevel', 'error', '-xerror', '-y',
            '-i', str(source), '-map', '0:a:0', '-map_metadata', '-1', '-fflags', '+bitexact',
            '-flags:a', '+bitexact', '-c:a', 'pcm_s16le', '-f', 'wav', str(temporary)], check=True)
        with wave.open(str(temporary), 'rb') as audio:
            if (audio.getframerate() != int(info['sample_rate']) or audio.getnchannels() != info['channels']
                    or audio.getsampwidth() != 2 or audio.getnframes() <= 0):
                raise ValueError(f'Invalid decoded native audio: {name}')
        if not supported_wav(temporary):
            raise ValueError(f'Unsupported decoded native audio: {name}')
        os.replace(temporary, target)
        records[name] = {'source_sha256': digest(source), 'runtime_sha256': digest(target)}
    write(BUILD / 'audio-report.json', records)
    return len(records)


def setup(args):
    macos_toolchain.require_host()
    previous = inputs() if (BUILD / 'dependencies.local.json').is_file() else {}
    settings = {name: str(getattr(args, name).resolve()) if getattr(args, name, None) else previous.get(name)
                for name in ('game', 'font')}
    if not all(settings.values()):
        raise ValueError('Native setup requires --game and --font on first use.')
    prepare_assets(settings)
    write(BUILD / 'dependencies.local.json', settings)
    macos_toolchain.prepare()
    print('Prepared native resources:', RUNTIME)


def localize(args):
    report = refresh(ROOT, RUNTIME, Path(inputs()['font']), 'classic')
    write(BUILD / 'localization-report.json', report)
    print('Refreshed native language packs:', RUNTIME / 'core/locales')


def compile_source(compiler, source, destination, runtime, cwd=None):
    destination.parent.mkdir(parents=True, exist_ok=True)
    env = dict(os.environ, blitzpath=str(compiler.parent.parent))
    cfg = macos_toolchain.config()
    target = cfg['architecture'] + '-apple-macosx' + cfg['deployment_target']
    macos_toolchain.run([compiler, '-r', runtime, '-target', target, '-o', destination, source],
                        cwd=cwd or ROOT / 'src/engine', env=env)


def license_sources():
    result = {}
    for folder in (ROOT / 'licenses', ROOT / 'src/native/macos/licenses'):
        result.update({p.relative_to(folder).as_posix(): p for p in folder.rglob('*') if p.is_file()})
    source, engine = macos_toolchain.SOURCE, macos_toolchain.ENGINE
    for path, name in [
        (source / 'LICENSE', 'WebGPU-port-MIT.txt'), (engine / 'LICENSE.TXT', 'Blitz3D-NG.txt'),
        (engine / 'deps/sdl/tree/LICENSE.txt', 'SDL2.txt'),
        (engine / 'deps/freetype2/tree/docs/FTL.TXT', 'FreeType.txt'),
        (engine / 'deps/ogg/tree/COPYING', 'Ogg.txt'), (engine / 'deps/vorbis/tree/COPYING', 'Vorbis.txt'),
        (engine / 'deps/minimp3/tree/LICENSE', 'minimp3.txt'), (engine / 'deps/zlib/tree/README', 'zlib.txt'),
    ]:
        result[name] = path
    return result


def install_licenses():
    for name, source in license_sources().items():
        target = RUNTIME / 'licenses' / name
        target.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(source, target)


def asset_names():
    """Explicit resource boundary shared by app packaging and native tests."""
    names = set(read(ROOT / 'config/game-files.json')) | set(override_files(ROOT, 'classic'))
    names.update(('core/modern.ini', 'core/version.json', 'core/version.txt',
                  'core/locales/languages.dat', 'mods/Stranded II/sys/ko_aliases.dat'))
    for code in languages(ROOT):
        names.add(f'core/locales/{code}.tsv')
        names.update(p.relative_to(RUNTIME).as_posix() for p in (RUNTIME / 'core/locales' / code).glob('*.bmp'))
        names.add(f'core/locales/{code}/glyphs.dat')
    names.update('licenses/' + name for name in license_sources())
    return sorted(name for name in names if Path(name).suffix.lower() not in
                  ('.exe', '.dll', '.sav', '.bak', '.tmp', '.log') and not any(
                      part.lower() in ('saves', 'preserved', 'screenshots') for part in Path(name).parts))


def asset_payload():
    """The app always ships official default preferences, never a player profile."""
    original = Path(inputs()['game'])
    official = read(ROOT / 'config/game-files.json')
    payload = {}
    for name in asset_names():
        relative = Path(name)
        source = RUNTIME / relative
        if name == 'core/modern.ini':
            payload[name] = default_ini().encode('utf8')
        else:
            if relative.suffix == '.cfg' or relative.name == 'settings.inf':
                source = original / relative
                if digest(source) != official.get(name):
                    raise ValueError(f'Official default preferences changed: {source}')
            if not source.is_file():
                raise ValueError(f'Missing packaged native asset: {source}')
            payload[name] = source.read_bytes()
    return payload


def package_app(executable, parent=None):
    payload = asset_payload()
    version = validate_info(json.loads(payload['core/version.json']))['version'].split('-')[0]
    hashes = {name: hashlib.sha256(data).hexdigest() for name, data in payload.items()}
    fingerprint = hashlib.sha256(json.dumps(hashes, sort_keys=True).encode()).hexdigest()
    recipe = digest(Path(__file__))[:8]
    directory = parent or BUILD / 'apps' / (digest(executable)[:12] + '-' + fingerprint[:8] + '-' + recipe)
    app = directory / 'Stranded II.app'
    if app.exists():
        if not (directory / 'package-report.json').is_file():
            raise ValueError(f'Preserving an existing, unrecognized app: {app}')
        previous = read(directory / 'package-report.json')
        if (previous.get('executable_sha256') != digest(executable) or
                previous.get('signed_executable_sha256') != digest(app / 'Contents/MacOS/StrandedII')):
            raise ValueError(f'Preserving a modified native app: {app}')
        subprocess.run(['codesign', '--verify', '--deep', '--strict', str(app)], check=True)
        return app
    macos = app / 'Contents/MacOS'
    resources = app / 'Contents/Resources'
    macos.mkdir(parents=True)
    resources.mkdir()
    shutil.copy2(executable, macos / 'StrandedII')
    for name, data in payload.items():
        destination = resources / 'game' / name
        destination.parent.mkdir(parents=True, exist_ok=True)
        destination.write_bytes(data)
    write(resources / 'native-assets.json', {'fingerprint': fingerprint, 'files': list(payload)})
    (app / 'Contents/Info.plist').write_bytes(plistlib.dumps({
        'CFBundleExecutable': 'StrandedII', 'CFBundleName': 'Stranded II',
        'CFBundleIdentifier': 'local.strandedii.modern.native-preview', 'CFBundlePackageType': 'APPL',
        'CFBundleVersion': version, 'CFBundleShortVersionString': version,
        'LSMinimumSystemVersion': macos_toolchain.config()['deployment_target'],
        'NSHighResolutionCapable': False, 'LSApplicationCategoryType': 'public.app-category.games',
    }))
    subprocess.run(['codesign', '--force', '--sign', '-', '--timestamp=none', str(app)], check=True)
    write(directory / 'package-report.json', {'app': str(app), 'executable_sha256': digest(executable),
          'signed_executable_sha256': digest(macos / 'StrandedII'),
          'asset_sha256': hashes, 'personal_data_included': False, 'signing': 'local ad hoc'})
    return app


def implementation_hashes():
    paths = [ROOT / name for name in (
        'config/macos-toolchain.json', 'config/modern.ini', 'config/dependencies.json',
        'config/game-files.json', 'scripts/macos_toolchain.py', 'scripts/macos_build.py',
        'scripts/locales.py', 'scripts/language_pack.py', 'scripts/web_audio.py',
        'scripts/editions.py', 'scripts/versioning.py')]
    paths += list((ROOT / 'patches/blitz3d-web').glob('*.patch'))
    paths += list((ROOT / 'patches/blitz3d-macos').glob('*.patch'))
    paths += [p for p in (ROOT / 'src/native/macos').rglob('*') if p.is_file()]
    return {p.relative_to(ROOT).as_posix(): digest(p) for p in sorted(paths)}


def build(args):
    inputs()
    validate(ROOT, 'classic')
    info = validate_info(read(args.version_info)) if args.version_info else local_info(ROOT, 'classic')
    if info['edition'] != 'classic':
        raise ValueError('Native version belongs to another edition.')
    if info['channel'] != 'local' and info['commit'] != git(ROOT, 'rev-parse', 'HEAD'):
        raise ValueError('Native release version belongs to another source commit.')
    compiler = macos_toolchain.build(graphics=True)
    candidate = RUNTIME / 'StrandedII.next'
    compile_source(compiler, 'StrandedIIMac.bb', candidate, 's2macos')
    destination = RUNTIME / 'StrandedII'
    os.replace(candidate, destination)
    write(RUNTIME / 'core/version.json', info)
    (RUNTIME / 'core/version.txt').write_text(info['display_version'] + '\n', encoding='ascii')
    install_licenses()
    app = package_app(destination)
    write(BUILD / 'build-report.json', dict(info, platform='macos', toolchain=macos_toolchain.config(),
          source_sha256=source_hashes(ROOT, 'classic', 'macos'), executable_sha256=digest(destination),
          implementation_sha256=implementation_hashes(), app=str(app),
          environment=macos_toolchain.environment_report()))
    print('Built native experiment:', destination)
    print('Local app:', app)


def run(args):
    macos_toolchain.require_host()
    report = BUILD / 'build-report.json'
    if not report.is_file():
        raise ValueError('Run build --platform macos first.')
    executable = Path(read(report)['app']) / 'Contents/MacOS/StrandedII'
    if not executable.is_file():
        raise ValueError('Run build --platform macos first.')
    options = args.game_args[1:] if args.game_args[:1] == ['--'] else args.game_args
    subprocess.Popen([str(executable), *options], cwd=RUNTIME,
                     env=dict(os.environ, S2_NATIVE_DATA_DIR=str(RUNTIME)))


def check(args):
    from verify_macos import main
    main(args.suite or 'full')
