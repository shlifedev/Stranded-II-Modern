Function load_map(path$,pw$)
	If Lower(Right(path$,4))=".sav" Then
		If modern_save_valid(path$)=0 Then
			gui_msg("[[loc:modern.load_failed]]",Cbmpf_red)
			Return 0
		EndIf
	EndIf
	map_id$="0"
	If FileType(path$)=1 Then map_id$=FileSize(path$)
	Local stream=ReadFile(path$)
	If stream=0 Then con_add("Unable to load Map "+path$,Cbmpf_red):Return 0
	e_clear()
	tmp_loading=1
	
	bmpf_loadscreen(s$(21),10)
	in$=ReadLine(stream)
	If Left(in$,15)<>"### Stranded II" Then RuntimeError("Invalid Map! (Invalid Header)")
	ReadLine(stream)
	map_id$=map_id$+ReadLine(stream)
	map_id$=map_id$+ReadLine(stream)
	Local f$=ReadLine(stream)
	map_mode$=ReadLine(stream)
	
	Local tfobject=0
	Local tfunit=0
	Local tfitem=0
	Local tfstring$=ReadLine(stream)
	If tfstring$<>"" Then
		tfobject=Int(Mid(tfstring$,1,1))
		tfunit=Int(Mid(tfstring$,2,1))
		tfitem=Int(Mid(tfstring$,3,1))
	EndIf
	
	ReadLine(stream)
	ReadLine(stream)
	ReadLine(stream)
	ReadLine(stream)
	ReadLine(stream)
	
	If map_image<>0 Then FreeImage(map_image)
	map_image=CreateImage(96,72)
	SetBuffer ImageBuffer(map_image)
	LockBuffer ImageBuffer(map_image)
	For x=0 To 96-1
		For y=0 To 72-1
			r=ReadByte(stream)
			g=ReadByte(stream)
			b=ReadByte(stream)
			rgb=255*$1000000 + r*$10000 + g*$100 + b
			WritePixelFast(x,y,rgb)
		Next
	Next
	UnlockBuffer ImageBuffer(map_image)
	SetBuffer BackBuffer()
	If set_debug_load=1 Then
		gui_win_image("",map_image)
	EndIf

	Local pwkey=ReadByte(stream)
	Local readpw$=ReadLine(stream)
	If m_section=Csection_editor Then			
		If code(pw$,pwkey,0)<>readpw$ Then
			con_add("ERROR: Invalid Map Password!",Cbmpf_red)
			tmp_loading=0
			Return 0
		EndIf
		map_password$=pw$
	EndIf
	
	If set_debug_load=1 Then
		gui_msg("Map PW Key: "+pwkey+", PW: "+code(readpw$,pwkey,1)+" (len: "+Len(readpw$)+")")
	EndIf
	
	map_name$=getfilename$(path$)
	map_path$=path$
	map_day=ReadInt(stream)
	map_hour=ReadByte(stream)
	map_minute=ReadByte(stream)
	map_freezetime=ReadByte(stream)
	map_skybox$=ReadString(stream)
	map_multiplayer=ReadByte(stream)
	map_climate=ReadByte(stream)
	map_music$=ReadString(stream)
	map_briefing$=ReadString(stream)
	map_fog(0)=ReadByte(stream)
	map_fog(1)=ReadByte(stream)
	map_fog(2)=ReadByte(stream)
	map_fog(3)=ReadByte(stream)
	map_lday=map_day
	map_lhour=map_hour
	map_lminute=map_minute
	map_timeupdate=1
	ReadByte(stream)
		
	For i=0 To 9
		in_quickslot(i)=ReadString(stream)
	Next
	
	If set_debug_load=1 Then
		gui_msg("Map Skybox: "+map_skybox$)
		gui_msg("Map Briefing: "+map_briefing$)
		gui_msg("Map Vars Loaded (day "+map_day+", time "+map_hour+":"+map_minute+")")
	EndIf
	
	If map_hour<0 Or map_hour>24 Then
		con_add("ERROR: Error in Map Vars (Hour)!",Cbmpf_red)
		tmp_loading=0
		Return 0
	ElseIf map_minute<0 Or map_minute>60 Then
		con_add("ERROR: Error in Map Vars (Minute)!",Cbmpf_red)
		tmp_loading=0
		Return 0
	EndIf
	
	
	bmpf_loadscreen(s$(22),20)					
	h=ReadInt(stream)
	ter_tex_color=CreateTexture(h,h,256)
	SetBuffer TextureBuffer(ter_tex_color)
	LockBuffer TextureBuffer(ter_tex_color)
	For x=0 To h-1
		For y=0 To h-1
			r=ReadByte(stream)
			g=ReadByte(stream)
			b=ReadByte(stream)
			rgb=255*$1000000 + r*$10000 + g*$100 + b
			WritePixelFast(x,y,rgb)
		Next
	Next
	UnlockBuffer TextureBuffer(ter_tex_color)
	SetBuffer BackBuffer()
	
	If set_debug_load=1 Then gui_msg("Colormap loaded ("+h+"x"+h+")")
	
	bmpf_loadscreen(s$(23),30)
	ter_size=ReadInt(stream)
	If ter_size<0 Then ter_size=32
	e_terrain(ter_size,"generated")
	For x=0 To ter_size
		For y=0 To ter_size
			ModifyTerrain(ter,x,y,ReadFloat(stream))
		Next
	Next
	
	If set_debug_load=1 Then gui_msg("Heightmap loaded ("+ter_size+"x"+ter_size+")")

	bmpf_loadscreen(s$(24),35)
	grass_map()
	For x=0 To h
		For y=0 To h
			grass_rgb(x,y,3)=ReadByte(stream)
		Next
	Next
	grass_x=-2147483648
	grass_spread()
	
	If set_debug_load=1 Then gui_msg("Grassmap loaded")
	
	ter_groundy=TerrainY(ter,-((ter_size/2)*Cworld_size),0,-((ter_size/2)*Cworld_size))

	
	map_briefing_key$=preparse_string$(map_briefing$)
	parse_globalevent("preload")
	parse_sel_event("preload")
	
	
	bmpf_loadscreen(s$(25),40)
	c=ReadInt(stream)
	For i=1 To c
		id=ReadInt(stream)
		If tfobject Then
			typ=ReadShort(stream)
		Else
			typ=ReadByte(stream)
		EndIf
		xp#=ReadFloat(stream)
		zp#=ReadFloat(stream)
		yaw#=ReadFloat(stream)
		If set_object(id,typ,xp#,zp#,yaw#)<>-1
			TCobject\health#=ReadFloat(stream)
			TCobject\health_max#=ReadFloat(stream)
			TCobject\daytimer=ReadInt(stream)
			HideEntity TCobject\h
			If TCobject\daytimer<0 Then
				grow_object(TCobject)
			EndIf
		Else
			RuntimeError("Object of type "+typ+" is undefined! Are you sure that you are using the right mod/version for this map?")
		EndIf
	Next
	serialpeak_object()
	If set_debug_load=1 Then gui_msg("Objects loaded ("+c+")")
	
	bmpf_loadscreen(s$(26),70)
	c=ReadInt(stream)
	For i=1 To c
		id=ReadInt(stream)
		If tfunit Then
			typ=ReadShort(stream)
		Else
			typ=ReadByte(stream)
		EndIf
		xp#=ReadFloat(stream)
		yp#=ReadFloat(stream)
		zp#=ReadFloat(stream)
		If set_unit(id,typ,xp#,zp#)<>-1 Then
			HideEntity TCunit\h
			PositionEntity TCunit\h,xp#,yp#,zp#								
			ShowEntity TCunit\h
			If TCunit\vh<>0 Then
				HideEntity TCunit\vh
				PositionEntity TCunit\vh,xp#,yp#,zp#
				ShowEntity TCunit\vh
			EndIf
			yaw#=ReadFloat(stream)
			RotateEntity TCunit\h,0,yaw#,0
			TCunit\health#=ReadFloat(stream)
			TCunit\health_max#=ReadFloat(stream)
			TCunit\hunger#=ReadFloat(stream)
			TCunit\thirst#=ReadFloat(stream)
			TCunit\exhaustion#=ReadFloat(stream)
			TCunit\ai_cx#=ReadFloat(stream)
			TCunit\ai_cz#=ReadFloat(stream)
			If m_section<>Csection_editor Then ai_ini()
		Else
			RuntimeError("Unit of type "+typ+" is undefined! Are you sure that you are using the right mod/version for this map?")
		EndIf
	Next
	serialpeak_unit()
	If set_debug_load=1 Then gui_msg("Units loaded ("+c+")")
		
	bmpf_loadscreen(s$(27),80)
	c=ReadInt(stream)
	For i=1 To c
		id=ReadInt(stream)
		If tfitem Then
			typ=ReadShort(stream)
		Else
			typ=ReadByte(stream)
		EndIf
		xp#=ReadFloat(stream)
		yp#=ReadFloat(stream)
		zp#=ReadFloat(stream)
		If set_item(id,typ,xp#,yp#,zp#)<>-1 Then
			yaw#=ReadFloat(stream)
			RotateEntity TCitem\h,0,yaw#,0
			TCitem\health#=ReadFloat(stream)
			TCitem\count=ReadInt(stream)
			TCitem\parent_class=ReadByte(stream)
			TCitem\parent_mode=ReadByte(stream)
			TCitem\parent_id=ReadInt(stream)
			If TCitem\parent_mode=Cpm_out Then
				If TCitem\parent_class<>0 Then 
					RotateEntity TCitem\h,-90,yaw#,0
				EndIf
			EndIf
		Else
			RuntimeError("Item of type "+typ+" is undefined! Are you sure that you are using the right mod/version for this map?")
		EndIf
	Next
	serialpeak_item()
	If set_debug_load=1 Then gui_msg("Items loaded ("+c+")")
	
	bmpf_loadscreen(s$(28),90)
	c=ReadInt(stream)
	For i=1 To c
		id=ReadInt(stream)
		typ=ReadByte(stream)
		xp#=ReadFloat(stream)
		yp#=ReadFloat(stream)
		zp#=ReadFloat(stream)
		set_info(id,typ,xp#,yp#,zp#)
		pitch#=ReadFloat(stream)
		yaw#=ReadFloat(stream)
		RotateEntity TCinfo\h,pitch#,yaw#,0
		TCinfo\vars$=ReadString(stream)
		info_stv()
		Select typ
			Case 5 info_setupsprite(TCinfo)
			Case 47 info_setuploudspeaker(TCinfo)
		End Select
	Next
	serialpeak_info()
	If set_debug_load=1 Then gui_msg("Infos loaded ("+c+")")
	
	bmpf_loadscreen(s$(29),92)
	c=ReadInt(stream)
	For i=1 To c
		typ=ReadByte(stream)
		parent_class=ReadByte(stream)
		parent_id=ReadInt(stream)
		If set_state(typ,parent_class,parent_id) Then
			TCstate\x#=ReadFloat(stream)
			TCstate\y#=ReadFloat(stream)
			TCstate\z#=ReadFloat(stream)
			TCstate\fx#=ReadFloat(stream)
			TCstate\fy#=ReadFloat(stream)
			TCstate\fz#=ReadFloat(stream)
			TCstate\value=ReadInt(stream)
			TCstate\value_f#=ReadFloat(stream)
			TCstate\value_s$=ReadString(stream)
			look_state()
		Else
			ReadFloat(stream):ReadFloat(stream):ReadFloat(stream)
			ReadFloat(stream):ReadFloat(stream):ReadFloat(stream)
			ReadInt(stream):ReadFloat(stream):ReadString(stream)
			con_add("Unable to create state "+typ+" ("+Dstate_name$(typ)+") @ "+parent_class+","+parent_id)
		EndIf
	Next
	If set_debug_load=1 Then gui_msg("States loaded ("+c+")")
	
	bmpf_loadscreen(s$(30),95)
	c=ReadInt(stream)
	For i=1 To c
		Tx.Tx=New Tx
		Tx\typ=ReadByte(stream)
		Tx\parent_class=ReadByte(stream)
		Tx\parent_id=ReadInt(stream)
		Tx\mode=ReadInt(stream)
		Tx\key$=ReadString(stream)
		Tx\value$=ReadString(stream)
		Tx\stuff$=ReadString(stream)
	Next
	load_map_extensions()
	If set_debug_load=1 Then gui_msg("Extensions loaded ("+c+")")
	
	If m_section<>Csection_editor Then
		If map_mode$="map" Then
			defvar_ini()
		EndIf
	EndIf
	
	pv_loadmaploaded=0
	For Txc.Txc=Each Txc
		pv_loadmaploaded=1
		If Txc\mode<7 Then
			Select Txc\mode
				Case 1
					For cx.Tx=Each Tx
						If cx\mode=Txc\mode Then
							If cx\key$=Txc\key$ Then Delete cx
						EndIf
					Next
				Case 3
					For cx.Tx=Each Tx
						If cx\mode=Txc\mode Then
							If cx\key$=Txc\key$ Then Delete cx
						EndIf
					Next					
				Case 5
					For cx.Tx=Each Tx
						If cx\mode=Txc\mode Then
							If cx\key$=Txc\key$ Then Delete cx
						EndIf
					Next
			End Select
			Tx.Tx=New Tx
			Tx\typ=Txc\typ
			Tx\parent_class=Txc\parent_class
			Tx\parent_id=Txc\parent_id
			Tx\mode=Txc\mode
			Tx\key$=Txc\key$
			Tx\value$=Txc\value$
			Tx\stuff$=Txc\stuff$
			Delete Txc
		EndIf
	Next
	
	If map_mode$="sav" Then
		Local campitch#=ReadFloat(stream)
		Local camyaw#=ReadFloat(stream)
		RotateEntity cam,campitch#,camyaw#,0
	EndIf
	
	preparse()
	

	ReadLine(stream)
	ReadLine(stream)
	in$=ReadLine(stream)
	If in$<>"www.unrealsoftware.de" Then
		
		con_add("ERROR: Unable to load map completely!",3)
		con_add("ERROR: It may be damaged!",3)
		con_add("ERROR: Possible attachments are lost!",3)
		
	Else
	
		If map_mode$="map" Then
			For Tat.Tat=Each Tat
				Delete Tat
			Next
			While Not Eof(stream)
				atfile$=strinv$(ReadLine(stream))
				atsize=ReadInt(stream)
				Tat.Tat=New Tat
				Tat\path$=atfile$
				If FileType(set_rootdir$+"mods\"+set_moddir$+"\"+atfile$)=1 Then
					For i=1 To atsize
						ReadByte(stream)
					Next
				Else
					atstream=WriteFile(set_rootdir$+"mods\"+set_moddir$+"\"+atfile$)
					If atstream<>0 Then
						For i=1 To atsize
							byte=ReadByte(stream)
							byte=255-byte
							WriteByte(atstream,byte)
						Next
						CloseFile(atstream)
						atstream=0
					EndIf
				EndIf
			Wend
		EndIf
	
	EndIf

	If map_music$<>"" Then
		amb_file=LoadSound("sfx\"+map_music$)
	EndIf
	
	bmpf_loadscreen(s$(31),97)
	e_environment()
	
	bmpf_loadscreen(s$(32),100)
	con_add("")
	If m_section=Csection_game_sp Then
		If map_mode$="map" Then
			g_player=1
			game_playerspawn(1)
			game_setplayer(1)
		Else
			game_setplayer(1)
		EndIf
	EndIf
	
	For Txc.Txc=Each Txc
		If Txc\mode=7 Then
			citem=set_item(-1,Txc\typ,0,0,0,Int(Txc\value$))
			If citem>-1 Then
				store_item(citem,Cclass_unit,g_player)
			EndIf
		ElseIf Txc\mode=8 Then
			If Txc\typ<10 Then
				in_quickslot(Txc\typ)=Int(Txc\value$)
			ElseIf Txc\typ=10 Then
				g_cplayer\player_weapon=Int(Txc\value$)
			ElseIf Txc\typ=11 Then
				g_cplayer\player_ammo=Int(Txc\value$)
			EndIf
		ElseIf Txc\mode=9 Then
			set_state(Txc\typ,Txc\parent_class,Txc\parent_id)
			If get_state(Txc\typ,Txc\parent_class,Txc\parent_id)
				split$(Txc\stuff$,"",15)
				TCstate\x#=Float(splits(0))
				TCstate\y#=Float(splits(1))
				TCstate\z#=Float(splits(2))
				TCstate\fx#=Float(splits(3))
				TCstate\fy#=Float(splits(4))
				TCstate\fz#=Float(splits(5))
				TCstate\value=Float(splits(6))
				TCstate\value_f#=Float(splits(7))
				TCstate\value_s$=Float(splits(8))
				TCstate\r=Float(splits(9))
				TCstate\g=Float(splits(10))
				TCstate\b=Float(splits(11))
				look_state()
			EndIf
		EndIf
		Delete Txc
	Next
	
	tmp_loading=0
	If map_mode$="map" Then
		parse_globalevent("start")
		parse_sel_event("start")
	EndIf
	parse_globalevent("load")
	parse_sel_event("load")
	
	
	FlushKeys()
	FlushMouse()
	CloseFile(stream)
	blackframe=10
	
	modern_reset_clock()
	ms=lastms
	gt=0
	
	m=m_menu
	m_menu=0
	game_setcam()
	m_menu=m
	
	Return 1
End Function


Function load_map_id$(path$)
	id$="0"
	If FileType(path$)=1 Then
		id$=FileSize(path$)
		Local stream=ReadFile(path$)
		If stream<>0 Then
			ReadLine(stream)
			ReadLine(stream)
			id$=id$+ReadLine(stream)
			id$=id$+ReadLine(stream)
			CloseFile(stream)
		EndIf
	EndIf	
	Return id$
End Function


Function load_map_header(path$,image=1)
	Local stream=ReadFile(path$)
	If stream=0 Then con_add("Unable to load Map Info from "+path$,Cbmpf_red):Return 0
	
	in$=ReadLine(stream)
	If Left(in$,15)<>"### Stranded II" Then RuntimeError("Invalid Map! (Invalid Header)")
	ReadLine(stream)
	sg_date$=ReadLine(stream)
	sg_time$=ReadLine(stream)
	Local f$=ReadLine(stream)
	map_mode$=ReadLine(stream)
	ReadLine(stream)
	ReadLine(stream)
	ReadLine(stream)
	ReadLine(stream)
	ReadLine(stream)
	ReadLine(stream)
	
	If image=1 Then
		If map_image<>0 Then FreeImage(map_image)
		map_image=CreateImage(96,72)
		SetBuffer ImageBuffer(map_image)
		LockBuffer ImageBuffer(map_image)
		For x=0 To 96-1
			For y=0 To 72-1
				r=ReadByte(stream)
				g=ReadByte(stream)
				b=ReadByte(stream)
				rgb=255*$1000000 + r*$10000 + g*$100 + b
				WritePixelFast(x,y,rgb)
			Next
		Next
		UnlockBuffer ImageBuffer(map_image)
		SetBuffer BackBuffer()
		MaskImage map_image,255,0,255
	Else
		For x=0 To 96-1
			For y=0 To 72-1
				ReadByte(stream)
				ReadByte(stream)
				ReadByte(stream)
			Next
		Next
	EndIf
		
	ReadByte(stream)
	ReadLine(stream)

	ReadInt(stream)
	ReadByte(stream)
	ReadByte(stream)
	ReadByte(stream)
	ReadString(stream)
	sg_multiplayer=ReadByte(stream)
	ReadByte(stream)
	ReadString(stream)
	ReadString(stream)
	ReadByte(stream)

	CloseFile(stream)
End Function


Function load_savegames(loadfirst=1)
	For Tsg.Tsg=Each Tsg
		Delete Tsg
	Next
	Local dir=ReadDir("saves")
	Local file$
	If dir<>0 Then
		Repeat
			file$=NextFile(dir)
			If file$="" Then Exit
			If FileType("saves\"+file$)=1 Then
				If Lower(Right(file$,4))=".sav" And modern_save_valid("saves\"+file$) Then
					Tsg.Tsg=New Tsg
					Tsg\name$=Left(file$,Len(file$)-4)
				EndIf
			EndIf
		Forever
		CloseDir dir
	Else
		con_add("ERROR: Unable to read 'saves' dir!",Cbmpf_red)
	EndIf
	If loadfirst Then
		If map_image<>0 Then FreeImage map_image
		map_image=0
		For Tsg.Tsg=Each Tsg
			load_map_header("saves\"+Tsg\name$+".sav")
			in_opt(0)=1
			in_input$(0)=""
			Return
		Next
	EndIf
End Function


Function load_maps(loadfirst=1,mponly=0)
	For Tsg.Tsg=Each Tsg
		Delete Tsg
	Next
	Local dir=ReadDir("maps")
	Local file$
	If dir<>0 Then
		Repeat
			file$=NextFile(dir)
			If file$="" Then Exit
			If FileType("maps\"+file$)=1 Then
				If Right(file$,3)=".s2" Then
					Tsg.Tsg=New Tsg
					Tsg\name$=Left(file$,Len(file$)-3)
				EndIf
			EndIf
		Forever
		CloseDir dir
	Else
		con_add("ERROR: Unable to read 'maps' dir!",Cbmpf_red)
	EndIf
	If mponly=1 Then
		For Tsg.Tsg=Each Tsg
			load_map_header("maps\"+Tsg\name$+".s2",0)
			If sg_multiplayer=0 Then
				Delete Tsg
			EndIf
		Next
	ElseIf mponly=-1 Then
		For Tsg.Tsg=Each Tsg
			load_map_header("maps\"+Tsg\name$+".s2",0)
			If sg_multiplayer=1 Then
				Delete Tsg
			EndIf
		Next		
	EndIf
	If loadfirst Then
		If map_image<>0 Then FreeImage map_image
		map_image=0
		For Tsg.Tsg=Each Tsg
			load_map_header("maps\"+Tsg\name$+".s2")
			in_opt(0)=1
			in_win_map$=Tsg\name$
			Return
		Next
	EndIf
End Function


Function load_map_extensions()
	For Tx.Tx=Each Tx
		If Tx\mode=6 Then
			Select Tx\key$
				Case "g_drive"
					g_drive=Int(Tx\value$)
					If con_unit(g_drive) Then
						game_input_vehicledata(TCunit\typ)
						TCunit\ai_mode=0
						TCunit\ai_duration=5000
						TCunit\ai_timer=gt
						Animate TCunit\mh,0
						TCunit\ani=0
					EndIf
				Case "env_cweather" env_cweather=Int(Tx\value$)
				Case "w/a"
					If con_unit(1) Then
						TCunit\player_weapon=Tx\parent_class
						TCunit\player_ammo=Tx\parent_id
						If Tx\value$<>"" Then
							TCunit\player_weapon=Int(Tx\value$)
						EndIf
					EndIf
				Case "t"
					Ttimer.Ttimer=New Ttimer
					Ttimer\parent_class=Tx\parent_class
					Ttimer\parent_id=Tx\parent_id
					split(Tx\value$,",",3)
					Ttimer\typ=splits$(0)
					Ttimer\duration=splits$(1)
					Ttimer\start=splits$(2)
					Ttimer\mode=splits$(3)
					Ttimer\scr$=Tx\stuff$
				Case "f"
					Tfrap.Tfrap=New Tfrap
					Tfrap\parent_class=Tx\parent_class
					Tfrap\parent_id=Tx\parent_id
					split(Tx\value$,",",2)
					Tfrap\y#=Float(splits$(0))
					Tfrap\pitch#=Float(splits$(1))
					Tfrap\roll#=Float(splits$(2))
					Select Tfrap\parent_class
						Case Cclass_object
							If con_object(Tfrap\parent_id) Then
								RotateEntity TCobject\h,Tfrap\pitch#,EntityYaw(TCobject\h),Tfrap\roll#
								PositionEntity TCobject\h,EntityX(TCobject\h),Tfrap\y#,EntityZ(TCobject\h)
								If TCobject\ch<>0 Then
									RotateEntity TCobject\ch,Tfrap\pitch#,EntityYaw(TCobject\h),Tfrap\roll#
									PositionEntity TCobject\ch,EntityX(TCobject\h),Tfrap\y#,EntityZ(TCobject\h)
								EndIf
							EndIf
						Case Cclass_unit
							If con_unit(Tfrap\parent_id) Then
								PositionEntity TCunit\h,EntityX(TCunit\h),Tfrap\y#,EntityZ(TCunit\h)
							EndIf
						Case Cclass_item
							If con_item(Tfrap\parent_id) Then
								PositionEntity TCitem\h,EntityX(TCitem\h),Tfrap\y#,EntityZ(TCitem\h)
							EndIf
					End Select
				Case "u"
					Tup.Tup=New Tup
					Tup\mode=Tx\parent_class
					Tup\info=Tx\parent_id
					Tup\unit=Int(Tx\value$)
				Case "i"
					If Tx\parent_class=Cclass_unit Then
						For Tunit.Tunit=Each Tunit
							If Tunit\id=Tx\parent_id Then
								Tunit\freeze=1
								Exit
							EndIf
						Next
					EndIf
				Case "t3d"	
					t3d.t3d=New t3d
					t3d\class=Tx\parent_class
					t3d\id=Tx\parent_id
					t3d\txt$=Tx\value$
					split(Tx\stuff$,",",2)
					t3d\typ=Int(splits$(0))
					t3d\offset=Int(splits$(1))
					t3d\range=Int(splits$(2))
				Case "lasttime"
					split(Tx\stuff$,",",2)
					map_lday=Int(splits$(0))
					map_lhour=Int(splits$(1))
					map_lminute=Int(splits$(2))					
					map_timeupdate=1
			End Select
			Delete Tx
		EndIf
	Next
End Function


Function load_recent(add$="")
	For Trecent.Trecent=Each Trecent
		Delete Trecent
	Next
	Local update=0
	Local limit=0
	Local file=ReadFile("sys/recent.cache")
	If file<>0 Then
		If add$<>"" Then
			Trecent.Trecent=New Trecent
			Trecent\file$=add$
		EndIf
		While Not Eof(file)
			in$=ReadLine(file)
			If in$<>"" Then
				If in$<>add$ Then
					Trecent.Trecent=New Trecent
					Trecent\file$=in$
					limit=limit+1
					If limit>=15 Then Exit
				EndIf
			EndIf
		Wend
		CloseFile file
		update=1
	Else
		If add<>"" Then
			Trecent.Trecent=New Trecent
			Trecent\file$=add$
			update=1
		EndIf
	EndIf
	If update=1 Then
		limit=0
		file=WriteFile("sys/recent.cache")
		If file<>0 Then
			For Trecent.Trecent=Each Trecent
				limit=limit+1
				WriteLine file,Trecent\file$
				If limit>=15 Then Exit
			Next
			CloseFile(file)
		EndIf
	EndIf
End Function
