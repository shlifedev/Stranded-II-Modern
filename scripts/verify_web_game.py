"""Prepare an isolated full-engine comparison, then run its hidden Windows reference.

The browser check uses the same scenario. Synthetic time does not validate
real-time pacing, audio playback, interactive controls or IndexedDB persistence.
"""
from pathlib import Path
import argparse
import json
import os
import shutil
import subprocess
import tempfile

import web_toolchain
import blitz3d_toolchain as engine
from web_build import ROOT, BUILD, SITE, compiler_environment, digest, asset_files
from web_audio import supported_wav
from verify_timing import function


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--prepare-only', action='store_true')
    args = parser.parse_args()
    output = Path(tempfile.mkdtemp(prefix='compatibility-', dir=BUILD))
    source = output / 'src'
    shutil.copytree(ROOT / 'src/engine', source)
    init = source / 'includes/engine_init.bb'
    init.write_text(init.read_text('utf8').replace('\nunrealsoftware()\n', '\n'), encoding='utf8')
    timing = source / 'includes/modern_runtime.bb'
    text = timing.read_text('utf8')
    text = text.replace(function(text, 'modern_focus'), 'Function modern_focus()\n Return 0\nEnd Function')
    text = text.replace(function(text, 'modern_clock'), 'Function modern_clock()\n Return compat_clock\nEnd Function')
    timing.write_text(text, encoding='utf8')
    sequence = source / 'includes/game_sequences.bb'
    sequence.write_text(sequence.read_text('utf8').replace('Select Tseq\\event$',
        'compat_event(Tseq\\event$,Tseq\\t)\n\t\t\tSelect Tseq\\event$'), encoding='utf8')
    scenario = (ROOT / 'tests/web_compatibility.bb').read_text('utf8')
    audio = [target.removeprefix('/game/mods/Stranded II/') for path, target in asset_files()
             if path.suffix.lower() == '.wav' and not supported_wav(path)]
    audio += [f'sfx/speech/intro_{part}.ogg' for part in 'abcd']
    scenario = scenario.replace('; AUDIO_CHECKS', '\n'.join(
        'compat_sound("' + path.replace('/', '\\') + '")' for path in audio))
    (source / 'includes/engine_loop.bb').write_text(scenario, encoding='utf8')
    runtime = output / 'runtime'
    shutil.copytree(ROOT / 'build/runtime', runtime,
        ignore=shutil.ignore_patterns('preserved', 'saves', '*.log', '*.exe'))
    (runtime / 'mods/Stranded II/saves').mkdir(exist_ok=True)
    # Decode the same web-prepared audio in both engines. The old Windows
    # decoder also rejects some original GSM files; player runtimes stay intact.
    for path in audio:
        relative = Path('mods/Stranded II') / path
        prepared = BUILD / 'audio' / relative
        if prepared.is_file():
            shutil.copy2(prepared, runtime / relative)
    ini = 'fps=144\ndisplay=windowed\nresolution=custom\nrender_width=800\nrender_height=600\nlanguage=ko\ndiagnostics=0\n'
    (runtime / 'core/modern.ini').write_text(ini, encoding='utf8')
    engine.build_native(True)
    exe = runtime / 'Compatibility.exe'
    engine.compile_test(source / 'StrandedII.bb', exe, cwd=source)
    checks = SITE / 'checks'
    checks.mkdir(exist_ok=True)
    web_toolchain.run([web_toolchain.upstream().BLITZCC, '-target', 'emscripten', '-o', checks / 'compatibility',
        source / 'StrandedIIWeb.bb'], cwd=source, env=compiler_environment())
    shutil.copy2(ROOT / 'tests/web_compatibility.html', checks / 'index.html')
    if args.prepare_only:
        print('Prepared full-engine reference:', exe)
        return
    startup = subprocess.STARTUPINFO()
    startup.dwFlags |= subprocess.STARTF_USESHOWWINDOW
    startup.wShowWindow = 0
    subprocess.run([str(exe)], cwd=runtime, startupinfo=startup,
        creationflags=subprocess.CREATE_NO_WINDOW, check=True, timeout=180)
    report = (runtime / 'web-compatibility.txt').read_text('utf8')
    (checks / 'windows-reference.txt').write_text(report, encoding='utf8')
    passed = 'FAILURES 0' in report and '\nFAIL ' not in '\n' + report
    (output / 'report.json').write_text(json.dumps({'windows_reference': 'pass' if passed else 'fail',
        'browser': 'not run', 'report': report,
        'audio_inputs': 'web-prepared PCM plus original Ogg speech',
        'scenario_sha256': digest(ROOT / 'tests/web_compatibility.bb')}, indent=2) + '\n', encoding='utf8')
    print(report)
    print('Reference report:', output / 'report.json')
    print('Browser comparison:', 'http://127.0.0.1:8765/checks/')
    if not passed:
        raise AssertionError('Windows reference failed: ' + str(output))


if __name__ == '__main__':
    main()
