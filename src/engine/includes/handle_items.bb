Function set_item(id=-1,typ,x#=0,y#=0,z#=0,count=1,silent=0)
	TCitem.Titem=Null

	If Len(Ditem_name$(typ))=0 Then Return -1

	If id=-1 Then
		item_serial=item_serial+1
		id=item_serial
	EndIf
	
	If Ditem_modelh(typ)=0 Then
		load_item_model(typ)
	EndIf
	
	If count<=1 Then count=1
	
	Titem.Titem=New Titem
	Titem\id=id
	Titem\typ=typ
	Local h
	If Ditem_modelh(typ)<>0 Then
		h=CopyEntity(Ditem_modelh(typ))
	Else
		h=CopyEntity(game_defaultitemmodel)
		EntityFX h,16
	EndIf
	Titem\h=h
	Titem\count=count
	
	ScaleEntity h,Ditem_size#(typ,0),Ditem_size#(typ,1),Ditem_size#(typ,2)
	EntityColor h,Ditem_color(typ,0),Ditem_color(typ,1),Ditem_color(typ,2)
	If Ditem_fx(typ)<>0 Then EntityFX h,Ditem_fx(typ)
	If Ditem_autofade(typ)<>0 Then EntityAutoFade h,Float(Ditem_autofade(typ))*set_viewfac#,(Float(Ditem_autofade(typ))*set_viewfac#)+50
	EntityAlpha h,Ditem_alpha#(typ)
	If Ditem_shininess#(typ)>0.0 EntityShininess h,Ditem_shininess#(typ)
	If Ditem_blend(typ)<>0 Then EntityBlend h,Ditem_blend(typ)
	
	Titem\health#=Ditem_health#(typ)
	Titem\phy_fall=gt
	
	PositionEntity h,x#,y#,z#
	
	If Ditem_radius#(typ)=0. Then
		EntityPickMode h,2,1
		EntityRadius h,0.1
		EntityType h,Cworld_itemcol
	Else
		EntityRadius h,Ditem_radius#(typ)
		EntityPickMode h,1,1
		EntityType h,Cworld_itemcol
	EndIf
		
	If silent=0 Then
		If tmp_loading=0 Then
			set_parsecache(Cclass_item,Titem\id,"create")
			If Instr(Ditem_scriptk$(Titem\typ),"create") Then
				parse_task(Cclass_item,Titem\id,"create","",Ditem_script(Titem\typ))
			EndIf
		EndIf
	EndIf
	
	If silent=0 Then
		If tmp_loading=0 Then defvar_oncreate(Cclass_item,id,typ)
	EndIf
	
	TCitem.Titem=Titem
	Return id
End Function


Function store_item(id,parentclass,parentid,parentmode=Cpm_in,x#=0,y#=0,z#=0)
	For Titem.Titem=Each Titem
		If Titem\id=id Then
			
			storemode=1
			If parentmode=Cpm_in
				storecount=0
				free=-1
				needed=0
				If parentclass=Cclass_object Then
					If con_object(parentid) Then
						free=capacity(parentclass,parentid,TCobject\typ)
					EndIf
				ElseIf parentclass=Cclass_unit Then
					If con_unit(parentid) Then
						free=capacity(parentclass,parentid,TCunit\typ)
					EndIf
				EndIf
				If free<>-1 Then
					needed=Ditem_weight(Titem\typ)*Titem\count
					
					If needed<=free Then
						storemode=1
					ElseIf Ditem_weight(Titem\typ)<=free Then
						storemode=2
						For i=1 To Titem\count
							If (Ditem_weight(Titem\typ)*i)>free Then Exit
						Next
						storecount=i-1
					Else
						storemode=0
					EndIf
					If storemode=0 Then
						Return 0
					EndIf
					
				EndIf
			EndIf

			
			createnew=1
			If parentmode=Cpm_in Then
				For Texists.Titem=Each Titem
					If Texists\id<>id Then
						If Texists\typ=Titem\typ Then
							If Texists\parent_class=parentclass Then
								If Texists\parent_id=parentid Then
									If storemode=1 Then
										Local oldcount=Titem\count
										free_item(id)
										Texists\count=Texists\Count+oldcount
										Titem.Titem=Texists
										createnew=0
										Exit
									Else
										Titem\count=Titem\count-storecount
										Texists\count=Texists\Count+storecount
										Titem.Titem=Texists
										createnew=0
										Exit
									EndIf
								EndIf
							EndIf
						EndIf
					EndIf	
				Next
			EndIf
			
			If storemode=2 Then
				If createnew=1 Then
					Titem\count=Titem\count-storecount
					set_item(-1,Titem\typ,0,0,0,storecount,1)
					Titem.Titem=TCitem
				EndIf
			ElseIf storemode=1 Then
				Insert Titem After Last Titem
			EndIf
		
			Titem\parent_class=parentclass
			Titem\parent_id=parentid
			Titem\parent_mode=parentmode
			
			free_childs(Cclass_item,Titem\id)
			
			PositionEntity Titem\h,x#,y#,z#
			If parentmode=Cpm_in Then
				HideEntity Titem\h
			Else
				ShowEntity Titem\h
			EndIf
			
			If parentmode=Cpm_out Then
				RotateEntity Titem\h,-90,Rand(360),0
				TCitem.Titem=Titem
			EndIf
			
			Return 1
		EndIf
	Next
	Return 0
End Function


Function unstore_item(id,count=1,atparent=0,x#=0,y#=0,z#=0)
	For Titem.Titem=Each Titem
		If Titem\id=id Then
		
			If atparent Then
				Local parenth=parent_h(Titem\parent_class,Titem\parent_id)
				x#=EntityX(parenth)
				y#=EntityY(parenth)
				z#=EntityZ(parenth)
			EndIf
			
			If count<Titem\count Then
				Titem\count=Titem\count-count
				set_item(-1,Titem\typ,x#,y#,z#,count)
			Else
				Titem\parent_class=0
				Titem\parent_id=0
				Titem\parent_mode=0
				Titem\if_sel=0
				Titem\phy_fall=gt
				Titem\phy_pause=0
				PositionEntity Titem\h,x#,y#,z#
				ShowEntity Titem\h
				TCitem.Titem=Titem
			EndIf
			
			Return 1
		EndIf
	Next
	Return 0
End Function


Function countstored_items(parent_class,parent_id,typ=0,dif=0)
	Local c=0,difc=0
	For Titem.Titem=Each Titem
		If Titem\parent_class=parent_class Then
			If Titem\parent_id=parent_id Then
				If Titem\typ=typ Or typ=0 Then
					c=c+Titem\count
					difc=difc+1
				EndIf
			EndIf
		EndIf
	Next
	If dif=0 Then
		Return c
	Else
		Return difc
	EndIf
End Function


Function merge_item(id,mergerange#=30.)
	Local mergecount=0
	For Titem.Titem=Each Titem
		If Titem\id=id Then
			
			For Tmerge.Titem=Each Titem
				If Tmerge\id<>Titem\id Then
					If Tmerge\typ=Titem\typ Then
						If Tmerge\parent_id=Titem\parent_id Then
							If EntityDistance(Tmerge\h,Titem\h)<=mergerange# Then
								Titem\count=Titem\count+Tmerge\count
								free_item(Tmerge\id,-1)
								mergecount=mergecount+1
							EndIf
						EndIf
					EndIf
				EndIf
			Next
			
			Return mergecount
		EndIf
	Next
	Return -1
End Function


Function combine_item(parentclass,parentid,forceid=-1)
	Local con
	Local conok
	Local conokc
	Local sel
	Local genid=0
	Local selthis
	
	For Trcom.Tcom=Each Tcom
		
		
		selthis=0
		If Trcom\mode=2 Then
			If forceid=-1 Then
				selthis=1
			Else
				If Trcom\id=forceid Then selthis=1
			EndIf
		EndIf
		
		If selthis=1 Then
			con=0
			conok=0
			conokc=0
			For Tccom.Tcom=Each Tcom
				If Tccom\id=Trcom\id Then
					If Tccom\mode=0 Then
						con=con+1
						For Titem.Titem=Each Titem
							If Titem\if_sel=1 Then
								If Titem\typ=Tccom\typ Then
									If Titem\count>=Tccom\count Then
										conok=conok+1
									EndIf
									conokc=conokc+1
								EndIf
							EndIf
						Next
					ElseIf Tccom\mode=1 Then
						genid=Tccom\typ
					EndIf
				EndIf
			Next
			sel=0
			For Titem.Titem=Each Titem
				If Titem\if_sel=1 Then
					sel=sel+1
				EndIf
			Next
			If sel=con Then
				If (con=conok) Or (game_combiscreen>0 And con=conokc) Then
				
					If forceid=-1 Then
						If Trcom\gid<>0 Then
							If Trcom\gid>0 Then
								If game_combiscreen>0 Then
									in_combi_gid=Trcom\gid
									in_scr_scr=0
									m_menu=Cmenu_if_combine
									Return 1
								EndIf
							ElseIf Trcom\gid<0 Then
								If game_combiscreen>1 Then
									in_combi_gid=Trcom\gid
									in_scr_scr=0
									m_menu=Cmenu_if_combine
									Return 1
								EndIf
							EndIf
						EndIf
					EndIf
				
					If con=conok Then
						locked=0
						For Tx.Tx=Each Tx
							If Tx\mode=50 Then
								If Tx\key$=Trcom\aid$ Then
									locked=1
									Exit
								EndIf
							EndIf
						Next
						If locked=0 Then
							p_skipevent=0
							If Trcom\script$<>"" Then
								parse_task(0,0,"combine","(combinations.inf-script)",Trcom\script$)
								parse_sel(0,0,"combine")
							EndIf
							If p_skipevent=0 Then
								For Tpcom.Tcom=Each Tcom
									If Tpcom\id=Trcom\id Then
										If Tpcom\mode=0 Then
											If Tpcom\del=1 Then
												For Titem.Titem=Each Titem
													If Titem\if_sel=1 Then
														If Titem\typ=Tpcom\typ Then
															free_item(Titem\id,Tpcom\count)
														EndIf
													EndIf
												Next
											EndIf
										ElseIf Tpcom\mode=1 Then
											id=set_item(-1,Tpcom\typ,EntityX(cam),EntityY(cam),EntityZ(cam),Tpcom\count)
											If store_item(id,parentclass,parentid)=0 Then
												if_msg(sm$(181),Cbmpf_red)
												sfx sfx_fail
											EndIf
										EndIf
									EndIf
								Next
							EndIf
							Return 1
						Else
							speech("negative")
							Return 0
						EndIf
					Else
						speech("negative")
						Return 0
					EndIf
				EndIf
			EndIf
			
			If forceid>-1 Then
				Exit
			EndIf
			
		EndIf
		
	Next
	speech("negative")
	Return 0
End Function


Function exchange_item(class,id,count)

	If count>TCitem\count Then
		count=TCitem\count
	EndIf
	
	Local creq=Ditem_weight(TCitem\typ)*count
	Local cfree=0
	Local cok=1
	If creq<0 Then
		cok=0
		Select TCitem\parent_class
			Case Cclass_object
				con_object(TCitem\parent_id)
				cfree=capacity(TCitem\parent_class,TCitem\parent_id,TCobject\typ)
				If cfree>=Abs(creq) Then cok=1
			Case Cclass_unit
				con_unit(TCitem\parent_id)
				cfree=capacity(TCitem\parent_class,TCitem\parent_id,TCunit\typ)
				If cfree>=Abs(creq) Then cok=1
			Case Cclass_info cok=1
		End Select
		If cok=0 Then Return -1
	EndIf
	
	cfree=0
	cok=0
	Select class
		Case Cclass_object
			con_object(id)
			cfree=capacity(class,id,TCobject\typ)
			If cfree>=creq Then cok=1
		Case Cclass_unit
			con_unit(id)
			cfree=capacity(class,id,TCunit\typ)
			If cfree>=creq Then cok=1
		Case Cclass_info cok=1
	End Select

	If cok=0 Then
		While ((creq>cfree) And (count>0))
			count=count-1
			creq=Ditem_weight(TCitem\typ)*count
		Wend
		If count<=0 Then Return 0
	EndIf
	
	p_skipevent=0
	If TCitem\parent_class=Cclass_unit Then
		If TCitem\parent_id=g_player Then
			If set_parsecache(Cclass_item,TCitem\id,"drop") Then parse()
			If Instr(Ditem_scriptk$(TCitem\typ),"drop") Then
				parse_env(Cclass_item,TCitem\id,"drop")
				loadstring_parsecache(Ditem_script(TCitem\typ))
				parse()
			EndIf
		EndIf
	EndIf
	
	If p_skipevent=1 Then Return -2
	
	If TCitem\count=count Then
		For Titem.Titem=Each Titem
			If Titem\parent_class=class Then
				If Titem\parent_id=id Then
					If Titem\typ=TCitem\typ Then
						If Titem\id<>TCitem\id Then
							Titem\count=Titem\count+TCitem\count
							free_item(TCitem\id)
							Return 1
						EndIf
					EndIf
				EndIf
			EndIf	
		Next
		TCitem\parent_class=class
		TCitem\parent_id=id
		Insert TCitem After Last Titem
		Return 1
	Else
		TCitem\count=TCitem\count-count
		set_item(-1,TCitem\typ,0,0,0,count)
		store_item(TCitem\id,class,id)
		Return 2
	EndIf	
End Function


Function capacity(class,id,typ)
	Local total=0
	Select class
		Case Cclass_object total=Dobject_maxweight(typ)
		Case Cclass_unit total=Dunit_maxweight(typ)
	End Select
	Local used=0
	For Titem.Titem=Each Titem
		If Titem\parent_class=class
			If Titem\parent_id=id Then
				used=used+(Ditem_weight(Titem\typ)*Titem\count)
			EndIf
		EndIf
	Next
	Return (total-used)
End Function


Function use_item(id)
	For Titem.Titem=Each Titem
		If Titem\id=id Then
			
			action=0
			
			If item_istool(Ditem_behaviour$(Titem\typ)) Then
				If m_menu=Cmenu_if_items Then
					if_quickslot(0,1)	
					action=1
				EndIf
			EndIf
			
			p_skipevent=0
			set_parsecache(Cclass_item,Titem\id,"use")
			If Instr(Ditem_scriptk$(Titem\typ),"use") Then
				parse_task(Cclass_item,Titem\id,"use","",Ditem_script(Titem\typ))
				tasks=parse_sel(Cclass_item,Titem\id,"use")
				If tasks>0 Then action=1
			EndIf
			
			If p_skipevent=0 Then
				If Handle(Titem)<>0 Then
					Select Ditem_behaviour$(Titem\typ)
						Case "watch"
							action=1
							If map_hour=13 And map_minute=37 Then
								if_msg("13:37 "+sm$(36)+" - Y4y! t3h 3l!te!")
								parse_globalevent("leet")
								parse()
								p_add(0,0,0,Cp_flash,0.06,0.75)
								EntityColor TCp\h,0,255,0
								
							Else
								if_msg(map_hour+":"+map_minute+" "+sm$(36))
							EndIf
						Case "map"
							action=1
							m_menu=Cmenu_if_map
					End Select
				EndIf
			EndIf
			
			If action=0 Then
				if_msg(sm$(196),Cbmpf_red)
				speech("negative")
			EndIf
			Return 1
		EndIf
	Next
	Return 0
End Function


Function eat_item(id)
	For Titem.Titem=Each Titem
		If Titem\id=id Then
			
			set_parsecache(Cclass_item,Titem\id,"eat")
			If Instr(Ditem_scriptk$(Titem\typ),"eat") Then
				parse_task(Cclass_item,Titem\id,"eat","",Ditem_script(Titem\typ))
				action=parse_sel(Cclass_item,Titem\id,"eat")
			EndIf
			
			If action=0 Then
				if_msg(sm$(195),Cbmpf_red)
				speech("negative")
			EndIf
			Return 1
		EndIf
	Next
	Return 0
End Function


Function get_item(searchhandle)
	For Titem.Titem=Each Titem
		If Titem\h=searchhandle Then
			TCitem.Titem=Titem
			Return Titem\id
		EndIf
	Next
	Return -1
End Function


Function get_stored_item(typ,parent_class,parent_id)
	For Titem.Titem=Each Titem
		If Titem\typ=typ Then
			If Titem\parent_class=parent_class Then
				If Titem\parent_id=parent_id Then
					TCitem.Titem=Titem
					Return 1
				EndIf
			EndIf
		EndIf
	Next
	Return 0
End Function


Function con_item(id)
	For Titem.Titem=Each Titem
		If Titem\id=id Then TCitem.Titem=Titem:Return 1
	Next
	Return 0
End Function


Function draw_item()
	Titem.Titem=TCitem
	If EntityInView(Titem\h,cam) Then
		CameraProject(cam,EntityX(Titem\h),EntityY(Titem\h),EntityZ(Titem\h))
		bmpf_txt_c(ProjectedX(),ProjectedY()-30,"#"+Titem\id+" "+Ditem_name$(Titem\typ)+" "+Titem\count+"x",Cbmpf_tiny)
		If KeyHit(57) Then
			in_fraph=Titem\h
		EndIf
		in_edoverinfo$=ss$(se$(198),Ditem_name$(Titem\typ))
		If KeyHit(211) Then free_item(Titem\id)
	EndIf
End Function


Function free_item(id,count=-1)
	For Titem.Titem=Each Titem
		If Titem\id=id Then
			If count=-1 Or count>=Titem\count Then
				free_childs(Cclass_item,id)
				If Titem\h<>0 Then FreeEntity Titem\h
				Delete Titem
				Return 1
			Else
				Titem\count=Titem\count-count
				Return 1
			EndIf
		EndIf
	Next
	Return 0
End Function


Function update_items()
	For i=1 To g_itemlistc
		h=g_itemlist(i)\h
		
		TranslateEntity h,0,-(Cworld_g#*f#*0.6),0
		
		tery#=e_tery(EntityX(h),EntityZ(h))
		If EntityY(h)<(tery#+5) Then
			PositionEntity h,EntityX(h),tery#,EntityZ(h)
		EndIf
		
	Next
End Function


Function serialpeak_item()
	Local peak=0
	For Titem.Titem=Each Titem
		If Titem\id>peak Then peak=Titem\id
	Next
	item_serial=peak
End Function


Function damage_item(damage#,kill=0,pickcords=0)
	If pickcords=1 Then
		material_fx(in_px#,in_py#,in_pz#,Ditem_mat(TCitem\typ))
	ElseIf pickcords=0
		material_fx(EntityX(TCitem\h),EntityY(TCitem\h),EntityZ(TCitem\h),Ditem_mat(TCitem\typ))
	EndIf
	If kill=0 Then
		If TCitem\parent_class<>0 Then
			If TCitem\parent_mode=Cpm_out Then
			
				p_skipevent=0
				set_parsecache(Cclass_item,TCitem\id,"separate")
				If Instr(Ditem_scriptk$(TCitem\typ),"separate") Then
					parse_task(Cclass_item,TCitem\id,"separate","",Ditem_script(TCitem\typ))
					parse_sel(Cclass_item,TCitem\id,"separate")
				EndIf
				If p_skipevent=0 Then
					TCitem\parent_class=0
					TCitem\parent_id=0
					Return 0
				EndIf
			EndIf
		EndIf
	EndIf
	If get_state(Cstate_invulnerability,Cclass_item,TCitem\id)=0 Then
		TCitem\health#=TCitem\health#-damage#
		If TCitem\health#<=0. Then
			If TCitem\count>1 Then
				TCitem\count=TCitem\count-1
				TCitem\health#=Ditem_health#(TCitem\typ)
			EndIf
		EndIf
	EndIf
	If TCitem\health#<=0. Or kill=1 Then
		Tkill.Titem=TCitem
		set_parsecache(Cclass_item,TCitem\id,"kill")
		If Instr(Ditem_scriptk$(TCitem\typ),"kill") Then
			parse_task(Cclass_item,TCitem\id,"kill","",Ditem_script(TCitem\typ))
		EndIf
		Local tmp_killpivot=CreatePivot()
		PositionEntity tmp_killpivot,EntityX(Tkill\h),EntityY(Tkill\h),EntityZ(Tkill\h)
		RotateEntity tmp_killpivot,EntityPitch(Tkill\h),EntityYaw(Tkill\h),EntityRoll(Tkill\h)
		tmp_h=0
		tmp_killclass=Cclass_item
		tmp_killid=Tkill\id
		tmp_killmodel=tmp_killpivot
		free_item(Tkill\id)
		parse_kill_add(tmp_killclass,tmp_killid,tmp_killmodel)
		tmp_kill=1
		Return 2
	EndIf
	Return 1
End Function


Function item_istool(behaviour$)
	Select behaviour$
		Case "tool" Return 1
		Case "weapon" Return 1
		Case "hammer" Return 1
		Case "slowblade" Return 1
		Case "blade" Return 1
		Case "fastblade" Return 1
		Case "slingshot" Return 1
		Case "bow" Return 1
		Case "launcher" Return 1
		Case "catapult" Return 1
		Case "pistol" Return 1
		Case "gun" Return 1
		Case "machinegun" Return 1
		Case "spear" Return 1
		Case "selfthrow" Return 1
		Case "spade" Return 1
		Case "fishingrod" Return 1
		Case "net" Return 1
		Case "torch" Return 1
		Default Return 0
	End Select
End Function


Function item_getrandom(parent_class,parent_id)
	Local c=0
	For Titem.Titem=Each Titem
		If Titem\parent_class=parent_class Then
			If Titem\parent_id=parent_id Then
				c=c+1
			EndIf
		EndIf
	Next
	If c=0 Then Return 0
	Local rnum=Rand(1,c)
	c=0
	For Titem.Titem=Each Titem
		If Titem\parent_class=parent_class Then
			If Titem\parent_id=parent_id Then
				c=c+1
				If c=rnum Then
					TCitem.Titem=Titem
					Return 1
				EndIf
			EndIf
		EndIf
	Next
End Function


Function frap_item()
	Titem.Titem=TCitem
	
	Local h=Titem\h
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
	
	If EntityInView(Titem\h,cam) Then
		
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
					CameraProject(cam,EntityX(Titem\h),EntityY(Titem\h),EntityZ(Titem\h))
					bmpf_txt_c(ProjectedX(),ProjectedY()-30,"#"+Titem\id+" "+Ditem_name$(Titem\typ),Cbmpf_tiny)
				EndIf
				
				in_edoverinfo$=ss$(se$(197),Ditem_name$(Titem\typ))
				
			EndIf
		EndIf			
	EndIf
	
	If KeyHit(211) Then free_item(Titem\id):in_fraph=0
		
	If KeyHit(57) Then in_fraph=0
		
End Function


Function item_phyreset()
	For Titem.Titem=Each Titem
		Titem\phy_pause=0
	Next
End Function