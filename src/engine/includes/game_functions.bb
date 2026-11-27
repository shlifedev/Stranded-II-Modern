Function game_playerh()
	For Tunit.Tunit=Each Tunit
		If Tunit\id=g_player Then Return Tunit\h
	Next
	Return -1
End Function


Function game_playerspawn(id=-1,spawnid=-1)
	If spawnid=-1 Then
		c=0
		For Tinfo.Tinfo=Each Tinfo
			If Tinfo\typ=1 Then c=c+1
		Next
		If c=0 Then 
			con_add("ERROR: Missing info '"+Dinfo_name$(1)+"' - spawning player at center",Cbmpf_red)
			free_unit(id)
			set_unit(id,1,0,0)
			tmp_spawnid=0
			Return 0
		EndIf
		Repeat
			For Tinfo.Tinfo=Each Tinfo
				If Tinfo\typ=1 Then
					If Rand(10)=1 Then
						x#=EntityX(Tinfo\h)
						y#=EntityY(Tinfo\h)
						z#=EntityZ(Tinfo\h)
						free_unit(id)
						set_unit(id,1,x#,z#)
						pos_unit(TCunit,x#,y#,z#)
						If g_player=id Then
							RotateEntity cam,EntityPitch(Tinfo\h),EntityYaw(Tinfo\h),0
						EndIf
						tmp_spawnid=Tinfo\id
						Return 1
					EndIf
				EndIf
			Next
		Forever
	Else
		For Tinfo.Tinfo=Each Tinfo
			If Tinfo\typ=1 Then
				If Tinfo\id=spawnid Then
					x#=EntityX(Tinfo\h)
					y#=EntityY(Tinfo\h)
					z#=EntityZ(Tinfo\h)
					free_unit(id)
					set_unit(id,1,x#,z#)
					pos_unit(TCunit,x#,y#,z#)
					If g_player=id Then
						RotateEntity cam,EntityPitch(Tinfo\h),EntityYaw(Tinfo\h),0
					EndIf
					tmp_spawnid=spawnid
					Return 1
				EndIf
			EndIf
		Next
		con_add("ERROR: Missing info '"+Dinfo_name$(1)+"' with ID #"+spawnid+" - spawning player at center",Cbmpf_red)
		free_unit(id)
		set_unit(id,1,0,0)
		tmp_spawnid=0
		Return 0
	EndIf
End Function


Function game_setplayer(id)
	g_player=id
	If Not con_unit(id) Then RuntimeError("Unable to setup player #"+id+" (game_setplayer)")
	g_cplayer.Tunit=TCunit
	col_player_reset()
End Function


Function game_setcam()
	Select m_menu
		Case 0
			If g_player<>0 Then
				h=g_cplayer\h
				typ=g_cplayer\typ
				HideEntity g_cplayer\mh
				If g_drive>0 Then
					If g_cplayer\health#>0. Then
						If con_unit(g_drive) Then
							If TCunit\health_max#>0. Then
								HideEntity h
								PositionEntity h,EntityX(TCunit\h),EntityY(TCunit\h)+Dunit_driveoffset#(TCunit\typ),EntityZ(TCunit\h)
								ShowEntity h
								PositionEntity cam,EntityX(h),EntityY(h)+Dunit_eyes#(typ),EntityZ(h)
								PositionEntity camxz,EntityX(cam),0,EntityZ(cam)
								TCunit\ai_mode=0
								TCunit\ai_duration=5000
								TCunit\ai_timer=gt
								If Sqr((EntityX(TCunit\h)-TCunit\ai_cx#)^2+(EntityZ(TCunit\h)-TCunit\ai_cz#)^2)>200 Then
									TCunit\ai_cx#=EntityX(TCunit\h)
									TCunit\ai_cz#=EntityZ(TCunit\h)
								EndIf
							Else
								g_drive=0
								PositionEntity cam,EntityX(h),EntityY(h)+Dunit_eyes#(typ),EntityZ(h)
								PositionEntity camxz,EntityX(cam),0,EntityZ(cam)
								Return 1
							EndIf
						Else
							g_drive=0
							PositionEntity cam,EntityX(h),EntityY(h)+Dunit_eyes#(typ),EntityZ(h)
							PositionEntity camxz,EntityX(cam),0,EntityZ(cam)
							Return 1
						EndIf
					Else
						g_drive=0
						PositionEntity cam,EntityX(h),EntityY(cam),EntityZ(h)
						TranslateEntity cam,0,-(1.5*f#),0
						y#=e_tery(EntityX(cam),EntityZ(cam))
						If EntityY(cam)<(y#+8) Then
							PositionEntity cam,EntityX(cam),y#+8,EntityZ(cam)
						EndIf
						PositionEntity camxz,EntityX(cam),0,EntityZ(cam)
						Return 1
					EndIf				
				Else
					If g_cplayer\health#>0. Then
						PositionEntity cam,EntityX(h),EntityY(h)+Dunit_eyes#(typ),EntityZ(h)
						PositionEntity camxz,EntityX(cam),0,EntityZ(cam)
						Return 1
					Else
						PositionEntity cam,EntityX(h),EntityY(cam),EntityZ(h)
						TranslateEntity cam,0,-(1.5*f#),0
						y#=e_tery(EntityX(cam),EntityZ(cam))
						If EntityY(cam)<(y#+8) Then
							PositionEntity cam,EntityX(cam),y#+8,EntityZ(cam)
						EndIf
						PositionEntity camxz,EntityX(cam),0,EntityZ(cam)
						Return 1
					EndIf
				EndIf
			EndIf
			Return 0	
		Case Cmenu_if_buildsetup
			If con_unit(g_player) Then
				If EntityY(TCunit\h)<0 Then
					If in_buildsetup_watersurface=0 Then
						If EntityDistance(TCunit\h,cam)<in_buildcamh Then
							TranslateEntity cam,0,(5.*f#),0
							If (EntityPitch(cam)+1.*f)<85 Then RotateEntity cam,EntityPitch(cam)+(1.*f),EntityYaw(cam),EntityRoll(cam)
							PositionEntity camxz,EntityX(cam),0,EntityZ(cam)
							If EntityY(cam)>-1 Then PositionEntity cam,EntityX(cam),-1,EntityZ(cam)
						EndIf
					Else
						If EntityY(cam)<(in_buildcamh*2) Then
							TranslateEntity cam,0,(5.*f#),0
							If (EntityPitch(cam)+1.*f)<85 Then RotateEntity cam,EntityPitch(cam)+(1.*f),EntityYaw(cam),EntityRoll(cam)
							PositionEntity camxz,EntityX(cam),0,EntityZ(cam)
						EndIf
					EndIf
				Else
					If EntityDistance(TCunit\h,cam)<in_buildcamh Then
						TranslateEntity cam,0,(5.*f#),0
						If (EntityPitch(cam)+1.*f)<85 Then RotateEntity cam,EntityPitch(cam)+(1.*f),EntityYaw(cam),EntityRoll(cam)
						PositionEntity camxz,EntityX(cam),0,EntityZ(cam)
					EndIf
				EndIf
			EndIf
		Case Cmenu_if_selplace
			If con_unit(g_player) Then
				If EntityDistance(TCunit\h,cam)<pc_sp_height# Then
					TranslateEntity cam,0,(5.*f#),0
					If (EntityPitch(cam)+1.*f)<85 Then RotateEntity cam,EntityPitch(cam)+(1.*f),EntityYaw(cam),EntityRoll(cam)
					PositionEntity camxz,EntityX(cam),0,EntityZ(cam)
				EndIf
			EndIf
		Case Cmenu_if_movie
			seq_update()
	End Select
	
End Function


Function game_move(speed#,diroff#,climbmax#=3.5)
	speed#=speed#*f#
	h=TCunit\h
	Local yaw#=EntityYaw(h)
	Local mx#=Cos(yaw#+diroff#)*speed#
	Local mz#=Sin(yaw#+diroff#)*speed#
	
	If climbmax#>0. Then
			TranslateEntity h,mx#,0,mz#
			Return 1
	Else
		TranslateEntity h,mx#,0,mz#
		Return 1
	EndIf
	
	Return 0
End Function


Function game_use()
	
	If g_drive>0 Then
		
		p_skipevent=0
		For Tunit.Tunit=Each Tunit
			If Tunit\id=g_drive Then
				set_parsecache(Cclass_unit,Tunit\id,"getoff")
				If Instr(Dunit_scriptk$(Tunit\typ),"getoff") Then
					parse_task(Cclass_unit,Tunit\id,"getoff","",Dunit_script(Tunit\typ))
				EndIf
				parse_sel(Cclass_unit,Tunit\id,"getoff")
				Exit	
			EndIf
		Next
		
		If p_skipevent=0 Then
			If con_unit(g_drive) Then
				ai_mode(ai_idle)
			EndIf
			g_drive=0
		EndIf
		
		Return 1
	
	Else
	
		Local target=CreatePivot()
		PositionEntity target,EntityX(cam),EntityY(cam),EntityZ(cam)
		RotateEntity target,EntityPitch(cam),EntityYaw(cam),EntityRoll(cam)
		MoveEntity target,0,0,Cworld_userange
		EntityPickMode g_cplayer\h,0
		in_pe=0
		in_pe=LinePick(EntityX(cam),EntityY(cam),EntityZ(cam),EntityX(target)-EntityX(cam),EntityY(target)-EntityY(cam),EntityZ(target)-EntityZ(cam),Cworld_useradius)
		in_px#=PickedX()
		in_py#=PickedY()
		in_pz#=PickedZ()
		EntityPickMode g_cplayer\h,2,1
		If in_pe=0 Or in_pe=ter Then
			EntityPickMode env_sea2,2,1
			EntityPickMode g_cplayer\h,0
			in_pe=0
			in_pe=LinePick(EntityX(cam),EntityY(cam),EntityZ(cam),EntityX(target)-EntityX(cam),EntityY(target)-EntityY(cam),EntityZ(target)-EntityZ(cam),Cworld_useradius)
			in_px#=PickedX()
			in_py#=PickedY()
			in_pz#=PickedZ()
			EntityPickMode env_sea2,0
			EntityPickMode g_cplayer\h,2,1
		EndIf
		FreeEntity target
		pc_use_x#=in_px#
		pc_use_y#=in_py#
		pc_use_z#=in_pz#
		
	EndIf
		
	
	If in_pe=0 Then
		Return 0
	EndIf
	
	If in_pe=ter Then
		parse_globalevent("useground")
		Return 1
	EndIf
	
	If in_pe=env_sea2 Then
		parse_globalevent("usesea")
		Return 1
	EndIf
	
	x=get_item(in_pe)
	If x>-1 Then
		p_skipevent=0
		set_parsecache(Cclass_item,x,"collect")
		If Instr(Ditem_scriptk$(TCitem\typ),"collect") Then
			parse_task(Cclass_item,x,"collect","",Ditem_script(TCitem\typ))
		EndIf
		parse_sel(Cclass_item,x,"collect")
		If p_skipevent=0 Then
			If Handle(TCitem)<>0 Then
				stored=0
				itemtyp=TCitem\typ
				oldcount=countstored_items(Cclass_unit,g_player,itemtyp,0)
				stored=store_item(x,Cclass_unit,g_player)
				count=countstored_items(Cclass_unit,g_player,itemtyp,0)
				If stored<>0 Then
					p_add2d(set_scrx/2,set_scry/2,-2,itemtyp)
					if_msg(ss$(sm$(180),Ditem_name$(itemtyp),(count-oldcount)) ,Cbmpf_green)
					sfx sfx_collect
				Else
					if_msg(sm$(181),Cbmpf_red)
					sfx sfx_fail
					speech("nospace")
				EndIf
			EndIf
		EndIf
		Return 1
	EndIf
	
	x=get_object(in_pe)
	If x>-1 Then
		For Titem.Titem=Each Titem
			If Titem\parent_mode=Cpm_out Then
				If Titem\parent_class=Cclass_object Then
					If Titem\parent_id=x Then
					
						EntityPickMode TCobject\h,0
						
						target=CreatePivot()
						PositionEntity target,EntityX(cam),EntityY(cam),EntityZ(cam)
						RotateEntity target,EntityPitch(cam),EntityYaw(cam),EntityRoll(cam)
						MoveEntity target,0,0,Cworld_userange
						EntityPickMode g_cplayer\h,0
						in_pe=0
						in_pe=LinePick(EntityX(cam),EntityY(cam),EntityZ(cam),EntityX(target)-EntityX(cam),EntityY(target)-EntityY(cam),EntityZ(target)-EntityZ(cam),Cworld_useradius)
						in_px#=PickedX()
						in_py#=PickedY()
						in_pz#=PickedZ()
						EntityPickMode g_cplayer\h,2,1
						
						If Dobject_col(TCobject\typ)>0 Then
							EntityPickMode TCobject\h,2,1
						EndIf
						
						x2=get_item(in_pe)
						If x2>-1 Then
							p_skipevent=0
							set_parsecache(Cclass_item,x2,"collect")
							If Instr(Ditem_scriptk$(TCitem\typ),"collect") Then
								parse_task(Cclass_item,x2,"collect","",Ditem_script(TCitem\typ))
							EndIf
							parse_sel(Cclass_item,x2,"collect")
							If p_skipevent=0 Then
								If Handle(TCitem)<>0 Then
									stored=0
									itemtyp=TCitem\typ
									oldcount=countstored_items(Cclass_unit,g_player,itemtyp,0)
									stored=store_item(x2,Cclass_unit,g_player)
									count=countstored_items(Cclass_unit,g_player,itemtyp,0)
									If stored<>0 Then
										p_add2d(set_scrx/2,set_scry/2,-2,itemtyp)
										if_msg(ss$(sm$(180),Ditem_name$(itemtyp),(count-oldcount)),Cbmpf_green)
										sfx sfx_collect
									Else
										if_msg(sm$(181),Cbmpf_red)
										sfx sfx_fail
									EndIf
								EndIf
							EndIf
							Return 1
						EndIf
						
						Exit
					EndIf
				EndIf
			EndIf
		Next
		use_object(x)
		Return 1
	EndIf
	
	x=get_unit(in_pe)
	If x>-1 Then
		use_unit(x)
		Return 1
	EndIf
	
End Function


Function game_useasweapon(typ)
	If typ=-1 Then
		con_unit(g_player)
		TCunit\player_weapon=0
		TCunit\player_ammo=0
		Return 1
	EndIf
	If typ=0 Then Return 0
	con_unit(g_player)
	For Titem.Titem=Each Titem
		If Titem\typ=typ Then
			If Titem\parent_class=Cclass_unit
				If Titem\parent_id=g_player Then
					TCunit\player_weapon=typ
					TCunit\player_ammo=0
					game_ammo(g_player)
					If Instr(Ditem_scriptk$(typ),"inhand") Then
						parse_task(Cclass_item,Titem\id,"inhand","",Ditem_script(typ))
					EndIf
					Return 1
				EndIf
			EndIf
		EndIf
	Next
	Return 0
End Function


Function game_scrollweapon(dir)
	Local i,current,oldweapon
	con_unit(g_player)
	If TCunit\player_weapon=0 Then
		If dir=1 Then
			For i=1 To 9
				If game_useasweapon(in_quickslot(i)) Then Return 1
			Next
		Else
			For i=9 To 1 Step -1
				If game_useasweapon(in_quickslot(i)) Then Return 1
			Next
		EndIf
		Return 0
	EndIf
	oldweapon=TCunit\player_weapon
	If dir=1 Then
		For i=1 To 9
			If in_quickslot(i)=oldweapon Then current=i:Exit
		Next
		current=current+1
		For i=current To 9
			If game_useasweapon(in_quickslot(i)) Then Return 1
		Next
		If oldweapon<>0 Then
			game_useasweapon(-1)
			Return 1
		EndIf
		For i=1 To 9
			If game_useasweapon(in_quickslot(i)) Then Return 1
		Next
	Else
		For i=1 To 9
			If in_quickslot(i)=oldweapon Then current=i:Exit
		Next
		current=current-1
		If current>0 Then
			For i=current To 1 Step -1
				If game_useasweapon(in_quickslot(i)) Then Return 1
			Next
		EndIf
		If oldweapon<>0 Then
			game_useasweapon(-1)
			Return 1
		EndIf
		For i=9 To 1 Step -1
			If game_useasweapon(in_quickslot(i)) Then Return 1
		Next
	EndIf
End Function


Function game_attack1()
	con_unit(g_player)
	
	If TCunit\player_weapon=0 Then
		p_skipevent=0
		set_parsecache(Cclass_unit,TCunit\id,"attack1")
		If Instr(Dunit_scriptk$(TCunit\typ),"attack1") Then
			parse_task(Cclass_unit,TCunit\id,"attack1","",Dunit_script(TCunit\typ))
		EndIf
		parse_sel(Cclass_unit,TCunit\id,"attack1")
		If p_skipevent=0 Then
			If game_wpn_none(g_player) Then sfx_emit(sfx_swing_slow,cam)
		EndIf
	
	Else
		
		wexists=0
		For Titem.Titem=Each Titem
			If Titem\typ=TCunit\player_weapon Then
				If Titem\parent_class=Cclass_unit
					If Titem\parent_id=g_player Then
						wexists=1
						Exit
					EndIf
				EndIf
			EndIf
		Next
		If wexists=0 Then Return 0
		
		speed=Ditem_rate(TCunit\player_weapon)
		If gt-TCunit\player_lastattack<speed Then Return 0
		
		p_skipevent=0
		If get_stored_item(TCunit\player_weapon,Cclass_unit,g_player) Then
			set_parsecache(Cclass_item,TCitem\id,"attack1")
			If Instr(Ditem_scriptk$(TCitem\typ),"attack1") Then
				parse_task(Cclass_item,TCitem\id,"attack1","",Ditem_script(TCitem\typ))
			EndIf
			parse_sel(Cclass_item,TCitem\id,"attack1")
		EndIf
		parse_emuevent(Cclass_unit,g_player,"attack1")

		If p_skipevent=0 Then
			Select Ditem_behaviour$(TCunit\player_weapon)
				
				Case "useenv" game_use()
								
				Case "hammer" If game_wpn_blade(g_player) Then sfx_emit(sfx_swing_slow,cam)
				
				Case "spade" If game_wpn_blade(g_player) Then sfx_emit(sfx_swing_slow,cam)
				
				Case "net" If game_wpn_blade(g_player) Then sfx_emit(sfx_swing_slow,cam)
				
				Case "fishingrod" If game_wpn_blade(g_player) Then sfx_emit(sfx_swing_slow,cam)
				
				Case "torch" If game_wpn_blade(g_player) Then sfx_emit(sfx_swing_slow,cam)
				
				Case "slowblade" If game_wpn_blade(g_player) Then sfx_emit(sfx_swing_slow,cam)
				Case "blade" If game_wpn_blade(g_player) Then sfx_emit(sfx_swing_slow,cam)
				Case "fastblade" If game_wpn_blade(g_player) Then sfx_emit(sfx_swing_fast,cam)
				
				Case "slingshot" If game_wpn_distance(g_player) Then sfx_emit(sfx_swing_fast,cam) 
				Case "bow" If game_wpn_distance(g_player) Then sfx_emit(sfx_swing_fast,cam) 
				Case "launcher"
					If game_wpn_distance(g_player) Then
						sfx_emit(sfx_launch,cam)
						If set_effects>0 Then
							For i=1 To 5
								p_add(EntityX(cam)+Rnd(-3,3),EntityY(cam)+Rnd(-3,3),EntityZ(cam)+Rnd(-3,3),Cp_smoke,Rnd(5,7),Rnd(0.4,0.7))
							Next
						EndIf
					EndIf
				Case "catapult" If game_wpn_distance(g_player) Then sfx_emit(sfx_swing_fast,cam)
				
				Case "pistol" If game_wpn_distancef(g_player) Then sfx_emit(sfx_pistol,cam)
				Case "gun" game_wpn_distancef(g_player)
				Case "machinegun" game_wpn_distancef(g_player)
				
				Case "selfthrow" game_wpn_selfthrow(g_player)
				Case "spear" If game_wpn_selfthrow(g_player) Then sfx_emit(sfx_swing_fast,cam)
				Case "killthrow" If game_wpn_selfthrow(g_player) Then sfx_emit(sfx_swing_fast,cam) 
				Case "throw" If game_wpn_selfthrow(g_player) Then sfx_emit(sfx_swing_fast,cam)
				
				Default
				
					If Instr(Ditem_behaviour$(TCunit\player_weapon),"selfthrow") Then
						game_wpn_selfthrow(g_player)
					ElseIf Instr(Ditem_behaviour$(TCunit\player_weapon),"spear") Then
						If game_wpn_selfthrow(g_player) Then sfx_emit(sfx_swing_fast,cam)
					ElseIf Instr(Ditem_behaviour$(TCunit\player_weapon),"killthrow") Then
						If game_wpn_selfthrow(g_player) Then sfx_emit(sfx_swing_fast,cam)
					ElseIf Instr(Ditem_behaviour$(TCunit\player_weapon),"throw") Then
						If game_wpn_selfthrow(g_player) Then sfx_emit(sfx_swing_fast,cam)
					Else
						If in_keyhit(Ckey_attack1) Then
							For Titem.Titem=Each Titem
								If Titem\parent_class=Cclass_unit Then
									If Titem\parent_id=g_player Then
										If Titem\typ=TCunit\player_weapon Then
											use_item(Titem\id)
										EndIf
									EndIf
								EndIf
							Next
						EndIf
					
					EndIf
				
			End Select
		EndIf
	EndIf
End Function


Function game_attack2()
	con_unit(g_player)
	
	If TCunit\player_weapon=0 Then
		game_use()
		parse_emuevent(Cclass_unit,g_player,"attack2")
	
	Else

		wexists=0
		For Titem.Titem=Each Titem
			If Titem\typ=TCunit\player_weapon Then
				If Titem\parent_class=Cclass_unit
					If Titem\parent_id=g_player Then
						wexists=1
						Exit
					EndIf
				EndIf
			EndIf
		Next
		If wexists=0 Then Return 0
		
		p_skipevent=0
		If get_stored_item(TCunit\player_weapon,Cclass_unit,g_player) Then
			set_parsecache(Cclass_item,TCitem\id,"attack2")
			If Instr(Ditem_scriptk$(TCitem\typ),"attack2") Then
				parse_task(Cclass_item,TCitem\id,"attack2","",Ditem_script(TCitem\typ))
			EndIf
			parse_sel(Cclass_item,TCitem\id,"attack2")
		EndIf
		parse_emuevent(Cclass_unit,g_player,"attack2")
	
		If p_skipevent=0 Then
			Select Ditem_behaviour$(TCunit\player_weapon)
			
				Case "hammer" game_build()
				
				Case "spade"
					If pc_typ=0 Then
						pc_typ=Cstate_pc_dig
						pc_gt=gt
						in_focus=pc_typ
					EndIf
				
				Case "fishingrod"
					If pc_typ=0 Then
						pc_typ=Cstate_pc_fish
						pc_gt=gt
						in_focus=pc_typ
						sfx sfx_fish
					EndIf
					
				Case "net"
					game_catch(Ditem_speed#(TCunit\player_weapon))
				
				Case "slingshot" game_ammo(g_player)
				Case "bow" game_ammo(g_player)
				Case "launcher"	game_ammo(g_player)
				Case "catapult"	game_ammo(g_player)
				
				Case "pistol" game_ammo(g_player)
				Case "gun" game_ammo(g_player)
				Case "machinegun" game_ammo(g_player)
			
			End Select
		EndIf
	EndIf
End Function


Function game_ammo(id)
	con_unit(id)
	Local wpn$="ammo:"+Str(TCunit\player_weapon)
	For Tcache.Tcache=Each Tcache
		Delete Tcache
	Next
	Local c=0
	For Titem.Titem=Each Titem
		If Titem\parent_class=Cclass_unit Then
			If Titem\parent_id=id Then
				If Instr(Ditem_behaviour$(Titem\typ),wpn$)>0 Then
					tmp$=Ditem_behaviour$(Titem\typ)
					tmp$=Replace(tmp$," ",",")
					tmp$=Replace(tmp$,";",",")
					tmp$=tmp$+","
					If Instr(tmp$,wpn$+",")>0 Then
						Tcache.Tcache=New Tcache
						Tcache\id=Titem\typ
						c=c+1
					EndIf
				EndIf
			EndIf
		EndIf
	Next
	If c=0 Then
		TCunit\player_ammo=0
		Return 0
	EndIf
	If TCunit\player_ammo=0 Then
		Tcache.Tcache=First Tcache
		TCunit\player_ammo=Tcache\id
		Return 1
	Else
		If Instr(Ditem_behaviour$(TCunit\player_ammo),wpn$)>0 Then
			Local foundincache=0
			For Tcache.Tcache=Each Tcache
				If foundincache=1 Then
					TCunit\player_ammo=Tcache\id
					Return 1
				Else
					If Tcache\id=TCunit\player_ammo Then foundincache=1
				EndIf
			Next
		Else
			Tcache.Tcache=First Tcache
			TCunit\player_ammo=Tcache\id
			Return 1
		EndIf
		Tcache.Tcache=First Tcache
		TCunit\player_ammo=Tcache\id
		Return 1
	EndIf
End Function


Function game_ammo_bytype(typ)
	If Handle(g_cplayer)<>0 Then
		Local wpn$="ammo:"+Str(g_cplayer\player_weapon)
		For Titem.Titem=Each Titem
			If Titem\typ=typ Then
				If Titem\parent_class=Cclass_unit Then
					If Titem\parent_id=g_cplayer\id Then
						If Instr(Ditem_behaviour$(Titem\typ),wpn$) Then
							tmp$=Ditem_behaviour$(Titem\typ)
							tmp$=Replace(tmp$," ",",")
							tmp$=Replace(tmp$,";",",")
							tmp$=tmp$+","
							If Instr(tmp$,wpn$+",")>0 Then
								g_cplayer\player_ammo=typ
								Return 1
							EndIf
						EndIf
					EndIf
				EndIf
			EndIf
		Next
	EndIf
	Return 0
End Function


Function game_weapon_bytype(typ)
	If Handle(g_cplayer)<>0 Then
		For Titem.Titem=Each Titem
			If Titem\typ=typ Then
				If Titem\parent_class=Cclass_unit Then
					If Titem\parent_id=g_cplayer\id Then
						g_cplayer\player_weapon=typ
						Return 1
					EndIf
				EndIf
			EndIf
		Next
	EndIf
	Return 0
End Function


Function game_picking(mode)
	If mode=1 Then
		For Tobject.Tobject=Each Tobject
			If Dobject_col(Tobject\typ)>0 Then
				EntityPickMode Tobject\h,2,1
			EndIf
		Next
		For Tunit.Tunit=Each Tunit
			EntityPickMode Tunit\mh,2,1
		Next
		For Titem.Titem=Each Titem
			If Ditem_radius#(Titem\typ)=0. Then
				EntityPickMode Titem\h,2,1
			Else
				EntityPickMode Titem\h,1,1
			EndIf
		Next
	Else
		For Tobject.Tobject=Each Tobject
			EntityPickMode Tobject\h,0,0
		Next
		For Tunit.Tunit=Each Tunit
			EntityPickMode Tunit\mh,0,0
		Next
		For Titem.Titem=Each Titem
			EntityPickMode Titem\h,0,0
		Next
	EndIf
End Function


Function game_damage(entity,damage#,causer,pickcords=0,bycollisionpivot=0)
	tmp_kill=0
	If get_item(entity)>-1 Then
		If causer=g_player Then
			set_parsecache(Cclass_item,TCitem\id,"hit")
			If Instr(Ditem_scriptk$(TCitem\typ),"hit") Then
				parse_task(Cclass_item,TCitem\id,"hit","",Ditem_script(TCitem\typ))
			EndIf
		EndIf
		If Handle(TCitem)<>0 Then
			tmp_class=Cclass_item
			tmp_id=TCitem\id
			damage_item(damage#,0,pickcords)
		EndIf
		Return 1
	ElseIf get_unit(entity,bycollisionpivot)>-1 Then
		If causer=g_player Then
			set_parsecache(Cclass_unit,TCunit\id,"hit")
			If Instr(Dunit_scriptk$(TCunit\typ),"hit") Then
				parse_task(Cclass_unit,TCunit\id,"hit","",Dunit_script(TCunit\typ))
			EndIf
		EndIf
		If Handle(TCunit)<>0 Then
			tmp_class=Cclass_unit
			tmp_id=TCunit\id
			hurt_unit(damage#,0,pickcords)
		EndIf
		Return 1
	ElseIf get_object(entity)>-1 Then
		If causer=g_player Then
			set_parsecache(Cclass_object,TCobject\id,"hit")
			If Instr(Dobject_scriptk$(TCobject\typ),"hit") Then
				parse_task(Cclass_object,TCobject\id,"hit","",Dobject_script(TCobject\typ))
			EndIf
		EndIf
		If Handle(TCobject)<>0 Then
			tmp_class=Cclass_object
			tmp_id=TCobject\id
			damage_object(damage#,0,pickcords)
		EndIf
		Return 1
	EndIf
	Return 0
End Function


Function game_falldamage()
	Local dif#=TCunit\phy_fally#-EntityY(TCunit\h)
	If dif#>game_fallminy# Then
		Local perc#=dif#-game_fallminy#
		Local max#=game_fallmaxy#-game_fallminy#
		damage#=(perc#/max#)*game_falldamage#
		hurt_unit(damage#)
	EndIf
End Function



Function game_sleep()
	parse_globalevent("sleep")
	tmp_skipevent=0
	parse_sel_event("sleep")
	
	If tmp_skipevent=0 Then
	
		g_sleep=1
	
		If map_timeupdate=0 Then
			map_lday=map_day
			map_lhour=map_hour
			map_lminute=map_minute
			If map_lminute>=60 Then
				map_lminute=0
				map_lhour=map_lhour+1
				If map_lhour>=24 Then
					map_lhour=0
					map_lday=map_lday+1
				EndIf
			EndIf
			map_timeupdate=1
		EndIf
		in_tgametime=gt
		If Not map_freezetime Then
			If map_hour>=12 Then
				map_hour=7
				map_minute=0
				game_cd()
				map_day=map_day+1
			Else
				map_hour=map_hour+6
			EndIf
		Else
			map_day=map_day+1
		EndIf
					
		p_add(0,0,0,Cp_flash,0.01,1.5)
		EntityColor TCp\h,0,0,0
		
		sfx sfx_sleep
		
		e_environment_update_light()
		
	EndIf
	
End Function


Function game_exhaust(hunger#,thirst#,exhaustion#)
	TCunit\hunger#=TCunit\hunger#+hunger#
	TCunit\thirst#=TCunit\thirst#+thirst#
	TCunit\exhaustion#=TCunit\exhaustion#+exhaustion#
	If TCunit\hunger#>Dunit_store#(TCunit\typ) Then TCunit\hunger#=Dunit_store#(TCunit\typ)
	If TCunit\thirst#>Dunit_store#(TCunit\typ) Then TCunit\thirst#=Dunit_store#(TCunit\typ)
	If TCunit\exhaustion#>Dunit_store#(TCunit\typ) Then TCunit\exhaustion#=Dunit_store#(TCunit\typ)
End Function


Function game_dig(range=100)
	skip=0
	con_add("DIG")
	For Tinfo.Tinfo=Each Tinfo
		If EntityDistance(Tinfo\h,cam)<range Then
			skip=set_parsecache(Cclass_info,Tinfo\id,"dig")
		EndIf
		If Tinfo\typ=42 Then
			If (dist#(EntityX(Tinfo\h),EntityZ(Tinfo\h),EntityX(cam),EntityZ(cam))-Tinfo\floats[0])<range Then
				If skip=0 Then
					skip=set_parsecache(Cclass_info,Tinfo\id,"dig")
				EndIf
				If item_getrandom(Cclass_info,Tinfo\id) Then
					p=CreatePivot()
					PositionEntity p,EntityX(cam),EntityY(cam),EntityZ(cam)
					RotateEntity p,0,EntityYaw(cam),0
					MoveEntity p,0,0,50
					stored=0
					If unstore_item(TCitem\id,1,0,EntityX(p),e_tery(EntityX(p),EntityZ(p))+20,EntityZ(p))
						If Handle(TCitem)<>0 Then
							itemtyp=TCitem\typ
							count=TCitem\count
							If store_item(TCitem\id,Cclass_unit,g_player)<>0 Then
								p_add2d(set_scrx/2,set_scry/2,-2,itemtyp)
								if_msg(ss$(sm$(180),Ditem_name$(itemtyp),count),Cbmpf_green)
								sfx sfx_collect
								stored=1
							EndIf
						EndIf
					EndIf
					FreeEntity p
					If stored=0 Then
						if_msg(sm$(181),Cbmpf_red)
						sfx sfx_fail
					EndIf
				EndIf
				skip=1
			EndIf
		EndIf
		If skip Then Return 1
	Next
	con_add("DIG NOINFO")
	For Tobject.Tobject=Each Tobject
		If EntityDistance(Tobject\h,cam)<range Then
			skip=set_parsecache(Cclass_object,Tobject\id,"dig")
			If Instr(Dobject_scriptk$(Tobject\typ),"dig") Then
				parse_task(Cclass_object,Tobject\id,"dig","",Dobject_script(Tobject\typ))
				skip=1
			EndIf
			If skip Then Return 1
		EndIf
	Next
	con_add("DIG NOOBJECT")
	For Tunit.Tunit=Each Tunit
		If EntityDistance(Tunit\h,cam)<range Then
			skip=set_parsecache(Cclass_unit,Tunit\id,"dig")
			If Instr(Dunit_scriptk$(Tunit\typ),"dig") Then
				parse_task(Cclass_unit,Tunit\id,"dig","",Dunit_script(Tunit\typ))
				skip=1
			EndIf
			If skip Then Return 1
		EndIf
	Next
	con_add("DIG NOUNIT")
	For Titem.Titem=Each Titem
		If Titem\parent_mode<>Cpm_in Then
			If EntityDistance(Titem\h,cam)<range Then
				skip=set_parsecache(Cclass_item,Titem\id,"dig")
				If Instr(Ditem_scriptk$(Titem\typ),"dig") Then
					parse_task(Cclass_item,Titem\id,"dig","",Ditem_script(Titem\typ))
					skip=1
				EndIf
				If skip Then Return 1
			EndIf
		EndIf
	Next
	con_add("DIG NOITEM")
	parse_globalevent("dig_failure")
	Return 0
End Function


Function game_fish(range=150)
	For Tinfo.Tinfo=Each Tinfo
		If EntityDistance(Tinfo\h,cam)<range Then
			skip=set_parsecache(Cclass_info,Tinfo\id,"fish")
		EndIf
		If Tinfo\typ=43 Then
			If (dist#(EntityX(Tinfo\h),EntityZ(Tinfo\h),EntityX(cam),EntityZ(cam))-Tinfo\floats[0])<range Then
				If item_getrandom(Cclass_info,Tinfo\id) Then
					p=CreatePivot()
					PositionEntity p,EntityX(cam),EntityY(cam),EntityZ(cam)
					RotateEntity p,0,EntityYaw(cam),0
					MoveEntity p,0,0,50
					stored=0
					If unstore_item(TCitem\id,1,0,EntityX(p),e_tery(EntityX(p),EntityZ(p))+20,EntityZ(p))
						If Handle(TCitem)<>0 Then
							itemtyp=TCitem\typ
							count=TCitem\count
							If store_item(TCitem\id,Cclass_unit,g_player)<>0 Then
								p_add2d(set_scrx/2,set_scry/2,-2,itemtyp)
								if_msg(ss$(sm$(180),Ditem_name$(itemtyp),count),Cbmpf_green)
								sfx sfx_collect
								stored=1
							EndIf
						EndIf
					EndIf
					FreeEntity p
					If stored=0 Then
						if_msg(sm$(181),Cbmpf_red)
						sfx sfx_fail
					EndIf
				EndIf
				skip=1
			EndIf
		EndIf
		If skip Then Return 1
	Next
	For Tobject.Tobject=Each Tobject
		If EntityDistance(Tobject\h,cam)<range Then
			skip=set_parsecache(Cclass_object,Tobject\id,"fish")
			If Instr(Dobject_scriptk$(Tobject\typ),"fish") Then
				parse_task(Cclass_object,Tobject\id,"fish","",Dobject_script(Tobject\typ))
				skip=1
			EndIf
			If skip Then Return 1
		EndIf
	Next
	For Tunit.Tunit=Each Tunit
		If EntityDistance(Tunit\h,cam)<range Then
			skip=set_parsecache(Cclass_unit,Tunit\id,"fish")
			If Instr(Dunit_scriptk$(Tunit\typ),"fish") Then
				parse_task(Cclass_unit,Tunit\id,"fish","",Dunit_script(Tunit\typ))
				skip=1
			EndIf
			If skip Then Return 1
		EndIf
	Next
	For Titem.Titem=Each Titem
		If EntityDistance(Titem\h,cam)<range Then
			If Titem\parent_mode<>Cpm_in Then
				skip=set_parsecache(Cclass_item,Titem\id,"fish")
				If Instr(Ditem_scriptk$(Titem\typ),"fish") Then
					parse_task(Cclass_item,Titem\id,"fish","",Ditem_script(Titem\typ))
					skip=1
				EndIf
				If skip Then Return 1
			EndIf
		EndIf
	Next
	Local success=0
	EntityPickMode g_cplayer\h,0
	EntityPickMode env_sea2,2,1
	h=CameraPick (cam,set_scrx/2,set_scry/2)
	If h=env_sea2 Then
		p=CreatePivot()
		PositionEntity p,PickedX(),PickedY(),PickedZ()
		If EntityDistance(p,cam)<=100 Then
			success=1
		EndIf
		FreeEntity p
	EndIf
	If success=0 Then
		Local c=CreateCamera()
		PositionEntity c,EntityX(cam),EntityY(cam),EntityZ(cam)
		For i=1 To 4
			RotateEntity c,0,EntityYaw(cam),0
			MoveEntity c,0,0,25
			tery=TerrainY(ter,EntityX(c),EntityY(c),EntityZ(c))
			If tery<0 Then tery=0
			If EntityY(c)<tery Then
				PositionEntity c,EntityX(c),tery+5,EntityZ(c)
			EndIf
			RotateEntity c,90,0,0
			h=CameraPick (c,set_scrx/2,set_scry/2)
			If h=env_sea2 Then
				success=1
				Exit
			EndIf
		Next
		FreeEntity c
	EndIf
	EntityPickMode env_sea2,0
	EntityPickMode g_cplayer\h,2,1
	If success=1 Then
		parse_globalevent("fish_success")
	Else
		parse_globalevent("fish_failure")
	EndIf
	Return 0
End Function

Function game_catch(range#=200)
	con_unit(g_player)
	If gt-TCunit\player_lastattack<Ditem_rate(TCunit\player_weapon) Then Return 0
	TCunit\player_lastattack=gt
	
	Local target=CreatePivot()
	PositionEntity target,EntityX(cam),EntityY(cam),EntityZ(cam)
	RotateEntity target,EntityPitch(cam),EntityYaw(cam),EntityRoll(cam)
	MoveEntity target,0,0,range#
	EntityPickMode g_cplayer\h,0
	EntityPickMode env_sea2,0
	in_pe=0
	in_pe=LinePick(EntityX(cam),EntityY(cam),EntityZ(cam),EntityX(target)-EntityX(cam),EntityY(target)-EntityY(cam),EntityZ(target)-EntityZ(cam),Cworld_useradius)
	in_px#=PickedX()
	in_py#=PickedY()
	in_pz#=PickedZ()
	FreeEntity target
	EntityPickMode g_cplayer\h,2,1
	
	sfx_emit(sfx_swing_slow,cam)
	
	p_add(Rand(-1,2),0,0,Cp_attack,12,0.5)
	
	If in_pe=ter Then Return 0
	
	skip=0
	
	If in_pe<>0 Then	
		If ha_getclass(in_pe,0) Then
			
			Select tmp_class
				Case Cclass_object
					Tobject.Tobject=TCobject
					skip=set_parsecache(Cclass_object,Tobject\id,"catch")
					If Instr(Dobject_scriptk$(Tobject\typ),"catch") Then
						parse_task(Cclass_object,Tobject\id,"catch","",Dobject_script(Tobject\typ))
						skip=1
					EndIf
					If skip Then Return 1
				Case Cclass_unit
					Tunit.Tunit=TCunit
					skip=set_parsecache(Cclass_unit,Tunit\id,"catch")
					If Instr(Dunit_scriptk$(Tunit\typ),"catch") Then
						parse_task(Cclass_unit,Tunit\id,"catch","",Dunit_script(Tunit\typ))
						skip=1
					EndIf
					If skip Then Return 1
				Case Cclass_item
					Titem.Titem=TCitem
					skip=set_parsecache(Cclass_item,Titem\id,"catch")
					If Instr(Ditem_scriptk$(Titem\typ),"catch") Then
						parse_task(Cclass_item,Titem\id,"catch","",Ditem_script(Titem\typ))
						skip=1
					EndIf
					If skip Then Return 1
			End Select		
		EndIf
	EndIf
	parse_globalevent("catch_failure")
	Return 0
End Function


Function game_explosion(x#,y#,z#,range#=50.,damage#=50.,style=1)
	Local p=CreatePivot()
	PositionEntity p,x#,y#,z#
	For Tobject.Tobject=Each Tobject
		If EntityDistance(Tobject\h,p)<range# Then
			TCobject=Tobject
			damage_object(damage#,0,0)
		EndIf
	Next
	For Tunit.Tunit=Each Tunit
		If EntityDistance(Tunit\h,p)<range# Then
			TCunit=Tunit
			hurt_unit(damage#,0,0)
		EndIf
	Next
	For Titem.Titem=Each Titem
		If EntityDistance(Titem\h,p)<range# Then
			If Titem\parent_mode<>Cpm_in Then
				TCitem=Titem
				damage_item(damage#,0,0)
			EndIf
		EndIf
	Next
	size#=range#/10
	Select style
		Case 0
		Case 1
			p_explosion(x#,y#,z#,size#)
		Case 2
		Case 3
			For i=1 To 10
				If Rand(3)=1 Then
					p_add(x#+Rnd(-size#*3.,size#*3.),y#+Rnd(size#*3.),z#+Rnd(-size#*3.,size#*3.),Cp_smoke,Rnd(10,30),Rnd(0.9,1.5))
					EntityColor TCp\h,256,Rand(128,256),Rand(32,128)
					TCp\a#=TCp\fadein#
					TCp\fadein#=0
					EntityAlpha TCp\h,TCp\a#
				EndIf
				p_add(x#+Rnd(-size#*3.,size#*3.),y#+Rnd(size#*3.),z#+Rnd(-size#*3.,size#*3.),Cp_spark,Rand(2,3),3)
				EntityColor TCp\h,128,Rand(64,128),Rand(0,64)
				EntityBlend TCp\h,1
				p_add(x#+Rnd(-size#*3.,size#*3.),y#+Rnd(-size#*3.,size#*3.),z#+Rnd(-size#*3.,size#*3.),Cp_firespark,Rand(0.5,1.5),1)
				EntityColor TCp\h,128,Rand(64,128),Rand(0,64)
				EntityBlend TCp\h,1
			Next
			sfx_3d(x#,y#,z#,sfx_pang)
	End Select
	FreeEntity p
End Function


Function game_target(range#=10000)
	tmp_tarclass=0
	tmp_tarid=0
	tmp_tarx#=0
	tmp_tary#=0
	tmp_tarz#=0
	
	If g_cplayer=Null Then Return 0
	
	Local h=CreatePivot()
	RotateEntity h,EntityPitch(cam),EntityYaw(cam),0
	PositionEntity h,EntityX(cam),EntityY(cam),EntityZ(cam)
	
	in_pe=0
	EntityPickMode env_sea2,2,1
	EntityPickMode g_cplayer\h,0
	in_pe=EntityPick(h,range#)
	EntityPickMode g_cplayer\h,2,1
	EntityPickMode env_sea2,0
	in_px#=PickedX()
	in_py#=PickedY()
	in_pz#=PickedZ()
	
	If in_pe<>0 Then
		tmp_tarx#=in_px#
		tmp_tary#=in_py#
		tmp_tarz#=in_pz#
		
		If in_pe=env_ground Or in_pe=ter Then
			tmp_tarclass=-1
		ElseIf in_pe=env_sea2 Then
			tmp_tarclass=-2
		ElseIf get_item(in_pe)>-1 Then
			tmp_tarclass=Cclass_item
			tmp_tarid=TCitem\id
		ElseIf get_unit(in_pe,0)>-1 Then
			tmp_tarclass=Cclass_unit
			tmp_tarid=TCunit\id
		ElseIf get_object(in_pe)>-1 Then
			tmp_tarclass=Cclass_object
			tmp_tarid=TCobject\id
		EndIf
		
		Return 1
	Else
		Return 0
	EndIf
End Function


Function game_renderweapon()
	free=1
	If m_menu=0 Then
		If g_player<>0 Then
			h=g_cplayer\h
			If g_cplayer\player_weapon<>0 Then
				tmp_x#=EntityX(h)
				tmp_y#=EntityY(h)
				tmp_z#=EntityZ(h)
				If countstored_items(Cclass_unit,g_player,g_cplayer\player_weapon)>0 Then
					Select Ditem_behaviour$(g_cplayer\player_weapon)
						Case "torch"
							If tmp_y#>0 Then
								If env_cweather<1 Or env_cweather>2 Then
									free=0
									If env_itemlight=0 Then env_itemlight=CreateLight(2)
									LightColor env_itemlight,70,Rnd(45,70),0
									LightRange env_itemlight,Rnd(300,305)
									PositionEntity env_itemlight,tmp_x#,tmp_y#,tmp_z#
									If in_gt100go Then
										If Rand(5)=1 Then
											p_add(tmp_x#,tmp_y#,tmp_z#,Cp_firespark,Rand(2,6),1)
										EndIf
										If in_gt1000go Then
											If Rand(1)=1 Then
												p_add(tmp_x#,tmp_y#,tmp_z#,Cp_spark,Rand(1,2),3)
											EndIf
										EndIf
									EndIf
									If Not ChannelPlaying(env_itemchan) Then
										env_itemchan=sfx_emit(sfx_fire,h,0.5)
									EndIf
								EndIf
							EndIf
					End Select
				EndIf
			EndIf
		EndIf
	EndIf
	If free=1 Then
		If env_itemlight<>0 Then
			FreeEntity env_itemlight
			env_itemlight=0
		EndIf
		If ChannelPlaying(env_itemchan) Then
			StopChannel env_itemchan
		EndIf
	EndIf
End Function