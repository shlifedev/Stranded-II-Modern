Function usgn_setup()
	bmpf_loadscreen("[[loc:engine.82d93516199d]]")
	udp_setup()
	If udp=0 Then RuntimeError("Failed to initialize UDP Sockets")
	Local tcp=OpenTCPStream("usgn.unrealsoftware.de",80)
	If tcp<>0 Then
		For Tserver.Tserver=Each Tserver
			Delete Tserver
		Next
		WriteLine(tcp,"GET http://usgn.unrealsoftware.de/game_stranded2/ips.txt")
		in_usgn_version$=Trim(ReadLine(tcp))
		in_usgn_msg$=Trim(ReadLine(tcp))
		in_usgn_msgscr#=500
		While Not Eof(tcp)
			in$=Trim(ReadLine(tcp))
			If in$<>"" Then
				Tserver.Tserver=New Tserver
				sep=Instr(in$,":")
				Tserver\ip=udp_int((Left(in$,sep-1)))
				Tserver\port=Mid(in$,sep+1,-1)
				If set_debug=1 Then
					con_add("Got U.S.G.N. Server "+DottedIP(Tserver\ip)+":"+Tserver\port,Cbmpf_yellow)
				EndIf
			EndIf
		Wend
		CloseTCPStream(tcp)
	EndIf
	
End Function


Function usgn_hostserver(port)
	bmpf_loadscreen("[[loc:engine.e1c1b0f73f66]]")
	udp_setup(port)
	If udp=0 Then RuntimeError("Failed to create server at UDP Port "+port+"!")
	udp_host=1
	set_sv_port=port
	Local tcp=OpenTCPStream("usgn.unrealsoftware.de",80)
	If tcp<>0 Then
		WriteLine(tcp,"GET http://usgn.unrealsoftware.de/game_stranded2/save.php?port="+port)
		If Not Eof(tcp)
			in$=Trim(ReadLine(tcp))
			If in$<>"" Then
				set_sv_ip=udp_int(in$)
			EndIf
		EndIf
		CloseTCPStream(tcp)
	EndIf
	m_section=Csection_game_mp
	load_map("maps\"+in_win_map$+".s2","")
	game_playerspawn(1)
	game_setplayer(1)
	TCunit\player_name$=mp_checkname$(set_name$)
	TCunit\player_ip=set_sv_ip
	TCunit\player_port=set_sv_port
	m_menu=0
End Function


Function usgn_joinserver(ip,port,password$="")
	udp_setup()
	If udp=0 Then RuntimeError("Failed to initialize UDP Sockets")
	udp_host=0
	out=udp_join(ip,port,10000,1000)
	Select out
		Case 1 joined=1
		Default joined=0
	End Select
	If joined=1 Then
		set_sv_ip=udp_hostip
		set_sv_port=udp_hostport
		udp_send(set_sv_ip,set_sv_port,1)
		udp_w_tiny(set_name$)
		udp_w_tiny(password$)
		mp_joinserver=0
		timeout=MilliSecs()
		While mp_joinserver=0
			udp_update()
			If MilliSecs()-timeout>10000 Then
				Return 0
			EndIf
		Wend
		Select mp_joinserver
			Case 1
				m_section=Csection_game_mp
				load_map("maps\"+set_sv_map$+".s2","")
				game_playerspawn(mp_id)
				game_setplayer(mp_id)
				TCunit\player_name$=mp_name$
				TCunit\player_ip=mp_ip
				TCunit\player_port=udp_port
				m_menu=0
				udp_send(set_sv_ip,set_sv_port,3)
				udp_w_byte(1)
		End Select
		Return 1
	Else
		Return 0
	EndIf
End Function


Function usgn_update()
	While RecvUDPMsg(udp)
		If ReadAvail(udp)>0 Then
		
			fip=UDPMsgIP(udp)
			fport=UDPMsgPort(udp)
			
			For Tserver.Tserver=Each Tserver
				If Tserver\ip=fip Then
					If Tserver\port=fport Then
						id=ReadByte(udp)
						
						Select id
							
							Case 101
								Tserver\name$=ReadString(udp)
								Tserver\map$=ReadString(udp)
								Tserver\players=ReadByte(udp)
								Tserver\maxplayers=ReadByte(udp)
								Tserver\password=ReadByte(udp)
							
							Default
								If set_debug=1 Then
									con_add("ERROR: Unexpected U.S.G.N. Message: "+id,Cbmpf_red)
								EndIf
								While ReadAvail(udp)>0
									ReadByte(udp)
								Wend
							
						End Select
						
						Exit		
					EndIf
				EndIf
			Next
		EndIf
	Wend
	
	For Tserver.Tserver=Each Tserver
		If ms-Tserver\timer>3000 Then
			If Tserver\ping<>0 Or (ms-Tserver\timer>15000) Then
				Tserver\timer=ms
				Tserver\pingstamp=ms
				WriteByte udp,100
				SendUDPMsg udp,Tserver\ip,Tserver\port
				con_add("send request to "+DottedIP(Tserver\ip)+":"+Tserver\port)
			EndIf
		EndIf
	Next
End Function