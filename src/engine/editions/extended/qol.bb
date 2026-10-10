; Extended-only settings and input. No save format or gameplay data changes.
Const Cqol_recipes=0,Cqol_storage=1,Cqol_close=2,Cqol_sort=3,Cqol_build=4
Dim qol_enabled(4),qol_pending(4),qol_keys$(4),qol_labels$(4)
Global qol_hold_state,qol_hold_target,qol_hold_weapon,qol_hold_resume
Global qol_transfer_id,qol_transfer_count,qol_status$,qol_status_until

Function qol_init()
 qol_keys(0)="qol_recipes":qol_labels(0)="[[loc:qol.recipes]]"
 qol_keys(1)="qol_storage":qol_labels(1)="[[loc:qol.storage]]"
 qol_keys(2)="qol_loot_autoclose":qol_labels(2)="[[loc:qol.autoclose]]"
 qol_keys(3)="qol_autosort":qol_labels(3)="[[loc:qol.autosort]]"
 qol_keys(4)="qol_hold_build":qol_labels(4)="[[loc:qol.hold_build]]"
 Local i,f,line$,eq,key$,value$
 For i=0 To 4:qol_enabled(i)=1:Next
 f=ReadFile(set_rootdir$+"core\modern.ini")
 If f Then
  While (Eof(f)=0)
   line$=Trim(ReadLine(f)):eq=Instr(line$,"=")
   If eq>1 Then
    key$=Lower(Trim(Left(line$,eq-1))):value$=Trim(Mid(line$,eq+1))
    For i=0 To 4
     If key$=qol_keys(i) And (value$="0" Or value$="1") Then qol_enabled(i)=Int(value$)
    Next
   EndIf
  Wend
  CloseFile f
 EndIf
 qol_settings_reset()
 qol_guides_load()
End Function

Function qol_settings_reset()
 For i=0 To 4:qol_pending(i)=qol_enabled(i):Next
End Function

Function qol_setting_owned(key$)
 For i=0 To 4
  If key$=qol_keys(i) Then Return True
 Next
 Return False
End Function

Function qol_settings_write(stream)
 For i=0 To 4:WriteLine stream,qol_keys(i)+"="+qol_pending(i):Next
End Function

Function qol_settings_apply()
 For i=0 To 4:qol_enabled(i)=qol_pending(i):Next
 qol_hold_state=3
End Function

Function qol_settings_restart()
 Return modern_pending_fps<>modern_saved_fps Or modern_pending_borderless<>modern_saved_borderless Or modern_pending_width<>modern_saved_width Or modern_pending_height<>modern_saved_height Or modern_pending_native<>modern_saved_native Or loc_pending$<>loc_saved$
End Function

Function qol_options_nav()
 Local y=155
 If game_scriptkeys>0 Then y=205
 If gui_button(5,y,"[[loc:qol.tab]]") Then menu_set(55)
End Function

Function qol_options_draw()
 DrawImage gfx_win,215,0
 DrawImage gfx_icons,236,5,Cicon_options
 bmpf_txt(273,10,"[[loc:qol.tab]]")
 DrawImage gfx_winbar,215,42
 Local i,label$,direction
 For i=0 To 4
  bmpf_txt(236,66+i*62,qol_labels(i))
  label$="[[loc:qol.off]]"
  If qol_pending(i) Then label$="[[loc:qol.on]]"
  direction=modern_choice(636,62+i*62,140,label$)
  If direction Then qol_pending(i)=1-qol_pending(i)
 Next
 bmpf_txt_rect(236,405,530,150,"[[loc:qol.settings_help]]",Cbmpf_tiny)
End Function

Function qol_button(x,y,w,label$,active=1,selected=0)
 Local over=(in_mx>=x And in_mx<x+w And in_my>=y And in_my<y+24)
 Color 70,51,28
 If over And active Then Color 110,77,37
 If selected Then Color 105,78,39
 If Not active Then Color 50,48,43
 Rect x,y,w,24,1
 Color 159,133,89:Rect x,y,w,24,0
 bmpf_txt(x+5,y+4,bmpf_trim(ko_localize(label$),over,w-10),Cbmpf_tiny)
 If over Then gui_tt(in_mx,in_my,label$)
 Return active And over And in_mrelease(1) And in_dnd=0
End Function

Function qol_frame()
 If m_menu<>28 Or m_section<>Csection_game_sp Then qol_search_close()
 If m_menu<>0 Or m_section<>Csection_game_sp Or modern_resume_count<>qol_hold_resume Then qol_hold_state=3
 If g_player<>0 Then
  If g_cplayer\health#<=0 Then qol_hold_state=3
 EndIf
 If (in_keydown(Ckey_attack2)=0) Then qol_hold_state=0:qol_hold_target=0
 If qol_search_hwnd Then qol_search_update()
End Function

Function qol_attack2()
 If (qol_enabled(Cqol_build)=0) Then Return in_keyhit(Ckey_attack2)
 If g_cplayer\player_weapon=0 Then qol_hold_state=0:Return in_keyhit(Ckey_attack2)
 If Ditem_behaviour$(g_cplayer\player_weapon)<>"hammer" Then qol_hold_state=0:Return in_keyhit(Ckey_attack2)
 If in_keyhit(Ckey_attack2) Then
  qol_hold_state=1:qol_hold_target=0:qol_hold_weapon=g_cplayer\player_weapon:qol_hold_resume=modern_resume_count
  Return True
 EndIf
 If qol_hold_state<>2 Or (in_keydown(Ckey_attack2)=0) Then Return False
 If g_cplayer\player_weapon<>qol_hold_weapon Then qol_hold_state=3:Return False
 If qol_count(qol_hold_weapon,Cclass_unit,g_player)=0 Then qol_hold_state=3:Return False
 If (con_object(qol_hold_target)=0) Then qol_hold_state=3:Return False
 If EntityDistance(cam,TCobject\h)>=75 Then qol_hold_state=3:Return False
 If (get_state(Cstate_buildplace,Cclass_object,qol_hold_target)=0) Then qol_hold_state=3:Return False
 If gt-g_cplayer\player_lastattack<Ditem_rate(qol_hold_weapon) Then Return False
 Return True
End Function

Function qol_build_accept(id)
 If qol_enabled(Cqol_build) And qol_hold_state=2 Then Return id=qol_hold_target
 Return True
End Function

Function qol_build_result(id,continuing)
 If (qol_enabled(Cqol_build)=0) Then Return
 If qol_hold_state=1 Or qol_hold_state=2 Then
  qol_hold_target=id:qol_hold_state=3
  If continuing Then qol_hold_state=2
 EndIf
End Function

Function qol_shutdown()
 qol_search_close()
End Function

Function qol_min(a,b)
 If a<b Then Return a
 Return b
End Function
Function qol_max(a,b)
 If a>b Then Return a
 Return b
End Function
