Function game()
 DebugUiProfileBegin 0
	
	g_unpaused=(m_menu=0) Or (m_menu>=Cmenu_if_movie)
	
	If dev_paused Then g_unpaused=0
	getinput(g_unpaused)
	If dev_paused Then dev_render_paused():DebugUiProfileEnd 0:Return
	If m_menu=0 Then
		MoveMouse set_scrx/2,set_scry/2
	ElseIf m_menu=Cmenu_if_movie Then
		If seq_cpoint=4 Then
			MoveMouse set_scrx/2,set_scry/2
		EndIf
	EndIf
	
	cull_items()
	
	ct=MilliSecs()
 DebugUiProfileBegin 1
	col_player_prepare()
	UpdateWorld(f#)
	col_player_finish()
 DebugUiProfileEnd 1
	If set_debug_rt Then
		set_debug_rt_updateworld$=(MilliSecs()-ct)+" ms Update"
	EndIf
	
	If in_tpforce>0 Then
		in_tpforce=in_tpforce-f#
		If Handle(g_cplayer) Then
			PositionEntity g_cplayer\h,in_tp#(0),in_tp#(1),in_tp#(2)
			ResetEntity g_cplayer\h
			col_player_reset()
		EndIf
	EndIf

	game_setcam()
	DebugUiProfileBegin 3:grass_spread():DebugUiProfileEnd 3

	DebugUiProfileBegin 2:cull():DebugUiProfileEnd 2
	
	DebugUiProfileBegin 5:e_environment_update_water():DebugUiProfileEnd 5
	e_environment_update()
	
	remaster_frame()
	game_renderweapon()
	
	set_debug_rt_render=MilliSecs()
	DebugUiProfileBegin 7:RenderWorld():DebugUiProfileEnd 7
	set_debug_rt_render=MilliSecs()-set_debug_rt_render
	If KeyHit(59) Then screenshot()

	
	ct=MilliSecs()
	
 DebugUiProfileBegin 8
	mb_update()
	e_environment_water2dfx()
	focus_update()
	update_state()
	sfx_update()
	up_update()
	
 DebugUiProfileEnd 8
	DebugUiProfileBegin 9:interface():DebugUiProfileEnd 9
	
	If m_menu=0 Then
		game_input_normal()
	EndIf
	
	game_input_menu()
	
	
	If set_debug=1 Then
		If KeyHit(67) Then set_debug=0:if_msg("[[loc:engine.37002f4c3232]]",Cbmpf_yellow)
		If KeyHit(68) Then con_debugmenu()
		If KeyHit(87) Then game_cd():if_msg("[[loc:engine.bf761a5d541d]]",Cbmpf_yellow)
	EndIf
			
	If g_unpaused Then
		p_update()
		pro_update()
	Else
		p_update_2d()
	EndIf

	If m_menu=0 Then
		If set_drawinterface=1 Then
			DrawImage gfx_cursor(6),set_scrx/2,set_scry/2
		EndIf
	EndIf
	
	seq_blend()
	
	x=((m_menu>0 And m_menu<Cmenu_if_quit)Or(m_menu>=Cmenu_if_buildsetup)Or(m_menu=Cmenu_if_clickscreen))
	endinput(x,x,0)
	
	If set_debug_rt Then
		set_debug_rt_poststuff$=(MilliSecs()-ct)+" ms Poststuff"
	EndIf
	
 DebugUiProfileEnd 0
End Function
