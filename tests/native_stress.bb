; Runs only in the isolated source/runtime copy made by benchmark_native.py.
; Real game loop, real clock, real window/swapchain; no synthetic input.
Global bench_bank=CreateBank(8),bench_low,bench_high,bench_focus_lost
Global bench_minimized,bench_sync
Global bench_active,bench_pitch#,bench_yaw#,bench_x#,bench_y#,bench_z#
Global bench_reflections,bench_triangles
Dim bench_started#(8)
Dim bench_phase#(8)
Dim bench_samples#(12,20000)

Function bench_fail(message$)
 WriteStderr "BENCHMARK ERROR: "+message$+Chr(10)
 EndGraphics():End
End Function

Function bench_reset_timer()
 modern_counter(bench_bank)
 bench_low=PeekInt(bench_bank,0):bench_high=PeekInt(bench_bank,4)
End Function

Function bench_now#()
 modern_counter(bench_bank)
 Return modern_counter_elapsed(bench_low,bench_high,PeekInt(bench_bank,0),PeekInt(bench_bank,4),modern_counter_hz#)
End Function

Function bench_begin(slot)
 bench_started(slot)=bench_now()
End Function

Function bench_end(slot)
 bench_phase(slot)=bench_phase(slot)+bench_now()-bench_started(slot)
End Function

Function bench_camera()
 If bench_active=0 Then Return
 m_menu=0:g_unpaused=1
 PositionEntity cam,bench_x,bench_y,bench_z
 PositionEntity camxz,bench_x,0,bench_z
 RotateEntity cam,bench_pitch,bench_yaw,0
End Function

Function bench_scene(count)
 Local path$="maps\stress_"+count+".s2"
 If FileType(path$)=1 Then Return
 SeedRnd 57013
 m_section=Csection_editor
 e_clear():editor_defaultmapsettings()
 map_freezetime=1:map_hour=12:map_music$=""
 set_grass=0:set_water=3
 ter_tex_color=CreateTexture(256,256)
 e_terrain(64,"generated")
 Local bx,bz,edge#,height#
 For bx=0 To 64
  For bz=0 To 64
   edge#=Min(Min(bx,64-bx),Min(bz,64-bz))
   height#=0.495+Min(edge#/8.,1.)*0.045
   ModifyTerrain ter,bx,bz,height
  Next
 Next
 in_opt(4)=0:editor_gencolormap()
 e_environment():grass_map():grass_heightmap()
 Local n,kind,px#,pz#
 For n=1 To count
  px=Rnd(-1400,1400):pz=Rnd(-850,1500)
  Select n Mod 8
   Case 0 kind=1
   Case 1 kind=2
   Case 2 kind=3
   Case 3 kind=10
   Case 4 kind=11
   Case 5 kind=12
   Case 6 kind=63
   Case 7 kind=64
  End Select
  If set_object(-1,kind,px,pz,Rnd(360))<0 Then RuntimeError "Stress object unavailable"
 Next
 set_info(-1,1,0,e_tery(0,-1150)+30,-1150)
 PositionEntity cam,0,e_tery(0,-1150)+45,-1150:RotateEntity cam,0,0,0
 map_name$="Stranded II stress scene ("+count+" objects)"
 If save_map(path$,"map","")=0 Then RuntimeError "Cannot save stress scene"
End Function

Function bench_load(scene$,count,water,grass,view,terrain,wind,blur,effects,fog)
 WriteStdout "BENCHMARK loading "+scene$+" objects="+count+Chr(10)
 bench_active=0
 If scene$="stress" Then bench_scene(count)
 set_water=water:set_grass=grass:set_viewrange=view:set_terrain=terrain
 set_windsway=wind:set_mb=blur:set_mb_alpha=0.35:set_effects=effects:set_fog=fog
 set_musicvolume=0:set_fxvolume=0
 settings_apply_viewrange():prepare_cache_viewranges()
 m_section=Csection_game_sp:m_menu=0:g_unpaused=1
 Local path$="maps\stress_"+count+".s2"
 If scene$="island" Then path$="maps\adventure\map02.s2"
 SeedRnd 57013
 If load_map(path$,"")=0 Then RuntimeError "Stress scene did not load"
 parse_all()
 ; The island's on:load opens the diary. Close it in test state before timing.
 m_menu=0:g_unpaused=1:game_setcam()
 map_freezetime=1:map_hour=12:map_minute=0:e_environment_update_light()
 If scene$="stress" Then
  bench_x=0:bench_z=-1150:bench_y=e_tery(bench_x,bench_z)+45:bench_yaw=0
 Else
  bench_x=0:bench_z=-1200:bench_y=Max(5,e_tery(bench_x,bench_z))+45:bench_yaw=0
 EndIf
 bench_active=1:bench_camera()
 grass_x=-2147483648:grass_y=-2147483648:grass_spread()
 modern_reset_clock():bench_reset_timer()
End Function

Function bench_trial(name$,warmup,duration,screenshot)
 Local frame_index,slot,stamp#,finish#,game_end#,frame_ms#,previous#
 Local stream,pixel,completed#,drain_start#
 bench_focus_lost=0:bench_minimized=0
 modern_reset_clock():bench_reset_timer()
 While bench_now()<warmup
  game():gframe=gframe+1:modern_present():qol_present()
 Wend
 If m_menu<>0 Then RuntimeError "Benchmark scene opened a menu"
 If screenshot Then SaveBuffer(BackBuffer(),set_rootdir$+"captures\"+name$+".bmp")
 ; Drain warm-up work before starting. Readback waits for the actual GPU,
 ; so background submission speed cannot be mistaken for completed FPS.
 pixel=ReadPixel(0,0,BackBuffer())
 bench_reset_timer():previous=bench_now()
 bench_focus_lost=0:bench_minimized=0
 Repeat
  For slot=0 To 8:bench_phase(slot)=0:Next
  bench_reflections=0
  stamp=bench_now()
  game()
  game_end=bench_now()
  gframe=gframe+1
  bench_begin(7):modern_present():qol_present():bench_end(7)
  If bench_sync Then
   bench_begin(8):pixel=ReadPixel(0,0,BackBuffer()):bench_end(8)
  EndIf
  finish=bench_now():frame_ms=finish-previous:previous=finish
  bench_samples(0,frame_index)=frame_ms
  For slot=0 To 7:bench_samples(slot+1,frame_index)=bench_phase(slot):Next
  bench_samples(9,frame_index)=game_end-stamp
  bench_samples(10,frame_index)=bench_triangles
  bench_samples(11,frame_index)=bench_reflections
  bench_samples(12,frame_index)=bench_phase(8)
  frame_index=frame_index+1
 Until finish>=duration Or frame_index>=20000 Or bench_minimized
 drain_start=bench_now():pixel=ReadPixel(0,0,BackBuffer()):completed=bench_now()
 stream=WriteFile(set_rootdir$+"samples\"+name$+".csv")
 WriteLine stream,"frame_ms,update_ms,cull_ms,water_ms,world_ms,blur_ms,grass_ms,reflection_ms,present_ms,game_ms,triangles,reflections,gpu_wait_ms"
 Local line$,row
 For row=0 To frame_index-1
  line$=""
  For slot=0 To 12
   If slot>0 Then line$=line$+","
   line$=line$+Str(bench_samples(slot,row))
  Next
  WriteLine stream,line$
 Next
 CloseFile stream
 stream=WriteFile(set_rootdir$+"samples\"+name$+".meta")
 WriteLine stream,"schema=2"
 WriteLine stream,"menu="+m_menu
 WriteLine stream,"frames="+frame_index
 WriteLine stream,"elapsed_ms="+finish
 WriteLine stream,"completed_ms="+completed
 WriteLine stream,"drain_ms="+(completed-drain_start)
 WriteLine stream,"focus_lost="+bench_focus_lost
 WriteLine stream,"minimized="+bench_minimized
 WriteLine stream,"sync_each_frame="+bench_sync
 WriteLine stream,"width="+GraphicsWidth()
 WriteLine stream,"height="+GraphicsHeight()
 WriteLine stream,"camera="+bench_x+","+bench_y+","+bench_z+","+bench_pitch+","+bench_yaw
 WriteLine stream,"options="+set_water+","+set_grass+","+set_viewrange+","+set_terrain+","+set_windsway+","+set_mb+","+set_effects+","+set_fog
 WriteLine stream,"grass_meshes="+((grass_c+1)*(grass_c+1))
 WriteLine stream,"reflection_size="+set_water_texsize
 CloseFile stream
 WriteStdout "BENCHMARK "+name$+": "+frame_index+" frames / "+finish+" ms; focus_lost="+bench_focus_lost+Chr(10)
End Function

AppTitle "Stranded II - isolated rendering benchmark"
ShowPointer()
Global bench_plan=ReadFile(set_rootdir$+"benchmark-plan.csv")
If bench_plan=0 Then RuntimeError "Missing benchmark plan"
Global bench_line$,bench_name$,bench_scene$,bench_count,bench_screenshot
Global bench_warmup,bench_duration
While Not Eof(bench_plan)
 bench_line$=ReadLine(bench_plan)
 If bench_line$<>"" Then
  split(bench_line$,",",17)
  bench_name=splits(0):bench_scene=splits(1):bench_count=Int(splits(2))
  ; Copy all parameters before calling game functions that reuse splits().
  Local bw=Int(splits(3)),bg=Int(splits(4)),bv=Int(splits(5)),bt=Int(splits(6))
  Local bwi=Int(splits(7)),bmb=Int(splits(8)),be=Int(splits(9)),bf=Int(splits(10))
  bench_pitch=Float(splits(11)):modern_fps=Int(splits(12))
  bench_sync=Int(splits(13))
  bench_warmup=Int(splits(14)):bench_duration=Int(splits(15)):bench_screenshot=Int(splits(16))
  bench_load(bench_scene,bench_count,bw,bg,bv,bt,bwi,bmb,be,bf)
  WriteStdout "BENCHMARK START "+bench_name+Chr(10)
  bench_trial(bench_name,bench_warmup,bench_duration,bench_screenshot)
 EndIf
Wend
CloseFile bench_plan
qol_shutdown():e_clear():EndGraphics()
End
