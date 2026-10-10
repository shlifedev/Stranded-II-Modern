"""Product versions, separate from the original game's map-format version."""
from pathlib import Path
import json
import re
import subprocess

NUMBER = r'(?:0|[1-9][0-9]*)'
CORE = rf'({NUMBER})\.({NUMBER})\.({NUMBER})'
TAG = re.compile(rf'v{CORE}(?:-dev\.([1-9][0-9]*))?')


def parse_base(value):
    if not isinstance(value, str) or not re.fullmatch(CORE, value):
        raise ValueError('Version must be MAJOR.MINOR.PATCH without leading zeros')
    return tuple(map(int, value.split('.')))


def read_base(text):
    config = json.loads(text)
    if not isinstance(config, dict) or 'version' not in config:
        raise ValueError('Version configuration requires a version field')
    value = config['version']
    parse_base(value)
    return value


def git(root, *args):
    result = subprocess.run(['git', '-C', str(root), *args], capture_output=True,
                            text=True, encoding='utf8')
    if result.returncode:
        raise ValueError(f'Git {args[0]} failed: {result.stderr.strip()}')
    return result.stdout.strip()


def make_info(version, channel, commit='', base_version=None, base_commit=''):
    if channel not in ('main', 'dev', 'local'):
        raise ValueError('Unknown release channel')
    if channel == 'dev':
        match = TAG.fullmatch('v' + version)
        if not match or match[4] is None:
            raise ValueError('Development version must end in -dev.N (N starts at 1)')
        display = version
    else:
        parse_base(version)
        display = version + '-' + channel
    if commit and not re.fullmatch(r'[0-9a-f]{40}', commit):
        raise ValueError('Invalid source commit SHA')
    if channel != 'local' and not commit:
        raise ValueError('A release requires a source commit')
    base_version = base_version or version.split('-')[0]
    parse_base(base_version)
    if base_commit and not re.fullmatch(r'[0-9a-f]{40}', base_commit):
        raise ValueError('Invalid version configuration commit SHA')
    return dict(version=version, channel=channel, display_version=display,
                tag='' if channel == 'local' else 'v' + version, commit=commit,
                base_version=base_version, base_commit=base_commit)


def validate_info(info):
    try:
        expected = make_info(info['version'], info['channel'], info['commit'],
                             info['base_version'], info['base_commit'])
    except (KeyError, TypeError) as exc:
        raise ValueError('Missing or invalid product version metadata') from exc
    if any(info.get(key) != value for key, value in expected.items()):
        raise ValueError('Inconsistent product version metadata')
    return expected


def local_info(root):
    base = read_base((root / 'config/version.json').read_text('utf8'))
    try:
        commit = git(root, 'rev-parse', 'HEAD')
    except ValueError:
        commit = ''
    return make_info(base, 'local', commit, base, commit)


def select_version(base, channel, commit, tags, published):
    """Tags reserve numbers; only published main releases advance the dev target."""
    major, minor, floor = parse_base(base)
    records = [(name, TAG.fullmatch(name), sha) for name, sha in tags.items()]
    records = [(name, match, sha) for name, match, sha in records if match]
    reused = [(name, match) for name, match, sha in records
              if sha == commit and ('dev' if match[4] else 'main') == channel]
    if reused:
        name, _ = max(reused, key=lambda row: tuple(int(x or 0) for x in row[1].groups()))
        return name[1:]
    series = [(name, match) for name, match, _ in records
              if (int(match[1]), int(match[2])) == (major, minor)]
    if channel == 'main':
        patch = max([floor - 1] + [int(m[3]) for _, m in series if m[4] is None]) + 1
        return f'{major}.{minor}.{patch}'
    if channel != 'dev':
        raise ValueError('Only main and dev allocate release numbers')
    patch = max([floor - 1] + [int(m[3]) for name, m in series
                              if m[4] is None and name in published]) + 1
    sequence = max([0] + [int(m[4]) for _, m in series
                          if m[4] is not None and int(m[3]) == patch]) + 1
    return f'{major}.{minor}.{patch}-dev.{sequence}'


def runtime_info(runtime):
    info = validate_info(json.loads((runtime / 'core/version.json').read_text('utf8')))
    if (runtime / 'core/version.txt').read_bytes() != (info['display_version'] + '\n').encode('ascii'):
        raise ValueError('Runtime version label does not match build metadata; rebuild first')
    return info
