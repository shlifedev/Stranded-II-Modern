"""Read-only gates shared by native verification and test ZIP packaging."""
from pathlib import Path
import hashlib
import json
import os
import plistlib
import re
import stat
import subprocess

import macos_build as build
from editions import source_hashes
from versioning import validate_info


def fingerprint(value):
    return hashlib.sha256(json.dumps(value, sort_keys=True).encode('utf8')).hexdigest()


def verifier_hashes():
    paths = [build.ROOT / name for name in (
        'scripts/verify_macos.py', 'scripts/macos_validation.py', 'scripts/verify_timing.py',
        'tests/web_semantics.bb', 'tests/web_compatibility.bb')]
    paths += list((build.ROOT / 'tests').glob('macos_*.bb'))
    paths.append(build.ROOT / 'tests/macos_abi.golden.hex')
    return {p.relative_to(build.ROOT).as_posix(): build.digest(p) for p in sorted(paths)}


def current_build():
    report = build.read(build.BUILD / 'build-report.json')
    if validate_info(report)['edition'] != 'classic' or report.get('platform') != 'macos':
        raise ValueError('Expected the native Stranded II build.')
    expected = {
        'source_sha256': source_hashes(build.ROOT, 'classic', 'macos'),
        'implementation_sha256': build.implementation_hashes(),
        'executable_sha256': build.digest(build.RUNTIME / 'StrandedII'),
        'toolchain': build.macos_toolchain.config(),
    }
    for key, value in expected.items():
        if report.get(key) != value:
            raise ValueError(f'Native {key} changed; rebuild and recheck first.')
    app = Path(report['app'])
    if app.name != 'Stranded II.app' or not app.resolve().is_relative_to((build.BUILD / 'apps').resolve()):
        raise ValueError('Native build report points outside the app build directory.')
    return report


def tree_records(root):
    """Include file contents and modes; refuse links and unexpected file types."""
    if root.is_symlink() or not root.is_dir():
        raise ValueError(f'Expected an app directory, not a link: {root}')
    records = {}
    for path in sorted(root.rglob('*')):
        mode = path.lstat().st_mode
        if stat.S_ISLNK(mode) or not (stat.S_ISREG(mode) or stat.S_ISDIR(mode)):
            raise ValueError(f'Unsupported app member: {path}')
        name = path.relative_to(root).as_posix()
        records[name] = {'mode': stat.S_IMODE(mode), 'type': 'directory' if path.is_dir() else 'file'}
        if path.is_file():
            records[name]['sha256'] = build.digest(path)
    return records


def validate_app(app, package_report):
    records = tree_records(app)
    payload = build.asset_payload()
    hashes = {name: hashlib.sha256(data).hexdigest() for name, data in payload.items()}
    expected_files = {f'Contents/Resources/game/{name}' for name in payload}
    expected_files.update(('Contents/Info.plist', 'Contents/MacOS/StrandedII',
                           'Contents/Resources/native-assets.json', 'Contents/_CodeSignature/CodeResources'))
    expected_dirs = {p.as_posix() for name in expected_files for p in Path(name).parents if p != Path('.')}
    if set(records) != expected_files | expected_dirs:
        raise ValueError('Native app contains missing or unexpected files/directories.')
    if package_report.get('asset_sha256') != hashes:
        raise ValueError('Native resources changed; rebuild and recheck first.')
    for name, expected in hashes.items():
        if records[f'Contents/Resources/game/{name}'].get('sha256') != expected:
            raise ValueError(f'Native app resource changed: {name}')
    manifest = build.read(app / 'Contents/Resources/native-assets.json')
    if manifest != {'fingerprint': fingerprint(hashes), 'files': list(payload)}:
        raise ValueError('Native asset manifest differs from the packaged resources.')
    executable = app / 'Contents/MacOS/StrandedII'
    if package_report.get('signed_executable_sha256') != build.digest(executable):
        raise ValueError('Native app executable changed; rebuild and recheck first.')
    if not os.access(executable, os.X_OK) or executable.stat().st_mode & 0o111 != 0o111:
        raise ValueError('Native app lost its executable permissions.')
    info = plistlib.loads((app / 'Contents/Info.plist').read_bytes())
    cfg = build.macos_toolchain.config()
    if (info.get('CFBundleExecutable') != 'StrandedII' or
            info.get('CFBundleIdentifier') != 'local.strandedii.modern.native-preview' or
            info.get('LSMinimumSystemVersion') != cfg['deployment_target'] or
            info.get('CFBundleVersion') != build.read(build.ROOT / 'config/version.json')['version']):
        raise ValueError('Native app metadata differs from its build configuration.')
    subprocess.run(['codesign', '--verify', '--deep', '--strict', str(app)], check=True)
    architecture = subprocess.check_output(['xcrun', 'lipo', '-archs', str(executable)], text=True).strip()
    if architecture != cfg['architecture']:
        raise ValueError(f'Unexpected native architecture: {architecture}')
    load = subprocess.check_output(['xcrun', 'vtool', '-show-build', str(executable)], text=True)
    if (not re.search(r'^\s*platform MACOS\s*$', load, re.M) or
            re.findall(r'^\s*minos ([\d.]+)\s*$', load, re.M) != [cfg['deployment_target']]):
        raise ValueError('Native Mach-O deployment target differs from the app metadata.')
    output = subprocess.check_output(['otool', '-L', str(executable)], text=True)
    libraries = [line.strip().split(' (compatibility version', 1)[0] for line in output.splitlines()[1:]]
    if not libraries or any(not lib.startswith(('/usr/lib/', '/System/Library/')) for lib in libraries):
        raise ValueError(f'Native app requires non-system libraries: {libraries}')
    return {'status': 'pass', 'app_sha256': fingerprint(records),
            'signed_executable_sha256': build.digest(executable), 'architecture': architecture,
            'minimum_macos': cfg['deployment_target'], 'linked_libraries': libraries,
            'signature': 'local ad hoc', 'personal_data_included': False}


def check_built_app(report):
    app = Path(report['app'])
    package = build.read(app.parent / 'package-report.json')
    if package.get('executable_sha256') != report['executable_sha256']:
        raise ValueError('Native app was not packaged from the current executable.')
    if validate_info(build.read(app / 'Contents/Resources/game/core/version.json')) != validate_info(report):
        raise ValueError('Native app version differs from the build report.')
    return validate_app(app, package)


def verified_build():
    report = current_build()
    facts = check_built_app(report)
    verification = build.read(build.BUILD / 'verification.json')
    if verification.get('passed') is not True or verification.get('suite') not in ('core', 'full'):
        raise ValueError('Run a successful native core/full check before packaging.')
    for key in ('source_sha256', 'implementation_sha256', 'executable_sha256'):
        if verification.get(key) != report[key]:
            raise ValueError(f'Stale native verification: {key}; recheck before packaging.')
    if verification.get('verifier_sha256') != verifier_hashes():
        raise ValueError('Native verification code changed; recheck before packaging.')
    if verification.get('app') != facts:
        raise ValueError('Native app changed after verification; recheck before packaging.')
    expected_size = len(bytes.fromhex((build.ROOT / 'tests/macos_abi.golden.hex').read_text('ascii')))
    if verification.get('abi') != {'bytes_equal_to_blitz_1_118': expected_size, 'type_reuse': 'pass'}:
        raise ValueError('Native ABI verification is missing or incomplete.')
    if verification['suite'] == 'full':
        if (verification.get('metal_render_and_readback') != 'pass' or
                verification.get('bundle_storage') != {'fresh_profile': 'pass',
                    'resource_refresh_preserves_settings_and_saves': 'pass'} or
                not isinstance(verification.get('game'), dict) or
                verification['game'].get('checks_passed', 0) < 77 or
                verification['game'].get('app_bundle') != 'pass'):
            raise ValueError('Native full verification is incomplete.')
    elif any(verification.get(key) != 'not_run' for key in ('metal_render_and_readback', 'bundle_storage', 'game')):
        raise ValueError('Core verification must mark graphical tests as not_run.')
    return report, verification
