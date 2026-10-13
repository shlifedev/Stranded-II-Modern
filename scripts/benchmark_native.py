"""Prepare/run a reproducible game scene and native GPU option sweep in build/."""
from pathlib import Path
import argparse
import csv
import json
import random
import re
import shutil
import statistics
import subprocess
import tempfile

import blitz3d_toolchain as engine
from editions import Edition, NAMES, override_files, source_hashes
from verify_timing import function

ROOT = engine.ROOT


def write_json(path, value):
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2) + '\n', encoding='utf8')


def replace_once(text, old, new):
    if text.count(old) != 1:
        raise ValueError(f'Benchmark source anchor changed: {old!r}')
    return text.replace(old, new, 1)


def instrument(source):
    """Instrument only this disposable copy. Keep production game functions."""
    path = source / 'includes/engine_init.bb'
    path.write_text(replace_once(path.read_text('utf8'), '\nunrealsoftware()\n', '\n'), encoding='utf8')
    path = source / 'includes/game.bb'
    text = path.read_text('utf8')
    for slot, call in enumerate(('UpdateWorld(f#)', 'cull()', 'e_environment_update_water()',
                                 'RenderWorld()', 'mb_update()', 'grass_spread()')):
        tail = ':bench_triangles=TrisRendered()' if slot == 3 else ''
        if slot == 1:
            tail = ':m_menu=0:g_unpaused=1'  # Suppress scripted diary overlays in the test scene.
        text = replace_once(text, call, f'bench_begin({slot}):{call}:bench_end({slot}){tail}')
    text = replace_once(text, '\tgame_setcam()', '\tgame_setcam():bench_camera()')
    text = text.replace('MoveMouse set_scrx/2,set_scry/2', '; Benchmark does not warp the pointer.')
    path.write_text(text, encoding='utf8')
    path = source / 'includes/e_environment.bb'
    text = path.read_text('utf8')
    water = function(text, 'e_environment_update_water')
    cube = 'RenderWorldCube env_watercam,env_watertex'
    if water.count(cube) == 1:
        marked = water.replace(cube, 'bench_begin(6):'+cube+':bench_end(6)'
                               ':bench_reflections=bench_reflections+6')
    elif water.count('\tRenderWorld\n') == 6:
        marked = water.replace('\tRenderWorld\n', '\tbench_begin(6):RenderWorld:bench_end(6)'
                               ':bench_reflections=bench_reflections+1\n')
    else:
        raise ValueError('Expected a six-face water capture or six legacy reflection views.')
    path.write_text(text.replace(water, marked), encoding='utf8')
    text = path.read_text('utf8')
    planar = 'RenderWorldPlanar cam,env_watertex,0'
    if planar in text:
        text = replace_once(text, planar, 'bench_begin(6):'+planar+':bench_end(6)'
                            ':bench_reflections=bench_reflections+1')
    path.write_text(text, encoding='utf8')
    path = source / 'includes/modern_runtime.bb'
    text = path.read_text('utf8')
    original = function(text, 'modern_focus')
    text = text.replace(original, '''Function modern_focus()
 If modern_hwnd<>0 Then
  If modern_win_foreground()<>modern_hwnd Or modern_win_iconic(modern_hwnd) Then bench_focus_lost=1
  If modern_win_iconic(modern_hwnd) Then bench_minimized=1
 EndIf
 Return 0
End Function''')
    path.write_text(text, encoding='utf8')
    path = source / 'includes/input.bb'
    text = path.read_text('utf8').replace('modern_mouse_capture(capturemouse)', 'modern_mouse_capture(0)')
    original = function(text, 'getinput')
    reset = '''
 Local benchInput
 For benchInput=0 To 41
  in_keyhit(benchInput)=0:in_keydown(benchInput)=0:in_keyrelease(benchInput)=0
 Next
 For benchInput=1 To 5
  in_mhit(benchInput)=0:in_mdown(benchInput)=0:in_mrelease(benchInput)=0
 Next
 in_mxs=0:in_mys=0:in_mzs=0:in_escape=0:in_key=0
 in_mx=-1000:in_my=-1000
End Function'''
    path.write_text(text.replace(original, original.removesuffix('End Function') + reset), encoding='utf8')
    shutil.copy2(ROOT / 'tests/native_stress.bb', source / 'includes/engine_loop.bb')
    for path in source.rglob('*.bb'):
        text = path.read_text('utf8')
        path.write_text(text.replace('RuntimeError', 'bench_fail'), encoding='utf8')


def compile_benchmark(folder, edition):
    profile = Edition(edition)
    source, target = folder / 'src', folder / 'runtime'
    shutil.copytree(ROOT / 'src/engine', source, dirs_exist_ok=True)
    instrument(source)
    engine.build_compiler()
    engine.build_native(True)
    exe = target / 'StrandedII-benchmark.exe'
    engine.compile(source / profile.entry, exe, graphics=True, cwd=source)
    engine.install_windows_runtime(target)
    settings = target / 'mods/Stranded II/sys/settings.cfg'
    metadata = {
        'edition': edition,
        'commit': subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=ROOT, text=True).strip(),
        'source_sha256': source_hashes(ROOT, edition), 'engine_sha256': engine.sources(),
        'exe_sha256': engine.digest(exe),
        'fixture_sha256': engine.digest(ROOT / 'tests/native_stress.bb'),
        'baseline_settings': settings.read_text('utf8') if settings.exists() else 'defaults',
        'clock': 'QPC deltas; real frame intervals including Flip(False)',
        'completion': 'ReadPixel GPU readback drains before and after each measured interval; optional per-frame drain control',
        'focus': 'Test copy renders without focus; records focus status and rejects minimized runs',
        'cpu_phases': 'Elapsed host time including driver waits, not GPU timestamp measurements',
        'storage': 'Isolated build folder; no user saves or personal maps',
        'scene_seed': 57013,
    }
    write_json(folder / 'metadata.json', metadata)


def prepare(edition):
    profile = Edition(edition)
    runtime = profile.runtime(ROOT)
    folder = Path(tempfile.mkdtemp(prefix='benchmark-', dir=ROOT / 'build'))
    target = folder / 'runtime'
    # The official resource manifest excludes personal maps, saves and caches.
    names = set(json.loads((ROOT / 'config/game-files.json').read_text('utf8')))
    names.update(override_files(ROOT, edition))
    for relative in sorted(names):
        src = runtime / relative
        if not src.is_file():
            raise ValueError(f'Missing prepared resource: {src}')
        dst = target / relative
        dst.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(src, dst)
    shutil.copytree(runtime / 'core', target / 'core', dirs_exist_ok=True,
                    ignore=shutil.ignore_patterns('*.log'))
    # Copy this edition's settings as the baseline, never across editions.
    settings = runtime / 'mods/Stranded II/sys/settings.cfg'
    if settings.exists():
        shutil.copy2(settings, target / 'mods/Stranded II/sys/settings.cfg')
    for name in ('samples', 'captures', 'mods/Stranded II/saves'):
        (target / name).mkdir(parents=True, exist_ok=True)
    compile_benchmark(folder, edition)
    return folder


def scenarios(suite):
    base = dict(scene='stress', count=768, water=6, grass=4, view=4, terrain=2,
                wind=1, blur=0, effects=2, fog=1, pitch=0, fps=1000000, sync=0)
    variants = {
        'baseline': {}, 'sky': {'pitch': -85},
        'water_3': {'water': 3}, 'water_4': {'water': 4}, 'water_5': {'water': 5},
        'grass_0': {'grass': 0}, 'grass_2': {'grass': 2},
        'view_2': {'view': 2}, 'terrain_0': {'terrain': 0},
        'wind_0': {'wind': 0}, 'blur_1': {'blur': 1},
        'objects_0': {'count': 0}, 'objects_256': {'count': 256},
        'objects_1536': {'count': 1536},
        'water3_grass0': {'water': 3, 'grass': 0},
        'island': {'scene': 'island'}, 'island_water3': {'scene': 'island', 'water': 3},
        'island_grass0': {'scene': 'island', 'grass': 0},
        'island_sky': {'scene': 'island', 'pitch': -85},
        'coast': {'scene': 'coast', 'pitch': 5},
        'underwater': {'scene': 'underwater'},
        'night': {'scene': 'night'},
        'cap144': {'fps': 144}, 'sync_control': {'sync': 1},
    }
    if suite == 'smoke':
        variants = {key: variants[key] for key in ('baseline', 'sky', 'water_3', 'grass_0', 'sync_control')}
    elif suite == 'resolution':
        variants = {key: variants[key] for key in ('baseline', 'water3_grass0', 'island')}
    return [(name, dict(base, **value)) for name, value in variants.items()]


def summarize(folder):
    results = []
    for path in sorted((folder / 'runtime/samples').glob('*.csv')):
        meta = dict(line.split('=', 1) for line in path.with_suffix('.meta').read_text().splitlines())
        with path.open(encoding='utf8') as stream:
            rows = [{key: float(value) for key, value in row.items()}
                    for row in csv.DictReader(stream)]
        times = sorted(row['frame_ms'] for row in rows)
        if not times:
            continue
        def percentile(p):
            return times[min(len(times)-1, int((len(times)-1)*p))]
        complete = len(rows) == int(meta['frames']) and float(meta['completed_ms']) >= sum(times)-1
        valid_scene = meta.get('schema') == '2' and meta.get('menu') == '0'
        results.append(dict(name=path.stem, valid=meta['minimized']=='0' and complete and valid_scene, frames=len(rows),
            fps=1000*len(rows)/float(meta['completed_ms']), submitted_fps=1000*len(rows)/sum(times),
            p95_ms=percentile(.95), p99_ms=percentile(.99),
            mean={key: statistics.mean(row[key] for row in rows) for key in rows[0]},
            metadata=meta))
    write_json(folder / 'results.json', results)
    by_name = {row['name']: row for row in results}
    comparisons = []
    for path in sorted(folder.glob('*plan-*.json')):
        cases = {}
        for trial in json.loads(path.read_text('utf8')):
            if 'case' in trial:
                cases.setdefault(trial['case'], []).append(trial)
        for case, planned in cases.items():
            valid = [by_name[row['name']] for row in planned
                     if row['name'] in by_name and by_name[row['name']]['valid']]
            if not valid:
                continue
            comparisons.append(dict(plan=path.name, case=case, repeats=len(valid),
                complete=len(valid)==len(planned),
                options={key: value for key, value in planned[0].items() if key not in ('name','case')},
                fps_median=statistics.median(row['fps'] for row in valid),
                fps_min=min(row['fps'] for row in valid), fps_max=max(row['fps'] for row in valid),
                p95_ms_median=statistics.median(row['p95_ms'] for row in valid),
                phase_ms_median={key: statistics.median(row['mean'][key] for row in valid)
                                 for key in valid[0]['mean'] if key.endswith('_ms')}))
    write_json(folder / 'summary.json', comparisons)
    for row in results:
        print(f"{row['name']}: {row['fps']:.1f} FPS, p95 {row['p95_ms']:.2f} ms, valid={row['valid']}")
    return results


def run(folder, args):
    folder = folder.resolve()
    if not folder.is_relative_to((ROOT / 'build').resolve()):
        raise ValueError('Benchmark folder must be inside build/.')
    target = folder / 'runtime'
    config = target / 'core/modern.ini'
    render_flags = {key: int(key in args.render.split(',')) for key in ('water','lighting','shadows','atmosphere')}
    antialias = {'off':0,'on':1,'msaa4x':4}[args.antialias]
    config.write_text(f'fps=144\ndisplay=borderless\nresolution=custom\nrender_width={args.width}\n'
                      f'render_height={args.height}\nlanguage=ko\ndiagnostics=0\n'
                      + ''.join(f'render_{key}={value}\n' for key, value in render_flags.items())
                      + f'render_antialias={antialias}\n', encoding='utf8')
    cases = scenarios(args.suite)
    if args.cases:
        selected = args.cases.split(',')
        unknown = set(selected) - {name for name, _ in cases}
        if unknown:
            raise ValueError(f'Unknown cases for {args.suite}: {sorted(unknown)}')
        cases = [(name, options) for name, options in cases if name in selected]
    prefix = f'{args.label}_' if args.label else ''
    plan, rows = [], []
    for repeat in range(args.repeats):
        ordered = cases[:]
        if repeat:
            random.Random(57013+repeat).shuffle(ordered)
        for case, options in ordered:
            name = f'{prefix}{args.width}x{args.height}_{case}_r{repeat+1}'
            if (target / 'samples' / (name+'.csv')).exists():
                raise ValueError(f'Result already exists: {name}; use a new --label.')
            values = [name, *(options[key] for key in ('scene','count','water','grass','view','terrain',
                        'wind','blur','effects','fog','pitch','fps','sync')), args.warmup*1000,args.seconds*1000,
                      int(repeat==0 and case in ('baseline','island','sky','coast','underwater','night'))]
            plan.append(','.join(map(str, values)))
            rows.append(dict(name=name, case=case, **options, width=args.width, height=args.height,
                             warmup_seconds=args.warmup, measure_seconds=args.seconds, render=render_flags,
                             antialias=antialias))
    write_json(folder / f'{prefix}plan-{args.width}x{args.height}.json', rows)
    shutil.copy2(folder / 'metadata.json', folder / f'{prefix}metadata-{args.width}x{args.height}.json')
    log = folder / f'{prefix}run-{args.width}x{args.height}.log'
    print('Running:', folder, flush=True)
    with log.open('w', encoding='utf8') as output:
        # A fresh process per trial prevents retained scene/driver state from
        # biasing later options. Keep the generated maps and immutable resources.
        for index, (line, row) in enumerate(zip(plan, rows), 1):
            (target / 'benchmark-plan.csv').write_text(line+'\n', encoding='utf8')
            print(f"[{index}/{len(rows)}] {row['name']}", flush=True)
            output.write(f"\nTRIAL {row['name']}\n")
            output.flush()
            subprocess.run([str(target/'StrandedII-benchmark.exe')], cwd=target, stdout=output,
                           stderr=subprocess.STDOUT, check=True,
                           timeout=240+args.warmup+args.seconds)
            if not (target / 'samples' / (row['name']+'.meta')).is_file():
                raise RuntimeError(f"Trial failed: {row['name']}; inspect {log}")
    results = summarize(folder)
    by_name = {row['name']: row for row in results}
    if any(row['name'] not in by_name or not by_name[row['name']]['valid'] for row in rows):
        raise RuntimeError('Benchmark incomplete, minimized or showing a menu; inspect the results before using them.')
    for row in rows:
        meta = by_name[row['name']]['metadata']
        expected = ','.join(str(row[key]) for key in
                            ('water','grass','view','terrain','wind','blur','effects','fog'))
        if (int(meta['width']), int(meta['height']), meta['options']) != (args.width, args.height, expected):
            raise RuntimeError(f"Effective graphics settings differ from the plan: {row['name']}")
        expected_render = ','.join(str(value) for value in render_flags.values())
        if ('renderer' in meta or any(render_flags.values())) and meta.get('renderer') != expected_render:
            raise RuntimeError(f"Effective renderer enhancements differ from the plan: {row['name']}")
        if meta.get('antialias') != str(antialias):
            raise RuntimeError(f"Effective FXAA setting differs from the plan: {row['name']}")
    maps = target / 'mods/Stranded II/maps'
    write_json(folder / f'{prefix}scenes-{args.width}x{args.height}.json',
               {path.relative_to(target).as_posix(): engine.digest(path)
                for path in [*sorted(maps.glob('stress_*.s2')), maps/'adventure/map02.s2']})


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--edition', choices=NAMES, default='classic')
    parser.add_argument('--existing', type=Path)
    parser.add_argument('--run', action='store_true')
    parser.add_argument('--rebuild', action='store_true', help='Rebuild the existing isolated source copy')
    parser.add_argument('--summarize', action='store_true')
    parser.add_argument('--suite', choices=('smoke','full','resolution'), default='full')
    parser.add_argument('--cases', help='Comma-separated case names from the selected suite')
    parser.add_argument('--label', default='', help='Unique alphanumeric result prefix (hyphens allowed)')
    parser.add_argument('--width', type=int, default=2048)
    parser.add_argument('--height', type=int, default=1152)
    parser.add_argument('--render', default='', help='Comma-separated enhancements: water,lighting,shadows,atmosphere')
    parser.add_argument('--antialias', choices=('off','on','msaa4x'), default='on', help='Scene AA: off, FXAA (on), or MSAA 4x; independent of --render')
    parser.add_argument('--repeats', type=int, default=3)
    parser.add_argument('--warmup', type=int, default=3)
    parser.add_argument('--seconds', type=int, default=8)
    args = parser.parse_args()
    if set(filter(None, args.render.split(','))) - {'water','lighting','shadows','atmosphere'}:
        parser.error('Unknown renderer enhancement.')
    if args.label and not re.fullmatch(r'[A-Za-z0-9-]+', args.label):
        parser.error('Labels may only contain ASCII letters, digits and hyphens.')
    if not (800<=args.width<=8192 and 600<=args.height<=8192 and 1<=args.repeats<=10
            and 1<=args.warmup<=30 and 1<=args.seconds<=60):
        parser.error('Invalid benchmark dimensions, repeat count or duration.')
    folder = args.existing.resolve() if args.existing else prepare(args.edition)
    if not folder.is_relative_to((ROOT / 'build').resolve()):
        parser.error('Benchmark folder must be inside build/.')
    if args.existing:
        metadata = folder / 'metadata.json'
        if not metadata.is_file() or not (folder / 'runtime/StrandedII-benchmark.exe').is_file():
            parser.error('--existing must refer to a prepared benchmark folder.')
        if json.loads(metadata.read_text('utf8'))['edition'] != args.edition:
            parser.error('--edition must match the existing benchmark folder.')
    if args.existing and args.rebuild:
        compile_benchmark(folder, args.edition)
    print('Benchmark:', folder, flush=True)
    if args.run:
        run(folder, args)
    elif args.summarize:
        summarize(folder)


if __name__ == '__main__':
    main()
