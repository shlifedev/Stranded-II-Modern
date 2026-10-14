"""Build and serve an isolated, real-time WebGPU rendering profiler.

The browser runs the copied engine with MEMFS only. Production artifacts,
Windows runtimes, browser saves and the pinned renderer are not modified.
"""
from functools import partial
from http.server import SimpleHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
import argparse
import json
import secrets
import shutil
import subprocess
import sys
import tempfile

import web_toolchain
from verify_timing import function
from web_build import ROOT, BUILD, SITE, compiler_environment, digest, implementation_hashes
from editions import source_hashes


def replace_once(text, old, new):
    if text.count(old) != 1:
        raise ValueError(f'Profiler source anchor changed: {old!r}')
    return text.replace(old, new, 1)


def instrument(source):
    """Add synchronous marks to a disposable source copy; keep real clocks."""
    init = source / 'includes/engine_init.bb'
    init.write_text(replace_once(init.read_text('utf8'), '\nunrealsoftware()\n', '\n'), encoding='utf8')
    for filename in ('game.bb', 'menu.bb'):
        path = source / 'includes' / filename
        text = path.read_text('utf8')
        for call, label in [('UpdateWorld(f#)', 'update'), ('e_environment_update_water()', 'water'),
                            ('RenderWorld()', 'world'), ('mb_update()', 'motion_blur')]:
            text = replace_once(text, call,
                f'profile("start:{label}"):{call}:profile("stop:{label}")')
        if filename == 'game.bb':
            text = replace_once(text, '\tgame_setcam()', '\tgame_setcam():profile_camera()')
        path.write_text(text, encoding='utf8')
    environment = source / 'includes/e_environment.bb'
    text = environment.read_text('utf8')
    water = function(text, 'e_environment_update_water')
    marked = water
    # Cube capture now renders all six views inside one runtime call. Measure
    # the active capture as a whole, including the optional planar path.
    for call in ('RenderWorldCube env_watercam,env_watertex',
                 'RenderWorldPlanar cam,env_watertex,0'):
        marked = replace_once(marked, call,
            f'profile("start:reflection"):{call}:profile("stop:reflection")')
    environment.write_text(text.replace(water, marked), encoding='utf8')
    timing = source / 'includes/modern_runtime.bb'
    text = timing.read_text('utf8')
    present = function(text, 'modern_present')
    marked = replace_once(present, '\n While modern_next-now>0',
        '\n profile("start:wait")\n While modern_next-now>0')
    marked = replace_once(marked, '\n Flip False\n',
        '\n profile("stop:wait")\n Flip False\n')
    # Both capped and uncapped presentation complete a frame; only the capped
    # branch enters the wait phase. Match whole lines to keep them distinct.
    for indent in (' ', '  '):
        marked = replace_once(marked, f'\n{indent}Flip False\n',
            f'\n{indent}profile("start:flip")\n{indent}Flip False\n{indent}profile("stop:flip")\n')
    timing.write_text(text.replace(present, marked), encoding='utf8')
    # Retain the real input/timing/timer path, but suppress manual controls in
    # the benchmark so clicking the page cannot change the scenario.
    inputs = source / 'includes/input.bb'
    text = inputs.read_text('utf8')
    original = function(text, 'getinput')
    reset = '''
 Local profileInput
 For profileInput=0 To 41
  in_keyhit(profileInput)=0:in_keydown(profileInput)=0:in_keyrelease(profileInput)=0
 Next
 For profileInput=1 To 5
  in_mhit(profileInput)=0:in_mdown(profileInput)=0:in_mrelease(profileInput)=0
 Next
 in_mxs=0:in_mys=0:in_mzs=0:in_escape=0:in_key=0
 in_mx=-1000:in_my=-1000
End Function'''
    inputs.write_text(text.replace(original, original.removesuffix('End Function') + reset), encoding='utf8')
    shutil.copy2(ROOT / 'tests/web_profile.bb', source / 'includes/engine_loop.bb')


def prepare():
    report = json.loads((BUILD / 'build-report.json').read_text('utf8'))
    if report['source_sha256'] != source_hashes(ROOT, 'classic', 'web') or report['implementation_sha256'] != implementation_hashes():
        raise ValueError('Build the current web version first: scripts/dev.py build --platform web')
    for name in ('assets.js', 'assets.data'):
        if digest(SITE / name) != report['files'][name]:
            raise ValueError(f'Stale asset pack: {name}')
    output = Path(tempfile.mkdtemp(prefix='profile-', dir=BUILD))
    source = output / 'src'
    shutil.copytree(ROOT / 'src/engine', source)
    instrument(source)
    site = output / 'site'
    site.mkdir()
    env = compiler_environment()
    # Measurement marks must not go through the Promise/JSPI bridge. Ordinary
    # services still use exactly the production bridge implementation.
    native = (ROOT / 'src/native/web/bridge.cpp').read_text('utf8')
    native = replace_once(native, 'std::string strandedWebProperty', '''EM_JS(int, stranded_profile, (const char* request), {
    return Module.s2Profile(UTF8ToString(request)) | 0;
});

std::string strandedWebProperty''')
    native = replace_once(native, '    char* result = stranded_web_service', '''    if (request.rfind("profile\\x1f", 0) == 0)
        return std::to_string(stranded_profile(request.c_str() + 8));
    char* result = stranded_web_service''')
    bridge = output / 'bridge.cpp'
    bridge.write_text(native, encoding='utf8')
    obj = output / 'bridge.o'
    web_toolchain.run([sys.executable, web_toolchain.paths()[1] / 'upstream/emscripten/emcc.py',
                      bridge, '-c', '-O2', '-std=c++17', '-fwasm-exceptions', '-o', obj], env=env)
    env['SCPCB_EMCC_EXTRA'] = env['SCPCB_EMCC_EXTRA'].replace(str(BUILD / 'native/bridge.o'), str(obj))
    web_toolchain.run([web_toolchain.upstream().BLITZCC, '-target', 'emscripten',
                      '-o', site / 'profile', source / 'StrandedIIWeb.bb'], cwd=source, env=env)
    shutil.copy2(ROOT / 'tests/web_profile.html', site / 'index.html')
    shutil.copy2(ROOT / 'tests/web_profile.js', site / 'profile-ui.js')
    metadata = {
        'source_commit': subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=ROOT, text=True).strip(),
        'source_sha256': report['source_sha256'], 'toolchain': report['toolchain'],
        'profiler_sha256': {name: digest(ROOT / name) for name in ('scripts/profile_web.py',
            'tests/web_profile.bb', 'tests/web_profile.js', 'tests/web_profile.html')},
        'profile_wasm_sha256': digest(site / 'profile.wasm'),
        'assets_sha256': report['files']['assets.data'],
        'storage': 'MEMFS only; no IndexedDB mount', 'gpu_time_measured': False,
        'instrumentation': 'synchronous phase marks and WebGPU API counters; same renderer and real clock',
        'limitations': ['Submission intervals are not display scanout timestamps.',
                        'CPU phase times include WebGPU command encoding, not GPU execution time.',
                        'Automatic camera motion and muted audio do not validate interactive gameplay.'],
        'report_token': secrets.token_hex(16),
    }
    (site / 'profile-config.json').write_text(json.dumps(metadata, indent=2) + '\n', encoding='utf8')
    (output / 'report.json').write_text(json.dumps(dict(metadata, status='not_run', trials=[]), indent=2) + '\n', encoding='utf8')
    return output


def validate_report(report):
    if not isinstance(report, dict) or not isinstance(report.get('trials'), list):
        raise ValueError('Invalid report object')
    if report.get('status') not in ('running', 'complete', 'failed'):
        raise ValueError('Invalid report status')
    if not all(isinstance(trial, dict) for trial in report['trials']):
        raise ValueError('Invalid trial object')
    if report['status'] == 'complete':
        expected = {(repeat, water, fps) for repeat in (1, 2, 3)
                    for water, fps in ((6, 144), (3, 144), (0, 144), (0, 60))}
        actual = {(t.get('repeat'), t.get('water'), t.get('fps_limit')) for t in report['trials']}
        if len(report['trials']) != 12 or actual != expected or not all(
                t.get('valid') is True and t.get('frames', 0) > 1 for t in report['trials']):
            raise ValueError('A complete report requires all twelve valid trials')
    return report


class Handler(SimpleHTTPRequestHandler):
    def __init__(self, *args, output, **kwargs):
        self.output = output
        self.config = json.loads((output / 'site/profile-config.json').read_text('utf8'))
        super().__init__(*args, directory=str(output / 'site'), **kwargs)

    def end_headers(self):
        self.send_header('Cache-Control', 'no-store')
        super().end_headers()

    def translate_path(self, path):
        name = path.split('?', 1)[0]
        if name in ('/assets.js', '/assets.data'):
            return str(SITE / name[1:])
        return super().translate_path(path)

    def do_POST(self):
        if self.path != '/report' or self.headers.get('X-Profile-Token') != self.config['report_token']:
            self.send_error(403)
            return
        try:
            size = int(self.headers.get('Content-Length', '0'))
            if not 0 < size <= 8 * 1024 * 1024:
                raise ValueError('Invalid report size')
            report = validate_report(json.loads(self.rfile.read(size)))
            report = dict(self.config, **report)
            report.pop('report_token', None)
            target = self.output / 'report.json'
            pending = target.with_suffix('.tmp')
            pending.write_text(json.dumps(report, ensure_ascii=False, indent=2) + '\n', encoding='utf8')
            pending.replace(target)
        except (ValueError, TypeError, OSError) as error:
            self.send_error(400, str(error))
            return
        self.send_response(204)
        self.end_headers()
        print(f"PROFILE {report['status']}: {len(report['trials'])} trials -> {target}", flush=True)

    def log_message(self, format, *args):
        if str(args[1]) not in ('200', '304'):
            super().log_message(format, *args)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--serve', action='store_true', help='Serve the new profiler on loopback after building')
    parser.add_argument('--existing', type=Path, help='Serve an already prepared profile directory without rebuilding')
    parser.add_argument('--port', type=int, default=8766)
    args = parser.parse_args()
    if not 1024 <= args.port <= 65535:
        parser.error('Choose a port between 1024 and 65535')
    output = args.existing.resolve() if args.existing else prepare()
    if args.existing and not output.is_relative_to(BUILD.resolve()):
        parser.error('--existing must be a prepared directory under build/web')
    print('Profiler:', output, flush=True)
    if args.serve or args.existing:
        server = ThreadingHTTPServer(('127.0.0.1', args.port), partial(Handler, output=output))
        print(f'Open http://127.0.0.1:{args.port}/ and start the comparison. Keep the tab foreground.', flush=True)
        try:
            server.serve_forever()
        finally:
            server.server_close()


if __name__ == '__main__':
    main()
