"""Stage and publish catalogs, language registry and bitmap fonts together."""
from pathlib import Path
import os, shutil, struct, tempfile
from locales import languages, validate, catalog_text


def registry(packs):
    data=struct.pack('<i',len(packs))
    for code,info in packs.items():
        for value in (code,info['name']):
            encoded=value.encode('utf8');data+=struct.pack('<i',len(encoded))+encoded
    return data


def refresh(root,runtime,default_font,edition='classic'):
    from fonts import make_fonts
    packs=languages(root);catalogs=validate(root,edition)
    parent=runtime/'core';parent.mkdir(parents=True,exist_ok=True)
    stage=Path(tempfile.mkdtemp(prefix='.locales-stage-',dir=parent))
    target=parent/'locales';backup=stage.with_name(stage.name+'-previous')
    try:
        for code in packs:
            text=catalog_text(root,code,edition)
            (stage/f'{code}.tsv').write_bytes(text.encode('utf8'))
        fonts=make_fonts(catalogs,packs,default_font,runtime/'mods/Stranded II/sys/gfx',stage,root)
        (stage/'languages.dat').write_bytes(registry(packs))
        # Publish only after all generation succeeds; rollback on rename failure.
        if target.exists():os.replace(target,backup)
        try:os.replace(stage,target)
        except OSError:
            if backup.exists():os.replace(backup,target)
            raise
        if backup.exists():shutil.rmtree(backup)
    finally:
        if stage.exists():shutil.rmtree(stage)
    return {'catalogs':{c:len(v) for c,v in catalogs.items()},
            'fallback_keys':{c:len(catalogs['en'].keys()-v.keys()) for c,v in catalogs.items()},
            'fonts':{c:{k:v for k,v in f.items() if k!='fallback_glyphs'} for c,f in fonts.items()}}
