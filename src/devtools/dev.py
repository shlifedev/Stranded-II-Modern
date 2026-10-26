"""Development entry point. Only build/ is generated; src/engine is authoritative."""
from pathlib import Path
import blitz3d_toolchain as engine
import argparse, hashlib, json, os, shutil, subprocess, sys
from locales import prepare, validate
from versioning import development_marker, local_info, validate_info, runtime_info
from editions import Edition, NAMES, source_hashes, override_files

ROOT=Path(__file__).resolve().parents[2]
SHARED_BUILD=ROOT/'build'
BUILD=SHARED_BUILD
PROFILE=Edition('classic')
RUNTIME=BUILD/'runtime'
def sha(path):return hashlib.sha256(path.read_bytes()).hexdigest()
def read(path):return json.loads(path.read_text('utf8'))
def write(path,value):
    path.parent.mkdir(parents=True,exist_ok=True)
    path.write_text(json.dumps(value,ensure_ascii=False,indent=2)+'\n',encoding='utf8')
def settings():
    path=SHARED_BUILD/'dependencies.local.json'
    if not path.exists():raise ValueError('Run setup --game PATH --font PATH first.')
    return read(path)
def checked(path,expected,label):
    if not path.is_file():raise ValueError(f'Missing {label}: {path}')
    if sha(path)!=expected:raise ValueError(f'Unexpected {label} SHA-256: {path}')

def setup(args):
    from language_pack import refresh
    deps=read(ROOT/'config/dependencies.json')
    previous=settings() if (SHARED_BUILD/'dependencies.local.json').exists() else {}
    inputs={name:str(getattr(args,name).resolve()) if getattr(args,name) else previous.get(name) for name in ('game','font')}
    if not all(inputs.values()):raise ValueError('setup requires --game and --font on first use')
    game,font=(Path(inputs[name]) for name in ('game','font'))
    if any(game.resolve().is_relative_to(Edition(name).runtime(ROOT).resolve()) for name in NAMES):raise ValueError('Input game must be an external official English installation')
    checked(font,deps['font_sha256'],'Noto Sans KR font')
    files=read(ROOT/'config/game-files.json')
    for relative,digest in files.items():checked(game/relative,digest,'official game resource')
    catalogs=validate(ROOT,PROFILE.name)
    # Validate before writing. Never import source installation saves or caches.
    BUILD.mkdir(parents=True,exist_ok=True)
    for relative in files:
        target=RUNTIME/relative
        target.parent.mkdir(parents=True,exist_ok=True)
        # User settings must survive repeated setup.
        if target.exists() and (target.suffix=='.cfg' or relative.endswith('/settings.inf')):continue
        shutil.copy2(game/relative,target)
    (RUNTIME/'mods/Stranded II/saves').mkdir(exist_ok=True)
    if not (RUNTIME/'core/modern.ini').exists():shutil.copy2(ROOT/PROFILE.defaults,RUNTIME/'core/modern.ini')
    catalogs,report=prepare(ROOT,game,RUNTIME,PROFILE.name)
    for relative,p in override_files(ROOT,PROFILE.name).items():
        dest=RUNTIME/relative
        if not dest.resolve().is_relative_to(RUNTIME.resolve()):raise ValueError('Override target escapes runtime')
        dest.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(p,dest)
    report.update(refresh(ROOT,RUNTIME,font,PROFILE.name))
    shutil.copytree(ROOT/'licenses',RUNTIME/'licenses',dirs_exist_ok=True)
    write(SHARED_BUILD/'dependencies.local.json',inputs)
    write(BUILD/'setup-report.json',report)
    engine.prepare()
    print(json.dumps(report,ensure_ascii=False));print('Prepared:',RUNTIME)

def localize(args):
    from language_pack import refresh
    inputs=settings()
    report=refresh(ROOT,RUNTIME,Path(inputs['font']),PROFILE.name)
    write(BUILD/'localization-report.json',report)
    print(json.dumps(report,ensure_ascii=False));print('Updated language packs:',RUNTIME/'core/locales')


def build(args):
    settings();validate(ROOT,PROFILE.name)
    if not (RUNTIME/'core/modern.ini').exists():raise ValueError('Run setup for the selected edition first')
    info=validate_info(read(args.version_info)) if args.version_info else local_info(ROOT,PROFILE.name)
    if info['edition']!=PROFILE.name:raise ValueError('Version belongs to another edition')
    if os.environ.get('GITHUB_SHA') and info['commit']!=os.environ['GITHUB_SHA']:
        raise ValueError('Selected version belongs to a different source commit')
    engine.build_compiler()
    engine.build_native(graphics=True)
    dest=RUNTIME/PROFILE.executable
    candidate=BUILD/'StrandedII.next.exe'
    if candidate.exists():candidate.unlink()
    engine.compile(ROOT/'src/engine'/PROFILE.entry,candidate,graphics=True,cwd=ROOT/'src/engine')
    if not candidate.exists():raise RuntimeError('Compiler did not produce the executable')
    runtime_files=engine.install_windows_runtime(RUNTIME)
    try:os.replace(candidate,dest)
    except PermissionError as exc:
        raise RuntimeError(f'Compiled {candidate}; close the development game and run build again to replace {dest}') from exc
    development_marker(RUNTIME,info['channel']=='local')
    write(RUNTIME/'core/version.json',info)
    (RUNTIME/'core/version.txt').write_text(info['display_version']+'\n',encoding='ascii',newline='\n')
    report=dict(info,exe_sha256=sha(dest),source_sha256=source_hashes(ROOT,PROFILE.name))
    report['engine_sha256']=engine.sources()
    report['runtime_files']=runtime_files
    report['platform']='windows-x64-d3d12'
    write(BUILD/'build-report.json',report)
    print('Built:',dest)

def run(args):
    exe=RUNTIME/PROFILE.executable
    if not exe.exists():raise ValueError('Run build first')
    if runtime_info(RUNTIME)['edition']!=PROFILE.name:raise ValueError('Runtime belongs to another edition')
    options=args.game_args[1:] if args.game_args[:1]==['--'] else args.game_args
    subprocess.Popen([str(exe),*options],cwd=RUNTIME)

def check(args):
    catalogs=validate(ROOT,PROFILE.name)
    print('Catalogs:',{k:len(v) for k,v in catalogs.items()})
    subprocess.run([sys.executable,'-X','utf8',str(ROOT/'src/devtools/verify.py'),'--edition',PROFILE.name],check=True,cwd=ROOT)

def main():
    parser=argparse.ArgumentParser(description=__doc__)
    sub=parser.add_subparsers(dest='command',required=True)
    p=sub.add_parser('setup',help='Validate external dependencies and refresh shared resources/fonts')
    for name in ('game','font'):p.add_argument('--'+name,type=Path)
    p.add_argument('--blitz',type=Path,help='Deprecated; original Blitz3D is only used by reference tests')
    sub.add_parser('localize',help='Refresh language packs and fonts without recompiling or copying game resources')
    p=sub.add_parser('build',help='Compile src/engine with the embedded Blitz3D engine')
    p.add_argument('--version-info',type=Path,help='CI-selected version metadata; defaults to config/version.json with -local')
    p=sub.add_parser('run',help='Launch the isolated development game')
    p.add_argument('--port',type=int,default=8765,help='Local web preview port; use the same port to retain browser saves')
    p.add_argument('game_args',nargs=argparse.REMAINDER)
    p=sub.add_parser('check',help='Check catalogs, resources and compiled runtime functions')
    p.add_argument('--suite',choices=('core','full'),help='Mac only: core skips graphical checks; defaults to full')
    for command in sub.choices.values():
        command.add_argument('--edition',choices=NAMES,default='classic')
        command.add_argument('--platform',choices=('windows','web','macos'),default='windows')
    args=parser.parse_args()
    global PROFILE,BUILD,RUNTIME
    PROFILE=Edition(args.edition);BUILD=PROFILE.build_dir(ROOT);RUNTIME=PROFILE.runtime(ROOT)
    try:
        if getattr(args,'suite',None) and args.platform!='macos':
            raise ValueError('--suite is only supported by check --platform macos.')
        if args.platform=='macos':
            if args.edition!='classic':raise ValueError('The native Mac experiment currently supports Stranded II only.')
            import macos_build
            getattr(macos_build,args.command)(args)
        elif args.platform=='web':
            if args.edition!='classic':raise ValueError('The web preview currently supports Stranded II only; Extended web is not available.')
            import web_build
            getattr(web_build,args.command)(args)
        else:globals()[args.command](args)
    except (ValueError,OSError,RuntimeError,subprocess.CalledProcessError) as exc:parser.exit(1,f'Error: {exc}\n')
if __name__=='__main__':main()
