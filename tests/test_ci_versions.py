"""Exercise release ordering, immutable retries and publication failure recovery."""
from pathlib import Path
from unittest.mock import patch
import hashlib
import json
import sys
import tempfile
import unittest
import zipfile

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'scripts'))
import release
from versioning import make_info, parse_base, select_version, validate_info

COMMIT = 'a' * 40
OLDER = 'b' * 40


class VersionTests(unittest.TestCase):
    def test_extended_allocation_uses_its_own_baseline_and_release_history(self):
        def fake_git(root, *args):
            if args == ('rev-parse', 'origin/main'): return OLDER
            if args == ('show', OLDER + ':config/editions/extended/version.json'): return '{"version":"0.1.0"}'
            if args == ('tag', '--list'): return 'v0.1.99\nextended-v0.1.0'
            if args == ('rev-parse', 'extended-v0.1.0^{}'): return OLDER
            self.fail(args)
        stable = dict(tag_name='extended-v0.1.0', draft=False, prerelease=False, assets=[])
        with patch.object(release, 'git', side_effect=fake_git), patch.object(release, 'releases', return_value=[stable]):
            info, published = release.allocation(Path('.'), 'owner/repo', 'dev', COMMIT, 'extended')
        self.assertEqual(info['version'], '0.1.1-dev.1')
        self.assertEqual(info['tag'], 'extended-v0.1.1-dev.1')
        self.assertEqual(info['base_commit'], OLDER)
        self.assertFalse(published)

    def test_first_main_and_dev_start_at_declared_base(self):
        tags = {'ci-deps-v1': OLDER, 'v1.0.9': OLDER, 'v2.0.0': OLDER}
        self.assertEqual(select_version('1.1.0', 'main', COMMIT, tags, set()), '1.1.0')
        self.assertEqual(select_version('1.1.0', 'dev', COMMIT, tags, set()), '1.1.0-dev.1')
        self.assertEqual(select_version('1.2.4', 'main', COMMIT, {}, set()), '1.2.4')

    def test_dev_uses_next_published_main_and_independent_sequence(self):
        tags = {'v1.1.1': OLDER, 'v1.1.2-dev.1': OLDER, 'v1.1.2-dev.9': OLDER}
        self.assertEqual(select_version('1.1.0', 'dev', COMMIT, tags, {'v1.1.1'}), '1.1.2-dev.10')
        self.assertEqual(select_version('1.1.0', 'main', COMMIT, tags, {'v1.1.1'}), '1.1.2')
        tags['v1.1.2'] = OLDER
        self.assertEqual(select_version('1.1.0', 'dev', COMMIT, tags, {'v1.1.1', 'v1.1.2'}), '1.1.3-dev.1')

    def test_new_series_resets_patch_and_dev_sequence(self):
        tags = {'v1.1.9': OLDER, 'v1.1.10-dev.20': OLDER}
        self.assertEqual(select_version('1.2.0', 'main', COMMIT, tags, {'v1.1.9'}), '1.2.0')
        self.assertEqual(select_version('1.2.0', 'dev', COMMIT, tags, {'v1.1.9'}), '1.2.0-dev.1')

    def test_unpublished_tag_reserves_number_without_advancing_dev_target(self):
        tags = {'v1.1.0': OLDER, 'v1.1.1': OLDER}
        self.assertEqual(select_version('1.1.0', 'main', COMMIT, tags, {'v1.1.0'}), '1.1.2')
        self.assertEqual(select_version('1.1.0', 'dev', COMMIT, tags, {'v1.1.0'}), '1.1.1-dev.1')

    def test_retry_reuses_same_commit_and_channel_even_after_series_change(self):
        tags = {'v1.1.0': COMMIT, 'v1.1.1-dev.3': COMMIT}
        self.assertEqual(select_version('1.2.0', 'main', COMMIT, tags, set()), '1.1.0')
        self.assertEqual(select_version('1.2.0', 'dev', COMMIT, tags, set()), '1.1.1-dev.3')

    def test_invalid_baselines_and_inconsistent_metadata_are_rejected(self):
        for value in ('1.1', '01.1.0', '1.1.0-main', '-1.1.0', 110):
            with self.subTest(value=value), self.assertRaises(ValueError):
                parse_base(value)
        info = make_info('1.1.0', 'main', COMMIT)
        with self.assertRaises(ValueError):
            validate_info(dict(info, display_version='1.1.9-main'))
        with self.assertRaises(ValueError):
            make_info('1.1.0-dev.0', 'dev', COMMIT)

    def test_dev_reads_main_config_and_freezes_it_for_retry(self):
        previous = make_info('1.1.1-dev.2', 'dev', COMMIT, '1.1.0', OLDER)
        entry = dict(tag_name=previous['tag'], draft=True, prerelease=True,
                     body='<!-- stranded-ii-build: ' + json.dumps(previous) + ' -->', assets=[])
        def fake_git(root, *args):
            if args == ('rev-parse', 'origin/main'): return OLDER
            if args == ('show', OLDER + ':config/version.json'): return '{"version":"1.2.0"}'
            if args == ('tag', '--list'): return previous['tag']
            if args == ('rev-parse', previous['tag'] + '^{}'): return COMMIT
            self.fail(args)
        with patch.object(release, 'git', side_effect=fake_git), patch.object(release, 'releases', return_value=[]):
            info, published = release.allocation(Path('.'), 'owner/repo', 'dev', OLDER)
            self.assertEqual(info['version'], '1.2.0-dev.1')
            self.assertEqual(info['base_commit'], OLDER)
            self.assertFalse(published)
        with patch.object(release, 'git', side_effect=fake_git), patch.object(release, 'releases', return_value=[entry]):
            info, published = release.allocation(Path('.'), 'owner/repo', 'dev', COMMIT)
            self.assertEqual(info, previous)
            self.assertFalse(published)


class PublicationTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.directory = Path(self.temp.name)
        self.info = make_info('1.1.1-dev.1', 'dev', COMMIT, '1.1.0', OLDER)
        self.history = []
        self.tags = {}
        self.commands = []
        self.fail_upload = False
        self.archive = self.make_package(self.info)
        self.addCleanup(patch.stopall)
        patch.object(release, 'releases', side_effect=lambda _: self.history.copy()).start()
        patch.object(release, 'remote_tag', side_effect=lambda repo, tag: self.tags.get(tag)).start()
        patch.object(release, 'api', side_effect=self.api).start()
        patch.object(release, 'gh', side_effect=self.gh).start()

    def make_package(self, info):
        from editions import Edition
        prefix = Edition(info['edition']).archive_root
        path = self.directory / release.archive_name(info)
        exe = b'MZexecutable'
        with zipfile.ZipFile(path, 'w') as archive:
            archive.writestr(f'{prefix}/core/version.json', json.dumps(info))
            archive.writestr(f'{prefix}/core/version.txt', info['display_version'] + '\n')
            archive.writestr(f'{prefix}/{Edition(info["edition"]).executable}', exe)
            archive.writestr(f'{prefix}/QolInput.dll', b'MZnative input bridge')
            archive.writestr(f'{prefix}/build-info.json', json.dumps(dict(info, exe_sha256=hashlib.sha256(exe).hexdigest())))
        checksum = hashlib.sha256(path.read_bytes()).hexdigest()
        path.with_suffix('.zip.sha256').write_text(f'{checksum}  {path.name}\n', encoding='utf8')
        return path

    def make_mac_package(self, info, metadata=None, execute=True, cpu=0x100000c, signing=None, ticket=True):
        import plistlib
        import stat
        import struct
        metadata = metadata or info
        prefix = 'Stranded II.app/'
        numeric = metadata['version'].split('-')[0]
        contents = {
            'Contents/MacOS/StrandedII': struct.pack('<II', 0xfeedfacf, cpu) + bytes(24),
            'Contents/_CodeSignature/CodeResources': b'fixture signature',
            'Contents/Resources/game/core/version.json': json.dumps(metadata).encode(),
            'Contents/Resources/game/core/version.txt': (metadata['display_version'] + '\n').encode(),
            'Contents/Info.plist': plistlib.dumps({'CFBundleExecutable': 'StrandedII',
                'CFBundleVersion': numeric, 'CFBundleShortVersionString': numeric,
                'LSMinimumSystemVersion': '15.0'}),
        }
        if ticket:
            contents['Contents/CodeResources'] = b'fixture notarization ticket'
        directories = {p.as_posix() for name in contents for p in Path(name).parents if p != Path('.')}
        records = {name: {'type': 'directory', 'mode': 0o755} for name in directories}
        for name, data in contents.items():
            records[name] = {'type': 'file', 'mode': 0o755 if name.endswith('/StrandedII') and execute else 0o644,
                             'sha256': hashlib.sha256(data).hexdigest()}
        summary = {'version_info': metadata, 'version': metadata['version'], 'source_commit': metadata['commit'],
                   'app_sha256': hashlib.sha256(json.dumps(records, sort_keys=True).encode()).hexdigest(),
                   'platform': 'macos-arm64', 'minimum_macos': '15.0', 'personal_data_included': False,
                   'suite': 'core', 'checks': {'app_structure_and_signature': 'pass',
                    'stapled_ticket_and_gatekeeper': 'pass',
                    'abi': {'bytes_equal_to_blitz_1_118': 613, 'type_reuse': 'pass'}}}
        summary.update(signing='Developer ID Application', notarized=True, team_id='TEAM123456',
                       hardened_runtime=True, secure_timestamp=True,
                       notarization={'id': '12345678-1234-1234-1234-123456789abc', 'status': 'Accepted'})
        summary.update(signing or {})
        path = self.directory / release.macos_archive_name(info)
        members = {prefix: (b'', stat.S_IFDIR | 0o755),
                   'verification-summary.json': (json.dumps(summary).encode(), stat.S_IFREG | 0o644),
                   'README-en.txt': (b'instructions', stat.S_IFREG | 0o644),
                   'README-ko.txt': (b'instructions', stat.S_IFREG | 0o644)}
        for name, record in records.items():
            is_dir = record['type'] == 'directory'
            members[prefix + name + ('/' if is_dir else '')] = (
                b'' if is_dir else contents[name], (stat.S_IFDIR if is_dir else stat.S_IFREG) | record['mode'])
        with zipfile.ZipFile(path, 'w') as archive:
            for name, (data, mode) in sorted(members.items()):
                entry = zipfile.ZipInfo(name)
                entry.create_system = 3
                entry.external_attr = mode << 16
                archive.writestr(entry, data)
        checksum = hashlib.sha256(path.read_bytes()).hexdigest()
        path.with_suffix('.zip.sha256').write_text(f'{checksum}  {path.name}\n', encoding='utf8')
        return path

    def api(self, repo, path, payload):
        self.assertEqual(path, 'git/refs')
        self.tags[payload['ref'].removeprefix('refs/tags/')] = payload['sha']
        return {}

    def gh(self, *args, **kwargs):
        self.commands.append(args)
        verb, tag = args[1:3]
        if verb == 'create':
            self.history.append(dict(tag_name=tag, draft=True, prerelease=True,
                                     body=Path(args[args.index('--notes-file') + 1]).read_text('utf8'), assets=[]))
        elif verb == 'edit':
            entry = next(r for r in self.history if r['tag_name'] == tag)
            if '--notes-file' in args:
                entry['body'] = Path(args[args.index('--notes-file') + 1]).read_text('utf8')
            if '--draft=false' in args: entry['draft'] = False
        elif verb == 'upload':
            if self.fail_upload: raise ValueError('injected upload failure')
            entry = next(r for r in self.history if r['tag_name'] == tag)
            entry['assets'] = [{'name': Path(p).name} for p in args[3:args.index('--repo')]]
        return ''

    def test_interrupted_upload_resumes_same_draft_then_becomes_immutable(self):
        self.fail_upload = True
        with self.assertRaisesRegex(ValueError, 'upload failure'):
            release.publish('owner/repo', self.directory, self.info)
        self.assertTrue(self.history[0]['draft'])
        self.assertEqual(self.tags[self.info['tag']], COMMIT)
        self.fail_upload = False
        release.publish('owner/repo', self.directory, self.info)
        self.assertFalse(self.history[0]['draft'])
        self.assertEqual(sum(c[1] == 'create' for c in self.commands), 1)
        self.assertIn('--prerelease=true', self.commands[-1])
        self.assertIn('--latest=false', self.commands[-1])
        commands = self.commands.copy()
        release.publish('owner/repo', self.directory, self.info)
        self.assertEqual(self.commands, commands)

    def test_main_is_stable_and_does_not_replace_a_newer_latest(self):
        info = make_info('1.1.0', 'main', COMMIT)
        self.make_package(info)
        self.history.append(dict(tag_name='v1.2.0', draft=False, prerelease=False))
        release.publish('owner/repo', self.directory, info)
        self.assertIn('--prerelease=false', self.commands[-1])
        self.assertIn('--latest=false', self.commands[-1])

    def test_extended_stable_release_is_independent_and_never_latest(self):
        info = make_info('0.1.0', 'main', COMMIT, edition='extended')
        self.make_package(info)
        self.fail_upload = True
        with self.assertRaisesRegex(ValueError, 'upload failure'):
            release.publish('owner/repo', self.directory, info)
        self.fail_upload = False
        release.publish('owner/repo', self.directory, info)
        self.assertEqual(self.tags, {'extended-v0.1.0': COMMIT})
        self.assertIn('--prerelease=false', self.commands[-1])
        self.assertIn('--latest=false', self.commands[-1])
        previous = self.commands.copy()
        release.publish('owner/repo', self.directory, info)
        self.assertEqual(self.commands, previous)

    def test_wrong_tag_commit_and_package_tampering_fail_before_publication(self):
        self.tags[self.info['tag']] = OLDER
        with self.assertRaisesRegex(ValueError, 'different commit'):
            release.publish('owner/repo', self.directory, self.info)
        self.assertFalse(self.commands)
        self.tags.clear()
        self.archive.write_bytes(self.archive.read_bytes() + b'tampered')
        with self.assertRaisesRegex(ValueError, 'checksum'):
            release.publish('owner/repo', self.directory, self.info)
        self.assertFalse(self.tags)

    def test_local_preview_cannot_be_published(self):
        with self.assertRaisesRegex(ValueError, 'cannot be published'):
            release.publish('owner/repo', self.directory, make_info('1.1.0', 'local'))

    def test_both_platforms_resume_together_and_remain_immutable(self):
        mac = self.make_mac_package(self.info)
        self.fail_upload = True
        with self.assertRaisesRegex(ValueError, 'upload failure'):
            release.publish('owner/repo', self.directory, self.info, include_macos=True)
        self.assertEqual(set(release.saved_info(self.history[0])['artifacts']), {self.archive.name, mac.name})
        self.fail_upload = False
        release.publish('owner/repo', self.directory, self.info, include_macos=True)
        self.assertEqual(len(self.history[0]['assets']), 4)
        commands = self.commands.copy()
        release.publish('owner/repo', self.directory, self.info, include_macos=True)
        self.assertEqual(commands, self.commands)

    def test_missing_mac_cannot_publish_a_windows_only_release(self):
        with self.assertRaises(OSError):
            release.publish('owner/repo', self.directory, self.info, include_macos=True)
        self.assertFalse(self.commands)
        self.assertFalse(self.tags)

    def test_mac_from_another_commit_cannot_publish(self):
        self.make_mac_package(self.info, metadata=dict(self.info, commit=OLDER))
        with self.assertRaisesRegex(ValueError, 'does not match'):
            release.publish('owner/repo', self.directory, self.info, include_macos=True)
        self.assertFalse(self.tags)

    def test_mac_must_keep_execute_permission_and_arm64_architecture(self):
        for options in ({'execute': False}, {'cpu': 0x1000007}):
            with self.subTest(options=options):
                self.make_mac_package(self.info, **options)
                with self.assertRaisesRegex(ValueError, 'executable ARM64 app'):
                    release.publish('owner/repo', self.directory, self.info, include_macos=True)
                self.assertFalse(self.tags)

    def test_mac_cannot_be_added_to_an_existing_published_release(self):
        release.publish('owner/repo', self.directory, self.info)
        commands = self.commands.copy()
        with self.assertRaisesRegex(ValueError, 'publish a new version'):
            release.publish('owner/repo', self.directory, self.info, include_macos=True)
        self.assertEqual(commands, self.commands)

    def test_unsigned_or_unnotarized_mac_cannot_publish(self):
        for change in ({'signing': 'local ad hoc'}, {'notarized': False}, {'team_id': None},
                       {'hardened_runtime': False}, {'secure_timestamp': False},
                       {'notarization': {'status': 'Invalid'}}, {'notarization': {'status': 'Accepted'}},
                       {'checks': {}}):
            with self.subTest(change=change):
                self.make_mac_package(self.info, signing=change)
                with self.assertRaisesRegex(ValueError, 'accepted, stapled notarization'):
                    release.publish('owner/repo', self.directory, self.info, include_macos=True)
                self.assertFalse(self.commands)
                self.assertFalse(self.tags)
        self.make_mac_package(self.info, ticket=False)
        with self.assertRaisesRegex(ValueError, 'accepted, stapled notarization'):
            release.publish('owner/repo', self.directory, self.info, include_macos=True)
        self.assertFalse(self.tags)

    def test_extended_mac_publication_is_rejected(self):
        info = make_info('0.1.0', 'main', COMMIT, edition='extended')
        with self.assertRaisesRegex(ValueError, 'only support Stranded II'):
            release.publish('owner/repo', self.directory, info, include_macos=True)
        self.assertFalse(self.tags)

    def test_changed_draft_package_is_rejected(self):
        self.fail_upload = True
        with self.assertRaises(ValueError):
            release.publish('owner/repo', self.directory, self.info)
        with zipfile.ZipFile(self.archive, 'a') as archive:
            archive.writestr('StrandedII/new-resource.txt', 'different')
        checksum = hashlib.sha256(self.archive.read_bytes()).hexdigest()
        self.archive.with_suffix('.zip.sha256').write_text(f'{checksum}  {self.archive.name}\n')
        with self.assertRaisesRegex(ValueError, 'Draft artifacts changed'):
            release.publish('owner/repo', self.directory, self.info)

    def add_retired_artifact(self):
        entry = self.history[0]
        previous = release.saved_info(entry)
        name = f"StrandedII-{self.info['display_version']}-retired.zip"
        previous['artifacts'][name] = 'c' * 64
        entry['body'] = '<!-- stranded-ii-build: ' + json.dumps(previous) + ' -->'
        entry['assets'].extend({'name': n} for n in (name, name + '.sha256'))

    def test_historical_published_artifacts_remain_immutable_and_complete(self):
        release.publish('owner/repo', self.directory, self.info)
        self.add_retired_artifact()
        commands = self.commands.copy()
        release.publish('owner/repo', self.directory, self.info)
        self.assertEqual(commands, self.commands)
        self.assertEqual(len(self.history[0]['assets']), 4)
        self.history[0]['assets'].pop()
        with self.assertRaisesRegex(ValueError, 'incomplete'):
            release.publish('owner/repo', self.directory, self.info)
        self.assertEqual(commands, self.commands)

    def test_historical_draft_artifacts_cannot_be_silently_removed(self):
        self.fail_upload = True
        with self.assertRaisesRegex(ValueError, 'upload failure'):
            release.publish('owner/repo', self.directory, self.info)
        self.add_retired_artifact()
        self.fail_upload = False
        commands = self.commands.copy()
        with self.assertRaisesRegex(ValueError, 'Draft artifacts changed'):
            release.publish('owner/repo', self.directory, self.info)
        self.assertEqual(commands, self.commands)

    def test_legacy_publication_is_immutable_and_incomplete_assets_are_rejected(self):
        release.publish('owner/repo', self.directory, self.info)
        previous = release.saved_info(self.history[0])
        previous.pop('artifacts')
        self.history[0]['body'] = '<!-- stranded-ii-build: ' + json.dumps(previous) + ' -->'
        release.publish('owner/repo', self.directory, self.info)
        self.history[0]['assets'].pop()
        with self.assertRaisesRegex(ValueError, 'incomplete'):
            release.publish('owner/repo', self.directory, self.info)


if __name__ == '__main__':
    unittest.main()
