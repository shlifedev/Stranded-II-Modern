"""Run the same numeric/binary fixture in the Windows and wasm runtimes (no GUI)."""
from pathlib import Path
import os
import subprocess
import tempfile
import json
import shutil

import web_toolchain
from web_build import ROOT, BUILD, compiler_environment
from s2map import Map
from verify_timing import function


def script_fixture(output):
    """Exercise unchanged parser helpers with the actual packaged story scripts."""
    assets = ROOT / 'build/web/runtime/mods/Stranded II'
    fixture = output / 'fixtures'
    fixture.mkdir()
    scripts = {}
    for name, relative in [('menu', 'maps/menu/menu.s2'), ('map01', 'maps/adventure/map01.s2')]:
        model = Map((assets / relative).read_bytes())
        scripts[name] = next(f.data for f in model.fields if f.key == 'global_script')
    scripts['intro'] = b'\x1f'.join((assets / 'maps/adventure/map01_intro.s2s').read_bytes().splitlines())
    body = '''\nType Tpc
 Field txt$
End Type
Global Tpc.Tpc,probeFile,probeText$,probeCount
'''
    inc = ROOT / 'src/engine/includes'
    for filename, names in {
        'parser_functions.bb': ('clear_parsecache', 'loadstring_parsecache', 'parse_end'),
        'parser_pre.bb': ('preparse_string$',),
    }.items():
        text = (inc / filename).read_text('utf8')
        for name in names:
            body += function(text, name) + '\n'
    for name, data in scripts.items():
        (fixture / (name + '.script')).write_bytes(data)
        count = len([line for line in data.split(b'\x1f') if line.strip()])
        body += f'''
probeFile=ReadFile("fixtures/{name}.script")
If probeFile=0 Then RuntimeError "Script fixture missing: {name}"
probeText$=""
While Not Eof(probeFile)
 probeText$=probeText$+Chr(ReadByte(probeFile))
Wend
CloseFile probeFile
loadstring_parsecache(probeText$)
probeCount=0
For Tpc=Each Tpc
 probeCount=probeCount+1
 WriteString output,Tpc\\txt$
Next
WriteInt output,probeCount
If probeCount<>{count} Then RuntimeError "Script line splitting failed: {name}"
WriteString output,preparse_string$(probeText$)
clear_parsecache()
'''
    return body, fixture


def verify_semantics():
    output = Path(tempfile.mkdtemp(prefix='semantics-', dir=BUILD))
    windows = output / 'windows'
    windows.mkdir()
    native = ROOT / 'build/toolchain'
    if not (native / 'bin/blitzcc.exe').is_file():
        raise ValueError('Numeric parity checks also require the private Windows compiler (setup --platform windows).')
    script_checks, fixture = script_fixture(output)
    source = output / 'semantics.bb'
    source.write_text((ROOT / 'tests/web_semantics.bb').read_text('utf8').replace('; SCRIPT_CHECKS', script_checks), encoding='utf8')
    shutil.copytree(fixture, windows / 'fixtures')
    web_toolchain.run([native / 'bin/blitzcc.exe', '-o', windows / 'test.exe', source],
                      cwd=windows, env=dict(os.environ, blitzpath=str(native)))
    subprocess.run([str(windows / 'test.exe')], cwd=windows, check=True, timeout=30)
    env = compiler_environment()
    pre = output / 'pre.js'
    pre.write_text('''var Module = {
  noInitialRun: true,
  s2Bridge: async function(request) {
    if (request === 'probe') return 42;
    throw new Error('Unexpected bridge request in the numeric fixture: ' + request);
  },
  onRuntimeInitialized: async function() {
    try {
      FS.mkdirTree('/game'); FS.chdir('/game');
      await callMain([]);
      require('node:fs').writeFileSync(process.env.S2_SEMANTICS_OUTPUT, FS.readFile('/game/semantics.bin'));
      console.log('WASM semantics fixture finished');
      await require(process.env.S2_STORAGE_HARNESS)(FS, process.env.S2_WEB_SHELL, process.env.S2_WEB_TEST_SAVED_LANGUAGE);
    } catch (error) { console.error(error); process.exitCode = 1; }
  }
};
''', encoding='utf8')
    # The upstream console-test runtime registers GL drivers too; no graphics
    # context is opened by this fixture, but their symbols must still link.
    env['SCPCB_EMCC_EXTRA'] += f' -sUSE_WEBGL2=1 -sFULL_ES3=1 --pre-js "{pre}" --embed-file "{fixture}@/game/fixtures"'
    web_toolchain.run([web_toolchain.upstream().BLITZCC, '-target', 'emscripten', '-r', 'test',
                      '-o', output / 'test', source], cwd=output, env=env)
    node = next((web_toolchain.paths()[1] / 'node').glob('*/node.exe'))
    env['S2_SEMANTICS_OUTPUT'] = str(output / 'semantics.bin')
    env['S2_STORAGE_HARNESS'] = str(ROOT / 'tests/web_storage.cjs')
    env['S2_WEB_SHELL'] = str(ROOT / 'src/web/shell.js')
    for saved_language in ('', 'ko'):
        env['S2_WEB_TEST_SAVED_LANGUAGE'] = saved_language
        web_toolchain.run([node, '--experimental-wasm-jspi', output / 'test.js'], cwd=output, env=env, timeout=60)
    expected, actual = (windows / 'semantics.bin').read_bytes(), (output / 'semantics.bin').read_bytes()
    if actual != expected:
        differences = [(i, a, b) for i, (a, b) in enumerate(zip(expected, actual)) if a != b]
        raise ValueError(f'Windows/wasm numeric or binary mismatch in {output}; lengths {len(expected)}/{len(actual)}, first differences: {differences[:12]}')
    print(f'Windows/wasm binary parity passed ({len(actual)} bytes): {output}')
    (output / 'report.json').write_text(json.dumps({'windows_wasm_parity': 'pass',
        'bytes': len(actual), 'scripts': ['menu', 'map01', 'intro'],
        'rendering_and_gameplay': 'not tested'}, indent=2) + '\n', encoding='utf8')
    return output


if __name__ == '__main__':
    verify_semantics()
