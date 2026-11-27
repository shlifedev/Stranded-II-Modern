Function game_mp()
	
	getinput()
	If m_menu=0 Then MoveMouse set_scrx/2,set_scry/2
	
	UpdateWorld(f#)
	game_setcam()
	e_environment_update_water()	
	RenderWorld()
	mb_update()
	e_environment_water2dfx()
	
	cull()
	
	grass_spread()
	e_environment_update()
	focus_update()
	update_state()
	sfx_update()
	
	
	If KeyHit(59) Then
		SaveBuffer(BackBuffer(),"screen"+ms+".bmp")
	EndIf
	
	interface()
	
	
	If m_menu=0 Then
		game_input_normal()
	EndIf
	
	game_input_menu()
			
	If g_unpaused Then
		p_update()
		pro_update()
	Else
		p_update_2d()
	EndIf

	If m_menu=0 Then DrawImage gfx_cursor(6),set_scrx/2,set_scry/2
	
	If KeyDown(15) Then
		mp_scoreboard()
	EndIf
	
	x=((m_menu>0 And m_menu<Cmenu_if_quit)Or(m_menu>=Cmenu_if_buildsetup))
	endinput(x,x)
	
	udp_update()
	
	If 1=1 Then
	
	If ms-mp_sendpostimer>mp_sendposrate Then
		mp_sendpostimer=ms
		For Tudp_con.Tudp_con=Each Tudp_con
			udp_send(Tudp_con\ip,Tudp_con\port,50,1)
			udp_w_byte(g_player)
			udp_w_float(EntityX(g_cplayer\h))
			udp_w_float(EntityY(g_cplayer\h))
			udp_w_float(EntityZ(g_cplayer\h))
			udp_w_float(EntityYaw(g_cplayer\h))
		Next
	EndIf
	
	If ms-mp_sendpingtimer>(5*1000) Then
		mp_sendpingtimer=ms
		For Tudp_con.Tudp_con=Each Tudp_con
			udp_send(Tudp_con\ip,Tudp_con\port,5,1)
			udp_w_byte(1)
			udp_w_int(MilliSecs())
		Next
	EndIf
	
	EndIf
	
	
	Delay 10
	
End Function