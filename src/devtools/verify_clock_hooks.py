"""Reproduce CE's persistent coarse timeGetTime hook in an isolated test process.

Only the temporary executable patches its own timeGetTime entry point, restoring
the original bytes before exit. No other process, OS setting or DLL file changes.
This tests the clock redirection responsible for the regression, not CE's UI.
"""
from pathlib import Path
import argparse
import json
import os
import shutil
import subprocess

from verify_timing import function


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--blitz', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    root = Path(__file__).resolve().parents[2]
    out = args.output.resolve()
    out.mkdir(parents=True, exist_ok=False)
    blitz = out / 'toolchain'
    shutil.copytree(args.blitz, blitz)
    (blitz / 'userlibs/clock_probe.decls').write_text('''
.lib "kernel32.dll"
probe_module%(name$):"GetModuleHandleA"
probe_proc%(module%,name$):"GetProcAddress"
probe_protect%(address%,size%,flags%,previous*):"VirtualProtect"
probe_read(destination*,source%,count%):"RtlMoveMemory"
probe_write(destination%,source*,count%):"RtlMoveMemory"
probe_flush%(process%,address%,size%):"FlushInstructionCache"
probe_process%():"GetCurrentProcess"
''', encoding='ascii')
    runtime = (root / 'src/engine/includes/modern_runtime.bb').read_text('utf8')
    code = r'''
Global modern_fps=144,modern_next,modern_pace_ready,modern_fraction#
Global modern_log_frames,modern_log_start,modern_diagnostics,m_section,m_menu
Global modern_time_bank=CreateBank(8)
Global modern_counter_bank=CreateBank(8),modern_counter_low,modern_counter_high,modern_counter_ready
Global modern_counter_hz#,modern_clock_ms,modern_clock_fraction#
Global report=WriteFile(SystemProperty("APPDIR")+"clock-hooks.txt"),failures
Function check(ok,label$)
 WriteLine report,"CHECK "+label$
 If Not ok Then failures=failures+1:WriteLine report,"FAIL "+label$
 CloseFile report
 report=OpenFile(SystemProperty("APPDIR")+"clock-hooks.txt")
 SeekFile report,FileSize(SystemProperty("APPDIR")+"clock-hooks.txt")
End Function
Function modern_focus()
 Return 0
End Function
Function modern_log(message$)
End Function

; Precision at large uptime, signed boundary, DWORD rollover, backwards samples.
check(Abs(modern_counter_elapsed($7FFFFFF0,1000000,$80000054,1000000,10000000.)-0.01)<0.00001,"signed low-word boundary")
check(Abs(modern_counter_elapsed($FFFFFFF0,1000000,$54,1000001,10000000.)-0.01)<0.00001,"unsigned low-word rollover")
check(modern_counter_elapsed(500,10,400,10,10000000.)=0.,"backward counter discarded")

; Match CE's timeGetTime -> GetTickCount redirection, including at 1x.
Global target=probe_proc(probe_module("winmm.dll"),"timeGetTime")
Global replacement=probe_proc(probe_module("kernel32.dll"),"GetTickCount")
Global original=CreateBank(5),jump=CreateBank(5),protection=CreateBank(4),oldprotection
check(target<>0 And replacement<>0,"timer entry points resolved")
If target=0 Or replacement=0 Then End
probe_read(original,target,5)
PokeByte jump,0,$E9:PokeInt jump,1,replacement-target-5
check(probe_protect(target,5,$40,protection),"test process timer page writable")
oldprotection=PeekInt(protection,0)
If oldprotection=0 Then End
probe_write(target,jump,5):probe_flush(probe_process(),target,5)
probe_protect(target,5,oldprotection,protection)

; Negative control: the previous Delay-based pacing under the same hook.
modern_pace_ready=0:legacy_present()
started=modern_walltime()
For frame=1 To 144:legacy_present():Next
duration=modern_walltime()-started
WriteLine report,"LEGACY fps="+(144000./Float(duration))+" real_ms="+duration

; New pacing AND game-clock smoothness with the coarse hook still installed.
Dim rates(2):rates(0)=60:rates(1)=120:rates(2)=144
For r=0 To 2
 modern_fps=rates(r):modern_pace_ready=0:modern_present()
 started=modern_walltime():simulation=modern_clock():previous=simulation:zero_steps=0:maxstep=0
 For frame=1 To modern_fps*2
  modern_present()
  current=modern_clock():stepms=current-previous:previous=current
  If stepms=0 Then zero_steps=zero_steps+1
  If stepms>maxstep Then maxstep=stepms
 Next
 duration=modern_walltime()-started
 measured#=Float(modern_fps*2)*1000./Float(duration)
 check(Abs(measured#/Float(modern_fps)-1.)<0.08,"pacing with coarse timer hook at "+modern_fps)
 check(zero_steps<modern_fps/20,"simulation advances smoothly at "+modern_fps)
 check(Abs(Float(current-simulation-duration))<15.,"simulation follows real 1x elapsed time")
 WriteLine report,"HOOKED target="+modern_fps+" fps="+measured#+" zero_steps="+zero_steps+" max_step_ms="+maxstep
Next

; Focus return resamples QPC without advancing the paused game clock.
previous=modern_clock():modern_sleep(50):modern_counter_ready=0
check(modern_clock()=previous,"focus rebase excludes background interval")

; Restore only this process's bytes. DLLs on disk were never modified.
probe_protect(target,5,$40,protection)
probe_write(target,original,5):probe_flush(probe_process(),target,5)
probe_protect(target,5,oldprotection,protection)
WriteLine report,"FAILURES "+failures
CloseFile report
End
'''
    for name in ('modern_walltime', 'modern_clock', 'modern_counter_elapsed'):
        code += '\n' + function(runtime, name) + '\n'
    pacing = function(runtime, 'modern_present').replace(' Flip False', ' ; No graphics context.')
    code += pacing + '\n'
    code += pacing.replace('Function modern_present()', 'Function legacy_present()').replace(
        'modern_sleep(1)', 'Delay 1') + '\n'
    source = out / 'clock-hooks.bb'
    source.write_bytes(code.replace('\n', '\r\n').encode('ascii'))
    exe = out / 'ClockHooks.exe'
    subprocess.run([str(blitz / 'bin/blitzcc.exe'), '-o', str(exe), str(source)],
                   cwd=out, env=dict(os.environ, blitzpath=str(blitz)), check=True)
    subprocess.run([str(exe)], cwd=out, timeout=30, check=True,
                   creationflags=subprocess.CREATE_NO_WINDOW)
    result = (out / 'clock-hooks.txt').read_text('ascii')
    print(result)
    assert 'FAILURES 0' in result and '\nFAIL ' not in '\n' + result, result
    (out / 'report.json').write_text(json.dumps({
        'persistent_coarse_timer_hook': 'pass', 'output': result,
        'scope': 'isolated process timer redirection; CE UI and graphics not tested',
    }, indent=2) + '\n', encoding='utf8')


if __name__ == '__main__':
    main()
