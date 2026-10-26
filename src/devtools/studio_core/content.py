"""Lossless INF record editing, including repeated properties and script blocks."""
from dataclasses import dataclass
import re

PREFIXES = {'item': 'items', 'object': 'objects', 'unit': 'units', 'building': 'buildings', 'recipe': 'combinations', 'info': 'infos'}
PROPERTY = re.compile(r'^([a-zA-Z_][a-zA-Z_0-9]*)\s*=(.*)$')


@dataclass
class Record:
    kind: str
    identifier: object
    path: str
    start: int
    end: int
    text: str
    properties: dict

    def public(self, localize=lambda s: s):
        name = self.properties.get('name', [f'{self.kind} {self.identifier}'])[0]
        return {'kind': self.kind, 'id': self.identifier, 'path': self.path,
                'name': localize(name), 'properties': self.properties}


def fields(text):
    """Yield property spans. A script's contents can contain arbitrary key=value."""
    lines = text.splitlines(keepends=True)
    offset = 0
    index = 0
    while index < len(lines):
        line = lines[index]
        match = PROPERTY.match(line.strip())
        if match and not line.lstrip().startswith('#'):
            key, value = match[1], match[2].strip()
            if key in ('script','description') and value == 'start':
                start = offset
                content = []
                offset += len(line)
                index += 1
                while index < len(lines) and lines[index].strip() != key+'=end':
                    content.append(lines[index])
                    offset += len(lines[index])
                    index += 1
                if index == len(lines):
                    raise ValueError(f'Unterminated {key}=start block')
                offset += len(lines[index])
                yield key, ''.join(content).rstrip('\r\n'), start, offset
                index += 1
                continue
            yield key, value, offset, offset+len(line)
        offset += len(line)
        index += 1


def records(path, text):
    filename = path.rsplit('/',1)[-1]
    kind = next((k for k, prefix in PREFIXES.items() if filename.startswith(prefix) and filename.endswith('.inf')), None)
    if not kind:
        return []
    parsed = list(fields(text))
    marker = 'combi' if kind == 'recipe' else 'id'
    starts = [entry for entry in parsed if entry[0] == marker and (kind != 'recipe' or entry[1] == 'start')]
    result = []
    for index, (_, value, start, _) in enumerate(starts):
        end = starts[index+1][2] if index+1 < len(starts) else len(text)
        if kind == 'recipe':
            finish = next((f for f in parsed if f[0] == 'combi' and f[1] == 'end' and start < f[2] < end), None)
            if finish is None:
                raise ValueError(f'{path}: unterminated combination')
            end = finish[3]
        try:
            identifier = f'{path}#{index}' if kind == 'recipe' else int(value)
        except ValueError as exc:
            raise ValueError(f'{path}: invalid {kind} ID {value!r}') from exc
        block = text[start:end]
        props = {}
        for key, val, _, _ in fields(block):
            if key not in ('id', 'combi'):
                props.setdefault(key, []).append(val)
        result.append(Record(kind, identifier, path, start, end, block, props))
    return result


def edit(text, updates):
    """Replace only named properties, retaining unknown fields/comments/order."""
    if not isinstance(updates, dict):
        raise ValueError('properties must be an object of property names to strings or lists')
    parsed = list(fields(text))
    replacements = []
    for key, value in updates.items():
        if not re.fullmatch(r'[a-z][a-z_0-9]*', key) or key == 'combi':
            raise ValueError(f'Invalid editable property: {key}')
        values = [] if value is None else value if isinstance(value, list) else [value]
        if key in ('script', 'description', 'id') and len(values) > 1:
            raise ValueError(f'{key} can occur only once')
        output = ''
        for val in values:
            if isinstance(val, bool) or not isinstance(val, (str, int, float)):
                raise ValueError(f'{key} must contain text or numbers')
            val = str(val)
            if '\0' in val or '\x1f' in val:
                raise ValueError(f'{key} contains a reserved control character')
            if key in ('script','description'):
                if any(line.strip() == key+'=end' for line in val.splitlines()):
                    raise ValueError(f'{key} contains its own end delimiter')
                output += key+'=start\n' + val.rstrip('\r\n') + '\n'+key+'=end\n'
            else:
                if '\n' in val or '\r' in val:
                    raise ValueError(f'{key} must be one line; use a list for repeated properties')
                output += f'{key}={val}\n'
        matches = [f for f in parsed if f[0] == key]
        if matches:
            replacements.extend((start, end, output if i == 0 else '') for i, (_, _, start, end) in enumerate(matches))
        elif output:
            end_marker = next((f[2] for f in parsed if f[0] == 'combi' and f[1] == 'end'), len(text))
            prefix = '\n' if end_marker and text[end_marker-1] != '\n' else ''
            replacements.append((end_marker, end_marker, prefix+output))
    for start, end, output in sorted(replacements, key=lambda r: (r[0],r[1]), reverse=True):
        text = text[:start] + output + text[end:]
    list(fields(text))
    return text


def new_record(kind, identifier, properties):
    head = 'combi=start\ncombi=end\n' if kind == 'recipe' else f'id={identifier}\n'
    return edit(head, properties)
