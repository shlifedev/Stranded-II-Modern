"""Read-only content icons, independent of maps and the Blitz viewport."""
import base64
from collections import OrderedDict
from hashlib import sha256
from io import BytesIO
from pathlib import PurePosixPath

from PIL import Image, ImageChops

from .project import MOD, resource_path, safe

MAX_BYTES = 16 * 1024 * 1024
MAX_PIXELS = 4096 * 4096
_cache = OrderedDict()


class Unavailable(ValueError):
    pass


def _read(project, relative):
    resource_path(relative)
    if relative in project.data['assets']:
        path = safe(project.root, project.data['assets'][relative])
    else:
        path = safe(project.base, relative)
    if path.stat().st_size > MAX_BYTES:
        raise Unavailable('image-too-large')
    with path.open('rb') as stream:
        raw = stream.read(MAX_BYTES + 1)
    if len(raw) > MAX_BYTES:
        raise Unavailable('image-too-large')
    return raw


def _image_source(project, record):
    props = record.properties
    if record.kind == 'building':
        for field, kind in (('objectid', 'object'), ('unitid', 'unit')):
            if props.get(field):
                try:
                    return _image_source(project, project.find(kind, props[field][-1].strip()))
                except ValueError as exc:
                    if isinstance(exc, Unavailable):
                        raise
                    raise Unavailable('missing-reference') from exc
        raise Unavailable('no-icon')
    if record.kind == 'recipe':
        if not props.get('gen'):
            raise Unavailable('no-icon')
        try:
            result = project.find('item', props['gen'][0].split(',')[0].strip())
        except ValueError as exc:
            raise Unavailable('missing-reference') from exc
        return _image_source(project, result)
    if record.kind == 'info':
        try:
            frame = int(props.get('frame', ['0'])[-1])
        except ValueError as exc:
            raise Unavailable('invalid-frame') from exc
        if not 0 <= frame < 100:
            raise Unavailable('invalid-frame')
        return MOD + 'sys/gfx/icons.bmp', frame, (255, 0, 255)
    icon = props.get('icon', [''])[-1].strip()
    if not icon:
        raise Unavailable('no-icon')
    return MOD + icon.replace('\\', '/'), None, (0, 0, 0)


def _decode(raw, frame, mask):
    key = (sha256(raw).digest(), frame, mask)
    if key in _cache:
        _cache.move_to_end(key)
        return _cache[key]
    with Image.open(BytesIO(raw)) as original:
        if original.width * original.height > MAX_PIXELS:
            raise Unavailable('image-too-large')
        image = original.convert('RGBA')
    if frame is not None:
        columns = image.width // 32
        if columns == 0 or frame >= columns * (image.height // 32):
            raise Unavailable('invalid-frame')
        x, y = frame % columns * 32, frame // columns * 32
        image = image.crop((x, y, x + 32, y + 32))
    width, height = image.size
    # DrawImage uses the canvas RGB key even when load_res(..., mode=0)
    # skips MaskImage: its initial key is black. DrawImage copies RGB and
    # replaces source alpha with opacity determined by that key alone.
    r, g, b = ImageChops.difference(image.convert('RGB'), Image.new('RGB', image.size, mask)).split()
    visible = ImageChops.lighter(ImageChops.lighter(r, g), b).point([0] + [255] * 255)
    image.putalpha(visible)
    image.thumbnail((160, 160), Image.Resampling.LANCZOS)
    output = BytesIO()
    image.save(output, format='PNG')
    result = {'width': width, 'height': height,
              'dataUrl': 'data:image/png;base64,' + base64.b64encode(output.getvalue()).decode('ascii')}
    _cache[key] = result
    if len(_cache) > 256:
        _cache.popitem(last=False)
    return result


def thumbnail(project, kind, identifier):
    record = project.find(kind, identifier)
    result = {'revision': project.data['revision'], 'version': project.thumbnail_version(), 'status': 'unavailable',
              'additionalResults': max(0, len(record.properties.get('gen', [])) - 1) if kind == 'recipe' else 0}
    try:
        relative, frame, mask = _image_source(project, record)
        # Like load_res, follow a single sibling .inf resource link when absent.
        try:
            raw = _read(project, relative)
        except FileNotFoundError:
            link = str(PurePosixPath(relative).with_suffix('.inf'))
            text = project.data['files'].get(link)
            if text is None:
                text = _read(project, link).decode('utf8')
            lines = text.splitlines()
            if not lines or not lines[0].strip():
                raise Unavailable('missing-file')
            relative = str(PurePosixPath(link).parent) + '/' + lines[0].strip().replace('\\', '/')
            raw = _read(project, relative)
        result.update(_decode(raw, frame, mask), status='ready', source=relative)
    except Unavailable as exc:
        result['reason'] = str(exc)
    except FileNotFoundError:
        result['reason'] = 'missing-file'
    except (Image.DecompressionBombError, Image.DecompressionBombWarning):
        result['reason'] = 'image-too-large'
    except (OSError, ValueError):
        result['reason'] = 'unreadable-image'
    return result
