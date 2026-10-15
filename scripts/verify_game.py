"""Opt-in real game save/load smoke test in a disposable runtime copy.

No GUI input is sent. Only branding, focus waiting and the interactive loop are
replaced. Use Computer Use separately for visual menu and options checks.
"""
from pathlib import Path
import blitz3d_toolchain as engine
from editions import Edition, NAMES
import argparse, json, os, shutil, subprocess, tempfile
from verify_timing import function
from s2map import Map


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--desktop', action='store_true',
                        help='Test fitted primary-monitor resolution in borderless mode')
    parser.add_argument('--edition', choices=NAMES, default='classic')
    args = parser.parse_args()
    profile = Edition(args.edition)
    root = Path(__file__).resolve().parents[1]
    out = Path(tempfile.mkdtemp(prefix='game-smoke-', dir=profile.build_dir(root)))
    runtime = out/'runtime'
    shutil.copytree(profile.runtime(root), runtime,
                    ignore=shutil.ignore_patterns('preserved', 'saves', '*.log'))
    (runtime/'mods/Stranded II/saves').mkdir()
    display = 'borderless' if args.desktop else 'windowed'
    resolution = 'desktop' if args.desktop else 'custom'
    (runtime/'core/modern.ini').write_text(
        f'fps=144\ndisplay={display}\nresolution={resolution}\n'
        'render_width=800\nrender_height=600\nlanguage=ko\n', encoding='utf8')
    source = out/'src'
    shutil.copytree(root/'src/engine', source)
    timing = source/'includes/modern_runtime.bb'
    text = timing.read_text('utf8')
    text = text.replace(function(text, 'modern_focus'), 'Function modern_focus()\n Return 0\nEnd Function')
    timing.write_text(text, encoding='utf8')
    entry = source/'includes/engine_init.bb'
    text = entry.read_text('utf8').replace('\nunrealsoftware()\n', '\n; Branding skipped for automated test.\n')
    entry.write_text(text, encoding='utf8')
    entry = source/'includes/engine_loop.bb'
    text = ''
    text += r'''
Global smoke_report=WriteFile(set_rootdir$+"game-smoke.txt"),smoke_failures
Function smoke_check(ok,label$)
 WriteLine smoke_report,"CHECK "+label$
 If Not ok Then smoke_failures=smoke_failures+1:WriteLine smoke_report,"FAIL "+label$
 CloseFile smoke_report
 smoke_report=OpenFile(set_rootdir$+"game-smoke.txt")
 SeekFile smoke_report,FileSize(set_rootdir$+"game-smoke.txt")
End Function
m_section=Csection_game_sp:m_menu=0:set_debug_testmap=0
WriteLine smoke_report,"RENDER "+GraphicsWidth()+"x"+GraphicsHeight()
smoke_check(ko_index(65)>0 And ko_index(32)>0,"Latin and space use generated language font")
smoke_check(bmpf_len("A A")=bmpf_framesw(65,0)*2+bmpf_framesw(32,0),"text width uses custom space advance")
smoke_check(bmpf_len("한 A")=bmpf_framesw(54620,0)+bmpf_framesw(32,0)+bmpf_framesw(65,0),"mixed UTF-8 text width")
smoke_check(bmpf_txt_pos("한 A",0,bmpf_framesw(54620,0))=Len("한"),"caret stays on UTF-8 boundary")
SetBuffer BackBuffer()
bmpf_txt_rect(20,20,80,200,"ABC 한글 123 ABC 한글 123",0)
smoke_check(tmp_lines>1,"mixed-language text wraps")
For smoke_line.Tbmpf_s=Each Tbmpf_s
 smoke_check(bmpf_len(smoke_line\s$)<=80,"wrapped line fits available width")
Next
smoke_check(load_map("maps\adventure\map01.s2",""),"load official adventure")
parse_all()
g_cplayer\health#=42.
; SAVE_MENU_CHECKS
smoke_check(save_map("saves\roundtrip.sav","sav",""),"serialize actual game")
smoke_check(modern_save_valid("saves\roundtrip.sav"),"actual save passes preflight")
g_cplayer\health#=7.
smoke_check(load_map("saves\roundtrip.sav",""),"load actual save")
smoke_check(Abs(g_cplayer\health#-42.)<0.01,"player health restored")
g_cplayer\health#=84.
smoke_check(save_map("saves\roundtrip.sav","sav",""),"replace actual save")
CopyFile "saves\roundtrip.sav.bak","saves\previous.sav"
smoke_check(load_map("saves\previous.sav",""),"load previous backup")
smoke_check(Abs(g_cplayer\health#-42.)<0.01,"backup preserves previous state")
For smoke_index=1 To 7
 g_cplayer\health#=60.+smoke_index
 smoke_check(save_map(modern_autosave_target(),"sav",""),"actual rolling autosave")
Next
smoke_check(load_map(modern_latest_save(1),""),"load latest autosave")
smoke_check(Abs(g_cplayer\health#-67.)<0.01,"latest autosave state")
modern_queue_save(1):modern_process_saves()
g_cplayer\health#=10.
modern_queue_save(2):modern_process_saves()
smoke_check(Abs(g_cplayer\health#-67.)<0.01,"actual quicksave and quickload")
col_player_prepare():UpdateWorld(0):col_player_finish()
smoke_check(col_player_valid,"loaded player obtains verified collision pose")
Global smoke_resolved_y#=col_player_y#
TranslateEntity g_cplayer\h,0,50,0
smoke_check(save_map("saves\collision-pose.sav","sav",""),"save with pending player movement")
smoke_check(load_map("saves\collision-pose.sav",""),"load verified player pose")
smoke_check(Abs(EntityY(g_cplayer\h)-smoke_resolved_y#)<0.001,"save excludes unresolved player movement")
smoke_check(col_player_valid=0,"loading clears collision history")
modern_queue_save(1):modern_process_saves()
modern_continue_path$=modern_latest_save()
smoke_check(modern_continue_path$="saves\QUICKSAVE.sav","continue selects latest actual save")
WriteLine smoke_report,"FAILURES "+smoke_failures
CloseFile smoke_report
e_clear()
EndGraphics()
End
'''
    if text.count('; SAVE_MENU_CHECKS') != 1:
        raise ValueError('The save menu fixture marker changed.')
    text = text.replace('; SAVE_MENU_CHECKS',
                        (root/'tests/save_menu.bb').read_text('utf8').replace('preview_check(', 'smoke_check('))
    entry.write_bytes(text.replace('\n','\r\n').encode('utf8'))
    exe = runtime/'SaveSmoke.exe'
    print('Isolated runtime:', runtime, flush=True)
    engine.compile_test(source / profile.entry, exe, cwd=source)
    startup = subprocess.STARTUPINFO()
    startup.dwFlags |= subprocess.STARTF_USESHOWWINDOW
    startup.wShowWindow = 0
    subprocess.run([str(exe)], cwd=runtime, startupinfo=startup,
                   creationflags=subprocess.CREATE_NO_WINDOW, timeout=90, check=True)
    result = (runtime/'game-smoke.txt').read_text('utf8')
    print(result)
    assert 'FAILURES 0' in result and '\nFAIL ' not in '\n'+result, result
    saves = runtime/'mods/Stranded II/saves'
    assert len(list(saves.glob('AUTOSAVE*.sav'))) == 5
    for path in saves.glob('*.sav'):
        data=path.read_bytes()
        assert Map(data).write({}) == data, path
    (out/'report.json').write_text(json.dumps({'display':display, 'resolution':resolution,
        'actual_game_save_load':'pass',
        'save_and_map_menu_previews':'pass',
        'backups':'pass','rolling_autosaves':'pass','quicksave_load':'pass',
        'resolved_player_pose':'pass','collision_history_reset':'pass',
        'continue_selection':'pass','visual_ui_and_cheat_engine':'not tested by this harness'},indent=2)+'\n',encoding='utf8')


if __name__ == '__main__':
    main()
