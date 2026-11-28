"""Acknowledged scene snapshots. Tokens survive CLI processes, never alter maps.

Only an explicit full preview advances the resource baseline. Deltas compare
saved final states, so undo, redo and edits from another process work identically.
"""
from contextlib import redirect_stdout
from pathlib import Path
from tempfile import TemporaryDirectory
import base64
import hashlib
import json
import re
import struct
import subprocess
import sys

from .maps import CLASSES, FIELDS, EditableMap, string


MAX_BASELINES = 128


def changed_heights(before, after):
    size = after.size + 1
    row_bytes = size * 4
    length = size * row_bytes
    new = memoryview(after.data)[after.heights_start:after.heights_start + length]
    if before.data.startswith(new, before.heights_start):
        return []

    old = memoryview(before.data)
    changes = []
    for gx in range(1, size):
        offset = gx * row_bytes
        row = new[offset + 4:offset + row_bytes]
        start = before.heights_start + offset + 4
        if before.data.startswith(row, start):
            continue
        previous = struct.iter_unpack('<f', old[start:start + len(row)])
        current = struct.iter_unpack('<f', row)
        for gz, (old_height, new_height) in enumerate(zip(previous, current), start=1):
            if old_height != new_height:
                changes.append((gx, gz, new_height[0]))
    return changes


def preview_assets(assets):
    import web_audio
    with TemporaryDirectory() as directory:
        staging = Path(directory)
        wavs = []
        names = {}
        for name, data in assets:
            if not name.lower().endswith('.wav') or web_audio.supported_wav_header(data[:36]):
                yield name, data
                continue
            source = staging / str(len(wavs)) / Path(name).name
            source.parent.mkdir()
            source.write_bytes(data)
            target = f'/game/{len(wavs)}.wav'
            names[target] = name
            wavs.append((source, target))
        if wavs:
            try:
                with redirect_stdout(sys.stderr):
                    prepared = web_audio.prepare(wavs, staging / 'pcm')
            except (subprocess.CalledProcessError, IndexError) as exc:
                raise ValueError(f'Could not prepare imported WAV audio for preview: {exc}') from exc
            for target, source in prepared.items():
                yield names[target], source.read_bytes()


def encode_patch(before, after):
    old = {(e['kind'], e['id']): e for e in before.entities}
    new = {(e['kind'], e['id']): e for e in after.entities}
    removed = [key for key in old if key not in new or old[key]['type'] != new[key]['type']]
    changed = [e for key, e in new.items() if old.get(key) != e]
    heights = changed_heights(before, after)
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


def preview(project, map_name, since=None, include_sources=True):
    from .project import MOD, encode, safe
    from .scripts import resources as script_resources, source_map, version
    from .fonts import extra_map_glyphs
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
    raw = project.map_bytes(map_name)
    model = EditableMap(raw)
    inputs = {'files':script_resources(project), **{k:project.data[k] for k in ('assets','locales')}}
    extra = extra_map_glyphs(project, model)
    if extra:
        inputs['mapGlyphs'] = extra
    resources = hashlib.sha256(encode(inputs)).hexdigest()
    available = previous['available'] if previous else [f'{r.kind}/{r.identifier}' for r in project.catalog()]
    required = any(f'{e["kind"]}/{e["type"]}' not in available for e in model.entities)
    baseline = previous['resources'] if previous else resources
    state = {'map':map_name, 'revision':project.data['revision'], 'resources':baseline,
             'available':available, 'data':base64.b64encode(raw).decode('ascii')}
    snapshot = encode(state)
    token = hashlib.sha256(snapshot).hexdigest()
    result = {'mode':'delta' if previous else 'full', 'token':token, 'baseToken':since,
              'scriptsVersion':version(project.script_files), 'resourcesVersion':resources,
              'map':'maps/studio.s2', 'revision':project.data['revision'], 'available':available,
              'resourcesPending':resources != baseline, 'resourcesRequired':required,
              'files':{MOD+'maps/studio.s2':state['data']}}
    if include_sources:
        result['sources'] = source_map(project, map_name, model)
    if previous:
        before = EditableMap(base64.b64decode(previous['data']))
        if before.size != model.size:
            raise ValueError('Terrain dimensions changed; explicitly reopen the map')
        patch, counts = encode_patch(before, model)
        result.update(patch=base64.b64encode(patch).decode('ascii'), changes=counts)
    else:
        result['files'].update(project.preview_resources())
    return result, snapshot


def save_baseline(root, token, since, snapshot):
    from .project import atomic, safe
    cache = safe(root, '.studio/previews')
    atomic(cache / (token + '.json'), snapshot)
    protected = {token, since} if since else {token}
    others = (path for path in cache.glob('*.json') if path.stem not in protected)
    newest = sorted(others, key=lambda path: path.stat().st_mtime_ns, reverse=True)
    for path in newest[MAX_BASELINES - len(protected):]:
        path.unlink(missing_ok=True)
