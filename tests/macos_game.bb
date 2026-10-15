; Appended to the shared full-engine fixture in an isolated native runtime.
; Exercise real frame functions after the synthetic intro/transition checks.
menu_start()
For nativeFrame=1 To 100
 compat_clock=compat_clock+20
 menu()
 modern_present()
Next
compat_check(SaveBuffer(BackBuffer(),set_rootdir$+"native-menu.bmp"),"native menu screenshot")

m_section=Csection_game_sp:m_menu=0:g_unpaused=1
compat_check(load_map("maps\adventure\map02.s2","")=1,"native playable island load")
parse_all()
For nativeFrame=1 To 180
 compat_clock=compat_clock+20
 game()
 modern_present()
Next
compat_check(SaveBuffer(BackBuffer(),set_rootdir$+"native-island.bmp"),"native island screenshot")
; Real host time, identical island loop and completed GPU work. This reports
; fixture throughput/measurement overhead, not foreground display FPS.
Local profile_saved_fps=modern_fps,profile_pixel,profile_start,profile_duration,profile_text$
Local profile_report=WriteFile(set_rootdir$+"native-profiler.txt")
modern_fps=0
DebugUiOpen 0
For profile_trial=0 To 5
 DebugUiProfileRecording profile_trial Mod 2
 DebugUiProfileBoundary()
 profile_pixel=ReadPixel(0,0,BackBuffer())
 profile_start=modern_walltime()
 For profile_frame=1 To 60
  compat_clock=compat_clock+20
  game()
  modern_present()
 Next
 profile_pixel=ReadPixel(0,0,BackBuffer())
 profile_duration=modern_walltime()-profile_start
 WriteLine profile_report,"TRIAL "+profile_trial+" recording="+(profile_trial Mod 2)+" completed_ms="+profile_duration+" frames=60"
Next
DebugUiProfileRecording 0
compat_clock=compat_clock+20:game():modern_present()
; The newest frame's asynchronous map callback may still be pending. Pump
; presentation events in this fixture with capture stopped before inspecting it.
For profile_frame=1 To 8:Flip False:Next
profile_text=DebugUiProfileSnapshot()
compat_check(Instr(profile_text,"frame_ms=")>0 And Instr(profile_text,"total_ms=")>0,"native profiler records while overlay is closed")
compat_check(Instr(profile_text,"GPU=ready")>0 Or Instr(profile_text,"GPU=unsupported")>0,"native profiler resolves GPU results or reports unsupported")
WriteLine profile_report,profile_text
CloseFile profile_report
modern_fps=profile_saved_fps
; SAVE_MENU_CHECKS
compat_check(save_map("saves\NATIVE-CHECK.sav","sav","")=1,"native checked save replacement")
compat_check(modern_save_valid("saves\NATIVE-CHECK.sav"),"native saved file preflight")
Local nativeX#=EntityX(g_cplayer\h),nativeY#=EntityY(g_cplayer\h),nativeZ#=EntityZ(g_cplayer\h)
; The shared saver deliberately writes the collision-resolved pose.
If col_player_valid And col_player_managed(g_cplayer) Then
 nativeX#=col_player_x#:nativeY#=col_player_y#:nativeZ#=col_player_z#
EndIf
compat_check(save_map("saves\NATIVE-CHECK.sav","sav","")=1,"native save backup replacement")
compat_check(modern_save_valid("saves\NATIVE-CHECK.sav.bak"),"native save backup preflight")
compat_check(load_map("saves\NATIVE-CHECK.sav","")=1,"native saved game reload")
WriteLine compat_report,"SAVED POSITION "+nativeX#+" "+nativeY#+" "+nativeZ#
compat_position("native-reloaded-player",g_cplayer\h)
compat_check(Abs(EntityX(g_cplayer\h)-nativeX#)<0.01 And Abs(EntityY(g_cplayer\h)-nativeY#)<0.01 And Abs(EntityZ(g_cplayer\h)-nativeZ#)<0.01,"native saved player position")
mac_service("search-open","한글")
mac_service("search-set","나무 돌")
compat_check(mac_service("search-text")="나무 돌","native Cocoa Korean text roundtrip")
mac_service("search-close")
