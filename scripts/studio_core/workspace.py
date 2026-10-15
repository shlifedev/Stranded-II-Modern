"""Portable workspaces, field-aware drafts and navigable authoring references."""
import json
from pathlib import Path

from . import scripts
from .maps import EditableMap
from .project import Project, MOD, FORMAT, atomic, encode, safe, locked, digest


def ignore_file(root):
    path = safe(root, '.gitignore')
    if path.is_symlink():
        raise ValueError('Use a regular .gitignore file')
    data = path.read_bytes() if path.exists() else b''
    lines = data.decode('utf8').splitlines()
    missing = [line for line in ('/.studio/', '/export/') if line not in lines]
    if not missing:
        return data
    return data + (b'\n' if data and not data.endswith(b'\n') else b'') + ''.join(s+'\n' for s in missing).encode()


def connect(directory, base):
    root, base = Path(directory).resolve(), Path(base).resolve()
    data = json.loads(safe(root, 'project.json').read_text('utf8'))
    if data.get('format') not in (FORMAT, 'stranded-studio/2', 'stranded-studio/3') or data.get('edition') != 'extended':
        raise ValueError('Not a supported Extended Studio project')
    if root == base or root.is_relative_to(base) or base.is_relative_to(root):
        raise ValueError('The authoring project and base runtime must be separate directories')
    if not (base/MOD/'sys').is_dir():
        raise ValueError('Base must be a prepared runtime containing mods/Stranded II/sys')
    if data['format'] in ('stranded-studio/2','stranded-studio/3'):
        from .file_store import base_hash
        if data['base']['contentHash'] != base_hash(base):
            raise ValueError('Base resource version mismatch; connect the same content version')
    ignore = ignore_file(root)
    with locked(root):
        local = safe(root, '.studio/local.json')
        settings = json.loads(local.read_text('utf8')) if local.is_file() else {}
        if not isinstance(settings, dict):
            raise ValueError('Local resource settings must be a JSON object')
        settings['base'] = str(base)
        atomic(root/'.gitignore', ignore)
        atomic(local, encode(settings))
    return {'project': str(root), 'base': str(base)}


class DraftConflict(ValueError):
    def __init__(self, draft, conflicts):
        super().__init__('초안과 저장된 내용이 충돌합니다. 비교 후 다시 편집하세요.')
        self.details = {'key': draft.get('key'), 'target': draft.get('target'), 'conflicts': conflicts}


def apply_draft(project, draft):
    target, section = draft['target'], draft['section']
    scope = target['scope']
    try:
        if scope == 'file':
            path = scripts.path_name(target['path'])
            current = project.script_files.get(path)
            if current is None:
                raise ValueError('Script file was removed')
            if draft.get('hash') != digest(current.encode('utf8')) and current != draft['text']:
                raise DraftConflict(draft, [{'field':'text','base':draft.get('base'),'current':current,'draft':draft['text']}])
            return scripts.write(project, path, draft['text'], digest(current.encode('utf8')))
        # Resolve the target even for an empty draft, so removed objects cannot be recreated.
        if scope == 'definition':
            current = project.find(target['kind'], target['id']).properties
        elif scope in ('map', 'entity'):
            model = EditableMap(project.map_bytes(target['map']))
            current = model.summary() if scope == 'map' else model.entity(target['kind'], target['id'])[2]
        else:
            raise ValueError('Unknown draft target')
        if section == 'script':
            if scripts.binding(project, target):
                raise ValueError('Script binding changed; edit the connected file')
            actual = scripts.target_text(project, target)
            updates = {'text': draft['text']}; current = {'text': actual}
            original = {'text': draft['base']} if 'base' in draft else None
        else:
            if (scope, section) not in (('definition','properties'), ('entity','values')):
                raise ValueError('Unknown draft section')
            updates = draft['changes']; original = draft.get('base')
            if 'script' in updates:
                raise ValueError('Use a script draft to edit scripts')
        if original is None:  # Preserve older persisted drafts without inventing a merge base.
            raise ValueError('이전 형식의 초안입니다. 내용을 보관한 뒤 초안을 취소하고 다시 편집하세요.')
        conflicts = [{'field': key, 'base': original.get(key), 'current': current.get(key), 'draft': value}
                     for key, value in updates.items()
                     if current.get(key) != original.get(key) and current.get(key) != value]
        if conflicts:
            raise DraftConflict(draft, conflicts)
        values = {key: value for key, value in updates.items() if current.get(key) != value}
        if not values:
            return {'unchanged': True}
        address = {k:v for k,v in target.items() if k != 'scope'}
        if section == 'script':
            operation = ({'op':'content.set', **address, 'properties':{'script':values['text']}} if scope == 'definition'
                         else {'op':scope+'.script', **address, 'text':values['text']})
        else:
            operation = {'op':'content.set' if scope == 'definition' else 'entity.set', **address, section:values}
        return project.perform(operation)
    except DraftConflict:
        raise
    except (ValueError, KeyError) as exc:
        raise DraftConflict(draft, [{'field':'target','message':str(exc)}]) from exc


REFERENCE_FIELDS = {'req':'item', 'gen':'item', 'objectid':'object', 'unitid':'unit', 'buildingsite':'object', 'atobject':'object'}


def references(project, target):
    results = []
    if target['scope'] == 'definition':
        kind, identifier = target['kind'], str(target['id'])
        project.find(kind, target['id'])
        for name in project.data['maps']:
            for entity in EditableMap(project.map_bytes(name)).entities:
                if entity['kind'] == kind and str(entity['type']) == identifier:
                    results.append({'label':f'{name} / {kind} #{entity["id"]}',
                                    'target':{'scope':'entity','map':name,'kind':kind,'id':entity['id']}})
        for record in project.catalog():
            if record.kind not in ('recipe','building'):
                continue
            for field, ref_kind in REFERENCE_FIELDS.items():
                if ref_kind == kind and any(v.split(',')[0].strip() == identifier for v in record.properties.get(field, [])):
                    results.append({'label':f'{record.kind} {record.identifier} · {field}', 'field':field,
                                    'target':{'scope':'definition','kind':record.kind,'id':record.identifier}})
    elif target['scope'] == 'file':
        path = scripts.path_name(target['path'])
        for entry in scripts.workspace(project)['files']:
            for ref in entry['references']:
                if ref['path'] == path:
                    results.append({'label':f'{entry["path"]}:{ref["line"]}', 'line':ref['line'],
                                    'target':{'scope':'file','path':entry['path']}})
        for key, bound in scripts.source_bindings(project).items():
            if bound == path:
                owner = json.loads(key)
                results.append({'label':'연결: '+scripts.target_key(owner), 'target':owner, 'field':'script'})
    else:
        scripts.target_text(project, target)
    if target['scope'] != 'file':
        path = scripts.source_bindings(project).get(scripts.target_key(target))
        if path:
            results.append({'label':'연결된 스크립트: '+path, 'target':{'scope':'file','path':path}})
    return {'references': results, 'projectHash':project.project_hash()}


def assets(project):
    paths = set(project.data['assets'])
    for folder in ('gfx', 'models'):
        for path in (project.base/MOD/folder).rglob('*'):
            if path.is_file() and path.resolve().is_relative_to(project.base):
                paths.add(path.relative_to(project.base).as_posix())
    return {'paths': sorted(p[len(MOD):] for p in paths if p.startswith(MOD)), 'projectHash':project.project_hash()}
