; One fresh WASM instance per trial. All timings use the real production clock.
Function profile(operation$)
 Return Int(web_service("profile",operation$))
End Function

Global profile_scene=profile("scene"),profile_water=profile("water"),profile_fps=profile("fps")
Global profile_start,profile_x#,profile_y#,profile_z#,profile_yaw#,profile_time#
Function profile_camera()
 profile_time#=Float(modern_clock()-profile_start)/1000.
 PositionEntity cam,profile_x#,profile_y#,profile_z#
 RotateEntity cam,0,profile_yaw#+profile_time#*12.,0
End Function
SeedRnd 12345
set_musicvolume#=0:set_fxvolume#=0
If profile_scene=0 Then
 menu_start():parse_all():seq_update()
Else
 m_section=Csection_game_sp:m_menu=0:g_unpaused=1
 If load_map("maps\adventure\map02.s2","")=0 Then RuntimeError "Profile island did not load"
 parse_all():game_setcam()
 profile_x#=EntityX(cam):profile_y#=EntityY(cam):profile_z#=EntityZ(cam):profile_yaw#=EntityYaw(cam)
EndIf
set_water=profile_water:e_environment_setup_water()
modern_fps=profile_fps
modern_reset_clock()
profile_start=modern_clock()
profile("ready")
Repeat
 profile("frame")
 If profile_scene=0 Then
  menu()
 Else
  game()
 EndIf
 gframe=gframe+1
 modern_present()
 profile("end")
Until profile("finished")
e_clear():EndGraphics()
profile("done")
End
