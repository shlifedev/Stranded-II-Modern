"""Pinned CI dependencies and standalone release packaging; never downloads upstream."""
from pathlib import Path, PurePosixPath
import argparse
import hashlib
import json
import os
import re
import shutil
import stat
import struct
import sys
import zipfile
from versioning import local_info, runtime_info, validate_info
from editions import Edition, NAMES, source_hashes, override_files

ROOT = Path(__file__).resolve().parents[1]


def read_json(path):
    return json.loads(path.read_text(encoding='utf8'))


def digest(data):
    return hashlib.sha256(data).hexdigest()


def file_bytes(base, relative):
    base = base.resolve()
    path = base / relative
    if not path.resolve().is_relative_to(base) or not path.is_file():
        raise ValueError(f'Missing file or path outside input directory: {path}')
    return path.read_bytes()


def checked_bytes(base, relative, expected):
    data = file_bytes(base, relative)
    if digest(data) != expected:
        raise ValueError(f'Unexpected SHA-256: {base / relative}')
    return data


def license_members():
    members = {p.relative_to(ROOT).as_posix(): file_bytes(ROOT, p.relative_to(ROOT))
               for p in sorted((ROOT / 'licenses').rglob('*')) if p.is_file()}
    if not members:
        raise ValueError('Missing licenses directory')
    return members


def dependency_members(game, blitz, font):
    members = {}
    for prefix, base, manifest in (
            ('game', game, 'game-files.json'),
            ('blitz', blitz, 'toolchain-files.json')):
        for relative, expected in read_json(ROOT / 'config' / manifest).items():
            members[f'{prefix}/{relative}'] = checked_bytes(base, relative, expected)
    expected = read_json(ROOT / 'config/dependencies.json')['font_sha256']
    members['fonts/NotoSansKR.ttf'] = checked_bytes(font.parent, font.name, expected)
    members.update(license_members())
    return members


def write_archive(path, members):
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary = path.with_name(path.name + '.tmp')
    try:
        with zipfile.ZipFile(temporary, 'w', compression=zipfile.ZIP_DEFLATED,
                             compresslevel=9) as archive:
            for name, data in sorted(members.items()):
                info = zipfile.ZipInfo(name, date_time=(2000, 1, 1, 0, 0, 0))
                info.create_system = 3
                info.external_attr = (stat.S_IFREG | 0o644) << 16
                archive.writestr(info, data, compress_type=zipfile.ZIP_DEFLATED,
                                 compresslevel=9)
        os.replace(temporary, path)
    finally:
        if temporary.exists():
            temporary.unlink()
    checksum = digest(path.read_bytes())
    path.with_suffix(path.suffix + '.sha256').write_text(
        f'{checksum}  {path.name}\n', encoding='utf8', newline='\n')
    return checksum


def ci_config():
    config = read_json(ROOT / 'config/ci-dependencies.json')
    if not re.fullmatch(r'[A-Za-z0-9][A-Za-z0-9._-]*', config['release']):
        raise ValueError('Invalid CI dependency release tag')
    if not re.fullmatch(r'[A-Za-z0-9][A-Za-z0-9._-]*\.zip', config['asset']):
        raise ValueError('Invalid CI dependency asset filename')
    if not re.fullmatch(r'[0-9a-f]{64}', config['sha256']):
        raise ValueError('CI dependency SHA-256 must contain 64 lowercase hex digits')
    return config


def github_outputs(path, values):
    if path:
        with path.open('a', encoding='utf8', newline='\n') as output:
            for key, value in values.items():
                if '\n' in str(value) or '\r' in str(value):
                    raise ValueError('Invalid newline in GitHub output')
                output.write(f'{key}={value}\n')


def extract_archive(path, destination):
    """Reject ambiguous Windows paths, links and duplicate names before writing."""
    with zipfile.ZipFile(path) as archive:
        entries = []
        names = set()
        for info in archive.infolist():
            name = info.filename.replace('\\', '/')
            parts = name.rstrip('/').split('/')
            if (not name or name.startswith('/') or
                    any(p in ('', '.', '..') or ':' in p or
                        p.endswith((' ', '.')) for p in parts) or
                    stat.S_ISLNK(info.external_attr >> 16)):
                raise ValueError(f'Unsafe dependency archive entry: {info.filename}')
            relative = PurePosixPath(*parts)
            key = relative.as_posix().casefold()
            if key in names:
                raise ValueError(f'Duplicate dependency archive entry: {name}')
            names.add(key)
            entries.append((info, relative))
        for info, relative in entries:
            target = destination.joinpath(*relative.parts)
            if not target.resolve().is_relative_to(destination.resolve()):
                raise ValueError(f'Archive path escapes destination: {relative}')
            if info.is_dir():
                target.mkdir(parents=True, exist_ok=True)
            else:
                target.parent.mkdir(parents=True, exist_ok=True)
                with archive.open(info) as source, target.open('xb') as output:
                    shutil.copyfileobj(source, output)


def bundle(args):
    members = dependency_members(args.game, args.blitz, args.font)
    checksum = write_archive(args.output, members)
    print(f'Dependency bundle: {args.output}\nSHA-256: {checksum}')
    print('Upload this ZIP to the dependency Release. Pin its SHA-256 in '
          'config/ci-dependencies.json when updating dependencies.')


def config(args):
    values = ci_config()
    github_outputs(args.github_output, values)
    print(json.dumps(values, indent=2))


def dependencies(args):
    expected = ci_config()['sha256']
    checked_bytes(args.archive.parent, args.archive.name, expected)
    destination = args.destination.resolve()
    if destination.exists():
        raise ValueError(f'Use a new dependency directory: {destination}')
    destination.mkdir(parents=True)
    extract_archive(args.archive, destination)
    paths = {'game': destination / 'game', 'blitz': destination / 'blitz',
             'font': destination / 'fonts/NotoSansKR.ttf'}
    members = dependency_members(paths['game'], paths['blitz'], paths['font'])
    actual = {p.relative_to(destination).as_posix(): p.read_bytes()
              for p in destination.rglob('*') if p.is_file()}
    if actual != members:
        raise ValueError('Dependency bundle contains unexpected or changed files')
    github_outputs(args.github_output, paths)
    print(json.dumps({k: str(v) for k, v in paths.items()}, indent=2))


def player_data(relative):
    path = PurePosixPath(relative)
    return (any(p.lower() in ('saves', 'preserved') for p in path.parts) or
            path.name.lower() in ('settings.cfg', 'settings.inf', 'modern.ini') or
            path.suffix.lower() in ('.sav', '.bak', '.tmp', '.log') or
            path.name.lower().startswith('screen_'))


def release_members(runtime, game, edition='classic'):
    profile = Edition(edition)
    info = runtime_info(runtime)
    if info['edition'] != edition:raise ValueError('Runtime belongs to another edition')
    from language_pack import registry
    from locales import languages, catalog_text
    members = {}
    manifest = read_json(ROOT / 'config/game-files.json')
    for relative, expected in manifest.items():
        if player_data(relative):
            continue
        # Ship original key bindings, even when packaging a used local runtime.
        members[relative] = (checked_bytes(game, relative, expected)
                             if relative.endswith('.cfg')
                             else file_bytes(runtime, relative))
    for relative, path in override_files(ROOT, edition).items():
        data = file_bytes(runtime, relative)
        if data != path.read_bytes():
            raise ValueError(f'Runtime override is stale; run setup: {relative}')
        members[relative] = data
    exe = file_bytes(runtime, profile.executable)
    if not exe.startswith(b'MZ'):
        raise ValueError(f'{profile.executable} is not a Windows executable')
    members[profile.executable] = exe
    bridge = file_bytes(runtime, 'QolInput.dll')
    if not bridge.startswith(b'MZ'):
        raise ValueError('QolInput.dll is not a Windows DLL')
    members['QolInput.dll'] = bridge
    report = read_json(profile.build_dir(ROOT) / 'build-report.json')
    native = report.get('native_qol', {})
    if (native.get('edition') != edition or native.get('dll_sha256') != digest(bridge) or
            native.get('source_sha256') != digest(file_bytes(ROOT, 'src/native/qol_input.c'))):
        raise ValueError('Native QOL build report is stale; rebuild the current sources first')
    sources = source_hashes(ROOT, edition)
    if (validate_info(report) != info or report['exe_sha256'] != digest(exe) or
            report['source_sha256'] != sources):
        raise ValueError('Runtime build report is stale; rebuild the current sources first')
    members['core/version.json'] = file_bytes(runtime, 'core/version.json')
    members['core/version.txt'] = file_bytes(runtime, 'core/version.txt')
    members['mods/Stranded II/sys/ko_aliases.dat'] = file_bytes(
        runtime, 'mods/Stranded II/sys/ko_aliases.dat')
    packs = languages(ROOT)
    registered = file_bytes(runtime, 'core/locales/languages.dat')
    if registered != registry(packs):
        raise ValueError('Runtime language registry is stale; run setup/localize first')
    members['core/locales/languages.dat'] = registered
    for code in packs:
        relative = f'core/locales/{code}.tsv'
        catalog = file_bytes(runtime, relative)
        if catalog != catalog_text(ROOT,code,edition).encode('utf8'):
            raise ValueError(f'Runtime catalog is stale: {code}; run setup/localize first')
        members[relative] = catalog
        relative = f'core/locales/{code}/glyphs.dat'
        glyphs = file_bytes(runtime, relative)
        if len(glyphs) < 16:
            raise ValueError(f'Invalid font metadata: {code}')
        magic, count, width, height = struct.unpack_from('<4sIII', glyphs)
        if (magic != b'FNT2' or count == 0 or len(glyphs) != 16 + count * 28 or
                not 0 < width <= 64 or height != 19):
            raise ValueError(f'Invalid font metadata: {code}')
        members[relative] = glyphs
        for style in range(7):
            for page in range((count + 1023) // 1024):
                relative = f'core/locales/{code}/{style}_{page}.bmp'
                members[relative] = file_bytes(runtime, relative)
    members['core/modern.ini'] = file_bytes(ROOT, profile.defaults)
    members.update(license_members())
    members['README.txt'] = (
        f'{profile.title}\n\n'
        f'Extract the entire ZIP, then double-click {profile.executable} in the game folder.\n'
        f'ZIP 전체를 풀고 게임 폴더의 {profile.executable}를 더블클릭하세요.\n\n'
        'Windows 10 or later. No separate installation is required.\n'
        'Windows 10 이상. 별도 설치는 필요하지 않습니다.\n\n'
        'Display, FPS and language: Settings > Graphics; apply, close and reopen the game.\n'
        '화면·FPS·언어: 설정 > 그래픽에서 적용한 뒤 게임을 종료하고 다시 실행하세요.\n'
        'Optional QOL: Settings > QOL; apply to activate individual options immediately.\n'
        '선택형 QOL: 설정 > QOL에서 개별 옵션을 선택하고 적용하면 즉시 반영됩니다.\n'
        'Saves / 저장 파일: mods/Stranded II/saves/\n'
        'Original game / 원작: Peter Schauß / Unreal Software, https://www.unrealsoftware.de/\n'
        'This is an unofficial modified version. / 비공식 수정 버전입니다.\n'
        'Copyright and component licenses / 저작권 및 구성물 라이선스: licenses/\n'
    ).encode('utf8')
    members['build-info.json'] = (json.dumps({
        **info,
        'upstream_game_version': read_json(ROOT / 'config/dependencies.json')['game_version'],
        'exe_sha256': digest(exe),
        'languages': list(packs),
        'source_sha256': sources,
        'native_qol': {key: native[key] for key in ('edition', 'source_sha256', 'dll_sha256')},
    }, ensure_ascii=False, indent=2) + '\n').encode('utf8')
    return {f'{profile.archive_root}/{name}': data for name, data in members.items()}


def package(args):
    from release import archive_name
    edition = getattr(args, 'edition', 'classic')
    runtime = args.runtime or Edition(edition).runtime(ROOT)
    info = runtime_info(runtime)
    if info['edition'] != edition:raise ValueError('Runtime belongs to another edition')
    if args.version_info and validate_info(read_json(args.version_info)) != info:
        raise ValueError('Runtime version differs from the CI-selected version')
    members = release_members(runtime, args.game, edition)
    output = args.output or ROOT / 'build/release' / archive_name(info)
    checksum = write_archive(output, members)
    print(f'Release ZIP: {output}\nSHA-256: {checksum}\nFiles: {len(members)}')


def version(args):
    from release import allocation
    edition = getattr(args, 'edition', 'classic')
    if args.channel:
        if not args.repo or not os.environ.get('GITHUB_SHA'):
            raise ValueError('Release allocation needs --repo and GITHUB_SHA')
        info, published = allocation(ROOT, args.repo, args.channel, os.environ['GITHUB_SHA'], edition)
    else:
        info, published = local_info(ROOT, edition), False
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(info, indent=2) + '\n', encoding='utf8', newline='\n')
    github_outputs(args.github_output, dict(info, published=str(published).lower()))
    print(json.dumps(dict(info, published=published), indent=2))


def release(args):
    from release import publish
    info = validate_info(read_json(args.version_info))
    if info['edition'] != args.edition:raise ValueError('Release belongs to another edition')
    publish(args.repo, args.directory, info, include_macos=args.include_macos)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    sub = parser.add_subparsers(dest='command', required=True)
    command = sub.add_parser('bundle', help='Create a pinned dependency ZIP from local inputs')
    command.add_argument('--game', type=Path, default=ROOT / 'deps/game')
    command.add_argument('--blitz', type=Path, default=ROOT / 'deps/Blitz3D-V1.118')
    command.add_argument('--font', type=Path, default=ROOT / 'deps/fonts/NotoSansKR[wght].ttf')
    command.add_argument('--output', type=Path,
                         default=ROOT / 'build/ci-upload/StrandedII-ci-deps-v1.zip')
    command = sub.add_parser('config', help='Read the dependency Release and archive pin')
    command.add_argument('--github-output', type=Path)
    command = sub.add_parser('dependencies', help='Verify and unpack the CI dependency ZIP')
    command.add_argument('--archive', type=Path, required=True)
    command.add_argument('--destination', type=Path, default=ROOT / 'build/ci/deps')
    command.add_argument('--github-output', type=Path)
    command = sub.add_parser('package', help='Create a standalone ZIP without player data')
    command.add_argument('--runtime', type=Path)
    command.add_argument('--game', type=Path, required=True)
    command.add_argument('--output', type=Path)
    command.add_argument('--version-info', type=Path)
    command = sub.add_parser('version', help='Select an immutable release version or a local/preview label')
    command.add_argument('--channel', choices=('main', 'dev'))
    command.add_argument('--repo')
    command.add_argument('--output', type=Path, default=ROOT / 'build/ci/version.json')
    command.add_argument('--github-output', type=Path)
    command = sub.add_parser('release', help='Publish the validated ZIP through a resumable draft Release')
    command.add_argument('--include-macos', action='store_true', help='Require the matching native Mac ZIP and checksum')
    command.add_argument('--repo', required=True)
    command.add_argument('--version-info', type=Path, default=ROOT / 'build/ci/version.json')
    command.add_argument('--directory', type=Path, default=ROOT / 'build/release')
    for name in ('package', 'version', 'release'):
        sub.choices[name].add_argument('--edition', choices=NAMES, default='classic')
    args = parser.parse_args()
    try:
        globals()[args.command](args)
    except (ValueError, OSError, zipfile.BadZipFile) as exc:
        parser.exit(1, f'Error: {exc}\n')


if __name__ == '__main__':
    main()
