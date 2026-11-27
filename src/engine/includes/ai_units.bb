Function ai_getbehaviour(behaviour$)
	Select Lower(behaviour$)
		Case "","player" Return 0
		
		Case "normal","animal" Return 1
		Case "raptor","predator" Return 2
		Case "standandsnap" Return 3
		Case "idle" Return 4
		Case "idleturn" Return 5
		Case "standandshoot" Return 6
		Case "monkey" Return 7
		Case "shy" Return 8
		Case "plague" Return 9
		Case "landbird" Return 10
		
		Case "crab" Return 200
		Case "amphibian" Return 201
		
		Case "fish" Return 300
		Case "circlingfish" Return 301
		Case "predatorfish" Return 302
		Case "deepseafish" Return 303
		
		Case "bird" Return 400
		Case "circlingbird" Return 401
		Case "predatorbird" Return 402
		Case "flyinginsect" Return 404
		Case "highbird" Return 405
		Case "lowbird" Return 406
		Case "landskybird" Return 407
		Case "killerbird" Return 408
		
		Case "vehicle" Return 500
		Case "watercraft" Return 501
		Case "aircraft" Return 502
		
		Default
			If set_debug Then con_add("ERROR: Invalid unit behaviour '"+behaviour$+"'",Cbmpf_red)
			Return 0
	End Select
End Function


Function ai_agressive(behaviour)
	Select behaviour
		Case 2 Return 1
		Case 3 Return 1
		Case 6 Return 1
		Case 302 Return 1
		Case 402 Return 1
		Case 408 Return 1
	End Select
	Return 0
End Function


Function ai_ini(setupcenter=1)
	If setupcenter=1 Then
		PositionEntity TCunit\ai_ch,TCunit\ai_cx#,0,TCunit\ai_cz#
	EndIf
	
	Select Dunit_behaviour(TCunit\typ)
		
		Case 300,302,303
			x=Rand(0,3)
			Select x
				Case 0 ai_mode(ai_move)
				Case 1 ai_mode(ai_movel)
				Case 2 ai_mode(ai_mover)
			End Select
		
		Case 301,401
			x=Rand(0,1)
			Select x
				Case 0 ai_mode(ai_movel)
				Case 1 ai_mode(ai_mover)
			End Select
		
		
		Case 404
			x=Rand(0,3)
			Select x
				Case 0 ai_mode(ai_move)
				Case 1 ai_mode(ai_movel)
				Case 2 ai_mode(ai_mover)
			End Select
			
	End Select
	TCunit\ai_timer=gt
	
	If TCunit\health<=0 Then
		TCunit\freeze=0
		TCunit\ani=Dunit_ani_die(TCunit\typ,1)
		Animate TCunit\mh,3,Dunit_ani_die(TCunit\typ,0),Dunit_ani_die(TCunit\typ,1)
		TCunit\health_max#=0.
		EntityType TCunit\h,0
		EntityPickMode TCunit\mh,2,1
	EndIf
End Function


Function ai_ani(typ)
	Select typ
		Case ani_idle1
			ani_unit(3,Dunit_ani_idle1(TCunit\typ,0),Dunit_ani_idle1(TCunit\typ,1))
			set_parsecache(Cclass_unit,TCunit\id,"ai_idle1")
			If Instr(Dunit_scriptk$(TCunit\typ),"ai_idle1") Then
				parse_task(Cclass_unit,TCunit\id,"ai_idle1","",Dunit_script(TCunit\typ))
			EndIf
		Case ani_idle2
			ani_unit(3,Dunit_ani_idle2(TCunit\typ,0),Dunit_ani_idle2(TCunit\typ,1))
			set_parsecache(Cclass_unit,TCunit\id,"ai_idle2")
			If Instr(Dunit_scriptk$(TCunit\typ),"ai_idle2") Then
				parse_task(Cclass_unit,TCunit\id,"ai_idle2","",Dunit_script(TCunit\typ))
			EndIf
		Case ani_idle3
			ani_unit(3,Dunit_ani_idle3(TCunit\typ,0),Dunit_ani_idle3(TCunit\typ,1))
			set_parsecache(Cclass_unit,TCunit\id,"ai_idle3")
			If Instr(Dunit_scriptk$(TCunit\typ),"ai_idle3") Then
				parse_task(Cclass_unit,TCunit\id,"ai_idle3","",Dunit_script(TCunit\typ))
			EndIf
		Case ani_move
			If Dunit_loopmoveani(TCunit\typ)=1 Then
				ani_unit(1,Dunit_ani_move(TCunit\typ,0),Dunit_ani_move(TCunit\typ,1))
			Else
				ani_unit(2,Dunit_ani_move(TCunit\typ,0),Dunit_ani_move(TCunit\typ,1))
			EndIf
		Case ani_attack
			ani_unit(3,Dunit_ani_attack(TCunit\typ,0),Dunit_ani_attack(TCunit\typ,1),1.,1)
		Case ani_use
			ani_unit(3,Dunit_ani_use(TCunit\typ,0),Dunit_ani_use(TCunit\typ,1))
		Case ani_die
			ani_unit(3,Dunit_ani_die(TCunit\typ,0),Dunit_ani_die(TCunit\typ,1))
	End Select
End Function


Function ai_mode(mode,duration=0,setani=1)
	If duration=0 Then
		duration=Rand(1500,3500)
	EndIf
	TCunit\ai_mode=mode
	TCunit\ai_duration=duration
	TCunit\ai_timer=gt
	
	If TCunit\states=1
		If mode<>ai_idle Then
			If get_state(Cstate_ai_stick,Cclass_unit,TCunit\id)=1 Then
				mode=ai_idle
			EndIf
		EndIf
	EndIf
	
	If setani=1 Then
		Select mode
			Case ai_idle
				Local playidle=0
				x=Rand(1,3)
				Select x
					Case 1
						If Dunit_ani_idle1(TCunit\typ,0)<>0. Then
							ai_ani(ani_idle1)
							play_unitsound(TCunit\typ,snd_idle1,TCunit\h)
							playidle=1
						EndIf
					Case 2
						If Dunit_ani_idle2(TCunit\typ,0)<>0. Then
							ai_ani(ani_idle2)
							play_unitsound(TCunit\typ,snd_idle2,TCunit\h)
							playidle=1
						EndIf
					Case 3
						If Dunit_ani_idle3(TCunit\typ,0)<>0. Then
							ai_ani(ani_idle3)
							play_unitsound(TCunit\typ,snd_idle3,TCunit\h)
							playidle=1
						EndIf
				End Select
				If playidle=0 Then
					Animate TCunit\mh,0
					TCunit\ani=0
					idleanis=0
					If Dunit_ani_idle1(TCunit\typ,0)<>0 Then idleanis=idleanis+1
					If Dunit_ani_idle2(TCunit\typ,0)<>0 Then idleanis=idleanis+1
					If Dunit_ani_idle3(TCunit\typ,0)<>0 Then idleanis=idleanis+1
					TCunit\ai_duration=0
				EndIf
			Case ai_move,ai_movel,ai_mover
				ai_ani(ani_move)
			Case ai_turnl,ai_turnr
				ai_ani(ani_move)
			Case ai_rise,ai_fall
				ai_ani(ani_move)
			Case ai_movetarget
				ai_ani(ani_move)
			Case ai_return,ai_sreturn
				ai_ani(ani_move)
			Case ai_attack
				ai_ani(ani_attack)
			Case ai_hunt
				ai_ani(ani_move)
			Case ai_getfood
				ai_ani(ani_move)
			Case ai_flee
				ai_ani(ani_move)
		End Select
	EndIf
End Function


Function ai_move(speed#,diroff#,landcheck=0)
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
	speed#=speed#*f#
	h=TCunit\h
	Local yaw#=EntityYaw(h)
	Local mx#=Cos(yaw#+diroff#)*speed#
	Local mz#=Sin(yaw#+diroff#)*speed#
	Local tery1#=e_tery(EntityX(h),EntityZ(h))
	Local tery2#=e_tery(EntityX(h)+mx#,EntityZ(h)+mz#)
	Local dif#=tery2#-tery1#
	
		TranslateEntity h,mx#,0,mz#
		Return 1
	
	Return 0
End Function


Function ai_check(attitude=0,landcheck=1,centercheck=1)
	If TCunit\ai_mode<>ai_attack Then
		
		If landcheck=1 Then
			If EntityY(TCunit\mh,1)<0 Then
				ai_mode(ai_sreturn)
				Return
			EndIf
		ElseIf landcheck=2 Then
			If EntityY(TCunit\mh,1)>-5 Then
				ai_mode(ai_sreturn)
				Return
			EndIf
		EndIf
		
		If centercheck=1
			If TCunit\ai_mode<>ai_return Then
				If Sqr((EntityX(TCunit\h)-TCunit\ai_cx#)^2+(EntityZ(TCunit\h)-TCunit\ai_cz#)^2)>Dunit_range(TCunit\typ) Then
					If Dunit_behaviour(TCunit\typ)=408 Then
						If TCunit\ai_mode<>ai_hunt Then
							If TCunit\ai_mode<>ai_rise Then
								ai_mode(ai_return)
							EndIf
						EndIf
					Else
						ai_mode(ai_return)
					EndIf
				EndIf
			EndIf
		EndIf
		
		Select attitude
			Case 0
			Case 1
				If TCunit\ai_mode<>ai_flee Then
					For Tunit.Tunit=Each Tunit
						If Tunit\id=g_player Then
							If EntityDistance (Tunit\h,TCunit\h)<100 Then
								ai_mode(ai_flee,10000)
								TCunit\ai_target_class=Cclass_unit
								TCunit\ai_target_id=g_player
							EndIf
						EndIf
					Next
				Else
					TCunit\ai_duration=10000
					TCunit\ai_timer=gt
				EndIf
			Case 2
				tame=0
				If TCunit\states=1 Then
					If get_state(Cstate_tame,Cclass_unit,TCunit\id)=1 Then
						tame=1
					EndIf
				EndIf
			
				If tame=0 Then
					If TCunit\ai_mode<>-1 Then
						If TCunit\ai_mode<>ai_attack Then
							If TCunit\ai_mode<ail_superior Then
								If EntityDistance(TCunit\h,g_cplayer\h)<Dunit_attackrange(TCunit\typ) Then
									If g_cplayer\health#>0. Then
										doit=0
										
										If checkattackline()=1 Then
											tempunit.Tunit=TCunit
											Select Dunit_behaviour(TCunit\typ)
												Case 0	game_damage(g_cplayer\mh,Dunit_damage#(TCunit\typ),TCunit\h):doit=1
												Case 6	ai_shoot():doit=1
												Case 302
													If EntityY(g_cplayer\h)<0 Then
														game_damage(g_cplayer\mh,Dunit_damage#(TCunit\typ),TCunit\h)
														doit=1
													EndIf
												Case 408
													If TCunit\ai_mode<>ai_rise Then
														If TCunit\ai_mode<>ai_sreturn And TCunit\ai_mode<>ai_return Then
															game_damage(g_cplayer\mh,Dunit_damage#(TCunit\typ),TCunit\h)
															doit=1
														EndIf
													EndIf
												Default	game_damage(g_cplayer\mh,Dunit_damage#(TCunit\typ),TCunit\h):doit=1
											End Select
											TCunit.Tunit=tempunit
											If tame=1 Then doit=0
											If doit=1 Then
												play_unitsound(TCunit\typ,snd_attack,TCunit\h)
												ai_mode(ai_attack)
												set_parsecache(Cclass_unit,TCunit\id,"ai_attack")
												If Instr(Dunit_scriptk$(TCunit\typ),"ai_attack") Then
													parse_task(Cclass_unit,TCunit\id,"ai_attack","",Dunit_script(TCunit\typ))
												EndIf
												parse_sel(Cclass_unit,TCunit\id,"ai_attack")
											EndIf
										EndIf
									EndIf
								EndIf
							EndIf
						EndIf
						If TCunit\ai_mode<>ai_attack Then
							If TCunit\ai_mode<>ai_hunt Then
								If TCunit\ai_mode<ail_superior Then
									If EntityDistance(TCunit\h,g_cplayer\h)<Dunit_range(TCunit\typ) Then
										If g_cplayer\health#>0. Then
											If EntityY(g_cplayer\h)<0.1 Then
												If Dunit_behaviour(TCunit\typ)>=300 Then
													If Dunit_behaviour(TCunit\typ)<>408 Then
														ai_mode(ai_hunt,10000)
														If ms-TCunit\lastspot>5000 Then
															TCunit\lastspot=ms
															play_unitsound(TCunit\typ,snd_spot,TCunit\h)
														EndIf
													EndIf
												EndIf
											Else
												If Dunit_behaviour(TCunit\typ)<300 Then
													ai_mode(ai_hunt,10000)
													If ms-TCunit\lastspot>5000 Then
														TCunit\lastspot=ms
														play_unitsound(TCunit\typ,snd_spot,TCunit\h)
													EndIf
												ElseIf Dunit_behaviour(TCunit\typ)=408 Then
													If TCunit\ai_mode<>ai_rise Then
														ai_mode(ai_hunt,10000)
														If ms-TCunit\lastspot>5000 Then
															TCunit\lastspot=ms
															play_unitsound(TCunit\typ,snd_spot,TCunit\h)
														EndIf
													EndIf
												EndIf
											EndIf
										EndIf
									EndIf
								EndIf
							EndIf
						EndIf
					EndIf
				EndIf
				
		End Select
		
	EndIf
End Function


Function ai_return(water=0,turndelay#=30.)
	RotateEntity TCunit\h,0,EntityYaw(TCunit\h)+(DeltaYaw(TCunit\h,TCunit\ai_ch)*modern_blend(1./turndelay#,f#)),0
	If water=0 Then
		ai_move(Dunit_speed#(TCunit\typ),90)
	Else
		MoveEntity TCunit\h,0,0,unit_speed()*f#
	EndIf
	If Sqr((EntityX(TCunit\h)-TCunit\ai_cx#)^2+(EntityZ(TCunit\h)-TCunit\ai_cz#)^2)<75 Then
		TCunit\ai_timer=0
	EndIf
End Function


Function ai_hunt(water=0)
	If Sqr((EntityX(TCunit\h)-EntityX(g_cplayer\h))^2+(EntityZ(TCunit\h)-EntityZ(g_cplayer\h))^2)<(Float(Dunit_attackrange(TCunit\typ))/2) Then
		TCunit\ai_timer=0
	Else
		RotateEntity TCunit\h,0,EntityYaw(TCunit\h)+(DeltaYaw(TCunit\h,g_cplayer\h)*modern_blend(1./5.,f#)),0
		If water=0 Then
			ai_move(Dunit_speed#(TCunit\typ),90)
		Else
			MoveEntity TCunit\h,0,0,unit_speed#()*f#
			If Abs(EntityY(TCunit\h)-EntityY(g_cplayer\h))>20. Then
				If EntityY(TCunit\h)>EntityY(g_cplayer\h) Then
					TranslateEntity TCunit\h,0,-unit_speed#()/3.*f#,0
				Else
					TranslateEntity TCunit\h,0,unit_speed#()/3.*f#,0
				EndIf
			EndIf
		EndIf
	EndIf
End Function


Function ai_movetarget(water=0)
	Local target=parent_h(TCunit\ai_target_class,TCunit\ai_target_id)
	If target=0 Then
		TCunit\ai_timer=0
	Else
		If Sqr((EntityX(TCunit\h)-EntityX(target))^2+(EntityZ(TCunit\h)-EntityZ(target))^2)<(10) Then
			TCunit\ai_timer=0
		Else
			RotateEntity TCunit\h,0,EntityYaw(TCunit\h)+(DeltaYaw(TCunit\h,target)*modern_blend(1./5.,f#)),0
			If water=0 Then
				ai_move(Dunit_speed#(TCunit\typ),90)
			Else
				MoveEntity TCunit\h,0,0,unit_speed#()*f#
				If Abs(EntityY(TCunit\h)-EntityY(g_cplayer\h))>20. Then
					If EntityY(TCunit\h)>EntityY(g_cplayer\h) Then
						TranslateEntity TCunit\h,0,-unit_speed#()/3.*f#,0
					Else
						TranslateEntity TCunit\h,0,unit_speed#()/3.*f#,0
					EndIf
				EndIf
			EndIf
		EndIf
	EndIf
End Function


Function ai_getfood(water=0)
	Local target=parent_h(TCunit\ai_target_class,TCunit\ai_target_id)
	If target=0 Then
		TCunit\ai_timer=0
		TCunit\ai_duration=0
	Else
		If TCunit\ai_target_class=Cclass_item
			For Titem.Titem=Each Titem
				If Titem\id=TCunit\ai_target_id Then
					If Titem\parent_class<>0 Then
						TCunit\ai_timer=0
						TCunit\ai_duration=0
						Return 0
					EndIf
				EndIf
			Next
		EndIf
		If Sqr((EntityX(TCunit\h)-EntityX(target))^2+(EntityZ(TCunit\h)-EntityZ(target))^2)<(30) Then
			If TCunit\ani<>Dunit_ani_attack(TCunit\typ,1) Or Animating(TCunit\mh)=0 Then
				TCunit\ai_mode=ai_idle
				TCunit\ai_duration=5000
				TCunit\ai_timer=gt
				ai_ani(ani_attack)
				play_unitsound(TCunit\typ,snd_attack,TCunit\h)
				sfx_emit sfx_eat,TCunit\h
				
				pv_ai_eater=TCunit\id
				p_skipevent=0
				parse_emuevent(TCunit\ai_target_class,TCunit\ai_target_id,"ai_eat")
				parse_sel(TCunit\ai_target_class,TCunit\ai_target_id,"ai_eat")
				If p_skipevent=0 Then
					Select TCunit\ai_target_class
						Case Cclass_object
							If con_object(TCunit\ai_target_id) Then
								damage_object(Dunit_damage(TCunit\typ))
								If con_object(TCunit\ai_target_id) Then
									TCunit\ai_mode=ai_getfood
									TCunit\ai_duration=20000
									TCunit\ai_timer=gt
								EndIf 
							EndIf
						Case Cclass_unit
							Local temp.Tunit=TCunit
							If con_unit(TCunit\ai_target_id) Then kill_unit()
							TCunit.Tunit=temp
						Case Cclass_item
							If con_item(TCunit\ai_target_id) Then
								damage_item(Dunit_damage(TCunit\typ),1)
								If con_item(TCunit\ai_target_id) Then 
									TCunit\ai_mode=ai_getfood
									TCunit\ai_duration=20000
									TCunit\ai_timer=gt
								EndIf
							EndIf
						Case Cclass_info
							free_info(TCunit\ai_target_id)
					End Select
				EndIf
			EndIf
		Else
			RotateEntity TCunit\h,0,EntityYaw(TCunit\h)+(DeltaYaw(TCunit\h,target)*modern_blend(1./5.,f#)),0
			If water=0 Then
				ai_move(Dunit_speed#(TCunit\typ),90)
			Else
				MoveEntity TCunit\h,0,0,unit_speed#()*f#
				If Abs(EntityY(TCunit\h)-EntityY(g_cplayer\h))>20. Then
					If EntityY(TCunit\h)>EntityY(g_cplayer\h) Then
						TranslateEntity TCunit\h,0,-unit_speed#()/3.*f#,0
					Else
						TranslateEntity TCunit\h,0,unit_speed#()/3.*f#,0
					EndIf
				EndIf
			EndIf
		EndIf
	EndIf
End Function


Function ai_flee(water=0)
	Local target=parent_h(TCunit\ai_target_class,TCunit\ai_target_id)
	If target=0 Then
		TCunit\ai_timer=0
	Else
		If Sqr((EntityX(TCunit\h)-EntityX(target))^2+(EntityZ(TCunit\h)-EntityZ(target))^2)>(50) Then
			If TCunit\ai_duration<>3000 Then
				TCunit\ai_duration=3000
				TCunit\ai_timer=gt
			EndIf
		EndIf
		
		Local fp=CreatePivot()
		PositionEntity fp,EntityX(target),EntityY(target),EntityZ(target)
		RotateEntity fp,0,EntityYaw(TCunit\h)+(DeltaYaw(TCunit\h,target))+180,0
		MoveEntity fp,0,0,300.
		
		RotateEntity TCunit\h,0,EntityYaw(TCunit\h)+(DeltaYaw(TCunit\h,fp)*modern_blend(1./10.,f#)),0
		If water=0 Then
			ai_move(Dunit_speed#(TCunit\typ),90)
		ElseIf water=-1 Then
			MoveEntity TCunit\h,0,0,unit_speed#()*f#*4.0
			TranslateEntity TCunit\h,0,unit_speed#()*f#*0.5,0
		Else
			MoveEntity TCunit\h,0,0,unit_speed#()*f#
			If Abs(EntityY(TCunit\h)-EntityY(g_cplayer\h))>20. Then
				If EntityY(TCunit\h)>EntityY(g_cplayer\h) Then
					TranslateEntity TCunit\h,0,-unit_speed#()/3.*f#,0
				Else
					TranslateEntity TCunit\h,0,unit_speed#()/3.*f#,0
				EndIf
			EndIf
			If e_tery(EntityX(TCunit\h),EntityZ(TCunit\h))<=(EntityY(TCunit\h)-Dunit_colyr#(TCunit\typ)) Then
				TurnEntity TCunit\h,0,unit_speed#()*f#,0
				ai_watertest()
			ElseIf EntityCollided(TCunit\h,Cworld_col) Then
				TurnEntity TCunit\h,0,unit_speed#()*f#,0
			EndIf
		EndIf	
		FreeEntity fp
	EndIf
End Function


Function ai_attack(turndelay#=30.)
	RotateEntity TCunit\h,0,EntityYaw(TCunit\h)+(DeltaYaw(TCunit\h,g_cplayer\h)*modern_blend(1./5.,f#)),0
	If Animating(TCunit\mh) Then
		TCunit\ai_timer=gt+10000
	Else
		TCunit\ai_mode=0
		TCunit\ai_timer=0
		TCunit\ai_duration=-1
		ai_check(ai_agressive)
	EndIf
End Function


Function ai_watertest()
	ty#=e_tery(EntityX(TCunit\h),EntityZ(TCunit\h))
	MoveEntity TCunit\h,0,0,unit_speed#()*f#
	If e_tery(EntityX(TCunit\h),EntityZ(TCunit\h))>ty# Then
		MoveEntity TCunit\h,0,0,-unit_speed#()*f#
		Return 1
	Else
		Return 0
	EndIf
End Function


Function ai_shoot()
	t=CreatePivot()
	PositionEntity t,EntityX(g_cplayer\h),EntityY(g_cplayer\h),EntityZ(g_cplayer\h)
	Local h=CreatePivot()
	PositionEntity h,EntityX(TCunit\h),EntityY(TCunit\h)+Dunit_eyes#(TCunit\typ),EntityZ(TCunit\h)
	PointEntity h,t
	FreeEntity t
	MoveEntity h,0,0,Dunit_colxr#(TCunit\typ)+5.
	in_pe=0
	EntityPickMode env_sea2,0
	EntityPickMode TCunit\h,0
	ShowEntity g_cplayer\mh
	in_pe=EntityPick(h,Dunit_attackrange(TCunit\typ))
	EntityPickMode TCunit\h,2,1
	HideEntity g_cplayer\mh
	in_px#=PickedX()
	in_py#=PickedY()
	in_pz#=PickedZ()
	
	If set_effects>0 Then
		For i=1 To 5
			p_add(EntityX(h)+Rnd(-3,3),EntityY(h)+Rnd(-3,3),EntityZ(h)+Rnd(-3,3),Cp_smoke,Rnd(5,7),Rnd(0.15,0.4))
		Next
	EndIf
	
	If (EntityY(h)<0)Or(in_py#<0) Then
		Local water=0
		Local target=CreatePivot()
		Local sx#,sy#,sz#
		sx#=EntityX(h)
		sy#=EntityY(h)
		sz#=EntityZ(h)
		Local cx#,cy#,cz#
		PositionEntity target,in_px#,in_py#,in_pz#
		If EntityY(h)<0 Then water=1
		For i=1 To 10000
			MoveEntity h,0,0,10
			If water=0 Then
				If EntityY(h)<0 Then
					water=1
					p_add(EntityX(h),1,EntityZ(h),Cp_rwave,Rnd(5,10),Rnd(0.9,1.5))
					cx#=EntityX(h)
					cy#=EntityY(h)
					cz#=EntityZ(h)
				EndIf
			Else
				If Rand(2)=1 Then 
					p_add(EntityX(h),EntityY(h),EntityZ(h),Cp_bubbles,Rnd(1,3),Rnd(0.3,0.5))
				EndIf
				If EntityY(h)>0 Then
					water=0
					p_add(EntityX(h),1,EntityZ(h),Cp_rwave,Rnd(5,10),Rnd(0.9,1.5))
					cx#=EntityX(h)
					cy#=EntityY(h)
					cz#=EntityZ(h)
					Exit
				EndIf
			EndIf
			If EntityDistance(h,target)<10 Then Exit
		Next
		If sy#>0 Then
			lineh=meshline(cx#,cy#,cz#,in_px#,in_py#,in_pz#,0.4)
		Else
			If water=0 Then
				lineh=meshline(sx#,sy#,sz#,cx#,cy#,cz#,0.4)
			Else
				lineh=meshline(sx#,sy#,sz#,in_px#,in_py#,in_pz#,0.4)
			EndIf
		EndIf
		If lineh<>0 Then
			EntityBlend lineh,3
			tmp_ph=lineh
			p_add(0,0.2,0.011,Cp_fadeout)
		EndIf
			
		FreeEntity target
	EndIf
			
	FreeEntity h				

	If in_pe<>0 Then
		If in_pe=e_ter Then
			p_add(in_px#,in_py#,in_pz#,Cp_smoke,Rnd(5,10),Rnd(0.3,1.5))
		Else
			Local tempunit.Tunit=TCunit
			game_damage(in_pe,Dunit_damage#(TCunit\typ),TCunit\id,1)
			TCunit.Tunit=tempunit
		EndIf
		Return 1
	Else
		Return 0
	EndIf
End Function


Function ai_obstacle()
	If EntityCollided(TCunit\h,Cworld_col) Then Return 1
	Local c=CountCollisions(TCunit\h)
	If c>0 Then
		Return 1
		Local i
		Local y#=EntityY(TCunit\h)-(Dunit_colyr#(TCunit\h)/2.)
		For i=1 To c
			If CollisionY(TCunit\h,i)>y# Then Return 1
		Next
	EndIf
	Return 0
End Function


Function ai_turn()
	If Rand(1,2)=1 Then
		ai_mode(ai_movel)
	Else
		ai_mode(ai_mover)
	EndIf
End Function


Function ai_scanenv()
	p=CreatePivot()
	HideEntity TCunit\h
	EntityPickMode ter,0
	For angle=0 To 270 Step 45
		PositionEntity p,EntityX(TCunit\h),EntityY(TCunit\h),EntityZ(TCunit\h)
		RotateEntity p,0,angle,0
		MoveEntity p,0,0,Dunit_colxr#(TCunit\typ)
		If EntityPick(p,300)=0 Then
			RotateEntity TCunit\h,0,angle,0
			p_add(EntityX(p),EntityY(p),EntityZ(p),Cp_debug)
			EntityColor TCp\h,0,255,0
		Else
			p_add(EntityX(p),EntityY(p),EntityZ(p),Cp_debug)
			EntityColor TCp\h,255,0,0
		EndIf
	Next
	EntityPickMode ter,2,1
	ShowEntity TCunit\h
	FreeEntity p
End Function


Function update_unit()
	behaviour=Dunit_behaviour(TCunit\typ)
	
	Select behaviour
		Case 1,2,3,7,8,9 mode=1
		Case 10 mode=6
		Case 200,201 mode=1
		Case 300,301,302,303 mode=2
		Case 400,401,402,403,404,405,406,407,408 mode=3
		Case 500 mode=1
		Case 501 mode=4
		Case 502 mode=5
		Default mode=0
	End Select
	
	
	If TCunit\health#>0.0 Then
		
		If TCunit\freeze=0 Then
		
			Select Dunit_behaviour(TCunit\typ)
				
				Case 0
				
				Case 1
					Include "includes\ai\001_normal_animal.bb"
				
				Case 2
					Include "includes\ai\002_raptor_predator.bb"
					
				Case 3
					Include "includes\ai\003_standandsnap.bb"
					
				Case 4
					Include "includes\ai\004_idle.bb"
					
				Case 5
					Include "includes\ai\005_idleturn.bb"
					
				Case 6
					Include "includes\ai\006_standandshoot.bb"
					
				Case 7
					Include "includes\ai\007_monkey.bb"
					
				Case 8
					Include "includes\ai\008_shy.bb"
					
				Case 9
					Include "includes\ai\009_plague.bb"
					
				Case 10
					Include "includes\ai\010_landbird.bb"
				
				Case 200
					Include "includes\ai\200_crab.bb"
					
				Case 201
					Include "includes\ai\201_amphibian.bb"
					
				Case 300
					Include "includes\ai\300_fish.bb"
					
				Case 301
					Include "includes\ai\301_circlingfish.bb"
					
				Case 302
					Include "includes\ai\302_predatorfish.bb"
					
				Case 303
					Include "includes\ai\303_deepseafish.bb"
				
				Case 400,405,406
					Include "includes\ai\400_405_406_bird.bb"
					
				Case 401
					Include "includes\ai\401_circlingbird.bb"		
					
				Case 404
					Include "includes\ai\404_flyinginsect.bb"
					
				Case 407
					Include "includes\ai\407_landskybird.bb"
					
				Case 408
					Include "includes\ai\408_killerbird.bb"
					
					
					
				Case 500
					If TCunit\id<>g_drive Then
						If ChannelPlaying(TCunit\chan)<>0 Then
							StopChannel TCunit\chan
						EndIf
					EndIf
				
				Case 501
					RotateEntity TCunit\h,Sin(in_wa#+(TCunit\id*20)),EntityYaw(TCunit\h),Cos(in_wa#+(TCunit\id*20))
					PositionEntity TCunit\vh,EntityX(TCunit\mh,1),EntityY(TCunit\mh,1),EntityZ(TCunit\mh,1)
					RotateEntity TCunit\vh,0,EntityYaw(TCunit\h),0
					If TCunit\id<>g_drive Then
						If ChannelPlaying(TCunit\chan)<>0 Then
							StopChannel TCunit\chan
						EndIf
					EndIf
				
				Case 502
					If TCunit\id<>g_drive Then
						If ChannelPlaying(TCunit\chan)<>0 Then
							StopChannel TCunit\chan
						EndIf
					EndIf
					
	
			End Select
			
			
			Select mode
				Case 0
					If col_player_managed(TCunit) Then
						col_player_physics()
					Else
					If EntityY(TCunit\h)>0.-(Dunit_colyr(TCunit\typ)/2.) Then
						If (gt-TCunit\phy_jump#)<0. Then
							par=TCunit\phy_jump-gt
							tot=game_jumptime
							perc#=Float(Float(par)/Float(tot))
							TranslateEntity TCunit\h,0,((4.*perc#)*f#),0
							tery#=e_tery(EntityX(TCunit\h),EntityZ(TCunit\h))
							If EntityY(TCunit\h)<=(tery#+Dunit_colyr(TCunit\typ)) Then
								PositionEntity TCunit\h,EntityX(TCunit\h),tery#+Dunit_colyr(TCunit\typ),EntityZ(TCunit\h)
								TCunit\phy_ft=gt
								TCunit\phy_jump=0
							ElseIf EntityCollided(TCunit\h,Cworld_col) Then
								TCunit\phy_ft=gt
								TCunit\phy_jump=0
							EndIf
							TCunit\phy_fall=gt
							TCunit\phy_fally#=EntityY(TCunit\h)
						Else
							par=gt-TCunit\phy_fall
							If par>Cworld_facct Then par=Cworld_facct
							tot=Cworld_facct
							perc#=Float(Float(par)/Float(tot))
							TranslateEntity TCunit\h,0,-(Cworld_g#*f#*(3.*perc#)),0
							tery#=e_tery(EntityX(TCunit\h),EntityZ(TCunit\h))
							col=0
							If EntityY(TCunit\h)<=(tery#+Dunit_colyr(TCunit\typ)) Then
								PositionEntity TCunit\h,EntityX(TCunit\h),tery#+Dunit_colyr(TCunit\typ),EntityZ(TCunit\h)
								If gt-TCunit\phy_fall>300 Then col=1
								TCunit\phy_ft=gt
								TCunit\phy_fall=gt
								TCunit\phy_fally#=EntityY(TCunit\h)
							ElseIf EntityCollided(TCunit\h,Cworld_col) Then
								If gt-TCunit\phy_fall>300 Then col=1
								TCunit\phy_ft=gt
								TCunit\phy_fall=gt
								TCunit\phy_fally#=EntityY(TCunit\h)
							EndIf
							If col=1 Then
								If TCunit\id=g_player Then
									If g_drive=0 Then
										If EntityCollided(TCunit\h,Cworld_col) Then
											If get_object(CollisionEntity(TCunit\h,1))<>-1 Then
												If Handle(TCobject) Then
													material_stepsfx(Dobject_mat(TCobject\typ))	
												EndIf
											ElseIf get_item(CollisionEntity(TCunit\h,1))<>-1 Then
												If Handle(TCitem) Then
													material_stepsfx(Ditem_mat(TCitem\typ))	
												EndIf
											Else
												colwithunit=0
												For Tunit.Tunit=Each Tunit
													If Tunit\vh=CollisionEntity(TCunit\h,1) Then
														material_stepsfx(Dunit_mat(Tunit\typ))	
														colwithunit=1
														Exit
													EndIf
												Next
												If colwithunit=0 Then
													If m_menu<>Cmenu_if_movie Then
														sfx(sfx_step(Rand(0,3)))
													EndIf
												EndIf
											EndIf
										Else
											If m_menu<>Cmenu_if_movie Then
												sfx(sfx_step(Rand(0,3)))
											EndIf
										EndIf
										g_player_mst=ms
									EndIf
								EndIf
							EndIf
						EndIf
					Else
						TCunit\phy_ft=gt
						TCunit\phy_fall=gt
						TCunit\phy_jump#=0.
						tery#=e_tery(EntityX(TCunit\h),EntityZ(TCunit\h))
						If EntityY(TCunit\h)<(tery#+Dunit_colyr(TCunit\typ)) Then
							PositionEntity TCunit\h,EntityX(TCunit\h),tery#+Dunit_colyr(TCunit\typ),EntityZ(TCunit\h)
						EndIf
					EndIf
					EndIf
				Case 1
					If EntityY(TCunit\h)>0. Then
						TranslateEntity TCunit\h,0,-(Cworld_g#*f#*0.6),0
						tery#=e_tery(EntityX(TCunit\h),EntityZ(TCunit\h))
						If EntityY(TCunit\h)<=(tery#+Dunit_colyr(TCunit\typ)) Then
							PositionEntity TCunit\h,EntityX(TCunit\h),tery#+Dunit_colyr(TCunit\typ),EntityZ(TCunit\h)
							TCunit\phy_ft=gt
						ElseIf EntityCollided(TCunit\h,Cworld_col) Then
							TCunit\phy_ft=gt
						EndIf
					Else
						TCunit\phy_ft=gt
						TCunit\phy_fall=gt
						TCunit\phy_jump#=0.
						TranslateEntity TCunit\h,0,-(Cworld_g#*f#*0.6),0
						tery#=e_tery(EntityX(TCunit\h),EntityZ(TCunit\h))
						If EntityY(TCunit\h)<(tery#+Dunit_colyr(TCunit\typ)) Then
							PositionEntity TCunit\h,EntityX(TCunit\h),tery#+Dunit_colyr(TCunit\typ),EntityZ(TCunit\h)
						EndIf
					EndIf			
				Case 2
					tery#=e_tery(EntityX(TCunit\h),EntityZ(TCunit\h))
					If EntityY(TCunit\h)<(tery#+Dunit_colyr(TCunit\typ)) Then
						PositionEntity TCunit\h,EntityX(TCunit\h),tery#+Dunit_colyr(TCunit\typ),EntityZ(TCunit\h)
					EndIf
					If EntityY(TCunit\h)>-10 Then
						PositionEntity TCunit\h,EntityX(TCunit\h),-10,EntityZ(TCunit\h)
					EndIf
				Case 3
					freecon=0
					tery#=e_tery(EntityX(TCunit\h),EntityZ(TCunit\h))
					If tery#<1 Then tery#=1
					rtery#=tery#
					Select Dunit_behaviour(TCunit\typ)
							Case 400 tery#=tery#+350
							Case 401 tery#=tery#+350
							Case 404 tery#=tery#+20
							Case 405 tery#=tery#+500
							Case 406 tery#=tery#+180
							Case 407 tery#=tery#+210:freecon=1
							Case 408 tery#=tery#+400:freecon=1
					End Select
					If freecon=0 Then
						If EntityY(TCunit\h)<tery# Then
							PositionEntity TCunit\h,EntityX(TCunit\h),tery#,EntityZ(TCunit\h)
						Else
							Select Dunit_behaviour(TCunit\typ)
								Case 400,401,405,406 TranslateEntity TCunit\h,0,-0.2*f#,0
								Case 404 TranslateEntity TCunit\h,0,-0.3*f#,0
							End Select
							If EntityY(TCunit\h)<tery# Then
								PositionEntity TCunit\h,EntityX(TCunit\h),tery#,EntityZ(TCunit\h)
							EndIf
						EndIf
					Else
						If EntityY(TCunit\h)<rtery# Then
							If TCunit\ai_mode=ai_fall Then
								If rtery#>2 Then
									ai_mode(ai_idle)
									PositionEntity TCunit\h,EntityX(TCunit\h),rtery#+Dunit_colyr(TCunit\typ),EntityZ(TCunit\h)
								Else
									ai_mode(ai_rise,Rand(3000,10000))
									PositionEntity TCunit\h,EntityX(TCunit\h),rtery#,EntityZ(TCunit\h)
								EndIf
							Else
								ai_mode(ai_rise,Rand(3000,10000))
								PositionEntity TCunit\h,EntityX(TCunit\h),rtery#,EntityZ(TCunit\h)
							EndIf
						ElseIf EntityY(TCunit\h)>tery# Then 
							TranslateEntity TCunit\h,0,-0.2*f#,0
							If TCunit\ai_mode<>ai_fall Then
								If TCunit\ai_mode<>ai_return And TCunit\ai_mode<>ai_sreturn Then
									If TCunit\ai_mode=ai_rise Then
									EndIf
								EndIf
							EndIf
						EndIf
					EndIf
				Case 4
					PositionEntity TCunit\h,EntityX(TCunit\h),1+Dunit_colyr(TCunit\typ),EntityZ(TCunit\h)
				Case 5
					If TCunit\vh<>0 Then HideEntity TCunit\vh
					If g_drive<>TCunit\id Then
						TranslateEntity TCunit\h,0,-(Cworld_g#*f#*2.),0
					EndIf
					tery#=e_tery(EntityX(TCunit\h),EntityZ(TCunit\h))
					If EntityY(TCunit\h)<(tery#+Dunit_colyr(TCunit\typ)) Then
						PositionEntity TCunit\h,EntityX(TCunit\h),tery#+Dunit_colyr(TCunit\typ),EntityZ(TCunit\h)
					EndIf
					If TCunit\vh<>0 Then ShowEntity TCunit\vh
				Case 6
					If TCunit\ai_mode<>ai_flee Then
						TranslateEntity TCunit\h,0,-(Cworld_g#*f#*0.6),0
						tery#=e_tery(EntityX(TCunit\h),EntityZ(TCunit\h))
						If EntityY(TCunit\h)<=(tery#+Dunit_colyr(TCunit\typ)) Then
							PositionEntity TCunit\h,EntityX(TCunit\h),tery#+Dunit_colyr(TCunit\typ),EntityZ(TCunit\h)
							TCunit\phy_ft=gt
						ElseIf EntityCollided(TCunit\h,Cworld_col) Then
							TCunit\phy_ft=gt
						EndIf
					Else
						TCunit\phy_ft=gt
					EndIf
			End Select
		
		
		Else
		
			If TCunit\ani<>0
				TCunit\ani=0
				Animate TCunit\mh,0
			EndIf
		
		EndIf
		
		
	Else
	
		Select mode
			Case 0
				TranslateEntity TCunit\h,0,-(Cworld_g#*f#*0.6),0 
				tery#=e_tery(EntityX(TCunit\h),EntityZ(TCunit\h))
				If tery#<-2 Then tery#=-2
				If EntityY(TCunit\h)<(tery#+Dunit_colyr(TCunit\typ)) Then
					PositionEntity TCunit\h,EntityX(TCunit\h),tery#+Dunit_colyr(TCunit\typ),EntityZ(TCunit\h)
				EndIf
			Case 1,6
				If EntityY(TCunit\h)<=-3+Dunit_colyr(TCunit\typ) Then
					TranslateEntity(TCunit\h,0,0.5*f#,0)
					If EntityY(TCunit\h)>-3+Dunit_colyr(TCunit\typ) Then
						PositionEntity TCunit\h,EntityX(TCunit\h),-3+Dunit_colyr(TCunit\typ),EntityZ(TCunit\h)
					EndIf
				Else
					TranslateEntity TCunit\h,0,-(Cworld_g#*f#*0.6),0
				EndIf
				tery#=e_tery(EntityX(TCunit\h),EntityZ(TCunit\h))
				If EntityY(TCunit\h)<(tery#+Dunit_colyr(TCunit\typ)) Then
					PositionEntity TCunit\h,EntityX(TCunit\h),tery#+Dunit_colyr(TCunit\typ),EntityZ(TCunit\h)
				EndIf
			Case 2
				If EntityY(TCunit\h)<-2 Then
					TranslateEntity(TCunit\h,0,0.5*f#,0)
				Else
					PositionEntity TCunit\h,EntityX(TCunit\h),-2,EntityZ(TCunit\h)
					If e_tery(EntityX(TCunit\h),EntityZ(TCunit\h))>EntityY(TCunit\h) Then
						PositionEntity TCunit\h,EntityX(TCunit\h),e_tery(EntityX(TCunit\h),EntityZ(TCunit\h))+Dunit_colyr(TCunit\typ),EntityZ(TCunit\h)
					EndIf
				EndIf
			Case 3
				TranslateEntity TCunit\h,0,-(Cworld_g#*f#*2.),0
				tery#=e_tery(EntityX(TCunit\h),EntityZ(TCunit\h))
				If tery#<-2 Then tery#=-2
				If EntityY(TCunit\h)<(tery#+Dunit_colyr(TCunit\typ)) Then
					PositionEntity TCunit\h,EntityX(TCunit\h),tery#+Dunit_colyr(TCunit\typ),EntityZ(TCunit\h)
				EndIf
					
		End Select
	
	EndIf
End Function


Function ai_movesound(mode=1)
	If TCunit<>Null Then
		If mode=1 Then
			If TCunit\chan<>0 Then
				If ChannelPlaying(TCunit\chan)=0 Then
					TCunit\chan=play_unitsound(TCunit\typ,snd_move,TCunit\h)
				EndIf
			Else
				TCunit\chan=play_unitsound(TCunit\typ,snd_move,TCunit\h)
			EndIf
		Else
			If TCunit\chan<>0 Then
				If ChannelPlaying(TCunit\chan)=1 Then
					StopChannel(TCunit\chan)
				EndIf
			EndIf
		EndIf
	EndIf
End Function


Function ai_center()
	TCunit\ai_cx#=EntityX(TCunit\h)
	TCunit\ai_cz#=EntityZ(TCunit\h)
	PositionEntity TCunit\ai_ch,TCunit\ai_cx#,0,TCunit\ai_cz#
End Function


Function ai_dosani()
	TCunit\ai_timer=0
	If Animating(TCunit\mh) Then
		TCunit\ai_timer=gt+10000
	EndIf
End Function


Function ai_modetxt$(mode)
	Select mode
		Case ai_idle Return ai_idle+": idle"
		Case ai_move Return ai_move+": move"
		Case ai_movel Return ai_movel+": move left"
		Case ai_mover Return ai_mover+": move right"
		Case ai_turnl Return ai_turnl+": turn left"
		Case ai_turnr Return ai_turnr+": turn right"
		Case ai_rise Return ai_rise+": rise"
		Case ai_fall Return ai_fall+": fall"
		Case ai_return Return ai_return+": return"
		Case ai_sreturn Return ai_sreturn+": strict return"
		Case ai_attack Return ai_idle+": attack player"
		Case ai_hunt Return ai_idle+": hunt player"
		Case ai_movetarget Return ai_movetarget+": move to target"
		Case ai_getfood Return ai_getfood+": get food"
		Case ai_flee Return ai_flee+": flee"
		Case ai_sani Return ai_sani+": script animation"
		Default Return mode+": UNKNOWN MODE"
	End Select
End Function