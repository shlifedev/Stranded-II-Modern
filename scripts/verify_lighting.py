"""Run the actual Blitz sun/ambient function against captured renderer calls.

Checks brightness budgets, coloured skies, option combinations and minute-by-minute
day/night continuity without a graphics window. GPU pixels are checked separately
by verify_engine.py --render.
"""
from pathlib import Path
import argparse
import csv
import json
import math
import re
import subprocess

import blitz3d_toolchain as engine
from verify_timing import function


SKIES = ((255, 255, 255), (255, 100, 50), (70, 120, 200), (0, 55, 40))
FIXTURE = r'''
Global map_hour,map_minute,env_light=42,gt=1234567
Global test_lighting,test_air,test_time#,test_rotates,test_lights,test_ambients
Global test_pitch#,test_yaw#,test_sunr#,test_sung#,test_sunb#,test_ar#,test_ag#,test_ab#
Dim remaster_enabled(4),env_col(2)
Global report=WriteFile(SystemProperty("APPDIR")+"lighting.csv")
Function test_effects(lighting,air,time#)
 test_lighting=lighting:test_air=air:test_time=time
End Function
Function test_rotate(h,pitch#,yaw#,roll#)
 test_rotates=test_rotates+1:test_pitch=pitch:test_yaw=yaw
End Function
Function test_light(h,r#,g#,b#)
 test_lights=test_lights+1:test_sunr=r:test_sung=g:test_sunb=b
End Function
Function test_ambient(r#,g#,b#)
 test_ambients=test_ambients+1:test_ar=r:test_ag=g:test_ab=b
End Function
For sky=0 To 3
 Select sky
  Case 0:env_col(0)=255:env_col(1)=255:env_col(2)=255
  Case 1:env_col(0)=255:env_col(1)=100:env_col(2)=50
  Case 2:env_col(0)=70:env_col(1)=120:env_col(2)=200
  Case 3:env_col(0)=0:env_col(1)=55:env_col(2)=40
 End Select
 For mode=0 To 3
  remaster_enabled(1)=mode And 1:remaster_enabled(2)=(mode Shr 1) And 1
  For air=0 To 1
   remaster_enabled(3)=air
   For minute=0 To 1439
    map_hour=minute/60:map_minute=minute Mod 60
    test_rotates=0:test_lights=0:test_ambients=0
    test_pitch=777:test_yaw=777
    test_sunr=777:test_sung=777:test_sunb=777:test_ar=777:test_ag=777:test_ab=777
    remaster_frame()
    WriteLine report,sky+","+mode+","+air+","+minute+","+test_lighting+","+test_air+","+test_time+","+test_rotates+","+test_lights+","+test_ambients+","+test_pitch+","+test_yaw+","+test_sunr+","+test_sung+","+test_sunb+","+test_ar+","+test_ag+","+test_ab
   Next
  Next
 Next
Next
CloseFile report
End
'''


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    output = args.output.resolve()
    output.mkdir(parents=True, exist_ok=False)
    source = (engine.ROOT / 'src/engine/includes/remaster.bb').read_text('utf8')
    body = FIXTURE + '\n' + function(source, 'remaster_frame')
    for name, replacement in {'RenderEffects': 'test_effects', 'RotateEntity': 'test_rotate',
                              'LightColor': 'test_light', 'AmbientLight': 'test_ambient'}.items():
        body = re.sub(r'\b' + name + r'\b', replacement, body)
    fixture, exe = output / 'lighting.bb', output / 'lighting.exe'
    fixture.write_text(body, encoding='utf8')
    engine.compile_test(fixture, exe, graphics=False, cwd=output)
    subprocess.run([str(exe)], cwd=output, check=True, timeout=30,
                   creationflags=subprocess.CREATE_NO_WINDOW)
    captured = {}
    with (output / 'lighting.csv').open(encoding='utf8', newline='') as stream:
        for row in csv.reader(stream):
            sky, mode, air, minute = map(int, row[:4])
            values = tuple(map(float, row[4:]))
            assert len(values) == 14 and all(math.isfinite(v) for v in values), row
            captured[sky, mode, air, minute] = values
    assert len(captured) == len(SKIES) * 4 * 2 * 1440
    for (sky, mode, air, minute), values in captured.items():
        lighting, atmosphere, time, rotates, lights, ambients, pitch, yaw, *colours = values
        assert lighting == int(mode != 0) and atmosphere == air and abs(time - 1234.567) < 0.01
        if mode == 0:
            assert (rotates, lights, ambients) == (0, 0, 0) and values[6:] == (777,) * 8
            continue
        assert (rotates, lights, ambients) == (1, 1, 1), (sky, mode, air, minute)
        assert values == captured[sky, 1, 0, minute][:1] + (float(air),) + captured[sky, 1, 0, minute][2:]
        elevation = math.sin(math.radians((minute / 60 - 6) * 15))
        assert abs(pitch - (15 + abs(elevation) * 50)) < 0.001
        assert abs(yaw - (-60 + (minute / 60 - 12) * 8)) < 0.001
        sun, ambient = colours[:3], colours[3:]
        original = tuple(max(c - 55, 0) for c in SKIES[sky])
        assert all(c >= -0.001 for c in colours), (sky, minute, colours)
        if minute <= 360 or minute >= 1080:
            assert all(abs(c - n) < 0.001 for c, n in zip(sun, (16, 20, 27)))
            assert all(abs(c - a * 0.9) < 0.001 for c, a in zip(ambient, original))
        if 420 <= minute <= 1020:
            up = math.sin(math.radians(pitch))
            for s, a, base in zip(sun, ambient, original):
                assert abs(a + s * up - base) < 0.002, (sky, minute, sun, ambient)
                assert s <= 0.30 * base + 0.002 and a + s <= 1.07 * base + 0.002
    # Fixed skies isolate new light discontinuities from the map's own lightcycle.
    max_step = 0
    for sky in range(len(SKIES)):
        for minute in range(1440):
            current = captured[sky, 1, 0, minute][8:]
            following = captured[sky, 1, 0, (minute + 1) % 1440][8:]
            step = max(abs(a - b) for a, b in zip(current, following))
            max_step = max(max_step, step)
            assert step < 1.0, (sky, minute, step)
    noon = captured[0, 1, 0, 720]
    assert all(abs(a - b) < 0.002 for a, b in zip(noon[8:11], (60, 59, 57.5))), noon
    report = {'actual_blitz_samples': len(captured), 'daytime_flat_ground_budget': 'pass',
              'light_shadow_option_combinations': 'pass', 'coloured_and_dark_skies': 'pass',
              'unchanged_night_and_disabled_calls': 'pass', 'max_per_minute_channel_step': max_step,
              'rendering': 'not tested; captured renderer calls'}
    (output / 'report.json').write_text(json.dumps(report, indent=2) + '\n', encoding='utf8')
    print('Sun/ambient lighting checks passed:', json.dumps(report))


if __name__ == '__main__':
    main()
