"""Exercise shared QOL in an isolated, real Blitz runtime (no GUI input).

Use --interactive to prepare a separate fixture for Computer Use visual checks.
Personal saves are excluded and modern.ini is replaced with fixture settings.
The source runtime and its user data are never written by these checks.
"""
from pathlib import Path
import argparse
import json
import os
import re
import shutil
import subprocess
import tempfile

from editions import Edition, NAMES
from verify_timing import function


FIXTURE = r'''
m_section=Csection_game_sp:m_menu=0:set_debug_testmap=0
If load_map("maps\adventure\map01.s2","")=0 Then RuntimeError "QOL fixture map missing"
parse_all()
For fixture_item.Titem=Each Titem
 If fixture_item\parent_class=Cclass_unit And fixture_item\parent_id=g_player Then free_item(fixture_item\id)
Next
For fixture_i=1 To 9:in_quickslot(fixture_i)=0:Next
set_object(-1,181,EntityX(g_cplayer\h)+40,EntityZ(g_cplayer\h))
Global fixture_storage=TCobject\id
set_unit(-1,2,EntityX(g_cplayer\h)+50,EntityZ(g_cplayer\h))
Global fixture_corpse=TCunit\id
con_unit(fixture_corpse):TCunit\health#=0
Global fixture_weight=Dunit_maxweight(g_cplayer\typ)
Function fixture_add(typ,count,class,id)
 Local itemid=set_item(-1,typ,0,0,0,count,1)
 store_item(itemid,class,id)
 Return itemid
End Function
fixture_add(30,1,Cclass_unit,g_player)
fixture_add(23,5,Cclass_unit,g_player)
fixture_add(24,10,Cclass_unit,g_player)
fixture_add(42,18,Cclass_unit,g_player)
fixture_add(85,2,Cclass_unit,g_player)
fixture_add(9,3,Cclass_unit,g_player)
fixture_add(26,3,Cclass_unit,g_player)
fixture_add(24,1,Cclass_object,fixture_storage)
fixture_add(30,1,Cclass_object,fixture_storage)
fixture_add(9,3,Cclass_unit,fixture_corpse)
in_quickslot(1)=30:in_quickslot(2)=9
g_cplayer\player_weapon=30
'''

CHECKS = r'''
Global qr=WriteFile(set_rootdir$+"qol-results.txt"),qfailures
Function qcheck(ok,label$)
 WriteLine qr,"CHECK "+label$
 If ok=0 Then qfailures=qfailures+1:WriteLine qr,"FAIL "+label$
End Function
For qi=0 To 4:qcheck(qol_enabled(qi)=Cedition_qol_default,"edition default for missing key "+qi):Next
CopyFile set_rootdir$+"core\modern.ini",set_rootdir$+"core\qol-before.ini"
Global qconfig=OpenFile(set_rootdir$+"core\modern.ini")
SeekFile qconfig,FileSize(set_rootdir$+"core\modern.ini")
WriteLine qconfig,"qol_recipes="+(1-Cedition_qol_default)
WriteLine qconfig,"qol_storage=invalid"
WriteLine qconfig,"qol_autosort=0"
WriteLine qconfig,"qol_hold_build=1"
CloseFile qconfig
qol_init()
qcheck(qol_enabled(Cqol_recipes)=1-Cedition_qol_default,"explicit value overrides edition default")
qcheck(qol_enabled(Cqol_storage)=Cedition_qol_default,"invalid value uses edition default")
qcheck(qol_enabled(Cqol_close)=Cedition_qol_default,"partial config uses default for absent key")
qcheck(qol_enabled(Cqol_sort)=0 And qol_enabled(Cqol_build)=1,"existing OFF and ON values retained")
CopyFile set_rootdir$+"core\qol-before.ini",set_rootdir$+"core\modern.ini"
DeleteFile set_rootdir$+"core\modern.ini"
qol_init()
For qi=0 To 4:qcheck(qol_enabled(qi)=Cedition_qol_default,"missing file uses edition default "+qi):Next
CopyFile set_rootdir$+"core\qol-before.ini",set_rootdir$+"core\modern.ini"
qol_init()
For qmask=0 To 31
 For qi=0 To 4:qol_pending(qi)=(qmask Shr qi) And 1:Next
 qol_hold_state=2
 qcheck(modern_options_save(),"save option combination "+qmask)
 qcheck(qol_hold_state=3,"applying options cancels held construction")
 For qi=0 To 4:qcheck(qol_enabled(qi)=((qmask Shr qi) And 1),"immediate option value "+qmask+":"+qi):Next
 qol_init()
 For qi=0 To 4:qcheck(qol_enabled(qi)=((qmask Shr qi) And 1),"persisted option value "+qmask+":"+qi):Next
 qcheck(qol_inventory_enabled()=((qmask Shr Cqol_sort) And 1),"inventory view follows sorting option")
Next
; Exercise shared features with all five enabled after checking both edition defaults.
For qi=0 To 4:qol_enabled(qi)=1:Next
qol_settings_reset()
If loc_language$="ko" Then
 qcheck(loc_resolve("[[loc:qol.recipes]]")="조합법 도감","Korean QOL catalog retains Unicode text")
Else
 qcheck(loc_resolve("[[loc:qol.recipes]]")="Recipe book","English QOL catalog")
EndIf
Global combinations,guides,heads
For qhead.Tcom=Each Tcom:If qhead\mode=2 Then heads=heads+1
Next
For qrecipe.Tqolrecipe=Each Tqolrecipe
 If qrecipe\combi Then combinations=combinations+1 Else guides=guides+1
Next
qcheck(combinations=heads And combinations>=51,"every loaded combination is indexed")
qcheck(guides=14,"interaction guides loaded")
qcheck(qol_category(30)=1 And qol_category(24)=6,"item groups match category ordering")
Global original_ids$,after_ids$
For qiitem.Titem=Each Titem:original_ids$=original_ids$+qiitem\id+",":Next
qol_view(Cclass_unit,g_player)
Global firstrow.Tqolitem=First Tqolitem
qcheck(firstrow\typ=30,"quick slot one first")
firstrow=After firstrow:qcheck(firstrow\typ=9,"quick slot two second")
For qiitem.Titem=Each Titem:after_ids$=after_ids$+qiitem\id+",":Next
qcheck(original_ids$=after_ids$,"sorting does not reorder engine items")
qol_enabled(Cqol_sort)=0:qol_view(Cclass_unit,g_player)
Global snapshot.Tqolitem=First Tqolitem
For qiitem.Titem=Each Titem
 If qiitem\parent_class=Cclass_unit And qiitem\parent_id=g_player Then
  qcheck(snapshot\itemid=qiitem\id,"OFF preserves original display order")
  snapshot=After snapshot
 EndIf
Next
qol_enabled(Cqol_sort)=1
Global flour.Tqolrecipe,iron.Tqolrecipe
For qrecipe.Tqolrecipe=Each Tqolrecipe
 If qrecipe\key$="flour" Then flour=qrecipe
 If qrecipe\key$="ironbar" Then iron=qrecipe
Next
qcheck(flour<>Null And iron<>Null,"craft fixtures found")
qol_query$="밀가루":qcheck(qol_recipe_matches(flour),"localized result search")
qol_query$="":qol_recipe_item=42:qol_recipe_relation=2
qcheck(qol_recipe_matches(flour),"ingredient usage filter")
qol_recipe_item=0:qol_recipe_relation=0
qcheck(qol_recipe_materials(flour),"material counts include player inventory")
Global lock.Tx=New Tx:lock\mode=50:lock\key$=flour\key$
qcheck(qol_recipe_locked(flour),"locked recipes stay visible but locked")
qcheck(qol_craft(flour)=0,"locked recipe cannot craft")
Delete lock
get_stored_item(9,Cclass_unit,g_player):TCitem\if_sel=1
qcheck(qol_craft(flour),"craft through original combination function")
qcheck(qol_count(42,Cclass_unit,g_player)=9,"craft consumes exact grain quantity")
qcheck(qol_count(23,Cclass_unit,g_player)=5,"reusable stone retained")
qcheck(qol_count(43,Cclass_unit,g_player)=1,"recipe output created")
get_stored_item(9,Cclass_unit,g_player):qcheck(TCitem\if_sel=1,"selection restored after crafting")
pc_typ=0:in_focus=0
get_stored_item(42,Cclass_unit,g_player):free_item(TCitem\id)
qcheck(qol_craft(flour)=0,"missing ingredients block crafting")
if_exchange(Cclass_object,fixture_storage,1)
qcheck(qol_bulk(1)=10,"matching deposit moves only matching unprotected items")
qcheck(qol_count(24,Cclass_object,fixture_storage)=11,"matching stacks merge")
qcheck(qol_count(30,Cclass_unit,g_player)=1,"equipped quick slot item protected")
qcheck(qol_count(9,Cclass_unit,g_player)=3,"nonmatching items retained")
in_ex_only$="|23|"
get_stored_item(24,Cclass_object,fixture_storage)
qcheck(qol_move(TCitem\id,99)=0,"restricted storage rejects disallowed type")
in_ex_only$="":in_ex_allowstore=0
get_stored_item(23,Cclass_unit,g_player)
qcheck(qol_move(TCitem\id,1)=0,"read-only container rejects deposits")
in_ex_allowstore=1
Global freeweight=capacity(Cclass_unit,g_player,g_cplayer\typ)
Dunit_maxweight(g_cplayer\typ)=fixture_weight-freeweight+Ditem_weight(24)*2
get_stored_item(24,Cclass_object,fixture_storage)
qcheck(qol_move(TCitem\id,99)=2,"capacity allows a partial stack")
qcheck(qol_count(24,Cclass_object,fixture_storage)=9,"partial transfer preserves remainder")
qcheck(qol_count(24,Cclass_unit,g_player)=2,"partial transfer preserves total")
if_exchange(Cclass_unit,fixture_corpse,0)
qcheck(qol_bulk(0)=0,"full inventory cannot loot")
qcheck(m_menu=Cmenu_if_exchange,"corpse stays open when items remain")
Dunit_maxweight(g_cplayer\typ)=fixture_weight
qcheck(qol_bulk(0)=3,"loot all moves corpse contents")
qcheck(m_menu=0,"empty corpse closes to game")
fixture_add(9,1,Cclass_unit,fixture_corpse)
qol_enabled(Cqol_close)=0:if_exchange(Cclass_unit,fixture_corpse,0)
qcheck(qol_bulk(0)=1,"collect corpse with auto-close disabled")
qcheck(m_menu=Cmenu_if_exchange,"auto-close OFF leaves empty corpse open")
qol_enabled(Cqol_close)=1
if_exchange(Cclass_object,fixture_storage,1)
qol_bulk(0):qcheck(m_menu=Cmenu_if_exchange,"empty storage stays open")
Global vetoid=fixture_add(26,1,Cclass_unit,g_player)
Global oldscript$=Ditem_script$(26),oldkeys$=Ditem_scriptk$(26)
Ditem_script$(26)="on:drop {"+Chr(31)+"skipevent;"+Chr(31)+"}"
Ditem_scriptk$(26)=Chr(31)+"drop"
get_stored_item(26,Cclass_unit,g_player):Global vetocount=TCitem\count
qcheck(qol_move(TCitem\id,1)=0,"drop script can veto quick transfer")
qcheck(qol_count(26,Cclass_unit,g_player)=vetocount,"veto preserves quantity")
Ditem_script$(26)=oldscript$:Ditem_scriptk$(26)=oldkeys$
; Hold scheduling runs on game time, independently of frame frequency.
set_object(-1,set_buildplaceid,EntityX(cam),EntityZ(cam))
Global site=TCobject\id
set_state(Cstate_buildplace,Cclass_object,site):TCstate\value=181
PositionEntity TCobject\h,EntityX(cam),EntityY(cam),EntityZ(cam)
m_menu=0:in_keydown(Ckey_attack2)=1:in_keyhit(Ckey_attack2)=1
qcheck(qol_attack2(),"initial secondary action retained")
qol_build_result(site,1):in_keyhit(Ckey_attack2)=0
gt=10000:g_cplayer\player_lastattack=gt
qcheck(qol_attack2()=0,"hold waits for existing tool interval")
gt=gt+Ditem_rate(30)
qcheck(qol_attack2(),"hold repeats at existing tool interval")
qcheck(qol_build_accept(site) And qol_build_accept(site+999)=0,"hold stays on original construction site")
For qrate=0 To 2
 Local hz=60
 If qrate=1 Then hz=120
 If qrate=2 Then hz=144
 Local repeats=0
 gt=10000:g_cplayer\player_lastattack=gt:qol_hold_state=2
 For qframe=1 To hz*2
  gt=10000+Int(Float(qframe)*1000./Float(hz))
  If qol_attack2() Then repeats=repeats+1:g_cplayer\player_lastattack=gt
 Next
 qcheck(repeats=4,"same hold cadence at "+hz+" simulated FPS")
Next
qol_build_result(site,0):qcheck(qol_attack2()=0,"completion stops repeating")
qol_hold_state=2:qol_hold_resume=modern_resume_count-1:qol_frame()
qcheck(qol_hold_state=3,"focus recovery cancels held construction")
qol_hold_state=2:m_menu=Cmenu_if_items:qol_hold_resume=modern_resume_count:qol_frame()
qcheck(qol_hold_state=3,"opening menu cancels held construction")
qol_enabled(Cqol_build)=0:in_keyhit(Ckey_attack2)=1
qcheck(qol_attack2(),"OFF retains secondary click")
in_keyhit(Ckey_attack2)=0:qcheck(qol_attack2()=0,"OFF does not repeat")
qol_enabled(Cqol_build)=1
qol_settings_reset():qol_pending(Cqol_sort)=0:qol_settings_reset()
qcheck(qol_pending(Cqol_sort)=1,"cancel restores QOL settings")
qcheck(qol_settings_restart()=0,"QOL-only apply does not request restart")
qol_pending(Cqol_sort)=0
qcheck(modern_options_save(),"QOL settings commit")
qcheck(qol_enabled(Cqol_sort)=0,"QOL applies immediately")
qol_init():qcheck(qol_enabled(Cqol_sort)=0,"QOL persists across reload")
qol_pending(Cqol_sort)=1:modern_options_save()
qcheck(save_map("saves\qol-roundtrip.sav","sav",""),"save after transfers and crafting")
Global savedbranches=qol_count(24,Cclass_unit,g_player)
qcheck(load_map("saves\qol-roundtrip.sav",""),"load QOL save with existing format")
qcheck(qol_count(24,Cclass_unit,g_player)=savedbranches,"save preserves inventory counts")
qcheck(in_quickslot(1)=30,"save preserves quick slot mapping")
WriteLine qr,"FAILURES "+qfailures:CloseFile qr
qol_shutdown():e_clear():EndGraphics():End
'''


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--interactive', action='store_true')
    parser.add_argument('--language', choices=('en', 'ko'), default='ko')
    parser.add_argument('--desktop', action='store_true')
    parser.add_argument('--edition', choices=NAMES, default='classic')
    args = parser.parse_args()
    root = Path(__file__).resolve().parents[1]
    profile = Edition(args.edition)
    out = Path(tempfile.mkdtemp(prefix='qol-smoke-', dir=profile.build_dir(root)))
    runtime = out/'runtime'
    shutil.copytree(profile.runtime(root), runtime,
                    ignore=shutil.ignore_patterns('preserved', 'saves', '*.log'))
    (runtime/'mods/Stranded II/saves').mkdir()
    (runtime/'core/modern.ini').write_text(
        'fps=144\ndisplay=' + ('borderless' if args.desktop else 'windowed') +
        '\nresolution=' + ('desktop' if args.desktop else 'custom') +
        f'\nrender_width=800\nrender_height=600\nlanguage={args.language}\n', encoding='utf8')
    source = out/'src'
    shutil.copytree(root/'src/engine', source)
    init = source/'includes/engine_init.bb'
    init.write_text(init.read_text('utf8').replace('\nunrealsoftware()\n', '\n'), encoding='utf8')
    loop = source/'includes/engine_loop.bb'
    if args.interactive:
        text = FIXTURE + '\nFor qi=0 To 4:qol_enabled(qi)=1:Next\nqol_settings_reset():qol_recipes_open(0)\n' + loop.read_text('utf8').replace('menu_start()', '')
    else:
        timing = source/'includes/modern_runtime.bb'
        text = timing.read_text('utf8')
        timing.write_text(text.replace(function(text, 'modern_focus'),
                                      'Function modern_focus()\n Return 0\nEnd Function'), encoding='utf8')
        text = FIXTURE + CHECKS
        if args.language == 'en':
            text = text.replace('qol_query$="밀가루"', 'qol_query$="Flour"')
    loop.write_text(text, encoding='utf8')
    blitz = root/'build/toolchain'
    exe = runtime/'QolSmoke.exe'
    result = subprocess.run([str(blitz/'bin/blitzcc.exe'), '-o', str(exe), str(source/profile.entry)],
                            cwd=source, env=dict(os.environ, blitzpath=str(blitz)), capture_output=True)
    if result.returncode:
        raise RuntimeError(result.stdout.decode('utf8', errors='replace'))
    print('Isolated runtime:', runtime, flush=True)
    if args.interactive:
        print('Prepared for Computer Use:', exe)
        return
    startup = subprocess.STARTUPINFO()
    startup.dwFlags |= subprocess.STARTF_USESHOWWINDOW
    startup.wShowWindow = 0
    subprocess.run([str(exe)], cwd=runtime, startupinfo=startup,
                   creationflags=subprocess.CREATE_NO_WINDOW, check=True, timeout=90)
    result = (runtime/'qol-results.txt').read_text('utf8')
    print(result)
    assert 'FAILURES 0' in result and '\nFAIL ' not in '\n'+result, result
    (out/'report.json').write_text(json.dumps({
        'edition': args.edition, 'language': args.language,
        'recipes_transfers_sorting_settings_save_load': 'pass',
        'hold_scheduling': 'pass', 'visual_ui_and_ime': 'not tested by this harness',
    }, indent=2)+'\n', encoding='utf8')


if __name__ == '__main__':
    main()
