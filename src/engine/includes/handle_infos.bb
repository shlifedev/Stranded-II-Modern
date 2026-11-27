Function set_info(id=-1,typ,x#,y#,z#)
	If Len(Dinfo_name$(typ))=0 Then Return -1
	
	If id=-1 Then
		info_serial=info_serial+1
		id=info_serial
	EndIf

	Tinfo.Tinfo=New Tinfo
	Tinfo\id=id
	Tinfo\typ=typ	
	
	If m_section=Csection_editor Then
		Tinfo\h=CreateCube()
		ScaleEntity Tinfo\h,5,5,5
		EntityBlend Tinfo\h,3
		EntityColor Tinfo\h,0,255,0
		EntityAutoFade Tinfo\h,5000,5500
		EntityPickMode Tinfo\h,2,1
	Else
		Tinfo\h=CreatePivot()
		HideEntity Tinfo\h
	EndIf
	
	If typ=5 Then
		Tinfo\strings[0]=""
		Tinfo\floats[0]=1.
		Tinfo\floats[1]=1.
		Tinfo\ints[0]=255
		Tinfo\ints[1]=255
		Tinfo\ints[2]=255
		Tinfo\floats[2]=1.
		Tinfo\strings[1]=0
		Tinfo\strings[2]=0
	EndIf
		
	PositionEntity Tinfo\h,x#,y#,z#
	
	If tmp_loading=0 Then defvar_oncreate(Cclass_info,id,typ)
	
	TCinfo.Tinfo=Tinfo
End Function


Function get_info(searchhandle)
	For Tinfo.Tinfo=Each Tinfo
		If Tinfo\h=searchhandle Then 
			TCinfo.Tinfo=Tinfo
			Return Tinfo\id
		EndIf
	Next
	Return -1
End Function


Function con_info(id)
	For Tinfo.Tinfo=Each Tinfo
		If Tinfo\id=id Then TCinfo.Tinfo=Tinfo:Return 1
	Next
	Return 0
End Function


Function free_info(id)
	For Tinfo.Tinfo=Each Tinfo	
		If Tinfo\id=id Then
			free_childs(Cclass_info,id)
			If Tinfo\h<>0 Then FreeEntity Tinfo\h
			If Tinfo\typ=47
				If Tinfo\ints[0]<>0 Then FreeSound Tinfo\ints[0]
				If Tinfo\ints[1]<>0 Then
					StopChannel Tinfo\ints[1]
				EndIf
			EndIf
			Delete Tinfo
			Return 1
		EndIf
	Next
	Return 0
End Function


Function draw_info()
	For Tinfo.Tinfo=Each Tinfo
		If EntityInView(Tinfo\h,cam) Then
			dist=EntityDistance(Tinfo\h,cam)
			If dist<1500 Then
				CameraProject(cam,EntityX(Tinfo\h),EntityY(Tinfo\h),EntityZ(Tinfo\h))
				DrawImage gfx_icons,ProjectedX()-16,ProjectedY()-16,Dinfo_frame(Tinfo\typ)
				If in_opt(0)=4 Then
					If dist<1000 Then
						bmpf_txt_c(ProjectedX(),ProjectedY()-30,"#"+Tinfo\id+" "+Dinfo_name$(Tinfo\typ),Cbmpf_tiny)
						Select Tinfo\typ
							Case 10
								editor_range(EntityX(Tinfo\h),EntityY(Tinfo\h),EntityZ(Tinfo\h),Tinfo\floats#[0])
							Case 36
								If Tinfo\ints[1]=1 Or in_cursor=1
									DrawImage gfx_arrows,ProjectedX(),ProjectedY()+18,Tinfo\ints[0]
								EndIf
							Case 41,42,43,44,45,46,47
								editor_range(EntityX(Tinfo\h),EntityY(Tinfo\h),EntityZ(Tinfo\h),Tinfo\floats#[0])
						End Select
					EndIf
				EndIf
			EndIf
		EndIf
	Next
End Function


Function update_info()
	Local c,t
	Select TCinfo\typ
		Case 10
			If Int(TCinfo\floats[1])=1 Then
				c=ha_inrange(TCinfo\h,TCinfo\floats#[0],TCinfo\ints[0],TCinfo\ints[1])
				t=0
				Select Int(TCinfo\floats[2])
					Case 0 If c=TCinfo\ints[2] Then t=1
					Case 1 If c>TCinfo\ints[2] Then t=1
					Case 2 If c<TCinfo\ints[2] Then t=1
					Case 3 If c>=TCinfo\ints[2] Then t=1
					Case 4 If c<=TCinfo\ints[2] Then t=1
					Case 5 If c<>TCinfo\ints[2] Then t=1
				End Select
				If t=1 Then
					set_parsecache(Cclass_info,TCinfo\id,"trigger","triggered")
				EndIf
			EndIf
		Case 11
			If Int(TCinfo\floats[1])=1 Then
				Select TCinfo\ints[0]
					Case 0
						TCinfo\timer=TCinfo\timer+1
						If TCinfo\timer>=TCinfo\ints[1] Then
							TCinfo\timer=0
							set_parsecache(Cclass_info,TCinfo\id,"trigger","triggered (every x seconds)")
						EndIf
					Case 1
						If map_timeupdate Then
							c=(map_day*24*60)+(map_hour*60)+map_minute
							l=(map_lday*24*60)+(map_lhour*60)+map_lminute
							TCinfo\timer=TCinfo\timer+(c-l)
							If TCinfo\timer>=TCinfo\ints[1] Then
								TCinfo\timer=0
								set_parsecache(Cclass_info,TCinfo\id,"trigger","triggered (every x game minutes)")
							EndIf
						EndIf
					Case 2
						If map_timeupdate Then
							If intime(TCinfo\ints[1],TCinfo\ints[2],Int(TCinfo\floats[0])) Then
								TCinfo\floats[1]=0
								info_vts()
								set_parsecache(Cclass_info,TCinfo\id,"trigger","triggered (at defined time)")
							EndIf
						EndIf
					Case 3
						If map_timeupdate Then
							If indailytime(TCinfo\ints[1],TCinfo\ints[2]) Then
								set_parsecache(Cclass_info,TCinfo\id,"trigger","triggered (daily at defined time)")
							EndIf
						EndIf
				End Select
			EndIf
		Case 12
			If Int(TCinfo\floats[1])=1 Then
				If TCinfo\ints[0]=0 Then
					c=countstored_items(Cclass_unit,g_player,TCinfo\ints[2])
				Else
					c=countstored_items(TCinfo\ints[0],TCinfo\ints[1],TCinfo\ints[2])
				EndIf
				t=0
				Select Int(TCinfo\floats[2])
					Case 0 If c=Int(TCinfo\floats[0]) Then t=1
					Case 1 If c>Int(TCinfo\floats[0]) Then t=1
					Case 2 If c<Int(TCinfo\floats[0]) Then t=1
					Case 3 If c>Int(TCinfo\floats[0]) Then t=1
					Case 4 If c<Int(TCinfo\floats[0]) Then t=1
					Case 5 If c<>Int(TCinfo\floats[0]) Then t=1
				End Select
				If t=1 Then
					set_parsecache(Cclass_info,TCinfo\id,"trigger","triggered")
				EndIf
			EndIf
		Case 43
			If (dist#(EntityX(TCinfo\h),EntityZ(TCinfo\h),EntityX(cam),EntityZ(cam))-TCinfo\floats[0])<500 Then
				dir=Rand(360)
				dist#=Rnd(TCinfo\floats[0])
				nx#=EntityX(TCinfo\h)+(Sin(dir)*dist#)
				nz#=EntityZ(TCinfo\h)-(Cos(dir)*dist#)
				p_add(nx#,1,nz#,Cp_rwave,Rnd(5,10),Rnd(0.9,1.5))
				For x=0 To Rand(5,10)
					dir=Rand(360)
					dist#=Rnd(TCinfo\floats[0])
					nx#=EntityX(TCinfo\h)+(Sin(dir)*dist#)
					nz#=EntityZ(TCinfo\h)-(Cos(dir)*dist#)
					p_add(nx#,-Rnd(5,50),nz#,10,Rnd(1,3))
				Next
			EndIf
		Case 46
			Select TCinfo\ints[0]
				Case 0 ai_signal(Cclass_info,TCinfo\id,ai_attract,TCinfo\floats[0])
				Case 1ai_signal(Cclass_info,TCinfo\id,ai_distract,TCinfo\floats[0])
			End Select
		Case 47
			If TCinfo\ints[0]<>0 Then
				If Int(TCinfo\floats[0])=0.0 Then
					If TCinfo\ints[1]=0 Then TCinfo\ints[1]=sfx(TCinfo\ints[0])
				Else
					dist#=EntityDistance(cam,TCinfo\h)
					If dist#<=TCinfo\floats[0] Then
						If TCinfo\ints[1]=0 Then TCinfo\ints[1]=sfx(TCinfo\ints[0])
					Else
						If TCinfo\ints[1]<>0 Then
							StopChannel TCinfo\ints[1]
							TCinfo\ints[1]=0
						EndIf
					EndIf
				EndIf
			EndIf
	End Select
End Function


Function serialpeak_info()
	Local peak=0
	For Tinfo.Tinfo=Each Tinfo
		If Tinfo\id>peak Then peak=Tinfo\id
	Next
	info_serial=peak
End Function


Function info_vts()
	TCinfo\vars$=TCinfo\ints[0]+""+TCinfo\ints[1]+""+TCinfo\ints[2]+""
	TCinfo\vars$=TCinfo\vars$+TCinfo\floats#[0]+""+TCinfo\floats#[1]+""+TCinfo\floats#[2]+""
	TCinfo\vars$=TCinfo\vars$+TCinfo\strings$[0]+""+TCinfo\strings$[1]+""+TCinfo\strings$[2]+""
End Function

Function info_stv()
	split$(TCinfo\vars$, "",8)
	TCinfo\ints[0]=splits$(0)
	TCinfo\ints[1]=splits$(1)
	TCinfo\ints[2]=splits$(2)
	TCinfo\floats#[0]=Float(splits$(3))
	TCinfo\floats#[1]=Float(splits$(4))
	TCinfo\floats#[2]=Float(splits$(5))
	TCinfo\strings$[0]=splits$(6)
	TCinfo\strings$[1]=splits$(7)
	TCinfo\strings$[2]=splits$(8)
End Function

Function info_area(typ,x#,z#)
	For Tinfo.Tinfo=Each Tinfo
		If Tinfo\typ=typ Then
			If (dist#(EntityX(Tinfo\h),EntityZ(Tinfo\h),x#,z#)-Tinfo\floats[0])<=0. Then
				Return 1
			EndIf
		EndIf
	Next
	Return 0
End Function

Function info_spawncontrol(Tinfo.Tinfo,force=0)
	Local c=0
	Local pc=0
	If Tinfo\typ=45 Then
		Tinfo\strings[2]=Int(Tinfo\strings[2])+1
		If Int(Tinfo\strings[2])>=Int(Tinfo\strings[1]) Or force=1 Then
			Tinfo\strings[2]=0
			pc=Int(Tinfo\strings[0])
			If Int(Tinfo\floats[1])=1 Or force=1 Then
				c=0
				Select Tinfo\ints[0]
					Case Cclass_object
						For Tobject.Tobject=Each Tobject
							If Tobject\typ=Tinfo\ints[1] Then
								If dist#(EntityX(Tinfo\h),EntityZ(Tinfo\h),EntityX(Tobject\h),EntityZ(Tobject\h))<=Tinfo\floats[0] Then
									c=c+1
								EndIf
							EndIf
						Next
						c=Tinfo\ints[2]-c
						If pc>0 And c>pc Then c=pc
						If c>0 Then
							For i=1 To c
							 	a=Rand(360)
								dist#=Rnd(Tinfo\floats[0])
								xos#=Sin(a)*dist#
								zos#=Cos(a)*dist#
								set_object(-1,Tinfo\ints[1],EntityX(Tinfo\h)+xos#,EntityZ(Tinfo\h)+zos#)
								If TCobject<>Null Then
									RotateEntity TCobject\h,0,Rnd(360),0
									set_parsecache(Cclass_object,TCobject\id,"spawn")
									If Instr(Dobject_scriptk$(TCobject\typ),"spawn") Then
										parse_task(Cclass_object,TCobject\id,"spawn","",Dobject_script(TCobject\typ))
									EndIf
									If EntityInView(TCobject\h,cam) Then
										If EntityDistance(TCobject\h,cam)<3000 Then
											For fx=1 To 5+(set_effects*10)
												p_add(EntityX(TCobject\h)+Rnd(-20.,20.),EntityY(TCobject\h)+Rnd(-5.,5.),EntityZ(TCobject\h)+Rnd(-20.,20.),Cp_spawn,Rnd(3,6),Rnd(0.5,2))
											Next
										EndIf
									EndIf
								EndIf
							Next
						EndIf
					Case Cclass_unit
						For Tunit.Tunit=Each Tunit
							If Tunit\typ=Tinfo\ints[1] Then
								If dist#(EntityX(Tinfo\h),EntityZ(Tinfo\h),EntityX(Tunit\h),EntityZ(Tunit\h))<=Tinfo\floats[0] Then
									c=c+1
								EndIf
							EndIf
						Next
						c=Tinfo\ints[2]-c
						If pc>0 And c>pc Then c=pc
						If c>0 Then
							For i=1 To c
								a=Rand(360)
								dist#=Rnd(Tinfo\floats[0])
								xos#=Sin(a)*dist#
								zos#=Cos(a)*dist#
								set_unit(-1,Tinfo\ints[1],EntityX(Tinfo\h)+xos#,EntityZ(Tinfo\h)+zos#)
								If TCunit<>Null Then
									RotateEntity TCunit\h,0,Rnd(360),0
									ai_ini()
									set_parsecache(Cclass_unit,TCunit\id,"spawn")
									If Instr(Dunit_scriptk$(TCunit\typ),"spawn") Then
										parse_task(Cclass_unit,TCunit\id,"spawn","",Dunit_script(TCunit\typ))
									EndIf
									If EntityInView(TCunit\h,cam) Then
										If EntityDistance(TCunit\h,cam)<3000 Then
											For fx=1 To 5+(set_effects*10)
												p_add(EntityX(TCunit\h)+Rnd(-20.,20.),EntityY(TCunit\h)-Dunit_colyr#(TCunit\typ)+Rnd(-5.,5.),EntityZ(TCunit\h)+Rnd(-20.,20.),Cp_spawn,Rnd(3,6),Rnd(0.5,2))
											Next
										EndIf
									EndIf
								EndIf
							Next
						EndIf
					Case Cclass_item
						For Titem.Titem=Each Titem
							If Titem\typ=Tinfo\ints[1] Then
								If dist#(EntityX(Tinfo\h),EntityZ(Tinfo\h),EntityX(Titem\h),EntityZ(Titem\h))<=Tinfo\floats[0] Then
									c=c+1
								EndIf
							EndIf
						Next
						c=Tinfo\ints[2]-c
						If pc>0 And c>pc Then c=pc
						If c>0 Then
							For i=1 To c
								a=Rand(360)
								dist#=Rnd(Tinfo\floats[0])
								xos#=Sin(a)*dist#
								zos#=Cos#(a)*dist#
								ypos#=e_tery(EntityX(Tinfo\h)+xos#,EntityZ(Tinfo\h)+zos#)
								set_item(-1,Tinfo\ints[1],EntityX(Tinfo\h)+xos#,ypos#,EntityZ(Tinfo\h)+zos#)
								If TCitem<>Null Then
									RotateEntity TCitem\h,0,Rnd(360),0
									set_parsecache(Cclass_item,TCitem\id,"spawn")
									If Instr(Ditem_scriptk$(TCitem\typ),"spawn") Then
										parse_task(Cclass_item,TCitem\id,"spawn","",Ditem_script(TCitem\typ))
									EndIf
									If EntityInView(TCitem\h,cam) Then
										If EntityDistance(TCitem\h,cam)<3000 Then
											For fx=1 To 5+(set_effects*10)
												p_add(EntityX(TCitem\h)+Rnd(-20.,20.),EntityY(TCitem\h)+Rnd(-5.,5.),EntityZ(TCitem\h)+Rnd(-20.,20.),Cp_spawn,Rnd(3,6),Rnd(0.5,2))
											Next
										EndIf
									EndIf
								EndIf
							Next
						EndIf
				End Select
			EndIf
		EndIf
	EndIf
End Function

Function info_setupsprite(i.Tinfo)
	Local x#=EntityX(i\h)
	Local y#=EntityY(i\h)
	Local z#=EntityZ(i\h)
	usesprite=0
	FreeEntity i\h
	If i\Strings[0]<>"" Then
		Local tex=load_res(i\strings[0],Cres_texture)
		If tex<>0 Then
			i\h=CreateSprite()
			EntityTexture i\h,tex
			ScaleSprite i\h,i\floats[0],i\floats[1]
			EntityColor i\h,i\ints[0],i\ints[1],i\ints[2]
			EntityAlpha i\h,i\floats[2]
			If Int(i\strings[1])=1 Then
				EntityBlend i\h,3
			EndIf
			If Int(i\strings[2])=1 Then
				SpriteViewMode i\h,4
			EndIf
			EntityAutoFade i\h,(500.*set_viewfac#),(500.*set_viewfac#)+300
			Local temp=i\h
			NameEntity temp,"model"
			If m_section=Csection_editor Then
				i\h=CreateCube()
				ScaleEntity i\h,5,5,5
				EntityBlend i\h,3
				EntityColor i\h,0,255,0
				EntityAutoFade i\h,5000,5500
				EntityPickMode i\h,2,1		
				EntityParent temp,i\h
			EndIf
			usesprite=1
		EndIf
	EndIf
	If usesprite=0 Then
		If m_section=Csection_editor Then
			i\h=CreateCube()
			ScaleEntity i\h,5,5,5
			EntityBlend i\h,3
			EntityColor i\h,0,255,0
			EntityAutoFade i\h,5000,5500
			EntityPickMode i\h,2,1
		Else
			i\h=CreatePivot()
			HideEntity i\h
		EndIf
	EndIf
	PositionEntity i\h,x#,y#,z#
End Function


Function info_setuploudspeaker(i.Tinfo)
	If i\strings[0]<>"" Then
		If FileType(i\strings[0])=1 Then
			i\ints[0]=LoadSound(i\strings[0])
			If i\ints[0]<>0 Then
				LoopSound(i\ints[0])
				If i\floats[0]=0.0 Then
					If i\ints[1]=0 Then i\ints[1]=sfx(i\ints[0])
				EndIf
			EndIf
		EndIf
	Else
		If tmp_loading=0 Then
			If i\ints[1]<>0 Then StopChannel i\ints[1]
			i\ints[0]=0
			i\ints[1]=0
		EndIf
	EndIf
End Function


Function frap_info()
	Tinfo.Tinfo=TCinfo
	
	Local h=Tinfo\h
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
	
	If EntityInView(Tinfo\h,cam) Then
		
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
												If shift=0 Then
													in_cursorf=4
													RotateEntity h,EntityPitch(h)+Float(Float(in_mxs#+in_mys#)*s#),EntityYaw(h),EntityRoll(h)
												Else
													in_cursorf=2
													MoveEntity h,Float(Float(in_mxs#+in_mys#)*s#),0,0
												EndIf
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
								If shift=0 Then
									in_cursorf=4
									RotateEntity h,EntityPitch(h)+Float(Float(in_mxs#+in_mys#)*s#),EntityYaw(h),EntityRoll(h)
								Else
									in_cursorf=2
									MoveEntity h,Float(Float(in_mxs#+in_mys#)*s#),0,0
								EndIf
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
												If shift=0 Then
													in_cursorf=4
													RotateEntity h,EntityPitch(h),EntityYaw(h)+Float(Float(in_mxs#+in_mys#)*s#),EntityRoll(h)
												Else
													in_cursorf=2
													MoveEntity h,0,Float(Float(in_mxs#+in_mys#)*s#),0
												EndIf
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
								If shift=0 Then
									in_cursorf=4
									RotateEntity h,EntityPitch(h),EntityYaw(h)+Float(Float(in_mxs#+in_mys#)*s#),EntityRoll(h)
								Else
									in_cursorf=2
									MoveEntity h,0,Float(Float(in_mxs#+in_mys#)*s#),0
								EndIf
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
												If shift=0 Then
													in_cursorf=4
													RotateEntity h,EntityPitch(h),EntityYaw(h),EntityRoll(h)+Float(Float(in_mxs#+in_mys#)*s#) 
												Else
													in_cursorf=2
													MoveEntity h,0,0,Float(Float(in_mxs#+in_mys#)*s#)
												EndIf
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
								If shift=0 Then
									in_cursorf=4
									RotateEntity h,EntityPitch(h),EntityYaw(h),EntityRoll(h)+Float(Float(in_mxs#+in_mys#)*s#) 
								Else
									in_cursorf=2
									MoveEntity h,0,0,Float(Float(in_mxs#+in_mys#)*s#)
								EndIf
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
					CameraProject(cam,EntityX(Tinfo\h),EntityY(Tinfo\h),EntityZ(Tinfo\h))
					bmpf_txt_c(ProjectedX(),ProjectedY()-30,"#"+Tinfo\id+" "+Dinfo_name$(Tinfo\typ),Cbmpf_tiny)
				EndIf
				
				in_edoverinfo$=ss$(se$(197),Dinfo_name$(Tinfo\typ))
				
			EndIf
		EndIf			
	EndIf
					
	If KeyHit(211) Then free_info(Tinfo\id):in_fraph=0
		
	If KeyHit(57) Then in_fraph=0
		
End Function