"""Font/publish regression fixtures, isolated from player settings and catalogs."""
from pathlib import Path
import argparse, hashlib, json, shutil, struct
from unittest.mock import patch
from fontTools.fontBuilder import FontBuilder
from fontTools.pens.ttGlyphPen import TTGlyphPen
from fontTools.pens.t2CharStringPen import T2CharStringPen
from fonts import Font
from language_pack import refresh
from locales import languages, validate


def fixture_font(path,codes,cff=False):
    builder=FontBuilder(1000,isTTF=not cff)
    names=['.notdef']+[f'u{c:04X}' for c in codes]
    builder.setupGlyphOrder(names);builder.setupCharacterMap(dict(zip(codes,names[1:])))
    glyphs={}
    for name in names:
        pen=T2CharStringPen(1400,None) if cff else TTGlyphPen(None)
        if name!='u0020':
            pen.moveTo((-100,0));pen.lineTo((1200,0));pen.lineTo((600,700));pen.closePath()
        glyphs[name]=pen.getCharString() if cff else pen.glyph()
    if cff:builder.setupCFF('StrandedFontFixture',{'FullName':'Stranded Font Fixture'},glyphs,{})
    else:builder.setupGlyf(glyphs)
    builder.setupHorizontalMetrics({name:(400 if name=='u0020' else 1400,-100) for name in names})
    builder.setupHorizontalHeader(ascent=800,descent=-200)
    builder.setupNameTable({'familyName':'Stranded Font Fixture','styleName':'Regular',
                           'uniqueFontIdentifier':'StrandedFixture','fullName':'Stranded Font Fixture',
                           'psName':'StrandedFontFixture'})
    builder.setupOS2(sTypoAscender=800,sTypoDescender=-200,usWinAscent=800,usWinDescent=200)
    builder.setupPost();builder.save(path)


def digest(folder):
    return {p.relative_to(folder).as_posix():hashlib.sha256(p.read_bytes()).hexdigest()
            for p in folder.rglob('*') if p.is_file()}


def main():
    ap=argparse.ArgumentParser();ap.add_argument('--output',type=Path,required=True);ap.add_argument('--runtime',type=Path);args=ap.parse_args()
    root=Path(__file__).resolve().parents[2];out=args.output.resolve();out.mkdir(parents=True)
    deps=json.loads((root/'build/dependencies.local.json').read_text('utf8'));default=Path(deps['font'])
    assert Font(default).axes,'default exercises variable font path'
    fixture=out/'project';locale=fixture/'resources/locales';locale.mkdir(parents=True)
    # The isolated project exercises the same pinned UI-font publication as setup.
    from ui_fonts import prepare as prepare_ui_font
    ui_font=prepare_ui_font(root)
    (fixture/'config').mkdir()
    shutil.copy2(root/'config/blitz3d-toolchain.json',fixture/'config/blitz3d-toolchain.json')
    (fixture/'deps/fonts/ui').mkdir(parents=True)
    shutil.copy2(ui_font,fixture/'deps/fonts/ui'/ui_font.name)
    (fixture/'licenses').mkdir()
    for name in ('Pretendard-OFL.txt','Proggy-fonts.txt'):
        shutil.copy2(root/'licenses'/name,fixture/'licenses'/name)
    runtime=out/'runtime';gfx=runtime/'mods/Stranded II/sys/gfx';gfx.mkdir(parents=True)
    for p in ((args.runtime or root/'build/runtime')/'mods/Stranded II/sys/gfx').glob('font_*.bmp'):shutil.copy2(p,gfx/p.name)
    latin=fixture/'Latin.ttf';other=fixture/'Other.otf'
    fixture_font(latin,list(range(32,127)))
    # A private-use character missing in Noto must come from another pack's OTF.
    private=next(c for c in range(0xE000,0xF900) if c not in Font(default).coverage)
    fixture_font(other,list(range(32,127))+[private],cff=True)
    assert not Font(latin).axes and not Font(other).axes
    packs={'ko':{'name':'한국어','font':'Latin.ttf'},'en':{'name':'English'},
           'ja':{'name':'日本語','font':'Other.otf'}}
    for code,info in packs.items():(locale/f'{code}.json').write_text(json.dumps(info,ensure_ascii=False),encoding='utf8')
    (locale/'en.tsv').write_text('ui.language\tLanguage\ntest.text\tSaved text\n',encoding='utf8')
    (locale/'ko.tsv').write_text('ui.language\t언어\ntest.text\t한글\n',encoding='utf8')
    (locale/'ja.tsv').write_text('ui.language\t言語'+chr(private)+'\n',encoding='utf8')
    assert list(languages(fixture))==['ko','en','ja']
    assert len(validate(fixture)['ja'])==1
    report=refresh(fixture,runtime,default)
    assert report['fallback_keys']['ja']==1
    generated=runtime/'core/locales';fonts=json.loads((generated/'fonts-report.json').read_text('utf8'))
    assert (generated/'ko/ui.ttf').read_bytes()==ui_font.read_bytes()
    assert (generated/'ko/fallback.ttf').read_bytes()==latin.read_bytes()
    assert (generated/'ko/fallback-extra.ttf').read_bytes()==default.read_bytes()
    assert fonts['ko']['fallback_glyphs']['U+D55C']['font']==str(default.resolve())
    assert fonts['ko']['fallback_glyphs'][f'U+{private:04X}']['font']==str(other.resolve())
    assert fonts['ko']['fallback_count']>0
    assert 'U+0041' not in fonts['ko']['fallback_glyphs'],'Latin uses chosen font'
    data=(generated/'ko/glyphs.dat').read_bytes();_,count,cellw,cellh=struct.unpack_from('<4sIII',data)
    glyphs={row[0]:row[1:] for row in struct.iter_unpack('<iiiiiii',data[16:])}
    assert len(glyphs)==count and glyphs[ord('A')][0]>19,'wide advances are not capped'
    assert glyphs[ord('A')][2]<0,'negative bearing is preserved'
    assert glyphs[32][0]<glyphs[ord('A')][0],'font-specific space width'
    before=digest(generated)
    def fails(message):
        try:refresh(fixture,runtime,default)
        except (ValueError,OSError) as exc:assert message in str(exc),str(exc)
        else:raise AssertionError('expected generation/publish failure')
        assert digest(generated)==before,'previous working pack was changed'
    packs['ko']['font']='missing.ttf'
    (locale/'ko.json').write_text(json.dumps(packs['ko']),encoding='utf8');fails('missing.ttf')
    packs['ko']['font']='Latin.ttf'
    (locale/'ko.json').write_text(json.dumps(packs['ko']),encoding='utf8')
    with (locale/'ja.tsv').open('a',encoding='utf8') as stream:stream.write('test.text\t\u0378\n')
    fails('U+0378')
    (locale/'ja.tsv').write_text('ui.language\t言語'+chr(private)+'\n',encoding='utf8')
    # Second rename fails after the old pack was moved; rollback must restore it.
    import os
    real_replace=os.replace
    def rename(source,target):
        if Path(source).name.startswith('.locales-stage-') and not str(source).endswith('-previous'):
            raise PermissionError('publish fixture locked')
        return real_replace(source,target)
    with patch('language_pack.os.replace',side_effect=rename):fails('publish fixture locked')
    assert not list((runtime/'core').glob('.locales-*'))
    (out/'report.json').write_text(json.dumps({'static_ttf':'pass','static_otf':'pass','variable_ttf':'pass',
        'glyph_fallback_and_full_latin_font':'pass','bearing_and_advance':'pass',
        'missing_glyph_diagnostic':'pass','failed_generation_and_publish_preserve_pack':'pass'},indent=2)+'\n',encoding='utf8')
    print('Language/font fixtures passed:',out)


if __name__=='__main__':main()
