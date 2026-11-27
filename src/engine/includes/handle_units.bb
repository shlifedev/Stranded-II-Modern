Function set_unit(id=-1,typ,x#,z#)
	TCunit.Tunit=Null

	If Len(Dunit_name$(typ))=0 Then Return -1

	If id=-1 Then
		unit_serial=unit_serial+1
		id=unit_serial
	EndIf
	
	Local y#=e_tery(x#,z#)+50.
	
	Tunit.Tunit=New Tunit
	Tunit\id=id
	Tunit\typ=typ
	
	Local ch
	If set_debug Then
		ch=CreateSphere()
		Tunit\h_pivot=0
		EntityAlpha ch,0.1
		ScaleEntity ch,Dunit_colxr#(typ),Dunit_colyr#(typ),Dunit_colxr#(typ)
		EntityBlend ch,3
	Else
		ch=CreatePivot()
		Tunit\h_pivot=1
	EndIf
	PositionEntity ch,x#,y#+Dunit_colyr#(typ),z#
	If Dunit_col(typ)>0 Then
		EntityType ch,Dunit_col(typ)
		EntityRadius ch,Dunit_colxr#(typ),Dunit_colyr#(typ)	
	EndIf
	Tunit\h=ch
	
	Local h=CopyEntity(Dunit_modelh(typ))
	PositionEntity h,x#,y#,z#
	Tunit\mh=h
	
	If m_section=Csection_editor
		Animate h,2,Dunit_ani_move(typ,0),Dunit_ani_move(typ,1)
	EndIf
	
	ScaleEntity h,Dunit_size#(typ,0),Dunit_size#(typ,1),Dunit_size#(typ,2)
	EntityColor h,Dunit_color(typ,0),Dunit_color(typ,1),Dunit_color(typ,2)
	If Dunit_fx(typ)<>0 Then EntityFX h,Dunit_fx(typ)
	If Dunit_autofade(typ)<>0 Then EntityAutoFade h,Float(Dunit_autofade(typ))*set_viewfac#,(Float(Dunit_autofade(typ))*set_viewfac#)+300
	EntityAlpha h,Dunit_alpha#(typ)
	If Dunit_shininess#(typ)>0.0 EntityShininess h,Dunit_shininess#(typ)
	
	If Dunit_behaviour(typ)>499 Then
		If Dunit_behaviour(typ)<>502 Then
			vh=CopyEntity(Dunit_modelh(typ))
			ScaleEntity vh,Dunit_size#(typ,0),Dunit_size#(typ,1),Dunit_size#(typ,2)
			If set_debug=1 Then
				EntityAlpha vh,0.5
			Else
				EntityAlpha vh,0.
			EndIf
			EntityType vh,Cworld_col
			Tunit\vh=vh
		EndIf
	EndIf
	
	Tunit\health#=Dunit_health#(typ)
	Tunit\health_max#=Dunit_health#(typ)
	
	Tunit\ai_cx#=x#
	Tunit\ai_cz#=z#
	Tunit\ai_ch=CreatePivot()
	
	EntityParent h,ch
	
	Select Dunit_align(typ)
		Case 0 TranslateEntity ch,0,-50.,0
		Case 1 PositionEntity Tunit\h,x#,1+Dunit_colyr#(typ),z#
	End Select
	
	EntityPickMode Tunit\mh,2,1
		
	If tmp_loading=0 Then
		set_parsecache(Cclass_unit,Tunit\id,"create")
		If Instr(Dunit_scriptk$(Tunit\typ),"create") Then
			parse_task(Cclass_unit,Tunit\id,"create","",Dunit_script(Tunit\typ))
		EndIf
	EndIf
	
	If tmp_loading=0 Then defvar_oncreate(Cclass_unit,id,typ)	
	
	TCunit.Tunit=Tunit
End Function


Function get_unit(searchhandle,bycollisionpivot=0)
	If bycollisionpivot=0 Then
		For Tunit.Tunit=Each Tunit
			If Tunit\mh=searchhandle Then
				TCunit.Tunit=Tunit
				Return Tunit\id
			EndIf
		Next
	Else
		For Tunit.Tunit=Each Tunit
			If Tunit\h=searchhandle Then
				TCunit.Tunit=Tunit
				Return Tunit\id
			ElseIf Tunit\vh=searchhandle Then
				TCunit.Tunit=Tunit
				Return Tunit\id
			EndIf
		Next		
	EndIf
	Return -1
End Function


Function get_unitcp(searchhandle)
	For Tunit.Tunit=Each Tunit
		If Tunit\h=searchhandle Then
			TCunit.Tunit=Tunit
			Return Tunit\id
		EndIf
	Next
	Return -1
End Function


Function con_unit(id)
	For Tunit.Tunit=Each Tunit
		If Tunit\id=id Then TCunit.Tunit=Tunit:Return 1
	Next
	Return 0
End Function


Function draw_unit()
	Tunit.Tunit=TCunit
	If EntityInView(Tunit\h,cam) Then
		CameraProject(cam,EntityX(Tunit\h),EntityY(Tunit\h),EntityZ(Tunit\h))
		bmpf_txt_c(ProjectedX(),ProjectedY()-60,"#"+Tunit\id+" "+Dunit_name$(Tunit\typ),Cbmpf_tiny)
		bmpf_txt_c(ProjectedX(),ProjectedY()-30,Tunit\health+"|"+Tunit\health_max,Cbmpf_tiny)
		If KeyHit(57) Then
			found=0
			For Tfrap.Tfrap=Each Tfrap
				If Tfrap\parent_class=Cclass_unit Then
					If Tfrap\parent_id=Tunit\id Then
						found=1
						Exit
					EndIf
				EndIf
			Next
			If found=0 Then
				Tfrap.Tfrap=New Tfrap
				Tfrap\parent_class=Cclass_unit
				Tfrap\parent_id=Tunit\id
				Tfrap\y#=EntityY(Tunit\h)
				Tfrap\pitch#=0.
				Tfrap\roll#=0.
				in_fraph=Tunit\h
			Else
				in_fraph=Tunit\h
			EndIf
		EndIf
		in_edoverinfo$=ss$(se$(197),Dunit_name$(Tunit\typ))
		If KeyHit(211) Then free_unit(Tunit\id)
	EndIf
End Function

Function draw_units_debug()
	For Tunit.Tunit=Each Tunit
		If EntityInView(Tunit\h,cam) Then
			If EntityDistance(Tunit\h,cam)<500 Then
				CameraProject(cam,EntityX(Tunit\h),EntityY(Tunit\h),EntityZ(Tunit\h))
				x=ProjectedX()
				y=ProjectedY()	
				bmpf_txt_c(x,y-45,"#"+Tunit\id+" "+Dunit_name$(Tunit\typ),Cbmpf_tiny)
				bmpf_txt_c(x,y-35,ai_modetxt$(Tunit\ai_mode),Cbmpf_tiny)
			EndIf
		EndIf
	Next
End Function

Function use_unit(id)
	For Tunit.Tunit=Each Tunit
		If Tunit\id=id Then
		
			p_skipevent=0
			set_parsecache(Cclass_unit,Tunit\id,"use")
			If Instr(Dunit_scriptk$(Tunit\typ),"use") Then
				parse_task(Cclass_unit,Tunit\id,"use","",Dunit_script(Tunit\typ))
			EndIf
			parse_sel(Cclass_unit,Tunit\id,"use")
			
			If p_skipevent=0 Then
				If Handle(Tunit)<>0 Then
					If Tunit\health#<=0. Then
						if_exchange(Cclass_unit,id)
					EndIf
				EndIf
			EndIf
			
			Return 1
		EndIf
	Next
	Return 0
End Function


Function free_unit(id,update=1,childs=1)
	For Tunit.Tunit=Each Tunit
		If Tunit\id=id Then
			If childs Then free_childs(Cclass_unit,id)
			If Tunit\chan<>0 Then StopChannel Tunit\chan
			If Tunit\h<>0 Then FreeEntity Tunit\h
			If Tunit\vh<>0 Then FreeEntity Tunit\vh
			Delete Tunit
			Return 1
		EndIf
	Next
	Return 0
End Function


Function serialpeak_unit()
	Local peak=99
	For Tunit.Tunit=Each Tunit
		If Tunit\id>peak Then peak=Tunit\id
	Next
	unit_serial=peak
End Function


Function hurt_unit(damage#,kill=0,pickcords=0)
	If get_state(Cstate_invulnerability,Cclass_unit,TCunit\id)=0 Then
		TCunit\health#=TCunit\health#-damage#
		If TCunit\health_max#<=0 Then
			TCunit\health#=0
		EndIf
		If TCunit\id=g_player Then
			If TCunit\health#>0 Then
				If Rand(5)<>2 Then
					sfx_emit(sfx_humanhit(Rand(0,4)),TCunit\h)
				EndIf
			EndIf
		EndIf
	EndIf
	If pickcords=1 Then
		material_fx(in_px#,in_py#,in_pz#,Dunit_mat(TCunit\typ))
	ElseIf pickcords=0
		material_fx(EntityX(TCunit\h),EntityY(TCunit\h)+Dunit_eyes#(TCunit\typ),EntityZ(TCunit\h),Dunit_mat(TCunit\typ))
	EndIf
	If TCunit\id=g_player Then
		p_add(0,0,0,Cp_flash,0.06,0.75)
		EntityColor TCp\h,255,0,0
	EndIf
	If TCunit\health#<=0 Or kill=1 Then
		kill_unit()
		Return 2
	Else
		If TCunit\health_max#>0 Then
			If TCunit\health#>0 Then
				Local action=0
				Select Dunit_behaviour(TCunit\typ)
					Case 1 action=2
					Case 2 action=1
					Case 7 action=2
					Case 200 action=2
					Case 201 action=2
					Case 300 action=2
					Case 302 action=1
					Case 303 action=2
					Case 403 action=2
					Case 404 action=2
					Default action=0
				End Select
				If action<>0 Then
					If action=1 Then
						If TCunit\ai_mode<>ai_hunt And TCunit\ai_mode<>ai_attack Then
							ai_mode(ai_hunt,15000)
						EndIf
					ElseIf action=2 Then
						ai_mode(ai_flee,10000)
						TCunit\ai_target_class=Cclass_unit
						TCunit\ai_target_id=g_player
					EndIf
				EndIf
			EndIf
		EndIf
	EndIf
	Return 1
End Function


Function health_unit(change#)
	TCunit\health#=TCunit\health#+change#
	If TCunit\health#>TCunit\health_max# Then TCunit\health#=TCunit\health_max#
	If TCunit\health#<=0
		kill_unit()
	EndIf
End Function


Function kill_unit()
	Tkill.Tunit=TCunit
	TCunit\health#=0
	If TCunit\health_max#>0. Then
		Tkill\health_max#=0
		Tkill\health#=0
		spawn_unit_items(TCunit\id,TCunit\typ)
		set_parsecache(Cclass_unit,TCunit\id,"kill")
		If Instr(Dunit_scriptk$(TCunit\typ),"kill") Then
			parse_task(Cclass_unit,TCunit\id,"kill","",Dunit_script(TCunit\typ))
		EndIf
		tmp_killclass=Cclass_unit
		tmp_killid=Tkill\id
		Local tmp_killpivot=CreatePivot()
		If Tkill\h<>0 Then
			PositionEntity tmp_killpivot,EntityX(Tkill\h),EntityY(Tkill\h),EntityZ(Tkill\h)
			RotateEntity tmp_killpivot,EntityPitch(Tkill\h),EntityYaw(Tkill\h),EntityRoll(Tkill\h)
		EndIf
		tmp_killmodel=tmp_killpivot
		parse_kill_add(tmp_killclass,tmp_killid,tmp_killmodel)
		If Handle(Tkill)<>0 Then
			Tkill\freeze=0
			If Tkill\ani<>Dunit_ani_die(Tkill\typ,1) Then
				Tkill\ani=Dunit_ani_die(Tkill\typ,1)
				Animate Tkill\mh,3,Dunit_ani_die(Tkill\typ,0),Dunit_ani_die(Tkill\typ,1)
				play_unitsound(Tkill\typ,snd_die,Tkill\h)
			EndIf
			Tkill\health_max#=0
		EndIf
		tmp_kill=1
	EndIf
	If Handle(Tkill)<>0 Then
		EntityType Tkill\h,0
		EntityPickMode Tkill\mh,2,1
		For Tstate.Tstate=Each Tstate
			If Tstate\parent_class=Cclass_unit Then
				If Tstate\parent_id=Tkill\id Then
					free_state(Tstate\typ,Cclass_unit,Tkill\id)
				EndIf
			EndIf
		Next
		up_free(Tkill\id)
		If Tkill\id=g_player Then
			g_player_dead=1
		EndIf
		
	EndIf
End Function
	

Function unit_values()
	If TCunit\health_max#<0 Then TCunit\health_max#=0.
	If TCunit\health#<=0 Then TCunit\health#=0
	If TCunit\health#>TCunit\health_max# Then TCunit\health#=TCunit\health_max#
	If TCunit\hunger#>Dunit_store#(TCunit\typ) Then TCunit\hunger#=Dunit_store#(TCunit\typ)
	If TCunit\hunger#<0 Then TCunit\hunger#=0
	If TCunit\thirst#>Dunit_store#(TCunit\typ) Then TCunit\thirst#=Dunit_store#(TCunit\typ)
	If TCunit\thirst#<0 Then TCunit\thirst#=0
	If TCunit\exhaustion#>Dunit_store#(TCunit\typ) Then TCunit\exhaustion#=Dunit_store#(TCunit\typ)
	If TCunit\exhaustion#<0 Then TCunit\exhaustion#=0
End Function


Function spawn_unit_items(unitid,unittyp)
	For Tfind.Tfind=Each Tfind
		If Tfind\class=Cclass_unit Then
			If Tfind\parent=unittyp Then
				id=set_item(-1,Tfind\typ,0,0,0,Tfind\max)
				If id<>0 Then
					store_item(id,Cclass_unit,unitid)
				EndIf
			EndIf
		EndIf
	Next
End Function


Function ani_unit(mode,speed#,seq,transition#=1.,override=0)
	If TCunit\ani<>seq Or override=1 Then
		TCunit\ani=seq
		Animate TCunit\mh,mode,speed#,seq,transition#
	EndIf
End Function


Function pos_unit(unit.Tunit,x#,y#,z#)
	If unit=g_cplayer Then col_player_reset()
	HideEntity unit\h
	PositionEntity unit\h,x#,y#,z#								
	ShowEntity unit\h
	If unit\vh<>0 Then
		HideEntity unit\vh
		PositionEntity unit\vh,x#,y#,z#
		ShowEntity unit\vh
	EndIf
End Function


Function frap_unit()
	Tunit.Tunit=TCunit
	
	Local h=Tunit\h
	Local size#=20.
	Local piv=CreatePivot()
	Local rx#=EntityX(h)
	Local ry#=EntityY(h)
	Local rz#=EntityZ(h)
	CameraProject(cam,rx#,ry#,rz#)
	Local rx2d=ProjectedX()
	Local ry2d=ProjectedY()
	Local px
	Local py
	RotateEntity piv,EntityPitch(h,1),EntityYaw(h,1),EntityRoll(h,1)
	
	Local s#=0.25
	Local shift=KeyDown(42)+KeyDown(54)
	
	If EntityInView(Tunit\h,cam) Then
		
		If rx2d>(in_editor_sidebar*210) Then
			If ry2d>0 Then
				
				Color 255,0,0
				PositionEntity piv,rx#,ry#,rz#
				MoveEntity piv,size#,0,0
				CameraProject(cam,EntityX(piv),EntityY(piv),EntityZ(piv))
				px=ProjectedX()
				py=ProjectedY()
				If px>210 Then
					If py>0 Then
				
						Line rx2d,ry2d,px,py
						DrawImage gfx_ex,px,py
						
						If in_frapdown=0 Then
							If in_mx>px-5 Then
								If in_my>py-5 Then
									If in_mx<px+5 Then
										If in_my<py+5 Then
											DrawImage gfx_esel,px,py
											If in_mdown(1) Then
												in_mx=px
												in_mxo=in_mx
												in_my=py
												in_myo=in_my
												in_frapdown=1											
												in_cursorf=2
												MoveEntity h,Float(Float(in_mxs#+in_mys#)*s#),0,0
											EndIf
										EndIf
									EndIf
								EndIf
							EndIf
						ElseIf in_frapdown=1 Then
							DrawImage gfx_esel,px,py
							If in_mdown(1) Then
								in_mx=px
								in_mxo=in_mx
								in_my=py
								in_myo=in_my
								in_frapdown=1
								in_cursorf=2
								MoveEntity h,Float(Float(in_mxs#+in_mys#)*s#),0,0
							EndIf
						EndIf
						
					EndIf
				EndIf
					
				Color 0,255,0
				PositionEntity piv,rx#,ry#,rz#
				MoveEntity piv,0,size#,0
				CameraProject(cam,EntityX(piv),EntityY(piv),EntityZ(piv))
				px=ProjectedX()
				py=ProjectedY()
				If px>(in_editor_sidebar*210) Then
					If py>0 Then
				
						Line rx2d,ry2d,px,py
						DrawImage gfx_ey,px,py
						
						If in_frapdown=0 Then
							If in_mx>px-5 Then
								If in_my>py-5 Then
									If in_mx<px+5 Then
										If in_my<py+5 Then
											DrawImage gfx_esel,px,py
											If in_mdown(1) Then
												in_mx=px
												in_mxo=in_mx
												in_my=py
												in_myo=in_my
												in_frapdown=2
												in_cursorf=2
												MoveEntity h,0,Float(Float(in_mxs#+in_mys#)*s#),0
											EndIf
										EndIf
									EndIf
								EndIf
							EndIf
						ElseIf in_frapdown=2 Then
							DrawImage gfx_esel,px,py
							If in_mdown(1) Then
								in_mx=px
								in_mxo=in_mx
								in_my=py
								in_myo=in_my
								in_frapdown=2
								in_cursorf=2
								MoveEntity h,0,Float(Float(in_mxs#+in_mys#)*s#),0
							EndIf
						EndIf
						
					EndIf
				EndIf
				
				Color 0,0,255
				PositionEntity piv,rx#,ry#,rz#
				MoveEntity piv,0,0,size#
				CameraProject(cam,EntityX(piv),EntityY(piv),EntityZ(piv))
				px=ProjectedX()
				py=ProjectedY()
				If px>(in_editor_sidebar*210) Then
					If py>0 Then
						
						Line rx2d,ry2d,px,py
						DrawImage gfx_ez,px,py
						
						If in_frapdown=0 Then
							If in_mx>px-5 Then
								If in_my>py-5 Then
									If in_mx<px+5 Then
										If in_my<py+5 Then
											DrawImage gfx_esel,px,py
											If in_mdown(1) Then
												in_mx=px
												in_mxo=in_mx
												in_my=py
												in_myo=in_my
												in_frapdown=3
												in_cursorf=2
												MoveEntity h,0,0,Float(Float(in_mxs#+in_mys#)*s#)
											EndIf
										EndIf
									EndIf
								EndIf
							EndIf
						ElseIf in_frapdown=3 Then
							DrawImage gfx_esel,px,py
							If in_mdown(1) Then
								in_mx=px
								in_mxo=in_mx
								in_my=py
								in_myo=in_my
								in_frapdown=3
								in_cursorf=2
								MoveEntity h,0,0,Float(Float(in_mxs#+in_mys#)*s#)
							EndIf	
						EndIf
						
					EndIf
				EndIf
					
				px=rx2d
				py=ry2d
				DrawImage gfx_ey,px,py
				
				If in_frapdown=0 Then
					If in_mx>px-5 Then
						If in_my>py-5 Then
							If in_mx<px+5 Then
								If in_my<py+5 Then
									DrawImage gfx_esel,px,py
									If in_mdown(1) Then
										in_mx=px
										in_mxo=in_mx
										in_my=py
										in_myo=in_my
										in_frapdown=4
										in_cursorf=2
										TranslateEntity h,0,-in_mys#*s#,0
										mhp=CreatePivot()
										PositionEntity mhp,EntityX(h),0,EntityZ(h)
										RotateEntity mhp,0,EntityYaw(cam),0
										MoveEntity mhp,-in_mxs#*s#,0,in_mzs#*s#
										PositionEntity h,EntityX(mhp),EntityY(h),EntityZ(mhp)
										FreeEntity mhp
									EndIf
								EndIf
							EndIf
						EndIf
					EndIf
				ElseIf in_frapdown=4 Then
					DrawImage gfx_esel,px,py
					If in_mdown(1) Then
						in_mx=px
						in_mxo=in_mx
						in_my=py
						in_myo=in_my
						in_frapdown=4
						in_cursorf=2
						TranslateEntity h,0,-in_mys#*s#,0
						mhp=CreatePivot()
						PositionEntity mhp,EntityX(h),0,EntityZ(h)
						RotateEntity mhp,0,EntityYaw(cam),0
						MoveEntity mhp,-in_mxs#*s#,0,in_mzs#*s#
						PositionEntity h,EntityX(mhp),EntityY(h),EntityZ(mhp)
						FreeEntity mhp
					EndIf
				EndIf
				
				If in_frapdown=0 Then
					CameraProject(cam,EntityX(Tunit\h),EntityY(Tunit\h),EntityZ(Tunit\h))
					bmpf_txt_c(ProjectedX(),ProjectedY()-30,"#"+Tunit\id+" "+Dunit_name$(Tunit\typ),Cbmpf_tiny)
				EndIf
				
				in_edoverinfo$=ss$(se$(197),Dunit_name$(Tunit\typ))
				
			EndIf
		EndIf			
	EndIf
	
	For Tfrap.Tfrap=Each Tfrap
		If Tfrap\parent_class=Cclass_unit Then
			If Tfrap\parent_id=Tunit\id Then
				Tfrap\y#=EntityY(Tunit\h)
				Exit
			EndIf
		EndIf
	Next
				
	If KeyHit(211) Then free_unit(Tunit\id):in_fraph=0
		
	If KeyHit(57) Then in_fraph=0
		
End Function


Function freeze_unit(unit.Tunit,mode=1)
	If mode=1 Then
		unit\freeze=1
		unit\ani=0
		Animate unit\mh,0
	Else
		unit\freeze=0
	EndIf
End Function


Function unit_speed#()
	Local speed#=Dunit_speed#(TCunit\typ)
	If TCunit\states=1 Then
		For Tstate.Tstate=Each Tstate
			If Tstate\typ<=Cstate_count Then
				If Tstate\parent_class=Cclass_unit Then
					If Tstate\parent_id=TCunit\id Then
						If Dstate_a(Tstate\typ) Then
							Select Tstate\typ
								Case Cstate_frostbite speed#=speed#*0.5
								Case Cstate_fracture speed#=speed#*0.5
								Case Cstate_electroshock speed#=speed#*0.5
								Case Cstate_bloodrush speed#=speed#*Tstate\value_f#
								Case Cstate_speedmod speed#=Tstate\value_f# 
							End Select
						EndIf
					EndIf
				EndIf
			EndIf
		Next
	EndIf
	Return speed#
End Function


Function unit_loadani(typ,f1,f2)
	For Tsani.Tsani=Each Tsani
		If Tsani\typ=typ
			If Tsani\f1=f1 Then
				If Tsani\f2=f2 Then
					Return 1
				EndIf
			EndIf
		EndIf
	Next
	If Dunit_modelh(typ)<>0 Then
		Tsani.Tsani=New Tsani
		Tsani\typ=typ
		Tsani\f1=f1
		Tsani\f2=f2
		Tsani\h=ExtractAnimSeq(Dunit_modelh(typ),f1,f2)
		If Tsani\h=0 Then
			parse_error("Failed to load animation")
			Return 0
		Else
			Return 1
		EndIf
	Else
		parse_error("Unit type '"+typ+"' does not exist - unable to load animation")
		Return 0
	EndIf
End Function

Function unit_scriptani(id,f1,f2,speed#,mode=3)
	con_add("animate unit "+id+" "+f1+" - "+f2+" speed "+speed#+" mode "+mode)
	For Tunit.Tunit=Each Tunit
		If Tunit\id=id Then
			For Tsani.Tsani=Each Tsani
				If Tsani\typ=Tunit\typ
					If Tsani\f1=f1 Then
						If Tsani\f2=f2 Then
							If Tsani\h<>0 Then
								Animate Tunit\mh,0
								Tunit\ani=Tsani\h
								Animate Tunit\mh,mode,speed#,Tsani\h,1.
								Tunit\ai_mode=ai_sani
								Tunit\ai_timer=gt+10000
								con_add("existing sani")
								Return 1
							EndIf
						EndIf
					EndIf
				EndIf
			Next
			Tsani.Tsani=New Tsani
			Tsani\typ=Tunit\typ
			Tsani\f1=f1
			Tsani\f2=f2
			Tsani\h=ExtractAnimSeq(Dunit_modelh(Tunit\typ),f1,f2)
			If Tsani\h<>0 Then
				Animate Tunit\mh,0
				Tunit\ani=Tsani\h
				Animate Tunit\mh,mode,speed#,Tsani\h,1.
				Tunit\ai_mode=ai_sani
				Tunit\ai_timer=gt+10000
				con_add("new sani")
				Return 1
			EndIf
		EndIf
	Next
End Function


Function checkattackline()
	If Dunit_eyes(TCunit\typ)=0 Then
		Return 1
	Else
		If g_cplayer<>Null Then
			Local p1=CreateCube()
			PositionEntity p1,EntityX(TCunit\h),EntityY(TCunit\h)-Dunit_eyes(TCunit\typ),EntityZ(TCunit\h)
			EntityPickMode p1,2,1
			EntityPickMode TCunit\mh,0
			v=EntityVisible(p1,g_cplayer\h)
			EntityPickMode TCunit\mh,2,1
			FreeEntity p1
			Return v
		Else
			Return 0
		EndIf
	EndIf
End Function