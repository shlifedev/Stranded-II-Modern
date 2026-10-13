"""Run exported authoring scripts in an isolated real Extended engine (Windows).

No GUI input or user saves are used. This verifies script/game logic, not UI.
"""
from pathlib import Path
import base64
import json
import shutil
import subprocess
import tempfile

import blitz3d_toolchain as engine
from studio_core.project import Project, ROOT, MOD
from verify_timing import function


def main():
    out=Path(tempfile.mkdtemp(prefix='script-smoke-',dir=ROOT/'build/studio'))
    project=Project.create(out/'project',ROOT/'build/studio/runtime','Script smoke')
    operation=json.loads((ROOT/'studio/examples/tonic-scenario.json').read_text('utf8'))
    result=project.apply([operation])
    assert not result['diagnostics'], result['diagnostics']
    created=result['results'][0]
    project.export(out/'export')
    runtime=out/'runtime'
    shutil.copytree(ROOT/'build/extended/runtime',runtime,ignore=shutil.ignore_patterns('preserved','saves','*.log'))
    (runtime/MOD/'saves').mkdir(exist_ok=True)
    shutil.copytree(out/'export/overrides',runtime,dirs_exist_ok=True)
    # The preview uses the same merged catalogs and generated fonts as export.
    for relative,encoded in project.preview_resources().items():
        if relative.startswith('core/'):
            p=runtime/relative;p.parent.mkdir(parents=True,exist_ok=True);p.write_bytes(base64.b64decode(encoded))
    (runtime/'core/modern.ini').write_text('fps=60\ndisplay=windowed\nresolution=custom\nrender_width=800\nrender_height=600\nlanguage=ko\n',encoding='utf8')
    source=out/'src';shutil.copytree(ROOT/'src/engine',source)
    p=source/'includes/modern_runtime.bb';text=p.read_text('utf8')
    p.write_text(text.replace(function(text,'modern_focus'),'Function modern_focus()\n Return 0\nEnd Function'),encoding='utf8')
    p=source/'includes/engine_init.bb';p.write_text(p.read_text('utf8').replace('\nunrealsoftware()\n','\n'),encoding='utf8')
    text=r'''
Global smoke_report=WriteFile(set_rootdir$+"script-smoke.txt"),smoke_failures
Global smoke_debug=WriteFile(set_rootdir$+"script-debug.jsonl")
Global studio_playing,studio_class,studio_id
Function web_service$(operation$,value$="")
 If operation$="studio-event" Then WriteLine smoke_debug,Mid(value$,7)
 Return ""
End Function
Include "studio\debug.bb"
Function smoke_check(ok,label$)
 WriteLine smoke_report,"CHECK "+label$
 If Not ok Then smoke_failures=smoke_failures+1:WriteLine smoke_report,"FAIL "+label$
End Function
smoke_check(p_trace_enabled=0,"ordinary game tracing disabled")
p_trace_enabled=1
m_section=Csection_game_sp:m_menu=0:set_debug_testmap=0
smoke_check(load_map("maps\tonic_delivery.s2",""),"load exported native map")
parse_all()
smoke_check(var_get("tonic_delivery_done")="0","scenario starts incomplete")
Global smoke_npc,smoke_recipe,smoke_reward,smoke_tonic,smoke_wood,smoke_stone
For smoke_unit.Tunit=Each Tunit
 If smoke_unit\typ=@NPC@ Then smoke_npc=smoke_unit\id
Next
smoke_check(smoke_npc>0,"template NPC is present")
For smoke_item.Titem=Each Titem
 If smoke_item\typ=1 Then smoke_wood=smoke_item\id
 If smoke_item\typ=2 Then smoke_stone=smoke_item\id
Next
smoke_check(store_item(smoke_wood,Cclass_unit,g_player),"collect first ingredient")
smoke_check(store_item(smoke_stone,Cclass_unit,g_player),"collect second ingredient")
For smoke_item.Titem=Each Titem
 smoke_item\if_sel=0
 If smoke_item\parent_class=Cclass_unit And smoke_item\parent_id=g_player Then
  If smoke_item\typ=1 Or smoke_item\typ=2 Then smoke_item\if_sel=1
 EndIf
Next
For smoke_com.Tcom=Each Tcom
 If smoke_com\mode=1 And smoke_com\typ=@ITEM@ Then smoke_recipe=smoke_com\id
Next
smoke_check(smoke_recipe>0,"exported recipe is loaded")
smoke_check(combine_item(Cclass_unit,g_player,smoke_recipe),"craft tonic from collected ingredients")
smoke_check(countstored_items(Cclass_unit,g_player,@ITEM@)=1,"crafted tonic enters inventory")
smoke_reward=countstored_items(Cclass_unit,g_player,1)
parse_task(Cclass_unit,smoke_npc,"use","",Dunit_script$(@NPC@)):parse_all()
smoke_check(var_get("tonic_delivery_done")="1","NPC delivery completes scenario")
smoke_check(countstored_items(Cclass_unit,g_player,@ITEM@)=0,"delivery consumes exactly one tonic")
smoke_check(countstored_items(Cclass_unit,g_player,1)=smoke_reward+1,"external reward script runs")
parse_task(Cclass_unit,smoke_npc,"use","",Dunit_script$(@NPC@)):parse_all()
smoke_check(countstored_items(Cclass_unit,g_player,1)=smoke_reward+1,"repeat interaction does not repeat reward")
smoke_tonic=set_item(-1,@ITEM@):store_item(smoke_tonic,Cclass_unit,g_player)
g_cplayer\health#=40.
parse_task(Cclass_item,smoke_tonic,"use","",Ditem_script$(@ITEM@)):parse_all()
smoke_check(Abs(g_cplayer\health#-60.)<0.01,"healing item runs native use script")
smoke_check(countstored_items(Cclass_unit,g_player,@ITEM@)=0,"healing consumes the item")
For smoke_trace.TparseTrace=Each TparseTrace
 If smoke_trace\category$="error" Then smoke_failures=smoke_failures+1:WriteLine smoke_report,"FAIL "+smoke_trace\message$+" / "+smoke_trace\code$
Next
smoke_check(p_trace_count>0,"Studio event trace captures executed scripts")
Delete Each TparseTrace:p_trace_count=0
parse_task(0,0,"start","fixture","on:start {"+Chr(31)+"unknown_fixture_command;"+Chr(31)+"}"):parse_all()
Global smoke_error=0
For smoke_trace.TparseTrace=Each TparseTrace
 If smoke_trace\category$="error" Then
  smoke_error=1
  smoke_check(smoke_trace\row=2 And smoke_trace\column>0,"native error position")
  smoke_check(Instr(smoke_trace\source$,"unknown_fixture_command;")>0,"native error source captured")
 EndIf
Next
smoke_check(smoke_error,"native parser error trace")
studio_debug_flush()
For smoke_i=1 To 200:parse_trace("event"):Next
smoke_check(p_trace_count=128,"trace memory is bounded")
var_set("diagnostic_fixture","한글 "+Chr(34)+Chr(92)+Chr(10))
studio_playing=1:gframe=0:studio_class=Cclass_unit:studio_id=smoke_npc
studio_debug_flush()
CloseFile smoke_debug
WriteLine smoke_report,"FAILURES "+smoke_failures
CloseFile smoke_report
e_clear():EndGraphics():End
'''.replace('@NPC@',str(created['npc'])).replace('@ITEM@',str(created['item']))
    (source/'includes/engine_loop.bb').write_text(text,encoding='utf8')
    exe=runtime/'ScriptSmoke.exe'
    print('Isolated authoring smoke:',out,flush=True)
    engine.compile_test(source/'StrandedIIExtended.bb',exe,cwd=source)
    startup=subprocess.STARTUPINFO();startup.dwFlags|=subprocess.STARTF_USESHOWWINDOW;startup.wShowWindow=0
    subprocess.run([str(exe)],cwd=runtime,startupinfo=startup,creationflags=subprocess.CREATE_NO_WINDOW,timeout=90,check=True)
    report=(runtime/'script-smoke.txt').read_text('utf8');print(report)
    assert 'FAILURES 0' in report and '\nFAIL ' not in '\n'+report,report
    events=[json.loads(line) for line in (runtime/'script-debug.jsonl').read_text('utf8').splitlines()]
    assert any(e['kind']=='error' and e['row']==2 and 'unknown_fixture_command' in e['script'] for e in events)
    variables=next(e['values'] for e in events if e['kind']=='variables')
    assert ['global','diagnostic_fixture','한글 "\\\n'] in variables,variables
    print('CHECK native diagnostic JSON and read-only variable snapshot')


if __name__=='__main__':
    main()
