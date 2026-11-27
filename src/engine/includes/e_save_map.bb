Function save_map(path$,mode$,pw$,s_skills=1,s_items=1,s_vars=1,s_diary=1,s_states=1,s_locks=1)
	If modern_saving Then Return False
	modern_saving=1
	Local staged$=path$+".tmp",ok
	ok=save_map_staged(staged$,mode$,pw$,s_skills,s_items,s_vars,s_diary,s_states,s_locks)
	If ok And mode$="sav" Then ok=modern_save_valid(staged$)
	If ok Then ok=modern_commit_file(path$,staged$)
	modern_saving=0
	modern_reset_clock()
	If ok Then
		If m_section<>Csection_editor Then parse_globalevent("postsave")
		If mode$="sav" Then if_msg("[[loc:modern.saved]]",Cbmpf_green)
	Else
		con_add("Save failed: "+path$+" (Win32 "+modern_file_error+")",Cbmpf_red)
		If mode$="sav" Then gui_msg("[[loc:modern.save_failed]]",Cbmpf_red)
	EndIf
	Return ok
End Function

Function save_map_staged(path$,mode$,pw$,s_skills,s_items,s_vars,s_diary,s_states,s_locks)
	Local stream=WriteFile(path$)
	If stream=0 Then Return 0
	
	If m_section<>Csection_editor Then
		parse_globalevent("presave")
		parse_all()
	EndIf
	
	bmpf_loadscreen(s$(10),10)
	WriteLine(stream,"### Stranded II Mapfile ©by Unreal Software 2004-2007")
	WriteLine(stream,Cversion$)
	WriteLine(stream,CurrentDate())
	WriteLine(stream,CurrentTime())
	WriteLine(stream,"default")
	WriteLine(stream,mode$)
	
	Local tfobject=0
	Local tfunit=0
	Local tfitem=0
	If Cobject_count>255 Then tfobject=1
	If Cunit_count>255 Then tfunit=1
	If Citem_count>255 Then tfitem=1
	If (tfobject+tfunit+tfitem)>0 Then
		Local tfstring$=""
		tfstring$=tfstring$+Str(tfobject)
		tfstring$=tfstring$+Str(tfunit)
		tfstring$=tfstring$+Str(tfitem)
		WriteLine(stream,tfstring$)
	Else
		WriteLine(stream,"")
	EndIf
	
	WriteLine(stream,"")
	WriteLine(stream,"")
	WriteLine(stream,"")
	WriteLine(stream,"")
	WriteLine(stream,"###")
	
	save_map_image(mode$)
	SetBuffer ImageBuffer(map_image)
	LockBuffer ImageBuffer(map_image)
	For x=0 To 96-1
		For y=0 To 72-1
			rgb=ReadPixelFast(x,y)
			WriteByte(stream,(rgb And $FF0000)/$10000)
			WriteByte(stream,(rgb And $FF00)/$100)
			WriteByte(stream,rgb And $FF)
		Next
	Next
	UnlockBuffer ImageBuffer(map_image)
	SetBuffer BackBuffer()
	
	Local pwkey=Rand(5,250)
	WriteByte(stream,pwkey)
	encodedpw$=code(pw$,pwkey,0)
	WriteLine(stream,encodedpw$)
	map_password$=pw$
	
	WriteInt(stream,map_day)
	WriteByte(stream,map_hour)
	WriteByte(stream,map_minute)
	WriteByte(stream,map_freezetime)
	WriteString(stream,map_skybox$)
	WriteByte(stream,map_multiplayer)
	WriteByte(stream,map_climate)
	WriteString(stream,map_music$)
	WriteString(stream,map_briefing$)
	WriteByte(stream,map_fog(0))
	WriteByte(stream,map_fog(1))
	WriteByte(stream,map_fog(2))
	WriteByte(stream,map_fog(3))
	WriteByte(stream,0)
	
	For i=0 To 9
		WriteString(stream,in_quickslot(i))
	Next
	
	
	bmpf_loadscreen(s$(11),20)
	h=TextureHeight(ter_tex_color)
	WriteInt(stream,h)
	SetBuffer TextureBuffer(ter_tex_color)
	LockBuffer TextureBuffer(ter_tex_color)
	For x=0 To h-1
		For y=0 To h-1
			rgb=ReadPixelFast(x,y)
			WriteByte(stream,(rgb And $FF0000)/$10000)
			WriteByte(stream,(rgb And $FF00)/$100)
			WriteByte(stream,rgb And $FF)
		Next
	Next
	UnlockBuffer TextureBuffer(ter_tex_color)
	SetBuffer BackBuffer()
	
	bmpf_loadscreen(s$(12),30)
	WriteInt(stream,ter_size)
	For x=0 To ter_size
		For y=0 To ter_size
			WriteFloat(stream,TerrainHeight(ter,x,y))
		Next
	Next
	
	bmpf_loadscreen(s$(13),35)
	For x=0 To h
		For y=0 To h
			WriteByte(stream,grass_rgb(x,y,3))
		Next
	Next
	
		
	bmpf_loadscreen(s$(14),40)
	c=0
	For Tobject.Tobject=Each Tobject:c=c+1:Next
	WriteInt(stream,c)
	For Tobject.Tobject=Each Tobject
		WriteInt(stream,Tobject\id)
		If tfobject Then
			WriteShort(stream,Tobject\typ)
		Else
			WriteByte(stream,Tobject\typ)
		EndIf
		WriteFloat(stream,EntityX(Tobject\h))
		WriteFloat(stream,EntityZ(Tobject\h))
		WriteFloat(stream,EntityYaw(Tobject\h))
		WriteFloat(stream,Tobject\health#)
		WriteFloat(stream,Tobject\health_max#)
		WriteInt(stream,Tobject\daytimer)
	Next

	Local peak=100
	If mode$="sav" Then peak=0
	bmpf_loadscreen(s$(15),70)
	c=0
	For Tunit.Tunit=Each Tunit
		If Tunit\id>=peak Then c=c+1
	Next
	WriteInt(stream,c)
	For Tunit.Tunit=Each Tunit
		If Tunit\id>=peak Then
			If mode$="map" Then
				Tunit\ai_cx#=EntityX(Tunit\h)
				Tunit\ai_cz#=EntityZ(Tunit\h)
			EndIf
			WriteInt(stream,Tunit\id)
			If tfunit Then
				WriteShort(stream,Tunit\typ)
			Else
				WriteByte(stream,Tunit\typ)
			EndIf								
			If mode$="sav" And Tunit=g_cplayer And col_player_valid And col_player_managed(Tunit) Then
				WriteFloat(stream,col_player_x#)
				WriteFloat(stream,col_player_y#)
				WriteFloat(stream,col_player_z#)
			Else
				WriteFloat(stream,EntityX(Tunit\h))
				WriteFloat(stream,EntityY(Tunit\h))
				WriteFloat(stream,EntityZ(Tunit\h))
			EndIf
			WriteFloat(stream,EntityYaw(Tunit\h))
			WriteFloat(stream,Tunit\health#)
			WriteFloat(stream,Tunit\health_max#)
			WriteFloat(stream,Tunit\hunger#)
			WriteFloat(stream,Tunit\thirst#)
			WriteFloat(stream,Tunit\exhaustion#)
			WriteFloat(stream,Tunit\ai_cx#)
			WriteFloat(stream,Tunit\ai_cz#)
		EndIf
	Next
	
	bmpf_loadscreen(s$(16),80)
	If s_items=1 Then
		c=0
		For Titem.Titem=Each Titem:c=c+1:Next
		WriteInt(stream,c)
		For Titem.Titem=Each Titem
			WriteInt(stream,Titem\id)
			If tfitem Then
				WriteShort(stream,Titem\typ)
			Else
				WriteByte(stream,Titem\typ)
			EndIf
			WriteFloat(stream,EntityX(Titem\h))
			WriteFloat(stream,EntityY(Titem\h))
			WriteFloat(stream,EntityZ(Titem\h))
			WriteFloat(stream,EntityYaw(Titem\h))
			WriteFloat(stream,Titem\health#)
			WriteInt(stream,Titem\count)
			WriteByte(stream,Titem\parent_class)
			WriteByte(stream,Titem\parent_mode)
			WriteInt(stream,Titem\parent_id)
		Next
	Else
		c=0
		For Titem.Titem=Each Titem
			If Titem\parent_class<>Cclass_unit Or Titem\parent_id<>1 Then
				c=c+1
			EndIf
		Next
		WriteInt(stream,c)
		For Titem.Titem=Each Titem
			If Titem\parent_class<>Cclass_unit Or Titem\parent_id<>1 Then
				WriteInt(stream,Titem\id)
				If tfitem Then
					WriteShort(stream,Titem\typ)
				Else
					WriteByte(stream,Titem\typ)
				EndIf
				WriteFloat(stream,EntityX(Titem\h))
				WriteFloat(stream,EntityY(Titem\h))
				WriteFloat(stream,EntityZ(Titem\h))
				WriteFloat(stream,EntityYaw(Titem\h))
				WriteFloat(stream,Titem\health#)
				WriteInt(stream,Titem\count)
				WriteByte(stream,Titem\parent_class)
				WriteByte(stream,Titem\parent_mode)
				WriteInt(stream,Titem\parent_id)
			EndIf
		Next		
	EndIf
		
	bmpf_loadscreen(s$(17),90)
	c=0
	For Tinfo.Tinfo=Each Tinfo:c=c+1:Next								
	WriteInt(stream,c)
	For Tinfo.Tinfo=Each Tinfo
		WriteInt(stream,Tinfo\id)
		WriteByte(stream,Tinfo\typ)
		WriteFloat(stream,EntityX(Tinfo\h))
		WriteFloat(stream,EntityY(Tinfo\h))
		WriteFloat(stream,EntityZ(Tinfo\h))
		WriteFloat(stream,EntityPitch(Tinfo\h))
		WriteFloat(stream,EntityYaw(Tinfo\h))
		WriteString(stream,Tinfo\vars$)
	Next
	
	bmpf_loadscreen(s$(18),92)
	If s_states=1 Then
		c=0
		For Tstate.Tstate=Each Tstate:c=c+1:Next							
		WriteInt(stream,c)
		For Tstate.Tstate=Each Tstate
			WriteByte(stream,Tstate\typ)
			WriteByte(stream,Tstate\parent_class)
			WriteInt(stream,Tstate\parent_id)
			WriteFloat(stream,Tstate\x#)
			WriteFloat(stream,Tstate\y#)
			WriteFloat(stream,Tstate\z#)
			WriteFloat(stream,Tstate\fx#)
			WriteFloat(stream,Tstate\fy#)
			WriteFloat(stream,Tstate\fz#)
			WriteInt(stream,Tstate\value)
			WriteFloat(stream,Tstate\value_f#)
			WriteString(stream,Tstate\value_s$)
		Next
	Else
		c=0
		For Tstate.Tstate=Each Tstate
			If Tstate\parent_class<>Cclass_unit Or Tstate\parent_id<>1 Then
				c=c+1
			EndIf
		Next
		WriteInt(stream,c)
		For Tstate.Tstate=Each Tstate
			If Tstate\parent_class<>Cclass_unit Or Tstate\parent_id<>1 Then
				WriteByte(stream,Tstate\typ)
				WriteByte(stream,Tstate\parent_class)
				WriteInt(stream,Tstate\parent_id)
				WriteFloat(stream,Tstate\x#)
				WriteFloat(stream,Tstate\y#)
				WriteFloat(stream,Tstate\z#)
				WriteFloat(stream,Tstate\fx#)
				WriteFloat(stream,Tstate\fy#)
				WriteFloat(stream,Tstate\fz#)
				WriteInt(stream,Tstate\value)
				WriteFloat(stream,Tstate\value_f#)
				WriteString(stream,Tstate\value_s$)
			EndIf
		Next		
	EndIf
	
	bmpf_loadscreen(s$(19),95)
	c=0
	save_map_extensions(mode$,s_items)
	For Tx.Tx=Each Tx
		If Tx\mode=1 Then
			If Tx\stuff$="temp" Then Delete Tx
		EndIf
	Next
	If s_skills+s_vars+s_diary+s_locks=4 Then
		For Tx.Tx=Each Tx:c=c+1:Next
		WriteInt(stream,c)
		For Tx.Tx=Each Tx
			WriteByte(stream,Tx\typ)
			WriteByte(stream,Tx\parent_class)
			WriteInt(stream,Tx\parent_id)
			WriteInt(stream,Tx\mode)
			If Tx\mode=0 Then
				WriteString(stream,"")
			Else
				WriteString(stream,Tx\key$)
			EndIf
			WriteString(stream,Tx\value$)
			WriteString(stream,Tx\stuff$)
			If Tx\mode=6 Then Delete Tx
		Next
	Else
		For Tx.Tx=Each Tx
			ds=1
			If s_skills=0 And Tx\mode=5 Then ds=0
			If s_vars=0 And Tx\mode=1 Then ds=0
			If s_diary=0 And Tx\mode=2 Then ds=0
			If s_locks=0 And Tx\mode=3 Then ds=0
			If ds=1 Then c=c+1
		Next
		WriteInt(stream,c)
		For Tx.Tx=Each Tx
			ds=1
			If s_skills=0 And Tx\mode=5 Then ds=0
			If s_vars=0 And Tx\mode=1 Then ds=0
			If s_diary=0 And Tx\mode=2 Then ds=0
			If s_locks=0 And Tx\mode=3 Then ds=0
			If ds=1 Then
				WriteByte(stream,Tx\typ)
				WriteByte(stream,Tx\parent_class)
				WriteInt(stream,Tx\parent_id)
				WriteInt(stream,Tx\mode)
				If Tx\mode=0 Then
					WriteString(stream,"")
				Else
					WriteString(stream,Tx\key$)
				EndIf
				WriteString(stream,Tx\value$)
				WriteString(stream,Tx\stuff$)
				If Tx\mode=6 Then Delete Tx
			EndIf
		Next		
	EndIf
	
	If mode$="sav" Then
		WriteFloat(stream,EntityPitch(cam))
		WriteFloat(stream,EntityYaw(cam))
	EndIf
	
	
	bmpf_loadscreen(s$(20),100)
	con_add("")
	WriteLine(stream,"")
	WriteLine(stream,"### EOF Map File")
	WriteLine(stream,"www.unrealsoftware.de")
	
	If mode$="map" Then
		For Tat.Tat=Each Tat
			If FileType(set_rootdir$+"mods\"+set_moddir$+"\"+Tat\path$)=1 Then
				atstream=ReadFile(set_rootdir$+"mods\"+set_moddir$+"\"+Tat\path$)
				If atstream<>0 Then
					WriteLine(stream,strinv$(Tat\path$))
					WriteInt(stream,FileSize(set_rootdir$+"mods\"+set_moddir$+"\"+Tat\path$))
					While Not Eof(atstream)
						byte=ReadByte(atstream)
						byte=255-byte
						WriteByte(stream,byte)
					Wend
					CloseFile(atstream)
					atstream=0
				EndIf
			EndIf
		Next
	EndIf
	
	Local expected=FilePos(stream)
	CloseFile(stream)
	Return FileSize(path$)=expected
End Function


Function save_map_image(mode$)
	Local pvimg=0
	Local ox#,oy#,oz#,o1#,o2#
	ox#=EntityX(cam)
	oy#=EntityY(cam)
	oz#=EntityZ(cam)
	o1#=EntityPitch(cam)
	o2#=EntityYaw(cam)
	If map_image<>0 Then FreeImage(map_image)
	map_image=CreateImage(set_scrx,set_scry)
	If mode$="map" Then
		For Tinfo.Tinfo=Each Tinfo
			If Tinfo\typ=48 Then
				If Tinfo\strings[0]<>"" Then
					pvimg=LoadImage(Tinfo\strings[0])
				EndIf
				PositionEntity cam,EntityX(Tinfo\h),EntityY(Tinfo\h),EntityZ(Tinfo\h)
				RotateEntity cam,EntityPitch(Tinfo\h),EntityYaw(Tinfo\h),0
				Exit
			EndIf
		Next
	EndIf
	If m_section=Csection_editor Then
		mb_reset()
		For Tinfo.Tinfo=Each Tinfo
			HideEntity Tinfo\h
		Next
	EndIf
	RenderWorld()
	GrabImage map_image,0,0
	ResizeImage map_image,96,72
	If pvimg<>0 Then
		If ImageWidth(pvimg)<>96 Or ImageHeight(pvimg)<>72 Then
			ResizeImage pvimg,96,72
		EndIf
		SetBuffer ImageBuffer(map_image)
		DrawImage pvimg,0,0
		SetBuffer BackBuffer()
		FreeImage pvimg
	EndIf
	PositionEntity cam,ox#,oy#,oz#
	RotateEntity cam,o1#,o2#,0
End Function


Function save_map_extensions(mode$="",s_items=1)
	For Tx.Tx=Each Tx
		If Tx\mode=6 Then Delete Tx
	Next
	If mode$<>"map"
		Tx.Tx=New Tx
		Tx\mode=6
		Tx\key$="g_drive"
		Tx\value$=g_drive
		Tx.Tx=New Tx
		Tx\mode=6
		Tx\key$="env_cweather"
		Tx\value$=env_cweather
	EndIf
	For Ttimer.Ttimer=Each Ttimer
		Tx.Tx=New Tx
		Tx\mode=6
		Tx\key$="t"
		Tx\parent_class=Ttimer\parent_class
		Tx\parent_id=Ttimer\parent_id
		start=gt-Ttimer\start
		If start<0 Then start=0
		Tx\value$=Ttimer\typ+","+Ttimer\duration+","+start+","+Ttimer\mode
		Tx\stuff$=Ttimer\scr$	
	Next
	If s_items=1 Then
		If con_unit(1) Then
			Tx.Tx=New Tx
			Tx\mode=6
			Tx\key$="w/a"
			Tx\value$=TCunit\player_weapon
			Tx\parent_class=TCunit\player_weapon
			Tx\parent_id=TCunit\player_ammo
		EndIf
	EndIf
	For Tfrap.Tfrap=Each Tfrap
		For c.Tfrap=Each Tfrap
			If Handle(Tfrap)<>Handle(c) Then
				If Tfrap\parent_class=c\parent_class Then
					If Tfrap\parent_id=c\parent_id Then
						Delete c
					EndIf
				EndIf
			EndIf
		Next
	Next
	For Tfrap.Tfrap=Each Tfrap
		Tx.Tx=New Tx
		Tx\mode=6
		Tx\key$="f"
		Tx\parent_class=Tfrap\parent_class
		Tx\parent_id=Tfrap\parent_id
		Tx\value$=Tfrap\y#+","+Tfrap\pitch#+","+Tfrap\roll#
	Next
	For Tup.Tup=Each Tup
		Tx.Tx=New Tx
		Tx\mode=6
		Tx\key$="u"
		Tx\parent_class=Tup\mode
		Tx\parent_id=Tup\info
		Tx\value$=Tup\unit
	Next
	For Tunit.Tunit=Each Tunit
		If Tunit\freeze=1 Then
			Tx.Tx=New Tx
			Tx\mode=6
			Tx\key$="i"
			Tx\parent_class=Cclass_unit
			Tx\parent_id=Tunit\id
		EndIf
	Next
	For t3d.t3d=Each t3d
		Tx.Tx=New Tx
		Tx\mode=6
		Tx\key$="t3d"
		Tx\value$=t3d\txt$
		Tx\stuff$=t3d\typ+","+t3d\offset+","+t3d\range
		Tx\parent_class=t3d\class
		Tx\parent_id=T3d\id
	Next
	Tx.Tx=New Tx
	Tx\mode=6
	Tx\key$="lasttime"
	Tx\stuff$=map_lday+","+map_lhour+","+map_lminute
End Function
