"""Compile/run actual Blitz timing functions without opening or driving a game UI.

Synthetic time verifies equal movement steps, slow motion and smoothing across FPS.
The pacing check uses real interrupt time, omitting ONLY Flip (no graphics context).
It does not measure rendering performance or validate focus/Alt+Tab behavior.
"""
from pathlib import Path
import blitz3d_toolchain as engine
import argparse, json, os, re, shutil, subprocess


def function(source, name):
    match = re.search(r'(?ms)^Function ' + re.escape(name) + r'[#%$]?\([^\n]*\n.*?^End Function', source)
    assert match, name
    return match[0]


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--blitz', type=Path, help=argparse.SUPPRESS)
    ap.add_argument('--output', type=Path, required=True)
    args = ap.parse_args()
    full = Path(__file__).resolve().parents[1] / 'src/engine/includes'
    out = args.output.resolve()
    out.mkdir(parents=True, exist_ok=False)
    runtime = (full / 'modern_runtime.bb').read_text('utf8')
    pacing = function(runtime, 'modern_present').replace(' Flip False', ' ; No graphics context in this timing-only harness.')
    body = '''
Global dev_frames,dev_frame_start,dev_fps#
Global modern_fps,modern_next,modern_pace_ready,modern_fraction#
Global modern_log_frames,modern_log_start,modern_diagnostics,m_section,m_menu
Global ms,lastms,gt,modern_previous,modern_reset,modern_step#,in_gtmod#=1.,f#
Global modern_gt_fraction#,test_clock,test_failures
Global modern_time_bank=CreateBank(8)
Global report=WriteFile(SystemProperty("APPDIR")+"timing.txt")
Function check(ok,label$)
 WriteLine report,"CHECK "+label$
 If Not ok Then test_failures=test_failures+1:WriteLine report,"FAIL "+label$
 CloseFile report
 report=OpenFile(SystemProperty("APPDIR")+"timing.txt")
 SeekFile report,FileSize(SystemProperty("APPDIR")+"timing.txt")
End Function
Function modern_clock()
 Return test_clock
End Function
Function modern_focus()
 Return 0
End Function
Function modern_log(message$)
End Function
Function dev_draw()
 ; No GUI is constructed in this clock-only harness.
End Function
Dim rates(3)
rates(0)=50:rates(1)=60:rates(2)=120:rates(3)=144
For r=0 To 3
 modern_fps=rates(r)
 gt=0:modern_previous=0:modern_reset=0:modern_gt_fraction#=0.
 steps#=0.:heading#=90.:damping#=1.
 For frame=1 To modern_fps*30
  test_clock=Floor(Float(frame)*1000./Float(modern_fps))
  modern_frame():modern_game_time()
  steps#=steps#+f#
  ; Compare the first second against the original 50 Hz reference.
  If frame<=modern_fps Then
   heading#=heading#*(1.-modern_blend(1./30.,f#))
   damping#=damping#*(0.96^f#)
  EndIf
 Next
 check(gt=30000,"game clock at "+modern_fps)
 check(Abs(steps#-1500.)<0.1,"movement steps at "+modern_fps)
 check(Abs(heading#-90.*((1.-1./30.)^50.))<0.001,"AI smoothing at "+modern_fps)
 check(Abs(damping#-(0.96^50.))<0.0001,"particle damping at "+modern_fps)
 gt=0:modern_previous=0:modern_gt_fraction#=0.
 For frame=1 To modern_fps*30
  test_clock=Floor(Float(frame)*1000./Float(modern_fps))
  in_gtmod#=0.375
  modern_frame():modern_game_time()
 Next
 check(gt=11250,"fractional slow-motion clock at "+modern_fps)
 WriteLine report,"RATE "+modern_fps+" gt="+gt+" steps="+steps#+" heading="+heading#+" damping="+damping#
Next
modern_previous=100:test_clock=1100:modern_reset=0
modern_frame():check(Abs(f#-5.)<0.00001,"100 ms stall clamp")
modern_reset=1:test_clock=2100
modern_frame():check(f#=0.,"load/resume resets elapsed time")
test_clock=2000:modern_frame():check(f#=0.,"negative elapsed guard")
For r=1 To 3
 modern_fps=rates(r):modern_pace_ready=0
 modern_present()
 started=MilliSecs()
 For frame=1 To modern_fps*2
  modern_present()
 Next
 duration=MilliSecs()-started
 measured#=Float(modern_fps*2)*1000./Float(duration)
 check(Abs(measured#/Float(modern_fps)-1.)<0.05,"real timer pacing at "+modern_fps)
 WriteLine report,"PACING "+modern_fps+" measured="+measured#+" duration="+duration
Next
; External speed tools alter the simulation clock, never the presentation clock.
Dim speeds(4)
speeds(0)=1:speeds(1)=3:speeds(2)=5:speeds(3)=10:speeds(4)=1
modern_fps=144:modern_pace_ready=0
For r=0 To 4
 modern_previous=0:modern_reset=0:gt=0:modern_gt_fraction#=0.
 started=modern_walltime()
 For frame=1 To 144
  test_clock=Floor(Float(frame)*1000.*Float(speeds(r))/144.)
  modern_frame():modern_game_time():modern_present()
 Next
 duration=modern_walltime()-started
 check(Abs(Float(duration)-1000.)<100.,"real pacing after speed change "+speeds(r))
 check(Abs(gt-1000*speeds(r))<=1,"accelerated game time "+speeds(r))
 WriteLine report,"SPEED "+speeds(r)+" real_ms="+duration+" game_ms="+gt
Next
modern_next=modern_walltime()+10000:modern_pace_ready=1
started=modern_walltime():modern_present()
check(modern_walltime()-started<100,"stale future deadline discarded")
modern_fps=0:modern_next=modern_walltime()+10000:modern_pace_ready=1
started=modern_walltime()
For frame=1 To 1000:modern_present():Next
check(modern_walltime()-started<100,"uncapped presentation never waits for a deadline")
check(modern_pace_ready=0,"uncapped presentation resets the capped deadline")
modern_fps=144:modern_present()
check(modern_pace_ready=1,"returning from uncapped mode starts a fresh deadline")
WriteLine report,"FAILURES "+test_failures
CloseFile report
End
'''
    code = (full / 'modern_math.bb').read_text('utf8') + '\n'
    for name in ('modern_frame', 'modern_game_time', 'modern_walltime', 'modern_record_frame'):
        code += function(runtime, name) + '\n'
    # Globals must precede functions in Blitz; append definitions after the harness.
    code += function((full / 'devmenu.bb').read_text('utf8'), 'dev_record_frame') + '\n'
    code = body + '\n' + code + '\n' + pacing + '\n'
    source = out / 'timing.bb'
    source.write_bytes(code.replace('\n', '\r\n').encode('ascii'))
    exe = out / 'timing.exe'
    engine.compile_test(source, exe, cwd=out)
    subprocess.run([str(exe)], cwd=out, timeout=30, check=True,
                   creationflags=subprocess.CREATE_NO_WINDOW)
    result = (out / 'timing.txt').read_text('ascii')
    print(result)
    assert 'FAILURES 0' in result and '\nFAIL ' not in '\n' + result, result
    (out / 'report.json').write_text(json.dumps({'timing': 'pass', 'output': result,
        'renderingAndFocus': 'not tested by this harness'}, indent=2) + '\n', 'utf8')


if __name__ == '__main__':
    main()
