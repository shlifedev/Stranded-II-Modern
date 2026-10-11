"""Local WebGPU preview; assets are prepared from official inputs, never player data."""
from pathlib import Path
from http.server import SimpleHTTPRequestHandler, ThreadingHTTPServer
import functools
import hashlib
import json
import shutil
import subprocess
import sys

from editions import source_hashes, override_files
from locales import prepare, validate, languages
from language_pack import refresh
from versioning import local_info, validate_info
import web_toolchain

ROOT = Path(__file__).resolve().parents[1]
BUILD = ROOT / 'build/web'
RUNTIME = BUILD / 'runtime'
SITE = BUILD / 'site'
ENTRY = 'StrandedIIWeb.bb'
PORT = 8765
SITE_FILES = ('game.js', 'game.wasm', 'assets.js', 'assets.data',
              'shell.js', 'index.html', 'defaults.json', 'version.json')


def read(path):
    return json.loads(path.read_text('utf8'))


def write(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2) + '\n', encoding='utf8')


def digest(path):
    result = hashlib.sha256()
    with path.open('rb') as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b''):
            result.update(block)
    return result.hexdigest()


def inputs():
    path = BUILD / 'dependencies.local.json'
    if not path.is_file():
        raise ValueError('Run setup --platform web first.')
    return read(path)


def prepare_assets(settings):
    game, font = Path(settings['game']), Path(settings['font'])
    if game.resolve().is_relative_to((ROOT / 'build').resolve()):
        raise ValueError('Web assets must come from the official dependency, not a development runtime.')
    dependencies = read(ROOT / 'config/dependencies.json')
    if digest(font) != dependencies['font_sha256']:
        raise ValueError('Unexpected font SHA-256.')
    files = read(ROOT / 'config/game-files.json')
    for relative, expected in files.items():
        path = game / relative
        if digest(path) != expected:
            raise ValueError(f'Unexpected official resource: {relative}')
    validate(ROOT, 'classic')
    for relative in files:
        target = RUNTIME / relative
        target.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(game / relative, target)
    (RUNTIME / 'mods/Stranded II/saves').mkdir(exist_ok=True)
    (RUNTIME / 'core').mkdir(exist_ok=True)
    shutil.copy2(ROOT / 'config/modern.ini', RUNTIME / 'core/modern.ini')
    _, report = prepare(ROOT, game, RUNTIME, 'classic')
    for relative, source in override_files(ROOT, 'classic').items():
        target = RUNTIME / relative
        target.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(source, target)
    report.update(refresh(ROOT, RUNTIME, font, 'classic'))
    shutil.copytree(ROOT / 'licenses', RUNTIME / 'licenses', dirs_exist_ok=True)
    write(BUILD / 'setup-report.json', report)
    return report


def setup(args):
    previous = read(ROOT / 'build/dependencies.local.json') if (ROOT / 'build/dependencies.local.json').exists() else {}
    if (BUILD / 'dependencies.local.json').exists():
        previous.update(inputs())
    settings = {name: str(getattr(args, name).resolve()) if getattr(args, name, None) else previous.get(name)
                for name in ('game', 'font')}
    if not all(settings.values()):
        raise ValueError('Web setup requires --game and --font on first use.')
    BUILD.mkdir(parents=True, exist_ok=True)
    prepare_assets(settings)
    write(BUILD / 'dependencies.local.json', settings)
    web_toolchain.prepare()
    print('Prepared web assets:', RUNTIME)


def localize(args):
    report = refresh(ROOT, RUNTIME, Path(inputs()['font']), 'classic')
    write(BUILD / 'localization-report.json', report)
    print('Refreshed web language packs; run build --platform web to repack.')


def asset_files():
    """Allowlist official/prepared assets; never enumerate player-data folders."""
    names = set(read(ROOT / 'config/game-files.json'))
    names.update(override_files(ROOT, 'classic'))
    names.update(('core/modern.ini', 'core/version.json', 'core/version.txt', 'core/locales/languages.dat'))
    for code in languages(ROOT):
        names.add(f'core/locales/{code}.tsv')
        directory = RUNTIME / 'core/locales' / code
        names.update(path.relative_to(RUNTIME).as_posix() for path in directory.glob('*.bmp'))
        names.add(f'core/locales/{code}/glyphs.dat')
    names.update(path.relative_to(RUNTIME).as_posix() for path in (RUNTIME / 'licenses').rglob('*') if path.is_file())
    result = []
    for name in sorted(names):
        relative = Path(name)
        if any(part.lower() in ('saves', 'preserved', 'screenshots') for part in relative.parts):
            continue
        if relative.suffix.lower() in ('.exe', '.dll', '.sav', '.bak', '.bkp', '.tmp', '.log', '.cache'):
            continue
        path = RUNTIME / relative
        if path.is_file():
            result.append((path, '/game/' + relative.as_posix()))
    return result


def pack_assets(env):
    sdk = web_toolchain.paths()[1]
    SITE.mkdir(parents=True, exist_ok=True)
    allowed = asset_files()
    from web_audio import prepare as prepare_audio
    audio = prepare_audio(allowed, BUILD / 'audio')
    command = [sys.executable, sdk / 'upstream/emscripten/tools/file_packager.py', SITE / 'assets.data',
               '--js-output=' + str(SITE / 'assets.js'), '--preload']
    # Use a preload directory instead of thousands of command-line arguments.
    package = BUILD / 'package'
    expected = {target.removeprefix('/game/') for _, target in allowed}
    for old in package.rglob('*') if package.exists() else ():
        if old.is_file() and old.relative_to(package).as_posix() not in expected:
            old.unlink()
    for path, target in allowed:
        destination = package / target.removeprefix('/game/')
        destination.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(audio.get(target, path), destination)
    command += [str(package) + '@/game']
    web_toolchain.run(command, env=env)
    writable = [target for path, target in allowed
                if path.suffix.lower() == '.cfg' or path.name in ('modern.ini', 'settings.inf')]
    writable.extend(('/game/mods/Stranded II/sys/settings.cfg', '/game/core/settings.inf'))
    write(SITE / 'defaults.json', {'files': sorted(set(writable)),
          'languages': [{'code': code, 'name': metadata['name']} for code, metadata in languages(ROOT).items()]})


def build(args):
    inputs()
    validate(ROOT, 'classic')
    info = validate_info(read(args.version_info)) if args.version_info else local_info(ROOT, 'classic')
    if info['edition'] != 'classic':
        raise ValueError('Web version belongs to another edition.')
    write(RUNTIME / 'core/version.json', info)
    (RUNTIME / 'core/version.txt').write_text(info['display_version'] + '\n', encoding='ascii')
    env = web_toolchain.build()
    upstream = web_toolchain.upstream()
    ng = Path(upstream.NG)
    runtime_licenses = [
        (web_toolchain.paths()[0] / 'LICENSE', 'WebGPU-port-MIT.txt'),
        (ng / 'LICENSE.TXT', 'Blitz3D-NG.txt'),
        (ng / 'deps/sdl/tree/LICENSE.txt', 'SDL2.txt'),
        (ng / 'deps/freetype2/tree/docs/FTL.TXT', 'FreeType.txt'),
        (ng / 'deps/ogg/tree/COPYING', 'Ogg.txt'),
        (ng / 'deps/vorbis/tree/COPYING', 'Vorbis.txt'),
        (ng / 'deps/minimp3/tree/LICENSE', 'minimp3.txt'),
        (ng / 'deps/zlib/tree/README', 'zlib.txt'),
        (web_toolchain.paths()[1] / 'upstream/emscripten/LICENSE', 'Emscripten.txt'),
    ]
    for source, name in runtime_licenses:
        shutil.copy2(source, RUNTIME / 'licenses' / name)
    SITE.mkdir(parents=True, exist_ok=True)
    native = BUILD / 'native'
    native.mkdir(exist_ok=True)
    for name, source in [('compat', ng / 'src/modules/bb/graphics.webgpu/web_compat.cpp'),
                         ('bridge', ROOT / 'src/native/web/bridge.cpp')]:
        obj = native / (name + '.o')
        command = [sys.executable, web_toolchain.paths()[1] / 'upstream/emscripten/emcc.py',
                   source, '-c', '-O2', '-std=c++17', '-fwasm-exceptions', '-o', obj]
        for inc in [ng / 'src/modules', ng / 'src/modules/bb/pixmap', ng / 'src', ng / 'src/modules/bb/graphics.webgpu']:
            command += ['-I', inc]
        web_toolchain.run(command, env=env)
    env = compiler_environment(env)
    web_toolchain.run([upstream.BLITZCC, '-target', 'emscripten', '-o', SITE / 'game', ENTRY],
                      cwd=ROOT / 'src/engine', env=env)
    pack_assets(env)
    (SITE / 'game.html').unlink() # The custom index owns storage and startup.
    for name in ('index.html', 'shell.js'):
        shutil.copy2(ROOT / 'src/web' / name, SITE / name)
    write(SITE / 'version.json', info)
    write(BUILD / 'build-report.json', dict(info, platform='web', toolchain=web_toolchain.config(),
          source_sha256=source_hashes(ROOT, 'classic', 'web'),
          implementation_sha256=implementation_hashes(),
          files={name: digest(SITE / name) for name in SITE_FILES}))
    print('Built WebGPU preview:', SITE / 'index.html')


def compiler_environment(env=None):
    upstream = web_toolchain.upstream()
    runtime = Path(upstream.RUNTIME_OUT)
    return dict(env or web_toolchain.environment(), LLVM_ROOT=str(Path(upstream.NG) / 'llvm'),
                blitzpath=str(runtime), SCPCB_WEBGPU='1',
                SCPCB_LIB_DIR=str(runtime / 'bin/wasm32-unknown-emscripten/lib'),
                SCPCB_COMPAT_OBJ=str(BUILD / 'native/compat.o'),
                SCPCB_EMCC_EXTRA='-O2 -sASSERTIONS=1 -sDEFAULT_TO_CXX -sGROWABLE_ARRAYBUFFERS=0 -sCASE_INSENSITIVE_FS=1 "' + str(BUILD / 'native/bridge.o') + '"')


class Handler(SimpleHTTPRequestHandler):
    def end_headers(self):
        self.send_header('Cache-Control', 'no-cache')
        super().end_headers()

    def do_POST(self):
        if self.path != '/__diagnostics':
            self.send_error(404)
            return
        length = min(int(self.headers.get('Content-Length', 0)), 65536)
        message = self.rfile.read(length).decode('utf8', errors='replace')
        with (BUILD / 'browser.log').open('a', encoding='utf8') as log:
            log.write(message + '\n')
        self.send_response(204)
        self.end_headers()


def run(args):
    if not (SITE / 'index.html').is_file():
        raise ValueError('Run build --platform web first.')
    if getattr(args, 'game_args', []):
        raise ValueError('The web preview does not accept native game command-line arguments.')
    port = getattr(args, 'port', PORT)
    if not 1024 <= port <= 65535:
        raise ValueError('Choose a local web port between 1024 and 65535.')
    server = ThreadingHTTPServer(('127.0.0.1', port), functools.partial(Handler, directory=str(SITE)))
    url = f'http://127.0.0.1:{port}/'
    print('WebGPU preview:', url, flush=True)
    try:
        server.serve_forever()
    finally:
        server.server_close()


def implementation_hashes():
    paths = [ROOT / name for name in ('scripts/web_build.py', 'scripts/web_toolchain.py',
             'scripts/web_audio.py', 'config/web-toolchain.json', 'src/native/web/bridge.cpp', 'src/web/shell.js', 'src/web/index.html')]
    paths += sorted((ROOT / 'patches/blitz3d-web').glob('*.patch'))
    return {path.relative_to(ROOT).as_posix(): digest(path) for path in paths}


def check(args):
    report = read(BUILD / 'build-report.json')
    verification = {'web_check': 'running', 'browser_gameplay': 'not verified by this command',
                    'artifact_sha256': report['files']}
    write(BUILD / 'verification.json', verification)
    if report['source_sha256'] != source_hashes(ROOT, 'classic', 'web'):
        raise ValueError('The web build is stale; build it again.')
    if report['implementation_sha256'] != implementation_hashes():
        raise ValueError('The web platform implementation changed; build it again.')
    for name, expected in report['files'].items():
        if digest(SITE / name) != expected:
            raise ValueError(f'Web build artifact changed: {name}')
    if (SITE / 'game.wasm').read_bytes()[:4] != b'\0asm':
        raise ValueError('Invalid WebAssembly binary.')
    validate(ROOT, 'classic')
    subprocess.run([sys.executable, '-m', 'unittest', 'discover', '-s', 'tests', '-p', 'test_web*.py'],
                   cwd=ROOT, check=True)
    from web_audio import verify as verify_audio
    audio_count = verify_audio(asset_files(), BUILD / 'package', BUILD / 'audio')
    print('Verified converted web audio:', audio_count)
    from verify_web import verify_semantics
    semantics = verify_semantics()
    verification.update(web_check='passed', windows_wasm_parity=read(semantics / 'report.json'),
        semantics_report=semantics.relative_to(ROOT).as_posix() + '/report.json',
        web_audio={'verified_pcm_files': audio_count, 'playback': 'not tested'},
        browser_storage_bridge='passed with real Emscripten VFS and simulated transactions')
    write(BUILD / 'verification.json', verification)
    print('Web artifacts and non-graphical checks passed. Browser gameplay is a separate verification.')
