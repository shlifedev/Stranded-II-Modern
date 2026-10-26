from contextlib import contextmanager, nullcontext
from pathlib import Path
from tempfile import TemporaryDirectory
from unittest import TestCase, mock
import base64
import json
import plistlib
import subprocess
import sys

sys.path.insert(0, str(Path(__file__).resolve().parents[2] / 'src/devtools'))
import macos_build as build
import macos_package as package
import macos_signing as signing

TEAM = 'TEAM123456'
DETAILS = ('CodeDirectory v=20500 size=500 flags=0x10000(runtime)\n'
           'Authority=Developer ID Application: Example (TEAM123456)\n'
           'TeamIdentifier=TEAM123456\nTimestamp=Oct 14, 2026 at 1:00 PM\n')


class StaplerRetries(TestCase):
    def test_observed_cloudkit_disconnect_retries_the_same_ticket_operation(self):
        for action in ('staple', 'validate'):
            command = ['xcrun', 'stapler', action, 'fixture.app']
            failure = subprocess.CalledProcessError(68, command, stderr=(
                'error is Error Domain=NSURLErrorDomain Code=-1005 "The network connection was lost."\n'
                "CloudKit's response is inconsistent with expections: (null)\n"
                f'The {action} action failed! Error 68.'))
            success = subprocess.CompletedProcess(command, 0, stdout='Ticket check passed.\n', stderr='')
            with self.subTest(action=action), mock.patch.object(signing.subprocess, 'run',
                    side_effect=[failure, success]) as run, mock.patch.object(signing.time, 'sleep') as sleep:
                signing.stapler(action, Path('fixture.app'))
                self.assertEqual(run.call_count, 2)
                self.assertTrue(all(call.args[0] == command for call in run.call_args_list))
                sleep.assert_called_once_with(5)

    def test_persistent_network_failure_stops_after_four_attempts(self):
        failure = subprocess.CalledProcessError(68, 'stapler', stderr='Error Domain=NSURLErrorDomain Code=-1005')
        with mock.patch.object(signing.subprocess, 'run', side_effect=failure) as run, \
             mock.patch.object(signing.time, 'sleep') as sleep:
            with self.assertRaises(subprocess.CalledProcessError):
                signing.stapler('validate', Path('fixture.dmg'))
        self.assertEqual(run.call_count, 4)
        self.assertEqual(sleep.call_args_list, [mock.call(5), mock.call(15), mock.call(30)])

    def test_invalid_missing_or_untrusted_tickets_fail_without_retry(self):
        for output in ('The validate action failed! Error 65.', 'The validate action failed! Error 68.',
                       'Error Domain=NSURLErrorDomain Code=-1202 "Certificate is not trusted."'):
            with self.subTest(output=output), mock.patch.object(signing.subprocess, 'run',
                    side_effect=subprocess.CalledProcessError(65, 'stapler', stderr=output)) as run, \
                 mock.patch.object(signing.time, 'sleep') as sleep:
                with self.assertRaises(subprocess.CalledProcessError):
                    signing.stapler('validate', Path('fixture.app'))
                run.assert_called_once()
                sleep.assert_not_called()


class SignatureValidation(TestCase):
    def verify(self, details=DETAILS, entitlements=None, notarized=False):
        def run(command, **kwargs):
            return subprocess.CompletedProcess(command, 0,
                stdout=plistlib.dumps(entitlements) if entitlements else b'', stderr=details)
        with mock.patch.object(signing.subprocess, 'run', side_effect=run):
            return signing.verify_signature(Path('fixture.app'), TEAM, notarized)

    def test_valid_developer_id_is_recognized(self):
        self.assertEqual(self.verify()['signature'], 'Developer ID Application')
        self.assertFalse(self.verify()['notarized'])

    def test_wrong_team_adhoc_missing_runtime_and_timestamp_fail(self):
        for details in ('Signature=adhoc\n', DETAILS.replace(TEAM, 'OTHER12345'),
                        DETAILS.replace('0x10000(runtime)', '0x0(none)'),
                        DETAILS.replace('Timestamp=', 'Signed Time='),
                        DETAILS.replace('Developer ID Application:', 'Apple Development:')):
            with self.subTest(details=details), self.assertRaises(ValueError):
                self.verify(details)
        with self.assertRaisesRegex(ValueError, 'debugger'):
            self.verify(entitlements={'com.apple.security.get-task-allow': True})

    def test_ticket_or_gatekeeper_failure_is_fatal(self):
        for tool in ('stapler', 'spctl'):
            def run(command, **kwargs):
                if tool in command:
                    raise subprocess.CalledProcessError(1, command)
                return subprocess.CompletedProcess(command, 0, stdout=b'', stderr=DETAILS)
            with self.subTest(tool=tool), mock.patch.object(signing.subprocess, 'run', side_effect=run):
                with self.assertRaises(subprocess.CalledProcessError):
                    signing.verify_signature(Path('fixture.app'), TEAM, notarized=True)

    def test_dmg_requires_developer_id_timestamp_and_attached_ticket(self):
        for details in (DETAILS, DETAILS.replace(TEAM, 'OTHER12345'),
                        DETAILS.replace('Timestamp=', 'Signed Time='), 'Signature=adhoc\n'):
            calls = []
            def run(command, **kwargs):
                calls.append(command)
                return subprocess.CompletedProcess(command, 0, stdout=b'', stderr=details)
            with self.subTest(details=details), mock.patch.object(signing.subprocess, 'run', side_effect=run):
                if details == DETAILS:
                    signing.verify_dmg_signature(Path('fixture.dmg'), TEAM)
                    self.assertEqual(calls[-1][1:4], ['--assess', '--type', 'open'])
                    self.assertTrue(any('stapler' in command for command in calls))
                else:
                    with self.assertRaisesRegex(ValueError, 'Developer ID signed DMG'):
                        signing.verify_dmg_signature(Path('fixture.dmg'), TEAM)


class Credentials(TestCase):
    def test_missing_secrets_do_not_create_a_keychain(self):
        with mock.patch.dict(signing.os.environ, {}, clear=True), mock.patch.object(signing, 'private_run') as run:
            with self.assertRaisesRegex(ValueError, 'Missing signing secrets'):
                with signing.credentials():
                    self.fail('Credentials should be unavailable')
            run.assert_not_called()

    def test_import_failure_cleans_keychain_and_does_not_expose_secrets(self):
        values = {name: 'private-value' for name in signing.SECRET_NAMES}
        values.update(APPLE_TEAM_ID=TEAM, APPLE_CERTIFICATE=base64.b64encode(b'certificate').decode())
        calls = []
        def run(command, **kwargs):
            calls.append(command)
            if command[1] == 'create-keychain':
                Path(command[-1]).touch()
            return subprocess.CompletedProcess(command, 1 if command[1] == 'import' else 0,
                                               stdout='private-value', stderr='private-value')
        with mock.patch.dict(signing.os.environ, values, clear=True), \
             mock.patch.object(signing.subprocess, 'run', side_effect=run):
            with self.assertRaisesRegex(ValueError, r'security import failed \(exit 1\)') as error:
                with signing.credentials():
                    self.fail('Import should fail')
        self.assertNotIn('private-value', str(error.exception))
        self.assertEqual(calls[-1][1], 'delete-keychain')
        self.assertFalse(Path(calls[-1][-1]).parent.exists())

    def test_keychain_search_list_lifecycle(self):
        for previous in ([], ['/Users/runner/Library/Keychains/login.keychain-db',
                               '/Users/runner/Other Keys/custom.keychain-db']):
            for failure in (None, 'signing', 'registration', 'restoration'):
                with self.subTest(previous=previous, failure=failure):
                    values = {name: 'private-value' for name in signing.SECRET_NAMES}
                    values.update(APPLE_TEAM_ID=TEAM, APPLE_CERTIFICATE=base64.b64encode(b'certificate').decode())
                    search_list, calls = previous.copy(), []
                    def run(command, **kwargs):
                        calls.append(command)
                        output, code = '', 0
                        if command[1] == 'create-keychain':
                            Path(command[-1]).touch()
                        elif command[1] == 'find-identity':
                            output = '1) ' + 'A' * 40 + f' "Developer ID Application: Example ({TEAM})"'
                        elif command[1] == 'list-keychains':
                            if '-s' not in command:
                                output = '\n'.join(json.dumps(name) for name in search_list)
                            else:
                                requested = command[5:]
                                if failure == 'restoration' and requested == previous:
                                    code = 1
                                else:
                                    search_list[:] = requested
                                    if failure == 'registration' and requested != previous:
                                        code = 1
                        elif command[1] == 'delete-keychain' and command[-1] in search_list:
                            search_list.remove(command[-1])
                        return subprocess.CompletedProcess(command, code, stdout=output, stderr='private-value')
                    error = RuntimeError if failure == 'signing' else ValueError
                    expectation = self.assertRaises(error) if failure else nullcontext()
                    with mock.patch.dict(signing.os.environ, values, clear=True), \
                         mock.patch.object(signing.subprocess, 'run', side_effect=run), expectation:
                        with signing.credentials() as (keychain, identity, team):
                            self.assertEqual(search_list, [keychain, *previous])
                            if failure == 'signing':
                                raise RuntimeError('fixture codesign failed')
                    self.assertEqual(search_list, previous)
                    self.assertEqual(calls[-1][1], 'delete-keychain')
                    self.assertFalse(Path(calls[-1][-1]).parent.exists())


class Distribution(TestCase):
    def test_release_requires_notarization(self):
        with mock.patch.object(build.macos_toolchain, 'require_host'), \
             mock.patch.object(package, 'verified_build', return_value=({'channel': 'main'}, {})):
            with self.assertRaisesRegex(ValueError, 'require --notarize'):
                package.package()

    def test_guides_describe_the_actual_signing_mode(self):
        self.assertIn('not notarized', package.guides(False)['en'])
        self.assertNotIn('not notarized', package.guides(True)['en'])
        self.assertIn('공증 티켓', package.guides(True)['ko'])
        for instructions in package.guides(True).values():
            self.assertIn('DMG', instructions)
            self.assertIn('Applications', instructions)
            self.assertNotIn('ZIP', instructions)

    def test_rejected_submission_cannot_be_stapled(self):
        @contextmanager
        def credentials():
            yield 'keychain', 'identity', TEAM
        def private_run(command):
            return json.dumps({'id': '12345678-1234-1234-1234-123456789abc', 'status': 'Invalid'})
        with TemporaryDirectory() as directory, mock.patch.object(build, 'BUILD', Path(directory)), \
             mock.patch.object(signing, 'credentials', credentials), \
             mock.patch.object(signing, 'verify_signature'), \
             mock.patch.object(signing, 'private_run', side_effect=private_run), \
             mock.patch.object(signing.subprocess, 'run') as run:
            with self.assertRaisesRegex(ValueError, 'not accepted'):
                signing.sign_and_notarize(Path('fixture.app'), {})
            self.assertFalse(any('stapler' in call.args[0] for call in run.call_args_list))
            self.assertEqual(build.read(Path(directory) / 'notarization-report.json')['status'], 'Invalid')

    def test_dmg_notarization_uses_final_image_and_staples_only_after_acceptance(self):
        @contextmanager
        def credentials():
            yield 'keychain', 'identity', TEAM
        for status in ('Accepted', 'Invalid'):
            with self.subTest(status=status), TemporaryDirectory() as directory, \
                 mock.patch.object(build, 'BUILD', Path(directory)), \
                 mock.patch.object(signing, 'credentials', credentials), \
                 mock.patch.object(signing, 'private_run', return_value=json.dumps({
                     'id': '12345678-1234-1234-1234-123456789abc', 'status': status})) as private, \
                 mock.patch.object(signing, 'verify_dmg_signature') as verify, \
                 mock.patch.object(signing.subprocess, 'run') as run:
                image = Path(directory) / 'test.dmg'
                if status == 'Accepted':
                    self.assertEqual(signing.sign_and_notarize_dmg(image)['status'], status)
                    verify.assert_called_once_with(image, TEAM)
                    self.assertTrue(any('staple' in call.args[0] for call in run.call_args_list))
                else:
                    with self.assertRaisesRegex(ValueError, 'not accepted'):
                        signing.sign_and_notarize_dmg(image)
                    verify.assert_not_called()
                    self.assertFalse(any('staple' in call.args[0] for call in run.call_args_list))
                self.assertEqual(private.call_args_list[0].args[0][3], str(image))


if __name__ == '__main__':
    import unittest
    unittest.main()
