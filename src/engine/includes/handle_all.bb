Function ha_inrange(h,range,class,typ=0)
	Local c=0
	Select class
		Case 0
			If m_section<>Csection_game_sp Then
				For Tunit.Tunit=Each Tunit
					If Tunit\id<100 Then
						If EntityDistance(h,Tunit\h)<=range Then
							c=c+1
						EndIf
					EndIf
				Next
			Else
				If EntityDistance(h,g_cplayer\h)<=range Then
					c=c+1
				EndIf
			EndIf
		Case Cclass_object
			For Tobject.Tobject=Each Tobject
				If Tobject\typ=typ Then
					If EntityDistance(h,Tobject\h)<=range Then
						c=c+1
					EndIf
				EndIf
			Next
		Case Cclass_unit
			For Tunit.Tunit=Each Tunit
				If Tunit\typ=typ Then
					If Tunit\health#>0. Then
						If EntityDistance(h,Tunit\h)<=range Then
							c=c+1
						EndIf
					EndIf
				EndIf
			Next
		Case Cclass_item
			For Titem.Titem=Each Titem
				If Titem\parent_class=0 Then
					If Titem\typ=typ Then
						If EntityDistance(h,Titem\h)<=range Then
							c=c+Titem\count
						EndIf
					EndIf
				EndIf
			Next
		Case Cclass_info
			For Tinfo.Tinfo=Each Tinfo
				If Tinfo\typ=typ Then
					If EntityDistance(h,Tinfo\h)<=range Then
						c=c+1
					EndIf
				EndIf
			Next
		Case Cclass_state
			Local stateh
			For Tstate.Tstate=Each Tstate
				If Tstate\typ=typ Then
					stateh=parent_h(Tstate\parent_class,Tstate\parent_id)
					If stateh<>0 Then
						If EntityDistance(h,stateh)<=range Then
							c=c+1
						EndIf
					EndIf
				EndIf
			Next
	End Select
	Return c
End Function


Function ha_getclass(h,bycollisionpivot=1)
	tmp_class=0
	tmp_id=0
	If get_object(h)>-1 Then
		tmp_class=Cclass_object
		tmp_id=TCobject\id
		Return 1
	ElseIf get_unit(h,bycollisionpivot)>-1 Then
		tmp_class=Cclass_unit
		tmp_id=TCunit\id
		Return 1
	ElseIf get_item(h)>-1 Then
		tmp_class=Cclass_item
		tmp_id=TCitem\id
		Return 1
	Else
		Return 0
	EndIf
End Function


Function ha_idinrange(h,range,class,id)
	Select class
		Case 0
			Return 0
		Case Cclass_object
			For Tobject.Tobject=Each Tobject
				If Tobject\id=id Then
					If EntityDistance(h,Tobject\h)<=range Then
						Return 1
					EndIf
				EndIf
			Next
		Case Cclass_unit
			For Tunit.Tunit=Each Tunit
				If Tunit\id=id Then
					If EntityDistance(h,Tunit\h)<=range Then
						Return 1
					EndIf
				EndIf
			Next
		Case Cclass_item
			For Titem.Titem=Each Titem
				If Titem\parent_class=0 Then
					If Titem\id=id Then
						If EntityDistance(h,Titem\h)<=range Then
							Return 1
						EndIf
					EndIf
				EndIf
			Next
		Case Cclass_info
			For Tinfo.Tinfo=Each Tinfo
				If Tinfo\id=id Then
					If EntityDistance(h,Tinfo\h)<=range Then
						Return 1
					EndIf
				EndIf
			Next
		Case Cclass_state
			Return 0
	End Select
End Function


Function ha_distance(class1,id1,class2,id2)
	Local h1=parent_h(class1,id1)
	Local h2=parent_h(class2,id2)
	If h1<>0 Then
		If h2<>0 Then
			Return EntityDistance(h1,h2)
		EndIf
	EndIf
	Return 0
End Function


Function ha_free(class,id)
	If class=-1
		class=p_env_class
		id=p_env_id
	EndIf
	Select class
		Case Cclass_object
			free_object(id)
		Case Cclass_unit
			free_unit(id)
		Case Cclass_item
			free_item(id)
		Case Cclass_info
			free_info(id)
		Case Cclass_state
			con_add("ERROR: Unable to remove a state by 'free' scriptcommand!",Cbmpf_red)
	End Select
End Function


Function ha_damage(class,id,damage#,causer=0,pickcords=0)
	Select class
		Case Cclass_object
			If con_object(id) Then
				Return damage_object(damage#,0,pickcords)
			EndIf
		Case Cclass_unit
			If con_unit(id) Then
				Return hurt_unit(damage#,0,pickcords)
			EndIf
		Case Cclass_item
			If con_item(id) Then
				Return damage_item(damage#,0,pickcords)
			EndIf
	End Select
	Return 0
End Function


Function ha_heal(class,id,damage#)
	damage#=Abs(damage#)
	Select class
		Case Cclass_object
			If con_object(id)
				TCobject\health=TCobject\health+damage#
				If TCobject\health>Dobject_health(TCobject\typ) Then
					TCobject\health=Dobject_health(TCobject\typ)
				EndIf
			EndIf
		Case Cclass_unit
			If con_unit(id) Then
				TCunit\health=TCunit\health+damage#
				If TCunit\health>TCunit\health_max Then
					TCunit\health=TCunit\health_max
				EndIf
			EndIf
		Case Cclass_item
			If con_item(id) Then
				TCitem\health=TCitem\health+damage#
				If TCitem\health>Ditem_health(TCitem\typ) Then
					TCitem\health=Ditem_health(TCitem\typ)
				EndIf
			EndIf
	End Select
	Return 0
End Function


Function ha_killed(class,id)
	Select class
		Case Cclass_object
			For Tobject.Tobject=Each Tobject
				If Tobject\id=id Then
					Return 0
				EndIf
			Next
			Return 1
		Case Cclass_unit
			For Tunit.Tunit=Each Tunit
				If Tunit\id=id Then
					If Tunit\health_max#=0. Then
						Return 1
					Else
						Return 0
					EndIf
				EndIf
			Next
			Return 1
		Case Cclass_item
			For Titem.Titem=Each Titem
				If Titem\id=id Then
					Return 0
				EndIf
			Next
			Return 1
		Case Cclass_info
			For Tinfo.Tinfo=Each Tinfo
				If Tinfo\id=id Then
					Return 0
				EndIf
			Next
			Return 1
	End Select
End Function


Function ha_freespace(x#,y#,z#,range=300,objects=1,units=1,items=1,infos=0)
	Local h=CreatePivot()
	PositionEntity h,x#,y#,z#
	If objects=1 Then
		For Tobject.Tobject=Each Tobject
			If EntityDistance(h,Tobject\h)<range Then
				FreeEntity h
				Return 0
			EndIf
		Next
	EndIf
	If units=1 Then
		For Tunit.Tunit=Each Tunit
			If EntityDistance(h,Tunit\h)<range Then
				FreeEntity h
				Return 0
			EndIf
		Next
	EndIf
	If items=1 Then
		For Titem.Titem=Each Titem
			If Titem\parent_mode=0 Then
				If EntityDistance(h,Titem\h)<range Then
					FreeEntity h
					Return 0
				EndIf
			EndIf
		Next
	EndIf
	If infos=1 Then
		For Tinfo.Tinfo=Each Tinfo
			If EntityDistance(h,Tinfo\h)<range Then
				FreeEntity h
				Return 0
			EndIf
		Next
	EndIf
	Return 1
End Function


Function ha_health#(class,id)
	Select class
		Case Cclass_object
			For Tobject.Tobject=Each Tobject
				If Tobject\id=id Then
					Return Tobject\health#
				EndIf
			Next
		Case Cclass_unit
			For Tunit.Tunit=Each Tunit
				If Tunit\id=id Then
					Return Tunit\health#
				EndIf
			Next
		Case Cclass_item
			For Titem.Titem=Each Titem
				If Titem\id=id Then
					Return Titem\health#
				EndIf
			Next
	End Select
	Return 0	
End Function


Function ha_type(class,id)
	Select class
		Case Cclass_object
			For Tobject.Tobject=Each Tobject
				If Tobject\id=id Then
					Return Tobject\typ
				EndIf
			Next
		Case Cclass_unit
			For Tunit.Tunit=Each Tunit
				If Tunit\id=id Then
					Return Tunit\typ
				EndIf
			Next
		Case Cclass_item
			For Titem.Titem=Each Titem
				If Titem\id=id Then
					Return Titem\typ
				EndIf
			Next
		Case Cclass_info
			For Tinfo.Tinfo=Each Tinfo
				If Tinfo\id=id Then
					Return Tinfo\typ
				EndIf
			Next
	End Select
	Return 0	
End Function


Function ha_typeexists(class,typ)
	If typ<1 Then Return 0
	Select class
		Case Cclass_object
			If typ>Cobject_count Then Return 0
			If Dobject_name$(typ)<>"" Then Return 1
		Case Cclass_unit
			If typ>Cunit_count Then Return 0
			If Dunit_name$(typ)<>"" Then Return 1
		Case Cclass_item
			If typ>Citem_count Then Return 0
			If Ditem_name$(typ)<>"" Then Return 1
	End Select
	Return 0
End Function


Function ha_intersect(class,id)
	h=parent_h(class,id)
	collide=0
	For Tobject.Tobject=Each Tobject
		If class<>Cclass_object Or id<>Tobject\id Then
			If EntityDistance(h,Tobject\h)<3000 Then
				If MeshesIntersect(h,Tobject\h) Then Return 1
			EndIf
		EndIf
	Next
	For Tunit.Tunit=Each Tunit
		If class<>Cclass_unit Or id<>Tunit\id Then
			If EntityDistance(h,Tunit\h)<1000 Then
				If MeshesIntersect(h,Tunit\mh) Then Return 1
			EndIf
		EndIf		
	Next
	For Titem.Titem=Each Titem
		If class<>Cclass_item Or id<>Titem\id Then
			If EntityDistance(h,Titem\h)<500 Then
				If MeshesIntersect(h,Titem\h) Then Return 1
			EndIf
		EndIf
	Next
	Return 0
End Function


Function ha_setpos(setph,classint,id,setpx$,setpy$,setpz$)
	HideEntity setph
	PositionEntity setph,Float(setpx$),Float(setpy$),Float(setpz$)
	ShowEntity setph
	If classint=Cclass_unit Then
		For Tunit.Tunit=Each Tunit
			If Tunit\id=id Then
				If Tunit\vh<>0 Then HideEntity Tunit\vh
				PositionEntity Tunit\h,Float(setpx$),Float(setpy$),Float(setpz$)
				If Tunit\vh<>0 Then
					PositionEntity Tunit\vh,Float(setpx$),Float(setpy$),Float(setpz$)
					ShowEntity Tunit\vh
				EndIf
			EndIf
		Next
	ElseIf classint=Cclass_object Then
		For Tobject.Tobject=Each Tobject
			If Tobject\id=id Then
				If Tobject\ch<>0 Then
					PositionEntity Tobject\ch,Float(setpx$),Float(setpy$),Float(setpz$)
				EndIf
				Exit
			EndIf
		Next
		update=0
		For Tfrap.Tfrap=Each Tfrap
			If Tfrap\parent_class=Cclass_object Then
				If Tfrap\parent_id=id Then
					Tfrap\y#=EntityY(setph)
					Tfrap\pitch#=EntityPitch(setph)
					Tfrap\roll#=EntityRoll(setph)
					update=1
					Exit
				EndIf
			EndIf
		Next
		If update=0 Then
			Tfrap.Tfrap=New Tfrap
			Tfrap\parent_class=Cclass_object
			Tfrap\parent_id=id
			Tfrap\y#=EntityY(setph)
			Tfrap\pitch#=EntityPitch(setph)
			Tfrap\roll#=EntityRoll(setph)
		EndIf
		item_phyreset()
	ElseIf classint=Cclass_item Then
		If con_item(id) Then
			TCitem\phy_pause=0
		EndIf
	EndIf
	If classint=Cclass_unit Then
		If id=g_player Then
			in_tpforce=3
			in_tp#(0)=Float(setpx$)
			in_tp#(1)=Float(setpy$)
			in_tp#(2)=Float(setpz$)
		EndIf
	EndIf
End Function


Function ha_copychilds(class,id,vars=1,items=1,states=1,script=0,addtocache=0)
	If addtocache=0 Then
		For Tcopy.Tcopy=Each Tcopy
			Delete Tcopy
		Next
	EndIf
	For Tx.Tx=Each Tx
		If Tx\parent_class=class Then
			If Tx\parent_id=id Then
				If Tx\mode=0 Then
					If script=1 Then
						Tcopy.Tcopy=New Tcopy
						Tcopy\typ=0
						Tcopy\value_s$=Tx\value$
					EndIf
				ElseIf Tx\mode=4 Then
					If vars=1 Then
						Tcopy.Tcopy=New Tcopy
						Tcopy\typ=1
						Tcopy\key$=Tx\key$
						Tcopy\value_s$=Tx\value$
					EndIf
				EndIf
			EndIf
		EndIf
	Next
	For Titem.Titem=Each Titem
		If Titem\parent_class=class Then
			If Titem\parent_id=id Then		
				If Titem\parent_mode=Cpm_in Then
					Tcopy.Tcopy=New Tcopy
					Tcopy\typ=2
					Tcopy\id=Titem\typ
					Tcopy\value=Titem\count
				EndIf
			EndIf
		EndIf	
	Next
	For Tstate.Tstate=Each Tstate
		If Tstate\parent_class=class Then
			If Tstate\parent_id=id Then
				Tcopy.Tcopy=New Tcopy
				Tcopy\typ=3
				Tcopy\id=Tstate\typ
				Tcopy\x#=Tstate\x#
				Tcopy\y#=Tstate\y#
				Tcopy\z#=Tstate\z#
				Tcopy\fx#=Tstate\fx#
				Tcopy\fy#=Tstate\fy#
				Tcopy\fz#=Tstate\fz#
				Tcopy\value=Tstate\value
				Tcopy\value_f#=Tstate\value_f#
				Tcopy\value_s$=Tstate\value_s$
				Tcopy\r=Tstate\r
				Tcopy\g=Tstate\g
				Tcopy\b=Tstate\b
			EndIf
		EndIf
	Next
End Function

Function ha_pastechilds(class,id,vars=1,items=1,states=1,script=0)
	If class=Cclass_item Then items=0
	If ha_type(class,id)<>0 Then
		For Tcopy.Tcopy=Each Tcopy
			Select Tcopy\typ
				Case 0
					If script=1 Then
						found=0
						For Tx.Tx=Each Tx
							If Tx\parent_class=class Then
								If Tx\parent_id=id Then
									Tx\value$=Tx\value+""+Tcopy\value_s$
									Tx\key$=preparse_string$(Tx\value$)
									found=1
									Exit
								EndIf
							EndIf
						Next
						If found=0 Then
							Tx.Tx=New Tx
							Tx\parent_class=class
							Tx\parent_id=id
							Tx\value$=Tcopy\value_s$
							Tx\key$=preparse_string$(Tx\value$)
						EndIf
					EndIf
				Case 1
					If vars=1 Then
						found=0
						For Tx.Tx=Each Tx
							If Tx\mode=4 Then
								If Tx\parent_class=class Then
									If Tx\parent_id=id Then
										If Tx\key$=Tcopy\key$ Then
											Tx\value$=Tcopy\value_s$
											found=1
											Exit
										EndIf
									EndIf
								EndIf
							EndIf
						Next
						If found=0 Then
							Tx.Tx=New Tx
							Tx\mode=4
							Tx\key$=Tcopy\key$
							Tx\value$=Tcopy\value_s$
							Tx\parent_class=class
							Tx\parent_id=id
						EndIf
					EndIf
				Case 2
					If items=1 Then
						tmpid=set_item(-1,Tcopy\id,0,0,0,Tcopy\value)
						store_item(tmpid,class,id)
					EndIf
				Case 3
					If states=1 Then
						set_state(Tcopy\id,class,id)
						For Tstate.Tstate=Each Tstate
							If Tstate\parent_class=class Then
								If Tstate\parent_id=id Then
									If Tstate\typ=Tcopy\id Then
										Tstate\value=Tcopy\value
										Tstate\value_f#=Tcopy\value_f#
										Tstate\value_s$=Tcopy\value_s$
										Tstate\r=Tcopy\r
										Tstate\g=Tcopy\g
										Tstate\b=Tcopy\b
										TCstate=Tstate
										look_state()
										Exit
									EndIf
								EndIf
							EndIf
						Next
					EndIf
			End Select
		Next
		Return 1
	Else
		Return 0
	EndIf
End Function