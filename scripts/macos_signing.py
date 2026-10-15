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
import time

import macos_build as build

SECRET_NAMES = ('APPLE_CERTIFICATE', 'APPLE_CERTIFICATE_PASSWORD', 'APPLE_ID',
                'APPLE_PASSWORD', 'APPLE_TEAM_ID')
STAPLER_RETRY_DELAYS = (5, 15, 30)
TRANSIENT_NETWORK_ERROR = re.compile(
    r'\b(?:NSURLErrorDomain|kCFErrorDomainCFNetwork) Code=-(?:1001|1003|1004|1005|1006|1009)\b')


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


def stapler(action, path):
    """Retry only transient Apple ticket-service network failures, never invalid tickets."""
    command = ['xcrun', 'stapler', action, str(path)]
    for attempt in range(len(STAPLER_RETRY_DELAYS) + 1):
        try:
            result = subprocess.run(command, capture_output=True, text=True, check=True)
            if isinstance(result.stdout, str) and result.stdout:
                print(result.stdout, end='', flush=True)
            return
        except subprocess.CalledProcessError as exc:
            output = (exc.stdout or '') + '\n' + (exc.stderr or '')
            if attempt == len(STAPLER_RETRY_DELAYS) or not TRANSIENT_NETWORK_ERROR.search(output):
                print(output, flush=True)
                raise
            delay = STAPLER_RETRY_DELAYS[attempt]
            print(f'Apple ticket-service network failure; retrying stapler {action} '
                  f'in {delay}s (attempt {attempt + 2}/{len(STAPLER_RETRY_DELAYS) + 1}).', flush=True)
            time.sleep(delay)


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
        stapler('validate', app)
        subprocess.run(['spctl', '--assess', '--type', 'execute', '--verbose=2', str(app)], check=True)
    return {'signature': 'Developer ID Application', 'team_id': team_id,
            'hardened_runtime': True, 'secure_timestamp': True, 'notarized': notarized}


def notarize_artifact(path, keychain, report_name='notarization'):
    print('Submitting distribution to Apple notarization (up to 20 minutes).', flush=True)
    auth = ['--keychain-profile', 'stranded-distribution', '--keychain', keychain]
    result = json.loads(private_run(['xcrun', 'notarytool', 'submit', str(path),
                                    *auth, '--wait', '--timeout', '20m', '--output-format', 'json']))
    record = {key: result.get(key) for key in ('id', 'status')}
    build.write(build.BUILD / f'{report_name}-report.json', record)
    if not isinstance(record['id'], str) or not re.fullmatch(r'[0-9a-fA-F-]{36}', record['id']):
        raise ValueError('Notarization did not return a submission ID.')
    log = json.loads(private_run(['xcrun', 'notarytool', 'log', record['id'], *auth]))
    build.write(build.BUILD / f'{report_name}-log.json', log)
    if record['status'] != 'Accepted':
        raise ValueError(f'Notarization was not accepted: {record["status"]}; see {report_name}-log.json.')
    return record


def verify_dmg_signature(path, team_id):
    subprocess.run(['codesign', '--verify', '--strict', str(path)], check=True)
    details = subprocess.run(['codesign', '--display', '--verbose=4', str(path)],
                             capture_output=True, text=True, check=True).stderr
    if (not re.fullmatch(r'[A-Z0-9]{10}', team_id or '') or
            f'TeamIdentifier={team_id}\n' not in details or
            '\nAuthority=Developer ID Application:' not in details or
            not re.search(r'^Timestamp=.+$', details, re.M)):
        raise ValueError('Expected a Developer ID signed DMG with matching team and secure timestamp.')
    stapler('validate', path)
    subprocess.run(['spctl', '--assess', '--type', 'open', '--context',
                    'context:primary-signature', '--verbose=2', str(path)], check=True)


def sign_and_notarize_dmg(path):
    with credentials() as (keychain, identity, team):
        subprocess.run(['codesign', '--force', '--sign', identity, '--keychain', keychain,
                        '--timestamp', str(path)], check=True)
        record = notarize_artifact(path, keychain, 'notarization-dmg')
        stapler('staple', path)
        verify_dmg_signature(path, team)
    return record


def sign_and_notarize(app, package_report):
    with credentials() as (keychain, identity, team):
        print('Signing distribution app with Developer ID.', flush=True)
        subprocess.run(['codesign', '--force', '--sign', identity, '--keychain', keychain,
                        '--options', 'runtime', '--timestamp', str(app)], check=True)
        verify_signature(app, team)
        with tempfile.TemporaryDirectory(prefix='notary-') as directory:
            archive = Path(directory) / 'submission.zip'
            subprocess.run(['ditto', '-c', '-k', '--keepParent', str(app), str(archive)], check=True)
            record = notarize_artifact(archive, keychain)
        stapler('staple', app)
        verify_signature(app, team, notarized=True)
    return dict(package_report, signing='Developer ID Application', team_id=team, notarized=True,
                notarization=record, signed_executable_sha256=build.digest(app / 'Contents/MacOS/StrandedII'))
