"""Native preview boundaries: player data, changed apps, and stale verification."""
from pathlib import Path
from tempfile import TemporaryDirectory
from unittest import TestCase, mock
import copy
import hashlib
import json
import plistlib
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / 'scripts'))
import macos_build as build
import macos_package as package
import macos_validation as validation
import verify_macos as verify
from editions import source_files
from versioning import make_info


class NativeToolchain(TestCase):
    def test_macos_compiler_and_both_runtimes_share_the_target_and_sdk(self):
        engine = build.engine_toolchain
        with TemporaryDirectory() as directory:
            root = Path(directory)
            (root / 'config').mkdir()
            (root / 'config/macos-toolchain.json').write_text(
                json.dumps({'deployment_target': '15.0'}), encoding='utf8')
            sdk_path = '/Applications/Xcode SDK/MacOSX.sdk'
            with mock.patch.object(engine, 'ROOT', root), \
                 mock.patch.object(engine, 'host', return_value='macos'), \
                 mock.patch.object(engine, 'environment', return_value={}), \
                 mock.patch.object(engine, 'configure', return_value=root / 'cmake') as configure, \
                 mock.patch.object(engine, 'run'), \
                 mock.patch.object(engine, 'dawn', return_value=root / 'dawn'), \
                 mock.patch.object(engine.subprocess, 'check_output', return_value=sdk_path + '\n'):
                engine.build_compiler()
                engine.build_native(graphics=False)
                engine.build_native(graphics=True)
            self.assertEqual([call.args[0] for call in configure.call_args_list],
                             ['compiler-macos', 'macos-console', 'macos-game'])
            for call in configure.call_args_list:
                options = call.args[1]
                self.assertIn('-DCMAKE_OSX_DEPLOYMENT_TARGET=15.0', options)
                self.assertIn('-DCMAKE_OSX_SYSROOT=' + sdk_path, options)
                self.assertIn('-DCMAKE_OSX_ARCHITECTURES=arm64', options)

    def test_windows_builds_do_not_query_or_use_the_apple_sdk(self):
        engine = build.engine_toolchain
        with mock.patch.object(engine, 'host', return_value='windows'), \
             mock.patch.object(engine, 'environment', return_value={}), \
             mock.patch.object(engine, 'configure', return_value=ROOT / 'build/cmake') as configure, \
             mock.patch.object(engine, 'run'), \
             mock.patch.object(engine, 'dawn', return_value=ROOT / 'build/dawn'), \
             mock.patch.object(engine, 'openal', return_value=ROOT / 'build/openal'), \
             mock.patch.object(engine.subprocess, 'check_output', side_effect=AssertionError('Apple SDK queried')):
            engine.build_compiler()
            engine.build_native(graphics=False)
            engine.build_native(graphics=True)
        self.assertEqual([call.args[0] for call in configure.call_args_list],
                         ['compiler-windows', 'windows-console', 'windows-game'])
        for call in configure.call_args_list:
            self.assertFalse(any(option.startswith('-DCMAKE_OSX_') for option in call.args[1]))


class ResourceBoundaries(TestCase):
    def test_platform_include_graphs_remain_separate(self):
        native = source_files(ROOT, 'classic', 'macos')
        self.assertTrue(set(source_files(ROOT)).issubset(native))
        self.assertTrue(any('/platforms/macos/' in name for name in native))
        for edition in ('classic', 'extended'):
            windows = source_files(ROOT, edition)
            self.assertEqual(windows, source_files(ROOT, edition, 'windows'))
            self.assertFalse(any('/platforms/macos/' in name for name in windows))
        with self.assertRaises(ValueError):
            source_files(ROOT, 'extended', 'macos')

    def test_suite_flag_cannot_silently_change_other_platform_checks(self):
        for platform in ('windows', 'web'):
            result = subprocess.run([sys.executable, str(ROOT / 'scripts/dev.py'),
                'check', '--platform', platform, '--suite', 'core'], capture_output=True, text=True)
            self.assertNotEqual(result.returncode, 0)
            self.assertIn('--suite is only supported', result.stderr)

    def test_app_uses_official_defaults_not_player_preferences(self):
        with TemporaryDirectory() as directory:
            root = Path(directory)
            game, runtime = root / 'deps/game', root / 'runtime'
            name = 'mods/Stranded II/sys/settings.inf'
            for base, data in ((game, b'official'), (runtime, b'private preferences')):
                (base / name).parent.mkdir(parents=True)
                (base / name).write_bytes(data)
            (root / 'config').mkdir()
            build.write(root / 'config/game-files.json', {name: hashlib.sha256(b'official').hexdigest()})
            with mock.patch.multiple(build, ROOT=root, RUNTIME=runtime), \
                 mock.patch.object(build, 'inputs', return_value={'game': str(game)}), \
                 mock.patch.object(build, 'asset_names', return_value=[name, 'core/modern.ini']), \
                 mock.patch.object(build, 'default_ini', return_value='language=en\n'):
                self.assertEqual(build.asset_payload(), {name: b'official', 'core/modern.ini': b'language=en\n'})
                (game / name).write_bytes(b'changed input preferences')
                with self.assertRaisesRegex(ValueError, 'Official default preferences changed'):
                    build.asset_payload()
            self.assertEqual((runtime / name).read_bytes(), b'private preferences')

    def test_asset_allowlist_excludes_saves_screenshots_and_unknown_licenses(self):
        with TemporaryDirectory() as directory:
            root = Path(directory)
            runtime = root / 'runtime'
            files = ['mods/Stranded II/maps/island.s2', 'core/locales/ko/0_0.bmp',
                     'licenses/known.txt', 'licenses/private.txt', 'personal.cfg',
                     'mods/Stranded II/saves/me.sav', 'mods/Stranded II/screen_001.bmp',
                     'preserved/old.cfg', 'core/modern.log']
            for name in files:
                path = runtime / name
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_bytes(b'fixture')
            build.write(root / 'config/game-files.json', {files[0]: 'hash'})
            with mock.patch.multiple(build, ROOT=root, RUNTIME=runtime), \
                 mock.patch.object(build, 'languages', return_value={'ko': {}}), \
                 mock.patch.object(build, 'override_files', return_value={}), \
                 mock.patch.object(build, 'license_sources', return_value={'known.txt': root / 'license'}):
                names = set(build.asset_names())
            self.assertTrue(set(files[:3]).issubset(names))
            self.assertFalse(set(files[3:]) & names)


class AppValidation(TestCase):
    def setUp(self):
        self.temporary = TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name)
        self.app = self.root / 'Stranded II.app'
        self.payload = {'core/modern.ini': b'language=en\n',
                        'core/version.json': json.dumps(make_info('1.1.0', 'local')).encode()}
        self.cfg = {'architecture': 'arm64', 'deployment_target': '15.0'}
        for name, data in self.payload.items():
            path = self.app / 'Contents/Resources/game' / name
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_bytes(data)
        hashes = {name: hashlib.sha256(data).hexdigest() for name, data in self.payload.items()}
        build.write(self.app / 'Contents/Resources/native-assets.json',
                    {'fingerprint': validation.fingerprint(hashes), 'files': list(self.payload)})
        self.executable = self.app / 'Contents/MacOS/StrandedII'
        self.executable.parent.mkdir()
        self.executable.write_bytes(b'fake native executable')
        self.executable.chmod(0o755)
        signature = self.app / 'Contents/_CodeSignature/CodeResources'
        signature.parent.mkdir()
        signature.write_bytes(b'fake signature')
        (self.app / 'Contents/Info.plist').write_bytes(plistlib.dumps({
            'CFBundleExecutable': 'StrandedII', 'CFBundleIdentifier': 'local.strandedii.modern.native-preview',
            'LSMinimumSystemVersion': '15.0', 'CFBundleVersion': '1.1.0',
            'CFBundleShortVersionString': '1.1.0'}))
        build.write(self.root / 'config/version.json', {'version': '1.1.0'})
        self.report = {'asset_sha256': hashes, 'signed_executable_sha256': build.digest(self.executable)}
        for patcher in (mock.patch.object(build, 'ROOT', self.root),
                        mock.patch.object(build, 'asset_payload', return_value=self.payload),
                        mock.patch.object(build.macos_toolchain, 'config', return_value=self.cfg),
                        mock.patch.object(validation.subprocess, 'run', return_value=
                                          subprocess.CompletedProcess([], 0, stdout=b'', stderr='Signature=adhoc\n')),
                        mock.patch.object(validation.subprocess, 'check_output', side_effect=self.tool_output)):
            patcher.start()
            self.addCleanup(patcher.stop)
        self.libraries = 'app:\n\t/usr/lib/libSystem.B.dylib (compatibility version 1.0.0)\n'
        self.minos = '15.0'

    def tool_output(self, command, **kwargs):
        if 'lipo' in command:
            return 'arm64\n'
        if 'vtool' in command:
            return f'platform MACOS\n minos {self.minos}\n'
        return self.libraries

    def check_app(self):
        return validation.validate_app(self.app, self.report)

    def test_unchanged_app_passes(self):
        self.assertEqual(self.check_app()['status'], 'pass')

    def test_local_app_excludes_development_marker_and_passes_validation(self):
        runtime = self.root / 'runtime'
        marker = runtime / 'core/development.flag'
        marker.parent.mkdir(parents=True)
        marker.write_bytes(b'local development runtime\n')
        with mock.patch.multiple(build, BUILD=self.root / 'build', RUNTIME=runtime):
            app = build.package_app(self.executable)
        signature = app / 'Contents/_CodeSignature/CodeResources'
        signature.parent.mkdir()
        signature.write_bytes(b'fake signature')
        report = build.read(app.parent / 'package-report.json')
        self.assertEqual(validation.validate_app(app, report)['status'], 'pass')
        self.assertFalse((app / 'Contents/Resources/game/core/development.flag').exists())
        self.assertNotIn('core/development.flag', report['asset_sha256'])
        self.assertEqual(marker.read_bytes(), b'local development runtime\n')

    def test_changed_executable_rejected(self):
        self.executable.write_bytes(b'changed')
        with self.assertRaisesRegex(ValueError, 'executable changed'):
            self.check_app()

    def test_changed_resource_rejected(self):
        (self.app / 'Contents/Resources/game/core/modern.ini').write_bytes(b'language=ko\n')
        with self.assertRaisesRegex(ValueError, 'resource changed'):
            self.check_app()

    def test_extra_private_file_rejected(self):
        (self.app / 'personal.sav').write_bytes(b'private')
        with self.assertRaisesRegex(ValueError, 'unexpected'):
            self.check_app()

    def test_symlink_rejected(self):
        (self.app / 'private-link').symlink_to(self.root)
        with self.assertRaisesRegex(ValueError, 'Unsupported app member'):
            self.check_app()

    def test_execute_permission_required(self):
        self.executable.chmod(0o644)
        with self.assertRaisesRegex(ValueError, 'executable permissions'):
            self.check_app()

    def test_homebrew_runtime_dependency_rejected(self):
        self.libraries = 'app:\n\t/opt/homebrew/lib/libzstd.dylib (compatibility version 1.0.0)\n'
        with self.assertRaisesRegex(ValueError, 'non-system libraries'):
            self.check_app()

    def test_higher_macos_target_rejected(self):
        self.minos = '26.0'
        with self.assertRaisesRegex(ValueError, 'deployment target'):
            self.check_app()

    def test_signature_failure_is_not_swallowed(self):
        validation.subprocess.run.side_effect = subprocess.CalledProcessError(1, 'codesign')
        with self.assertRaises(subprocess.CalledProcessError):
            self.check_app()

    def test_stapled_ticket_is_allowed_only_for_notarized_app(self):
        (self.app / 'Contents/CodeResources').write_bytes(b'fixture ticket')
        with self.assertRaisesRegex(ValueError, 'unexpected'):
            self.check_app()
        self.report.update(notarized=True, team_id='TEAM123456')
        with mock.patch.object(validation, 'verify_signature', return_value={'signature': 'Developer ID Application'}):
            self.assertEqual(self.check_app()['status'], 'pass')
            (self.app / 'Contents/CodeResources').unlink()
            with self.assertRaisesRegex(ValueError, 'unexpected'):
                self.check_app()


class VerificationGates(TestCase):
    def setUp(self):
        self.report = {key: 'hash' for key in ('source_sha256', 'implementation_sha256', 'executable_sha256')}
        self.facts = {'app_sha256': 'app hash'}
        self.check = dict(self.report, passed=True, suite='core', app=self.facts, verifier_sha256='verifier',
                          abi={'bytes_equal_to_blitz_1_118': 613, 'type_reuse': 'pass'},
                          metal_render_and_readback='not_run', bundle_storage='not_run', game='not_run')
        for patcher in (mock.patch.object(validation, 'current_build', return_value=self.report),
                        mock.patch.object(validation, 'check_built_app', return_value=self.facts),
                        mock.patch.object(validation, 'verifier_hashes', return_value='verifier'),
                        mock.patch.object(build, 'read', side_effect=lambda _: self.check)):
            patcher.start()
            self.addCleanup(patcher.stop)

    def test_successful_core_report_is_accepted(self):
        self.assertEqual(validation.verified_build(), (self.report, self.check))

    def test_failed_missing_old_and_mislabelled_reports_rejected(self):
        original = copy.deepcopy(self.check)
        changes = {'passed': False, 'suite': 'full', 'source_sha256': 'old source',
                   'implementation_sha256': 'old implementation', 'executable_sha256': 'old executable',
                   'verifier_sha256': 'old checks', 'app': {'app_sha256': 'old app'},
                   'abi': {}, 'metal_render_and_readback': 'pass'}
        for key, value in changes.items():
            with self.subTest(key=key):
                self.check = dict(original, **{key: value})
                with self.assertRaises(ValueError):
                    validation.verified_build()


class Suites(TestCase):
    def test_core_never_starts_graphical_checks(self):
        with TemporaryDirectory() as directory:
            report = {key: {} for key in ('source_sha256', 'implementation_sha256', 'executable_sha256')}
            with mock.patch.object(verify, 'BUILD', Path(directory)), \
                 mock.patch.object(verify.macos_toolchain, 'require_host'), \
                 mock.patch.object(verify, 'validate'), \
                 mock.patch.object(verify, 'current_build', return_value=report), \
                 mock.patch.object(verify, 'check_built_app', return_value={'status': 'pass'}), \
                 mock.patch.object(verify, 'verifier_hashes', return_value={}), \
                 mock.patch.object(verify, 'check_abi', return_value={'type_reuse': 'pass'}), \
                 mock.patch.object(verify.macos_toolchain, 'build', side_effect=AssertionError('GUI build called')), \
                 mock.patch.object(verify, 'check_bundle', side_effect=AssertionError('GUI fixture called')), \
                 mock.patch.object(verify, 'check_game', side_effect=AssertionError('Game fixture called')):
                verify.main('core')
            result = json.loads((Path(directory) / 'verification.json').read_text())
            self.assertTrue(result['passed'])
            self.assertEqual(result['game'], 'not_run')
            self.assertEqual(result['suite'], 'core')


if __name__ == '__main__':
    import unittest
    unittest.main()
