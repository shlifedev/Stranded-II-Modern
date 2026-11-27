Function getinput(gametimer=1)
	
	modern_frame()
	dev_input()
	If dev_paused Then gametimer=0:modern_step#=0.:f#=0.
	Local capturemouse=0
	If in_console=0 And (m_section=Csection_game_sp Or m_section=Csection_game_mp) Then
		capturemouse=(m_menu=0) Or (m_menu=Cmenu_if_movie And seq_cpoint=4)
	EndIf
	If DebugUiOpen() Then capturemouse=0
	modern_mouse_capture(capturemouse)
	
	Local downcache
	For i=1 To 5
		in_mhit(i)=MouseHit(i)
		downcache=MouseDown(i)
		If in_mdown(i)=1 And downcache=0 Then
			in_mrelease(i)=1
			If i=1 Then
				if_drop()
			EndIf
		Else
			in_mrelease(i)=0
		EndIf
		in_mdown(i)=downcache
	Next
	in_mxo=in_mx
	in_myo=in_my
	in_mx=MouseX()
	in_my=MouseY()
	If in_mxo=in_mx And in_myo=in_my Then
		If in_mpt=-1 Then in_mpt=ms
	Else
		in_mpt=-1
	EndIf
	in_mxs#=-MouseXSpeed()
	in_mys#=MouseYSpeed()
	in_mzs#=MouseZSpeed()
	
	If m_section=Csection_editor Then
		If in_opt(0)>=1 And in_opt(0)<=4 Then
			If in_fraph<>0 Then
				If in_mdown(1)=0 Then
					in_frapdown=0
					If in_mrelease(1) Then
						MoveMouse in_mxo,in_myo
					EndIf
				Else
					If in_frapdown<>0 Then
						MoveMouse set_scrx/2,set_scry/2
						in_mx=in_mxo
						in_my=in_myo
					EndIf
				EndIf
			EndIf
		EndIf
	EndIf
	
	If in_mhit(1) Then in_mark=0
	
	If m_section=Csection_game_sp Or m_section=Csection_game_mp
		For i=0 To 20
			If in_keys(i)<255 Then
				in_keyhit(i)=KeyHit(in_keys(i))
				downcache=KeyDown(in_keys(i))
				If in_keydown(i)=1 And downcache=0 Then
					in_keyrelease(i)=1
				Else
					in_keyrelease(i)=0
				EndIf
				in_keydown(i)=downcache
			Else
				Select in_keys(i)
					Case 256 in_keyhit(i)=in_mhit(1):in_keydown(i)=in_mdown(1):in_keyrelease(i)=in_mrelease(1)
					Case 257 in_keyhit(i)=in_mhit(2):in_keydown(i)=in_mdown(2):in_keyrelease(i)=in_mrelease(2)
					Case 258 in_keyhit(i)=in_mhit(3):in_keydown(i)=in_mdown(3):in_keyrelease(i)=in_mrelease(3)
					Case 259 in_keyhit(i)=in_mhit(4):in_keydown(i)=in_mdown(4):in_keyrelease(i)=in_mrelease(4)
					Case 260 in_keyhit(i)=in_mhit(5):in_keydown(i)=in_mdown(5):in_keyrelease(i)=in_mrelease(5)
					Case 261
						in_keyhit(i)=0
						If in_mzs>0 Then in_keyhit(i)=1
					Case 262
						in_keyhit(i)=0
						If in_mzs<0 Then in_keyhit(i)=1
				End Select
			EndIf
			If game_scriptkeys>0 Then
				If game_scriptkey(i)<255 Then
					in_keyhit(i+21)=KeyHit(game_scriptkey(i))
					downcache=KeyDown(game_scriptkey(i))
					If in_keydown(i+21)=1 And downcache=0 Then
						in_keyrelease(i+21)=1
					Else
						in_keyrelease(i+21)=0
					EndIf
					in_keydown(i+21)=downcache
				Else
					Select game_scriptkey(i)
						Case 256 in_keyhit(i+21)=in_mhit(1):in_keydown(i+21)=in_mdown(1):in_keyrelease(i+21)=in_mrelease(1)
						Case 257 in_keyhit(i+21)=in_mhit(2):in_keydown(i+21)=in_mdown(2):in_keyrelease(i+21)=in_mrelease(2)
						Case 258 in_keyhit(i+21)=in_mhit(3):in_keydown(i+21)=in_mdown(3):in_keyrelease(i+21)=in_mrelease(3)
						Case 259 in_keyhit(i+21)=in_mhit(4):in_keydown(i+21)=in_mdown(4):in_keyrelease(i+21)=in_mrelease(4)
						Case 260 in_keyhit(i+21)=in_mhit(5):in_keydown(i+21)=in_mdown(5):in_keyrelease(i+21)=in_mrelease(5)
						Case 261
							in_keyhit(i+21)=0
							If in_mzs>0 Then in_keyhit(i+21)=1
						Case 262
							in_keyhit(i+21)=0
							If in_mzs<0 Then in_keyhit(i+21)=1
					End Select
				EndIf
			EndIf
		Next
	EndIf
	
	If in_keyhit(Ckey_jump) Then
		If m_menu=0 Then
			in_jumprehit=1
		Else
			in_jumprehit=0
		EndIf
	EndIf
	
	in_escape=KeyHit(1)
	
	in_pe=0
	
	If in_mdown(1) Then
		in_inputfocus=-1
	ElseIf in_mrelease(1) Then
		in_slidefocus=-1
		If in_sticktoslide=1 Then
			in_sticktoslide=0
			in_mrelease(1)=0
			MoveMouse in_sticktoslidex,in_sticktoslidey
			in_mx=in_sticktoslidex
			in_my=in_sticktoslidey
		EndIf
	EndIf
	If in_mhit(1) Then
		If in_sticktoslide=1 Then
			in_sticktoslide=0
			in_mrelease(1)=0
			MoveMouse in_sticktoslidex,in_sticktoslidey
			in_mx=in_sticktoslidex
			in_my=in_sticktoslidey
		EndIf
	EndIf
	If in_sticktoslide=1 Then
		in_mx=-500
	EndIf
	
	in_ttt$=""
	
	in_key=GetKey()
	
	in_cursorf=0
	If ms-in_cursorms>200 Then
		in_cursorms=ms
		in_cursor=Not in_cursor
	EndIf
	
	in_ca#=(in_ca#+5.*f#) Mod 360
	
	in_wa#=(in_wa#+3.*f#) Mod 360
	
	in_t50go=0
	If ms-in_t50>=50 Then in_t50=ms-((ms-in_t50) Mod 50):in_t50go=1
	in_t100go=0
	If ms-in_t100>=100 Then in_t100=ms-((ms-in_t100) Mod 100):in_t100go=1
	in_t500go=0
	If ms-in_t500>=500 Then in_t500=ms-((ms-in_t500) Mod 500):in_t500go=1
	in_t1000go=0
	in_t2000go=0
	in_t3000go=0
	in_t5000go=0
	in_t10000go=0
	If ms-in_t1000>=1000 Then
		in_t1000=ms-((ms-in_t1000) Mod 1000):in_t1000go=1
		in_t2000=in_t2000+1
		If in_t2000=2 Then in_t2000=0:in_t2000go=1
		in_t3000=in_t3000+1
		If in_t3000=3 Then in_t3000=0:in_t3000go=1
		in_t5000=in_t5000+1
		If in_t5000=5 Then in_t5000=0:in_t5000go=1
		in_t10000=in_t10000+1
		If in_t10000=10 Then in_t10000=0:in_t10000go=1
	EndIf
	
	If gametimer=1 Then
		If lastms<>0 Then
			modern_game_time()
		EndIf
	EndIf
	
	in_gt20go=0
	If gt-in_gt20>=20 Then in_gt20=gt-((gt-in_gt20) Mod 20):in_gt20go=1
	in_gt50go=0
	If gt-in_gt50>=50 Then in_gt50=gt-((gt-in_gt50) Mod 50):in_gt50go=1
	in_gt100go=0
	If gt-in_gt100>=100 Then in_gt100=gt-((gt-in_gt100) Mod 100):in_gt100go=1
	in_gt500go=0
	If gt-in_gt500>=500 Then in_gt500=gt-((gt-in_gt500) Mod 500):in_gt500go=1
	in_gt1000go=0
	If gt-in_gt1000>=1000 Then in_gt1000=gt-((gt-in_gt1000) Mod 1000):in_gt1000go=1
	in_gt5000go=0
	If gt-in_gt5000>=5000 Then in_gt5000=gt-((gt-in_gt5000) Mod 5000):in_gt5000go=1
	
	If ms-in_statefxt>100 Then
		in_statefxt=ms
		in_statefxf=in_statefxf+1
		If in_statefxf>=9 Then in_statefxf=1
	EndIf
	
	If DebugUiOpen() Then dev_clear_input()
	qol_frame()
End Function

Function endinput(tooltip=1,cursor=1,scripthelper=1)
	
	If blackframe>0 Then
		blackframe=blackframe-f#
		ClsColor 0,0,0
		Cls
	EndIf
	
	If tooltip=1 Then
		If in_mpt>-1 Then
			If ms-in_mpt>500 Then
				If in_ttt$<>"" Then
					Color 0,0,0
					Rect in_ttx,in_tty,bmpf_len(in_ttt$,Cbmpf_tiny)+2,18,1
					bmpf_txt(in_ttx+1,in_tty,in_ttt$,Cbmpf_tiny)
				EndIf
			EndIf
		EndIf
	EndIf
	
	If scripthelper=1 Then
		If m_section=Csection_editor Then
			If m_menu=Cmenu_ed_scripts Then
				If in_sh=1 Then					
					Color 100,60,10
					Rect in_sh_x,in_sh_y,bmpf_len(in_sh_txt$,Cbmpf_tiny)+4,18,1
					bmpf_txt(in_sh_x+3,in_sh_y,in_sh_txt$,Cbmpf_tiny)
				EndIf
			EndIf
		EndIf
	EndIf
	
	If in_dnd=1 Then
		If m_menu=Cmenu_if_items Or m_menu=Cmenu_if_exchange Then
			If Ditem_iconh(in_dnd_typ)<>0 Then
				If in_dnd_show=1 Then
					DrawImage Ditem_iconh(in_dnd_typ),in_mx-in_dnd_pivotx-1,in_my-in_dnd_pivoty-1
					If in_mx<215 Or in_mx>795 Or in_my>595 Then
						If in_dnd_src=0 Then
							DrawImage gfx_icons,in_mx+6,in_my-10,Cicon_x
						EndIf
					EndIf
				Else
					If Abs(in_dnd_x-in_mx)>2 Or Abs(in_dnd_y-in_my)>2 Then
						in_dnd_show=1
					EndIf
				EndIf
			EndIf
		Else
			in_dnd=0
		EndIf
	EndIf
	
	If cursor Then
		If in_cursorf>=0 Then DrawImage gfx_cursor(in_cursorf),in_mx,in_my	
	EndIf
		
	If KeyHit(60) Then fps_visible=Not fps_visible
	If fps_visible And set_debug=0 Then bmpf_txt_r(set_scrx-3,0,fps+" FPS",Cbmpf_tiny)

	debug()
	
	If ms-fpsms>999 Then
		fps=fpsf
		fpsf=0
		fpsms=ms
	EndIf
	fpsf=fpsf+1
	
End Function

Function inputhit(key)
	If KeyHit(key) Then Return 1
	For i=0 To 20
		If in_keys(i)=key Then
			If in_keyhit(i)=1 Then Return 1
		EndIf
		If game_scriptkeys>0 Then
			If game_scriptkey(i)=key Then
				If in_keyhit(i+21) Then Return 1
			EndIf
		EndIf
	Next
	Select key
		Case 1 Return in_escape
	End Select
	Return 0
End Function


Function flushinput(keys=1,mouse=1)
	If keys=1 Then
		FlushKeys()
		For i=0 To 20
			in_keyhit(i)=0
			in_keyhit(i+21)=0
			in_keydown(i)=0
			in_keyrelease(i)=0
			in_keydown(i+21)=0
			in_keyrelease(i+21)=0
		Next
		in_escape=0
	EndIf
	If mouse=1 Then
		FlushMouse()
		For i=1 To 5
			in_mhit(i)=0
			in_mdown(i)=0
			in_mrelease(i)=0
		Next
	EndIf
End Function
