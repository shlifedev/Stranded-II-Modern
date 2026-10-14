Include "includes\dev_benchmark.bb"
; Shared Dear ImGui developer tools. Draws at presentation; actions run before world updates.
Global dev_epoch,dev_generation=-1,dev_profile_labels_ready
Global dev_frames,dev_frame_start,dev_fps#,dev_status$
Global dev_item_search$,dev_item_count=1,dev_script$,dev_log_search$,dev_log_level=-1,dev_log_follow=1
Global dev_x#,dev_y#,dev_z#,dev_day=1,dev_hour,dev_minute
Global dev_refresh,dev_saved_name$,dev_saved_language$
Global dev_saved_hotkey
Dim dev_settings_paths$(3),dev_settings_exists(3)
Dim dev_saved#(23),dev_saved_keys(41),dev_saved_qol(4),dev_saved_render(4)
Type TdevAction
 Field epoch,player,kind,a,b,c
 Field x#,y#,z#,text$
End Type

Function dev_label$(key$)
 Return loc_resolve("[[loc:dev."+key$+"]]")
End Function

Function dev_player()
 If m_section<>Csection_game_sp Then Return False
 If g_player=0 Or Handle(g_cplayer)=0 Then Return False
 Return g_cplayer\id=g_player And g_cplayer\h<>0
End Function

Function dev_queue(kind,a=0,b=0,c=0,x#=0.,y#=0.,z#=0.,text$="")
 Local task.TdevAction=New TdevAction
 task\epoch=dev_epoch:task\player=g_player:task\kind=kind
 task\a=a:task\b=b:task\c=c:task\x=x:task\y=y:task\z=z:task\text$=text$
End Function

Function dev_world_reset()
 dev_epoch=dev_epoch+1:dev_paused=0
 For task.TdevAction=Each TdevAction:Delete task:Next
End Function

; Same helpers serve the original cheat menu and the overlay.
Function cheat_invulnerable()
 If dev_player()=0 Then Return False
 If get_state(Cstate_invulnerability,Cclass_unit,g_player)=0 Then
  set_state(Cstate_invulnerability,Cclass_unit,g_player)
 Else
  free_state(Cstate_invulnerability,Cclass_unit,g_player)
 EndIf
 g_player_dead=0
 g_cplayer\health_max=Dunit_health(g_cplayer\typ):g_cplayer\health=g_cplayer\health_max
 EntityType g_cplayer\h,Dunit_col(g_cplayer\typ)
 EntityPickMode g_cplayer\mh,2,1
 Return True
End Function

Function cheat_needs()
 If dev_player()=0 Then Return False
 g_cplayer\hunger=0:g_cplayer\thirst=0:g_cplayer\exhaustion=0
 con_unit(g_player):unit_values()
 Return True
End Function

Function cheat_states()
 If dev_player()=0 Then Return False
 For state.Tstate=Each Tstate
  If state\parent_class=Cclass_unit And state\parent_id=g_player Then free_state(state\typ,Cclass_unit,g_player)
 Next
 Return True
End Function

Function cheat_item(typ,count)
 If dev_player()=0 Then Return False
 If typ<0 Or typ>Citem_count Or count<1 Or count>10000 Then Return False
 If Ditem_name$(typ)="" Then Return False
 Local id=set_item(-1,typ,EntityX(cam),EntityY(cam),EntityZ(cam),count)
 If id=0 Then Return False
 merge_item(id):store_item(id,Cclass_unit,g_player)
 Return True
End Function

Function cheat_build()
 If dev_player()=0 Then Return False
 Local obj.Tobject,material.Tbui,item.Titem,building,id,typ
 For obj.Tobject=Each Tobject
  If obj\typ=set_buildplaceid Or obj\typ=set_buildplacewaterid Then
   If EntityDistance(cam,obj\h)<75 Then
    If get_state(Cstate_buildplace,Cclass_object,obj\id) Then
     typ=TCstate\value:building=0
     For material.Tbui=Each Tbui
      If material\mode=2 And material\typ=typ Then building=material\id:Exit
     Next
     If building=0 Then Return False
     For item.Titem=Each Titem
      If item\parent_class=Cclass_object And item\parent_id=obj\id Then free_item(item\id)
     Next
     For material.Tbui=Each Tbui
      If material\mode=0 And material\id=building Then
       id=set_item(-1,material\typ,0,0,0,material\count)
       If id Then store_item(id,Cclass_object,obj\id)
      EndIf
     Next
     Return True
    EndIf
   EndIf
  EndIf
 Next
 Return False
End Function

Function cheat_script(script$)
 If dev_player()=0 Or p_parseinprogress Then Return False
 ; Run only this requested script. Paused timers and unrelated tasks remain queued.
 parse_env(0,0,"cheat","Cheat Script Execution")
 loadstring_parsecache(Replace(Replace(script$,Chr(13),""),Chr(10),Chr(31)),Chr(31))
 parse()
 Return True
End Function

Function dev_actions()
 If p_parseinprogress Then Return
 While First TdevAction<>Null
  Local task.TdevAction=First TdevAction
  Local epoch=task\epoch,player=task\player,kind=task\kind,a=task\a,b=task\b,c=task\c,x#=task\x,y#=task\y,z#=task\z,text$=task\text$
  Delete task
  If epoch=dev_epoch And player=g_player And dev_player() Then
   Local result=True
   Select kind
    Case 1:result=cheat_invulnerable()
    Case 2:result=cheat_needs()
    Case 3:result=cheat_states()
    Case 4:result=cheat_item(a,b)
    Case 5:result=cheat_build()
    Case 6:result=cheat_script(text$)
    Case 7
     PositionEntity g_cplayer\h,x,y,z
     ResetEntity g_cplayer\h:col_player_reset():game_setcam()
    Case 8
     map_day=a:map_hour=b:map_minute=c
     ; Move time without generating a day of growth or replaying time events.
     map_lday=map_day:map_lhour=map_hour:map_lminute=map_minute
     e_environment_update_light()
    Case 9
     g_cplayer\health=x:g_cplayer\hunger=y:g_cplayer\thirst=z
     g_cplayer\exhaustion=Float(a):con_unit(g_player):unit_values()
   End Select
   dev_status$=dev_label("done")
   If result=0 Then dev_status$=dev_label("failed")
   DebugUiLog "cheat",2*(result=0),dev_status$+" ("+kind+")"
  EndIf
 Wend
End Function

Function dev_clear_input()
 For i=0 To 41:in_keyhit(i)=0:in_keydown(i)=0:in_keyrelease(i)=0:Next
 For i=1 To 5:in_mhit(i)=0:in_mdown(i)=0:in_mrelease(i)=0:Next
 in_mx=-10000:in_my=-10000:in_mxs=0:in_mys=0:in_mzs=0:in_key=0:in_escape=0
 in_dnd=0:in_frapdown=0:in_sticktoslide=0:in_jumprehit=0:in_pe=0
End Function

Function dev_input()
	Local hotkey=in_keys(Ckey_devmenu)
	If in_getkey<>-1 Then hotkey=0
	DebugUiHotkey hotkey
 If dev_initialized=0 Then dev_capture_settings():dev_initialized=1
 If dev_generation<>DebugUiGeneration() Then
  dev_generation=DebugUiGeneration():flushinput():dev_clear_input():qol_search_close()
 EndIf
 bench_input()
 If bench_active Then Return
 dev_apply_preview()
 dev_actions()
End Function

Function dev_capture_settings()
 dev_saved(0)=set_viewrange:dev_saved(1)=set_terrain:dev_saved(2)=set_water
 dev_saved(3)=set_sky:dev_saved(4)=set_effects:dev_saved(5)=set_grass
 dev_saved(6)=set_2dfx:dev_saved(7)=set_lightfx:dev_saved(8)=set_windsway:dev_saved(9)=set_fog:dev_saved(10)=set_hwmultitex
 dev_saved(11)=set_musicvolume:dev_saved(12)=set_fxvolume:dev_saved(13)=set_msens
 dev_saved(14)=set_minvert:dev_saved(15)=set_msmooth:dev_saved(16)=set_serverport
 dev_saved(17)=modern_pending_fps:dev_saved(18)=modern_pending_borderless
 dev_saved(19)=modern_pending_width:dev_saved(20)=modern_pending_height:dev_saved(21)=modern_pending_native
 dev_saved_name$=set_name$:dev_saved_language$=loc_pending$
 dev_saved_hotkey=in_keys(Ckey_devmenu)
 For i=0 To 20:dev_saved_keys(i)=in_keys(i):dev_saved_keys(i+21)=game_scriptkey(i):Next
 For i=0 To 4:dev_saved_qol(i)=qol_enabled(i):dev_saved_render(i)=remaster_enabled(i):Next
End Function

Function dev_revert_settings()
 set_viewrange=dev_saved(0):set_terrain=dev_saved(1):set_water=dev_saved(2)
 set_sky=dev_saved(3):set_effects=dev_saved(4):set_grass=dev_saved(5)
 set_2dfx=dev_saved(6):set_lightfx=dev_saved(7):set_windsway=dev_saved(8):set_fog=dev_saved(9):set_hwmultitex=dev_saved(10)
 set_musicvolume=dev_saved(11):set_fxvolume=dev_saved(12):set_msens=dev_saved(13)
 set_minvert=dev_saved(14):set_msmooth=dev_saved(15):set_serverport=dev_saved(16)
 modern_pending_fps=dev_saved(17):modern_pending_borderless=dev_saved(18)
 modern_pending_width=dev_saved(19):modern_pending_height=dev_saved(20):modern_pending_native=dev_saved(21)
 set_name$=dev_saved_name$:loc_pending$=dev_saved_language$
 in_keys(Ckey_devmenu)=dev_saved_hotkey
 For i=0 To 20:in_keys(i)=dev_saved_keys(i):game_scriptkey(i)=dev_saved_keys(i+21):Next
 For i=0 To 4:qol_pending(i)=dev_saved_qol(i):remaster_pending(i)=dev_saved_render(i):Next
 dev_refresh=7:dev_apply_preview()
End Function

Function dev_apply_preview()
 If dev_refresh=0 Then Return
 settings_apply_viewrange():prepare_cache_viewranges():mb_reset()
 If dev_refresh And 2 Then
  If env_sea Then e_environment_setup_water()
  If ter Then grass_map():grass_heightmap():grass_x=-2147483648:grass_y=-2147483648:grass_spread()
 EndIf
 If dev_refresh And 4 Then qol_settings_apply():remaster_settings_apply()
 If amb_chan Then ChannelVolume amb_chan,set_fxvolume
 dev_refresh=0
 If m_menu>=50 And m_menu<=56 Then menu_set(m_menu,0)
End Function

Function dev_restart()
 Return modern_pending_fps<>modern_fps Or modern_pending_borderless<>modern_borderless Or modern_pending_native<>modern_native Or (modern_pending_native=0 And (modern_pending_width<>modern_width Or modern_pending_height<>modern_height)) Or loc_pending$<>loc_language$
End Function

Function dev_settings_dirty()
 If set_name$<>dev_saved_name$ Or loc_pending$<>dev_saved_language$ Then Return True
 If in_keys(Ckey_devmenu)<>dev_saved_hotkey Then Return True
 If set_viewrange<>dev_saved(0) Or set_terrain<>dev_saved(1) Or set_water<>dev_saved(2) Or set_sky<>dev_saved(3) Or set_effects<>dev_saved(4) Or set_grass<>dev_saved(5) Then Return True
 If set_2dfx<>dev_saved(6) Or set_lightfx<>dev_saved(7) Or set_windsway<>dev_saved(8) Or set_fog<>dev_saved(9) Or set_hwmultitex<>dev_saved(10) Then Return True
 If set_musicvolume<>dev_saved(11) Or set_fxvolume<>dev_saved(12) Or set_msens<>dev_saved(13) Or set_minvert<>dev_saved(14) Or set_msmooth<>dev_saved(15) Or set_serverport<>dev_saved(16) Then Return True
 If modern_pending_fps<>dev_saved(17) Or modern_pending_borderless<>dev_saved(18) Or modern_pending_width<>dev_saved(19) Or modern_pending_height<>dev_saved(20) Or modern_pending_native<>dev_saved(21) Then Return True
 For i=0 To 20:If in_keys(i)<>dev_saved_keys(i) Or game_scriptkey(i)<>dev_saved_keys(i+21) Then Return True
 Next
 For i=0 To 4:If qol_pending(i)<>dev_saved_qol(i) Or remaster_pending(i)<>dev_saved_render(i) Then Return True
 Next
 Return False
End Function

Function dev_save_settings()
 dev_apply_preview()
 dev_settings_paths(0)="sys\controls.cfg":dev_settings_paths(1)="sys\scriptcontrols.cfg"
 dev_settings_paths(2)="sys\settings.cfg":dev_settings_paths(3)=set_rootdir$+"core\modern.ini"
 Local index,path$,ok=True,wrote=0
 ; Snapshot all preference files before replacing any of them.
 For index=0 To 3
  path$=dev_settings_paths(index):dev_settings_exists(index)=(FileType(path$)=1)
  If dev_settings_exists(index) Then
   If modern_file_copy(path$,path$+".devmenu-rollback",0)=0 Then ok=False:Exit
  EndIf
 Next
 If ok Then wrote=1:ok=save_settings()
 If ok Then ok=modern_options_save()
 If ok=0 And wrote Then
  For index=0 To 3
   path$=dev_settings_paths(index)
   If FileType(path$+".devmenu-rollback")=1 Then
    If modern_file_copy(path$+".devmenu-rollback",path$,0)=0 Then DebugUiLog "settings",2,"Preference rollback failed: "+path$
   ElseIf dev_settings_exists(index)=0 Then
    If FileType(path$)=1 Then DeleteFile path$
   EndIf
  Next
 ElseIf ok Then
  dev_capture_settings()
 EndIf
 For index=0 To 3
  path$=dev_settings_paths(index)+".devmenu-rollback"
  If FileType(path$)=1 Then DeleteFile path$
 Next
 Return ok
End Function

Function dev_int(label$,value,minimum,maximum,refresh=1)
 Local result=DebugUiInt(loc_resolve(label$),value,minimum,maximum)
 If DebugUiChanged() Then dev_refresh=dev_refresh Or refresh
 Return result
End Function

Function dev_check(label$,value,refresh=1)
 Local result=DebugUiCheck(loc_resolve(label$),value)
 If DebugUiChanged() Then dev_refresh=dev_refresh Or refresh
 Return result
End Function

Function dev_float#(label$,value#,minimum#,maximum#)
 Local result#=DebugUiFloat(loc_resolve(label$),value,minimum,maximum)
 If DebugUiChanged() Then dev_refresh=dev_refresh Or 1
 Return result
End Function

Function dev_settings_ui()
 DebugUiText dev_label("preview")
 If dev_settings_dirty() Then DebugUiText dev_label("unsaved")
 If dev_restart() Then DebugUiText dev_label("restart")
 If DebugUiButton(dev_label("save")) Then
  dev_status$=dev_label("saved")
  If dev_save_settings()=0 Then dev_status$=loc_resolve("[[loc:modern.settings_failed]]")
  DebugUiLog "settings",2*(dev_settings_dirty()<>0),dev_status$
 EndIf
 DebugUiSameLine()
 If DebugUiButton(dev_label("revert")) Then dev_revert_settings():dev_status$=dev_label("reverted")
 If DebugUiSection(dev_label("display")) Then
  DebugUiText dev_label("restart")
  Local limit=0
  Select modern_pending_fps
   Case 60:limit=1
   Case 120:limit=2
   Case 144:limit=3
  End Select
  limit=DebugUiInt(loc_resolve("[[loc:modern.fps]]")+" (0 / 60 / 120 / 144)",limit,0,3)
  Select limit
   Case 0:modern_pending_fps=0
   Case 1:modern_pending_fps=60
   Case 2:modern_pending_fps=120
   Case 3:modern_pending_fps=144
  End Select
  modern_pending_borderless=DebugUiCheck(loc_resolve("[[loc:modern.borderless]]"),modern_pending_borderless)
  modern_pending_native=DebugUiCheck(loc_resolve("[[loc:modern.desktop]]"),modern_pending_native)
  DebugUiDisabled modern_pending_native
  modern_pending_width=DebugUiInt(dev_label("width"),modern_pending_width,800,2048)
  modern_pending_height=DebugUiInt(dev_label("height"),modern_pending_height,600,2048)
  DebugUiDisabledEnd()
  For language.Tlanguage=Each Tlanguage
   If DebugUiButton(language\name$+"##language-"+language\code$) Then loc_pending$=language\code$
   DebugUiSameLine()
  Next
  DebugUiText loc_name(loc_pending$)
 EndIf
 If DebugUiSection(loc_resolve(sm$(50))) Then
  set_viewrange=dev_int(sm$(78),set_viewrange,0,4)
  set_terrain=dev_int(sm$(84),set_terrain,0,2)
  set_water=dev_int(sm$(90),set_water,0,6,2)
  set_sky=dev_int(sm$(93),set_sky,0,2)
  set_effects=dev_int(sm$(94),set_effects,0,2)
  set_grass=dev_int(sm$(95),set_grass,0,4,2)
  set_2dfx=dev_check(sm$(102),set_2dfx)
  set_lightfx=dev_check(sm$(103),set_lightfx)
  set_windsway=dev_check(sm$(104),set_windsway)
  set_fog=dev_check(sm$(105),set_fog)
  set_hwmultitex=dev_check(sm$(106),set_hwmultitex)
 EndIf
 If DebugUiSection(loc_resolve("[[loc:render.tab]]")) Then
  For i=0 To 3:remaster_pending(i)=dev_check(remaster_labels(i),remaster_pending(i),4):Next
  Local aa=remaster_pending(4)
  If aa=4 Then aa=2
  aa=dev_int(remaster_labels(4)+" (0: "+dev_label("off")+" / 1: FXAA / 2: MSAA 4x)",aa,0,2,4)
  If aa=2 Then aa=4
  remaster_pending(4)=aa
 EndIf
 If DebugUiSection(loc_resolve("[[loc:qol.tab]]")) Then
  For i=0 To 4:qol_pending(i)=dev_check(qol_labels(i),qol_pending(i),4):Next
 EndIf
 If DebugUiSection(loc_resolve(sm$(110))) Then
  set_musicvolume=dev_float(sm$(111),set_musicvolume,0.,1.)
  set_fxvolume=dev_float(sm$(113),set_fxvolume,0.,1.)
  set_name$=DebugUiInput(loc_resolve(sm$(114)),set_name$)
  set_serverport=DebugUiInt(loc_resolve(sm$(115)),set_serverport,1,65535)
 EndIf
 If DebugUiSection(loc_resolve(sm$(49))) Then
  set_msens=dev_float(sm$(72),set_msens,0.02,2.)
  Local invert=(set_minvert=-1)
  invert=dev_check(sm$(74),invert):set_minvert=1-2*invert
  set_msmooth=dev_check(sm$(75),set_msmooth)

 EndIf
End Function

Function dev_cheats_ui()
 Local active=dev_player()
 If active=0 Then DebugUiText dev_label("play_only")
 DebugUiDisabled active=0
 If DebugUiButton(dev_label("invulnerable")) Then dev_queue(1)
 DebugUiSameLine()
 If DebugUiButton(dev_label("needs")) Then dev_queue(2)
 If DebugUiButton(dev_label("states")) Then dev_queue(3)
 DebugUiSameLine()
 If DebugUiButton(dev_label("build")) Then dev_queue(5)
 If active Then
  Local health#=g_cplayer\health,hunger#=g_cplayer\hunger,thirst#=g_cplayer\thirst,exhaustion#=g_cplayer\exhaustion,changed=0
  health=DebugUiFloat(dev_label("health"),health,0.,g_cplayer\health_max):changed=changed Or DebugUiChanged()
  hunger=DebugUiFloat(dev_label("hunger"),hunger,0.,Dunit_store(g_cplayer\typ)):changed=changed Or DebugUiChanged()
  thirst=DebugUiFloat(dev_label("thirst"),thirst,0.,Dunit_store(g_cplayer\typ)):changed=changed Or DebugUiChanged()
  exhaustion=DebugUiFloat(dev_label("exhaustion"),exhaustion,0.,Dunit_store(g_cplayer\typ)):changed=changed Or DebugUiChanged()
  If changed Then dev_queue(9,Int(exhaustion),0,0,health,hunger,thirst)
 EndIf
 If DebugUiSection(dev_label("teleport")) Then
  If DebugUiButton(dev_label("read_position")) And active Then dev_x=EntityX(g_cplayer\h):dev_y=EntityY(g_cplayer\h):dev_z=EntityZ(g_cplayer\h)
  dev_x=DebugUiFloat("X",dev_x,-1000000.,1000000.)
  dev_y=DebugUiFloat("Y",dev_y,-1000000.,1000000.)
  dev_z=DebugUiFloat("Z",dev_z,-1000000.,1000000.)
  If DebugUiButton(dev_label("teleport")) Then dev_queue(7,0,0,0,dev_x,dev_y,dev_z)
 EndIf
 If DebugUiSection(dev_label("time")) Then
  If DebugUiButton(dev_label("read_time")) Then dev_day=map_day:dev_hour=map_hour:dev_minute=map_minute
  dev_day=DebugUiInt(dev_label("day"),dev_day,1,1000000)
  dev_hour=DebugUiInt(dev_label("hour"),dev_hour,0,23)
  dev_minute=DebugUiInt(dev_label("minute"),dev_minute,0,59)
  If DebugUiButton(dev_label("apply_time")) Then dev_queue(8,dev_day,dev_hour,dev_minute)
 EndIf
 If DebugUiSection(dev_label("items")) Then
  dev_item_search$=DebugUiInput(dev_label("search"),dev_item_search$)
  dev_item_count=DebugUiInt(dev_label("count"),dev_item_count,1,10000)
  If DebugUiItemsBegin() Then
  For i=0 To Citem_count
   If Ditem_name$(i)<>"" Then
    Local name$=ko_localize(Ditem_name$(i))
    If dev_item_search$="" Or Instr(Lower(name$+" "+i),Lower(dev_item_search$)) Then
     Local icon=Ditem_iconh(i)
     If icon=0 Then icon=gfx_defaulticon
     If DebugUiItem(name$+" ("+i+")##item"+i,ImageBuffer(icon)) Then dev_queue(4,i,dev_item_count)
    EndIf
   EndIf
  Next
  DebugUiItemsEnd()
  EndIf
 EndIf
 If DebugUiSection(dev_label("script")) Then
  dev_script$=DebugUiInput(dev_label("script"),dev_script$,1)
  If DebugUiButton(dev_label("execute")) Then dev_queue(6,0,0,0,0,0,0,dev_script$)
 EndIf
 DebugUiDisabledEnd()
End Function

Function dev_draw()
 If dev_initialized=0 Then Return
 If dev_paused Then bmpf_txt(5,20,"[[loc:dev.paused]]",Cbmpf_yellow)
 If DebugUiFrame(dev_label("title")+" · "+in_keyname$(in_keys(Ckey_devmenu)),set_rootdir$+"core\locales\"+loc_language$+"\ui.ttf")=0 Then Return
 If bench_active<>0 And bench_run<>0 Then
  If DebugUiTab(bench_label("tab")) Then bench_ui():DebugUiTabEnd()
  DebugUiFinish():Return
 EndIf
 If DebugUiTab(bench_label("tab")) Then dev_profile_labels():bench_labels():bench_ui():DebugUiTabEnd()
 If DebugUiTab(dev_label("status")) Then
  DebugUiText "FPS: "+Int(dev_fps)+" / "+modern_fps
  DebugUiText dev_label("map")+": "+ko_localize(map_name$)+" ("+map_path$+")"
  DebugUiText dev_label("time")+": "+map_day+" / "+map_hour+":"+map_minute
  DebugUiDisabled dev_player()=0
  dev_paused=DebugUiCheck(dev_label("pause"),dev_paused)
  DebugUiDisabledEnd()
  If dev_player() Then
   DebugUiText dev_label("player")+": "+g_player+" / "+ko_localize(Dunit_name$(g_cplayer\typ))
   DebugUiText "XYZ: "+EntityX(g_cplayer\h)+", "+EntityY(g_cplayer\h)+", "+EntityZ(g_cplayer\h)
   DebugUiText dev_label("health")+": "+g_cplayer\health+" / "+g_cplayer\health_max
   DebugUiText dev_label("hunger")+": "+g_cplayer\hunger+" / "+dev_label("thirst")+": "+g_cplayer\thirst+" / "+dev_label("exhaustion")+": "+g_cplayer\exhaustion
   DebugUiText dev_label("invulnerable")+": "+(get_state(Cstate_invulnerability,Cclass_unit,g_player)<>0)
  EndIf
  DebugUiTabEnd()
 EndIf
 If DebugUiTab(dev_label("profiler")) Then dev_profile_labels():DebugUiProfiler():DebugUiTabEnd()
 If DebugUiTab(dev_label("cheats")) Then dev_cheats_ui():DebugUiTabEnd()
 If bench_active=0 Then
  If DebugUiTab(dev_label("settings")) Then dev_settings_ui():DebugUiTabEnd()
 EndIf
 If DebugUiTab(dev_label("logs")) Then
  dev_log_search$=DebugUiInput(dev_label("search"),dev_log_search$)
  dev_log_level=DebugUiInt(dev_label("level"),dev_log_level,-1,3)
  dev_log_follow=DebugUiCheck(dev_label("follow"),dev_log_follow)
  If DebugUiButton(dev_label("clear")) Then DebugUiClearLogs()
  DebugUiSameLine()
  If DebugUiButton(dev_label("copy")) Then DebugUiCopyLogs dev_log_search$,dev_log_level
  dev_log_follow=DebugUiLogs(dev_log_search$,dev_log_level,dev_log_follow)
  DebugUiTabEnd()
 EndIf
 DebugUiText dev_status$
 DebugUiFinish()
End Function

Function dev_record_frame()
 Local now=modern_walltime(),elapsed=now-dev_frame_start
 dev_frames=dev_frames+1
 If elapsed>=1000 Then dev_fps=1000.*Float(dev_frames)/Float(elapsed):dev_frames=0:dev_frame_start=now
End Function

Function dev_render_paused()
 g_unpaused=0
 game_setcam()
 remaster_frame()
 DebugUiProfileBegin 7:RenderWorld():DebugUiProfileEnd 7
 DebugUiProfileBegin 9:interface():DebugUiProfileEnd 9
 game_input_menu()
 endinput(0,0,0)
End Function

; The presentation boundary also covers menus, editors and modal loops.
Function dev_profile_begin(scope)
 DebugUiProfileBegin scope
End Function
Function dev_profile_end(scope)
 DebugUiProfileEnd scope
End Function
Function dev_profile_invalidate()
 DebugUiProfileInvalidate()
End Function
Function dev_profile_frame()
 If DebugUiProfileActive() Then
  Local budget#=0.
  If modern_fps>0 Then budget=1000./Float(modern_fps)
  Local context$="edition="+Cedition$+" resolution="+set_scrx+"x"+set_scry+" fps_limit="+modern_fps
  context=context+" water="+remaster_enabled(0)+" lighting="+remaster_enabled(1)+" shadows="+remaster_enabled(2)+" atmosphere="+remaster_enabled(3)+" antialias="+remaster_enabled(4)
  context=context+" water_quality="+set_water+" grass="+set_grass+" view_range="+set_viewrange+" section="+m_section+" menu="+m_menu+" paused="+dev_paused
  If bench_active Then context=context+" "+bench_context()+" trial="+bench_trial_index+" phase="+bench_run
  DebugUiProfileContext context,budget
 EndIf
 DebugUiProfileBoundary()
End Function

Function dev_profile_labels()
 If dev_profile_labels_ready Then Return
 dev_profile_labels_ready=1
 DebugUiProfileLabel "start",dev_label("profiler.start")
 DebugUiProfileLabel "stop",dev_label("profiler.stop")
 DebugUiProfileLabel "clear",dev_label("profiler.clear")
 DebugUiProfileLabel "copy",dev_label("profiler.copy")
 DebugUiProfileLabel "latest",dev_label("profiler.latest")
 DebugUiProfileLabel "cpu_help",dev_label("profiler.cpu_help")
 DebugUiProfileLabel "help",dev_label("profiler.help")
 DebugUiProfileLabel "empty",dev_label("profiler.empty")
 DebugUiProfileLabel "backend",dev_label("profiler.backend")
 DebugUiProfileLabel "statistics",dev_label("profiler.statistics")
 DebugUiProfileLabel "invalid",dev_label("profiler.invalid")
 DebugUiProfileLabel "scope",dev_label("profiler.scope")
 DebugUiProfileLabel "average",dev_label("profiler.average")
 DebugUiProfileLabel "p95",dev_label("profiler.p95")
 DebugUiProfileLabel "maximum",dev_label("profiler.maximum")
 DebugUiProfileLabel "total",dev_label("profiler.total")
 DebugUiProfileLabel "self",dev_label("profiler.self")
 DebugUiProfileLabel "gpu_ms",dev_label("profiler.gpu_ms")
 DebugUiProfileLabel "passes",dev_label("profiler.passes")
 DebugUiProfileLabel "draws",dev_label("profiler.draws")
 DebugUiProfileLabel "instances",dev_label("profiler.instances")
 DebugUiProfileLabel "triangles",dev_label("profiler.triangles")
 DebugUiProfileLabel "gpu_sum",dev_label("profiler.gpu_sum")
 DebugUiProfileLabel "gpu_help",dev_label("profiler.gpu_help")
 DebugUiProfileLabel "pending",dev_label("profiler.pending")
 DebugUiProfileLabel "ready",dev_label("profiler.ready")
 DebugUiProfileLabel "skipped",dev_label("profiler.skipped")
 DebugUiProfileLabel "partial",dev_label("profiler.partial")
 DebugUiProfileLabel "failed",dev_label("profiler.failed")
 DebugUiProfileLabel "unsupported",dev_label("profiler.unsupported")
 DebugUiProfileLabel "device_lost",dev_label("profiler.device_lost")
 DebugUiProfileLabel "gpu_init_failed",dev_label("profiler.gpu_init_failed")
 DebugUiProfileLabel "cpu.game",dev_label("profiler.cpu.game")
 DebugUiProfileLabel "cpu.physics",dev_label("profiler.cpu.physics")
 DebugUiProfileLabel "cpu.objects",dev_label("profiler.cpu.objects")
 DebugUiProfileLabel "cpu.grass",dev_label("profiler.cpu.grass")
 DebugUiProfileLabel "cpu.shadows",dev_label("profiler.cpu.shadows")
 DebugUiProfileLabel "cpu.water",dev_label("profiler.cpu.water")
 DebugUiProfileLabel "cpu.reflection",dev_label("profiler.cpu.reflection")
 DebugUiProfileLabel "cpu.main",dev_label("profiler.cpu.main")
 DebugUiProfileLabel "cpu.post",dev_label("profiler.cpu.post")
 DebugUiProfileLabel "cpu.hud",dev_label("profiler.cpu.hud")
 DebugUiProfileLabel "cpu.ui",dev_label("profiler.cpu.ui")
 DebugUiProfileLabel "cpu.pace",dev_label("profiler.cpu.pace")
 DebugUiProfileLabel "cpu.flip",dev_label("profiler.cpu.flip")
 DebugUiProfileLabel "gpu.scene",dev_label("profiler.gpu.scene")
 DebugUiProfileLabel "gpu.near",dev_label("profiler.gpu.near")
 DebugUiProfileLabel "gpu.far",dev_label("profiler.gpu.far")
 DebugUiProfileLabel "gpu.reflection",dev_label("profiler.gpu.reflection")
 DebugUiProfileLabel "gpu.2d",dev_label("profiler.gpu.2d")
 DebugUiProfileLabel "gpu.present",dev_label("profiler.gpu.present")
 DebugUiProfileLabel "gpu.mips",dev_label("profiler.gpu.mips")
End Function
