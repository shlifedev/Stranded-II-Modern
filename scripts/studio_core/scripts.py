"""File-backed native scripts. No alternate script interpreter or code generation."""
from copy import deepcopy
from pathlib import Path
import json
import re

PREFIX = 'sys/scripts/studio/'
MAX_FILE = 1024 * 1024
MAX_TOTAL = 16 * MAX_FILE


def path_name(value):
    from .project import safe
    if not isinstance(value, str) or not value.startswith('scripts/') or not value.endswith('.s2s'):
        raise ValueError('Use scripts/<name>.s2s inside the project')
    safe(Path('.'), value)
    if any(not re.fullmatch(r'[A-Za-z0-9_][A-Za-z0-9_.-]*', p) for p in value.split('/')[1:]):
        raise ValueError('Script path components must use ASCII letters, numbers, _, - and .')
    return value


def native_path(path):
    return PREFIX + path_name(path)[len('scripts/'):]


def clean(text):
    if not isinstance(text, str) or any(c in text for c in ('\0', '\x1f', '\x1e', '\x1d', '\ufeff')):
        raise ValueError('Script must be UTF-8 text without BOM or reserved controls')
    text = text.replace('\r\n', '\n').replace('\r', '\n')
    if len(text.encode('utf8')) > MAX_FILE:
        raise ValueError('Script exceeds 1 MiB')
    return text


def scan(project):
    from .project import safe
    result = {}; folded = set(); total = 0
    directory = safe(project.root, 'scripts')
    if directory.exists():
        for path in sorted(directory.rglob('*')):
            if not path.is_file() or path.suffix.lower() != '.s2s':
                continue
            relative = path_name(path.relative_to(project.root).as_posix())
            if relative.casefold() in folded:
                raise ValueError('Case-insensitive script path collision: '+relative)
            folded.add(relative.casefold())
            raw = safe(project.root, relative).read_bytes(); total += len(raw)
            if len(raw) > MAX_FILE or total > MAX_TOTAL or len(result) >= 1000:
                raise ValueError('Script workspace exceeds 1000 files / 16 MiB')
            result[relative] = clean(raw.decode('utf8'))
    return result


def version(files):
    from .project import digest, encode
    return digest(encode(dict(sorted(files.items()))))


def target_key(target):
    scope = target.get('scope')
    if scope == 'map':
        value = {'scope': scope, 'map': target['map']}
    elif scope in ('entity', 'definition'):
        value = {'scope': scope, 'kind': target['kind'], 'id': target['id']}
        if target['kind']!='recipe':
            from .maps import number
            identifier=target['id']
            if isinstance(identifier,str) and re.fullmatch(r'[0-9]+',identifier): identifier=int(identifier)
            value['id']=number(identifier,integer=True,minimum=1)
        if scope == 'entity': value['map'] = target['map']
    else:
        raise ValueError('Script target scope must be map, entity or definition')
    return json.dumps(value, sort_keys=True, separators=(',', ':'))


def target_text(project, target):
    from .maps import EditableMap
    scope = target['scope']
    if scope == 'definition':
        return project.find(target['kind'], target['id']).properties.get('script', [''])[0]
    model = EditableMap(project.map_bytes(target['map']))
    if scope == 'map': return model.summary()['script']
    if scope == 'entity':
        model.entity(target['kind'], target['id'])
        return next(e['script'] for e in model.summary()['entities'] if e['kind'] == target['kind'] and e['id'] == target['id'])
    raise ValueError('Unknown script target')


def binding(project, target):
    return project.data.get('scriptBindings', {}).get(target_key(target))


def source_bindings(project):
    if project.data['format'] in ('stranded-studio/2','stranded-studio/3'):
        return {target_key(target):meta['script']['path'] for _,meta,target in project.targets() if meta.get('script')}
    return project.data.get('scriptBindings', {})


def write(project, path, text, expected):
    from .project import digest
    path = path_name(path); text = clean(text)
    old = project.script_files.get(path)
    actual = digest(old.encode('utf8')) if old is not None else None
    if actual != expected:
        raise ValueError('Script file conflict: '+path+'; reload or save the draft to another file')
    if any(p.casefold() == path.casefold() and p != path for p in project.script_files):
        raise ValueError('Case-insensitive script path collision')
    size=sum(len(t.encode('utf8')) for p,t in project.script_files.items() if p!=path)+len(text.encode('utf8'))
    if size>MAX_TOTAL or (old is None and len(project.script_files)>=1000):
        raise ValueError('Script workspace exceeds 1000 files / 16 MiB')
    project.script_files[path] = text
    project._script_writes.add(path)
    project._texts = project._catalog = None
    return {'path': path, 'hash': digest(text.encode('utf8'))}


def perform(project, operation):
    op = operation['op']
    # Templates also call this service directly; v2 stores bindings beside each
    # target rather than in the legacy in-memory manifest.
    if project.data['format'] in ('stranded-studio/2','stranded-studio/3') and op != 'script.write':
        return project.perform(operation)
    if op == 'script.write':
        if 'expectedHash' not in operation: raise ValueError('script.write requires expectedHash (null for a new file)')
        return write(project, operation['path'], operation['text'], operation['expectedHash'])
    target = operation['target']; key = target_key(target)
    text = target_text(project, target)  # Validate the target before changing anything.
    if op == 'script.unbind':
        project.data.setdefault('scriptBindings', {}).pop(key, None)
        project._texts = project._catalog = None
        scope = target['scope']
        if scope == 'definition':
            project.perform({'op': 'content.set', 'kind': target['kind'], 'id': target['id'], 'properties': {'script': text}})
        else:
            project.perform({'op': 'map.script' if scope == 'map' else 'entity.script', **{k:v for k,v in target.items() if k!='scope'}, 'text': text})
        return {'target': target, 'path': None}
    path = path_name(operation['path'])
    if op == 'script.extract':
        write(project, path, text, None)
    elif op != 'script.bind':
        raise ValueError('Unknown script operation: '+op)
    if path not in project.script_files: raise ValueError('Script file missing: '+path)
    project.data.setdefault('scriptBindings', {})[key] = path
    project._texts = project._catalog = None
    return {'target': target, 'path': path}


def route_write(project, operation):
    """Old script setters also address a bound file; never maintain two originals."""
    op = operation['op']
    target = None; text = None
    if op in ('map.script', 'entity.script'):
        target = {'scope': 'map' if op == 'map.script' else 'entity', 'map': operation['map']}
        if op == 'entity.script': target.update(kind=operation['kind'], id=operation['id'])
        text = operation['text']
    elif op == 'content.set' and 'script' in operation.get('properties', {}):
        target = {'scope': 'definition', 'kind': operation['kind'], 'id': operation['id']}
        text = operation['properties']['script']
        if isinstance(text, list):
            if len(text) != 1: raise ValueError('One script per definition')
            text = text[0]
        if text is None: text = ''
    path = binding(project, target) if target else None
    if not path: return None
    if 'expectedHash' not in operation:
        raise ValueError('Bound script requires expectedHash; use the script file editor: '+path)
    result = write(project, path, text, operation['expectedHash'])
    if op == 'content.set':
        rest = deepcopy(operation); rest['properties'].pop('script')
        if rest['properties']: project.perform(rest)
    return result


def overlay_definitions(project, files):
    from . import content
    for key, path in project.data.get('scriptBindings', {}).items():
        target = json.loads(key)
        if target['scope'] != 'definition' or path not in project.script_files: continue
        matches = [r for name,text in files.items() for r in content.records(name,text)
                   if r.kind == target['kind'] and str(r.identifier) == str(target['id'])]
        if len(matches) != 1: continue  # A diagnostic reports a removed target.
        r = matches[0]; text = files[r.path]
        files[r.path] = text[:r.start]+content.edit(r.text, {'script':project.script_files[path]})+text[r.end:]
    return files


def overlay_map(project, name, raw):
    from .maps import EditableMap
    for key,path in project.data.get('scriptBindings', {}).items():
        target = json.loads(key)
        if target.get('map') != name or path not in project.script_files: continue
        values = {k:v for k,v in target.items() if k not in ('scope','map')}
        raw = EditableMap(raw).apply({'op':'map.script' if target['scope']=='map' else 'entity.script', **values, 'text':project.script_files[path]})
    return raw


def resources(project):
    from .project import MOD
    files = dict(project.data['files'])
    effective = project.text_files()
    # Bound definitions may come from an otherwise untouched base INF.
    for key in project.data.get('scriptBindings', {}):
        target = json.loads(key)
        if target['scope'] == 'definition':
            r = project.find(target['kind'], target['id']); files[r.path] = effective[r.path]
    files.update({MOD+native_path(p):t for p,t in project.script_files.items()})
    return files


def analyze(text, path):
    """Conservative lexical diagnostics, preserving the native parser as authority."""
    issues=[]; stack=[]; code=[]; quoted=[]; row=1; col=1; i=0
    def issue(message, line, column):
        issues.append({'severity':'error','code':'script-syntax','message':message,'location':path,'path':path,'line':line,'column':column})
    while i < len(text):
        c=text[i]
        if c=='\n': code.append(c);row+=1;col=1;i+=1;continue
        if text.startswith('//', i) or c=='#':
            end=text.find('\n',i);end=len(text) if end<0 else end
            code.append(' '*(end-i));col+=end-i;i=end;continue
        if c=='"':
            start=i; line=row; column=col;i+=1;col+=1
            while i<len(text) and text[i] not in ('"','\n'):
                # The native language uses quotes as delimiters, not JS escapes.
                i+=1;col+=1
            if i==len(text) or text[i]!='"': issue('Unterminated string',line,column)
            else:
                quoted.append({'value':text[start+1:i],'line':line,'column':column});i+=1;col+=1
            code.append(' '*(i-start));continue
        if c in '{(': stack.append((c,row,col))
        elif c in '})':
            expected='{' if c=='}' else '('
            if not stack or stack[-1][0]!=expected: issue('Unmatched '+c,row,col)
            else: stack.pop()
        code.append(c);i+=1;col+=1
    for c,row,col in stack: issue('Unclosed '+c,row,col)
    code=''.join(code)
    events=[{'name':m[1],'line':code.count('\n',0,m.start())+1} for m in re.finditer(r'\bon\s*:\s*([A-Za-z_][\w]*)',code)]
    refs=[{**q,'path':'scripts/'+q['value'].replace('\\','/')[len(PREFIX):]} for q in quoted
          if q['value'].replace('\\','/').startswith(PREFIX) and q['value'].endswith('.s2s')]
    return {'events':events,'references':refs,'diagnostics':issues}


def workspace(project, query=''):
    from .project import digest
    files=[]; diagnostics=[]; matches=[]
    for path,text in sorted(project.script_files.items()):
        info=analyze(text,path); diagnostics.extend(info.pop('diagnostics'))
        for ref in info['references']:
            if ref['path'] not in project.script_files:
                diagnostics.append({'severity':'error','code':'missing-script','message':'Missing script: '+ref['path'], 'location':path,'path':path,'line':ref['line'],'column':ref['column']})
        files.append({'path':path,'hash':digest(text.encode('utf8')),'runtimePath':native_path(path), **info})
        if query:
            for row,line in enumerate(text.splitlines(),1):
                if query.casefold() in line.casefold() and len(matches)<500:
                    matches.append({'path':path,'line':row,'text':line[:300]})
    bindings=[]
    for key,path in project.data.get('scriptBindings',{}).items():
        target=json.loads(key);bindings.append({'target':target,'path':path})
        try:
            if path not in project.script_files: raise ValueError('Missing script: '+path)
            target_text(project,target)
        except (ValueError,KeyError) as exc:
            diagnostics.append({'severity':'error','code':'script-binding','message':str(exc),'location':path,'path':path,'line':1,'column':1})
    return {'version':version(project.script_files),'files':files,'bindings':bindings,'diagnostics':diagnostics,'matches':matches}


def source_map(project, map_name):
    from .maps import EditableMap, CLASSES
    from .project import digest
    records=[]; model=EditableMap(project.map_bytes(map_name))
    for path,text in project.script_files.items():
        records.append({'path':path,'lines':text.splitlines(),'hash':digest(text.encode('utf8'))})
    return {'files':records,'bindings':[{'target':json.loads(k),'path':p} for k,p in source_bindings(project).items()],
            'map':map_name,'entities':[{'class':CLASSES[e['kind']],'id':e['id'],'kind':e['kind'],'type':e['type']} for e in model.entities]}


def commit_files(project, before, history):
    """Recoverable multi-file commit. Never recover over an unrelated external edit."""
    from .project import safe, atomic, encode, digest
    changes=[]
    for path in sorted(project._script_writes):
        p=safe(project.root,path);raw=p.read_bytes() if p.exists() else None
        original=project._script_originals.get(path)
        if (clean(raw.decode('utf8')) if raw is not None else None) != original:
            raise ValueError('Script changed during operation: '+path)
        changes.append({'path':path,'before':digest(raw) if raw is not None else None,'text':project.script_files.get(path)})
    journal={'files':changes,'project':project.data,'history':history}
    if changes:
        atomic(safe(project.root,'.studio/script-transaction.json'),encode(journal))
        recover(project.root)
    else:
        atomic(project.root/'.studio/history.json',encode(history));atomic(project.path,encode(project.data))


def recover(root):
    from .project import safe, atomic, encode, digest
    journal=safe(root,'.studio/script-transaction.json')
    if not journal.exists(): return
    data=json.loads(journal.read_text('utf8'))
    for change in data['files']:
        path=safe(root,path_name(change['path']));raw=path.read_bytes() if path.exists() else None
        new=clean(change['text']).encode('utf8') if change['text'] is not None else None
        if (digest(raw) if raw is not None else None) not in (change['before'],digest(new) if new is not None else None):
            raise ValueError('Recovery conflict; preserve both versions in .studio/script-transaction.json: '+change['path'])
    for change in data['files']:
        path=safe(root,change['path'])
        if change['text'] is None: path.unlink(missing_ok=True)
        else: atomic(path,clean(change['text']).encode('utf8'))
    atomic(root/'.studio/history.json',encode(data['history']));atomic(root/'project.json',encode(data['project']))
    journal.unlink()
