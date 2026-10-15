"""Explicit, lossless conversion to per-map state with guarded restoration."""
from copy import deepcopy
from datetime import datetime, timezone
from pathlib import Path
import re
import uuid

from . import file_store as store, definition_files, map_files, scripts
from .file_project import FileProject
from .project import Project, MOD, atomic, encode, safe, locked, digest


def converted(old):
    if isinstance(old,FileProject):
        new = object.__new__(FileProject)
        new.__dict__ = deepcopy(old.__dict__)
        new.manifest['format'] = store.FORMAT
        new.files['project.json'] = encode(new.manifest)
        new._state = {'revision':old.data['revision'],'undo':[],'redo':[]}
        for name in old.data['maps']:
            map_files.split(new,name,old.map_bytes(name))
        new._sync_scripts();new._rebuild()
        return verified(old,new)
    new = object.__new__(FileProject)
    new.root, new.path, new.base = old.root, old.path, old.base
    new.base_actual_hash = store.base_hash(old.base)
    new.manifest = {'format':store.FORMAT,'edition':'extended','name':old.data['name'],
                    'base':{'contentHash':new.base_actual_hash}}
    existing = store.scan(old.root)
    if any(p.startswith(('definitions/','maps/','resources/','locales/')) for p in existing):
        raise ValueError('v2 source folders already contain files; preserve them and choose a clean project layout')
    new.files = {**existing,'project.json':encode(new.manifest)}
    new.base_texts = {p.relative_to(old.base).as_posix():p.read_text('utf8') for p in sorted((old.base/MOD/'sys').glob('*.inf'))}
    new._state = {'revision':old.data['revision'],'undo':[],'redo':[]}
    new.script_files = deepcopy(old.script_files)
    new._script_writes = set()
    new._blobs = {}
    new._rebuild()
    for path,text in old.data['files'].items():
        definition_files.import_file(new,path,text)
    for name in old.data['maps']:
        map_files.split(new,name,old.map_bytes(name))
    for destination, source in old.data['assets'].items():
        new.files['resources/'+destination[len(MOD):]] = safe(old.root,source).read_bytes()
    from .project import catalog_output
    for code, entries in old.data['locales'].items():
        new.files['locales/'+code+'.tsv'] = catalog_output(entries).encode('utf8')
    new._sync_scripts();new._rebuild()
    for key,path in old.data.get('scriptBindings',{}).items():
        import json
        new.perform({'op':'script.bind','target':json.loads(key),'path':path})
    # Remove only newly extracted copies superseded by a pre-existing shared file.
    referenced = {m['script']['path'] for _,m,_ in new.targets() if m.get('script')}
    for path in list(new.script_files):
        if path not in old.script_files and path not in referenced:
            new.script_files.pop(path);new.files.pop(path,None)
    new._script_writes.clear();new._rebuild()
    return verified(old,new)


def verified(old,new):
    if old.text_files() != new.text_files():
        different = [p for p in old.text_files().keys() | new.text_files().keys() if old.text_files().get(p) != new.text_files().get(p)]
        raise ValueError('Conversion would alter native definitions; original preserved: '+', '.join(different))
    for name in old.data['maps']:
        if old.map_bytes(name) != new.map_bytes(name):
            raise ValueError('Conversion would alter native map bytes; original preserved: '+name)
    if old.data['locales'] != new.data['locales']:
        raise ValueError('Conversion would alter translations; original preserved')
    for path,text in old.script_files.items():
        if new.script_files.get(path) != text: raise ValueError('Conversion would alter '+path)
    for path,source in old.data['assets'].items():
        if new.files[new.data['assets'][path]] != safe(old.root,source).read_bytes(): raise ValueError('Conversion would alter '+path)
    return new


def _backup_path(root, identifier):
    if not isinstance(identifier,str) or not re.fullmatch(r'[a-f0-9]{32}',identifier):
        raise ValueError('Invalid migration backup ID')
    return safe(root,'.studio/migrations/'+identifier+'.json')


def backups(directory):
    root = Path(directory).resolve()
    result = []
    for path in sorted(safe(root,'.studio/migrations').glob('*.json'),reverse=True):
        record = store.read_json(path.read_bytes(),str(path))
        result.append({'id':path.stem,'created':record['created']})
    return {'backups':sorted(result,key=lambda r:r['created'],reverse=True)}


def migrate(directory, dry_run=True, expected_hash=None):
    old = Project(directory)
    if old.data['format'] == store.FORMAT:
        return {'format':store.FORMAT,'alreadyConverted':True,'changedFiles':[],'dryRun':dry_run}
    with locked(old.root):
        old.load();old.check_hash(expected_hash)
        before = store.scan(old.root)
        if before['project.json'] != old._manifest_bytes:
            raise ValueError('Project changed before conversion')
        source_bytes = {p:safe(old.root,p).read_bytes() for p in
                        {*(e['source'] for e in old.data['maps'].values() if 'source' in e),
                         *(old.data['assets'].values() if not isinstance(old,FileProject) else ())}}
        new = converted(old)
        from .workspace import ignore_file
        new.files['.gitignore'] = ignore_file(old.root)
        new.files['.gitattributes'] = store.git_attributes(before.get('.gitattributes',b''))
        changed = sorted(p for p in before.keys() | new.files.keys() if before.get(p) != new.files.get(p))
        report = {'format':store.FORMAT,'from':old.data['format'],'changedFiles':changed,
                  'verified':{'maps':len(old.data['maps']),'definitions':len(old.catalog()),'scripts':len(old.script_files),'assets':len(old.data['assets'])},
                  'projectHash':old.project_hash(),'dryRun':dry_run}
        for path,raw in source_bytes.items():
            if safe(old.root,path).read_bytes() != raw:
                raise ValueError('Original source changed during conversion; original preserved: '+path)
        if store.base_hash(old.base) != new.base_actual_hash:
            raise ValueError('Base resources changed during conversion; original preserved')
        if dry_run: return report
        local = safe(old.root,'.studio/local.json')
        local_before = local.read_bytes() if local.exists() else None
        settings = store.read_json(local_before,str(local)) if local_before is not None else {}
        local_after = encode({**settings,'base':str(old.base)})
        identifier = uuid.uuid4().hex
        sources = {p:store.put_blob(old.root,raw) for p,raw in source_bytes.items()}
        backup = {'created':datetime.now(timezone.utc).isoformat(),'format':old.data['format'],'before':{p:store.put_blob(old.root,b) for p,b in before.items()},
                  'after':store.hashes(new.files),'sources':sources,
                  'localBefore':store.put_blob(old.root,local_before) if local_before is not None else None,'localAfter':digest(local_after)}
        atomic(_backup_path(old.root,identifier),encode(backup))
        atomic(local,local_after)
        store.commit(old.root,before,new.files,new._state,remember=False)
        return {**report,'backup':identifier}


def restore(directory, identifier, dry_run=True, expected_hash=None):
    project = Project(directory)
    with locked(project.root):
        project.load();project.check_hash(expected_hash)
        record = store.read_json(_backup_path(project.root,identifier).read_bytes(),'migration backup')
        original_format = record.get('format','stranded-studio/1')
        current = store.scan(project.root)
        local = safe(project.root,'.studio/local.json')
        if store.hashes(current) == record['before']:
            # Finish local cleanup if the prior restore stopped after publishing
            # the v1 manifest. Preserve any subsequently changed connection.
            if not dry_run and local.is_file() and digest(local.read_bytes()) == record['localAfter']:
                if record['localBefore'] is None: local.unlink()
                else: atomic(local,store.blob(project.root,record['localBefore']))
            return {'format':original_format,'backup':identifier,'changedFiles':[],'dryRun':dry_run,'alreadyRestored':True}
        if store.hashes(current) != record['after']:
            raise ValueError('Project changed after migration; restore refused to preserve later work')
        for path,key in record['sources'].items():
            if not safe(project.root,path).is_file() or digest(safe(project.root,path).read_bytes()) != key:
                raise ValueError('Original source changed after migration: '+path)
        if not local.is_file() or digest(local.read_bytes()) != record['localAfter']:
            raise ValueError('Resource connection changed after migration; restore refused')
        before = {p:store.blob(project.root,key) for p,key in record['before'].items()}
        changes = sorted(p for p in before.keys() | current.keys() if before.get(p)!=current.get(p))
        report = {'format':original_format,'backup':identifier,'changedFiles':changes,'dryRun':dry_run,'projectHash':project.project_hash()}
        if dry_run: return report
        # A compatible local connection may remain after a crash; it points at
        # the exact same base, so v1 remains usable before local cleanup retries.
        store.commit(project.root,current,before,{'revision':project.data['revision'],'undo':[],'redo':[]},remember=False)
        if record['localBefore'] is None: local.unlink(missing_ok=True)
        else: atomic(local,store.blob(project.root,record['localBefore']))
        return report
