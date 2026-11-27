Function game_input_normal()
	TCunit.Tunit=g_cplayer
	If Handle(TCunit)=0 Then RuntimeError("Missing Player Data (game_input_normal)")
	h=TCunit\h
	
	If in_gt5000go Then
		If TCunit\health>0. Then
			damage#=0
			If TCunit\hunger#>=Dunit_store#(TCunit\typ) Then
				damage#=damage#+game_exh_damage#(0)
			EndIf	
			If TCunit\thirst#>=Dunit_store#(TCunit\typ) Then
				damage#=damage#+game_exh_damage#(1)
			EndIf
			If TCunit\exhaustion#>=Dunit_store#(TCunit\typ) Then
				damage#=damage#+game_exh_damage#(2)
			EndIf
			If damage#>0. Then
				hurt_unit(damage#)
				If Handle(TCunit)=0 Then Return
			EndIf
		EndIf
	EndIf
	
	If in_console=0 Then
		
		If game_scriptkeys>0 Then
			For i=0 To 20
				If game_scriptkey(i)<>0 Then
					If game_scriptkeyn$(i)<>0 Then
						If in_keyhit(i+21) Then
							If Instr(game_scriptk$,"keyhit"+zerofill$(i,2)) Then
								parse_task(0,0,"keyhit"+zerofill$(i,2),"",game_script$)
							EndIf
						EndIf
						If in_keydown(i+21) Then
							If Instr(game_scriptk$,"keydown"+zerofill$(i,2)) Then
								parse_task(0,0,"keydown"+zerofill$(i,2),"",game_script$)
							EndIf
						EndIf
						If in_keyrelease(i+21) Then
							If Instr(game_scriptk$,"keyrelease"+zerofill$(i,2)) Then
								parse_task(0,0,"keyrelease"+zerofill$(i,2),"",game_script$)
							EndIf
						EndIf
					EndIf
				EndIf
			Next
		EndIf
		
		If TCunit\health#>0. Then
			
			If pc_typ<150 Then
			
				If TCunit\freeze=0 Then
					
					If g_drive>0 Then
						
						game_input_vehicles()
						
					Else
					
						speed#=Dunit_speed#(TCunit\typ)
						If TCunit\states=1 Then
							For Tstate.Tstate=Each Tstate
								If Dstate_a(Tstate\typ) Then
									If Tstate\parent_class=Cclass_unit Then
										If Tstate\parent_id=1 Then
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
							Next
						EndIf
						
						m_lr=0
						m_fb=0
						If in_keydown(Ckey_forward) Then
							m_fb=1
						ElseIf in_keydown(Ckey_backward) Then
							m_fb=1
						EndIf 
						If in_keydown(Ckey_left) Then
							m_lr=1
						ElseIf in_keydown(Ckey_right) Then
							m_lr=1
						EndIf
						If m_fb=1 Then
							If m_lr=1 Then
								speed#=speed#*0.75
							EndIf
						EndIf
						
						x=0
						moved=0
						If EntityY(TCunit\h)>0.-(Dunit_colyr(TCunit\typ)/2.) Then
							g_swim=0
							If (gt-TCunit\phy_jump#)<0. Then
								If in_keydown(Ckey_forward) Then
									game_move(speed#*game_jumpfac#,90)
								EndIf
								If in_keydown(Ckey_backward) Then
									game_move(speed#*game_jumpfac#,270)
								EndIf
								If in_keydown(Ckey_left) Then
									game_move(speed#*game_jumpfac#,180)
								EndIf
								If in_keydown(Ckey_right) Then
									game_move(speed#*game_jumpfac#,0)
								EndIf					
							Else
								If in_keydown(Ckey_forward) Then
									game_move(speed#,90)
									moved=1
								EndIf
								If in_keydown(Ckey_backward) Then
									game_move(speed#,270)
									moved=1
								EndIf
								If in_keydown(Ckey_left) Then
									game_move(speed#,180)
									moved=1
								EndIf
								If in_keydown(Ckey_right) Then
									game_move(speed#,0)
									moved=1
								EndIf
								If moved=1 Then
									If in_gt100go Then game_exhaust(game_exh_move#(0),game_exh_move#(1),game_exh_move#(2))
									If ms-g_player_mst>500 Then
										If gt-TCunit\phy_fall<20 Then
											If (EntityY(TCunit\h)-Dunit_colyr(TCunit\typ))>0. Then
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
															sfx(sfx_step(Rand(0,3)))
														EndIf
													EndIf
												Else
													If Abs((EntityY(TCunit\h)-Dunit_colyr(TCunit\typ))-e_tery(EntityX(TCunit\h),EntityZ(TCunit\h)))<5. Then
														sfx(sfx_step(Rand(0,3)))
													EndIf
												EndIf
											Else
												sfx(sfx_waterstep)
												p_add(EntityX(TCunit\h),1,EntityZ(TCunit\h),Cp_rwave,Rnd(5,10),Rnd(0.9,1.5))
												p_add(EntityX(TCunit\h),1,EntityZ(TCunit\h),Cp_splash,Rnd(15,20),1.)
											EndIf
											g_player_mst=ms
										EndIf
									EndIf
								EndIf
							EndIf
						Else
							If g_swim=0 Then
								g_swim=1
								If (gt-TCunit\phy_fall)>50 Then
								EndIf
							EndIf
							divey#=EntityY(TCunit\h)
							If in_keydown(Ckey_forward) Then
								MoveEntity TCunit\h,0,0,(speed#*f#*0.8)
								moved=1
							EndIf
							If in_keydown(Ckey_backward) Then
								MoveEntity TCunit\h,0,0,-(speed#*f#*0.8)
								moved=1
							EndIf
							If in_keydown(Ckey_left) Then
								MoveEntity TCunit\h,-(speed#*f#*0.8),0,0
								moved=1
							EndIf
							If in_keydown(Ckey_right) Then
								MoveEntity TCunit\h,(speed#*f#*0.8),0,0
								moved=1
							EndIf
							If EntityY(TCunit\h)>0.-(Dunit_colyr(TCunit\typ)/2.) Then
								PositionEntity TCunit\h,EntityX(TCunit\h),-(Dunit_colyr(TCunit\typ)/2.),EntityZ(TCunit\h)							
							EndIf
							If in_gt100go Then
								If EntityY(cam)>0 Then
									p_add(EntityX(TCunit\h)+Rnd(-3,3),1,EntityZ(TCunit\h)+Rnd(-3,3),Cp_rwave,Rnd(3,6),Rnd(0.3,0.7))
								EndIf					
							EndIf
							If moved=1 Then
								If in_gt100go Then game_exhaust(game_exh_swim#(0),game_exh_swim#(1),game_exh_swim#(2))
								If ms-g_player_mst>1000 Then
									sfx(sfx_swim)
									g_player_mst=ms
								EndIf
							EndIf
							
							If game_divetime>-1 Then
								If (gt-g_airtimer)>=game_divetime Then
									If in_gt1000go Then
										sfx sfx_drown
										For i=1 To 5
											p_add(EntityX(cam)+Rnd(-5,5),EntityY(cam)-10,EntityZ(cam)+Rnd(-5,5),10,Rnd(1,3))
										Next
										hurt_unit(game_divedamage#,0,-1)
									EndIf
								EndIf
							EndIf
						EndIf
						
						If in_keydown(Ckey_jump) Then
							If in_jumprehit=1 Then
								If (gt-TCunit\phy_ft)<10 Then
									If g_dive=0 Then
										If col_jump() Then
											TCunit\phy_jump#=gt+game_jumptime
											If (gt-in_lastjump)>100 Then
												If EntityY(TCunit\h)>0 Then
													game_exhaust(game_exh_jump#(0),game_exh_jump#(1),game_exh_jump#(2))
												EndIf
												in_lastjump=gt
											EndIf
										EndIf
									EndIf
								EndIf
							EndIf
						EndIf
						
					EndIf
						
					If in_keyhit(Ckey_use) Then game_use()
					
					If KeyHit(2) Then game_useasweapon(in_quickslot(1))
					If KeyHit(3) Then game_useasweapon(in_quickslot(2))
					If KeyHit(4) Then game_useasweapon(in_quickslot(3))
					If KeyHit(5) Then game_useasweapon(in_quickslot(4))
					If KeyHit(6) Then game_useasweapon(in_quickslot(5))
					If KeyHit(7) Then game_useasweapon(in_quickslot(6))
					If KeyHit(8) Then game_useasweapon(in_quickslot(7))
					If KeyHit(9) Then game_useasweapon(in_quickslot(8))
					If KeyHit(10) Then game_useasweapon(in_quickslot(9))
					If KeyHit(11) Then game_useasweapon(-1)
					
					If in_keyhit(Ckey_next) Then
						game_scrollweapon(1)
					ElseIf in_keyhit(Ckey_prev) Then
						game_scrollweapon(0)
					EndIf
					
					If in_keydown(Ckey_attack1) Then
						game_attack1()
					ElseIf qol_attack2() Then
						game_attack2()
					EndIf
					
					If in_keyhit(Ckey_sleep) Then
						game_sleep()
					EndIf
					
					If game_m_loadsave=1 Then
						If in_keyhit(Ckey_quicksave) Then
							modern_queue_save(1)
						EndIf
						
						If in_keyhit(Ckey_quickload) Then
							modern_queue_save(2)
						EndIf
					EndIf
					
				EndIf
				
			EndIf
				
			If set_xinvert=1 Then
				in_mxs#=-in_mxs#
			EndIf
			If in_mxs<>0 Then RotateEntity cam,EntityPitch(cam),EntityYaw(cam)+(in_mxs#*set_msens#),EntityRoll(cam)
			If in_mys<>0 Then
				in_mys#=(in_mys#*set_msens#)*set_minvert
				If in_mys>0 Then
					If EntityPitch(cam)+in_mys>88
						in_mys=88-EntityPitch(cam)
					EndIf
					RotateEntity cam,EntityPitch(cam)+in_mys,EntityYaw(cam),EntityRoll(cam)
				ElseIf in_mys<0 Then
					If EntityPitch(cam)+in_mys<-88
						in_mys=-88-EntityPitch(cam)
					EndIf
					RotateEntity cam,EntityPitch(cam)+in_mys,EntityYaw(cam),EntityRoll(cam)
				EndIf
			EndIf
			RotateEntity h,EntityPitch(cam),EntityYaw(cam),0
			
			If in_keyhit(Ckey_chat) Then
				FlushKeys()
				m_menu=Cmenu_if_chat
				in_chat$=""
			EndIf
		
		
		Else
		
			If in_mxs<>0 Then RotateEntity cam,EntityPitch(cam),EntityYaw(cam)+(in_mxs#*set_msens#),EntityRoll(cam)
			If in_mys<>0 Then
				in_mys#=(in_mys#*set_msens#)*set_minvert
				If in_mys>0 Then
					If EntityPitch(cam)+in_mys>70
						in_mys=70-EntityPitch(cam)
					EndIf
					RotateEntity cam,EntityPitch(cam)+in_mys,EntityYaw(cam),EntityRoll(cam)
				ElseIf in_mys<0 Then
					If EntityPitch(cam)+in_mys<-70
						in_mys=-70-EntityPitch(cam)
					EndIf
					RotateEntity cam,EntityPitch(cam)+in_mys,EntityYaw(cam),EntityRoll(cam)
				EndIf
			EndIf
			RotateEntity h,EntityPitch(cam),EntityYaw(cam),0
			
		EndIf
	EndIf
End Function


Function game_input_menu()
	If in_console=0 Then
		If m_menu<Cmenu_if_quit Then
			If in_inputfocus=-1 Then
				If in_keyhit(Ckey_char) Then
					m_menu=Cmenu_if_char
				ElseIf in_keyhit(Ckey_items) Then
					m_menu=Cmenu_if_items
					if_unselectitems()
					in_scr_scr=0
				ElseIf in_keyhit(Ckey_diary) Then
					m_menu=Cmenu_if_diary
					in_scr_scr=0
					in_scr_scr2=0
					in_opt(0)=0
					if_lastentry()
				ElseIf in_escape Then
					If KeyDown(42) And qol_shift_reserved()=0 Then
						m_performquit=1
					Else
						If m_menu=0 Then
							m_menu=Cmenu_if_items
							if_unselectitems()
							in_scr_scr=0
						Else
							m_menu=0
						EndIf
					EndIf
				ElseIf in_keyhit(Ckey_jump) Then
					If m_menu<>0 Then
						If ms-in_blockclose>1500 Then
							m_menu=0
						EndIf
					EndIf
				EndIf
			EndIf
		ElseIf m_menu=Cmenu_if_movie
			If in_escape Then
				If seq_skipable=1 Then
					Local time=(modern_clock()-seq_start)+1
					ht=0
					For Tseq.Tseq=Each Tseq
						Delete Tseq
					Next
					seq_end()
					parse_globalevent("skipsequence","sequence skipped by user")
				EndIf
			EndIf
		EndIf
	EndIf
End Function
