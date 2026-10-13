"""Transactional project commands shared by the CLI and desktop application.

project.json is the authoring source. Imported maps/assets are immutable,
content-addressed files. Export builds native resources without touching the base.
"""
from contextlib import contextmanager
from copy import deepcopy
from pathlib import Path, PurePosixPath
import base64
import hashlib
import json
import os
import re
import tempfile

from . import content
from .maps import EditableMap, new_map, number, CLASSES

ROOT = Path(__file__).resolve().parents[2]
MOD = 'mods/Stranded II/'
FORMAT = 'stranded-studio/1'
KEY = re.compile(r'[A-Za-z0-9][A-Za-z0-9_.-]{0,79}')
LOC = re.compile(r'\[\[loc:([A-Za-z0-9_.-]+)\]\]')


def digest(data):
    return hashlib.sha256(data).hexdigest()


def encode(value):
    return (json.dumps(value, ensure_ascii=False, indent=2, allow_nan=False) + '\n').encode('utf8')


def catalog_output(entries):
    def escape(text):
        return text.replace('\\', '\\\\').replace('\n', '\\n').replace('\r', '\\r').replace('\t', '\\t')
    return ''.join(key+'\t'+escape(value)+'\n' for key,value in sorted(entries.items()))


def atomic(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    fd, name = tempfile.mkstemp(prefix='.' + path.name + '-', dir=path.parent)
    try:
        with os.fdopen(fd, 'wb') as stream:
            stream.write(data)
            stream.flush()
            os.fsync(stream.fileno())
        os.replace(name, path)
    finally:
        if os.path.exists(name):
            os.unlink(name)


def safe(root, relative):
    if not isinstance(relative, str) or '\\' in relative or ':' in relative or '\0' in relative:
        raise ValueError(f'Invalid relative path: {relative!r}')
    parts = PurePosixPath(relative)
    if parts.is_absolute() or not parts.parts or any(p in ('.', '..') for p in relative.split('/')):
        raise ValueError(f'Path must remain inside its project: {relative}')
    path = root / relative
    if not path.resolve().is_relative_to(root.resolve()):
        raise ValueError(f'Path escapes through a symlink: {relative}')
    return path


def resource_path(relative):
    safe(Path('/studio'), relative)
    parts = PurePosixPath(relative).parts
    forbidden = {'saves','preserved','screenshots'}
    if (not relative.startswith(MOD) or any(p.lower() in forbidden for p in parts)
            or Path(relative).suffix.lower() in ('.cfg','.sav','.exe','.dll','.app','.bak','.tmp','.log')
            or parts[-1].lower() in ('settings.inf','modern.ini') or parts[-1].lower().startswith('screen_')):
        raise ValueError('Studio resources must be mod content; settings, saves and executables are excluded')
    return relative


@contextmanager
def locked(root):
    directory = safe(root, '.studio')
    directory.mkdir(exist_ok=True)
    with (directory / 'lock').open('a+b') as stream:
        if os.name == 'nt':
            import msvcrt
            stream.seek(0)
            if not stream.read(1):
                stream.write(b'0'); stream.flush()
            stream.seek(0)
            msvcrt.locking(stream.fileno(), msvcrt.LK_LOCK, 1)
        else:
            import fcntl
            fcntl.flock(stream, fcntl.LOCK_EX)
        try:
            yield
        finally:
            if os.name == 'nt':
                stream.seek(0); msvcrt.locking(stream.fileno(), msvcrt.LK_UNLCK, 1)
            else:
                fcntl.flock(stream, fcntl.LOCK_UN)


class Project:
    def __init__(self, directory):
        self.root = Path(directory).resolve()
        self.path = self.root / 'project.json'
        self.load()

    @classmethod
    def create(cls, directory, base, name='Extended project'):
        root, base = Path(directory).resolve(), Path(base).resolve()
        if root.exists() and any(root.iterdir()):
            raise ValueError('Choose a new or empty project directory')
        if root == base or root.is_relative_to(base) or base.is_relative_to(root):
            raise ValueError('The authoring project and base runtime must be separate directories')
        if not (base / MOD / 'sys').is_dir():
            raise ValueError('Base must be a prepared runtime containing mods/Stranded II/sys')
        root.mkdir(parents=True, exist_ok=True)
        try:
            base_path = os.path.relpath(base, root).replace(os.sep, '/')
        except ValueError:  # Different Windows drive letters.
            base_path = base.as_posix()
        data = {'format': FORMAT, 'edition': 'extended', 'name': name, 'revision': 0,
                'base': base_path,
                'files': {}, 'maps': {}, 'assets': {}, 'locales': {}}
        atomic(root / 'project.json', encode(data))
        atomic(root / '.gitignore', b'.studio/\nexport/\n')
        return cls(root)

    def load(self):
        self.data = json.loads(self.path.read_text('utf8'))
        if self.data.get('format') != FORMAT or self.data.get('edition') != 'extended':
            raise ValueError('Not a supported Extended Studio project')
        self.base = (self.root / self.data['base']).resolve()
        if not (self.base / MOD / 'sys').is_dir():
            raise ValueError(f'Base runtime is missing: {self.base}. Prepare the runtime or update project.json base.')
        for relative in self.data['files']:
            resource_path(relative)
        for relative, stored in self.data['assets'].items():
            resource_path(relative); safe(self.root, stored)
        for entry in self.data['maps'].values():
            safe(self.root, entry['source'])
        self._texts = None
        self._catalog = None
        self._blobs = {}

    def text_files(self):
        if self._texts is None:
            result = {}
            for path in sorted((self.base / MOD / 'sys').glob('*.inf')):
                relative = path.relative_to(self.base).as_posix()
                try:
                    result[relative] = safe(self.base, relative).read_text('utf8')
                except UnicodeDecodeError as exc:
                    raise ValueError('Base resources must be prepared UTF-8, not the official CP1252 originals') from exc
            result.update(self.data['files'])
            self._texts = result
        return self._texts

    def catalog(self):
        if self._catalog is None:
            self._catalog = [r for path, text in self.text_files().items() for r in content.records(path, text)]
        return self._catalog

    def find(self, kind, identifier):
        matches = [r for r in self.catalog() if r.kind == kind and str(r.identifier) == str(identifier)]
        if len(matches) != 1:
            raise ValueError(f'Expected one {kind}/{identifier}; found {len(matches)}')
        return matches[0]

    def put_text(self, path, text):
        resource_path(path)
        self.data['files'][path] = text
        self._texts = self._catalog = None

    def store_blob(self, data, extension):
        relative = 'sources/' + digest(data) + extension
        path = safe(self.root, relative)
        if not path.exists():
            self._blobs[relative] = data
        elif path.read_bytes() != data:
            raise ValueError('Imported source hash mismatch')
        return relative

    def map_bytes(self, name):
        if name not in self.data['maps']:
            raise ValueError(f'Unknown map: {name}')
        entry = self.data['maps'][name]
        data = self._blobs.get(entry['source'])
        if data is None:
            data = safe(self.root, entry['source']).read_bytes()
        if digest(data) != Path(entry['source']).stem:
            raise ValueError(f'Imported map was changed outside Studio: {name}')
        for op in entry['edits']:
            data = EditableMap(data).apply(op)
        return data

    def translations(self, language='ko'):
        from locales import catalog
        result = {}
        for code in dict.fromkeys(('en', language)):
            path = self.base / 'core/locales' / (code+'.tsv')
            if path.is_file():
                result.update(catalog(path))
            result.update(self.data['locales'].get(code, {}))
        return result

    def localizer(self, language='ko'):
        translations = self.translations(language)
        def localize(value):
            for _ in range(8):
                updated = LOC.sub(lambda m: translations.get(m[1], m[0]), value)
                if updated == value:
                    break
                value = updated
            return value
        return localize

    def public_catalog(self, language='ko'):
        localize = self.localizer(language)
        records = self.catalog()
        names = {(r.kind,str(r.identifier)):localize(r.properties.get('name',[str(r.identifier)])[0]) for r in records}
        result = []
        for record in records:
            value, props = record.public(localize), record.properties
            if record.kind == 'building':
                for field,kind in (('objectid','object'),('unitid','unit')):
                    if props.get(field):value['name']=names.get((kind,props[field][0]),value['name'])
            elif record.kind == 'recipe':
                value['name'] = localize(props['genname'][0]) if props.get('genname') else ' + '.join(
                    names.get(('item',entry.split(',')[0].strip()),entry) for entry in props.get('gen',[])) or value['name']
            result.append(value)
        return result

    def limits(self):
        limits = {'item': 255, 'object': 255, 'unit': 255, 'info': 100, 'building': 65535}
        # These limits come from the actual game configuration, not the map's type width.
        setup = self.text_files().get(MOD+'sys/game.inf', '')
        for key, value, _, _ in content.fields(setup):
            for kind in ('item','object','unit'):
                if key == 'limit_'+kind+'s':
                    limits[kind] = int(value)
        return limits

    def next_id(self, kind):
        used = {r.identifier for r in self.catalog() if r.kind == kind}
        for identifier in range(1, self.limits().get(kind, 65535)+1):
            if identifier not in used:
                return identifier
        raise ValueError(f'No free {kind} IDs within the engine limit')

    def perform(self, operation):
        if not isinstance(operation, dict) or not isinstance(operation.get('op'), str):
            raise ValueError('Each operation must be an object with a string op')
        op = operation.get('op')
        if op in ('content.set','content.clone','content.create'):
            kind = operation['kind']
            if kind not in content.PREFIXES:
                raise ValueError(f'Unknown content kind: {kind}')
            updates = operation.get('properties', {})
            if 'id' in updates or 'combi' in updates:
                raise ValueError('Use the command id field; structural markers are not editable properties')
            if op == 'content.set':
                record = self.find(kind, operation['id'])
                text = self.text_files()[record.path]
                self.put_text(record.path, text[:record.start]+content.edit(record.text, updates)+text[record.end:])
                return {'kind': kind, 'id': record.identifier}
            identifier = None if kind == 'recipe' else operation.get('id', self.next_id(kind))
            if kind != 'recipe':
                identifier = number(identifier, 'id', 1, self.limits()[kind], True)
                if any(r.kind == kind and r.identifier == identifier for r in self.catalog()):
                    raise ValueError(f'Duplicate {kind} definition: {identifier}')
            path = MOD+'sys/'+content.PREFIXES[kind]+'_studio.inf'
            previous = self.text_files().get(path, '')
            if op == 'content.clone':
                record = self.find(kind, operation['source'])
                changes = {'const': None, **updates}
                if kind != 'recipe':
                    changes['id'] = identifier
                block = content.edit(record.text, changes)
            else:
                block = content.new_record(kind, identifier, updates)
            self.put_text(path, previous.rstrip()+'\n\n'+block)
            if kind == 'recipe':
                identifier = content.records(path, self.text_files()[path])[-1].identifier
            return {'kind': kind, 'id': identifier}
        if op in ('map.create','map.import'):
            name = operation['name']
            if not isinstance(name, str) or not KEY.fullmatch(name):
                raise ValueError('Map name must use letters, numbers, _, - or . (1–80 characters)')
            if name in self.data['maps']:
                raise ValueError(f'Map already exists: {name}')
            data = new_map(operation.get('size', 32)) if op == 'map.create' else Path(operation['path']).resolve().read_bytes()
            if EditableMap(data).protected:
                raise ValueError('Password-protected maps must be unlocked in the original editor before importing')
            self.data['maps'][name] = {'source': self.store_blob(data, '.s2'), 'edits': []}
            return {'map': name}
        if op.startswith('entity.') or op in ('terrain.brush','map.script','entities.move'):
            name = operation['map']
            model = EditableMap(self.map_bytes(name))
            operation = deepcopy(operation)
            if op == 'entity.add':
                record = self.find(operation['kind'], operation['type'])
                props = record.properties
                values = dict(operation.get('values', {}))
                for field in ('health','health_max') if record.kind in ('object','unit') else ('health',) if record.kind == 'item' else ():
                    values.setdefault(field, float(props.get('health', ['100'])[0]))
                operation['values'] = values
                operation.setdefault('id', max([e['id'] for e in model.entities if e['kind'] == operation['kind']]
                                              + [99 if operation['kind'] == 'unit' else 0])+1)
            output = model.apply(operation)
            EditableMap(output)
            self.data['maps'][name]['edits'].append(operation)
            return {'map': name, 'id': operation.get('id')}
        if op == 'asset.import':
            relative = resource_path(operation['destination'])
            if not any(relative.startswith(MOD+p+'/') for p in ('gfx','sfx','music','models')):
                raise ValueError('Import assets under mods/Stranded II/gfx, sfx, music or models')
            source = Path(operation['path']).resolve()
            if not source.is_file():
                raise ValueError(f'Asset not found: {source}')
            self.data['assets'][relative] = self.store_blob(source.read_bytes(), source.suffix.lower())
            return {'asset': relative}
        if op == 'locale.set':
            code, key, value = operation['language'], operation['key'], operation['text']
            registered = [p.stem for p in (ROOT/'locales').glob('*.json')]
            if code not in registered or not re.fullmatch(r'[A-Za-z0-9_.-]+', key) or not isinstance(value, str):
                raise ValueError('Use a registered language, a fixed localization key and text')
            self.data['locales'].setdefault(code, {})[key] = value
            return {'key': key, 'language': code}
        raise ValueError(f'Unknown operation: {op}')

    def history(self):
        path = self.root / '.studio/history.json'
        if path.is_file():
            value = json.loads(path.read_text('utf8'))
            if value.get('head') == digest(encode(self.data)):
                return value
        return {'head': digest(encode(self.data)), 'undo': [], 'redo': []}

    def commit(self, before, history):
        if self.data == before:
            return
        self.data['revision'] = before['revision'] + 1
        history['head'] = digest(encode(self.data))
        for relative, data in self._blobs.items():
            atomic(safe(self.root, relative), data)
        # If interrupted between these writes, load ignores unmatched history and
        # retains the intact previous project. Sources are immutable and reusable.
        atomic(self.root/'.studio/history.json', encode(history))
        atomic(self.path, encode(self.data))

    def apply(self, operations, expected=None, dry_run=False):
        if not isinstance(operations, list) or not operations or len(operations) > 1000:
            raise ValueError('Provide 1–1000 operations in an array')
        with locked(self.root):
            self.load()
            if expected is not None and expected != self.data['revision']:
                raise ValueError(f'Revision conflict: expected {expected}, current {self.data["revision"]}; reload before editing')
            before, history = deepcopy(self.data), self.history()
            try:
                results = [self.perform(operation) for operation in operations]
                issues = self.validate()
                if not dry_run:
                    history['undo'] = (history['undo'] + [before])[-100:]
                    history['redo'] = []
                    self.commit(before, history)
                return {'revision': self.data['revision'], 'results': results, 'diagnostics': issues, 'dryRun': dry_run}
            finally:
                self.load()

    def undo(self, redo=False, expected=None):
        with locked(self.root):
            self.load()
            if expected is not None and expected != self.data['revision']:
                raise ValueError('Revision conflict; reload before undo/redo')
            before, history = deepcopy(self.data), self.history()
            source, target = ('redo','undo') if redo else ('undo','redo')
            if not history[source]:
                raise ValueError(f'Nothing to {source}')
            self.data = history[source].pop()
            history[target].append(before)
            self.commit(before, history)
            self.load()
            return {'revision': self.data['revision']}

    def validate(self):
        issues = []
        def error(code, message, location):
            issues.append({'severity': 'error', 'code': code, 'message': message, 'location': location})
        catalog = self.catalog()
        indices = {}
        for record in catalog:
            key = record.kind, str(record.identifier)
            if key in indices:
                error('duplicate-id', f'Duplicate {record.kind} ID {record.identifier}', record.path)
            indices[key] = record
        translations = self.translations('en')
        for record in catalog:
            props, location = record.properties, f'{record.kind}/{record.identifier}'
            changed = record.path in self.data['files']
            # Report the project's edits; legacy scripts and optional assets are
            # retained verbatim rather than reinterpreted by a second game engine.
            if not changed:
                continue
            if record.kind != 'recipe' and not 1 <= record.identifier <= self.limits()[record.kind]:
                error('id-limit', 'Definition exceeds the current game limit', location)
            for field in ('model','icon'):
                for value in props.get(field, []):
                    relative = MOD+value.replace('\\','/')
                    try:
                        exists = relative in self.data['assets'] or safe(self.base, relative).is_file()
                    except ValueError:
                        exists = False
                    if not exists:
                        error('missing-asset', f'{field}: {value}', location)
            for field, target in (('req','item'),('gen','item'),('objectid','object'),('unitid','unit'),('buildingsite','object'),('atobject','object')):
                if record.kind not in ('recipe','building'):
                    continue
                for value in props.get(field, []):
                    bits = value.split(',')
                    if (target, bits[0].strip()) not in indices:
                        error('missing-reference', f'{field} references unknown {target} {bits[0]}', location)
                    if len(bits)>1:
                        try:
                            if int(bits[1]) < 1: raise ValueError()
                        except ValueError:
                            error('invalid-count', f'{field} requires a positive quantity', location)
            if record.kind == 'recipe' and (len(props.get('req', [])) < 2 or not props.get('gen')):
                error('recipe-shape', 'A combination requires at least two req properties and one gen property', location)
            if record.kind == 'building' and not (props.get('objectid') or props.get('unitid')):
                error('building-target', 'A building must refer to an object or unit', location)
            for value in [v for values in props.values() for v in values]:
                for key in LOC.findall(value):
                    if key not in translations:
                        error('missing-translation', f'English fallback is missing: {key}', location)
        for name in self.data['maps']:
            try:
                model = EditableMap(self.map_bytes(name))
                seen = set()
                for entity in model.entities:
                    identity = entity['kind'], entity['id']
                    if identity in seen:
                        error('duplicate-entity', f'Duplicate entity: {identity}', name)
                    seen.add(identity)
                    if (entity['kind'], str(entity['type'])) not in indices:
                        error('missing-definition', f'{entity["kind"]} type {entity["type"]} does not exist', name)
                if not any(e['kind']=='info' and e['type']==1 for e in model.entities):
                    error('player-start', 'A playable map needs a player start (info type 1)', name)
            except (ValueError, KeyError) as exc:
                error('invalid-map', str(exc), name)
        return issues

    def thumbnail_version(self):
        # Map edits and their revision counter do not change content images.
        return digest(encode({'base': str(self.base), 'files': self.data['files'], 'assets': self.data['assets']}))

    def snapshot(self, map_name=None):
        if map_name and map_name not in self.data['maps']:
            map_name = next(iter(self.data['maps']), None)
        history = self.history()
        base_maps = sorted(p.relative_to(self.base).as_posix() for p in (self.base/MOD/'maps').rglob('*.s2')
                           if 'editor' not in p.relative_to(self.base/MOD/'maps').parts)
        return {'name': self.data['name'], 'project': str(self.root), 'base': str(self.base),
                'revision': self.data['revision'], 'thumbnailVersion': self.thumbnail_version(),
                'maps': list(self.data['maps']), 'activeMap': map_name,
                'catalog': self.public_catalog(), 'baseMaps': base_maps,
                'assets': list(self.data['assets']), 'canUndo': bool(history['undo']), 'canRedo': bool(history['redo']),
                'map': EditableMap(self.map_bytes(map_name)).summary() if map_name else None,
                'diagnostics': self.validate()}

    def export(self, destination):
        issues = self.validate()
        if any(i['severity'] == 'error' for i in issues):
            raise ValueError('Export blocked by validation errors: '+json.dumps(issues, ensure_ascii=False))
        destination = Path(destination).resolve()
        if destination == self.root or destination == self.base or destination.is_relative_to(self.base):
            raise ValueError('Export must not overwrite the project or its base runtime')
        if destination.exists() and any(destination.iterdir()):
            raise ValueError('Use a new or empty export directory; existing resources are preserved')
        from .fonts import project_fonts
        fonts = project_fonts(self)
        destination.mkdir(parents=True, exist_ok=True)
        files = {}
        for relative, text in self.data['files'].items():
            files[relative] = text.encode('utf8')
        for relative, source in self.data['assets'].items():
            files[relative] = safe(self.root, source).read_bytes()
        for name in self.data['maps']:
            files[MOD+'maps/'+name+'.s2'] = self.map_bytes(name)
        for relative, data in files.items():
            atomic(safe(destination/'overrides', resource_path(relative)), data)
        for language, entries in self.data['locales'].items():
            atomic(safe(destination/'locales', language+'.tsv'), catalog_output(entries).encode('utf8'))
        for relative, data in fonts.items():
            atomic(safe(destination/'fonts', relative),data)
        report = {'format': FORMAT, 'edition': 'extended', 'revision': self.data['revision'],
                  'files': {path: digest(data) for path,data in sorted(files.items())},
                  'locales': {code+'.tsv':digest(catalog_output(entries).encode('utf8')) for code,entries in self.data['locales'].items()},
                  'fonts': {path:digest(data) for path,data in sorted(fonts.items())}}
        atomic(destination/'manifest.json', encode(report))
        return {'directory': str(destination), **report}

    def preview(self, map_name, engine_base=None, since=None):
        """Only explicit project resources cross into the isolated WASM filesystem."""
        from .preview import preview
        with locked(self.root):
            self.load()
            if engine_base and self.base != Path(engine_base).resolve():
                raise ValueError('Blitz preview requires the project base to be build/studio/runtime, matching its bundled assets')
            issues = self.validate()
            if any(i['severity'] == 'error' for i in issues):
                raise ValueError('Preview blocked by validation errors: '+json.dumps(issues, ensure_ascii=False))
            return preview(self, map_name, since)

    def preview_resources(self):
        files = {path: base64.b64encode(text.encode('utf8')).decode('ascii') for path,text in self.data['files'].items()}
        files.update({path: base64.b64encode(safe(self.root, source).read_bytes()).decode('ascii')
                      for path,source in self.data['assets'].items()})
        from locales import catalog
        for language, entries in self.data['locales'].items():
            path = self.base/'core/locales'/f'{language}.tsv'
            merged = catalog(path) if path.is_file() else {}
            merged.update(entries)
            files[f'core/locales/{language}.tsv'] = base64.b64encode(catalog_output(merged).encode('utf8')).decode('ascii')
        from .fonts import project_fonts
        for relative,data in project_fonts(self).items():
            files['core/locales/'+relative] = base64.b64encode(data).decode('ascii')
        return files
