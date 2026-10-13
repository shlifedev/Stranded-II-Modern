; Shared presentation, focus recovery and elapsed-time pacing.
; Windows services are compiled into the embedded Blitz runtime.
Include "includes\modern_math.bb"
Global dev_paused,dev_initialized,dev_strings_ready
Global modern_fps=144,modern_borderless=1,modern_width=1280,modern_height=720
Global modern_hwnd,modern_ui_ready,modern_previous,modern_reset=1
Global modern_next,modern_pace_ready,modern_resume_count,modern_diagnostics
Global modern_fraction#,modern_gt_fraction#,modern_step#,modern_log_frames,modern_log_start
Global modern_log_path$
Global modern_native,modern_time_bank=CreateBank(8)
Global modern_counter_bank=CreateBank(8),modern_counter_low,modern_counter_high,modern_counter_ready
Global modern_counter_hz#,modern_clock_ms,modern_clock_fraction#

Function modern_log(message$)
 DebugUiLog "timing",3,message$
 If modern_diagnostics=0 Then Return
 Local stream=OpenFile(modern_log_path$)
 If stream=0 Then stream=WriteFile(modern_log_path$)
 If stream<>0 Then
  SeekFile stream,FileSize(modern_log_path$)
  WriteLine stream,message$
  CloseFile stream
 EndIf
End Function

Function modern_option(key$,value$)
 Select Lower(Trim(key$))
  Case "fps"
   Select value$
    Case "0","60","120","144":modern_fps=Int(value$)
    Default:modern_fps=144:con_add("MODERN: invalid fps; using 144")
   End Select
  Case "display"
   Select Lower(value$)
    Case "borderless":modern_borderless=1
    Case "windowed":modern_borderless=0
    Default:modern_borderless=1:con_add("MODERN: invalid display; using borderless")
   End Select
  Case "render_width"
   modern_width=Int(value$)
   If modern_width<800 Or modern_width>8192 Then modern_width=1280:con_add("MODERN: invalid width; using 1280")
  Case "render_height"
   modern_height=Int(value$)
   If modern_height<600 Or modern_height>8192 Then modern_height=720:con_add("MODERN: invalid height; using 720")
  Case "diagnostics"
   modern_diagnostics=(value$="1")
  Case "resolution"
   modern_native=(Lower(value$)="desktop")
  Default
   remaster_option(Lower(Trim(key$)),value$)
 End Select
End Function

Function modern_config()
 modern_dpi_aware()
 remaster_init()
 Local stream=ReadFile(set_rootdir$+"core\modern.ini")
 Local line$,split,word$,rest$,pos
 If stream<>0 Then
  While Not Eof(stream)
   line$=Trim(ReadLine(stream))
   split=Instr(line$,"=")
   If split>1 And Left(line$,1)<>";" Then modern_option(Left(line$,split-1),Trim(Mid(line$,split+1)))
  Wend
  CloseFile stream
 EndIf
 modern_capture_settings()
 ; Explicit switches take precedence over the config file, in command order.
 rest$=Lower(Trim(set_cmd$))+" "
 While Len(rest$)>0
  pos=Instr(rest$," ")
  If pos=0 Then Exit
  word$=Left(rest$,pos-1):rest$=Trim(Mid(rest$,pos+1))
  If Len(rest$)>0 Then rest$=rest$+" "
  Select word$
   Case "-win":modern_borderless=0
   Case "-borderless":modern_borderless=1
   Case "-fps"
    pos=Instr(rest$," ")
    If pos>0 Then
     modern_option("fps",Left(rest$,pos-1))
     rest$=Mid(rest$,pos+1)
    Else
     modern_option("fps","")
    EndIf
  End Select
 Wend
 modern_resolve_resolution()
 set_scrx=modern_width:set_scry=modern_height:set_scrbit=32
 set_scrwin=2
 If modern_borderless Then set_scrwin=3
 modern_log_path$=set_rootdir$+"core\modern.log"
 con_add("MODERN: "+modern_fps+" FPS, "+set_scrx+"x"+set_scry+", borderless="+modern_borderless)
 modern_log("START fps="+modern_fps+" render="+set_scrx+"x"+set_scry+" borderless="+modern_borderless)
End Function

Function modern_window()
 modern_hwnd=Int(SystemProperty("AppHWND"))
 If modern_borderless And modern_hwnd<>0 Then
  ; WS_POPUP | WS_VISIBLE; HWND_NOTOPMOST; SWP_FRAMECHANGED | SWP_NOACTIVATE.
  modern_win_style(modern_hwnd,-16,$90000000)
  modern_win_position(modern_hwnd,-2,0,0,modern_win_metric(0),modern_win_metric(1),$30)
 EndIf
 modern_reset_clock()
End Function

Function modern_clock()
 If modern_counter_hz#=0. Then
  If modern_counter_frequency(modern_counter_bank)=0 Then RuntimeError "Performance counter unavailable"
  modern_counter_hz#=Float(PeekInt(modern_counter_bank,4))*4294967296.+Float(PeekInt(modern_counter_bank,0) And $7FFFFFFF)
  If PeekInt(modern_counter_bank,0)<0 Then modern_counter_hz#=modern_counter_hz#+2147483648.
  If modern_counter_hz#<=0. Then RuntimeError "Invalid performance counter frequency"
 EndIf
 If modern_counter(modern_counter_bank)=0 Then Return modern_clock_ms
 Local low=PeekInt(modern_counter_bank,0),high=PeekInt(modern_counter_bank,4),whole
 If modern_counter_ready Then
  modern_clock_fraction#=modern_clock_fraction#+modern_counter_elapsed(modern_counter_low,modern_counter_high,low,high,modern_counter_hz#)
  whole=Floor(modern_clock_fraction#)
  modern_clock_ms=modern_clock_ms+whole
  modern_clock_fraction#=modern_clock_fraction#-Float(whole)
 EndIf
 modern_counter_low=low:modern_counter_high=high:modern_counter_ready=1
 Return modern_clock_ms
End Function

Function modern_counter_elapsed#(previouslow,previoushigh,low,high,frequency#)
 ; Subtract the integer counter words before converting small deltas to float.
 ; Converting absolute QPC uptime to Blitz's 32-bit float would lose precision.
 Local delta=low-previouslow,upper=high-previoushigh,ticks#
 If (low Xor $80000000)<(previouslow Xor $80000000) Then upper=upper-1
 If upper<0 Then Return 0.
 ticks#=Float(upper)*4294967296.+Float(delta And $7FFFFFFF)
 If delta<0 Then ticks#=ticks#+2147483648.
 Return ticks#*1000./frequency#
End Function

Function modern_reset_clock()
 modern_previous=modern_clock()
 ms=modern_previous:lastms=ms
 modern_reset=1:modern_pace_ready=0
 modern_log_start=modern_walltime():modern_log_frames=0
End Function

; Rendering uses real interrupt time. QPC is the high-resolution simulation
; clock, so external speed tools can accelerate play without coarse timer steps.
Function modern_walltime()
 modern_interrupt_time(modern_time_bank)
 Local i,remainder,value,result
 ; Exact unsigned 64-bit / 10000 division, retaining low 32-bit milliseconds.
 For i=7 To 0 Step -1
  value=remainder*256+PeekByte(modern_time_bank,i)
  result=result*256+value/10000
  remainder=value Mod 10000
 Next
 Return result
End Function

Function modern_focus()
 If modern_hwnd=0 Then Return 0
 If modern_win_foreground()=modern_hwnd And modern_win_iconic(modern_hwnd)=0 Then Return 0
 modern_clock()
 Local paused=modern_walltime()
 ShowPointer()
 mb_reset()
 Repeat
  modern_sleep(10)
  Delay 0 ; Pump Blitz window messages without a timeGetTime-based wait.
 Until modern_win_foreground()=modern_hwnd And modern_win_iconic(modern_hwnd)=0
 modern_counter_ready=0 ; Exclude all elapsed simulation time while unfocused.
 HidePointer()
 flushinput()
 in_dnd=0:in_sticktoslide=0:in_frapdown=0:in_jumprehit=0
 If m_section=Csection_game_sp Or m_section=Csection_game_mp Then
  If m_menu=0 Then MoveMouse set_scrx/2,set_scry/2
 EndIf
 MouseXSpeed():MouseYSpeed():MouseZSpeed()
 in_mxs=0:in_mys=0:in_mzs=0
 If modern_ui_ready Then modern_rebuild_ui()
 modern_resume_count=modern_resume_count+1
 modern_log("RESUME count="+modern_resume_count+" paused_ms="+(modern_walltime()-paused)+" gt="+gt)
 modern_reset_clock()
 Return 1
End Function

Function modern_frame()
 modern_focus()
 lastms=ms:ms=modern_clock()
 Local elapsed=modern_delta(ms-modern_previous)
 modern_previous=ms
 If modern_reset Then elapsed=0:modern_reset=0
 modern_step#=Float(elapsed)*in_gtmod#
 in_gtmod#=1.
 f#=modern_step#/20.
End Function

Function modern_game_time()
 modern_gt_fraction#=modern_gt_fraction#+modern_step#
 Local whole=Floor(modern_gt_fraction#)
 gt=gt+whole
 modern_gt_fraction#=modern_gt_fraction#-Float(whole)
End Function

Function modern_present()
 ; All main/menu/editor/modal loops use this path; never busy-spin.
 If modern_focus() Then Return
 dev_draw()
 If modern_fps=0 Then
  ; Keep Flip's event pump and the normal simulation clock when uncapped.
  modern_pace_ready=0:modern_fraction#=0.
  Flip False
  modern_record_frame()
  Return
 EndIf
 Local now=modern_walltime(),advance
 ; Discard stale deadlines after pauses or a clock discontinuity.
 If modern_next-now>100 Or now-modern_next>100 Then modern_pace_ready=0
 If modern_pace_ready=0 Then
  modern_next=now:modern_fraction#=0.:modern_pace_ready=1
 EndIf
 While modern_next-now>0
  ; Blitz Delay polls timeGetTime, which CE redirects to coarse GetTickCount.
  ; Native Sleep waits in real time; Flip below still pumps window messages.
  modern_sleep(1)
  now=modern_walltime()
 Wend
 Flip False
 modern_fraction#=modern_fraction#+1000./Float(modern_fps)
 advance=Floor(modern_fraction#)
 modern_fraction#=modern_fraction#-Float(advance)
 If now-modern_next>advance Then modern_next=now
 modern_next=modern_next+advance
 modern_record_frame()
End Function

Function modern_record_frame()
 dev_record_frame()
 If modern_diagnostics Then
  Local now=modern_walltime()
  modern_log_frames=modern_log_frames+1
  Local duration=now-modern_log_start
  If duration>=5000 Then
   modern_log("FPS actual="+(1000.*Float(modern_log_frames)/Float(duration))+" target="+modern_fps+" section="+m_section+" menu="+m_menu+" gt="+gt)
   modern_log_frames=0:modern_log_start=now
  EndIf
 EndIf
End Function
