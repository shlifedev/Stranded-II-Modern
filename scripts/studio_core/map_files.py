"""Lossless map sections with independently editable entity JSON and scripts."""
import re
import struct

from . import scripts
from .file_store import read_json, FORMAT
from .maps import EditableMap, FIELDS, CLASSES, number, string
from .project import encode


def script_text(raw):
    value = raw.decode('utf8')
    endings = re.findall(r'\x1f|\r\n|\r|\n', value)
    return scripts.clean(re.sub(r'\x1f|\r\n|\r', '\n', value)), endings


def script_bytes(text, ref):
    lines = text.split('\n')
    endings = ref.get('lineEndings', [])
    return ''.join(line+(endings[i] if i < len(endings) else '\x1f') if i < len(lines)-1 else line
                   for i, line in enumerate(lines)).encode('utf8')


def split(project, name, raw):
    model = EditableMap(raw)
    if model.protected:
        raise ValueError('Password-protected maps must be unlocked before importing')
    prefix = 'maps/'+name+'/'
    old_map = read_json(project.files[prefix+'map.json'], prefix+'map.json') if prefix+'map.json' in project.files else {}
    output = {}
    changes = {}
    compact = project.manifest['format'] == FORMAT
    meta = {**old_map, 'format': 'stranded-map/2' if compact else 'stranded-map/1'}

    def extract(raw_text, target, old):
        text, endings = script_text(raw_text)
        ref = project.script_ref(target, text, old)
        if any(e != '\x1f' for e in endings):
            ref['lineEndings'] = endings
        else:
            ref.pop('lineEndings', None)
        return ref

    global_script = model.fields['global_script']
    if global_script.data or old_map.get('script'):
        meta['script'] = extract(global_script.data, {'scope':'map','map':name}, old_map.get('script'))
    changes['global_script'] = b''
    previous = {(entry['kind'],entry['id']):entry for _,entry in entity_entries(project,name,old_map)}
    orders = {kind: max([e.get('order', 0) for e in previous.values() if e['kind'] == kind]+[-1024]) for kind in CLASSES}
    entities = []
    seen = set()
    for index, entity in enumerate(model.entities):
        kind, identifier = entity['kind'], entity['id']
        path = prefix+f'entities/{kind}-{identifier}.json'
        if (kind,identifier) in seen:
            raise ValueError(f'Duplicate entity ID: {kind}/{identifier}')
        seen.add((kind,identifier))
        old = previous.get((kind, identifier), {})
        if not old:
            orders[kind] += 1024
        entry = {**old, **entity, 'order': old.get('order', orders[kind])}
        matches = [e for e in model.extensions if e[2] == (CLASSES[kind],identifier) and e[3] == 0]
        if len(matches) > 1:
            raise ValueError(f'Multiple scripts on {kind}/{identifier}; preserve the original map')
        if matches:
            begin, _, _, _, values = matches[0]
            field = next(f for f in model.source.fields if f.start == begin+10+4+len(values[0]))
            changes[field.key] = b''
            if values[1] or old.get('script'):
                entry['script'] = extract(values[1], {'scope':'entity','map':name,'kind':kind,'id':identifier}, old.get('script'))
        else:
            entry.pop('script', None)
        if compact:
            entities.append(entry)
        else:
            output[path] = project.files[path] if path in project.files and read_json(project.files[path],path)==entry else encode(entry)
    if compact: meta['entities'] = entities
    skeleton = EditableMap(model.source.write(changes))
    terrain_start = skeleton.colors_start-4
    output.update({prefix+'map.json': project.files[prefix+'map.json'] if old_map==meta else encode(meta), prefix+'native/header.bin': skeleton.data[:terrain_start],
                   prefix+'terrain.bin': skeleton.data[terrain_start:skeleton.sections['object'][0]],
                   prefix+'native/tail.bin': skeleton.data[skeleton.sections['info'][1]:]})
    for path in list(project.files):
        if path.startswith(prefix) and path not in output:
            del project.files[path]
    project.files.update(output)
    project._map_cache = {}


def entity_entries(project, name, meta=None):
    prefix = 'maps/'+name+'/'
    meta = meta if meta is not None else read_json(project.files[prefix+'map.json'],prefix+'map.json')
    if meta.get('format') == 'stranded-map/2':
        if any(p.startswith(prefix+'entities/') for p in project.files):
            raise ValueError('Both map JSON and entity files own '+name)
        if not isinstance(meta.get('entities'),list):
            raise ValueError('Map entities must be an array: '+name)
        for entry in meta['entities']:
            if not isinstance(entry,dict): raise ValueError('Invalid map entity: '+name)
            yield prefix+'map.json',entry
    else:
        for path,raw in project.files.items():
            if path.startswith(prefix+'entities/'):
                if not path.endswith('.json'): raise ValueError('Unexpected entity file: '+path)
                yield path,read_json(raw,path)


def set_entity(project, name, entity):
    path = 'maps/'+name+'/map.json'
    meta = read_json(project.files[path],path)
    meta['entities'] = [entity if (e['kind'],e['id'])==(entity['kind'],entity['id']) else e for e in meta['entities']]
    project.files[path] = encode(meta)
    project._map_cache = {}


def assemble(project, name):
    prefix = 'maps/'+name+'/'
    meta = read_json(project.files[prefix+'map.json'], prefix+'map.json')
    if meta.get('format') not in ('stranded-map/1','stranded-map/2'):
        raise ValueError('Unsupported map format: '+name)
    header = project.files[prefix+'native/header.bin']
    lines = header.splitlines()
    if len(lines) < 7:
        raise ValueError('Incomplete native map header: '+name)
    formats = (lines[6]+b'000')[:3]
    entities = []
    seen = set()
    for path, e in entity_entries(project,name,meta):
        if e.get('kind') not in CLASSES:
            raise ValueError('Unknown entity kind: '+path)
        number(e['id'], 'id', 1, 2147483647, True)
        number(e['order'], 'order', 0, integer=True)
        if meta['format']=='stranded-map/1' and path != prefix+f'entities/{e["kind"]}-{e["id"]}.json':
            raise ValueError('Entity filename and ID disagree: '+path)
        key = (e['kind'],e['id'])
        if key in seen: raise ValueError('Duplicate entity ID: '+str(key))
        seen.add(key)
        fields = dict(FIELDS[e['kind']])
        values = {field:e[field] for field in fields}
        if e['kind'] == 'info': values['vars'] = e['vars']
        EditableMap.validate_values(e['kind'], values)
        entities.append(e)
    out = bytearray(header+project.files[prefix+'terrain.bin'])
    for index, kind in enumerate(CLASSES):
        records = sorted((e for e in entities if e['kind'] == kind), key=lambda e:(e['order'], e['id']))
        out += struct.pack('<i', len(records))
        code = 'B' if kind == 'info' or formats[index] == ord('0') else 'H'
        for e in records:
            number(e['type'], 'type', 1, 255 if code == 'B' else 65535, True)
            out += struct.pack('<i'+code, e['id'], e['type'])
            for field, fmt in FIELDS[kind]:
                out += struct.pack('<'+fmt, e[field])
            if kind == 'info': out += string(e['vars'])
    out += project.files[prefix+'native/tail.bin']
    model = EditableMap(bytes(out))
    changes = {}
    if meta.get('script'):
        ref = meta['script']
        changes['global_script'] = script_bytes(project.script_files.get(ref['path'],''), ref)
    for e in entities:
        if not e.get('script'): continue
        ref = e['script']
        matches = [x for x in model.extensions if x[2] == (CLASSES[e['kind']],e['id']) and x[3] == 0]
        if len(matches) != 1:
            raise ValueError('Script metadata does not match the native map: '+name)
        begin, _, _, _, values = matches[0]
        field = next(f for f in model.source.fields if f.start == begin+10+4+len(values[0]))
        changes[field.key] = script_bytes(project.script_files.get(ref['path'],''), ref)
    return model.source.write(changes)


def targets(project):
    for path, raw in project.files.items():
        if path.startswith('maps/') and path.endswith('.json'):
            parts = path.split('/')
            value = read_json(raw, path)
            if len(parts) == 3 and parts[2] == 'map.json':
                target = {'scope':'map','map':parts[1]}
            elif len(parts) == 4 and parts[2] == 'entities':
                target = {'scope':'entity','map':parts[1],'kind':value['kind'],'id':value['id']}
            else:
                raise ValueError('Unexpected map metadata: '+path)
            yield path, value, target
            if value.get('format') == 'stranded-map/2':
                for _,entry in entity_entries(project,parts[1],value):
                    yield path,entry,{'scope':'entity','map':parts[1],'kind':entry['kind'],'id':entry['id']}
