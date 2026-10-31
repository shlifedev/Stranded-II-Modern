"""Check fresh-install defaults and personal settings with the real Blitz loader."""
from pathlib import Path
import blitz3d_toolchain as engine
import argparse
import json
import os
import struct
import subprocess
from verify_timing import function


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--blitz', type=Path, help=argparse.SUPPRESS)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    output = args.output.resolve()
    (output / 'sys').mkdir(parents=True, exist_ok=True)
    includes = Path(__file__).resolve().parents[2] / 'src/engine/includes'
    variables = (includes / 'load_vars.bb').read_text('utf8').splitlines()
    body = '\n'.join(line for line in variables
                     if line.startswith('Global set_') and
                     line.split(';', 1)[0].strip().split()[1].split(',')[0].rstrip('#$') in (
                         'set_scrx', 'set_scry', 'set_scrbit', 'set_msens',
                         'set_minvert', 'set_msmooth', 'set_viewrange',
                         'set_terrain', 'set_water', 'set_sky', 'set_effects',
                         'set_grass', 'set_2dfx', 'set_lightfx', 'set_windsway',
                         'set_fog', 'set_hwmultitex', 'set_mb', 'set_mb_alpha',
                         'set_musicvolume', 'set_fxvolume', 'set_name', 'set_serverport'))
    body += '\nGlobal modern_hwnd\n'
    body += 'Global game_scriptkeys,set_moddir$,set_rootdir$=SystemProperty("APPDIR")\nConst Ckey_devmenu=21\n'
    body += 'Dim in_keys(21)\nDim game_scriptkey(20)\n'
    settings_source = (includes / 'load_settings.bb').read_text('utf8')
    body += (includes / 'modern_files.bb').read_text('utf8') + '\n'
    body += function(settings_source, 'load_settings') + '\n'
    body += function(settings_source, 'save_settings') + '\n'
    body += function(settings_source, 'settings_apply_viewrange') + '\n'
    # Only graphics refreshes are substituted; both real settings file paths run.
    body += '\nFunction mp_checkname$(value$,mode)\nReturn value$\nEnd Function\n'
    for name in ('mb_reset', 'e_environment_setup_water', 'grass_map', 'grass_heightmap', 'grass_spread'):
        body += f'Function {name}()\nEnd Function\n'
    body += '''
If CommandLine$()="devmenu" Then
 Local binding_report=WriteFile("devmenu-settings-result.txt")
 load_settings():WriteLine binding_report,in_keys(Ckey_devmenu)
 in_keys(Ckey_devmenu)=0:save_settings():load_settings()
 WriteLine binding_report,in_keys(Ckey_devmenu)
 in_keys(Ckey_devmenu)=63:save_settings():load_settings()
 WriteLine binding_report,in_keys(Ckey_devmenu)
 CloseFile binding_report
 End
EndIf
If CommandLine$()="seed" Then
 load_settings()
 Local seed_report=WriteFile("seed-result.txt")
 WriteLine seed_report,in_keys(0)
 WriteLine seed_report,set_msens#
 CloseFile seed_report
 End
EndIf
If CommandLine$()="roundtrip" Then
 load_settings()
 Local range_report=WriteFile("viewrange-result.txt")
 For choice=-1 To 4
  set_viewrange=choice
  save_settings()
  WriteLine range_report,set_viewfac#
  load_settings()
  WriteLine range_report,set_viewfac#
 Next
 CloseFile range_report
 End
EndIf
'''
    fields = ('set_scrx', 'set_scry', 'set_scrbit', 'set_viewrange',
              'set_terrain', 'set_water', 'set_sky', 'set_effects',
              'set_musicvolume#', 'set_fxvolume#', 'set_grass', 'set_2dfx',
              'set_lightfx', 'set_windsway', 'set_fog', 'set_hwmultitex',
              'set_mb', 'set_mb_alpha#', 'set_viewfac#')
    body += '\nload_settings()\nLocal report=WriteFile("settings-result.txt")\n'
    body += ''.join(f'WriteLine report,{field}\n' for field in fields)
    body += 'CloseFile report\nEnd\n'
    source = output / 'settings-test.bb'
    source.write_text(body, encoding='utf8')
    exe = output / 'settings-test.exe'
    engine.compile_test(source, exe, cwd=output)
    # Old controls retain their binary prefix; only the tagged developer extension is new.
    controls = output / 'sys/controls.cfg'
    marker = output / 'core/development.flag'
    if controls.exists():controls.unlink()
    if marker.exists():marker.unlink()
    subprocess.run([str(exe), 'devmenu'], cwd=output, check=True, timeout=15,
                   creationflags=subprocess.CREATE_NO_WINDOW)
    values = (output / 'devmenu-settings-result.txt').read_text('utf8').splitlines()
    if values != ['0', '0', '63']:
        raise AssertionError(f'Release developer shortcut default/save/reload: {values}')
    controls.unlink();marker.parent.mkdir(exist_ok=True);marker.write_text('local\n')
    subprocess.run([str(exe), 'devmenu'], cwd=output, check=True, timeout=15,
                   creationflags=subprocess.CREATE_NO_WINDOW)
    values = (output / 'devmenu-settings-result.txt').read_text('utf8').splitlines()
    if values != ['210', '0', '63']:
        raise AssertionError(f'Local developer shortcut default/explicit None/custom key: {values}')
    marker.unlink();controls.unlink()
    print('Developer shortcut release/local defaults and saved None/custom binding: passed')
    # Release ZIPs ship .default copies. A fresh folder receives them; an update
    # extracted over an existing game must leave the player's files alone.
    def controls_bytes(first_key, sensitivity):
        return (b'### controls\n' + struct.pack('<21h', *range(first_key, first_key + 21)) +
                struct.pack('<f', sensitivity) + bytes((129, 0)))
    seeded = {
        controls: controls_bytes(17, 0.25),
        output / 'sys/scriptcontrols.cfg': b'### script controls\n' + bytes(42),
        output / 'core/modern.ini': b'language=ko\n',
    }
    defaults = {path: path.with_name(path.stem + '.default' + path.suffix) for path in seeded}
    (output / 'core').mkdir(exist_ok=True)
    for path, data in seeded.items():
        defaults[path].write_bytes(data)
        if path.exists():path.unlink()
    for label, expected in (('fresh release folder', ['17', '0.25']), ('update over personal settings', ['44', '0.5'])):
        subprocess.run([str(exe), 'seed'], cwd=output, check=True, timeout=15,
                       creationflags=subprocess.CREATE_NO_WINDOW)
        values = (output / 'seed-result.txt').read_text('ascii').splitlines()
        if [values[0], str(float(values[1]))] != expected:
            raise AssertionError(f'{label}: loaded {values}; expected {expected}')
        for path, data in seeded.items():
            if path.read_bytes() != data:
                raise AssertionError(f'{label}: unexpected {path.name}')
            if path.with_name(path.name + '.tmp').exists():
                raise AssertionError(f'{label}: staging file left for {path.name}')
        # The player's own settings now differ from every default.
        seeded = {controls: controls_bytes(44, 0.5),
                  output / 'sys/scriptcontrols.cfg': b'### script controls\n' + bytes(range(42)),
                  output / 'core/modern.ini': b'language=en\nfps=60\n'}
        for path, data in seeded.items():
            path.write_bytes(data)
        print('Release default settings passed:', label)
    for path, default in defaults.items():
        path.unlink();default.unlink()
    # This file follows the original settings format and represents a player who
    # chose lower quality, different volumes, and disabled motion blur.
    custom = ('### personal settings\n1024\n768\n16\n1\n0\n2\n1\n0\n'
              '0.2\n0.75\n1\n0\n1\n0\nPlayer\n36963\n0\n0\n0\n0.12\n').encode('ascii')
    cases = [
        ('fresh install', None,
         (800, 600, 32, 4, 2, 6, 2, 2, 0.5, 0.5, 4, 1, 1, 1, 1, 1, 1, 0.35, 4.0)),
        ('existing personal settings', custom,
         (1024, 768, 16, 1, 0, 2, 1, 0, 0.2, 0.75, 1, 0, 1, 0, 0, 0, 0, 0.12, 1.0)),
    ]
    path = output / 'sys/settings.cfg'
    for label, value, expected in cases:
        if value is not None:
            path.write_bytes(value)
        elif path.exists():
            path.unlink()
        subprocess.run([str(exe)], cwd=output, check=True, timeout=15,
                       creationflags=subprocess.CREATE_NO_WINDOW)
        actual = [float(line) for line in (output / 'settings-result.txt').read_text('ascii').splitlines()]
        if len(actual) != len(expected) or any(abs(a - b) > 0.00001 for a, b in zip(actual, expected)):
            raise ValueError(f'{label}: {dict(zip(fields, actual))}; expected {expected}')
        if value is None:
            assert not path.exists(), 'Loading defaults must not create or replace player settings'
        else:
            assert path.read_bytes() == value, 'Loading must preserve the existing settings file'
        print('Blitz settings loader passed:', label)
    subprocess.run([str(exe), 'roundtrip'], cwd=output, check=True, timeout=15,
                   creationflags=subprocess.CREATE_NO_WINDOW)
    actual = [float(line) for line in (output / 'viewrange-result.txt').read_text('ascii').splitlines()]
    expected = [value for factor in (1.5, 0.5, 1.0, 1.5, 2.5, 4.0) for value in (factor, factor)]
    if actual != expected:
        raise ValueError(f'View distance must match after save and reload: {actual}; expected {expected}')
    print('Blitz settings save/reload passed: every view distance and invalid-value fallback')
    (output / 'report.json').write_text(json.dumps({
        'fresh_install_defaults': 'pass', 'personal_settings_preservation': 'pass',
        'view_distance_save_reload': 'pass', 'release_default_seeding': 'pass',
        'graphics_and_audio_output': 'not tested by this harness',
    }) + '\n', encoding='utf8')


if __name__ == '__main__':
    main()
