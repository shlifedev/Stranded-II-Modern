"""Native ARM64 compatibility, actual Metal rendering and isolated game checks."""
from pathlib import Path
import json
import os
import shutil
import subprocess
import tempfile
import argparse

import macos_build as build
import macos_toolchain
from editions import source_hashes
from locales import validate
from verify_timing import function
from macos_validation import current_build, check_built_app, verifier_hashes

ROOT, BUILD = build.ROOT, build.BUILD


def execute(executable, cwd, log, timeout=30, env=None):
    # Only these newly compiled, self-terminating fixtures are timeout-bound.
    # Never attach to or stop a user's running game.
    with log.open('w', encoding='utf8') as output:
        subprocess.run([str(executable)], cwd=cwd, stdout=output,
                       stderr=subprocess.STDOUT, check=True, timeout=timeout, env=env)


def check_abi(output):
    compiler = macos_toolchain.build()
    source = (ROOT / 'tests/web_semantics.bb').read_text('utf8')
    marker = 'FreeBank bank\nCloseFile output'
    if source.count(marker) != 1:
        raise ValueError('The shared semantics fixture footer changed.')
    source = source.replace(marker, (ROOT / 'tests/macos_abi.bb').read_text('utf8') + '\n' + marker)
    fixture = output / 'abi.bb'
    fixture.write_text(source, encoding='utf8')
    executable = output / 'abi'
    build.compile_source(compiler, fixture, executable, 's2console', cwd=output)
    execute(executable, output, output / 'abi.log')
    actual = (output / 'semantics.bin').read_bytes()
    # Captured from the hash-verified original Blitz3D 1.118 compiler/runtime.
    expected = bytes.fromhex((ROOT / 'tests/macos_abi.golden.hex').read_text('ascii'))
    if actual != expected:
        raise AssertionError(f'Native/Blitz3D 1.118 binary mismatch: {output / "semantics.bin"}')
    lifetime = output / 'lifetime'
    build.compile_source(compiler, ROOT / 'tests/macos_lifetime.bb', lifetime, 's2console', cwd=output)
    execute(lifetime, output, output / 'lifetime.log')
    return {'bytes_equal_to_blitz_1_118': len(expected), 'type_reuse': 'pass'}


def fixture_assets(destination):
    """Copy only prepared assets, excluding all saves and current preferences."""
    original = Path(build.inputs()['game'])
    names = build.asset_names()
    for name in sorted(names):
        relative = Path(name)
        if relative.suffix.lower() in ('.exe', '.dll', '.sav', '.bak', '.log') or any(
                part.lower() in ('saves', 'preserved') for part in relative.parts):
            continue
        source = build.RUNTIME / relative
        if relative.suffix == '.cfg' or relative.name == 'settings.inf':
            source = original / relative
        if not source.is_file():
            continue
        target = destination / relative
        target.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(source, target)
    (destination / 'mods/Stranded II/saves').mkdir(parents=True, exist_ok=True)
    (destination / 'core/modern.ini').write_text(
        'fps=60\ndisplay=windowed\nresolution=custom\nrender_width=800\nrender_height=600\n'
        'language=ko\ndiagnostics=0\n', encoding='utf8')


def check_game(compiler, output):
    source, runtime = output / 'src', output / 'runtime'
    shutil.copytree(ROOT / 'src/engine', source)
    fixture_assets(runtime)
    init = source / 'includes/engine_init.bb'
    init.write_text(init.read_text('utf8').replace('\nunrealsoftware()\n', '\n'), encoding='utf8')
    timing = source / 'includes/modern_runtime.bb'
    text = timing.read_text('utf8')
    text = text.replace(function(text, 'modern_focus'), 'Function modern_focus()\n Return 0\nEnd Function')
    text = text.replace(function(text, 'modern_clock'), 'Function modern_clock()\n Return compat_clock\nEnd Function')
    timing.write_text(text, encoding='utf8')
    sequence = source / 'includes/game_sequences.bb'
    sequence.write_text(sequence.read_text('utf8').replace('Select Tseq\\event$',
        'compat_event(Tseq\\event$,Tseq\\t)\nSelect Tseq\\event$'), encoding='utf8')
    scenario = (ROOT / 'tests/web_compatibility.bb').read_text('utf8')
    audio = [name.removeprefix('mods/Stranded II/') for name in build.read(BUILD / 'audio-report.json')]
    audio += [f'sfx/speech/intro_{part}.ogg' for part in 'abcd']
    scenario = scenario.replace('; AUDIO_CHECKS', '\n'.join(
        'compat_sound("' + name.replace('/', '\\') + '")' for name in audio))
    marker = 'WriteLine compat_report,"FAILURES "+compat_failures'
    if scenario.count(marker) != 1:
        raise ValueError('The shared full-engine fixture footer changed.')
    scenario = scenario.replace(marker, (ROOT / 'tests/macos_game.bb').read_text('utf8') + '\n' + (ROOT / 'tests/benchmark_scene.bb').read_text('utf8') + '\n' + marker)
    if scenario.count('; SAVE_MENU_CHECKS') != 1:
        raise ValueError('The native save menu fixture marker changed.')
    scenario = scenario.replace('; SAVE_MENU_CHECKS',
        (ROOT / 'tests/save_menu.bb').read_text('utf8').replace('preview_check(', 'compat_check('))
    (source / 'includes/engine_loop.bb').write_text(scenario, encoding='utf8')
    executable = runtime / 'NativeCompatibility'
    build.compile_source(compiler, source / 'StrandedIIMac.bb', executable, 's2macos', cwd=source)
    app = build.package_app(executable, parent=output / 'app')
    subprocess.run(['codesign', '--verify', '--deep', '--strict', str(app)], check=True)
    execute(app / 'Contents/MacOS/StrandedII', runtime, output / 'game.log', timeout=180,
            env=dict(os.environ, S2_NATIVE_DATA_DIR=str(runtime)))
    report = (runtime / 'web-compatibility.txt').read_text('utf8')
    (output / 'game-results.txt').write_text(report, encoding='utf8')
    if 'FAILURES 0\n' not in report or '\nFAIL ' in '\n' + report:
        raise AssertionError(f'Native game check failed: {output / "game-results.txt"}')
    from PIL import Image
    for name in ('native-menu', 'native-island'):
        with Image.open(runtime / (name + '.bmp')) as frame:
            frame.save(output / (name + '.png'))
    return {'checks_passed': sum(line.startswith('CHECK ') for line in report.splitlines()),
            'synthetic_time': True, 'app_bundle': 'pass', 'report': str(output / 'game-results.txt')}


def check_bundle(compiler, output):
    executable = output / 'bootstrap'
    build.compile_source(compiler, ROOT / 'tests/macos_bundle.bb', executable, 's2macos', cwd=output)
    app = build.package_app(executable, parent=output / 'bootstrap-app')
    profile = output / 'fresh-profile'
    env = dict(os.environ, S2_NATIVE_DATA_DIR=str(profile))
    execute(app / 'Contents/MacOS/StrandedII', output, output / 'bootstrap-first.log', env=env)
    settings = profile / 'core/modern.ini'
    original = settings.read_bytes()
    if b'language=en' not in original:
        raise AssertionError('A fresh app imported a personal language preference.')
    selected = original.replace(b'language=en', b'language=ko')
    settings.write_bytes(selected)
    save = profile / 'mods/Stranded II/saves/preservation-check.sav'
    save.write_bytes(b'player data must survive native resource refresh')
    expected = save.read_bytes()
    (profile / '.native-assets').write_text('previous-app-version\n', encoding='ascii')
    execute(app / 'Contents/MacOS/StrandedII', output, output / 'bootstrap-second.log', env=env)
    if settings.read_bytes() != selected or save.read_bytes() != expected:
        raise AssertionError('App bootstrap changed saved settings or player data.')
    return {'fresh_profile': 'pass', 'resource_refresh_preserves_settings_and_saves': 'pass'}


def main(suite='full'):
    if suite not in ('core', 'full'):
        raise ValueError('Native check suite must be core or full.')
    macos_toolchain.require_host()
    validate(ROOT, 'classic')
    report = current_build()
    output = Path(tempfile.mkdtemp(prefix='verification-', dir=BUILD))
    results = {'platform': 'macos-arm64', 'suite': suite, 'output': str(output), 'passed': False,
               'source_sha256': report['source_sha256'],
               'implementation_sha256': report['implementation_sha256'],
               'executable_sha256': report['executable_sha256'], 'verifier_sha256': verifier_hashes(),
               'metal_render_and_readback': 'not_run', 'bundle_storage': 'not_run', 'game': 'not_run'}
    try:
        results['app'] = check_built_app(report)
        results['abi'] = check_abi(output)
        if suite == 'full':
            compiler = macos_toolchain.build(graphics=True)
            graphics = output / 'graphics'
            build.compile_source(compiler, ROOT / 'tests/macos_graphics.bb', graphics, 's2macos', cwd=output)
            execute(graphics, output, output / 'graphics.log')
            results['metal_render_and_readback'] = 'pass'
            import blitz3d_toolchain as engine
            directory = engine.BUILD / 'macos-game/cmake'
            engine.run(['cmake', '--build', directory, '--target', 's2_profiler_check',
                        's2_profiler_gpu_check', 's2_benchmark_check', '-j', '8'], env=engine.environment())
            for name in ('s2_profiler_check', 's2_profiler_gpu_check', 's2_benchmark_check'):
                execute(directory / name, output, output / (name + '.log'))
            results['profiler'] = 'CPU recorder and actual Metal timestamp/readback checks passed'
            results['bundle_storage'] = check_bundle(compiler, output)
            results['game'] = check_game(compiler, output)
        if (current_build() != report or results['verifier_sha256'] != verifier_hashes() or
                results['app'] != check_built_app(report)):
            raise ValueError('Native sources changed during verification; rebuild and recheck.')
        results['passed'] = True
    finally:
        build.write(BUILD / f'verification-{suite}.json', results)
        build.write(BUILD / 'verification.json', results)
    print(json.dumps({k: v for k, v in results.items() if not k.endswith('_sha256')},
                     ensure_ascii=False, indent=2))


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--suite', choices=('core', 'full'), default='full')
    main(parser.parse_args().suite)
