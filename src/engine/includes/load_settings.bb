;############################################ SETTINGS

; Use the same distances at startup, on Apply and after cancelling options.
Function settings_apply_viewrange()
	Select set_viewrange
		Case 0 set_viewfac#=0.5
		Case 1 set_viewfac#=1.0
		Case 2 set_viewfac#=1.5
		Case 3 set_viewfac#=2.5
		Case 4 set_viewfac#=4.0
		Default set_viewfac#=1.5
	End Select
End Function

;### Load Settings
Function load_settings()
	;Controls
	Dim in_keys(21)
	; Local builds opt in; release packages omit this development-only marker.
	If FileType(set_rootdir$+"core\development.flag")=1 Then in_keys(Ckey_devmenu)=210
	Local stream=ReadFile("sys\controls.cfg")
	If stream=0 Then
		;Failed? Set Defaults!
		set_msens#=0.2
		set_minvert=1
		set_msmooth=0
	Else
		;Load
		ReadLine(stream)
		For i=0 To 20
			in_keys(i)=ReadShort(stream)
		Next
		set_msens#=ReadFloat(stream)
		set_minvert=ReadByte(stream)-128
		set_msmooth=ReadByte(stream)
		; Append a tagged extension without changing the original control layout.
		If FileSize("sys\controls.cfg")-FilePos(stream)>=6 Then
			If ReadInt(stream)=$314B5644 Then in_keys(Ckey_devmenu)=ReadShort(stream)
		EndIf
		CloseFile(stream)
	EndIf
	If in_keys(Ckey_devmenu)>262 Then in_keys(Ckey_devmenu)=0
	DebugUiHotkey in_keys(Ckey_devmenu)
	;Scriptcontrols
	Dim game_scriptkey(20)
	stream=ReadFile("sys\scriptcontrols.cfg")
	If stream<>0 Then
		ReadLine(stream)
		For i=0 To 20
			game_scriptkey(i)=ReadShort(stream)
		Next
		CloseFile(stream)
	EndIf
	;Settings
	stream=ReadFile("sys\settings.cfg")
	If stream=0 Then
		;Failed? Set Defaults!
		set_scrx=800
		set_scry=600
		set_scrbit=32
		; Fresh installs use the highest quality offered by the graphics menu.
		set_viewrange=4
		set_terrain=2
		set_water=6
		set_sky=2
		set_effects=2
		set_grass=4
		set_2dfx=1
		set_lightfx=1
		set_windsway=1
		set_fog=1
		set_hwmultitex=1
		set_musicvolume#=0.5
		set_fxvolume#=0.5
		; Preserve legacy motion blur fields for settings-file compatibility only.
		set_mb=1
		set_mb_alpha#=0.35
	Else
		;Load
		ReadLine(stream)
		set_scrx=ReadLine(stream):set_scrx_c=set_scrx
		set_scry=ReadLine(stream):set_scry_c=set_scry
		set_scrbit=ReadLine(stream):set_scrbit_c=set_scrbit
		set_viewrange=ReadLine(stream)
		set_terrain=ReadLine(stream)
		set_water=ReadLine(stream)
		set_sky=ReadLine(stream)
		set_effects=ReadLine(stream)
		set_musicvolume#=ReadLine(stream)
		set_fxvolume#=ReadLine(stream)
		set_grass=ReadLine(stream)
		set_2dfx=ReadLine(stream)
		set_lightfx=ReadLine(stream)
		set_windsway=ReadLine(stream)
		set_name$=ReadLine(stream)
		set_serverport=ReadLine(stream)
		set_fog=ReadLine(stream)
		set_hwmultitex=ReadLine(stream)
		; Legacy fields are retained but never enable ordinary motion blur.
		set_mb=ReadLine(stream)
		set_mb_alpha#=ReadLine(stream)
		CloseFile(stream)
	EndIf
	
	; Keep the active graphics size during options load/save.
	If modern_hwnd<>0 Then set_scrx=GraphicsWidth():set_scry=GraphicsHeight():set_scrbit=32
	;Setup
	
	;Viewrange
	settings_apply_viewrange()
	
	;Discard event blur history after settings refresh.
	mb_reset()
	
End Function

load_settings()


;### Save Settings
Function save_settings()
	;Controls
	Local stream=WriteFile("sys\controls.cfg.tmp")
	If stream=0 Then Return False
	WriteLine(stream,"### "+set_moddir$+" Control Settings (Warning: Editing this File may cause Errors)")
	For i=0 To 20
		WriteShort(stream,in_keys(i))
	Next
	WriteFloat(stream,set_msens#)
	WriteByte(stream,set_minvert+128)
	WriteByte(stream,set_msmooth)
	WriteInt(stream,$314B5644)
	WriteShort(stream,in_keys(Ckey_devmenu))
	CloseFile(stream)
	If modern_commit_file("sys\controls.cfg","sys\controls.cfg.tmp")=0 Then Return False
	;Scriptcontrols
	If game_scriptkeys>0 Then
		stream=WriteFile("sys\scriptcontrols.cfg.tmp")
		If stream=0 Then Return False
		WriteLine(stream,"### "+set_moddir$+" Script Control Settings (Warning: Editing this File may cause Errors)")
		For i=0 To 20
			WriteShort(stream,game_scriptkey(i))
		Next
		CloseFile(stream)
	If modern_commit_file("sys\scriptcontrols.cfg","sys\scriptcontrols.cfg.tmp")=0 Then Return False
	EndIf
	;Settings
	stream=WriteFile("sys\settings.cfg.tmp")
	If stream=0 Then Return False
	WriteLine(stream,"### "+set_moddir$+" Settings (Warning: Editing this File may cause Errors)")
	WriteLine(stream,set_scrx_c)
	WriteLine(stream,set_scry_c)
	WriteLine(stream,set_scrbit_c)
	WriteLine(stream,set_viewrange)
	WriteLine(stream,set_terrain)
	WriteLine(stream,set_water)
	WriteLine(stream,set_sky)
	WriteLine(stream,set_effects)
	WriteLine(stream,set_musicvolume#)
	WriteLine(stream,set_fxvolume#)
	WriteLine(stream,set_grass)
	WriteLine(stream,set_2dfx)
	WriteLine(stream,set_lightfx)
	WriteLine(stream,set_windsway)
	set_name$=mp_checkname(set_name$,0)
	WriteLine(stream,set_name$)
	WriteLine(stream,set_serverport)
	WriteLine(stream,set_fog)
	WriteLine(stream,set_hwmultitex)
	;Keep the original file layout and legacy values.
	WriteLine(stream,set_mb)
	WriteLine(stream,set_mb_alpha#)
	CloseFile(stream)
	If modern_commit_file("sys\settings.cfg","sys\settings.cfg.tmp")=0 Then Return False
	
	; Keep the active graphics size during options load/save.
	If modern_hwnd<>0 Then set_scrx=GraphicsWidth():set_scry=GraphicsHeight():set_scrbit=32
	;Setup
	
	;Viewrange
	settings_apply_viewrange()
	
	;Discard event blur history after settings refresh.
	mb_reset()
	
	;Water
	e_environment_setup_water()
	
	;Grass
	grass_map()
	grass_heightmap()
	grass_x=-2147483648
	grass_y=-2147483648
	grass_spread()
	Return True
End Function
