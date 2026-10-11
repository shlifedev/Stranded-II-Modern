"""GitHub release allocation and resumable draft publication through GitHub CLI."""
from pathlib import Path, PurePosixPath
from editions import Edition
import hashlib
import json
import re
import plistlib
import stat
import struct
import subprocess
import zipfile
from versioning import TAG, git, make_info, parse_base, read_base, select_version, validate_info

MARKER = re.compile(r'<!-- stranded-ii-build: (.+?) -->')


def recorded_assets(previous, info):
    """Keep historical publications immutable, including retired artifacts."""
    assets = previous.get('artifacts')
    if assets is None:
        assets = {archive_name(info): previous.get('zip_sha256')}
    pattern = re.compile(rf"StrandedII-{re.escape(info['display_version'])}-[a-z0-9-]+\.zip")
    if (not isinstance(assets, dict) or archive_name(info) not in assets or
            any(not pattern.fullmatch(name) for name in assets) or
            any(not isinstance(value, str) or not re.fullmatch(r'[0-9a-f]{64}', value) for value in assets.values())):
        raise ValueError('Invalid recorded release artifacts')
    return assets


def check_published(existing, info):
    previous = saved_info(existing)
    if not previous or validate_info(previous) != info:
        raise ValueError('Existing Release belongs to another build; refusing to overwrite it')
    assets = recorded_assets(previous, info)
    expected = set(assets) | {name + '.sha256' for name in assets}
    if not expected <= {a['name'] for a in existing['assets']}:
        raise ValueError('Existing published Release is incomplete; refusing to overwrite it')


def gh(*args, payload=None):
    result = subprocess.run(['gh', *args], input=None if payload is None else json.dumps(payload),
                            capture_output=True, text=True, encoding='utf8')
    if result.returncode:
        raise ValueError(f'GitHub CLI failed: {result.stderr.strip()}')
    return result.stdout


def api(repo, path, payload=None):
    args = ['api', f'repos/{repo}/{path}']
    if payload is not None:
        args += ['--method', 'POST', '--input', '-']
    return json.loads(gh(*args, payload=payload))


def releases(repo):
    pages = json.loads(gh('api', f'repos/{repo}/releases?per_page=100', '--paginate', '--slurp'))
    return [release for page in pages for release in page]


def saved_info(release):
    match = MARKER.search(release.get('body') or '')
    return json.loads(match[1]) if match else None


def allocation(root, repo, channel, commit, edition='classic'):
    profile = Edition(edition)
    pattern = re.compile(('extended-' if edition == 'extended' else '') + TAG.pattern)
    # Freeze the exact main configuration read by dev, rather than requiring a merge.
    base_commit = commit if channel == 'main' else git(root, 'rev-parse', 'origin/main')
    try:
        base = read_base(git(root, 'show', f'{base_commit}:{profile.version_config}'))
    except (ValueError, KeyError) as exc:
        raise ValueError(f'Add a valid {profile.version_config} to main before releasing dev') from exc
    tags = {tag: git(root, 'rev-parse', tag + '^{}')
            for tag in git(root, 'tag', '--list').splitlines() if pattern.fullmatch(tag)}
    history = releases(repo)
    # Our drafts always target an exact SHA; include a draft even if its tag has not appeared yet.
    for entry in history:
        if pattern.fullmatch(entry['tag_name']):
            previous = saved_info(entry)
            if previous:
                info = validate_info(previous)
                if info['tag'] != entry['tag_name']:
                    raise ValueError('Release metadata disagrees with its tag')
                if entry['tag_name'] in tags and tags[entry['tag_name']] != info['commit']:
                    raise ValueError('Release tag no longer points to its recorded source commit')
                tags.setdefault(entry['tag_name'], info['commit'])
    published = {r['tag_name'] for r in history if not r['draft'] and not r['prerelease']}
    version = select_version(base, channel, commit, tags, published, edition)
    info = make_info(version, channel, commit, base, base_commit, edition)
    existing = next((r for r in history if r['tag_name'] == info['tag']), None)
    if existing:
        previous = saved_info(existing)
        if previous:
            info = validate_info(previous)
        if info['commit'] != commit or info['channel'] != channel or info['edition'] != edition:
            raise ValueError('Cannot reuse a release for a different commit/channel')
        if not existing['draft']:
            check_published(existing, info)
    return info, bool(existing and not existing['draft'])


def archive_name(info):
    return f"StrandedII-{info['display_version']}-windows.zip"


def macos_archive_name(info):
    if info.get('edition', 'classic') != 'classic':
        raise ValueError('Native Mac releases only support Stranded II.')
    return f"StrandedII-{info['display_version']}-macos-arm64.zip"


def validate_macos_package(directory, info):
    """Validate the Mac artifact on the Windows release coordinator without extracting it."""
    path = directory / macos_archive_name(info)
    checksum = hashlib.sha256(path.read_bytes()).hexdigest()
    if path.with_suffix('.zip.sha256').read_text('utf8') != f'{checksum}  {path.name}\n':
        raise ValueError('Mac ZIP checksum file disagrees with the archive')
    prefix = 'Stranded II.app/'
    with zipfile.ZipFile(path) as archive:
        records, seen = {}, set()
        for entry in archive.infolist():
            name = entry.filename
            relative = PurePosixPath(name)
            key = name.rstrip('/').casefold()
            if (key in seen or relative.is_absolute() or '..' in relative.parts or
                    '\\' in name or ':' in name or name.startswith('/') or
                    name.rstrip('/') != relative.as_posix()):
                raise ValueError('Unsafe or duplicate Mac ZIP member')
            seen.add(key)
            mode = entry.external_attr >> 16
            directory_entry = entry.is_dir()
            if not (stat.S_ISDIR(mode) if directory_entry else stat.S_ISREG(mode)):
                raise ValueError('Mac ZIP member is not a regular file/directory')
            if name == prefix:
                continue
            if not name.startswith(prefix):
                if name not in ('README-en.txt', 'README-ko.txt', 'verification-summary.json') or directory_entry:
                    raise ValueError('Unexpected file outside the Mac app')
                continue
            record = {'mode': stat.S_IMODE(mode), 'type': 'directory' if directory_entry else 'file'}
            if not directory_entry:
                record['sha256'] = hashlib.sha256(archive.read(entry)).hexdigest()
            records[name[len(prefix):].rstrip('/')] = record
        summary = json.loads(archive.read('verification-summary.json'))
        app_hash = hashlib.sha256(json.dumps(records, sort_keys=True).encode('utf8')).hexdigest()
        core = prefix + 'Contents/Resources/game/core/'
        version = validate_info(json.loads(archive.read(core + 'version.json')))
        label = archive.read(core + 'version.txt')
        plist = plistlib.loads(archive.read(prefix + 'Contents/Info.plist'))
        executable = archive.read(prefix + 'Contents/MacOS/StrandedII')
        numeric_version = info['version'].split('-')[0]
        if (validate_info(summary.get('version_info', {})) != info or version != info or
                summary.get('version') != info['version'] or summary.get('source_commit') != info['commit'] or
                label != (info['display_version'] + '\n').encode('ascii') or
                summary.get('app_sha256') != app_hash or summary.get('platform') != 'macos-arm64' or
                summary.get('personal_data_included') is not False or
                plist.get('CFBundleExecutable') != 'StrandedII' or
                plist.get('CFBundleVersion') != numeric_version or
                plist.get('CFBundleShortVersionString') != numeric_version or
                plist.get('LSMinimumSystemVersion') != summary.get('minimum_macos')):
            raise ValueError('Mac ZIP version/commit/app does not match the selected build')
        checks = summary.get('checks', {})
        if (summary.get('suite') not in ('core', 'full') or
                checks.get('app_structure_and_signature') != 'pass' or
                checks.get('abi') != {'bytes_equal_to_blitz_1_118': 613, 'type_reuse': 'pass'}):
            raise ValueError('Mac ZIP is missing successful native checks')
        if (len(executable) < 32 or struct.unpack_from('<II', executable) != (0xfeedfacf, 0x100000c) or
                records['Contents/MacOS/StrandedII']['mode'] & 0o111 != 0o111 or
                'Contents/_CodeSignature/CodeResources' not in records):
            raise ValueError('Mac ZIP must contain a signed executable ARM64 app')
    return path, checksum


def validate_package(directory, info):
    path = directory / archive_name(info)
    checksum = hashlib.sha256(path.read_bytes()).hexdigest()
    if path.with_suffix('.zip.sha256').read_text('utf8') != f'{checksum}  {path.name}\n':
        raise ValueError('Release ZIP checksum file disagrees with the archive')
    profile = Edition(info['edition'])
    prefix = profile.archive_root
    with zipfile.ZipFile(path) as archive:
        if len(archive.namelist()) != len(set(archive.namelist())):
            raise ValueError('Duplicate release ZIP members')
        build = json.loads(archive.read(f'{prefix}/build-info.json'))
        metadata = validate_info(json.loads(archive.read(f'{prefix}/core/version.json')))
        label = archive.read(f'{prefix}/core/version.txt')
        exe = archive.read(f'{prefix}/{profile.executable}')
        if (metadata != info or any(build.get(k, 'classic' if k == 'edition' else None) != v for k, v in info.items()) or
                label != (info['display_version'] + '\n').encode('ascii') or
                build['exe_sha256'] != hashlib.sha256(exe).hexdigest()):
            raise ValueError('Release ZIP version/commit/executable does not match the selected build')
    return path, checksum


def remote_tag(repo, tag):
    refs = json.loads(gh('api', f'repos/{repo}/git/matching-refs/tags/{tag}'))
    ref = next((r for r in refs if r['ref'] == 'refs/tags/' + tag), None)
    if not ref:
        return None
    obj = ref['object']
    while obj['type'] == 'tag':
        obj = api(repo, 'git/tags/' + obj['sha'])['object']
    if obj['type'] != 'commit':
        raise ValueError('Release tag must reference a commit')
    return obj['sha']


def publish(repo, directory, info, include_macos=False):
    info = validate_info(info)
    if info['channel'] == 'local':
        raise ValueError('Local/preview builds cannot be published')
    if include_macos:
        macos_archive_name(info)
    history = releases(repo)
    existing = next((r for r in history if r['tag_name'] == info['tag']), None)
    if existing:
        previous = saved_info(existing)
        if not previous or validate_info(previous) != info:
            raise ValueError('Existing Release belongs to another build; refusing to overwrite it')
        if not existing['draft']:
            # A published artifact is immutable, including on re-runs.
            check_published(existing, info)
            if include_macos and macos_archive_name(info) not in recorded_assets(previous, info):
                raise ValueError('Published release has no Mac artifact; publish a new version instead')
            print('Already published:', info['tag'])
            return
    # Drafts freeze both the asset set and hashes. Do not replace a different
    # build or remove historical artifacts when resuming a publication.
    frozen = recorded_assets(previous, info) if existing and (previous.get('artifacts') or previous.get('zip_sha256')) else None
    packages = [validate_package(directory, info)]
    if include_macos:
        packages.append(validate_macos_package(directory, info))
    artifacts = {path.name: checksum for path, checksum in packages}
    if frozen is not None and artifacts != frozen:
        raise ValueError('Draft artifacts changed; recover the original build artifacts before retrying')
    target = remote_tag(repo, info['tag'])
    if target and target != info['commit']:
        raise ValueError('Existing tag points to a different commit')
    if not target:
        api(repo, 'git/refs', {'ref': 'refs/tags/' + info['tag'], 'sha': info['commit']})
    notes = directory / 'release-notes.txt'
    recorded = dict(info, zip_sha256=artifacts[archive_name(info)], artifacts=artifacts)
    mac_note = ('\nmacOS: Apple Silicon용 네이티브 실험판입니다. 로컬 서명이며 공증하지 않았습니다. '
                'ZIP의 README-ko.txt / README-en.txt에서 실행 방법과 검증 범위를 확인하세요.\n'
                if include_macos else '')
    notes.write_text('<!-- stranded-ii-build: ' + json.dumps(recorded, separators=(',', ':')) + ' -->\n' + mac_note,
                     encoding='utf8', newline='\n')
    if existing:
        gh('release', 'edit', info['tag'], '--repo', repo, '--notes-file', str(notes))
    else:
        gh('release', 'create', info['tag'], '--repo', repo, '--verify-tag', '--draft',
           '--title', info['display_version'], '--generate-notes', '--notes-file', str(notes),
           '--prerelease=' + str(info['channel'] == 'dev').lower(), '--latest=false')
    # Only drafts may replace assets, so interruption halfway through upload is recoverable.
    uploads = [str(p) for path, _ in packages for p in (path, path.with_suffix('.zip.sha256'))]
    gh('release', 'upload', info['tag'], *uploads, '--repo', repo, '--clobber')
    latest = info['edition'] == 'classic' and info['channel'] == 'main' and not any(
        not r['draft'] and not r['prerelease'] and TAG.fullmatch(r['tag_name']) and
        TAG.fullmatch(r['tag_name'])[4] is None and
        parse_base(r['tag_name'][1:]) > parse_base(info['version']) for r in history)
    gh('release', 'edit', info['tag'], '--repo', repo, '--draft=false',
       '--prerelease=' + str(info['channel'] == 'dev').lower(), '--latest=' + str(latest).lower())
    print('Published:', info['tag'])
