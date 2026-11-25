"""Reuse game bitmap-font generation when a project introduces new characters."""
from contextlib import redirect_stdout
from pathlib import Path
import hashlib
import json
import shutil
import struct
import sys
import tempfile


def map_literals(model):
    return '\n'.join(field.data.decode('utf8') for field in model.source.fields if field.kind != 'attachment')


def base_glyphs(project):
    glyphfile = project.base/'core/locales/ko/glyphs.dat'
    if not glyphfile.is_file():
        return None  # CLI-only projects can use a minimal prepared base.
    return {code for (code,) in struct.iter_unpack('<i24x', glyphfile.read_bytes()[16:])}


def extra_map_glyphs(project, model):
    from fonts import repertoire
    known = base_glyphs(project)
    if known is None:
        return []
    return sorted(repertoire({'en': {'map': map_literals(model)}}, {}).keys() - known)


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
        literals += map_literals(EditableMap(project.map_bytes(name)))
    catalogs['en']['studio.literal-characters'] = literals
    chars = sorted(repertoire(catalogs, packs))
    known = base_glyphs(project)
    if known is None or set(chars) <= known:
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
