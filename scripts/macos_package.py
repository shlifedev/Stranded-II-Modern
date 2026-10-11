"""Package a verified, locally signed Mac preview. Does not publish anything."""
from pathlib import Path
import argparse
import json
import os
import shutil
import stat
import subprocess
import tempfile
import zipfile

import macos_build as build
from macos_validation import fingerprint, tree_records, validate_app, verified_build
from versioning import validate_info

GUIDES = {
    'en': '''Stranded II — Native Mac build (experimental)

Requires Apple Silicon and targets macOS 15.0 or later. No browser, Python,
Homebrew, or separate game download is required to play.

Extract the entire ZIP, move Stranded II.app to Applications or another folder,
then double-click it. This test build is locally signed and is not notarized.
macOS may require approval in System Settings > Privacy & Security to open it.
Apple's instructions: https://support.apple.com/102445

Settings and saves are stored separately from the app at:
~/Library/Application Support/Stranded II Native Preview/
To update, quit the game normally and replace the app. Keep that data folder.
Windows and browser saves are not imported. New profiles start in English,
windowed at 1280 x 720; select Korean or German in Settings and restart.

verification-summary.json identifies this build, the machine environment,
and the tests actually performed. A core pass does not verify rendering.
Minimum-OS and other-Mac gameplay, IME composition, fullscreen/focus changes,
audible sound, and a 30-minute play session require separate manual testing.
Native movie decoding, raw TCP/UDP, Intel, and Extended are not supported.

When reporting a problem, include the build ID, Mac model, macOS version,
selected language/display mode, and reproduction steps. Attach the verification
summary; do not attach your entire data folder. Test saving, quit/relaunch and
loading, Korean input, app switching, fullscreen, music/effects, and progression.
''',
    'ko': '''Stranded II — Mac 네이티브 빌드 (실험용)

Apple Silicon용이며 macOS 15.0 이상을 대상으로 합니다. 플레이에 브라우저,
Python, Homebrew 또는 별도 게임 리소스 다운로드가 필요하지 않습니다.

ZIP 전체를 압축 해제하고 Stranded II.app을 응용 프로그램 또는 원하는 폴더로
옮겨 더블클릭하세요. 로컬 서명한 테스트판이며 공증하지 않았습니다.
macOS가 실행을 차단하면 시스템 설정 > 개인정보 보호 및 보안에서 실행을
허용해야 할 수 있습니다. Apple 안내: https://support.apple.com/102445

설정과 세이브는 앱과 분리된 다음 폴더에 저장됩니다.
~/Library/Application Support/Stranded II Native Preview/
업데이트할 때는 게임을 정상 종료하고 앱을 교체하세요. 데이터 폴더는 유지하세요.
Windows·브라우저 세이브는 가져오지 않습니다. 새 프로필은 영어, 1280×720
창 모드로 시작합니다. 설정에서 한국어·독일어를 선택한 뒤 재시작할 수 있습니다.

verification-summary.json에 빌드 ID, 검사 환경과 실제 수행한 검사가 기록됩니다.
core 검사 통과는 화면 검증을 뜻하지 않습니다. 최소 OS·다른 Mac에서의 플레이,
한글 조합 입력, 전체화면·포커스 전환, 실제 소리, 30분 플레이는 별도 수동 검사
대상입니다. 네이티브 동영상, 원시 TCP/UDP, Intel, Extended는 지원하지 않습니다.

문제를 제보할 때 빌드 ID, Mac 모델, macOS 버전, 선택한 언어·화면 모드와 재현
순서를 알려주세요. 검증 요약을 첨부하되 데이터 폴더 전체는 첨부하지 마세요.
저장 후 종료·재실행·불러오기, 한글 입력, 앱 전환, 전체화면, 배경음·효과음과
게임 진행을 확인해 주세요.
''',
}


def summary(report, verification):
    # Allowlist public metadata. Local dependency paths and fixture logs stay local.
    app = verification['app']
    game = verification['game']
    if isinstance(game, dict):
        game = {key: game[key] for key in ('checks_passed', 'synthetic_time', 'app_bundle')}
    return {
        'product': 'Stranded II', 'version': report['version'], 'source_commit': report['commit'],
        'version_info': validate_info(report),
        'source_fingerprint': fingerprint({'engine': report['source_sha256'],
                                           'implementation': report['implementation_sha256']}),
        'platform': 'macos-arm64', 'minimum_macos': app['minimum_macos'],
        'app_sha256': app['app_sha256'], 'signing': 'local ad hoc', 'notarized': False,
        'build_environment': report.get('environment', {}), 'suite': verification['suite'],
        'checks': {'abi': verification['abi'], 'app_structure_and_signature': 'pass',
                   'metal_render_and_readback': verification['metal_render_and_readback'],
                   'bundle_storage': verification['bundle_storage'], 'game': game},
        'manual_checks': {key: 'not_run' for key in (
            'minimum_macos_gameplay', 'other_mac_gameplay', 'ime_composition',
            'fullscreen_and_focus', 'audible_sound', '30_minute_play_and_reload')},
        'personal_data_included': False,
    }


def write_archive(path, stage):
    """Stable ZIP metadata with Unix execute permissions preserved."""
    records = tree_records(stage)
    with zipfile.ZipFile(path, 'w', compression=zipfile.ZIP_DEFLATED, compresslevel=6) as archive:
        for name, record in records.items():
            directory = record['type'] == 'directory'
            info = zipfile.ZipInfo(name + ('/' if directory else ''), date_time=(2000, 1, 1, 0, 0, 0))
            info.create_system = 3
            info.external_attr = ((stat.S_IFDIR if directory else stat.S_IFREG) | record['mode']) << 16
            if directory:
                info.external_attr |= 0x10
            archive.writestr(info, b'' if directory else (stage / name).read_bytes(),
                             compress_type=zipfile.ZIP_DEFLATED, compresslevel=6)
    return records


def package():
    build.macos_toolchain.require_host()
    report, verification = verified_build()
    app = Path(report['app'])
    public = summary(report, verification)
    public['build_id'] = fingerprint({'summary': public, 'guides': GUIDES,
                                      'packager': build.digest(Path(__file__))})[:16]
    directory = build.BUILD / 'release'
    directory.mkdir(parents=True, exist_ok=True)
    if report['channel'] == 'local':
        filename = f'StrandedII-{report["version"]}-macos-arm64-preview-{public["build_id"]}.zip'
    else:
        from release import macos_archive_name
        filename = macos_archive_name(report)
    destination = directory / filename
    with tempfile.TemporaryDirectory(prefix='package-', dir=build.BUILD) as temporary:
        temporary = Path(temporary)
        stage = temporary / 'stage'
        stage.mkdir()
        shutil.copytree(app, stage / app.name)
        for language, text in GUIDES.items():
            (stage / f'README-{language}.txt').write_text(text, encoding='utf8', newline='\n')
        build.write(stage / 'verification-summary.json', public)
        candidate = temporary / filename
        records = write_archive(candidate, stage)
        extracted = temporary / '압축 해제 Test'
        subprocess.run(['ditto', '-x', '-k', str(candidate), str(extracted)], check=True)
        if tree_records(extracted) != records:
            raise ValueError('Native ZIP round-trip changed file contents or permissions.')
        extracted_facts = validate_app(extracted / app.name, build.read(app.parent / 'package-report.json'))
        if extracted_facts != verification['app']:
            raise ValueError('Extracted native app differs from the verified app.')
        # Recheck the originals after the ZIP round-trip to detect concurrent changes.
        if verified_build() != (report, verification):
            raise ValueError('Native build or verification changed during packaging.')
        checksum = build.digest(candidate)
        if destination.exists():
            if destination.is_symlink() or build.digest(destination) != checksum:
                raise ValueError(f'Preserving an existing, different ZIP: {destination}')
        else:
            os.replace(candidate, destination)
    checksum_file = destination.with_suffix('.zip.sha256')
    expected = f'{checksum}  {destination.name}\n'
    if checksum_file.exists() and (checksum_file.is_symlink() or checksum_file.read_text('ascii') != expected):
        raise ValueError(f'Preserving a modified checksum: {checksum_file}')
    checksum_file.write_text(expected, encoding='ascii')
    build.write(build.BUILD / 'distribution-report.json', {
        'zip': str(destination), 'sha256': checksum, 'build_id': public['build_id'],
        'suite': verification['suite'], 'zip_round_trip': 'pass',
        'extracted_app_signature_and_permissions': 'pass', 'personal_data_included': False})
    print('Test ZIP:', destination)
    print('SHA-256:', checksum)
    return destination


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.parse_args()
    try:
        package()
    except (ValueError, OSError, KeyError, subprocess.CalledProcessError) as exc:
        parser.exit(1, f'Error: {exc}\n')


if __name__ == '__main__':
    main()
