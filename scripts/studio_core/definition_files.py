"""One native INF block per definition; compile overrides into original files."""
from . import content
from .file_store import read_json
from .project import MOD, digest, encode, resource_path


def stem(kind, identifier):
    key = digest(str(identifier).encode())[:24] if kind == 'recipe' else str(identifier)
    return f'definitions/{kind}/{key}'


def blocks(path, text):
    records = content.records(path, text)
    return text[:records[0].start] if records else text, [
        (r, text[r.start:records[i+1].start if i+1 < len(records) else len(text)])
        for i, r in enumerate(records)]


def entries(project):
    seen = set()
    for path, raw in sorted(project.files.items()):
        if not path.startswith('definitions/') or not path.endswith('.meta.json'):
            continue
        meta = read_json(raw, path)
        kind, identifier = meta['kind'], meta['id']
        if kind not in content.PREFIXES:
            raise ValueError('Invalid definition kind: '+path)
        key = kind, str(identifier)
        if key in seen:
            raise ValueError('Duplicate definition ID: '+str(key))
        seen.add(key)
        source = resource_path(meta['source'])
        if not source.startswith(MOD+'sys/') or not source.endswith('.inf'):
            raise ValueError('Invalid definition source: '+source)
        body_path = path[:-len('.meta.json')]+'.inf'
        body = project.files[body_path].decode('utf8')
        parsed = content.records(source, body)
        if len(parsed) != 1 or parsed[0].kind != kind or (kind != 'recipe' and str(parsed[0].identifier) != str(identifier)):
            raise ValueError('Definition file must contain exactly its declared ID: '+body_path)
        if not isinstance(meta['order'], int) or meta['order'] < 0:
            raise ValueError('Invalid definition order: '+path)
        yield path, meta, body


def render(project, body, meta):
    ref = meta.get('script')
    if not ref:
        return body
    text = project.script_files.get(ref['path'],'')
    fields = [f for f in content.fields(body) if f[0] == 'script']
    if len(fields) != 1 or fields[0][1]:
        raise ValueError('Keep script bodies in '+ref['path']+'; the INF script block must remain empty')
    _, _, start, end = fields[0]
    native = ref.get('prefix','script=start\n')+text
    if text and not text.endswith(('\n','\r')): native += '\n'
    native += ref.get('suffix','script=end\n')
    return body[:start]+native+body[end:]


def compile_files(project):
    sources = {}
    consumed = set()
    for path, meta, body in entries(project):
        sources.setdefault(meta['source'], []).append((meta, render(project, body, meta)))
        consumed.update((path,path[:-len('.meta.json')]+'.inf'))
    descriptions = {}
    for path, raw in project.files.items():
        if path.startswith('definitions/_sources/') and path.endswith('.json'):
            value = read_json(raw, path)
            descriptions[value['source']] = (value, project.files[path[:-5]+'.head'].decode('utf8'))
            sources.setdefault(value['source'], [])
            consumed.update((path,path[:-5]+'.head'))
    unexpected = [p for p in project.files if p.startswith('definitions/') and p not in consumed]
    if unexpected: raise ValueError('Definition file has no matching metadata: '+unexpected[0])
    output, aliases = {}, {}
    for source, values in sources.items():
        if source in project.raw_definitions:
            raise ValueError('Both raw resource and definition blocks own '+source)
        prefix, originals = blocks(source, project.base_texts.get(source, ''))
        if source in descriptions:
            descriptor, prefix = descriptions[source]
            if descriptor.get('replace'): originals = []
        emitted = []
        used = set()
        for index, (record, body) in enumerate(originals):
            matches = [(i,m,b) for i,(m,b) in enumerate(values) if m.get('slot') == index]
            if len(matches)>1: raise ValueError('Duplicate definition source slot: '+source)
            if matches:
                i, meta, body = matches[0];used.add(i);identifier = meta['id']
            else:
                identifier = record.identifier
            emitted.append((body, identifier))
        for i, (meta, body) in sorted(enumerate(values), key=lambda pair:(pair[1][0]['order'], str(pair[1][0]['id']))):
            if i not in used:
                if originals and meta.get('slot') is not None:
                    raise ValueError('Definition source slot no longer exists: '+source)
                emitted.append((body, meta['id']))
        text = prefix
        for body, _ in emitted:
            if text and not text.endswith(('\n','\r')) and body and not body.startswith(('\n','\r')):
                text += '\n'
            text += body
        output[source] = text
        for index, (_, identifier) in enumerate(emitted):
            aliases[(source, f'{source}#{index}')] = identifier
    output.update(project.raw_definitions)
    return output, aliases


def write_block(project, meta, body):
    path = next((p[:-len('.meta.json')] for p,m,_ in entries(project)
                 if m['kind']==meta['kind'] and str(m['id'])==str(meta['id'])),stem(meta['kind'], meta['id']))
    old = meta.get('script')
    fields = [f for f in content.fields(body) if f[0] == 'script']
    if len(fields)>1:
        raise ValueError('Multiple script fields in one definition')
    if fields:
        _, text, begin, end = fields[0]
        block = body[begin:end]
        lines = block.splitlines(keepends=True)
        if len(lines)<2 or lines[0].strip() != 'script=start' or lines[-1].strip() != 'script=end':
            raise ValueError('Definition scripts require a script=start/end block')
        payload = ''.join(lines[1:-1])
        ref = project.script_ref({'scope':'definition','kind':meta['kind'],'id':meta['id']}, payload, old)
        ref.update(prefix=lines[0], suffix=lines[-1])
        meta['script'] = ref
        body = body[:begin]+'script=start\nscript=end\n'+body[end:]
    elif old:
        meta.pop('script', None)
    project.files[path+'.inf'] = body.encode('utf8')
    original = project.files.get(path+'.meta.json')
    project.files[path+'.meta.json'] = original if original and read_json(original,path)==meta else encode(meta)


def import_file(project, source, text):
    """Split v1 text, retaining unusual headers, gaps and original record order."""
    prefix, records = blocks(source, text)
    base_prefix, originals = blocks(source, project.base_texts.get(source, ''))
    if not records:
        project.files['resources/'+source[len(MOD):]] = text.encode('utf8')
        return
    overlay = prefix == base_prefix and len(records) >= len(originals) and all(
        records[i][0].identifier == r.identifier for i,(r,_) in enumerate(originals))
    if not overlay or not originals and prefix:
        key = 'definitions/_sources/'+digest(source.encode())[:24]
        project.files[key+'.json'] = encode({'source':source,'replace':not overlay})
        project.files[key+'.head'] = prefix.encode('utf8')
    for index, (record, body) in enumerate(records):
        if overlay and index < len(originals) and body == originals[index][1]:
            continue
        meta = {'kind':record.kind,'id':record.identifier,'source':source,'order':index*1024}
        if overlay and index < len(originals): meta['slot'] = index
        write_block(project, meta, body)
