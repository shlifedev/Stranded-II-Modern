"""Package a verified Mac preview or signed and notarized distribution. Does not publish."""
from pathlib import Path
import argparse
import os
import shutil
import subprocess
import tempfile

import macos_build as build
import macos_dmg as dmg
from macos_signing import sign_and_notarize, sign_and_notarize_dmg, verify_dmg_signature
from macos_validation import fingerprint, validate_app, verified_build
from versioning import validate_info

GUIDES = {
    'en': '''Stranded II — Native Mac build (experimental)

Requires Apple Silicon and targets macOS 15.0 or later. No browser, Python,
Homebrew, or separate game download is required to play.

Open the DMG, drag Stranded II.app onto Applications, eject the disk image,
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

DMG를 열고 Stranded II.app을 Applications 바로가기로 드래그하세요.
복사가 끝나면 DMG를 꺼내고 설치한 앱을 실행하세요. 로컬 서명한 테스트판이며 공증하지 않았습니다.
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


def guides(notarized):
    if not notarized:
        return GUIDES
    result = dict(GUIDES)
    result['en'] = result['en'].replace(
        'then double-click it. This test build is locally signed and is not notarized.\n'
        'macOS may require approval in System Settings > Privacy & Security to open it.\n'
        "Apple's instructions: https://support.apple.com/102445",
        'then double-click it. This build is signed with Developer ID and notarized by\n'
        'Apple, with the notarization ticket attached to the app.')
    result['ko'] = result['ko'].replace(
        '복사가 끝나면 DMG를 꺼내고 설치한 앱을 실행하세요. 로컬 서명한 테스트판이며 공증하지 않았습니다.\n'
        'macOS가 실행을 차단하면 시스템 설정 > 개인정보 보호 및 보안에서 실행을\n'
        '허용해야 할 수 있습니다. Apple 안내: https://support.apple.com/102445',
        '복사가 끝나면 DMG를 꺼내고 설치한 앱을 실행하세요. Developer ID로 서명하고 Apple 공증을 받은 빌드이며,\n'
        '공증 티켓이 앱에 첨부되어 있습니다.')
    return result


def summary(report, verification, app=None):
    # Allowlist public metadata. Local dependency paths and fixture logs stay local.
    app = verification['app'] if app is None else app
    game = verification['game']
    if isinstance(game, dict):
        game = {key: game[key] for key in ('checks_passed', 'synthetic_time', 'app_bundle')}
    return {
        'product': 'Stranded II', 'version': report['version'], 'source_commit': report['commit'],
        'version_info': validate_info(report),
        'source_fingerprint': fingerprint({'engine': report['source_sha256'],
                                           'implementation': report['implementation_sha256']}),
        'platform': 'macos-arm64', 'minimum_macos': app['minimum_macos'],
        'app_sha256': app['app_sha256'], 'signing': app['signature'],
        'notarized': app['notarized'], 'team_id': app.get('team_id'),
        'hardened_runtime': app.get('hardened_runtime', False),
        'secure_timestamp': app.get('secure_timestamp', False),
        'build_environment': report.get('environment', {}), 'suite': verification['suite'],
        'checks': {'abi': verification['abi'], 'app_structure_and_signature': 'pass',
                   'metal_render_and_readback': verification['metal_render_and_readback'],
                   'bundle_storage': verification['bundle_storage'], 'game': game},
        'manual_checks': {key: 'not_run' for key in (
            'minimum_macos_gameplay', 'other_mac_gameplay', 'ime_composition',
            'fullscreen_and_focus', 'audible_sound', '30_minute_play_and_reload')},
        'personal_data_included': False,
    }


def package(notarize=False):
    build.macos_toolchain.require_host()
    report, verification = verified_build()
    if report['channel'] != 'local' and not notarize:
        raise ValueError('Release packages require --notarize and Apple signing secrets.')
    app = Path(report['app'])
    directory = build.BUILD / 'release'
    directory.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(prefix='package-', dir=build.BUILD) as temporary:
        temporary = Path(temporary)
        stage = temporary / 'stage'
        stage.mkdir()
        staged_app = stage / app.name
        shutil.copytree(app, staged_app)
        package_report = build.read(app.parent / 'package-report.json')
        if validate_app(staged_app, package_report) != verification['app']:
            raise ValueError('Distribution copy differs from the verified app.')
        if notarize:
            package_report = sign_and_notarize(staged_app, package_report)
        final_facts = validate_app(staged_app, package_report)
        public = summary(report, verification, final_facts)
        if notarize:
            public['notarization'] = package_report['notarization']
            public['checks']['stapled_ticket_and_gatekeeper'] = 'pass'
        instructions = guides(notarize)
        public['build_id'] = fingerprint({'summary': public, 'guides': instructions,
                                          'packager': build.digest(Path(__file__)),
                                          'dmg': build.digest(Path(dmg.__file__))})[:16]
        if report['channel'] == 'local':
            filename = f'StrandedII-{report["version"]}-macos-arm64-preview-{public["build_id"]}.dmg'
        else:
            from release import macos_archive_name
            filename = macos_archive_name(report)
        destination = directory / filename
        for language, text in instructions.items():
            (stage / f'README-{language}.txt').write_text(text, encoding='utf8', newline='\n')
        build.write(stage / 'verification-summary.json', public)
        (stage / 'Applications').symlink_to('/Applications')
        candidate = temporary / filename
        records = dmg.write_image(candidate, stage)
        dmg_notarization = sign_and_notarize_dmg(candidate) if notarize else None
        with dmg.mounted_image(candidate) as mounted:
            if dmg.payload_records(mounted) != records:
                raise ValueError('Native DMG round-trip changed file contents or permissions.')
            installed = temporary / '복사 Test' / app.name
            subprocess.run(['ditto', str(mounted / app.name), str(installed)], check=True)
            if validate_app(installed, package_report) != final_facts:
                raise ValueError('Copied native app differs from the verified app.')
        # Recheck the originals after the DMG round-trip to detect concurrent changes.
        if verified_build() != (report, verification):
            raise ValueError('Native build or verification changed during packaging.')
        if destination.exists() or destination.is_symlink():
            if destination.is_symlink() or not destination.is_file():
                raise ValueError(f'Preserving an existing, different DMG: {destination}')
            # DMG filesystem UUIDs/timestamps need not be byte-reproducible.
            with dmg.mounted_image(destination) as mounted:
                if dmg.payload_records(mounted) != records:
                    raise ValueError(f'Preserving an existing, different DMG: {destination}')
            if notarize:
                verify_dmg_signature(destination, public['team_id'])
        else:
            os.replace(candidate, destination)
        checksum = build.digest(destination)
    checksum_file = destination.with_suffix('.dmg.sha256')
    expected = f'{checksum}  {destination.name}\n'
    if (checksum_file.exists() or checksum_file.is_symlink()) and (
            checksum_file.is_symlink() or checksum_file.read_text('ascii') != expected):
        raise ValueError(f'Preserving a modified checksum: {checksum_file}')
    checksum_file.write_text(expected, encoding='ascii')
    build.write(build.BUILD / 'distribution-report.json', {
        'dmg': str(destination), 'sha256': checksum, 'build_id': public['build_id'],
        'suite': verification['suite'], 'dmg_round_trip': 'pass',
        'copied_app_signature_and_permissions': 'pass', 'personal_data_included': False,
        'signing': public['signing'], 'notarized': public['notarized'],
        'dmg_notarization': dmg_notarization})
    print('Distribution DMG:', destination)
    print('SHA-256:', checksum)
    return destination


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--notarize', action='store_true', help='Use Apple secrets to sign and notarize (required for releases)')
    args = parser.parse_args()
    try:
        package(args.notarize)
    except (ValueError, OSError, KeyError, subprocess.CalledProcessError) as exc:
        parser.exit(1, f'Error: {exc}\n')


if __name__ == '__main__':
    main()
