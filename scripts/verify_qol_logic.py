"""Non-graphical Blitz checks of real QOL, recipe, transfer and settings logic.

Entity creation/rendering and script execution are explicit test doubles. This
does not replace verify_qol.py's full-runtime or Computer Use/IME checks.
"""
from pathlib import Path
import blitz3d_toolchain as engine
import argparse
import json
import os
import re
import shutil
import subprocess
import tempfile

from editions import Edition, NAMES
from verify_qol import FIXTURE, CHECKS
from verify_timing import function
from remaster_fixture import settings_fixture


DOUBLES = r'''
Global modern_fps=144,modern_borderless,modern_width=800,modern_height=600,modern_native
Global modern_resume_count,modern_hwnd,qol_search_hwnd,test_distance#,test_script$
Global qol_query$
Dim sm$(250)
Function var_get$(key$)
 Return "0"
End Function
Function test_trace(label$)
 Local f=OpenFile("trace.txt")
 If f=0 Then f=WriteFile("trace.txt")
 SeekFile f,FileSize("trace.txt"):WriteLine f,label$:CloseFile f
End Function
Function test_position(h,x#,y#,z#)
End Function
Function test_hide(h)
End Function
Function test_rotate(h,x#,y#,z#)
End Function
Function test_coordinate#(h)
 Return 0
End Function
Function test_entity_distance#(a,b)
 Return test_distance#
End Function
Function set_item(id=-1,typ,x#=0,y#=0,z#=0,count=1,silent=0)
 item_serial=item_serial+1
 TCitem=New Titem:TCitem\id=item_serial:TCitem\typ=typ:TCitem\count=count
 Return item_serial
End Function
Function set_object(id=-1,typ,x#=0,z#=0,yaw#=0)
 object_serial=object_serial+1
 TCobject=New Tobject:TCobject\id=object_serial:TCobject\typ=typ
End Function
Function set_unit(id=-1,typ,x#=0,z#=0)
 unit_serial=unit_serial+1
 TCunit=New Tunit:TCunit\id=unit_serial:TCunit\typ=typ:TCunit\health#=100
End Function
Function set_state(typ,class,id)
 TCstate=New Tstate:TCstate\typ=typ:TCstate\parent_class=class:TCstate\parent_id=id
End Function
Function con_info(id)
 Return False
End Function
Function free_childs(class,id)
End Function
Function ha_typeexists(class,typ)
 Return Ditem_name$(typ)<>""
End Function
Function const_set(name$,id)
End Function
Function ko_localize$(text$)
 Return loc_resolve(text$)
End Function
Function con_add(text$,col=0)
End Function
Function speech(text$)
End Function
Function sfx(sound)
End Function
Function if_msg(text$,col=0,duration=0)
End Function
Function p_add2d(x,y,mode,typ)
End Function
Function parse_task(class,id,event$,source$,script$)
 test_script$=script$
End Function
Function parse_sel(class,id,event$)
 p_skipevent=(Instr(test_script$,"skipevent")>0)
End Function
Function parse_env(class,id,event$)
End Function
Function loadstring_parsecache(script$)
 test_script$=script$
End Function
Function set_parsecache(class,id,event$)
 Return False
End Function
Function parse()
 p_skipevent=(Instr(test_script$,"skipevent")>0)
End Function
Function parse_all()
End Function
Function flushinput()
End Function
Function qol_search_close()
End Function
Function qol_search_update()
End Function
Function qol_shutdown()
End Function
Function e_clear()
End Function
'''


def declarations(text):
    text = re.sub(r'(?ims)^Function\b.*?^End Function\s*', '', text)
    blocks = re.findall(r'(?ims)^Type\b.*?^End Type', text)
    lines = [line for line in text.splitlines()
             if re.match(r'^(Global|Dim|Const)\s', line)]
    return '\n'.join(lines + blocks) + '\n'


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output', type=Path)
    parser.add_argument('--language', choices=('en', 'ko'), default='ko')
    parser.add_argument('--edition', choices=NAMES, default='classic')
    args = parser.parse_args()
    root = Path(__file__).resolve().parents[1]
    profile = Edition(args.edition)
    runtime = profile.runtime(root)
    output = args.output or Path(tempfile.mkdtemp(prefix='qol-logic-', dir=profile.build_dir(root)))
    output = output.resolve()
    (output/'sys').mkdir(parents=True, exist_ok=True)
    (output/'core/locales').mkdir(parents=True, exist_ok=True)
    for p in (runtime/'mods/Stranded II/sys').glob('combinations*.inf'):
        shutil.copy2(p, output/'sys'/p.name)
    shutil.copy2(runtime/'mods/Stranded II/sys/qol_guides.inf', output/'sys/qol_guides.inf')
    for name in ('en.tsv', 'ko.tsv', 'languages.dat'):
        shutil.copy2(runtime/'core/locales'/name, output/'core/locales'/name)
    (output/'core/modern.ini').write_text(
        f'; retain this comment\nfps=144\ndiagnostics=0\nlanguage={args.language}\n', encoding='utf8')
    inc = root/'src/engine/includes'
    body = (root/f'src/engine/editions/{args.edition}/profile.bb').read_text('utf8') + '\n'
    body += (inc/'load_const.bb').read_text('utf8') + '\n' + (inc/'load_vars.bb').read_text('utf8')
    body += '\nGlobal Citem_count=1000,Cunit_count=1000,Cobject_count=1000\n'
    body += '\n' + declarations((inc/'load_items.bb').read_text('utf8'))
    body += '\nDim Dunit_maxweight(Cunit_count),Dobject_maxweight(Cobject_count)\n'
    for name in ('qol.bb', 'qol_inventory.bb', 'qol_recipes.bb'):
        body += declarations((inc/name).read_text('utf8'))
    # qol_query belongs to the omitted native text-input module.
    body += DOUBLES
    body += (inc/'modern_files.bb').read_text('utf8') + '\n'
    body += (inc/'localization.bb').read_text('utf8') + '\n'
    body += declarations((inc/'modern_settings.bb').read_text('utf8'))
    body += settings_fixture(inc)
    selected = {
        'functions.bb': ('split$', 'trimspace$'),
        'load_strings.bb': ('ss$',),
        'modern_settings.bb': ('modern_capture_settings', 'modern_settings_reset', 'modern_options_save'),
        'handle_items.bb': ('free_item', 'store_item', 'countstored_items', 'combine_item',
                            'exchange_item', 'capacity', 'get_stored_item'),
        'handle_units.bb': ('con_unit',),
        'handle_objects.bb': ('con_object',),
        'handle_states.bb': ('get_state',),
        'interface.bb': ('if_unselectitems', 'if_exchange', 'if_exchange_allowed'),
    }
    for filename, names in selected.items():
        text = (inc/filename).read_text('utf8')
        for name in names:
            body += function(text, name) + '\n'
    selected = {
        'qol.bb': ('qol_init', 'qol_settings_reset', 'qol_setting_owned', 'qol_settings_write',
                   'qol_settings_apply', 'qol_settings_restart', 'qol_frame', 'qol_attack2',
                   'qol_build_accept', 'qol_build_result', 'qol_min', 'qol_max'),
        'qol_inventory.bb': ('qol_category', 'qol_rank', 'qol_item.Titem', 'qol_count', 'qol_view',
                             'qol_inventory_enabled', 'qol_protected', 'qol_destination_exists', 'qol_move', 'qol_bulk'),
        'qol_recipes.bb': ('qol_recipe.Tqolrecipe', 'qol_part', 'qol_guides_load', 'qol_recipe_locked',
                           'qol_recipe_materials', 'qol_recipe_matches', 'qol_craft'),
    }
    for filename, names in selected.items():
        text = (inc/filename).read_text('utf8')
        for name in names:
            body += function(text, name) + '\n'
    body += '\ntest_trace("startup")\nset_rootdir$=SystemProperty("APPDIR"):loc_boot():modern_capture_settings()\ntest_trace("catalogs")\n'
    # Use actual prepared item names, groups, weights and tool rates.
    for p in (runtime/'mods/Stranded II/sys').glob('items*.inf'):
        for section in re.split(r'(?m)^id=', p.read_text('utf8'))[1:]:
            lines = section.splitlines()
            ident = int(lines[0].strip())
            values = dict(re.findall(r'(?m)^(name|group|weight|rate|behaviour)=(.*)$', section))
            for key, array in (('name', 'Ditem_name$'), ('group', 'Ditem_group$'),
                               ('behaviour', 'Ditem_behaviour$')):
                value = values.get(key, '').strip()
                assert '"' not in value
                body += f'{array}({ident})="{value}"\n'
            body += f'Ditem_weight({ident})={int(values.get("weight", "0"))}\n'
            body += f'Ditem_rate({ident})={int(values.get("rate", "500"))}\n'
    body += 'test_trace("items")\n' + (inc/'load_combinations.bb').read_text('utf8') + '\ntest_trace("combinations")\nqol_init()\ntest_trace("qol init")\n'
    body += 'Dunit_maxweight(1)=25000:Dunit_maxweight(2)=25000:Dobject_maxweight(181)=50000\n'
    fixture = FIXTURE.replace('If load_map("maps\\adventure\\map01.s2","")=0 Then RuntimeError "QOL fixture map missing"',
                              'set_unit(-1,1,0,0):g_player=TCunit\\id:g_cplayer=TCunit')
    checks = CHECKS[:CHECKS.index('qcheck(save_map(')]
    checks += 'WriteLine qr,"FAILURES "+qfailures:CloseFile qr:End\n'
    if args.language == 'en':
        checks = checks.replace('qol_query$="밀가루"', 'qol_query$="Flour"')
    fixture = re.sub(r'(?m)^(fixture_add\(.*)$', r'test_trace("adding fixture")\n\1', fixture)
    body += fixture + '\ntest_trace("fixture ready")\n' + checks
    for name, replacement in {'PositionEntity': 'test_position', 'RotateEntity': 'test_rotate',
                              'HideEntity': 'test_hide', 'ShowEntity': 'test_hide',
                              'EntityX': 'test_coordinate', 'EntityY': 'test_coordinate',
                              'EntityZ': 'test_coordinate', 'EntityDistance': 'test_entity_distance'}.items():
        body = re.sub(r'\b' + name + r'\b', replacement, body)
    source = output/'qol-logic.bb'
    source.write_text(body, encoding='utf8')
    exe = output/'QolLogic.exe'
    engine.compile_test(source, exe, cwd=output)
    subprocess.run([str(exe)], cwd=output, check=True, timeout=30,
                   creationflags=subprocess.CREATE_NO_WINDOW)
    result = (output/'qol-results.txt').read_text('utf8')
    print(result)
    assert 'FAILURES 0' in result and '\nFAIL ' not in '\n'+result, result
    config = (output/'core/modern.ini').read_text('utf8')
    assert '; retain this comment' in config and 'diagnostics=0' in config
    assert config.count('qol_autosort=') == 1
    (output/'report.json').write_text(json.dumps({
        'edition': args.edition, 'language': args.language, 'actual_blitz_logic': 'pass',
        'graphics_entities_scripts_ime_save_load': 'not tested; explicit test doubles',
    }, indent=2)+'\n', encoding='utf8')
    print('QOL logic checks passed:', output)


if __name__ == '__main__':
    main()
