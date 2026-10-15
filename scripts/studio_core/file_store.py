"""Content-addressed local history and recoverable authoring-file transactions."""
from pathlib import Path
import json
import os

from .project import atomic, digest, encode, safe, MOD

FORMAT = 'stranded-studio/3'
FORMATS = ('stranded-studio/2', FORMAT)
DIRECTORIES = ('definitions', 'maps', 'resources', 'locales', 'scripts')
ROOT_FILES = ('project.json', '.gitignore', '.gitattributes')
JOURNAL = '.studio/file-transaction.json'
_hash_cache = {}


def read_json(raw, location):
    try:
        value = json.loads(raw)
        if not isinstance(value, dict):
            raise ValueError('Expected a JSON object')
        return value
    except (ValueError, UnicodeError) as exc:
        raise ValueError(f'{location}: {exc}') from exc


def scan(root):
    files = {}
    paths = [root/p for p in ROOT_FILES if (root/p).exists()]
    for directory in DIRECTORIES:
        path = safe(root, directory)
        if path.is_symlink():
            raise ValueError('Authoring files must not be symlinks: '+directory)
        if path.exists() and not path.is_dir():
            raise ValueError(f'Expected a directory: {directory}')
        if path.exists():
            for child in path.rglob('*'):
                if child.is_symlink():
                    raise ValueError('Authoring files must not be symlinks: '+child.relative_to(root).as_posix())
                if child.is_file(): paths.append(child)
    folded = set()
    for path in sorted(paths):
        name = path.relative_to(root).as_posix()
        if name.casefold() in folded:
            raise ValueError('Case-insensitive source path collision: '+name)
        folded.add(name.casefold())
        if any(p.is_symlink() for p in [path, *path.parents] if p != root and p.is_relative_to(root)):
            raise ValueError('Authoring files must not be symlinks: '+name)
        files[name] = safe(root, name).read_bytes()
    return files


def hashes(files):
    return {p: digest(raw) for p, raw in sorted(files.items())}


def version(files):
    return digest(encode(hashes(files)))


def base_hash(base):
    """Pin content, independent of machine paths, generated versions and settings."""
    base = Path(base).resolve()
    entries = {}
    def visit(directory, prefix):
        with os.scandir(directory) as children:
            for child in children:
                relative = prefix+'/'+child.name
                if child.is_dir(follow_symlinks=False):
                    visit(child.path, relative)
                elif child.is_file():
                    if child.name.lower() in ('settings.inf', 'modern.ini', 'fonts-report.json') or os.path.splitext(child.name)[1].lower() in ('.cfg','.sav','.log','.tmp'):
                        continue
                    # Ordinary children inherit the checked root. Resolve links
                    # explicitly; repeating realpath for every ancestor/file was
                    # the dominant cost of each editor request.
                    if child.is_symlink(): safe(base, relative)
                    stat = child.stat()
                    stamp = (stat.st_size, stat.st_mtime_ns, stat.st_ctime_ns, stat.st_ino)
                    cached = _hash_cache.get(child.path)
                    if not cached or cached[0] != stamp:
                        cached = (stamp, digest(Path(child.path).read_bytes()))
                        _hash_cache[child.path] = cached
                    entries[relative] = cached[1]
    for folder in (MOD+'sys', MOD+'gfx', MOD+'models', MOD+'sfx', MOD+'music', MOD+'maps', 'core/locales'):
        directory = safe(base, folder)
        if directory.is_dir():
            visit(directory, folder)
    # Keep Path's component ordering so existing v2 resource pins stay valid.
    return digest(encode(dict(sorted(entries.items(),key=lambda item:tuple(item[0].split('/'))))))


def git_attributes(original=b''):
    rules = ['*.json text eol=lf','*.inf text eol=lf','*.s2s text eol=lf','*.tsv text eol=lf','*.bin binary','*.b3d binary']
    missing = [line for line in rules if line not in original.decode('utf8').splitlines()]
    if not missing: return original
    return original+(b'\n' if original and not original.endswith(b'\n') else b'')+''.join(s+'\n' for s in missing).encode()


def put_blob(root, raw):
    key = digest(raw)
    path = safe(root, '.studio/objects/'+key)
    if not path.exists():
        atomic(path, raw)
    elif digest(path.read_bytes()) != key:
        raise ValueError('Damaged local history blob: '+key)
    return key


def blob(root, key):
    if not isinstance(key, str) or len(key) != 64 or any(c not in '0123456789abcdef' for c in key):
        raise ValueError('Invalid local history blob')
    raw = safe(root, '.studio/objects/'+key).read_bytes()
    if digest(raw) != key:
        raise ValueError('Damaged local history blob: '+key)
    return raw


def state(root, files):
    path = safe(root, '.studio/state.json')
    value = read_json(path.read_bytes(), str(path)) if path.exists() else {}
    head = version(files)
    if value.get('head') == head:
        return value
    return {'head': head, 'revision': value.get('revision', 0)+bool(value), 'undo': [], 'redo': []}


def prepare(root, before, after, next_state, *, remember=True, baseline=None):
    """Durably journal an edit; the desktop may publish source files later."""
    baseline = before if baseline is None else baseline
    if scan(root) != baseline:
        raise ValueError('Project content changed during editing; reload before saving')
    if before == after:
        return next_state
    def difference(left, right):
        return {path:{side:put_blob(root,files[path]) if path in files else None
                      for side,files in (('before',left),('after',right))}
                for path in sorted(left.keys() | right.keys()) if left.get(path)!=right.get(path)}
    changes = difference(before, after)
    next_state = dict(next_state)
    next_state.update(head=version(after), revision=next_state.get('revision', 0)+1)
    if remember:
        next_state['undo'] = (next_state.get('undo', [])+[changes])[-100:]
        next_state['redo'] = []
    # Prepare every payload before publishing the journal. Recovery checks every
    # source first, then only writes the transaction's addressed files.
    atomic(safe(root, JOURNAL), encode({'before': hashes(baseline), 'after': hashes(after),
                                      'changes': changes if baseline is before else difference(baseline,after), 'state': next_state}))
    return next_state


def commit(root, before, after, next_state, *, remember=True):
    prepare(root,before,after,next_state,remember=remember)
    recover(root)


def recover(root):
    path = safe(root, JOURNAL)
    if not path.exists():
        return
    journal = read_json(path.read_bytes(), JOURNAL)
    before, after = journal['before'], journal['after']
    for name in before.keys() | after.keys() | journal['changes'].keys():
        if name not in ROOT_FILES and not any(name.startswith(p+'/') for p in DIRECTORIES):
            raise ValueError('Recovery journal addresses a non-authoring file: '+name)
        safe(root,name)
    current = hashes(scan(root))
    for name in current.keys() | before.keys() | after.keys():
        if current.get(name) not in (before.get(name), after.get(name)):
            raise ValueError('Recovery conflict; external file preserved: '+name)
    payloads = {name: blob(root, change['after']) if change['after'] else None
                for name, change in journal['changes'].items()}
    # Publish the manifest last, so it never advertises a partially installed format.
    for name in sorted(payloads, key=lambda p: (p == 'project.json', p)):
        target = safe(root, name)
        if payloads[name] is None:
            target.unlink(missing_ok=True)
        else:
            atomic(target, payloads[name])
    atomic(safe(root, '.studio/state.json'), encode(journal['state']))
    path.unlink()


def history_step(root, files, current, redo=False):
    source, destination = ('redo', 'undo') if redo else ('undo', 'redo')
    if not current[source]:
        raise ValueError('Nothing to '+source+'; external changes invalidate local history')
    changes = current[source][-1]
    previous, desired = ('before', 'after') if redo else ('after', 'before')
    output = dict(files)
    for path, change in changes.items():
        actual = digest(files[path]) if path in files else None
        if actual != change[previous]:
            raise ValueError('Undo/redo conflicts with external edit: '+path)
        if change[desired] is None:
            output.pop(path, None)
        else:
            output[path] = blob(root, change[desired])
    next_state = {**current, source: current[source][:-1], destination: current[destination]+[changes]}
    commit(root, files, output, next_state, remember=False)
