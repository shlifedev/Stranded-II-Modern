"""Fetch checksum-pinned language fonts selected by registered language packs."""
from pathlib import Path
import json
import shutil
import tempfile
import urllib.request

from ui_fonts import digest


def prepare(root, packs):
    config = root / 'config/dependencies.json'
    specs = json.loads(config.read_text('utf8')).get('language_fonts', {}) if config.is_file() else {}
    selected = {info.get('font') for info in packs.values()}
    licenses = []
    for relative, spec in specs.items():
        if relative not in selected:
            continue
        notices = []
        for field in ('license', 'notice'):
            if field not in spec:
                continue
            license = root / spec[field]
            if digest(license) != spec[field + '_sha256']:
                raise ValueError(f'Language font license checksum mismatch: {license}')
            notices.append(license)
        target = root / relative
        target.parent.mkdir(parents=True, exist_ok=True)
        if not target.exists():
            with tempfile.TemporaryDirectory(prefix='.language-font-', dir=target.parent) as temporary:
                pending = Path(temporary) / target.name
                print('Downloading language font:', spec['url'], flush=True)
                with urllib.request.urlopen(spec['url'], timeout=120) as source, pending.open('wb') as output:
                    shutil.copyfileobj(source, output)
                if digest(pending) != spec['sha256']:
                    raise ValueError(f'Language font checksum mismatch: {target}')
                pending.replace(target)
        if digest(target) != spec['sha256']:
            raise ValueError(f'Language font checksum mismatch: {target}')
        licenses.extend(notices)
    return licenses
