Function mp_checkname$(name$,checkexistence=1)
	Local s,i
	name$=Trim(name$)
	If Len(name$)>15 Then
		name$=Left(name$,15)
	EndIf
	For i=1 To Len(name$)
		s=Asc(Mid(name$,i,1))
		If s<48 Then
			Select s
				Case 32,40,41,45,46
				Default name$=Replace(name$,Chr(s),"_")
			End Select
		ElseIf s>57 And s<65
			name$=Replace(name$,Chr(s),"_")
		ElseIf s>90 And s<97 Then
			Select s
				Case 91,93,95
				Default name$=Replace(name$,Chr(s),"_")
			End Select
		ElseIf s>122 Then
			name$=Replace(name$,Chr(s),"_")
		EndIf
	Next
	name$=Replace(name$,"__","_")
	If name$="" Then name$="Mr. Stranded"
	If checkexistence=1 Then
		Local free=1
		For Tunit.Tunit=Each Tunit
			If Tunit\id<100 Then
				If Tunit\player_name$=name$ Then
					free=0
					Exit
				EndIf
			EndIf
		Next
		If free=0 Then
			i=2
			Local set=0
			While set=0
				free=1
				For Tunit.Tunit=Each Tunit
					If Tunit\id<100 Then
						If Tunit\player_name$=name$+"("+i+")" Then
							free=0
							Exit
						EndIf
					EndIf
				Next
				If free=1 Then
					set=1
					name$=name$+"("+i+")"
				Else
					i=i+1
				EndIf
			Wend
		EndIf
	EndIf
	Return name$
End Function


Function mp_getplayerid()
	For i=1 To 100
		free=1
		For Tunit.Tunit=Each Tunit
			If Tunit\id=i Then free=0:Exit
		Next
		If free=1 Then Return i
	Next
	Return 0
End Function


Function mp_getplayer(ip,port)
	For Tunit.Tunit=Each Tunit
		If Tunit\id<100 Then
			If Tunit\player_ip=ip Then
				If Tunit\player_port=port Then
					TCunit.Tunit=Tunit
					Return Tunit\id
				EndIf
			EndIf
		EndIf
	Next
	Return 0
End Function


Function mp_server_joinrequest(ip,port,name$,pw$)
	name$=mp_checkname$(name$,1)
	If pw$=set_sv_pw$ Or set_sv_pw$="" Then
		players=0
		For Tunit.Tunit=Each Tunit
			If Tunit\id<100 Then
				players=players+1
			EndIf
		Next
		If players<set_sv_playerlimit Then
			found=0
			freeid=2
			While found=0
				free=1
				For Tunit.Tunit=Each Tunit
					If Tunit\id=freeid Then
						free=0
						Exit
					EndIf
				Next
				If free=1 Then
					found=1
				Else
					freeid=freeid+1
				EndIf
			Wend
			game_playerspawn(freeid,-1)
			con_unit(freeid)
			TCunit\player_name$=name$
			TCunit\player_ip=ip
			TCunit\player_port=port
			udp_send(ip,port,2)
			udp_w_byte(1)
			udp_w_tiny(name$)
			udp_w_byte(freeid)
			udp_w_int(ip)
			udp_w_tiny(set_sv_name$)
			udp_w_tiny(set_sv_map$)
			udp_w_tiny(map_id$)
			udp_w_int(tmp_spawnid)
			if_msg(name$+"[[loc:engine.ada84842489e]]",Cbmpf_yellow)
		Else
			udp_send(ip,port,2)
			udp_w_byte(3)
		EndIf
	Else
		udp_send(ip,port,2)
		udp_w_byte(2)
	EndIf
End Function


Function mp_client_checkmap(map$,mapid$)
	ownid$=load_map_id("maps\"+map$+".s2")
	If ownid$="0" Then
		udp_send(set_sv_ip,set_sv_port,3)
		udp_w_byte(2)
		mp_joinserver=2
	Else
		If ownid$=mapid$ Then
			mp_joinserver=1
		Else
			udp_send(set_sv_ip,set_sv_port,3)
			udp_w_byte(7)
			mp_joinserver=3
		EndIf
	EndIf
End Function


Function mp_server_sendplayerdata(ip,port)
	id=mp_getplayer(ip,port)
	If id<>0 Then
		For Tunit.Tunit=Each Tunit
			If Tunit\id<100 Then
				If Tunit\id<>id Then
					udp_send(ip,port,3)
					udp_w_byte(100)
					udp_w_tiny(Tunit\player_name$)
					udp_w_byte(Tunit\id)
					udp_w_float(EntityX(Tunit\h))
					udp_w_float(EntityY(Tunit\h))
					udp_w_float(EntityZ(Tunit\h))
					udp_w_float(EntityYaw(Tunit\h))
					udp_w_short(Tunit\player_score)
					udp_w_short(Tunit\player_deads)
					udp_w_byte(1)
					udp_w_short(Tunit\player_ping)
					con_add("send player data "+TCunit\player_name$+" #"+Tunit\id+" To "+DottedIP(ip)+":"+port)
				EndIf
			EndIf
		Next
		For Tunit.Tunit=Each Tunit
			If Tunit\id<100 Then
				If Tunit\id<>1 And Tunit\id<>id Then
					udp_send(Tunit\player_ip,Tunit\player_port,3)
					udp_w_byte(101)
					udp_w_tiny(TCunit\player_name$)
					udp_w_byte(TCunit\id)
					udp_w_float(EntityX(TCunit\h))
					udp_w_float(EntityY(TCunit\h))
					udp_w_float(EntityZ(TCunit\h))
					udp_w_float(EntityYaw(TCunit\h))
					udp_w_short(TCunit\player_score)
					udp_w_short(TCunit\player_deads)
					udp_w_byte(1)
					udp_w_short(Tunit\player_ping)
					con_add("send player data "+TCunit\player_name$+" #"+TCunit\id+" To "+DottedIP(Tunit\player_ip)+":"+Tunit\player_port)
				EndIf
			EndIf
		Next
	EndIf
End Function


Function mp_getplayerdata(mode)
	Local name$=udp_r_tiny()
	Local id=udp_r_byte()
	Local x#=udp_r_float()
	Local y#=udp_r_float()
	Local z#=udp_r_float()
	Local yaw#=udp_r_float()
	Local score=udp_r_short()
	Local deads=udp_r_short()
	Local dead=udp_r_byte()
	Local ping=udp_r_short()
	con_add("get player data "+name$+" #"+id)
	If con_unit(id)=0 Then
		set_unit(id,1,x#,z#)
	EndIf
	PositionEntity TCunit\h,x#,y#,z#
	RotateEntity TCunit\h,0,yaw#,0
	TCunit\player_name$=name$
	TCunit\player_score=score
	TCunit\player_deads=deads
	TCunit\player_ping=ping
	If mode=101 Then
		if_msg(name$+"[[loc:engine.daf7578d3ee6]]",Cbmpf_green)
	EndIf
End Function


Function mp_scoreboard()
	DrawImage gfx_win,215,0
	DrawImage gfx_icons,236,5,Cicon_globe
	DrawImage gfx_winbar,215,42
	
	bmpf_txt(236+37,10,set_sv_name$)
	
	bmpf_txt_c(236+37+400,7,DottedIP(set_sv_ip)+":"+set_sv_port,Cbmpf_tiny)
	bmpf_txt_c(236+37+400,19,set_sv_map$,Cbmpf_tiny)
	
	bmpf_txt(236,63,"Player",Cbmpf_dark)
	bmpf_txt(236+300,63,"[[loc:engine.38e5a46cbc5a]]",Cbmpf_dark)
	bmpf_txt(236+370,63,"[[loc:engine.d9684930a271]]",Cbmpf_dark)
	bmpf_txt(236+470,63,"Net",Cbmpf_dark)
	
	y=63+20
	For Tunit.Tunit=Each Tunit
		If Tunit\id<100 Then
			
			bmpf_txt(236,y,Tunit\player_name)
			If Tunit\id=1 Then bmpf_txt(236,y+15,"[[loc:engine.aef7de28d529]]",Cbmpf_tiny)
			
			If Tunit\player_ping=0 Then
				bmpf_txt(236+470,y,"...",Cbmpf_dark)
			Else
				col=Cbmpf_red
				If Tunit\player_ping<200 Then col=Cbmpf_yellow
				If Tunit\player_ping<100 Then col=Cbmpf_green
				bmpf_txt(236+470,y,Tunit\player_ping,col)
			EndIf
			
			y=y+40
		
		EndIf
	Next
	
End Function