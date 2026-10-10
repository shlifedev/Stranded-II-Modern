"""Build a self-contained, unnotarized Apple Silicon app from a checked Windows ZIP."""
from pathlib import Path, PurePosixPath
import argparse
import hashlib
import json
import os
import plistlib
import shutil
import subprocess
import sys
import tarfile
import tempfile
import urllib.request
import zipfile

from ci import extract_archive
from release import archive_name, validate_package
from versioning import validate_info

ROOT = Path(__file__).resolve().parents[1]
PLATFORM = 'macos-apple-silicon'
APP = 'Stranded II.app'


def digest(path):
    with path.open('rb') as stream:
        return hashlib.file_digest(stream, 'sha256').hexdigest()


def wine_config():
    return json.loads((ROOT / 'config/macos-wine.json').read_text('utf8'))


def checked_download(config, destination):
    destination.parent.mkdir(parents=True, exist_ok=True)
    if not destination.exists():
        temporary = destination.with_suffix(destination.suffix + '.tmp')
        try:
            with urllib.request.urlopen(config['url'], timeout=60) as source, temporary.open('wb') as output:
                shutil.copyfileobj(source, output)
            if digest(temporary) != config['sha256']:
                raise ValueError('Wine download SHA-256 mismatch')
            os.replace(temporary, destination)
        finally:
            temporary.unlink(missing_ok=True)
    if digest(destination) != config['sha256']:
        raise ValueError('Wine archive SHA-256 mismatch (including cached archives)')
    return destination


def extract_wine(archive_path, destination):
    # Engine links are supported only within the extracted engine; unlike game ZIPs.
    with tarfile.open(archive_path) as archive:
        seen = set()
        for member in archive.getmembers():
            parts = PurePosixPath(member.name).parts
            if (not parts or parts[0] != 'wswine.bundle' or '..' in parts or
                    '\\' in member.name or member.name.casefold() in seen or
                    not (member.isfile() or member.isdir() or member.issym() or member.islnk())):
                raise ValueError(f'Unsafe Wine archive member: {member.name}')
            seen.add(member.name.casefold())
            if member.issym() or member.islnk():
                parent = destination if member.islnk() else (destination / member.name).parent
                target = (parent / member.linkname).resolve()
                if not target.is_relative_to((destination / 'wswine.bundle').resolve()):
                    raise ValueError(f'Wine link escapes engine: {member.name}')
        archive.extractall(destination, filter='data')
    engine = destination / 'wswine.bundle'
    for name in ('wine', 'wineserver'):
        if not os.access(engine / 'bin' / name, os.X_OK):
            raise ValueError(f'Wine is missing executable bin/{name}')
    return engine


def library_archive(wine_archive):
    return wine_archive.with_name('macos-wine-libraries.tar.xz')


def add_libraries(archive_path, engine, config):
    """Copy only open-source runtime dylibs, never the template's launcher/renderers."""
    if digest(archive_path) != config['libraries']['sha256']:
        raise ValueError('Wine libraries archive SHA-256 mismatch')
    prefix = config['libraries']['version'] + '.app/Contents/Frameworks/'
    with tempfile.TemporaryDirectory(dir=engine.parent) as temporary:
        extracted = Path(temporary)
        with tarfile.open(archive_path) as archive:
            members = []
            for member in archive.getmembers():
                if not member.name.startswith(prefix):
                    continue
                name = member.name[len(prefix):]
                if '/' in name or not name.endswith('.dylib'):
                    continue
                if not (member.isfile() or member.issym()) or member.linkname and ('/' in member.linkname or member.linkname in ('.', '..')):
                    raise ValueError('Unsafe Wine library archive member')
                member.name = name
                members.append(member)
            archive.extractall(extracted, members=members, filter='data')
        # The game's FMOD audio and engine cutscenes do not use these optional
        # FFmpeg/GStreamer bridges, which require separately installed frameworks.
        for name in ('winedmo.so', 'winegstreamer.so'):
            (engine / 'lib/wine/x86_64-unix' / name).unlink(missing_ok=True)
        pending = ['libinotify.0.dylib', 'libfreetype.6.dylib', 'libgnutls.30.dylib', 'libSDL2-2.0.0.dylib']
        copied = set()
        while pending:
            name = pending.pop()
            if name in copied:
                continue
            source = extracted / name
            if not source.is_file() or source.parent != extracted:
                raise ValueError(f'Missing Wine support library: {name}')
            target = engine / 'lib' / name
            if source.is_symlink():
                link = os.readlink(source)
                target.symlink_to(link)
                pending.append(link)
            else:
                shutil.copy2(source, target)
                for line in subprocess.check_output(['otool', '-L', str(source)], text=True).splitlines():
                    if line.startswith('\t@rpath/'):
                        pending.append(line.strip().split(' (', 1)[0].removeprefix('@rpath/'))
            copied.add(name)
        return sorted(copied)


def audit_libraries(engine):
    """Reject build-machine dependencies; every non-system library stays in the app."""
    binaries = []
    for path in engine.rglob('*'):
        if not path.is_file() or path.is_symlink():
            continue
        with path.open('rb') as stream:
            magic = stream.read(4)
        if magic not in (b'\xcf\xfa\xed\xfe', b'\xce\xfa\xed\xfe', b'\xca\xfe\xba\xbe', b'\xca\xfe\xba\xbf'):
            continue
        binaries.append(path)
        identities = set(subprocess.check_output(['otool', '-D', str(path)], text=True).splitlines()[1:])
        output = subprocess.check_output(['otool', '-L', str(path)], text=True)
        for line in output.splitlines():
            if not line.startswith('\t'):
                continue
            dependency = line.strip().split(' (', 1)[0]
            if dependency.startswith(('/usr/lib/', '/System/Library/')) or dependency in identities:
                continue
            if dependency.startswith(('@rpath/', '@loader_path/', '@executable_path/')):
                suffix = dependency.split('/', 1)[1]
                bases = (path.parent, engine / 'bin', engine / 'lib', engine / 'lib/wine/x86_64-unix')
                if any((base / suffix).is_file() for base in bases):
                    continue
            raise ValueError(f'External or unresolved Wine dependency: {path.name}: {dependency}')
    if not binaries:
        raise ValueError('Wine archive contains no macOS binaries')


def game_manifest(game):
    return {p.relative_to(game).as_posix(): digest(p) for p in sorted(game.rglob('*')) if p.is_file()}


def write_app_zip(app, output):
    """Preserve executable modes and relative symlinks without Finder metadata."""
    temporary = output.with_suffix('.zip.tmp')
    try:
        with zipfile.ZipFile(temporary, 'w', zipfile.ZIP_DEFLATED, compresslevel=6) as archive:
            for path in sorted(app.rglob('*')):
                if path.is_dir() and not path.is_symlink():
                    continue
                name = path.relative_to(app.parent).as_posix()
                info = zipfile.ZipInfo(name, (2000, 1, 1, 0, 0, 0))
                info.create_system = 3
                info.external_attr = path.lstat().st_mode << 16
                data = os.readlink(path).encode('utf8') if path.is_symlink() else path.read_bytes()
                archive.writestr(info, data, compress_type=zipfile.ZIP_DEFLATED, compresslevel=6)
        os.replace(temporary, output)
    finally:
        temporary.unlink(missing_ok=True)
    checksum = digest(output)
    output.with_suffix('.zip.sha256').write_text(f'{checksum}  {output.name}\n', encoding='utf8')
    return checksum


def package(windows_zip, version_info, wine_archive, output_directory, smoke_test=False):
    if sys.platform != 'darwin':
        raise ValueError('macOS packaging requires macOS and Xcode Command Line Tools')
    info = validate_info(json.loads(version_info.read_text('utf8')))
    if info['edition'] != 'classic':
        raise ValueError('macOS packaging currently supports only Stranded II')
    if windows_zip.name != archive_name(info):
        raise ValueError('Windows archive name does not match selected version')
    _, windows_sha = validate_package(windows_zip.parent, info)
    config = wine_config()
    if digest(wine_archive) != config['sha256']:
        raise ValueError('Wine archive SHA-256 mismatch')
    output_directory.mkdir(parents=True, exist_ok=True)
    output = output_directory / archive_name(info, PLATFORM)
    with tempfile.TemporaryDirectory(prefix='macos-package-', dir=output_directory) as temporary:
        work = Path(temporary)
        unpacked = work / 'windows'
        extract_archive(windows_zip, unpacked)
        app = work / APP
        resources = app / 'Contents/Resources'
        macos = app / 'Contents/MacOS'
        resources.mkdir(parents=True)
        macos.mkdir()
        shutil.move(unpacked / 'StrandedII', resources / 'game')
        files = game_manifest(resources / 'game')
        payload = {'id': hashlib.sha256(json.dumps(files, sort_keys=True).encode()).hexdigest(), 'files': files}
        (resources / 'payload.json').write_text(json.dumps(payload, indent=2) + '\n', encoding='utf8')
        engine = extract_wine(wine_archive, work / 'engine')
        libraries = add_libraries(library_archive(wine_archive), engine, config)
        audit_libraries(engine)
        # Stranded II uses neither .NET nor the HTML engine. Do not ship Mono or
        # Gecko, or trigger their installers at runtime (see WINEDLLOVERRIDES).
        for component in ('mono', 'gecko'):
            folder = engine / 'share/wine' / component
            if folder.exists():
                shutil.rmtree(folder)
        shutil.move(engine, resources / 'wine')
        shutil.copytree(ROOT / 'resources/macos/wine-notices', resources / 'wine-notices')
        build = dict(info, platform=PLATFORM, windows_zip_sha256=windows_sha,
                     wine=config, payload_id=payload['id'], notarized=False, libraries=libraries,
                     omitted_wine_components=['mono', 'gecko', 'winedmo.so', 'winegstreamer.so'],
                     launcher_source_sha256={name: digest(ROOT / 'src/native/macos' / name)
                                             for name in ('Runtime.swift', 'Launcher.swift')},
                     packager_sha256=digest(Path(__file__)))
        (resources / 'macos-build.json').write_text(json.dumps(build, indent=2) + '\n', encoding='utf8')
        with (app / 'Contents/Info.plist').open('wb') as stream:
            plistlib.dump(dict(CFBundleExecutable='StrandedII', CFBundleIdentifier='io.github.shlifedev.strandedii',
                              CFBundleName='Stranded II', CFBundleDisplayName='Stranded II',
                              CFBundlePackageType='APPL', CFBundleVersion=info['version'].split('-')[0],
                              CFBundleShortVersionString=info['version'].split('-')[0], LSMinimumSystemVersion=config['minimum_macos'],
                              LSArchitecturePriority=['arm64'], LSUIElement=True, NSHighResolutionCapable=True), stream)
        subprocess.run(['xcrun', 'swiftc', '-target', 'arm64-apple-macos15.0', '-O',
                        str(ROOT / 'src/native/macos/Runtime.swift'), str(ROOT / 'src/native/macos/Launcher.swift'),
                        '-o', str(macos / 'StrandedII')], check=True)
        subprocess.run(['codesign', '--force', '--sign', '-', str(app)], check=True)
        subprocess.run(['codesign', '--verify', '--deep', '--strict', str(app)], check=True)
        if smoke_test:
            try:
                subprocess.run([str(macos / 'StrandedII'), '--smoke-test', str(work / 'smoke-data')], check=True, timeout=180)
            finally:
                # Only this temporary prefix belongs to the smoke test. Never
                # terminate the user's Wine environment after a failed check.
                env = dict(os.environ, WINEPREFIX=str(work / 'smoke-data/prefix'),
                           DYLD_LIBRARY_PATH=str(resources / 'wine/lib'),
                           DYLD_FALLBACK_LIBRARY_PATH=str(resources / 'wine/lib') + ':/usr/lib')
                subprocess.run([str(resources / 'wine/bin/wineserver'), '-k'], env=env,
                               stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, timeout=10, check=False)
                log = work / 'smoke-data/launcher.log'
                if log.exists():
                    shutil.copy2(log, output_directory / 'macos-smoke.log')
        checksum = write_app_zip(app, output)
    validate_package(output_directory, info, PLATFORM)
    print(f'macOS test package: {output}\nSHA-256: {checksum}')
    return output


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    sub = parser.add_subparsers(dest='command', required=True)
    download = sub.add_parser('download-wine', help='Download or verify the pinned Wine engine')
    download.add_argument('--output', type=Path, default=ROOT / 'build/ci/macos-wine.tar.xz')
    build = sub.add_parser('package')
    build.add_argument('--windows-zip', type=Path, required=True)
    build.add_argument('--version-info', type=Path, required=True)
    build.add_argument('--wine-archive', type=Path, required=True)
    build.add_argument('--output-directory', type=Path, default=ROOT / 'build/release')
    build.add_argument('--smoke-test', action='store_true')
    args = parser.parse_args()
    try:
        if args.command == 'download-wine':
            print(checked_download(wine_config(), args.output))
            print(checked_download(wine_config()['libraries'], library_archive(args.output)))
        else:
            package(args.windows_zip.resolve(), args.version_info.resolve(), args.wine_archive.resolve(),
                    args.output_directory.resolve(), args.smoke_test)
    except (ValueError, OSError, KeyError, zipfile.BadZipFile, tarfile.TarError, subprocess.SubprocessError) as exc:
        parser.exit(1, f'Error: {exc}\n')


if __name__ == '__main__':
    main()
