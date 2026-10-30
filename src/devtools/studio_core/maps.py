"""Conservative map edits on top of the existing lossless s2map reader.

Only addressed records/height samples are replaced. Unknown strings, extensions,
states, attachments and all other bytes retain their original representation.
"""
import math
import struct

from s2map import Map

CLASSES = {'object': 1, 'unit': 2, 'item': 3, 'info': 4}
WORLD_SIZE = 64
WORLD_HEIGHT = 3200
FIELDS = {
    'object': [('x', 'f'), ('z', 'f'), ('yaw', 'f'), ('health', 'f'), ('health_max', 'f'), ('daytimer', 'i')],
    'unit': [(n, 'f') for n in ('x', 'y', 'z', 'yaw', 'health', 'health_max', 'hunger', 'thirst', 'exhaustion', 'ai_x', 'ai_z')],
    'item': [('x', 'f'), ('y', 'f'), ('z', 'f'), ('yaw', 'f'), ('health', 'f'), ('count', 'i'),
             ('parent_class', 'B'), ('parent_mode', 'B'), ('parent_id', 'i')],
    'info': [(n, 'f') for n in ('x', 'y', 'z', 'pitch', 'yaw')],
}


def number(value, name='number', minimum=None, maximum=None, integer=False):
    if isinstance(value, bool) or not isinstance(value, (int, float)) or not math.isfinite(value):
        raise ValueError(f'{name} must be a finite number')
    if integer and int(value) != value:
        raise ValueError(f'{name} must be an integer')
    if minimum is not None and value < minimum or maximum is not None and value > maximum:
        raise ValueError(f'{name} must be between {minimum} and {maximum}')
    return int(value) if integer else float(value)


def string(value):
    raw = value.encode('utf8') if isinstance(value, str) else value
    return struct.pack('<i', len(raw)) + raw


class EditableMap:
    def __init__(self, data):
        try:
            self.source = Map(data)
        except (ValueError, struct.error) as exc:
            raise ValueError(f'Invalid or unsupported .s2 map: {exc}') from exc
        if self.source.headers[5] != b'map':
            raise ValueError('Studio edits maps only; player saves are not authoring inputs')
        self.data = data
        self.fields = {f.key: f for f in self.source.fields}
        header_end = 0
        for _ in range(12):
            header_end = data.index(b'\n',header_end)+1
        password_start = header_end + 96*72*3 + 1
        self.protected = bool(data[password_start:data.index(b'\n',password_start)].rstrip(b'\r'))
        self.pos = self.fields['quickslot/9'].end
        self.color_size = self.read('i')
        self.colors_start = self.pos
        self.pos += self.color_size ** 2 * 3
        self.size = self.read('i')
        if self.size not in (16, 32, 64, 128, 256, 512, 1024, 2048):
            raise ValueError(f'Unsupported terrain size: {self.size}')
        self.heights_start = self.pos
        self.pos += (self.size + 1) ** 2 * 4 + (self.color_size + 1) ** 2
        self.sections = {}
        self.entities = []
        formats = (self.source.headers[6] + b'000')[:3]
        for index, kind in enumerate(CLASSES):
            start = self.pos
            count = self.read('i')
            records = []
            type_format = 'B' if kind == 'info' or formats[index] == ord('0') else 'H'
            for _ in range(count):
                begin = self.pos
                entity = {'kind': kind, 'id': self.read('i'), 'type': self.read(type_format)}
                offsets = {}
                for name, code in FIELDS[kind]:
                    offsets[name] = (self.pos, code)
                    entity[name] = self.read(code)
                if kind == 'info':
                    length = self.read('i')
                    entity['vars'] = data[self.pos:self.pos + length].decode('utf8')
                    self.pos += length
                records.append((begin, self.pos, entity, offsets))
                self.entities.append(entity)
            self.sections[kind] = (start, self.pos, records, type_format)
        self.references = []
        for _ in range(self.read('i')):
            self.read('B')
            parent = self.read('B'), self.read('i')
            self.pos += 32
            length = self.read('i')
            self.pos += length
            self.references.append(parent)
        self.extension_start = self.pos
        self.extensions = []
        for _ in range(self.read('i')):
            begin = self.pos
            self.read('B')
            parent = self.read('B'), self.read('i')
            mode = self.read('i')
            values = []
            for _ in range(3):
                length = self.read('i')
                values.append(self.data[self.pos:self.pos+length])
                self.pos += length
            self.extensions.append((begin, self.pos, parent, mode, values))
            self.references.append(parent)
        self.extension_end = self.pos

    def read(self, code):
        result = struct.unpack_from('<' + code, self.data, self.pos)[0]
        self.pos += struct.calcsize('<' + code)
        return result

    def entity(self, kind, identifier):
        for record in self.sections[kind][2]:
            if record[2]['id'] == identifier:
                return record
        raise ValueError(f'Unknown entity: {kind}/{identifier}')

    def height(self, gx, gz):
        gx, gz = max(0, min(self.size, gx)), max(0, min(self.size, gz))
        return struct.unpack_from('<f', self.data, self.heights_start + (gx * (self.size + 1) + gz) * 4)[0]

    def world_height(self, x, z):
        gx, gz = x / WORLD_SIZE + self.size / 2, z / WORLD_SIZE + self.size / 2
        ix, iz = math.floor(gx), math.floor(gz)
        ax, az = gx - ix, gz - iz
        def sample(gx, gz):
            return self.height(gx,gz) if 0 <= gx <= self.size and 0 <= gz <= self.size else 0
        h = ((sample(ix, iz) * (1-ax) + sample(ix+1, iz) * ax) * (1-az)
             + (sample(ix, iz+1) * (1-ax) + sample(ix+1, iz+1) * ax) * az)
        # Match Blitz TerrainY and e_tery's ground-plane floor, including edges.
        return (max(h, self.height(0,0)) - .5) * WORLD_HEIGHT

    def summary(self):
        script = self.fields['global_script'].data.decode('utf8').replace('\x1f', '\n')
        return {'size': self.size, 'worldSize': self.size * WORLD_SIZE,
                'entities': [dict(entity, script=next((e[4][1].decode('utf8').replace('\x1f','\n') for e in self.extensions
                     if e[2] == (CLASSES[entity['kind']],entity['id']) and e[3] == 0), '')) for entity in self.entities], 'script': script,
                'attachments': [f.key for f in self.source.fields if f.kind == 'attachment']}

    def apply(self, operation):
        op = operation['op']
        if op == 'map.script':
            script = operation['text']
            if not isinstance(script, str):
                raise ValueError('Script must be text')
            return self.source.write({'global_script': script.replace('\r\n', '\n').replace('\n', '\x1f').encode('utf8')})
        if op == 'entity.script':
            self.entity(operation['kind'], operation['id'])
            parent = (CLASSES[operation['kind']],operation['id'])
            if not isinstance(operation['text'],str):
                raise ValueError('Script must be text')
            script = operation['text'].replace('\r\n', '\n').replace('\n', '\x1f')
            matches = [e for e in self.extensions if e[2] == parent and e[3] == 0]
            if len(matches) > 1:
                raise ValueError('Multiple attached scripts; preserve this map and edit its scripts individually')
            if matches:
                begin, end, _, _, values = matches[0]
                if not script and not values[2]:
                    return (self.data[:self.extension_start] + struct.pack('<i',len(self.extensions)-1)
                            + self.data[self.extension_start+4:begin] + self.data[end:])
                return self.data[:begin+10] + string(values[0]) + string(script) + string(values[2]) + self.data[end:]
            if not script:
                return self.data
            record = struct.pack('<BBii', 0,parent[0],parent[1],0) + string('') + string(script) + string('')
            return (self.data[:self.extension_start] + struct.pack('<i', len(self.extensions)+1)
                    + self.data[self.extension_start+4:self.extension_end] + record + self.data[self.extension_end:])
        if op == 'entities.move':
            delta = operation['delta']
            dx, dy, dz = (number(delta.get(axis, 0), axis) for axis in ('x','y','z'))
            selected = operation['selection']
            if not selected or len({(e['kind'], e['id']) for e in selected}) != len(selected):
                raise ValueError('Move requires distinct selected entities')
            entities = [self.entity(e['kind'], e['id'])[2] for e in selected]
            if dy and any(e['kind'] == 'object' for e in entities):
                raise ValueError('Objects do not store Y; group vertical movement is unavailable')
            out = self.data
            for e in entities:
                x, z = e['x']+dx, e['z']+dz
                values = {'x':x, 'z':z}
                if e['kind'] != 'object':
                    values['y'] = e['y']+dy
                    if operation.get('terrain'):
                        values['y'] += self.world_height(x,z)-self.world_height(e['x'],e['z'])
                out = EditableMap(out).apply({'op':'entity.set','kind':e['kind'],'id':e['id'],'values':values})
            return out
        if op == 'terrain.brush':
            x = number(operation['x'], 'x', 0, self.size)
            z = number(operation['z'], 'z', 0, self.size)
            radius = number(operation.get('radius', 2), 'radius', .1, self.size)
            strength = number(operation.get('strength', .005), 'strength', -1, 1)
            mode = operation.get('mode', 'raise')
            if mode not in ('raise', 'lower', 'flatten', 'smooth'):
                raise ValueError('Brush mode: raise, lower, flatten or smooth')
            level = number(operation.get('height', self.height(round(x), round(z))), 'height', 0, 1)
            out = bytearray(self.data)
            for gx in range(max(0, math.floor(x-radius)), min(self.size, math.ceil(x+radius))+1):
                for gz in range(max(0, math.floor(z-radius)), min(self.size, math.ceil(z+radius))+1):
                    weight = max(0, 1 - math.hypot(gx-x, gz-z) / radius)
                    if weight == 0:
                        continue
                    h = self.height(gx, gz)
                    if mode == 'flatten':
                        h += (level - h) * weight
                    elif mode == 'smooth':
                        average = sum(self.height(gx+dx, gz+dz) for dx in (-1,0,1) for dz in (-1,0,1))/9
                        h += (average-h) * weight
                    else:
                        h += abs(strength) * weight * (1 if mode == 'raise' else -1)
                    struct.pack_into('<f', out, self.heights_start+(gx*(self.size+1)+gz)*4, min(1, max(0, h)))
            return bytes(out)
        if not op.startswith('entity.'):
            raise ValueError(f'Unsupported map operation: {op}')
        kind = operation['kind']
        if kind not in CLASSES:
            raise ValueError('Entity kind must be object, unit, item or info')
        start, end, records, type_format = self.sections[kind]
        if op == 'entity.add':
            identifier = operation.get('id', max([r[2]['id'] for r in records] + [99 if kind == 'unit' else 0])+1)
            identifier = number(identifier, 'id', 100 if kind == 'unit' else 1, 2147483647, True)
            if any(r[2]['id'] == identifier for r in records):
                raise ValueError(f'Duplicate entity ID: {kind}/{identifier}')
            typ = number(operation['type'], 'type', 1, 255 if type_format == 'B' else 65535, True)
            values = dict(operation.get('values', {}))
            defaults = {'x': 0, 'z': 0, 'yaw': 0, 'health': 100, 'health_max': 100, 'count': 1}
            defaults.update(values)
            defaults.setdefault('y', self.world_height(defaults['x'], defaults['z']))
            self.validate_values(kind, values)
            raw = struct.pack('<i' + type_format, identifier, typ)
            for name, code in FIELDS[kind]:
                value = defaults.get(name, 0)
                number(value, name, integer=code != 'f')
                raw += struct.pack('<' + code, value)
            if kind == 'info':
                raw += string(values.get('vars', ''))
            return self.data[:start] + struct.pack('<i', len(records)+1) + self.data[start+4:end] + raw + self.data[end:]
        record = self.entity(kind, operation['id'])
        begin, finish, entity, offsets = record
        if op == 'entity.remove':
            if kind == 'unit' and entity['id'] < 100:
                raise ValueError('Reserved player entities cannot be removed')
            parent = (CLASSES[kind], entity['id'])
            if parent in self.references or any((e.get('parent_class'), e.get('parent_id')) == parent for e in self.entities):
                raise ValueError('Entity has attached scripts, states or stored items; detach them before removal')
            return self.data[:start] + struct.pack('<i', len(records)-1) + self.data[start+4:begin] + self.data[finish:]
        if op != 'entity.set':
            raise ValueError(f'Unsupported map operation: {op}')
        values = operation['values']
        self.validate_values(kind, values)
        out = bytearray(self.data)
        for name, value in values.items():
            if name == 'vars':
                continue
            pos, code = offsets[name]
            struct.pack_into('<' + code, out, pos, value)
        if 'vars' in values:
            pos = begin + 25
            return bytes(out[:pos]) + string(values['vars']) + bytes(out[finish:])
        return bytes(out)

    @staticmethod
    def validate_values(kind, values):
        allowed = dict(FIELDS[kind])
        if kind == 'info':
            allowed['vars'] = 'text'
        for key, value in values.items():
            if key not in allowed:
                raise ValueError(f'{kind} maps do not store {key}; editable properties: {", ".join(allowed)}')
            if allowed[key] == 'text':
                if not isinstance(value, str):
                    raise ValueError('vars must be text')
            else:
                number(value, key, -3.4e38 if allowed[key]=='f' else -2147483648,
                       3.4e38 if allowed[key]=='f' else 2147483647, integer=allowed[key] != 'f')
                if key in ('health', 'health_max', 'count', 'parent_id', 'parent_class', 'parent_mode') and value < 0:
                    raise ValueError(f'{key} cannot be negative')
                if key == 'count' and value < 1:
                    raise ValueError('count must be positive')
                if allowed[key] == 'B' and value > 255:
                    raise ValueError(f'{key} exceeds 255')


def new_map(size=32):
    size = number(size, 'size', 16, 256, True)
    if size not in (16,32,64,128,256):
        raise ValueError('New map size must be 16, 32, 64, 128 or 256')
    headers = ['### Stranded II Mapfile ©by Unreal Software 2004-2007', '1.0.0.1', '', '', 'default', 'map', '111', '', '', '', '', '###']
    data = bytearray(('\r\n'.join(headers)+'\r\n').encode('utf8'))
    data += bytes(96*72*3) + b'\x05\r\n'
    data += struct.pack('<iBBB', 1, 9, 0, 0) + string('sky') + b'\0\0' + string('amb_jungle.mp3') + string('')
    data += bytes((205,210,245,0,0)) + string('') * 10
    color_size = 128
    data += struct.pack('<i', color_size) + bytes((100,135,72))*(color_size**2)
    data += struct.pack('<i', size)
    for x in range(size+1):
        for z in range(size+1):
            distance = math.hypot(x-size/2, z-size/2)/(size*.43)
            data += struct.pack('<f', .45 + .10*max(0, min(1, (1-distance)*4)))
    data += bytes((color_size+1)**2)
    data += struct.pack('<iii', 0,0,0)  # objects, units, items
    # Player start (info type 1), required for a playable new map.
    data += struct.pack('<iiBfffff', 1, 1, 1, 0, 160, 0, 0, 0) + string('')
    data += struct.pack('<ii', 0,0)  # states, extensions
    data += b'\r\n###\r\nwww.unrealsoftware.de\r\n'
    EditableMap(bytes(data))
    return bytes(data)
