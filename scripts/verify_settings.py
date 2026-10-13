"""Check fresh-install defaults and personal settings with the real Blitz loader."""
from pathlib import Path
import blitz3d_toolchain as engine
import argparse
import json
import os
import subprocess
from verify_timing import function


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--blitz', type=Path, help=argparse.SUPPRESS)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    output = args.output.resolve()
    (output / 'sys').mkdir(parents=True, exist_ok=True)
    includes = Path(__file__).resolve().parents[1] / 'src/engine/includes'
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
    body += 'Global game_scriptkeys,set_moddir$\n'
    body += 'Dim in_keys(20)\nDim game_scriptkey(20)\n'
    settings_source = (includes / 'load_settings.bb').read_text('utf8')
    body += function(settings_source, 'load_settings') + '\n'
    body += function(settings_source, 'save_settings') + '\n'
    body += function(settings_source, 'settings_apply_viewrange') + '\n'
    # Only graphics refreshes are substituted; both real settings file paths run.
    body += '\nFunction mp_checkname$(value$,mode)\nReturn value$\nEnd Function\n'
    for name in ('mb_reset', 'e_environment_setup_water', 'grass_map', 'grass_heightmap', 'grass_spread'):
        body += f'Function {name}()\nEnd Function\n'
    body += '''
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
        'view_distance_save_reload': 'pass',
        'graphics_and_audio_output': 'not tested by this harness',
    }) + '\n', encoding='utf8')


if __name__ == '__main__':
    main()
