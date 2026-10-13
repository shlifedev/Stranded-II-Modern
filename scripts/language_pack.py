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
    from ui_fonts import prepare
    ui_font=prepare(root)
    packs=languages(root);catalogs=validate(root,edition)
    parent=runtime/'core';parent.mkdir(parents=True,exist_ok=True)
    stage=Path(tempfile.mkdtemp(prefix='.locales-stage-',dir=parent))
    target=parent/'locales';backup=stage.with_name(stage.name+'-previous')
    try:
        for code in packs:
            text=catalog_text(root,code,edition)
            (stage/f'{code}.tsv').write_bytes(text.encode('utf8'))
        fonts=make_fonts(catalogs,packs,default_font,runtime/'mods/Stranded II/sys/gfx',stage,root)
        # ImGui uses full Unicode fonts for dynamic text and native IME input.
        for code,info in packs.items():
            selected=root/info['font'] if info.get('font') else Path(default_font)
            folder=stage/code
            shutil.copy2(ui_font,folder/'ui.ttf')
            # Preserve a language's chosen font for glyphs absent from the UI font.
            shutil.copy2(selected,folder/'fallback.ttf')
            if selected.resolve()!=Path(default_font).resolve():
                shutil.copy2(default_font,folder/'fallback-extra.ttf')
        (stage/'languages.dat').write_bytes(registry(packs))
        (runtime/'licenses').mkdir(exist_ok=True)
        for name in ('Pretendard-OFL.txt','Proggy-fonts.txt'):
            shutil.copy2(root/'licenses'/name,runtime/'licenses'/name)
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
