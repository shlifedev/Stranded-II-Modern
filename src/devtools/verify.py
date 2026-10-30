"""Actual Blitz localization/timing tests plus structural resource/save checks."""
from pathlib import Path
import blitz3d_toolchain as engine
from collections import Counter
import argparse, json, os, re, shutil, struct, subprocess, sys, tempfile
from editions import Edition, NAMES
from versioning import runtime_info
from locales import catalog, catalog_text, validate, languages, REFERENCE
from language_pack import registry
from s2map import Map
from verify_timing import function

ROOT=Path(__file__).resolve().parents[2]
BUILD=ROOT/'build'
def main():
    parser=argparse.ArgumentParser();parser.add_argument('--edition',choices=NAMES,default='classic')
    args=parser.parse_args();profile=Edition(args.edition)
    runtime=profile.runtime(ROOT);output_root=profile.build_dir(ROOT)
    catalogs=validate(ROOT,args.edition)
    if not (runtime/profile.executable).exists():raise ValueError('Run setup and build before check')
    if runtime_info(runtime)['edition']!=args.edition:raise ValueError('Runtime belongs to another edition')
    output=Path(tempfile.mkdtemp(prefix='check-',dir=output_root))
    (output/'core/locales').mkdir(parents=True)
    (output/'sys').mkdir()
    stock_strings='0041=[[loc:legacy.1622]]\nm053=[[loc:legacy.1231]]\nm179=[[loc:legacy.1048]]\n'
    (output/'sys/strings.inf').write_text(stock_strings,encoding='utf8')
    for lang in catalogs:(output/'core/locales'/f'{lang}.tsv').write_text(catalog_text(ROOT,lang,args.edition),encoding='utf8',newline='\n')
    packs=languages(ROOT);packs['zz-test']={'name':'日本語テスト'}
    (output/'core/locales/languages.dat').write_bytes(registry(packs))
    (output/'core/locales/zz-test.tsv').write_text('ui.language\t言語\n',encoding='utf8')
    with (output/'core/locales/en.tsv').open('a',encoding='utf8') as f:f.write('test.fallback\tFallback text\n')
    (output/'core/modern.ini').write_text('# preserve comment\nfps=144\ndisplay=windowed\nlanguage=ko\nrender_width=1280\n',encoding='utf8')
    includes=ROOT/'src/engine/includes'
    body=r'''
Global set_rootdir$=SystemProperty("APPDIR"),failures,variable$="7"
Global p_internal,p_p,p_len,p_l$,p_internaltxt$,p_gotparam
Global report=WriteFile(set_rootdir$+"localization.txt")
'''+(includes/'localization.bb').read_text('utf8')+'\n'+(includes/'load_strings.bb').read_text('utf8')+'\n'+function((includes/'parser_functions.bb').read_text('utf8'),'params$')+'\n'+function((includes/'functions.bb').read_text('utf8'),'trimtext$')+'\n'+function((includes/'parser_vars.bb').read_text('utf8'),'var_rep$')+r'''
Function parse_error(message$)
 check(False,"unexpected parse error: "+message$)
End Function
Function var_get$(key$)
 If key$="wood" Then Return variable$
 If key$="wood2" Then Return "9"
 Return "0"
End Function
Function check(ok,label$)
 WriteLine report,"CHECK "+label$
 If Not ok Then failures=failures+1:WriteLine report,"FAIL "+label$
 CloseFile report
 report=OpenFile(set_rootdir$+"localization.txt")
 SeekFile report,FileSize(set_rootdir$+"localization.txt")
End Function
check(True,"start")
loc_boot()
check(loc_language$="ko","existing Korean configuration preserved")
check(loc_resolve("[[loc:ui.language]]")="언어","UTF-8 catalog")
check(loc_available("zz-test"),"third language discovered without engine changes")
check(loc_name("zz-test")="日本語テスト","native language name")
loc_cycle(1):check(loc_pending$="en","next language")
loc_cycle(-1):check(loc_pending$="ko","previous language")
loc_cycle(-1):check(loc_pending$="zz-test","previous wraps to final language")
loc_cycle(1):check(loc_pending$="ko","next wraps to first language")
loc_pending$="zz-test":loc_pending$=loc_saved$
check(loc_pending$="ko","cancel discards language selection")
check(loc_resolve("[[loc:test.fallback]]")="Fallback text","missing Korean falls back to English")
loc_put("test.bound","wood=$wood, more=$wood2")
p_l$=Chr(34)+"[[loc:test.bound]]"+Chr(34)+",":p_len=Len(p_l$):p_p=1
parsed$=params()
check(loc_resolve(parsed$)="wood=7, more=9","actual script string parser binds placeholders")
p_internal=1:p_internaltxt$=p_l$:parsed$=params():p_internal=0
check(loc_resolve(parsed$)="wood=7, more=9","internal script parser binds placeholders")
bound$=loc_bind("before [[loc:test.bound]] after")
check(Instr(bound$,"|wood=37")>0,"bound value stored without translated text")
variable$="88"
check(loc_resolve(bound$)="before wood=7, more=9 after","variable snapshot and full-name boundary")
stream=WriteFile(set_rootdir$+"reference-save.dat")
WriteString stream,bound$:CloseFile stream
loc_load("en")
loc_put("test.bound","$wood2 first; $wood second")
stream=ReadFile(set_rootdir$+"reference-save.dat")
bound$=ReadString(stream):CloseFile stream
check(loc_resolve(bound$)="before 9 first; 7 second after","serialized reference resolves after language switch")
check(loc_resolve("[[loc:ui.language]]")="Language","English catalog")
loc_load("ko")
check(loc_resolve("[[loc:ui.language]]")="언어","English to Korean round trip")
check(loc_resolve(s$(41))=loc_resolve("[[loc:legacy.1622]]"),"Korean compass label preserved")
check(loc_resolve(sm$(53))=loc_resolve("[[loc:legacy.1231]]"),"Korean key label preserved")
check(loc_resolve(sm$(179))=loc_resolve("[[loc:legacy.1048]]"),"Korean fishing label preserved")
loc_pending$="de":check(loc_save(),"German selection saved")
check(loc_language$="ko","German apply waits for restart")
loc_boot():check(loc_language$="de","German restart")
check(loc_name("de")="Deutsch","German native language name")
check(loc_resolve("[[loc:ui.language]]")="Sprache","German catalog loaded")
check(loc_resolve(s$(41))="O","German compass east")
check(loc_resolve("[[loc:legacy.1622]]")="E","keyboard E is not translated to compass east")
check(loc_resolve(sm$(53))="Taste","German keyboard key")
check(loc_resolve("[[loc:legacy.1231]]")="Schlüssel","German inventory key and umlaut")
check(loc_resolve(sm$(179))="Angeln","German fishing action")
check(loc_resolve("[[loc:legacy.1048]]")="Fisch","German fish item")
check(ss("[[loc:extra.30db153c32e4|score=3132]]")="12 Punkte","German bound variable")
check(loc_resolve("[[loc:test.fallback]]")="Fallback text","missing German falls back to English")
stream=WriteFile("sys\strings.inf")
WriteLine stream,"0041=Custom East":WriteLine stream,"m053=Custom Key":WriteLine stream,"m179=Custom Fish"
CloseFile stream:load_strings()
check(s$(41)="Custom East" And sm$(53)="Custom Key" And sm$(179)="Custom Fish","custom mod labels remain intact")
loc_load("ko")
check(loc_resolve("이전에 저장된 한글")="이전에 저장된 한글","legacy literal remains unchanged")
value$="한글,"+Chr(34)+"$var|]]"
check(loc_unhex(loc_hex(value$))=value$,"delimiter-safe UTF-8 binding encoding")
check(loc_resolve("[[loc:unknown.key]]")="[missing: unknown.key]","unknown reference diagnostic")
check(FileType(set_rootdir$+"core\localization.log")=1,"missing reference log")
loc_pending$="en":check(loc_save(),"language config saved")
check(loc_language$="ko","saving language does not change active catalog")
loc_boot():check(loc_language$="en","restart applies language")
loc_pending$="zz-test":check(loc_save(),"third language config saved")
check(loc_language$="en","third language apply waits for restart")
loc_boot():check(loc_language$="zz-test","third language restart")
check(loc_resolve("[[loc:ui.language]]")="言語","third language catalog")
check(loc_resolve("[[loc:test.fallback]]")="Fallback text","partial third language uses English")
DeleteFile set_rootdir$+"core\locales\zz-test.tsv"
loc_boot():check(loc_language$="en","removed selected pack falls back to English")
loc_pending$="not-installed":check(loc_save(),"write unavailable language fixture")
loc_boot():check(loc_language$="en","unknown configured language falls back to English")
loc_pending$="en":loc_save()
CopyFile set_rootdir$+"core\modern.ini",set_rootdir$+"core\modern-preserved.ini"
DeleteFile set_rootdir$+"core\modern.ini"
loc_boot():check(loc_language$="en","missing config defaults to English")
stream=WriteFile(set_rootdir$+"core\modern.ini")
WriteLine stream,"fps=144":CloseFile stream
loc_boot():check(loc_language$="en","missing language key defaults to English")
CopyFile set_rootdir$+"core\modern-preserved.ini",set_rootdir$+"core\modern.ini"
WriteLine report,"FAILURES "+failures
CloseFile report
End
'''
    source=output/'localization.bb';source.write_text(body,encoding='utf8')
    engine.build_native(graphics=False)
    engine.compile_test(source,output/'localization.exe',cwd=output,graphics=False)
    from verify_engine import check_abi
    check_abi(output/'abi')
    subprocess.run([str(output/'localization.exe')],cwd=output,check=True,timeout=30,creationflags=subprocess.CREATE_NO_WINDOW)
    result=(output/'localization.txt').read_text('utf8');print(result)
    assert 'FAILURES 0' in result and '\nFAIL ' not in '\n'+result,result
    config=(output/'core/modern.ini').read_text('utf8')
    assert all(x in config for x in ('# preserve comment','fps=144','display=windowed','render_width=1280','language=en'))
    subprocess.run([sys.executable,'-X','utf8',str(ROOT/'src/devtools/verify_timing.py'),'--output',str(output/'timing')],check=True)
    subprocess.run([sys.executable,'-X','utf8',str(ROOT/'src/devtools/verify_modern.py'),'--output',str(output/'modern')],check=True)
    subprocess.run([sys.executable,'-X','utf8',str(ROOT/'src/devtools/verify_version.py'),'--output',str(output/'version')],check=True)
    subprocess.run([sys.executable,'-X','utf8',str(ROOT/'src/devtools/verify_settings.py'),'--output',str(output/'settings')],check=True)
    subprocess.run([sys.executable,'-X','utf8',str(ROOT/'src/devtools/verify_qol_logic.py'),'--edition',args.edition,'--output',str(output/'qol')],check=True)
    subprocess.run([sys.executable,'-X','utf8',str(ROOT/'src/devtools/verify_visibility.py'),'--output',str(output/'visibility')],check=True)
    subprocess.run([sys.executable,'-X','utf8',str(ROOT/'src/devtools/verify_lighting.py'),'--output',str(output/'lighting')],check=True)
    subprocess.run([sys.executable,'-X','utf8',str(ROOT/'src/devtools/verify_text_runs.py'),'--output',str(output/'text-runs')],check=True)
    # Check every emitted reference, including binary map string fields.
    refcount=0
    for p in runtime.rglob('*'):
        texts=[]
        if p.relative_to(runtime).parts[0]=='preserved':continue
        if '/saves/' in p.as_posix():continue
        if p.suffix in ('.inf','.s2s','.txt') and p.is_file():
            try:texts=[p.read_text('utf8')]
            except UnicodeError:continue  # third-party license originals
        elif p.suffix=='.s2':texts=[f.data.decode('utf8') for f in Map(p.read_bytes()).fields if f.kind!='attachment']
        for text in texts:
            for key in REFERENCE.findall(text):
                assert key in catalogs['en'],(p,key)
                refcount+=1
    from fonts import repertoire
    expected=set(repertoire(catalogs,languages(ROOT)))
    for code in catalogs:
        data=(runtime/f'core/locales/{code}/glyphs.dat').read_bytes()
        magic,count,cw,ch=struct.unpack_from('<4sIII',data)
        assert magic==b'FNT2' and len(data)==16+count*28 and 0<cw<=64 and ch==19
        glyphs={struct.unpack_from('<i',data,16+i*28)[0] for i in range(count)}
        assert expected<=glyphs,(code,expected-glyphs)
    subprocess.run([sys.executable,'-X','utf8',str(ROOT/'src/devtools/verify_fonts.py'),'--runtime',str(runtime),'--output',str(output/'fonts')],check=True)
    report={'localization_runtime':'pass','config_preservation':'pass','timing':'pass','modern_save_and_display':'pass','product_version_loader':'pass','initial_settings_and_preservation':'pass','validated_references':refcount,'unicode_glyphs':len(glyphs),'graphics_and_actual_game_save':'requires game smoke test'}
    report['qol_logic']='pass; entity/script test doubles, no rendering or IME'
    report['grass_visibility_logic']='pass; entity/terrain test doubles, no rendering'
    report['offscreen_shadow_caster_logic']='pass; object/collision state test doubles, no rendering'
    report['sun_ambient_brightness']='pass; actual Blitz function with captured renderer calls, no rendering'
    report['text_run_cache']='pass; cached layouts match the uncached walk, recorded glyph calls, no rendering'
    (output/'report.json').write_text(json.dumps(report,indent=2)+'\n',encoding='utf8')
    print('Checks passed:',output)
if __name__=='__main__':main()
