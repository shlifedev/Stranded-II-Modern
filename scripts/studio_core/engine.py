"""Isolated Extended Studio WASM build, using the existing pinned web toolchain."""
from pathlib import Path
import blitz3d_toolchain as engine_toolchain
import json
import os
import re
import shutil
import sys

import web_build
import web_toolchain
from editions import override_files, INCLUDE
from locales import prepare, validate
from language_pack import refresh
from versioning import development_marker, local_info

from .project import ROOT, atomic, encode

BUILD = ROOT/'build/studio'
RUNTIME = BUILD/'runtime'
SITE = BUILD/'engine'
ENTRY = 'StrandedIIStudio.bb'


def configure():
    # This command runs in its own CLI process, never the ordinary web builder.
    # Keep its compiler and allowlisted packer; redirect only Studio artifacts.
    web_build.BUILD, web_build.RUNTIME, web_build.SITE = BUILD, RUNTIME, SITE
    os.environ['PATH'] = str(Path(sys.executable).parent) + os.pathsep + os.environ.get('PATH','')


def setup(args):
    configure()
    candidates = [BUILD/'dependencies.local.json', ROOT/'build/web/dependencies.local.json', ROOT/'build/dependencies.local.json']
    previous = next((json.loads(p.read_text('utf8')) for p in candidates if p.is_file()), {})
    settings = {key: str(getattr(args,key).resolve()) if getattr(args,key,None) else previous.get(key) for key in ('game','font')}
    if not all(settings.values()):
        raise ValueError('Studio setup needs --game OFFICIAL_GAME --font PINNED_FONT on first use')
    web_build.prepare_assets(settings)
    validate(ROOT,'extended')
    prepare(ROOT,Path(settings['game']),RUNTIME,'extended')
    for relative,source in override_files(ROOT,'extended').items():
        target=RUNTIME/relative;target.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(source,target)
    refresh(ROOT,RUNTIME,Path(settings['font']),'extended')
    shutil.copy2(ROOT/'config/editions/extended/modern.ini',RUNTIME/'core/modern.ini')
    atomic(BUILD/'dependencies.local.json',encode(settings))
    if not (web_toolchain.paths()[1]/'.emscripten').is_file():
        web_toolchain.prepare()
    print('Prepared Extended Studio runtime:',RUNTIME)


def source_hashes():
    result = {}
    def visit(relative):
        path=ROOT/'src/engine'/relative
        key=path.relative_to(ROOT).as_posix()
        if key in result:return
        if not path.resolve().is_relative_to((ROOT/'src/engine').resolve()):
            raise ValueError('Studio Include escapes engine sources')
        if 'editions/classic/' in key:raise ValueError('Studio must use Extended modules')
        result[key]=web_build.digest(path)
        for child in INCLUDE.findall(path.read_text('utf8')):visit(child.replace('\\','/'))
    visit(ENTRY)
    return result


def implementation_hashes():
    paths = [ROOT/'studio/engine/index.html',ROOT/'studio/engine/studio.js',ROOT/'studio/engine/gizmo.js',ROOT/'src/web/debugui.js',Path(__file__),
             ROOT/'scripts/web_build.py',ROOT/'scripts/web_toolchain.py',ROOT/'scripts/web_audio.py',
             ROOT/'config/web-toolchain.json',ROOT/'src/native/web/bridge.cpp']
    return {**{p.relative_to(ROOT).as_posix():web_build.digest(p) for p in paths}, **engine_toolchain.sources()}


def build(args):
    configure()
    if not (BUILD/'dependencies.local.json').is_file():setup(args)
    env=web_toolchain.build()
    upstream=web_toolchain.upstream()
    ng=Path(upstream.NG)
    SITE.mkdir(parents=True,exist_ok=True)
    native=BUILD/'native';native.mkdir(exist_ok=True)
    for name,source in [('compat',ng/'src/modules/bb/graphics.webgpu/web_compat.cpp'),('bridge',ROOT/'src/native/web/bridge.cpp')]:
        argv=[sys.executable,web_toolchain.paths()[1]/'upstream/emscripten/emcc.py',source,'-c','-O2','-std=c++17','-fwasm-exceptions','-o',native/(name+'.o')]
        for include in (engine_toolchain.BUILD/'web/cmake/src/modules',ng/'src/modules',ng/'src/modules/bb/pixmap',ng/'src',ng/'src/modules/bb/graphics.webgpu'):
            argv+=['-I',include]
        web_toolchain.run(argv,env=env)
    info=local_info(ROOT,'extended')
    development_marker(RUNTIME,True)
    atomic(RUNTIME/'core/version.json',encode(info))
    atomic(RUNTIME/'core/version.txt',(info['display_version']+'\n').encode())
    # License originals accompany the generated engine/resources.
    for source,name in ((engine_toolchain.ENGINE/'WEBGPU-LICENSE.txt','WebGPU-port-MIT.txt'),(ng/'LICENSE.TXT','Blitz3D-NG.txt'),
                        (engine_toolchain.library('imgui') / 'LICENSE.txt','Dear-ImGui.txt'),
                        (engine_toolchain.library('sdl') / 'LICENSE.txt','SDL2.txt'),
                        (engine_toolchain.library('freetype2') / 'docs/FTL.TXT','FreeType.txt'),(engine_toolchain.library('ogg') / 'COPYING','Ogg.txt'),
                        (engine_toolchain.library('vorbis') / 'COPYING','Vorbis.txt'),(engine_toolchain.library('minimp3') / 'LICENSE','minimp3.txt'),
                        (engine_toolchain.library('zlib') / 'README','zlib.txt'),(web_toolchain.paths()[1]/'upstream/emscripten/LICENSE','Emscripten.txt')):
        shutil.copy2(source,RUNTIME/'licenses'/name)
    web_toolchain.run([upstream.BLITZCC,'-target','emscripten','-o',SITE/'game',ENTRY],
                      cwd=ROOT/'src/engine',env=web_build.compiler_environment(env))
    # Include Extended resources in the existing allowlisted packer without
    # changing normal web/CI edition routing.
    original=web_build.override_files
    web_build.override_files=lambda root,edition='classic': original(root,'extended')
    try:web_build.pack_assets(env)
    finally:web_build.override_files=original
    for name in ('index.html','studio.js','gizmo.js'):
        shutil.copy2(ROOT/'studio/engine'/name,SITE/name)
    shutil.copy2(ROOT/'src/web/debugui.js',SITE/'debugui.js')
    (SITE/'game.html').unlink(missing_ok=True)
    report={'edition':'extended','entrypoint':ENTRY,'source_sha256':source_hashes(),
            'implementation_sha256':implementation_hashes(),
            'files':{n:web_build.digest(SITE/n) for n in ('game.js','game.wasm','assets.js','assets.data','index.html','studio.js','gizmo.js','debugui.js')}}
    atomic(BUILD/'build-report.json',encode(report))
    print('Built Blitz Studio engine:',SITE)


def check(args):
    configure()
    report=json.loads((BUILD/'build-report.json').read_text('utf8'))
    if report['source_sha256']!=source_hashes():raise ValueError('Studio engine sources changed; rebuild first')
    if report.get('implementation_sha256')!=implementation_hashes():raise ValueError('Studio build inputs changed; rebuild first')
    for name,expected in report['files'].items():
        if web_build.digest(SITE/name)!=expected:raise ValueError('Changed Studio artifact: '+name)
    validate(ROOT,'extended')
    loader=(SITE/'assets.js').read_text('utf8')
    metadata=json.loads(re.search(r'loadPackage\((\{"files":.*)\);',loader).group(1))
    original=web_build.override_files
    web_build.override_files=lambda root,edition='classic': original(root,'extended')
    try:
        allowed=web_build.asset_files()
    finally:web_build.override_files=original
    if {f['filename'] for f in metadata['files']}!={target for _,target in allowed}:
        raise ValueError('Studio archive differs from its allowlisted resources')
    from web_audio import verify
    verify(allowed,BUILD/'package',BUILD/'audio')
    print(json.dumps({'engine_artifacts':'passed','extended_catalogs':'passed','allowlisted_assets':len(allowed),
                      'graphical_check':'not_run'},indent=2))
