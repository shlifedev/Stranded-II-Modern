"""Developer ID signing and notarization of an isolated distribution app."""
from contextlib import contextmanager
from pathlib import Path
import base64
import json
import os
import plistlib
import re
import secrets
import shlex
import subprocess
import tempfile

import macos_build as build

SECRET_NAMES = ('APPLE_CERTIFICATE', 'APPLE_CERTIFICATE_PASSWORD', 'APPLE_ID',
                'APPLE_PASSWORD', 'APPLE_TEAM_ID')


def private_run(command):
    # Never include argv or tool output in exceptions: both can contain credentials.
    result = subprocess.run(command, capture_output=True, text=True)
    if result.returncode:
        raise ValueError(f'{Path(command[0]).name} {command[1]} failed (exit {result.returncode}).')
    return result.stdout


@contextmanager
def credentials():
    values = {name: os.environ.get(name, '').strip() for name in SECRET_NAMES}
    missing = [name for name, value in values.items() if not value]
    if missing:
        raise ValueError('Missing signing secrets: ' + ', '.join(missing))
    if not re.fullmatch(r'[A-Z0-9]{10}', values['APPLE_TEAM_ID']):
        raise ValueError('Invalid APPLE_TEAM_ID.')
    certificate = base64.b64decode(values['APPLE_CERTIFICATE'], validate=True)
    previous_keychains = shlex.split(private_run(['security', 'list-keychains', '-d', 'user']))
    with tempfile.TemporaryDirectory(prefix='stranded-signing-') as directory:
        keychain = str(Path(directory) / 'signing.keychain-db')
        p12 = Path(directory) / 'certificate.p12'
        p12.write_bytes(certificate)
        p12.chmod(0o600)
        password = secrets.token_urlsafe(32)
        search_list_changed = False
        try:
            private_run(['security', 'create-keychain', '-p', password, keychain])
            private_run(['security', 'set-keychain-settings', '-lut', '3600', keychain])
            private_run(['security', 'unlock-keychain', '-p', password, keychain])
            private_run(['security', 'import', str(p12), '-k', keychain,
                         '-P', values['APPLE_CERTIFICATE_PASSWORD'], '-T', '/usr/bin/codesign'])
            p12.unlink()
            private_run(['security', 'set-key-partition-list', '-S', 'apple-tool:,apple:,codesign:',
                         '-s', '-k', password, keychain])
            search_list_changed = True
            private_run(['security', 'list-keychains', '-d', 'user', '-s', keychain, *previous_keychains])
            identities = private_run(['security', 'find-identity', '-v', '-p', 'codesigning', keychain])
            matches = re.findall(r'\b([0-9A-F]{40}) "Developer ID Application: [^"\n]+ \(' +
                                 re.escape(values['APPLE_TEAM_ID']) + r'\)"', identities)
            if len(matches) != 1:
                raise ValueError('Expected exactly one valid Developer ID Application identity for APPLE_TEAM_ID.')
            private_run(['xcrun', 'notarytool', 'store-credentials', 'stranded-distribution',
                         '--keychain', keychain, '--apple-id', values['APPLE_ID'],
                         '--password', values['APPLE_PASSWORD'], '--team-id', values['APPLE_TEAM_ID']])
            yield keychain, matches[0], values['APPLE_TEAM_ID']
        finally:
            try:
                if search_list_changed:
                    private_run(['security', 'list-keychains', '-d', 'user', '-s', *previous_keychains])
            finally:
                if Path(keychain).exists():
                    private_run(['security', 'delete-keychain', keychain])


def verify_signature(app, team_id=None, notarized=False):
    subprocess.run(['codesign', '--verify', '--deep', '--strict', str(app)], check=True)
    details = subprocess.run(['codesign', '--display', '--verbose=4', str(app)],
                             capture_output=True, text=True, check=True).stderr
    if team_id is None:
        if 'Signature=adhoc' not in details:
            raise ValueError('Expected a local ad hoc signature.')
        return {'signature': 'local ad hoc', 'notarized': False}
    flags = re.search(r'\bflags=0x([0-9a-fA-F]+)', details)
    if (not re.fullmatch(r'[A-Z0-9]{10}', team_id) or
            f'TeamIdentifier={team_id}\n' not in details or
            '\nAuthority=Developer ID Application:' not in details or
            not flags or not int(flags[1], 16) & 0x10000 or
            not re.search(r'^Timestamp=.+$', details, re.M)):
        raise ValueError('Expected Developer ID, matching team, hardened runtime, and a secure timestamp.')
    entitlements = subprocess.run(['codesign', '--display', '--entitlements', ':-', str(app)],
                                  capture_output=True, check=True).stdout
    if entitlements.strip() and plistlib.loads(entitlements).get('com.apple.security.get-task-allow'):
        raise ValueError('Distribution app must not allow debugger attachment.')
    if notarized:
        subprocess.run(['xcrun', 'stapler', 'validate', str(app)], check=True)
        subprocess.run(['spctl', '--assess', '--type', 'execute', '--verbose=2', str(app)], check=True)
    return {'signature': 'Developer ID Application', 'team_id': team_id,
            'hardened_runtime': True, 'secure_timestamp': True, 'notarized': notarized}


def sign_and_notarize(app, package_report):
    with credentials() as (keychain, identity, team):
        print('Signing distribution app with Developer ID.', flush=True)
        subprocess.run(['codesign', '--force', '--sign', identity, '--keychain', keychain,
                        '--options', 'runtime', '--timestamp', str(app)], check=True)
        verify_signature(app, team)
        with tempfile.TemporaryDirectory(prefix='notary-') as directory:
            archive = Path(directory) / 'submission.zip'
            subprocess.run(['ditto', '-c', '-k', '--keepParent', str(app), str(archive)], check=True)
            print('Submitting app to Apple notarization (up to 20 minutes).', flush=True)
            auth = ['--keychain-profile', 'stranded-distribution', '--keychain', keychain]
            result = json.loads(private_run(['xcrun', 'notarytool', 'submit', str(archive),
                                            *auth, '--wait', '--timeout', '20m', '--output-format', 'json']))
            record = {key: result.get(key) for key in ('id', 'status')}
            build.write(build.BUILD / 'notarization-report.json', record)
            if not isinstance(record['id'], str) or not re.fullmatch(r'[0-9a-fA-F-]{36}', record['id']):
                raise ValueError('Notarization did not return a submission ID.')
            log = json.loads(private_run(['xcrun', 'notarytool', 'log', record['id'], *auth]))
            build.write(build.BUILD / 'notarization-log.json', log)
            if record['status'] != 'Accepted':
                raise ValueError(f'Notarization was not accepted: {record["status"]}; see notarization-log.json.')
        subprocess.run(['xcrun', 'stapler', 'staple', str(app)], check=True)
        verify_signature(app, team, notarized=True)
    return dict(package_report, signing='Developer ID Application', team_id=team, notarized=True,
                notarization=record, signed_executable_sha256=build.digest(app / 'Contents/MacOS/StrandedII'))
