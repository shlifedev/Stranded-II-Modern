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
        path = self.directory / release.archive_name(info)
        exe = b'MZexecutable'
        with zipfile.ZipFile(path, 'w') as archive:
            archive.writestr('StrandedII/core/version.json', json.dumps(info))
            archive.writestr('StrandedII/core/version.txt', info['display_version'] + '\n')
            archive.writestr('StrandedII/StrandedII.exe', exe)
            archive.writestr('StrandedII/build-info.json', json.dumps(dict(info, exe_sha256=hashlib.sha256(exe).hexdigest())))
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
            entry['assets'] = [{'name': Path(p).name} for p in args[3:5]]
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


if __name__ == '__main__':
    unittest.main()
