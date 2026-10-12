"""Acknowledged scene snapshots. Tokens survive CLI processes, never alter maps.

Only an explicit full preview advances the resource baseline. Deltas compare
saved final states, so undo, redo and edits from another process work identically.
"""
import base64
import hashlib
import json
import re
import struct

from .maps import CLASSES, FIELDS, EditableMap, string


def encode_patch(before, after):
    old = {(e['kind'], e['id']): e for e in before.entities}
    new = {(e['kind'], e['id']): e for e in after.entities}
    removed = [key for key in old if key not in new or old[key]['type'] != new[key]['type']]
    changed = [e for key, e in new.items() if old.get(key) != e]
    size = after.size+1
    old_heights = before.data[before.heights_start:before.heights_start+size*size*4]
    new_heights = after.data[after.heights_start:after.heights_start+size*size*4]
    heights = []
    if old_heights != new_heights:
        for index,(old_h,new_h) in enumerate(zip(struct.iter_unpack('<f',old_heights),struct.iter_unpack('<f',new_heights))):
            if old_h != new_h:
                heights.append((index//size,index%size,new_h[0]))
    data = bytearray(struct.pack('<ii', 1, len(removed)))
    for kind, identifier in removed:
        data += struct.pack('<Bi', CLASSES[kind], identifier)
    data += struct.pack('<i', len(heights))
    for x,z,height in heights:
        data += struct.pack('<iif', x,z,height)
    data += struct.pack('<i', len(changed))
    for e in changed:
        data += struct.pack('<Bii', CLASSES[e['kind']], e['id'], e['type'])
        for field, code in FIELDS[e['kind']]:
            data += struct.pack('<'+code, e[field])
        if e['kind'] == 'info':
            data += string(e['vars'])
    return bytes(data), {'removed':len(removed), 'upserted':len(changed), 'heights':len(heights)}


def preview(project, map_name, since=None):
    from .project import MOD, atomic, encode, safe
    cache = safe(project.root, '.studio/previews')
    previous = None
    if since:
        if not re.fullmatch('[a-f0-9]{64}', since):
            raise ValueError('Invalid preview token')
        path = safe(cache, since+'.json')
        if not path.is_file():
            raise ValueError('Preview token expired; explicitly apply resources to reconnect')
        cached = path.read_bytes()
        if hashlib.sha256(cached).hexdigest() != since:
            raise ValueError('Preview baseline is damaged; explicitly apply resources to reconnect')
        previous = json.loads(cached)
        if previous['map'] != map_name:
            raise ValueError('Preview token belongs to a different map')
    resources = hashlib.sha256(encode({k:project.data[k] for k in ('files','assets','locales')})).hexdigest()
    raw = project.map_bytes(map_name)
    model = EditableMap(raw)
    available = previous['available'] if previous else [f'{r.kind}/{r.identifier}' for r in project.catalog()]
    required = any(f'{e["kind"]}/{e["type"]}' not in available for e in model.entities)
    baseline = previous['resources'] if previous else resources
    state = {'map':map_name, 'revision':project.data['revision'], 'resources':baseline,
             'available':available, 'data':base64.b64encode(raw).decode('ascii')}
    token = hashlib.sha256(encode(state)).hexdigest()
    atomic(cache/(token+'.json'), encode(state))
    # Keep recent baselines without unbounded map copies. An expired token is an
    # explicit error, never an excuse for an automatic viewport reload.
    for path in sorted(cache.glob('*.json'), key=lambda p:p.stat().st_mtime, reverse=True)[128:]:
        if path.stem not in (token,since):
            path.unlink(missing_ok=True)
    result = {'mode':'delta' if previous else 'full', 'token':token, 'baseToken':since,
              'map':'maps/studio.s2', 'revision':project.data['revision'], 'available':available,
              'resourcesPending':resources != baseline, 'resourcesRequired':required,
              'files':{MOD+'maps/studio.s2':state['data']}}
    if previous:
        before = EditableMap(base64.b64decode(previous['data']))
        if before.size != model.size:
            raise ValueError('Terrain dimensions changed; explicitly reopen the map')
        patch, counts = encode_patch(before, model)
        result.update(patch=base64.b64encode(patch).decode('ascii'), changes=counts)
    else:
        result['files'].update(project.preview_resources())
    return result
