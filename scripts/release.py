"""GitHub release allocation and resumable draft publication through GitHub CLI."""
from pathlib import Path
from editions import Edition
import hashlib
import json
import re
import subprocess
import zipfile
from versioning import TAG, git, make_info, parse_base, read_base, select_version, validate_info

MARKER = re.compile(r'<!-- stranded-ii-build: (.+?) -->')


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
            expected = {archive_name(info), archive_name(info) + '.sha256'}
            if not previous or not previous.get('zip_sha256') or not expected <= {a['name'] for a in existing['assets']}:
                raise ValueError('Existing published Release is incomplete; refusing to overwrite it')
    return info, bool(existing and not existing['draft'])


def archive_name(info):
    return f"StrandedII-{info['display_version']}-windows.zip"


def validate_package(directory, info):
    path = directory / archive_name(info)
    checksum = hashlib.sha256(path.read_bytes()).hexdigest()
    if path.with_suffix('.zip.sha256').read_text('utf8') != f'{checksum}  {path.name}\n':
        raise ValueError('Release ZIP checksum file disagrees with the archive')
    prefix = Edition(info['edition']).archive_root
    with zipfile.ZipFile(path) as archive:
        build = json.loads(archive.read(f'{prefix}/build-info.json'))
        metadata = validate_info(json.loads(archive.read(f'{prefix}/core/version.json')))
        label = archive.read(f'{prefix}/core/version.txt')
        exe = archive.read(f'{prefix}/StrandedII.exe')
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


def publish(repo, directory, info):
    info = validate_info(info)
    if info['channel'] == 'local':
        raise ValueError('Local/preview builds cannot be published')
    path, checksum = validate_package(directory, info)
    history = releases(repo)
    existing = next((r for r in history if r['tag_name'] == info['tag']), None)
    if existing:
        previous = saved_info(existing)
        if not previous or validate_info(previous) != info:
            raise ValueError('Existing Release belongs to another build; refusing to overwrite it')
        if not existing['draft']:
            # A published artifact is immutable, including on re-runs.
            print('Already published:', info['tag'])
            return
    target = remote_tag(repo, info['tag'])
    if target and target != info['commit']:
        raise ValueError('Existing tag points to a different commit')
    if not target:
        api(repo, 'git/refs', {'ref': 'refs/tags/' + info['tag'], 'sha': info['commit']})
    notes = directory / 'release-notes.txt'
    recorded = dict(info, zip_sha256=checksum)
    notes.write_text('<!-- stranded-ii-build: ' + json.dumps(recorded, separators=(',', ':')) + ' -->\n',
                     encoding='utf8', newline='\n')
    if existing:
        gh('release', 'edit', info['tag'], '--repo', repo, '--notes-file', str(notes))
    else:
        gh('release', 'create', info['tag'], '--repo', repo, '--verify-tag', '--draft',
           '--title', info['edition'].title() + ' ' + info['display_version'], '--generate-notes', '--notes-file', str(notes),
           '--prerelease=' + str(info['channel'] == 'dev').lower(), '--latest=false')
    # Only drafts may replace assets, so interruption halfway through upload is recoverable.
    gh('release', 'upload', info['tag'], str(path), str(path.with_suffix('.zip.sha256')),
       '--repo', repo, '--clobber')
    latest = info['edition'] == 'classic' and info['channel'] == 'main' and not any(
        not r['draft'] and not r['prerelease'] and TAG.fullmatch(r['tag_name']) and
        TAG.fullmatch(r['tag_name'])[4] is None and
        parse_base(r['tag_name'][1:]) > parse_base(info['version']) for r in history)
    gh('release', 'edit', info['tag'], '--repo', repo, '--draft=false',
       '--prerelease=' + str(info['channel'] == 'dev').lower(), '--latest=' + str(latest).lower())
    print('Published:', info['tag'])
