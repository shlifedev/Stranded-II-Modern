"""Run actual Blitz save/config/queue functions against disposable fixtures.

Save serialization is stubbed here; real game save/load remains a graphics smoke
test. Filesystem replacement and failure handling use the actual Windows APIs.
"""
from pathlib import Path
import argparse, ctypes, json, os, subprocess
from verify_timing import function
from verify_qol_logic import declarations


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--blitz', type=Path, required=True)
    ap.add_argument('--output', type=Path, required=True)
    args = ap.parse_args()
    root = Path(__file__).resolve().parents[1]
    includes = root / 'src/engine/includes'
    out = args.output.resolve()
    (out / 'saves').mkdir(parents=True)
    (out / 'core').mkdir()
    config = '# keep comment\ndiagnostics=1\ncustom_flag=yes\nfps=144\nresolution=custom\nrender_width=1280\nrender_height=720\nlanguage=ko\n'
    (out / 'core/modern.ini').write_text(config, encoding='utf8')
    locked = out / 'locked.txt'
    locked.write_bytes(b'original locked bytes')
    (out / 'locked.txt.tmp').write_bytes(b'replacement bytes')
    kernel = ctypes.WinDLL('kernel32', use_last_error=True)
    kernel.CreateFileW.argtypes = [ctypes.c_wchar_p, ctypes.c_uint32, ctypes.c_uint32, ctypes.c_void_p, ctypes.c_uint32, ctypes.c_uint32, ctypes.c_void_p]
    kernel.CreateFileW.restype = ctypes.c_void_p
    kernel.CloseHandle.argtypes = [ctypes.c_void_p]
    # Share reads, but deliberately prevent rename/deletion of the destination.
    lock = kernel.CreateFileW(str(locked), 0x80000000, 1, None, 3, 0, None)
    assert lock != ctypes.c_void_p(-1).value, ctypes.get_last_error()
    body = r'''
Global set_rootdir$=SystemProperty("APPDIR"),failures,writer_fail,serial,loaded$,messages,postevents
Global report=WriteFile("modern.txt"),in_quicksave,game_m_loadsave=1,m_section=1
Global modern_fps=144,modern_borderless=1,modern_width=1280,modern_height=720,modern_native=0
Global loc_pending$="ko",loc_saved$="ko"
Const Csection_editor=2,Cbmpf_green=4,Cbmpf_red=3,Ckey_quicksave=14,Ckey_quickload=15
Dim in_keyhit(20)
Type Tin_quicksave
 Field mode,map$,skills,items,vars,diary,states,locks
End Type
Type Tp
 Field typ
End Type
Function check(ok,label$)
 WriteLine report,"CHECK "+label$
 If Not ok Then failures=failures+1:WriteLine report,"FAIL "+label$
End Function
Function gui_msg(message$,color=0)
 messages=messages+1
End Function
Function if_msg(message$,color=0)
 messages=messages+1
End Function
Function con_add(message$,color=0)
End Function
Function modern_reset_clock()
End Function
Function parse_error(message$)
 check(False,message$)
End Function
Function parse_globalevent(event$)
 If event$="postsave" Then postevents=postevents+1
End Function
Function load_map(path$,password$)
 loaded$=path$
 For q.Tin_quicksave=Each Tin_quicksave
  Delete q
 Next
 Return True
End Function
Function save_map_staged(path$,mode$,password$,skills,items,vars,diary,states,locks)
 Local stream=WriteFile(path$),i
 If stream=0 Then Return False
 If writer_fail Then WriteLine stream,"partial":CloseFile stream:Return False
 serial=serial+1
 WriteLine stream,"### Stranded II fixture"
 WriteLine stream,serial
 WriteLine stream,"10 OCT 2026"
 WriteLine stream,"12:00:00"
 WriteLine stream,"default"
 WriteLine stream,"sav"
 For i=1 To 22000:WriteByte stream,0:Next
 WriteLine stream,""
 WriteLine stream,"### EOF Map File"
 WriteLine stream,"www.unrealsoftware.de"
 CloseFile stream
 Return True
End Function
Function saved_serial(path$)
 Local stream=ReadFile(path$),number
 If stream=0 Then Return -1
 ReadLine stream:number=Int(ReadLine(stream)):CloseFile stream
 Return number
End Function
'''
    body += (includes / 'modern_files.bb').read_text('utf8') + '\n'
    body += (includes / 'modern_saves.bb').read_text('utf8') + '\n'
    body += function((includes / 'e_save_map.bb').read_text('utf8'), 'save_map') + '\n'
    hooks = (includes / 'qol.bb').read_text('utf8')
    body += declarations(hooks) + '\n'
    body += 'Const Cedition_qol_default=0\nFunction qol_guides_load()\nEnd Function\n'
    for name in ('qol_init', 'qol_settings_reset', 'qol_setting_owned', 'qol_settings_write', 'qol_settings_apply'):
        body += function(hooks, name) + '\n'
    body += '\nqol_init()\n'
    settings = (includes / 'modern_settings.bb').read_text('utf8')
    body += settings[:settings.index('Function ')]
    for name in ('modern_fit_resolution', 'modern_resolve_resolution', 'modern_capture_settings', 'modern_settings_reset',
                 'modern_add_mode', 'modern_cycle_resolution', 'modern_options_save'):
        body += function(settings, name) + '\n'
    game_input = (includes / 'game_input.bb').read_text('utf8')
    start = game_input.index('\t\t\t\t\tIf game_m_loadsave=1 Then')
    end = game_input.index('\n\t\t\t\t\tEndIf', start) + len('\n\t\t\t\t\tEndIf')
    body += 'Function test_hotkeys()\n' + game_input[start:end] + '\nEnd Function\n'
    body += r'''
check(modern_commit_file("locked.txt","locked.txt.tmp")=0,"locked destination fails cleanly")
check(modern_file_error<>0,"Windows error retained")
check(modern_save_valid("saves/missing.sav")=0,"missing save rejected")
check(save_map("saves/manual.sav","sav","")=1,"first save")
check(modern_save_valid("saves/manual.sav"),"legacy format accepted")
check(save_map("saves/manual.sav","sav","")=1,"replace existing save")
check(saved_serial("saves/manual.sav")=2,"new save committed")
check(saved_serial("saves/manual.sav.bak")=1,"previous save backed up")
writer_fail=1
check(save_map("saves/manual.sav","sav","")=0,"failed serialization")
check(saved_serial("saves/manual.sav")=2,"failed write retains main save")
check(saved_serial("saves/manual.sav.bak")=1,"failed write retains backup")
check(modern_save_valid("saves/manual.sav.tmp")=0,"partial staging rejected")
check(postevents=2,"postsave only after successful commit")
writer_fail=0
For i=1 To 8
 Delay 25
 path$=modern_autosave_target()
 check(path$<>"","autosave target")
 check(save_map(path$,"sav","")=1,"autosave commit")
Next
For i=1 To 5
 check(modern_save_valid(modern_autosave_path(i)),"five rolling slots valid")
Next
check(modern_latest_save(1)="saves\AUTOSAVE_3.sav","latest autosave after wrap")
check(saved_serial(modern_latest_save(1))=10,"latest autosave content")
check(modern_latest_save()=modern_latest_save(1),"continue includes autosaves")
Delay 25
save_map("saves/recent.sav","sav","")
check(modern_latest_save()="saves\recent.sav","continue chooses newest manual save")
writer_fail=1
save_map("saves/broken.sav","sav","")
CopyFile "saves/broken.sav.tmp","saves/broken.sav"
check(modern_latest_save()="saves\recent.sav","continue ignores incomplete save and staging")
writer_fail=0
check(modern_stamp_newer($FFFFFFFF,10,0,10),"unsigned FILETIME low word")
check(modern_stamp_newer(0,11,$FFFFFFFF,10),"FILETIME high word")
in_keyhit(Ckey_quicksave)=1:test_hotkeys():test_hotkeys()
count=0
For q.Tin_quicksave=Each Tin_quicksave:count=count+1:Next
check(count=1,"hotkey creates one deferred request")
modern_process_saves()
check(modern_save_valid("saves/QUICKSAVE.sav"),"hotkey reaches writer")
in_keyhit(Ckey_quicksave)=0:in_keyhit(Ckey_quickload)=1:test_hotkeys()
modern_queue_save(1)
modern_process_saves()
check(loaded$="saves\QUICKSAVE.sav","quickload dispatch")
check(in_quicksave=0,"world-clearing load safely drains requests")
modern_queue_save(4):modern_process_saves()
check(loaded$=modern_latest_save(1),"autoload chooses latest rolling slot")
modern_capture_settings()
modern_pending_fps=60:modern_pending_native=1:modern_pending_borderless=0
modern_settings_reset()
check(modern_pending_fps=144 And modern_pending_native=0 And modern_pending_borderless=1,"cancel discards pending display settings")
modern_pending_fps=120:modern_pending_native=1:modern_pending_borderless=0:loc_pending$="en"
check(modern_options_save(),"settings committed")
check(modern_fps=144 And modern_native=0 And modern_borderless=1,"apply waits for restart")
check(modern_saved_fps=120 And loc_saved$="en","saved selections updated")
modern_settings_reset():check(modern_pending_fps=120,"reopening settings retains saved choices")
modern_mode_count=0
modern_add_mode(800,600):modern_add_mode(1920,1080):modern_add_mode(1920,1080):modern_add_mode(640,480)
modern_add_mode(2560,1440):modern_add_mode(3840,2160)
check(modern_mode_count=2,"resolution list excludes duplicate, undersized and unsafe modes")
modern_fit_resolution(2560,1440)
check(modern_fitted_width=2048 And modern_fitted_height=1152,"QHD fits legacy render limit without changing aspect")
modern_fit_resolution(3840,2160)
check(modern_fitted_width=2048 And modern_fitted_height=1152,"4K fits legacy render limit")
modern_fit_resolution(1080,3840)
check(modern_fitted_width>=800 And modern_fitted_height=2048,"portrait mode respects minimum UI width")
modern_width=2560:modern_height=1440:modern_native=0:modern_resolve_resolution()
check(modern_width=2048 And modern_height=1152,"oversized custom config also fits render limit")
modern_pending_native=1:modern_cycle_resolution(-1)
check(modern_pending_native=0 And modern_pending_width=1920,"resolution previous wraps")
modern_cycle_resolution(1):check(modern_pending_native=1,"resolution next reaches monitor mode")
modern_native=1:modern_resolve_resolution()
check(modern_width>=800 And modern_height>=600,"monitor resolution resolved")
WriteLine report,"FAILURES "+failures
CloseFile report
End
'''
    source = out / 'modern.bb'
    source.write_bytes(body.replace('\n', '\r\n').encode('utf8'))
    blitz = args.blitz.resolve()
    exe = out / 'modern.exe'
    try:
        subprocess.run([str(blitz / 'bin/blitzcc.exe'), '-o', str(exe), str(source)],
                       env=dict(os.environ, blitzpath=str(blitz)), cwd=out, check=True)
        subprocess.run([str(exe)], cwd=out, timeout=30, check=True,
                       creationflags=subprocess.CREATE_NO_WINDOW)
    finally:
        kernel.CloseHandle(lock)
    result = (out / 'modern.txt').read_text('utf8')
    print(result)
    assert 'FAILURES 0' in result and '\nFAIL ' not in '\n'+result, result
    assert locked.read_bytes() == b'original locked bytes'
    assert (out / 'locked.txt.tmp').read_bytes() == b'replacement bytes'
    saved = (out / 'core/modern.ini').read_text('utf8')
    for line in ('# keep comment', 'diagnostics=1', 'custom_flag=yes', 'fps=120',
                 'display=windowed', 'resolution=desktop', 'language=en'):
        assert line in saved, line
    assert (out / 'core/modern.ini.bak').read_text('utf8') == config
    assert len(list((out/'saves').glob('AUTOSAVE*.sav'))) == 5
    (out / 'report.json').write_text(json.dumps({'save_replacement': 'pass',
        'save_queue': 'pass', 'five_autosaves': 'pass', 'continue_selection': 'pass',
        'display_settings': 'pass', 'real_game_serialization': 'requires graphics smoke test'}, indent=2)+'\n', encoding='utf8')


if __name__ == '__main__':
    main()
