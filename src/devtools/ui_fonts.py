"""Prepare the pinned Unicode UI font without changing game bitmap fonts."""
from pathlib import Path
import hashlib
import json
import shutil
import tempfile
import urllib.request


def digest(path):
    with path.open('rb') as stream:
        return hashlib.file_digest(stream, 'sha256').hexdigest()


def prepare(root):
    spec = json.loads((root / 'config/blitz3d-toolchain.json').read_text('utf8'))['ui_fonts']['pretendard']
    license = root / spec['license']
    if digest(license) != spec['license_sha256']:
        raise ValueError('Pretendard license checksum mismatch')
    folder = root / 'deps/fonts/ui'
    folder.mkdir(parents=True, exist_ok=True)
    target = folder / spec['filename']
    # The filename stays the same across pin updates; replace an outdated copy.
    if not target.exists() or digest(target) != spec['sha256']:
        with tempfile.TemporaryDirectory(prefix='.ui-font-', dir=folder) as temporary:
            pending = Path(temporary) / target.name
            print('Downloading UI font:', spec['url'], flush=True)
            with urllib.request.urlopen(spec['url'], timeout=120) as source, pending.open('wb') as output:
                shutil.copyfileobj(source, output)
            if digest(pending) != spec['sha256']:
                raise ValueError('UI font checksum mismatch')
            pending.replace(target)
    return target
