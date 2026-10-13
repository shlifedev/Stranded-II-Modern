"""Reuse game bitmap-font generation when a project introduces new characters."""
from contextlib import redirect_stdout
from pathlib import Path
import hashlib
import json
import shutil
import struct
import sys
import tempfile


def project_fonts(project):
    from fonts import make_fonts, repertoire
    from locales import languages
    from .project import ROOT, encode, safe
    packs = languages(ROOT)
    catalogs = {code: project.translations(code) for code in packs}
    from .scripts import resources
    literals = '\n'.join(resources(project).values())
    for name in project.data['maps']:
        from .maps import EditableMap
        literals += '\n'.join(f.data.decode('utf8') for f in EditableMap(project.map_bytes(name)).source.fields if f.kind != 'attachment')
    catalogs['en']['studio.literal-characters'] = literals
    chars = sorted(repertoire(catalogs, packs))
    glyphfile = project.base/'core/locales/ko/glyphs.dat'
    if not glyphfile.is_file():
        return {}  # CLI-only projects can use a minimal prepared base.
    data = glyphfile.read_bytes()
    known = {struct.unpack_from('<i',data,offset)[0] for offset in range(16,len(data),28)}
    if set(chars) <= known:
        return {}
    report = json.loads((project.base/'core/locales/fonts-report.json').read_text('utf8'))
    font = Path(report['ko']['font'])
    if not font.is_file():
        raise ValueError('New project characters require the preparation font. Run Studio engine setup with --font.')
    fingerprint = hashlib.sha256(encode(chars)+font.read_bytes()).hexdigest()
    cache = safe(project.root,'.studio/fonts/'+fingerprint)
    if not cache.is_dir():
        cache.parent.mkdir(parents=True,exist_ok=True)
        stage = Path(tempfile.mkdtemp(prefix='stage-',dir=cache.parent))
        try:
            with redirect_stdout(sys.stderr):
                make_fonts(catalogs,packs,font,project.base/'mods/Stranded II/sys/gfx',stage,ROOT)
            try: stage.rename(cache)
            except FileExistsError: pass  # Another reader prepared the same glyphs.
        finally:
            if stage.exists():shutil.rmtree(stage)
    return {p.relative_to(cache).as_posix():p.read_bytes() for p in cache.rglob('*')
            if p.is_file() and p.suffix in ('.dat','.bmp')}
