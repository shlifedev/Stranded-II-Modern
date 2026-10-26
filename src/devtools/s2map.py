"""Lossless Stranded II 1.0.0.1 map reader, based on e_load_map.bb.

Unchanged bytes are retained verbatim. Only explicitly edited strings/attachments
are serialized, with their byte lengths recomputed.
"""
from dataclasses import dataclass
import struct


@dataclass
class Field:
    key: str
    start: int
    end: int
    data: bytes
    kind: str = 'string'


class Map:
    def __init__(self, data):
        self.data = data
        self.pos = 0
        self.fields = []
        self.headers = [self.line() for _ in range(12)]
        assert self.headers[0].startswith(b'### Stranded II'), 'Bad map header'
        mode = self.headers[5]
        formats = (self.headers[6] + b'000')[:3]
        self.skip(96 * 72 * 3)
        self.skip(1)
        self.line()  # encoded password
        self.skip(7)
        self.string('skybox')
        self.skip(2)
        self.string('music')
        self.string('global_script')
        self.skip(5)
        for i in range(10):
            self.string(f'quickslot/{i}')
        color_size = self.integer()
        self.skip(color_size * color_size * 3)
        terrain_size = self.integer()
        self.skip((max(terrain_size, 0) + 1) ** 2 * 4)
        self.skip((color_size + 1) ** 2)
        for i, kind in enumerate(('object', 'unit', 'item')):
            count = self.integer()
            assert 0 <= count < 100000, (kind, count)
            type_size = 2 if formats[i] != ord('0') else 1
            stride = {'object': 28, 'unit': 48, 'item': 34}[kind] + type_size
            self.skip(stride * count)
        for i in range(self.integer()):
            object_id = self.integer()
            info_type = self.byte()
            self.skip(20)
            self.string(f'info/{object_id}/type{info_type}/vars')
        for i in range(self.integer()):
            self.skip(38)
            self.string(f'state/{i}/value')
        for i in range(self.integer()):
            typ, parent_class = self.byte(), self.byte()
            parent_id, ext_mode = self.integer(), self.integer()
            prefix = f'extension/{i}/type{typ}/parent{parent_class}-{parent_id}/mode{ext_mode}'
            for kind in ('key', 'value', 'stuff'):
                self.string(f'{prefix}/{kind}')
        if mode == b'sav':
            self.skip(8)
        trailer = [self.line() for _ in range(3)]
        assert trailer[-1] == b'www.unrealsoftware.de', (self.pos, trailer)
        while self.pos < len(data):
            start = self.pos
            name_raw = self.line()
            if not name_raw and self.pos == len(data):
                break
            name = bytes(255 - b for b in name_raw).decode('cp1252')
            body_start = self.pos
            length = self.integer()
            content = bytes(255 - b for b in self.take(length))
            self.fields.append(Field('attachment/' + name, body_start, self.pos, content, 'attachment'))
        assert self.pos == len(data)

    def take(self, count):
        assert count >= 0 and self.pos + count <= len(self.data), (self.pos, count, len(self.data))
        result = self.data[self.pos:self.pos + count]
        self.pos += count
        return result

    def skip(self, count):
        self.take(count)

    def byte(self):
        return self.take(1)[0]

    def integer(self):
        return struct.unpack('<i', self.take(4))[0]

    def line(self):
        end = self.data.find(b'\n', self.pos)
        if end < 0:
            end = len(self.data) - 1
        return self.take(end + 1 - self.pos).rstrip(b'\r\n')

    def string(self, key):
        start = self.pos
        content = self.take(self.integer())
        self.fields.append(Field(key, start, self.pos, content))

    def write(self, changes=None):
        changes = changes or {}
        known = {f.key for f in self.fields}
        assert set(changes) <= known, set(changes) - known
        out, pos = bytearray(), 0
        for field in self.fields:
            out.extend(self.data[pos:field.start])
            payload = changes.get(field.key, field.data)
            if field.kind == 'attachment':
                payload = bytes(255 - b for b in payload)
            out.extend(struct.pack('<i', len(payload)))
            out.extend(payload)
            pos = field.end
        out.extend(self.data[pos:])
        return bytes(out)


if __name__ == '__main__':
    from pathlib import Path
    import json
    summary = []
    for path in Path('mods/Stranded II/maps').rglob('*.s2'):
        data = path.read_bytes()
        model = Map(data)
        assert model.write() == data, path
        summary.append({'map': str(path), 'text_fields': len(model.fields), 'text_bytes': sum(len(f.data) for f in model.fields)})
    print(json.dumps(summary, indent=2))
