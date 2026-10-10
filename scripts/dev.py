"""Development entry point. Only build/ is generated; src/engine is authoritative."""
from pathlib import Path
import argparse, hashlib, json, os, shutil, subprocess, sys
from locales import prepare, validate

ROOT=Path(__file__).resolve().parents[1]
BUILD=ROOT/'build'
RUNTIME=BUILD/'runtime'
def sha(path):return hashlib.sha256(path.read_bytes()).hexdigest()
def read(path):return json.loads(path.read_text('utf8'))
def write(path,value):
    path.parent.mkdir(parents=True,exist_ok=True)
    path.write_text(json.dumps(value,ensure_ascii=False,indent=2)+'\n',encoding='utf8')
def settings():
    path=BUILD/'dependencies.local.json'
    if not path.exists():raise ValueError('Run setup --game PATH --blitz PATH --font PATH first.')
    return read(path)
def checked(path,expected,label):
    if not path.is_file():raise ValueError(f'Missing {label}: {path}')
    if sha(path)!=expected:raise ValueError(f'Unexpected {label} SHA-256: {path}')

def setup(args):
    from language_pack import refresh
    deps=read(ROOT/'config/dependencies.json')
    previous=settings() if (BUILD/'dependencies.local.json').exists() else {}
    inputs={name:str(getattr(args,name).resolve()) if getattr(args,name) else previous.get(name) for name in ('game','blitz','font')}
    if not all(inputs.values()):raise ValueError('setup requires --game, --blitz and --font on first use')
    game,blitz,font=(Path(inputs[name]) for name in ('game','blitz','font'))
    if game==RUNTIME or RUNTIME in game.parents:raise ValueError('Input game must be an external official English installation')
    checked(blitz/'bin/blitzcc.exe',deps['compiler_sha256'],'Blitz3D 1.118 compiler')
    for relative,digest in read(ROOT/'config/toolchain-files.json').items():
        checked(blitz/relative,digest,'Blitz3D 1.118 component')
    checked(font,deps['font_sha256'],'Noto Sans KR font')
    files=read(ROOT/'config/game-files.json')
    for relative,digest in files.items():checked(game/relative,digest,'official game resource')
    catalogs=validate(ROOT)
    # Validate before writing. Never import source installation saves or caches.
    BUILD.mkdir(exist_ok=True)
    for relative in files:
        target=RUNTIME/relative
        target.parent.mkdir(parents=True,exist_ok=True)
        # User settings must survive repeated setup.
        if target.exists() and (target.suffix=='.cfg' or relative.endswith('/settings.inf')):continue
        shutil.copy2(game/relative,target)
    (RUNTIME/'mods/Stranded II/saves').mkdir(exist_ok=True)
    private=BUILD/'toolchain'
    for dirname in ('bin','lib'):
        if (blitz/dirname).exists():shutil.copytree(blitz/dirname,private/dirname,dirs_exist_ok=True)
    shutil.copytree(ROOT/'config/userlibs',private/'userlibs',dirs_exist_ok=True)
    if not (RUNTIME/'core/modern.ini').exists():shutil.copy2(ROOT/'config/modern.ini',RUNTIME/'core/modern.ini')
    catalogs,report=prepare(ROOT,game,RUNTIME)
    overrides=ROOT/'resources/overrides'
    if overrides.exists():
        for p in overrides.rglob('*'):
            if p.is_file():
                rel=p.relative_to(overrides)
                if 'saves' in rel.parts or rel.name in ('modern.ini','settings.cfg','settings.inf'):raise ValueError('Overrides may not replace player settings/saves')
                dest=RUNTIME/rel;dest.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(p,dest)
    report.update(refresh(ROOT,RUNTIME,font))
    shutil.copytree(ROOT/'licenses',RUNTIME/'licenses',dirs_exist_ok=True)
    write(BUILD/'dependencies.local.json',inputs)
    write(BUILD/'setup-report.json',report)
    print(json.dumps(report,ensure_ascii=False));print('Prepared:',RUNTIME)

def localize(args):
    from language_pack import refresh
    inputs=settings()
    report=refresh(ROOT,RUNTIME,Path(inputs['font']))
    write(BUILD/'localization-report.json',report)
    print(json.dumps(report,ensure_ascii=False));print('Updated language packs:',RUNTIME/'core/locales')


def build(args):
    settings();validate(ROOT)
    compiler=BUILD/'toolchain/bin/blitzcc.exe'
    for relative,digest in read(ROOT/'config/toolchain-files.json').items():
        checked(BUILD/'toolchain'/relative,digest,'private Blitz3D component')
    env=dict(os.environ,blitzpath=str(BUILD/'toolchain'))
    dest=RUNTIME/'StrandedII.exe'
    candidate=BUILD/'StrandedII.next.exe'
    if candidate.exists():candidate.unlink()
    result=subprocess.run([str(compiler),'-o',str(candidate),'StrandedII.bb'],cwd=ROOT/'src/engine',env=env,capture_output=True)
    print(result.stdout.decode('utf8',errors='replace'));print(result.stderr.decode('utf8',errors='replace'))
    if result.returncode:raise RuntimeError('Blitz3D compilation failed')
    if not candidate.exists():raise RuntimeError('Compiler did not produce the executable')
    try:os.replace(candidate,dest)
    except PermissionError as exc:
        raise RuntimeError(f'Compiled {candidate}; close the development game and run build again to replace {dest}') from exc
    write(BUILD/'build-report.json',{'exe_sha256':sha(dest),'source_sha256':{p.relative_to(ROOT).as_posix():sha(p) for p in (ROOT/'src/engine').rglob('*.bb')}})
    print('Built:',dest)

def run(args):
    exe=RUNTIME/'StrandedII.exe'
    if not exe.exists():raise ValueError('Run build first')
    options=args.game_args[1:] if args.game_args[:1]==['--'] else args.game_args
    subprocess.Popen([str(exe),*options],cwd=RUNTIME)

def check(args):
    catalogs=validate(ROOT)
    print('Catalogs:',{k:len(v) for k,v in catalogs.items()})
    subprocess.run([sys.executable,'-X','utf8',str(ROOT/'scripts/verify.py')],check=True,cwd=ROOT)

def main():
    parser=argparse.ArgumentParser(description=__doc__)
    sub=parser.add_subparsers(dest='command',required=True)
    p=sub.add_parser('setup',help='Validate external dependencies and refresh shared resources/fonts')
    for name in ('game','blitz','font'):p.add_argument('--'+name,type=Path)
    sub.add_parser('localize',help='Refresh language packs and fonts without recompiling or copying game resources')
    sub.add_parser('build',help='Compile directly edited src/engine with private Blitz3D')
    p=sub.add_parser('run',help='Launch the isolated development game')
    p.add_argument('game_args',nargs=argparse.REMAINDER)
    sub.add_parser('check',help='Check catalogs, resources and compiled runtime functions')
    args=parser.parse_args()
    try:globals()[args.command](args)
    except (ValueError,OSError,RuntimeError) as exc:parser.exit(1,f'Error: {exc}\n')
if __name__=='__main__':main()
