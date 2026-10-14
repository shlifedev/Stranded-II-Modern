; Disposable rendering scene. Actions run at the input boundary, never during ImGui drawing.
Const Cbench_limit=10000
Global bench_action,bench_job,bench_remove,bench_serial,bench_selected,bench_catalog=1,bench_search$
Global bench_layout=1,bench_seed=57013,bench_random,bench_ai,bench_animation,bench_camera_index
Global bench_reflection=2,bench_run,bench_auto,bench_warm_start,bench_trial_index,bench_trial_total
Global bench_saved_fps,bench_saved_recording,bench_base_camera,bench_base_ai,bench_base_animation
Global bench_requested,bench_actual,bench_failed,bench_rejected,bench_revision,bench_status$
Global bench_source,bench_drop
Global bench_measure_ms=8000,bench_warm_ms=3000
Global bench_px#,bench_pz#,bench_yaw#
Dim bench_saved#(18),bench_entry_pending(4),bench_base#(18),bench_compare(10)
Type TbenchGroup
 Field serial,class,typ,count=96,base_count,target,attempted,actual,failed,layout,seed
 Field spacing#=60.,height#,offset#
End Type
Type TbenchSpawn
 Field group,class,id,index
 Field x#,y#,z#,yaw#
End Type
Type TbenchPlacement
 Field group,index
 Field x#,y#,z#,yaw#
End Type
Type TbenchTrial
 Field condition,repetition,effect=-1,value
 Field multiplier#=1.,name$
End Type
Global bench_current.TbenchTrial

Function bench_label$(key$)
 Return dev_label("benchmark."+key$)
End Function

Function bench_name$(class,typ)
 Select class
  Case Cclass_object:Return ko_localize(Dobject_name$(typ))
  Case Cclass_unit:Return ko_localize(Dunit_name$(typ))
  Case Cclass_item:Return ko_localize(Ditem_name$(typ))
 End Select
End Function

Function bench_config_capture(slot)
 For i=0 To 4
  If slot=0 Then bench_saved(i)=remaster_enabled(i) Else bench_base(i)=remaster_enabled(i)
 Next
 Local water=set_water,grass=set_grass,view=set_viewrange,wind=set_windsway,reflection=bench_reflection
 If slot=0 Then
  bench_saved(5)=water:bench_saved(6)=grass:bench_saved(7)=view:bench_saved(8)=wind:bench_saved(9)=reflection
  bench_saved(10)=set_musicvolume:bench_saved(11)=set_fxvolume:bench_saved(12)=set_mb
 Else
  bench_base(5)=water:bench_base(6)=grass:bench_base(7)=view:bench_base(8)=wind:bench_base(9)=reflection
 EndIf
End Function

Function bench_config_apply(slot)
 If slot=0 Then
  For i=0 To 4:remaster_pending(i)=bench_saved(i):Next
  set_water=bench_saved(5):set_grass=bench_saved(6):set_viewrange=bench_saved(7):set_windsway=bench_saved(8):bench_reflection=bench_saved(9)
  set_musicvolume=bench_saved(10):set_fxvolume=bench_saved(11):set_mb=bench_saved(12)
 Else
  For i=0 To 4:remaster_pending(i)=bench_base(i):Next
  set_water=bench_base(5):set_grass=bench_base(6):set_viewrange=bench_base(7):set_windsway=bench_base(8):bench_reflection=bench_base(9)
 EndIf
End Function

Function bench_refresh()
 remaster_pending(Cremaster_water)=(bench_reflection<>1)
 remaster_settings_apply()
 settings_apply_viewrange():prepare_cache_viewranges():mb_reset()
 If env_sea Then
  ; Recreate capture storage when changing modes, including a deterministic OFF texture.
  If env_watertex Then FreeTexture env_watertex:env_watertex=0
  e_environment_setup_water()
  If bench_reflection=0 And env_watertex Then
   Local previous=GraphicsBuffer()
   SetBuffer TextureBuffer(env_watertex):ClsColor 60,100,140:Cls:SetBuffer previous:ClsColor 0,0,0
   EntityTexture env_sea,env_watertex
  EndIf
 EndIf
 If ter Then grass_map():grass_heightmap():grass_x=-2147483648:grass_y=-2147483648:grass_spread()
 bench_revision=bench_revision+1
End Function

Function bench_add(class,typ,count=96)
 Local group.TbenchGroup=New TbenchGroup
 bench_serial=bench_serial+1:group\serial=bench_serial:group\class=class:group\typ=typ:group\count=count
 group\spacing=60.:group\layout=bench_layout:group\seed=bench_seed:group\offset=Float((bench_serial-1) Mod 8)*40.-140.
 bench_selected=group\serial
 Return group\serial
End Function

Function bench_enter()
 If m_section<>Csection_menu Or m_menu<>Cmenu_main Or bench_active Then Return False
 bench_config_capture(0):bench_saved_fps=modern_fps:bench_saved_recording=DebugUiProfileRecording()
 For i=0 To 4:bench_entry_pending(i)=remaster_pending(i):Next
 e_clear():bench_active=1:m_section=Csection_benchmark:m_menu=0:g_unpaused=0
 editor_defaultmapsettings():map_freezetime=1:map_hour=12:map_music$="":map_name$=bench_label("tab")
 map_path$="":set_musicvolume=0:set_fxvolume=0:set_mb=0:modern_fps=0
 bench_ai=0:bench_animation=0:bench_camera_index=0:bench_reflection=2
 SeedRnd 57013
 ter_tex_color=CreateTexture(256,256):e_terrain(64,"generated"):EntityType ter,Cworld_col
 Local bx,bz,edge#,height#
 For bx=0 To 64
  For bz=0 To 64
   edge=Min(Min(bx,64-bx),Min(bz,64-bz)):height=0.495+Min(edge/8.,1.)*0.045
   ModifyTerrain ter,bx,bz,height
  Next
 Next
 in_opt(4)=0:editor_gencolormap():e_environment():grass_map():grass_heightmap()
 ; A hidden passive player supplies the references expected by AI and culling.
 tmp_loading=1:set_unit(-1,1,-1000000.,-1000000.):tmp_loading=0
 If TCunit<>Null Then g_player=TCunit\id:g_cplayer.Tunit=TCunit:TCunit\freeze=1:HideEntity TCunit\h
 set_water=6:set_grass=4:set_viewrange=4:bench_refresh()
 For group.TbenchGroup=Each TbenchGroup:Delete group:Next
 For spawn.TbenchSpawn=Each TbenchSpawn:Delete spawn:Next
 bench_serial=0:bench_selected=0:bench_actual=0:bench_failed=0
 bench_add(Cclass_object,1):bench_add(Cclass_object,2):bench_add(Cclass_object,3)
 bench_add(Cclass_object,10):bench_add(Cclass_object,11):bench_add(Cclass_object,12)
 bench_add(Cclass_object,63):bench_add(Cclass_object,64)
 bench_targets(1.):bench_job=1:bench_run=0:bench_status$=""
 DebugUiOpen 1:modern_reset_clock()
 Return True
End Function

Function bench_exit()
 If bench_active=0 Then Return
 DebugUiBenchmarkCancel():bench_auto=0:bench_run=0:bench_job=0:bench_action=0:bench_drop=0
 e_clear()
 For spawn.TbenchSpawn=Each TbenchSpawn:Delete spawn:Next
 For group.TbenchGroup=Each TbenchGroup:Delete group:Next
 For trial.TbenchTrial=Each TbenchTrial:Delete trial:Next
 For placement.TbenchPlacement=Each TbenchPlacement:Delete placement:Next
 bench_current=Null:bench_active=0
 bench_config_apply(0):remaster_settings_apply()
 For i=0 To 4:remaster_pending(i)=bench_entry_pending(i):Next
 modern_fps=bench_saved_fps:DebugUiProfileRecording bench_saved_recording
 settings_apply_viewrange():prepare_cache_viewranges():menu_start():modern_reset_clock()
End Function

Function bench_targets(multiplier#,source=0,recreate=0)
 bench_source=source
 Local remaining=Cbench_limit,wanted,needed,granted
 If recreate=0 Then remaining=Max(0,Cbench_limit-bench_actual)
 bench_requested=0:bench_rejected=0
 For group.TbenchGroup=Each TbenchGroup
  wanted=Int(Float(group\count)*multiplier)
  If source Then wanted=Int(Float(group\base_count)*multiplier)
  bench_requested=bench_requested+wanted
  If recreate Then
   group\target=Min(wanted,remaining):remaining=remaining-group\target
  Else
   needed=Max(0,wanted-group\attempted):granted=Min(needed,remaining)
   group\target=Min(wanted,group\attempted)+granted:remaining=remaining-granted
  EndIf
  bench_rejected=bench_rejected+wanted-group\target
 Next
End Function

Function bench_totals()
 bench_actual=0:bench_failed=bench_rejected
 For group.TbenchGroup=Each TbenchGroup:bench_actual=bench_actual+group\actual:bench_failed=bench_failed+group\failed:Next
End Function

Function bench_remove_one(spawn.TbenchSpawn)
 bench_actual=Max(0,bench_actual-1)
 Select spawn\class
  Case Cclass_object:free_object(spawn\id,0,0)
  Case Cclass_unit
   If con_unit(spawn\id) Then
    If TCunit\ai_ch Then FreeEntity TCunit\ai_ch:TCunit\ai_ch=0
    free_unit(spawn\id,0,0)
   EndIf
  Case Cclass_item:free_item(spawn\id)
 End Select
 For group.TbenchGroup=Each TbenchGroup
  If group\serial=spawn\group Then group\actual=Max(0,group\actual-1):Exit
 Next
 Delete spawn
End Function

Function bench_random#()
 bench_random=(bench_random*25173+13849) And 65535
 Return Float(bench_random)/65535.
End Function

Function bench_position(group.TbenchGroup,index)
 ; Stable square spiral: growing the count never moves the previous cells.
 Local ring=Ceil((Sqr(Float(index+1))-1.)/2.),side=ring*2,delta=(ring*2+1)^2-index-1
 Local x,z
 If side=0 Then
  x=0:z=0
 ElseIf delta<side Then
  x=ring-delta:z=-ring
 ElseIf delta<side*2 Then
  x=-ring:z=-ring+(delta-side)
 ElseIf delta<side*3 Then
  x=-ring+(delta-side*2):z=ring
 Else
  x=ring:z=ring-(delta-side*3)
 EndIf
 bench_px=Float(x)*group\spacing+group\offset:bench_pz=Float(z)*group\spacing+150.
 bench_random=(group\seed+group\serial*977+index*613) And 65535
 If group\layout=1 Then
  bench_px=(bench_random()-0.5)*group\spacing*48.+group\offset
  bench_pz=(bench_random()-0.5)*group\spacing*40.+250.
 EndIf
 bench_yaw=bench_random()*360.
End Function

Function bench_spawn_one(group.TbenchGroup)
 Local index=group\attempted,id=0,h=0,y#
 group\attempted=group\attempted+1
 If bench_actual>=Cbench_limit Then group\failed=group\failed+1:Return
 bench_position(group,index)
 Local placement.TbenchPlacement=Null
 If bench_source Then
  For candidate.TbenchPlacement=Each TbenchPlacement
   If candidate\group=group\serial And candidate\index=index Then placement=candidate:Exit
  Next
  If placement<>Null Then bench_px=placement\x:bench_pz=placement\z:bench_yaw=placement\yaw
 EndIf
 tmp_loading=1
 Select group\class
  Case Cclass_object
   If Dobject_modelh(group\typ)=0 Then load_object_model(group\typ)
   If Dobject_modelh(group\typ) Then
    set_object(-1,group\typ,bench_px,bench_pz,bench_yaw)
    If TCobject<>Null Then
     id=TCobject\id:h=TCobject\h:TranslateEntity h,0,group\height,0
     If TCobject\ch Then TranslateEntity TCobject\ch,0,group\height,0
     TCobject\windsway=bench_yaw
    EndIf
   EndIf
  Case Cclass_unit
   If Dunit_modelh(group\typ) Then
    set_unit(-1,group\typ,bench_px,bench_pz)
    If TCunit<>Null Then
     id=TCunit\id:h=TCunit\h:TranslateEntity h,0,group\height,0:RotateEntity h,0,bench_yaw,0
     ai_ini():TCunit\freeze=1-bench_ai:Animate TCunit\mh,0
     If TCunit\vh Then PositionEntity TCunit\vh,EntityX(h),EntityY(h),EntityZ(h):RotateEntity TCunit\vh,0,bench_yaw,0
    EndIf
   EndIf
  Case Cclass_item
   y=e_tery(bench_px,bench_pz)+1.+group\height
   id=set_item(-1,group\typ,bench_px,y,bench_pz,1,1)
   If id>0 And TCitem<>Null Then h=TCitem\h:RotateEntity h,0,bench_yaw,0:TCitem\phy_pause=1
 End Select
 tmp_loading=0
 If id>0 And h<>0 Then
  If placement<>Null Then
   Local delta#=placement\y-EntityY(h)
   TranslateEntity h,0,delta,0
   If group\class=Cclass_object Then
    If TCobject\ch Then TranslateEntity TCobject\ch,0,delta,0
   EndIf
  EndIf
  Local spawn.TbenchSpawn=New TbenchSpawn
  spawn\group=group\serial:spawn\class=group\class:spawn\id=id:spawn\index=index
  spawn\x=EntityX(h):spawn\y=EntityY(h):spawn\z=EntityZ(h):spawn\yaw=bench_yaw
  group\actual=group\actual+1:bench_actual=bench_actual+1
 Else
  group\failed=group\failed+1
 EndIf
End Function

Function bench_jobs()
 If bench_job=0 Then Return
 Local started=modern_walltime(),processed=0,pending=0
 If bench_job=2 Or bench_job=3 Then
  For spawn.TbenchSpawn=Each TbenchSpawn
   If bench_remove=0 Or spawn\group=bench_remove Then
    bench_remove_one(spawn):processed=processed+1
    If processed>=32 Or modern_walltime()-started>=4 Then bench_totals():Return
   EndIf
  Next
  For group.TbenchGroup=Each TbenchGroup
   If bench_remove=0 Or group\serial=bench_remove Then group\attempted=0:group\failed=0
  Next
  If bench_drop Then
   For group.TbenchGroup=Each TbenchGroup
    If group\serial=bench_drop Then Delete group:Exit
   Next
   bench_drop=0:bench_selected=0
   group.TbenchGroup=First TbenchGroup
   If group<>Null Then bench_selected=group\serial
  EndIf
  If bench_job=2 Then bench_job=0 Else bench_job=1
 EndIf
 If bench_job=1 Then
  For group.TbenchGroup=Each TbenchGroup
   While group\attempted<group\target
    bench_spawn_one(group):processed=processed+1
    If processed>=32 Or modern_walltime()-started>=4 Then bench_totals():Return
   Wend
  Next
  bench_job=0:bench_revision=bench_revision+1
 EndIf
 bench_totals()
End Function

Function bench_effect_name$(effect)
 Select effect
  Case 0:Return loc_resolve(remaster_labels(Cremaster_shadows))
  Case 1:Return loc_resolve(remaster_labels(Cremaster_light))
  Case 2:Return loc_resolve(remaster_labels(Cremaster_air))
  Case 3:Return loc_resolve(sm$(95))
  Case 4:Return loc_resolve(sm$(104))
  Case 5:Return loc_resolve(sm$(78))
  Case 6:Return loc_resolve(sm$(90))
  Case 7:Return loc_resolve(remaster_labels(Cremaster_aa))
  Case 8:Return bench_label("reflection")
  Case 9:Return bench_label("ai")
  Case 10:Return bench_label("animation")
 End Select
End Function

Function bench_trial_add(condition,repetition,multiplier#,effect=-1,value=0,name$="")
 Local trial.TbenchTrial=New TbenchTrial
 trial\condition=condition:trial\repetition=repetition:trial\multiplier=multiplier
 trial\effect=effect:trial\value=value:trial\name=name$
 bench_trial_total=bench_trial_total+1
End Function

Function bench_condition_add(condition,repetition)
 Select condition
  Case 0:bench_trial_add(0,repetition,1.,-1,0,bench_label("baseline"))
  Case 1:bench_trial_add(1,repetition,0.,-1,0,"0x")
  Case 2:bench_trial_add(2,repetition,0.5,-1,0,"0.5x")
  Case 3:bench_trial_add(3,repetition,2.,-1,0,"2x")
  Default
   Local effect=condition-4
   If effect<=10 And bench_compare(effect) Then bench_trial_add(condition,repetition,1.,effect,0,bench_effect_name(effect))
   If effect=8 And bench_compare(effect) Then
    ; Compare all three reflection paths; omit the path already used by the baseline.
    Delete Last TbenchTrial:bench_trial_total=bench_trial_total-1
    For mode=0 To 2
     If mode<>bench_base(9) Then bench_trial_add(100+mode,repetition,1.,8,mode,bench_effect_name(8)+" / "+mode)
    Next
   EndIf
 End Select
End Function

Function bench_start(automatic)
 If bench_active=0 Or bench_job Or bench_run Then Return
 For placement.TbenchPlacement=Each TbenchPlacement:Delete placement:Next
 For group.TbenchGroup=Each TbenchGroup:group\base_count=0:Next
 For spawn.TbenchSpawn=Each TbenchSpawn
  For group.TbenchGroup=Each TbenchGroup
   If group\serial=spawn\group Then
    placement.TbenchPlacement=New TbenchPlacement
    placement\group=spawn\group:placement\index=group\base_count
    placement\x=spawn\x:placement\y=spawn\y:placement\z=spawn\z:placement\yaw=spawn\yaw
    group\base_count=group\base_count+1:Exit
   EndIf
  Next
 Next
 bench_config_capture(1):bench_base_camera=bench_camera_index:bench_base_ai=bench_ai:bench_base_animation=bench_animation
 For trial.TbenchTrial=Each TbenchTrial:Delete trial:Next
 bench_trial_total=0:bench_trial_index=0:bench_auto=automatic:DebugUiBenchmarkClear()
 If automatic Then
  For repetition=1 To 3
   ; Reverse the middle repetition to reduce systematic temperature/order bias.
   If repetition=2 Then
    For condition=14 To 0 Step -1:bench_condition_add(condition,repetition):Next
   Else
    For condition=0 To 14:bench_condition_add(condition,repetition):Next
   EndIf
  Next
 Else
  bench_trial_add(0,1,1.,-1,0,bench_label("manual"))
 EndIf
 bench_current=First TbenchTrial:bench_run=1
 If automatic Then
  bench_trial_prepare()
 Else
  bench_run=2:bench_warm_start=modern_walltime():bench_trial_index=1
 EndIf
End Function

Function bench_trial_prepare()
 bench_config_apply(1):bench_camera_index=bench_base_camera:bench_ai=bench_base_ai:bench_animation=bench_base_animation
 If bench_current=Null Then bench_run=0:Return
 Select bench_current\effect
  Case 0:remaster_pending(Cremaster_shadows)=1-Int(bench_base(Cremaster_shadows))
  Case 1:remaster_pending(Cremaster_light)=1-Int(bench_base(Cremaster_light))
  Case 2:remaster_pending(Cremaster_air)=1-Int(bench_base(Cremaster_air))
  Case 3:set_grass=0:If bench_base(6)=0 Then set_grass=4
  Case 4:set_windsway=1-Int(bench_base(8))
  Case 5:set_viewrange=0:If bench_base(7)=0 Then set_viewrange=4
  Case 6:set_water=0:If bench_base(5)=0 Then set_water=6
  Case 7:remaster_pending(Cremaster_aa)=0:If bench_base(Cremaster_aa)=0 Then remaster_pending(Cremaster_aa)=1
  Case 8:bench_reflection=bench_current\value
  Case 9:bench_ai=1-bench_base_ai
  Case 10:bench_animation=1-bench_base_animation
 End Select
 bench_refresh():bench_targets(bench_current\multiplier,1,1)
 bench_remove=0:bench_job=3:bench_run=1:bench_trial_index=bench_trial_index+1
 ; AI and animation also start from the same creation seed for each trial.
 SeedRnd bench_seed
End Function

Function bench_cancel(reason$="")
 Local automatic=bench_auto
 DebugUiBenchmarkCancel():bench_run=0:bench_auto=0:bench_job=0
 bench_config_apply(1):bench_ai=bench_base_ai:bench_animation=bench_base_animation:bench_camera_index=bench_base_camera
 bench_refresh()
 If automatic Then bench_targets(1.,1,1):bench_remove=0:bench_job=3
 bench_status$=reason$
End Function

Function bench_context$()
 Local context$="benchmark revision="+bench_revision+" requested="+bench_requested+" actual="+bench_actual+" failed="+bench_failed
 context=context+" camera="+bench_camera_index+" ai="+bench_ai+" animation="+bench_animation+" reflection="+bench_reflection+" seed="+bench_seed
 context=context+" edition="+Cedition$+" resolution="+set_scrx+"x"+set_scry+" fps_limit="+modern_fps
 context=context+" water_quality="+set_water+" grass="+set_grass+" view="+set_viewrange+" wind="+set_windsway
 context=context+" water="+remaster_enabled(0)+" lighting="+remaster_enabled(1)+" shadows="+remaster_enabled(2)+" effective_lighting="+(remaster_enabled(1) Or remaster_enabled(2))+" atmosphere="+remaster_enabled(3)+" aa="+remaster_enabled(4)
 For group.TbenchGroup=Each TbenchGroup
  context=context+" group="+group\serial+":"+group\class+":"+group\typ+":"+group\count+":"+group\target+":"+group\actual+":"+group\spacing+":"+group\height+":"+group\layout+":"+group\seed
 Next
 Return context
End Function

Function bench_tick()
 If bench_active=0 Then Return
 bench_jobs()
 If bench_run=1 And bench_job=0 Then bench_run=2:bench_warm_start=modern_walltime()
 If bench_run=2 And modern_walltime()-bench_warm_start>=bench_warm_ms Then
  DebugUiBenchmarkBegin bench_current\name,bench_context(),bench_current\condition,bench_current\repetition,bench_measure_ms
  bench_run=3
 EndIf
 If bench_run=3 And DebugUiBenchmarkState()=5 Then
  Local current.TbenchTrial=bench_current
  bench_current=After current
  If bench_current<>Null Then
   bench_trial_prepare()
  Else
   Local automatic=bench_auto
   bench_run=0:bench_auto=0:bench_config_apply(1)
   bench_ai=bench_base_ai:bench_animation=bench_base_animation:bench_camera_index=bench_base_camera
   bench_refresh()
   If automatic Then bench_targets(1.,1,1):bench_remove=0:bench_job=3
  EndIf
 EndIf
End Function

Function bench_input()
 Local action=bench_action:bench_action=0
 Select action
  Case 1:bench_enter()
  Case 2:bench_exit()
  Case 3:bench_targets(1.):bench_job=1
  Case 4:If bench_selected Then bench_remove=bench_selected:bench_job=2
  Case 5:bench_remove=0:bench_job=2
  Case 6:bench_targets(1.,0,1):bench_remove=0:bench_job=3
  Case 7:bench_job=0:bench_drop=0
  Case 8:bench_refresh()
  Case 9:bench_start(0)
  Case 10:bench_start(1)
  Case 11:bench_cancel(bench_label("cancelled"))
  Case 12
   If bench_selected Then bench_drop=bench_selected:bench_remove=bench_selected:bench_job=2
 End Select
 bench_tick()
End Function

Function bench_focus_lost()
 If bench_active<>0 And bench_run<>0 Then bench_cancel(bench_label("focus_lost"))
End Function

Function bench_camera()
 Local x#=0.,z#=-1150.,y#=e_tery(0.,-1150.)+45.,pitch#=0.
 If bench_camera_index=1 Then z=-1960.:y=20.:pitch=0.
 If bench_camera_index=2 Then pitch=-75.
 PositionEntity cam,x,y,z:PositionEntity camxz,x,0,z:RotateEntity cam,pitch,0,0
End Function

Function bench_unit_animation()
 For unit.Tunit=Each Tunit
  If unit\id<>g_player Then
   unit\freeze=1-bench_ai
   If bench_animation=0 Then
    Animate unit\mh,0
   ElseIf bench_ai=0 And Animating(unit\mh)=0 Then
    Animate unit\mh,1,Dunit_ani_idle1(unit\typ,0),Dunit_ani_idle1(unit\typ,1)
   EndIf
  EndIf
 Next
End Function

Function bench_frame()
 DebugUiProfileBegin 0
 getinput(1)
 If bench_active=0 Then DebugUiProfileEnd 0:Return
 Local phase=DebugUiBenchmarkState()
 If bench_run=3 And (phase=1 Or phase=2 Or phase=4) Then
  ; While draining, keep events/UI responsive without submitting another scene.
  endinput(0,0,0):DebugUiProfileEnd 0:Return
 EndIf
 bench_camera():bench_unit_animation()
 g_unpaused=0:cull_items():g_unpaused=1
 DebugUiProfileBegin 1:UpdateWorld(f):DebugUiProfileEnd 1
 DebugUiProfileBegin 3:grass_spread():DebugUiProfileEnd 3
 DebugUiProfileBegin 2:cull():DebugUiProfileEnd 2
 bench_unit_animation()
 DebugUiProfileBegin 5:e_environment_update_water():DebugUiProfileEnd 5
 e_environment_update():remaster_frame()
 DebugUiProfileBegin 7:RenderWorld():DebugUiProfileEnd 7
 DebugUiProfileBegin 8:mb_update():e_environment_water2dfx():DebugUiProfileEnd 8
 endinput(0,0,0)
 DebugUiProfileEnd 0
End Function

Function bench_ui()
 If bench_active=0 Then
  DebugUiText bench_label("intro")
  DebugUiDisabled m_section<>Csection_menu Or m_menu<>Cmenu_main
  If DebugUiButton(bench_label("enter")) Then bench_action=1
  DebugUiDisabledEnd()
 Else
  DebugUiText bench_label("temporary")
  DebugUiText set_scrx+" x "+set_scry+" / "+bench_label("requested")+": "+bench_requested+" / "+bench_label("actual")+": "+bench_actual+" / "+bench_label("failed")+": "+bench_failed
  If DebugUiButton(bench_label("exit")) Then bench_action=2
  If bench_run Then
   DebugUiText bench_label("progress")+" "+bench_trial_index+" / "+bench_trial_total+" · "+bench_current\name+" / "+bench_current\repetition
   If bench_run=1 Then DebugUiText bench_label("generating")
   If bench_run=2 Then DebugUiText bench_label("warmup")
   If bench_run=3 Then DebugUiText bench_label("measuring")+" / "+DebugUiBenchmarkState()
   If DebugUiButton(bench_label("cancel")) Then bench_action=11
   Return
  EndIf
  If bench_job Then
   DebugUiText bench_label("generating")
   If DebugUiButton(bench_label("cancel")) Then bench_action=7
  EndIf
  DebugUiDisabled bench_job<>0
  If DebugUiSection(bench_label("catalog")) Then
   bench_catalog=DebugUiInt(bench_label("category"),bench_catalog,1,3)
   DebugUiText bench_label("categories")
   bench_search$=DebugUiInput(dev_label("search"),bench_search$)
   Local catalog_end=Cobject_count
   If bench_catalog=2 Then catalog_end=Cunit_count
   If bench_catalog=3 Then catalog_end=Citem_count
   If DebugUiListBegin(220.) Then
    For id=0 To catalog_end
     Local name$=bench_name(bench_catalog,id)
     If name$<>"" And (bench_search$="" Or Instr(Lower(name$+" "+id),Lower(bench_search$))) Then
      If DebugUiButton(name$+" ("+id+")##benchcatalog"+id) Then bench_add(bench_catalog,id)
     EndIf
    Next
    DebugUiListEnd()
   EndIf
  EndIf
  If DebugUiSection(bench_label("groups")) Then
   For group.TbenchGroup=Each TbenchGroup
    If DebugUiButton(bench_name(group\class,group\typ)+" ("+group\class+":"+group\typ+")##benchgroup"+group\serial) Then bench_selected=group\serial
    DebugUiSameLine():DebugUiText group\actual+" / "+group\count+" · "+group\failed
    If bench_selected=group\serial Then
     group\count=DebugUiInt(dev_label("count")+"##count"+group\serial,group\count,0,Cbench_limit)
     group\spacing=DebugUiFloat(bench_label("spacing")+"##spacing"+group\serial,group\spacing,1.,200.)
     group\height=DebugUiFloat(bench_label("height")+"##height"+group\serial,group\height,-500.,1000.)
     group\layout=DebugUiInt(bench_label("layout")+"##layout"+group\serial,group\layout,0,1)
     group\seed=DebugUiInt(bench_label("seed")+"##seed"+group\serial,group\seed,0,65535)
    EndIf
   Next
   DebugUiText bench_label("layout_help")
   If DebugUiButton(bench_label("append")) Then bench_action=3
   DebugUiSameLine():If DebugUiButton(bench_label("regenerate")) Then bench_action=6
   If DebugUiButton(bench_label("delete_group")) Then bench_action=4
   DebugUiSameLine():If DebugUiButton(bench_label("delete_all")) Then bench_action=5
   If DebugUiButton(bench_label("remove_group")) Then bench_action=12
  EndIf
  If DebugUiSection(bench_label("effects")) Then
   For effect=0 To 2
    Local index=Cremaster_shadows
    If effect=1 Then index=Cremaster_light
    If effect=2 Then index=Cremaster_air
    remaster_pending(index)=DebugUiCheck(bench_effect_name(effect),remaster_pending(index))
    If DebugUiChanged() Then bench_action=8
   Next
   set_grass=DebugUiInt(bench_effect_name(3),set_grass,0,4):If DebugUiChanged() Then bench_action=8
   set_windsway=DebugUiCheck(bench_effect_name(4),set_windsway)
   set_viewrange=DebugUiInt(bench_effect_name(5),set_viewrange,0,4):If DebugUiChanged() Then bench_action=8
   set_water=DebugUiInt(bench_effect_name(6),set_water,0,6):If DebugUiChanged() Then bench_action=8
   Local aa=remaster_pending(Cremaster_aa):If aa=4 Then aa=2
   aa=DebugUiInt(bench_effect_name(7)+" (0 / FXAA / MSAA 4x)",aa,0,2):If DebugUiChanged() Then bench_action=8
   If aa=2 Then aa=4
   remaster_pending(Cremaster_aa)=aa
   bench_reflection=DebugUiInt(bench_effect_name(8),bench_reflection,0,2):If DebugUiChanged() Then bench_action=8
   DebugUiText bench_label("reflection_help")
   bench_ai=DebugUiCheck(bench_effect_name(9),bench_ai)
   bench_animation=DebugUiCheck(bench_effect_name(10),bench_animation)
   bench_camera_index=DebugUiInt(bench_label("camera"),bench_camera_index,0,2)
   DebugUiText bench_label("camera_help")
   modern_fps=DebugUiInt(bench_label("fps_limit"),modern_fps,0,240)
   DebugUiText bench_label("lighting_help")
  EndIf
  If DebugUiSection(bench_label("comparison")) Then
   DebugUiText bench_label("comparison_help")
   For effect=0 To 10:bench_compare(effect)=DebugUiCheck(bench_effect_name(effect)+"##compare"+effect,bench_compare(effect)):Next
   If DebugUiButton(bench_label("measure")) Then bench_action=9
   DebugUiSameLine():If DebugUiButton(bench_label("automatic")) Then bench_action=10
  EndIf
  DebugUiDisabledEnd()
  DebugUiText bench_status$
 EndIf
 DebugUiBenchmarkResults()
 If DebugUiButton(bench_label("csv")) Then DebugUiBenchmarkExport set_rootdir$+"core\benchmarks",0
 DebugUiSameLine():If DebugUiButton(bench_label("json")) Then DebugUiBenchmarkExport set_rootdir$+"core\benchmarks",1
End Function

Function bench_labels()
 DebugUiProfileLabel "benchmark.tab",bench_label("tab")
 DebugUiProfileLabel "benchmark.intro",bench_label("intro")
 DebugUiProfileLabel "benchmark.enter",bench_label("enter")
 DebugUiProfileLabel "benchmark.exit",bench_label("exit")
 DebugUiProfileLabel "benchmark.temporary",bench_label("temporary")
 DebugUiProfileLabel "benchmark.requested",bench_label("requested")
 DebugUiProfileLabel "benchmark.actual",bench_label("actual")
 DebugUiProfileLabel "benchmark.failed",bench_label("failed")
 DebugUiProfileLabel "benchmark.progress",bench_label("progress")
 DebugUiProfileLabel "benchmark.generating",bench_label("generating")
 DebugUiProfileLabel "benchmark.warmup",bench_label("warmup")
 DebugUiProfileLabel "benchmark.measuring",bench_label("measuring")
 DebugUiProfileLabel "benchmark.cancel",bench_label("cancel")
 DebugUiProfileLabel "benchmark.cancelled",bench_label("cancelled")
 DebugUiProfileLabel "benchmark.catalog",bench_label("catalog")
 DebugUiProfileLabel "benchmark.category",bench_label("category")
 DebugUiProfileLabel "benchmark.categories",bench_label("categories")
 DebugUiProfileLabel "benchmark.groups",bench_label("groups")
 DebugUiProfileLabel "benchmark.spacing",bench_label("spacing")
 DebugUiProfileLabel "benchmark.height",bench_label("height")
 DebugUiProfileLabel "benchmark.layout",bench_label("layout")
 DebugUiProfileLabel "benchmark.seed",bench_label("seed")
 DebugUiProfileLabel "benchmark.layout_help",bench_label("layout_help")
 DebugUiProfileLabel "benchmark.append",bench_label("append")
 DebugUiProfileLabel "benchmark.regenerate",bench_label("regenerate")
 DebugUiProfileLabel "benchmark.delete_group",bench_label("delete_group")
 DebugUiProfileLabel "benchmark.delete_all",bench_label("delete_all")
 DebugUiProfileLabel "benchmark.remove_group",bench_label("remove_group")
 DebugUiProfileLabel "benchmark.effects",bench_label("effects")
 DebugUiProfileLabel "benchmark.reflection",bench_label("reflection")
 DebugUiProfileLabel "benchmark.ai",bench_label("ai")
 DebugUiProfileLabel "benchmark.animation",bench_label("animation")
 DebugUiProfileLabel "benchmark.reflection_help",bench_label("reflection_help")
 DebugUiProfileLabel "benchmark.camera",bench_label("camera")
 DebugUiProfileLabel "benchmark.camera_help",bench_label("camera_help")
 DebugUiProfileLabel "benchmark.fps_limit",bench_label("fps_limit")
 DebugUiProfileLabel "benchmark.lighting_help",bench_label("lighting_help")
 DebugUiProfileLabel "benchmark.comparison",bench_label("comparison")
 DebugUiProfileLabel "benchmark.comparison_help",bench_label("comparison_help")
 DebugUiProfileLabel "benchmark.measure",bench_label("measure")
 DebugUiProfileLabel "benchmark.automatic",bench_label("automatic")
 DebugUiProfileLabel "benchmark.baseline",bench_label("baseline")
 DebugUiProfileLabel "benchmark.manual",bench_label("manual")
 DebugUiProfileLabel "benchmark.csv",bench_label("csv")
 DebugUiProfileLabel "benchmark.json",bench_label("json")
 DebugUiProfileLabel "benchmark.copy",bench_label("copy")
 DebugUiProfileLabel "benchmark.metric_help",bench_label("metric_help")
 DebugUiProfileLabel "benchmark.trial",bench_label("trial")
 DebugUiProfileLabel "benchmark.fps",bench_label("fps")
 DebugUiProfileLabel "benchmark.delta",bench_label("delta")
 DebugUiProfileLabel "benchmark.valid",bench_label("valid")
 DebugUiProfileLabel "benchmark.details",bench_label("details")
 DebugUiProfileLabel "benchmark.median",bench_label("median")
 DebugUiProfileLabel "benchmark.focus_lost",bench_label("focus_lost")
 DebugUiProfileLabel "benchmark.invalid_frame",bench_label("invalid_frame")
 DebugUiProfileLabel "benchmark.gpu_completion_failed",bench_label("gpu_completion_failed")
 DebugUiProfileLabel "benchmark.gpu_completion_unavailable",bench_label("gpu_completion_unavailable")
 DebugUiProfileLabel "focus_lost",bench_label("focus_lost")
 DebugUiProfileLabel "invalid_frame",bench_label("invalid_frame")
 DebugUiProfileLabel "gpu_completion_failed",bench_label("gpu_completion_failed")
 DebugUiProfileLabel "gpu_completion_unavailable",bench_label("gpu_completion_unavailable")
 DebugUiProfileLabel "cancelled",bench_label("cancelled")
End Function
