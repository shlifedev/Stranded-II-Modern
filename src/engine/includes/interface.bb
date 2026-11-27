Function interface()
	
	Local alive=0
	If g_player<>0 Then
		If g_cplayer\health>0. Then alive=1
	EndIf
	If alive=0 Then
		bmpf_txt_c(set_scrx/2,set_scry/2-50,sm$(140),Cbmpf_red)
		
		mb_override(0.96)
	EndIf
	
	If alive=1 Then
		If m_menu=0 Then
			Local t3dh
			For t3d.t3d=Each t3d
				t3dh=parent_h(t3d\class,t3d\id)
				If t3dh<>0 Then
					If EntityDistance(t3dh,cam)<t3d\range Then
						If EntityInView(t3dh,cam) Then
							CameraProject cam,EntityX(t3dh),EntityY(t3dh)+t3d\offset,EntityZ(t3dh)
							bmpf_txt_c(ProjectedX()-10,ProjectedY(),t3d\txt$,t3d\typ)
						EndIf
					EndIf
				Else
					Delete t3d
				EndIf
			Next
		EndIf
	EndIf	
	
	If m_menu=0 Or m_menu>=Cmenu_if_buildsetup Then
		y=set_scry/2
		For Tmsg.Tmsg=Each Tmsg
			If y<55 Then
				Delete Tmsg
			Else
				If ms-Tmsg\age>0 Then Tmsg\scroll#=Tmsg\scroll#*(1.+f#)
				bmpf_txt(5-Tmsg\scroll#,y,Tmsg\msg$,Tmsg\col)
				If Tmsg\scroll#>400 Then Delete Tmsg
			EndIf
			y=y-20
		Next
		
		If set_drawinterface=1 Then
			For i=0 To 19
				If in_siimg(i)<>0 Then
					If in_siimg_mask(i)=0 Then
						DrawBlock in_siimg(i),in_siimg_x(i),in_siimg_y(i)
					Else
						DrawImage in_siimg(i),in_siimg_x(i),in_siimg_y(i)
					EndIf
				EndIf
			Next
		EndIf	
	EndIf
	
	If m_menu<=Cmenu_if_quit Or m_menu>=Cmenu_if_buildsetup Then
		If set_drawinterface=1 Then
	
			Local perc
			con_unit(g_player)
			Local typ=TCunit\typ
			DrawImage gfx_if_values,0,0
			
			perc=(TCunit\health#/TCunit\health_max#)*100.
			If perc<=30 Then
				DrawBlockRect gfx_bars,5,4,0,0,perc,5,in_cursor
			Else
				DrawBlockRect gfx_bars,5,4,0,0,perc,5,0
			EndIf
		
			If game_healthsystem>=2 Then
				perc=(TCunit\hunger#/Dunit_store#(typ))*100.
				If perc>=70 Then
					DrawBlockRect gfx_bar_hunger,5,12,0,0,perc,5,in_cursor
				Else
					DrawBlockRect gfx_bar_hunger,5,12,0,0,perc,5,1
				EndIf
		
				If game_healthsystem>=3 Then
					perc=(TCunit\thirst#/Dunit_store#(typ))*100.
					If perc>=70 Then
						DrawBlockRect gfx_bar_thirst,5,20,0,0,perc,5,in_cursor
					Else
						DrawBlockRect gfx_bar_thirst,5,20,0,0,perc,5,1
					EndIf
			
					If game_healthsystem>=4 Then
						perc=(TCunit\exhaustion#/Dunit_store#(typ))*100.
						If perc>=70 Then
							DrawBlockRect gfx_bar_exhaustion,5,28,0,0,perc,5,in_cursor
						Else
							DrawBlockRect gfx_bar_exhaustion,5,28,0,0,perc,5,1
						EndIf
						
					EndIf
				EndIf
			EndIf
			
			If m_menu=0 Or m_menu>=Cmenu_if_buildsetup Then
				x=200
				For Tstate.Tstate=Each Tstate
					If Tstate\parent_class=Cclass_unit Then
						If Tstate\parent_id=g_player Then
							If Tstate\typ<50 Then
								DrawImage gfx_state,x,2,in_statefxf
								DrawImage gfx_states,x,2,Dstate_frame(Tstate\typ)
								x=x+26
							EndIf
						EndIf
					EndIf
				Next
			EndIf
					
			If m_menu=0 Or m_menu>=Cmenu_if_buildsetup Then
				DrawImage gfx_if_weapon,set_scrx-137,set_scry-60
				DrawBlock gfx_if_itemback,set_scrx-5-45-40,set_scry-5-40
				If TCunit\player_weapon<>0 Then
					
					total=Ditem_rate(TCunit\player_weapon)
					current=(gt-TCunit\player_lastattack)
					If current>total Then current=total
					tperc#=Float(Float(total-current)/Float(total))
					If tperc#>0. Then
						in_wpnrdy=0
						in_wpnrdyg#=0.
						Color 255.*tperc#,0,0
						Rect set_scrx-5-45-40,set_scry-5-40,39,39,0
					ElseIf in_wpnrdy=0 Then
						in_wpnrdy=1
						in_wpnrdyg#=255
					EndIf
					If in_wpnrdyg#>0. Then
						Color 0,in_wpnrdyg#,0
						Rect set_scrx-5-45-40,set_scry-5-40,39,39,0
						in_wpnrdyg#=in_wpnrdyg#-(15.*f#)
					EndIf
					
					DrawImage Ditem_iconh(TCunit\player_weapon),set_scrx-5-45-40,set_scry-5-40
					
					Local weaponc=0
					For Titem.Titem=Each Titem
						If Titem\parent_class=Cclass_unit Then
							If Titem\parent_id=TCunit\id Then
								If Titem\typ=TCunit\player_weapon Then
									weaponc=Titem\count
								EndIf
							EndIf
						EndIf
					Next
					gui_iconboxc(set_scrx-5-45-40,set_scry-5-40,weaponc,1)
					
				EndIf
				
				Local ammo=0
				DrawBlock gfx_if_itemback,set_scrx-5-40,set_scry-5-40
				If TCunit\player_ammo<>0 Then
					For Titem.Titem=Each Titem
						If Titem\parent_class=Cclass_unit Then
							If Titem\parent_id=TCunit\id Then
								If Titem\typ=TCunit\player_ammo Then
									ammo=Titem\count
								EndIf
							EndIf
						EndIf
					Next
					DrawImage Ditem_iconh(TCunit\player_ammo),set_scrx-5-40,set_scry-5-40
					gui_iconboxc(set_scrx-5-40,set_scry-5-40,ammo,1)
					If ammo=0 Then
						Color Abs(255.*Sin(in_ca#)),0,0
						Rect set_scrx-5-40,set_scry-5-40,40,40,0
					EndIf
				EndIf
				
			
				If m_menu=0 Then
					If set_drawinterface=1 Then
						If in_compass=1 Then
							cox=set_scrx-46
							coy=20
							DrawImage gfx_if_compass,cox,coy
							a#=EntityYaw(cam)
							bmpf_txt_c(cox+2-Sin(a)*34,coy+14+Cos(a)*34,s$(40),Cbmpf_red)
							bmpf_txt_c(cox+2-Sin(a+90)*34,coy+14+Cos(a+90)*34,s$(41),0)
							bmpf_txt_c(cox+2-Sin(a+180)*34,coy+14+Cos(a+180)*34,s$(42),Cbmpf_green)
							bmpf_txt_c(cox+2-Sin(a+270)*34,coy+14+Cos(a+270)*34,s$(43),0)
						EndIf
					EndIf
				EndIf
				
			EndIf
			
		EndIf
	
	EndIf
	
	
	If m_menu=0 Then
		If alive=1 Then
			If in_focus<>0 Then
				focus_draw()
			ElseIf g_dive<>0 Then
				If game_divetime<>-1 Then
					If (gt-g_airtimer)>=game_divetime Then
						bmpf_txt_c(set_scrx/2,35,sm$(168),Cbmpf_red)
						If in_cursor Then
							gui_healthbar#(set_scrx/2-51,55,0,1,1,gfx_bar_thirst)
						EndIf
					Else
						bmpf_txt_c(set_scrx/2,35,sm$(168))
						gui_healthbar#(set_scrx/2-51,55,game_divetime-(gt-g_airtimer),game_divetime,1,gfx_bar_thirst)
					EndIf
				EndIf
			EndIf
		EndIf
	EndIf
	
	If m_menu=0 Or m_menu>=Cmenu_if_buildsetup Then
		For i=20 To 39
			If in_siimg(i)<>0 Then
				If in_siimg_mask(i)=0 Then
					DrawBlock in_siimg(i),in_siimg_x(i),in_siimg_y(i)
				Else
					DrawImage in_siimg(i),in_siimg_x(i),in_siimg_y(i)
				EndIf
			EndIf
		Next
		If set_debug=1 Then
			y=100
		Else
			y=0
		EndIf		
		If in_compass=1 Then y=y+100
		For i=0 To 19
			If in_sitxt(i)=1 Then
				If in_sitxt_align(i)=-1 Then
					bmpf_txt_r(set_scrx-3,y,in_sitxt_txt$(i),in_sitxt_font(i))
					y=y+16
				Else
					Select in_sitxt_align(i)
						Case 1 bmpf_txt(in_sitxt_x(i),in_sitxt_y(i),in_sitxt_txt$(i),in_sitxt_font(i))
						Case 2 bmpf_txt_c(in_sitxt_x(i),in_sitxt_y(i),in_sitxt_txt$(i),in_sitxt_font(i))
						Case 3 bmpf_txt_r(in_sitxt_x(i),in_sitxt_y(i),in_sitxt_txt$(i),in_sitxt_font(i))
						Default bmpf_txt(in_sitxt_x(i),in_sitxt_y(i),in_sitxt_txt$(i),in_sitxt_font(i))
					End Select
				EndIf
			EndIf
		Next	
	EndIf
	
	If m_menu>0 Then
		If m_menu<=Cmenu_if_quit Then
			
			If gui_button(5,60,sm$(141),alive,Cicon_figure) Then
				m_menu=Cmenu_if_char
			EndIf
			
			If gui_button(5,60+50,sm$(142),alive,Cicon_bag) Then
				m_menu=Cmenu_if_items
				if_unselectitems()
				in_scr_scr=0
			EndIf
			
			If gui_button(5,60+100,sm$(143),alive,Cicon_dialog) Then
				m_menu=Cmenu_if_diary
				in_scr_scr=0
				in_scr_scr2=0
				in_opt(0)=0
				if_lastentry()
			EndIf
			
			If game_m_loadsave=1 Then
				If gui_button(5,(set_scry/2),sm$(144),1,Cicon_load) Then
					If m_menu=Cmenu_if_loadgame Then
						loadtemp$=""
						i=0
						For Tsg.Tsg=Each Tsg
							i=i+1
							If i=in_opt(0) Then
								loadtemp$=Tsg\name$
								Exit
							EndIf
						Next
						If loadtemp$<>"" Then
							load_map("saves\"+loadtemp$+".sav","")
							m_menu=0
							set_debug_testmap=0
						EndIf
					Else
						load_savegames()
						in_scr_scr=0
						m_menu=Cmenu_if_loadgame
					EndIf
				EndIf
				
				If gui_button(5,(set_scry/2)+50,sm$(145),alive,Cicon_save) Then
					If m_menu=Cmenu_if_savegame Then
						loadtemp$=in_input$(0)
						If loadtemp$<>"" Then
							If save_map("saves\"+loadtemp$+".sav","sav","") Then m_menu=0
						EndIf
					Else
						load_savegames()
						in_scr_scr=0
						m_menu=Cmenu_if_savegame
					EndIf
				EndIf
			EndIf
			
			If gui_button(5,(set_scry)-100,sm$(47),1,Cicon_x) Then
				If gui_msgdecide(sm$(191),Cbmpf_red,sm$(2),sm$(1)) Then
					menu_start()
				EndIf
			EndIf
			
			If gui_button(5,(set_scry)-50,sm$(15),1,Cicon_x) Then m_menu=0
			
			DrawImage gfx_win,215,0
			DrawImage gfx_winbar,215,42
			If gui_ibutton(742,5,Cicon_x,sm$(15)) Then m_menu=0
					
			If alive=0 Then
				Select m_menu
					Case 0,Cmenu_if_loadgame
					Case Cmenu_if_debugmenu,Cmenu_if_debugmenu_item
					Default
						load_savegames():in_scr_scr=0:m_menu=Cmenu_if_loadgame
				End Select
			EndIf
			
			
			Select m_menu
			
				Case Cmenu_if_char
				
					DrawImage gfx_icons,236,5,Cicon_figure
					bmpf_txt(236+37,10,sm$(141))
					
					bmpf_txt(236,63,sm$(146)+":")
					perc=(TCunit\health#/TCunit\health_max#)*100.
					DrawBlock gfx_if_barback,435,69
					DrawBlockRect gfx_bars,436,70,0,0,perc,5
					bmpf_txt(546,63,Int(perc)+"% ("+Int(TCunit\health#)+"/"+Int(TCunit\health_max#)+")")
					
					If game_healthsystem>=2 Then
						bmpf_txt(236,83,sm$(147)+":")
						perc=(TCunit\hunger#/Dunit_store#(typ))*100.
						DrawBlock gfx_if_barback,435,89
						DrawBlockRect gfx_bar_hunger,436,90,0,0,perc,5
						bmpf_txt(546,83,Int(perc)+"%")
					
						If game_healthsystem>=3 Then
							bmpf_txt(236,103,sm$(148)+":")
							perc=(TCunit\thirst#/Dunit_store#(typ))*100.
							DrawBlock gfx_if_barback,435,109
							DrawBlockRect gfx_bar_thirst,436,110,0,0,perc,5
							bmpf_txt(546,103,Int(perc)+"%")
							
							If game_healthsystem>=4 Then
								bmpf_txt(236,123,sm$(149)+":")
								perc=(TCunit\exhaustion#/Dunit_store#(typ))*100.
								DrawBlock gfx_if_barback,435,129
								DrawBlockRect gfx_bar_exhaustion,436,130,0,0,perc,5
								bmpf_txt(546,123,Int(perc)+"%")
							EndIf
						EndIf
					EndIf
					
					x=0
					For Tstate.Tstate=Each Tstate
						If Tstate\parent_class=Cclass_unit Then
							If Tstate\parent_id=g_player Then
								If Tstate\typ<50 Then
									DrawImage gfx_state,236+x,150,in_statefxf
									DrawImage gfx_states,236+x,150,Dstate_frame(Tstate\typ)
									If in_mx>=236+x Then
										If in_my>=150 Then 
											If in_mx<=236+x+26 Then
												If in_my<=150+26 Then
													gui_tt(in_mx,in_my,Dstate_name$(Tstate\typ))
												EndIf
											EndIf
										EndIf
									EndIf
									x=x+26
								EndIf
							EndIf
						EndIf
					Next
					
					y=0
					For Tx.Tx=Each Tx
						If Tx\mode=5 Then
							If Tx\stuff$<>"" Then
								DrawImage gfx_check(2),236,191+y
								If Tx\value$<>0 Then
									If gui_txtb(255,190+y,Tx\stuff$+" ("+Tx\value$+")") Then
										parse_globalevent("skill_"+Replace(Tx\key$," ","_"),"menu click at skill")
									EndIf
								Else
									If gui_txtb(255,190+y,Tx\stuff$) Then
										parse_globalevent("skill_"+Replace(Tx\key$," ","_"),"menu click at skill")
									EndIf
								EndIf
								y=y+20
							EndIf
						EndIf
					Next
					
					
					bmpf_txt(236,535,se$(162)+ ":")
					bmpf_txt(346,535,map_day)
					
					bmpf_txt(236,555,sm$(163)+":")
					bmpf_txt(346,555,map_hour+" "+sm$(36))
					
					If gui_ibutton(742,542,Cicon_sleep,sm$(164)) Then
						game_sleep()
						m_menu=0
					EndIf
					
				
				Case Cmenu_if_items			
					DrawImage gfx_icons,236,5,Cicon_bag
					bmpf_txt(236+37,10,sm$(142))
					
					If qol_inventory_enabled() Then
						selcount=qol_inventory_grid():selx=tmp_iselx:sely=tmp_isely
					Else
					For x=0 To 10
						For y=0 To 4
							DrawBlock gfx_if_itemback,236+x*45,63+y*45
						Next
					Next
					x=0:y=0:j=0:selcount=0
					selx=0:sely=0
					
					For Titem.Titem=Each Titem
						If Titem\parent_class=Cclass_unit Then
							If Titem\parent_id=g_player Then
								j=j+1
								
								If j>in_scr_scr And y<225 Then
									typ=Titem\typ
									id=Titem\id
									If gui_iconbox(236+x,63+y,Ditem_iconh(typ),Ditem_name$(typ),Titem\if_sel=1) Then
										FlushKeys()
										Titem\if_sel=1-Titem\if_sel
									EndIf
									If gui_dndwatch(236+x,63+y) Then
										in_dnd=1
										in_dnd_pivotx=in_mx-(236+x)
										in_dnd_pivoty=in_my-(63+y)
										in_dnd_src=0
										in_dnd_typ=typ
										in_dnd_x=in_mx
										in_dnd_y=in_my
										in_dnd_show=0
									EndIf
									gui_iconboxc(236+x,63+y,Titem\count)
									For i=1 To 9
										If in_quickslot(i)=Titem\typ Then
											gui_iconboxqs(236+x,63+y,i)
											Exit
										EndIf
									Next
									If Titem\if_sel=1 Then
										selcount=selcount+1
										TCitem.Titem=Titem
										selx=236+x
										sely=63+y
										tmp_iselx=selx
										tmp_isely=sely
										tmp_iseltyp=Titem\typ
									EndIf
									x=x+45
									If x>=495 Then
										x=0
										y=y+45
									EndIf
								Else
									If Titem\if_sel=1 Then
										selcount=selcount+1
										TCitem.Titem=Titem
										selx=236+x
										sely=63+y
										tmp_iselx=selx
										tmp_isely=sely
										tmp_iseltyp=Titem\typ
									EndIf
								EndIf
							EndIf
						EndIf
					Next				
				
					If gui_ibutton(742,63,Cicon_up,sm$(29),in_scr_scr>0) Then
						in_scr_scr=in_scr_scr-11
						If in_scr_scr<0 Then in_scr_scr=0
					EndIf
					If gui_ibutton(742,63+180+10,Cicon_down,sm$(30),y>=225) Then
						in_scr_scr=in_scr_scr+11
					EndIf
					If in_mzs#>0. And in_scr_scr>0 Then
						in_scr_scr=in_scr_scr-11
						If in_scr_scr<0 Then in_scr_scr=0
					EndIf
					If in_mzs#<0. And y>225 Then in_scr_scr=in_scr_scr+11
					
						
					EndIf
					x=0
					For Titem.Titem=Each Titem
						If Titem\parent_class=Cclass_unit Then
							If Titem\parent_id=g_player Then
								x=x+(Ditem_weight(Titem\typ)*Titem\count)
							EndIf
						EndIf
					Next
					total=Dunit_maxweight(1)
					bmpf_txt(236,285,ss$(sm$(150),numsep$(x),numsep$(total),numsep$(total-x)),Cbmpf_tiny)
					
					perc=(Float(x)/Float(total))*100.
					DrawBlock gfx_if_barback,500,289
					DrawBlockRect gfx_bars,501,290,0,0,100-perc,5
					

					If selcount=1 Then
						If TCitem\count=1 Then
							bmpf_txt(236,305,Ditem_name$(TCitem\typ)+" ("+TCitem\count+" x "+numsep$(Ditem_weight(TCitem\typ))+"g)")
						Else
							bmpf_txt(236,305,Ditem_name$(TCitem\typ)+" ("+TCitem\count+" x "+numsep$(Ditem_weight(TCitem\typ))+"g = "+numsep$(Ditem_weight(TCitem\typ)*TCitem\count)+"g)")
						EndIf
					EndIf
					
					If selcount=1 Then
						If Ditem_info$(TCitem\typ)<>"" Then
							bmpf_txt_rect(236,325,480+64,150,Ditem_info$(TCitem\typ),Cbmpf_dark)
						EndIf
					EndIf
					
					msgy=520
					For Tmsg.Tmsg=Each Tmsg
						If ms-Tmsg\age>0 Then
							Delete Tmsg
						Else
							bmpf_txt(236,msgy,Tmsg\msg$,Tmsg\col)
							msgy=msgy-20
							If msgy<440 Then Exit
						EndIf
					Next
					
					actv=1
					If in_focus>99 Then
						If in_focus<>Cstate_pc_build Then
							actv=0
						EndIf
					EndIf
					
					
					ix=236
					
					If gui_ibutton(ix,542,Cicon_check,sm$(151),selcount=1 And actv=1,0,18) Then
						use_item(TCitem\id)
					EndIf
					
					ix=ix+37
					If gui_ibutton(ix,542,Cicon_eat,sm$(194),selcount=1 And actv=1,0,19) Then
						eat_item(TCitem\id)
					EndIf
					
					ix=ix+37+37
					If gui_ibutton(ix,542,Cicon_hand,sm$(188),selcount=1 And actv=1,0,20) Then
						If Handle(g_cplayer.Tunit) Then
							game_useasweapon(TCitem\typ)
							m_menu=0
						EndIf
					EndIf
					
					ix=ix+37+37
					If gui_ibutton(ix,542,Cicon_inc,sm$(152),selcount>1 And actv=1,0,31) Then
						combine_item(Cclass_unit,g_player)
					EndIf
					
					ix=ix+37+37
					If gui_ibutton(ix,542,Cicon_x,sm$(153)+" (1x)",selcount=1 And actv=1,0,32) Then
						p_skipevent=0
						If set_parsecache(Cclass_item,TCitem\id,"drop") Then parse()
						If Instr(Ditem_scriptk$(TCitem\typ),"drop") Then
							parse_env(Cclass_item,TCitem\id,"drop")
							loadstring_parsecache(Ditem_script(TCitem\typ))
							parse()
						EndIf
						If p_skipevent=0 Then
							p_add2d(selx,sely,-2,TCitem\typ)
							If unstore_item(TCitem\id,1,1) Then merge_item(TCitem\id)
						EndIf
					EndIf
					
					If selcount=1 Then
						If Handle(TCitem)<>0 Then
							If TCitem\count>5 Then
								half=TCitem\count/2
								ix=ix+37
								If gui_ibutton(ix,542,Cicon_x,sm$(153)+" ("+half+"x)",selcount=1 And actv=1) Then
									p_skipevent=0
									If set_parsecache(Cclass_item,TCitem\id,"drop") Then parse()
									If Instr(Ditem_scriptk$(TCitem\typ),"drop") Then
										parse_env(Cclass_item,TCitem\id,"drop")
										loadstring_parsecache(Ditem_script(TCitem\typ))
										parse()
									EndIf
									If p_skipevent=0 Then
										For i=1 To half
											p_add2d(selx,sely,-2,TCitem\typ)
											If i=10 Then Exit
										Next
										If unstore_item(TCitem\id,half,1) Then merge_item(TCitem\id)
									EndIf
								EndIf
								bmpf_txt_c(ix+16,542+19,half,Cbmpf_tiny)
								ix=ix+37
								half=TCitem\count
								If gui_ibutton(ix,542,Cicon_x,sm$(153)+" ("+TCitem\count+"x)",selcount=1 And actv=1) Then
									p_skipevent=0
									If set_parsecache(Cclass_item,TCitem\id,"drop") Then parse()
									If Instr(Ditem_scriptk$(TCitem\typ),"drop") Then
										parse_env(Cclass_item,TCitem\id,"drop")
										loadstring_parsecache(Ditem_script(TCitem\typ))
										parse()
									EndIf
									If p_skipevent=0 Then
										For i=1 To half
											p_add2d(selx,sely,-2,TCitem\typ)
											If i=20 Then Exit
										Next
										If unstore_item(TCitem\id,TCitem\count,1) Then merge_item(TCitem\id)
									EndIf
								EndIf
								bmpf_txt_c(ix+16,542+19,TCitem\count,Cbmpf_tiny)
							EndIf
						EndIf
					EndIf
					
					If selcount=1 Then
						If KeyHit(2) Then if_quickslot(1,1)
						If KeyHit(3) Then if_quickslot(2,1)
						If KeyHit(4) Then if_quickslot(3,1)
						If KeyHit(5) Then if_quickslot(4,1)
						If KeyHit(6) Then if_quickslot(5,1)
						If KeyHit(7) Then if_quickslot(6,1)
						If KeyHit(8) Then if_quickslot(7,1)
						If KeyHit(9) Then if_quickslot(8,1)
						If KeyHit(10) Then if_quickslot(9,1)
					EndIf
					
					
					qol_inventory_toolbar()

				Case 28:qol_recipe_draw()

				Case Cmenu_if_diary
					DrawImage gfx_icons,236,5,Cicon_dialog
					bmpf_txt(236+37,10,sm$(143))
								
					i=0:j=0
					y=0
					For Tx.Tx=Each Tx
						If Tx\mode=2 Then
							j=j+1
							If j>in_scr_scr Then
								If gui_opt(236,63+i*20,Tx\key$,j,0) Then
									in_scr_scr2=0
								EndIf
								i=i+1
								If i>4 Then Exit
							EndIf
						EndIf
					Next
					
					If gui_ibutton(742,63,Cicon_up,sm$(29),in_scr_scr>0) Then
						in_scr_scr=in_scr_scr-1
					EndIf
					If gui_ibutton(742,163-32-5,Cicon_down,sm$(30),i>4) Then
						in_scr_scr=in_scr_scr+1
					EndIf
					
					DrawImage gfx_winbar,215,163
					
					DrawImage gfx_paperback,231,163+16
					
					i=0:j=0:y=0
					For Tx.Tx=Each Tx
						If Tx\mode=2 Then
							j=j+1
							If in_opt(0)=j Then
								i=bmpf_txt_rect(236,163+16+5,480,390,Tx\value$,Cbmpf_handwriting,in_scr_scr2)
								y=1
								Exit
							EndIf
						EndIf
					Next
					If y=0 Then i=1
					
					If gui_ibutton(742,163+16+5,Cicon_up,sm$(29),in_scr_scr2>0) Then
						in_scr_scr2=in_scr_scr2-1
					EndIf
					If gui_ibutton(742,542-37,Cicon_down,sm$(30),i=0) Then
						in_scr_scr2=in_scr_scr2+1
					EndIf
					If in_mzs#>0. And in_scr_scr2>0 Then in_scr_scr2=in_scr_scr2-1
					If in_mzs#<0. And i=0 Then in_scr_scr2=in_scr_scr2+1
					
					in_scr_scr2=gui_scrollbar(742,221,537-221-37,tmp_lines,in_scr_scr2,19)
					
					If gui_ibutton(742,542,Cicon_x,sm$(15)) Then m_menu=0
				
				
				Case Cmenu_if_loadgame
					DrawImage gfx_icons,236,5,Cicon_load
					bmpf_txt(236+37,10,sm$(154))
					
					Color 0,0,0
					Rect 236,195,500,379,1
					i=0:j=0
					y=0
					For Tsg.Tsg=Each Tsg
						j=j+1
						If j>in_scr_scr Then
							If gui_opt(236+5,200+i*20,Tsg\name$,j,0) Then
								load_map_header("saves\"+Tsg\name$+".sav")
							EndIf
							i=i+1
							If i>18 Then Exit
						EndIf
					Next
					
					If gui_ibutton(742,200,Cicon_up,sm$(29),in_scr_scr>0) Then
						in_scr_scr=in_scr_scr-1
					EndIf
					If gui_ibutton(742,542-37-37,Cicon_down,sm$(30),i>18) Then
						in_scr_scr=in_scr_scr+1
					EndIf
					If in_mzs#>0. And in_scr_scr>0 Then in_scr_scr=in_scr_scr-1
					If in_mzs#<0. And i>18 Then in_scr_scr=in_scr_scr+1
					
					c=0
					For Tsg.Tsg=Each Tsg
						c=c+1
					Next
					in_scr_scr=gui_scrollbar(742,237,500-237-37,c,in_scr_scr,19)
					
					loadtemp$=""
					i=0
					For Tsg.Tsg=Each Tsg
						i=i+1
						If i=in_opt(0) Then
							loadtemp$=Tsg\name$
							Exit
						EndIf
					Next
					If loadtemp$="" Then
						Color 0,0,0
						Rect 236,63,96,72,1
						bmpf_txt(336,63,sm$(156),Cbmpf_red)
					Else
						DrawBlock map_image,236,63
						bmpf_txt(336,63,sm$(157)+":")
						bmpf_txt(450,63,loadtemp$)
						bmpf_txt(336,83,sm$(158)+":")
						bmpf_txt(450,83,sg_date$)
						bmpf_txt(336,103,sm$(35)+":")
						bmpf_txt(450,103,sg_time$+" "+sm$(36))
					EndIf
					
					If gui_ibutton(742,542-37,Cicon_x,sm$(189),loadtemp$<>"") Then
						If gui_msgdecide(ss$(sm$(190),loadtemp$),Cbmpf_red,sm$(2),sm$(1)) Then
							DeleteFile("saves\"+loadtemp$+".sav")
							load_savegames()
							in_scr_scr=0
						EndIf
					EndIf
					
					If gui_ibutton(742,542,Cicon_load,sm$(154),loadtemp$<>"") Then
						load_map("saves\"+loadtemp$+".sav","")
						m_menu=0
						set_debug_testmap=0
					EndIf
				
					
				Case Cmenu_if_savegame
					DrawImage gfx_icons,236,5,Cicon_save
					bmpf_txt(236+37,10,sm$(155))
					
					bmpf_txt(236,150,sm$(157)+":")
					gui_input(236,170,320,0)
					
					i=0
					Local matched=0
					For Tsg.Tsg=Each Tsg
						i=i+1
						If Tsg\name$=in_input$(0) Then
							matched=1
							If in_opt(0)<>i Then
								in_opt(0)=i
								load_map_header("saves\"+Tsg\name$+".sav")
							EndIf
						EndIf
					Next
					If matched=0 Then
						in_opt(0)=0
					EndIf
					
					Color 0,0,0
					Rect 236,195,500,379,1
					i=0:j=0
					y=0
					For Tsg.Tsg=Each Tsg
						j=j+1
						If j>in_scr_scr Then
							If gui_opt(236+5,200+i*20,Tsg\name$,j,0) Then
								load_map_header("saves\"+Tsg\name$+".sav")
								in_input$(0)=Tsg\name$
							EndIf
							i=i+1
							If i>18 Then Exit
						EndIf
					Next
					
					If gui_ibutton(742,200,Cicon_up,sm$(29),in_scr_scr>0) Then
						in_scr_scr=in_scr_scr-1
					EndIf
					If gui_ibutton(742,542-37-37,Cicon_down,sm$(30),i>18) Then
						in_scr_scr=in_scr_scr+1
					EndIf
					If in_mzs#>0. And in_scr_scr>0 Then in_scr_scr=in_scr_scr-1
					If in_mzs#<0. And i>18 Then in_scr_scr=in_scr_scr+1
					
					c=0
					For Tsg.Tsg=Each Tsg
						c=c+1
					Next
					in_scr_scr=gui_scrollbar(742,237,500-237-37,c,in_scr_scr,19)
					
					loadtemp$=""
					i=0
					For Tsg.Tsg=Each Tsg
						i=i+1
						If i=in_opt(0) Then
							loadtemp$=Tsg\name$
							Exit
						EndIf
					Next
					If loadtemp$="" Then
						loadtemp$=in_input$(0)
						Color 0,0,0
						Rect 236,63,96,72,1
						bmpf_txt(336,63,sm$(157)+":")
						bmpf_txt(450,63,loadtemp$,Cbmpf_over)
						bmpf_txt(336,83,sm$(158)+":")
						bmpf_txt(450,83,CurrentDate())
						bmpf_txt(336,103,sm$(35)+":")
						bmpf_txt(450,103,CurrentTime()+" "+sm$(36))
					Else
						DrawBlock map_image,236,63
						bmpf_txt(336,63,sm$(157)+":")
						bmpf_txt(450,63,loadtemp$)
						bmpf_txt(336,83,sm$(158)+":")
						bmpf_txt(450,83,sg_date$)
						bmpf_txt(336,103,sm$(35)+":")
						bmpf_txt(450,103,sg_time$+" "+sm$(36))
					EndIf
					
					If gui_ibutton(742,542-37,Cicon_x,sm$(189),in_opt(0)<>0) Then
						If gui_msgdecide(ss$(sm$(190),loadtemp$),Cbmpf_red,sm$(2),sm$(1)) Then
							DeleteFile("saves\"+loadtemp$+".sav")
							load_savegames()
							in_scr_scr=0
						EndIf
					EndIf
					
					If gui_ibutton(742,542,Cicon_save,sm$(155),loadtemp$<>"") Then
						If save_map("saves\"+loadtemp$+".sav","sav","") Then m_menu=0
					EndIf
					
				
				Case Cmenu_if_msg
					DrawImage gfx_icons,236,5,Cicon_dialog
					bmpf_txt(236+37,10,in_msgtitle$)
					
					btc=0
					For i=0 To 9
						If in_sb(i)=1 Then btc=btc+1
					Next
					
					i=bmpf_txt_rect(236,63,480,500-(btc*37),in_msgtext$,Cbmpf_norm,in_scr_scr)
					If btc>0 Then btc=btc-1
					
					If gui_ibutton(742,63,Cicon_up,sm$(29),in_scr_scr>0) Then
						in_scr_scr=in_scr_scr-1
					EndIf
					If gui_ibutton(742,542-(btc*37),Cicon_down,sm$(30),i=0) Then
						in_scr_scr=in_scr_scr+1
					EndIf
					
					If in_mzs#>0. And in_scr_scr>0 Then in_scr_scr=in_scr_scr-1
					If in_mzs#<0. And i=0 Then in_scr_scr=in_scr_scr+1
					in_scr_scr=gui_scrollbar(742,63+37,542-(btc*37)-63-42,tmp_lines,in_scr_scr,tmp_linesvisible)
		
					y=542-(btc*37)
					For i=0 To 9
						If in_sb(i)=1 Then
							If in_sb_icon(i)>-1 Then
								If gui_ibutton(236,y,in_sb_icon(i),in_sb_txt$(i)) Then
									If parse_isevent(in_sb_scr$(i)) Then
										parse_globalevent(in_sb_scr$(i),"Script Button Click")
									Else
										parse_task(0,0,"click","Script Button Click",in_sb_scr$(i))
									EndIf
								EndIf
							Else
								If gui_ibuttona(236,y,in_sb_handle(i),in_sb_txt$(i)) Then
									If parse_isevent(in_sb_scr$(i)) Then
										parse_globalevent(in_sb_scr$(i),"Script Button Click")
									Else
										parse_task(0,0,"click","Script Button Click",in_sb_scr$(i))
									EndIf
								EndIf
							EndIf
							bmpf_txt(236+40,y+7,in_sb_txt$(i),0)
							y=y+37
						EndIf
					Next


				Case Cmenu_if_dlg
					DrawImage gfx_icons,236,5,Cicon_dialog
					bmpf_txt(236+37,10,in_msgtitle$)
					
					btc=0
					For i=0 To 9
						If in_sb(i)=1 Then btc=btc+1
					Next
					
					tradec=0
					For Trd.Ttrade=Each Ttrade
						If Trd\id>tradec Then tradec=Trd\id
					Next
					If tradec=0 Then
						i=bmpf_txt_rect(236,63,480,500-(btc*37),in_msgtext$,Cbmpf_norm,in_scr_scr)
					Else
						i=1
						y=63
						x=236
						For Trd.Ttrade=Each Ttrade
							If Trd\mode=0 Then
								If Trd\id>in_scr_scr Then
									c=0
									For Trd2.Ttrade=Each Ttrade
										If Trd2\id=Trd\id And Trd2\mode=1 Then c=c+1
									Next
									x=236
									For Trd2.Ttrade=Each Ttrade
										If Trd2\id=Trd\id Then
											If Trd2\mode=1 Then
												DrawBlock gfx_if_itemback,x,y
												DrawImage Ditem_iconh(Trd2\typ),x,y
												
												exc=countstored_items(Cclass_unit,g_player,Trd2\typ)
												If exc>=Trd2\count Then
													gui_iconboxc(x,y,Trd2\count,1)
												Else
													If in_cursor Then gui_iconboxc(x,y,Trd2\count,1,1)
												EndIf
												
												If in_mx>=x And in_mx<=x+40 Then
													If in_my>=y And in_my<=y+40 Then
														gui_tt(in_mx,in_my,ss$(sm$(203),Trd2\count,Ditem_name(Trd2\typ),exc))
													EndIf
												EndIf
												
												x=x+45
											EndIf
										EndIf
									Next
									c=0
									For Trd2.Ttrade=Each Ttrade
										If Trd2\id=Trd\id And Trd2\mode=2 Then c=c+1
									Next
									x=236+500-40
									For Trd2.Ttrade=Each Ttrade
										If Trd2\id=Trd\id Then
											If Trd2\mode=2 Then
												DrawBlock gfx_if_itemback,x,y
												DrawImage Ditem_iconh(Trd2\typ),x,y
												gui_iconboxc(x,y,Trd2\count,1)
												
												If in_mx>=x And in_mx<=x+40 Then
													If in_my>=y And in_my<=y+40 Then
														exc=countstored_items(Cclass_unit,g_player,Trd2\typ)
														gui_tt(in_mx,in_my,ss$(sm$(204),Trd2\count,Ditem_name(Trd2\typ),exc))
													EndIf
												EndIf
												
												x=x-45
											EndIf
										EndIf
									Next
									over=0
									If in_mx>=236 And in_mx<=236+500 Then
										If in_my>=y And in_my<=y+40 Then
											over=1	
										EndIf
									EndIf
									If over=0 Then
										DrawImage gfx_icons_passive,236+250-16,y+4,Cicon_right
									Else
										DrawImage gfx_icons,236+250-16,y+4,Cicon_right
										If in_mrelease(1) Then
											play(sfx_click)
											over=1
											For Trd2.Ttrade=Each Ttrade
												If Trd2\id=Trd\id Then
													If Trd2\mode=1 Then
														If countstored_items(Cclass_unit,g_player,Trd2\typ)<Trd2\count Then
															over=0
															Exit
														EndIf
													EndIf
												EndIf
											Next
											If over=1 Then
												speech("positive")
												For Trd2.Ttrade=Each Ttrade
													If Trd2\id=Trd\id Then
														If Trd2\mode=1 Then
															If get_stored_item(Trd2\typ,Cclass_unit,g_player) Then
																free_item(TCitem\id,Trd2\count)
															EndIf
														EndIf
													EndIf
												Next
												For Trd2.Ttrade=Each Ttrade
													If Trd2\id=Trd\id Then
														If Trd2\mode=2 Then
															If set_item(-1,Trd2\typ,EntityX(cam),EntityY(cam),EntityZ(cam),Trd2\count)>=0 Then
																store_item(TCitem\id,Cclass_unit,g_player)
															EndIf
														EndIf
													EndIf
												Next
											Else
												speech("negative")
											EndIf
										EndIf
									EndIf
									y=y+50
									If y>(542-(btc*37)) Then
										If Trd\id<tradec Then i=0
										Exit
									EndIf
								EndIf
							EndIf
						Next
					EndIf
					If btc>0 Then btc=btc-1
					
					If gui_ibutton(742,63,Cicon_up,sm$(29),in_scr_scr>0) Then
						in_scr_scr=in_scr_scr-1
					EndIf
					If gui_ibutton(742,542-(btc*37),Cicon_down,sm$(30),i=0) Then
						in_scr_scr=in_scr_scr+1
					EndIf
					
					If in_mzs#>0. And in_scr_scr>0 Then in_scr_scr=in_scr_scr-1
					If in_mzs#<0. And i=0 Then in_scr_scr=in_scr_scr+1
					i=0
					y=63
					Repeat
						i=i+1
						y=y+50
						If y>(542-(btc*37)) Then Exit
					Forever
					in_scr_scr=gui_scrollbar(742,63+37,542-(btc*37)-63-42,tradec,in_scr_scr, i )
					
					y=542-(btc*37)
					For i=0 To 9
						If in_sb(i)=1 Then
							If in_sb_icon(i)>-1 Then
								If gui_ibutton(236,y,in_sb_icon(i),in_sb_txt$(i)) Then
									parse_dialogue(in_sb_scr$(i))
								EndIf
							Else
								If gui_ibuttona(236,y,in_sb_handle(i),in_sb_txt$(i)) Then
									parse_dialogue(in_sb_scr$(i))
								EndIf
							EndIf
							bmpf_txt(236+40,y+7,in_sb_txt$(i),0)
							y=y+37
						EndIf
					Next
			
	
				Case Cmenu_if_build
					DrawImage gfx_icons,236,5,Cicon_build
					bmpf_txt(236+37,10,sm$(159))
					
					x=0:y=0:j=0:selcount=0
					
					buildings=0
					buildingsvisible=0
					groups=0
					totalbuildings=0
					
					For grp.Tgroup=Each Tgroup
						If grp\class=Cclass_building Then
							groups=groups+1
						EndIf
					Next
					
					If groups>0 Then
					
						If in_buildg$="" Then
							
							y=63
							totalbuildings=0
							For grp.Tgroup=Each Tgroup
								If grp\class=Cclass_building Then
									buildings=0
									For Tbui.Tbui=Each Tbui
										If Tbui\mode=2 Then
											If Tbui\group$=grp\group$ Then
												locked=0
												For Tx.Tx=Each Tx
													If Tx\mode=3 Then
														If Tx\key$=Tbui\id Then locked=1:Exit
													EndIf
												Next
												If locked=0 Then
													buildings=buildings+1
												EndIf
											EndIf
										EndIf
									Next
									If game_showemptybuildinggroups=1 Then
										If gui_ibutton(236,y,Cicon_right,grp\name$,buildings>0) Then
											in_buildg$=grp\group$
										EndIf
										If buildings>0 Then
											bmpf_txt(236+40,y+7,grp\name$+" ("+buildings+")",0)
										Else
											bmpf_txt(236+40,y+7,grp\name$+" (0)",Cbmpf_dark)
										EndIf
										y=y+37
										totalbuildings=totalbuildings+buildings
									Else
										If buildings>0 Then
											If gui_ibutton(236,y,Cicon_right,grp\name$,buildings>0) Then
												in_buildg$=grp\group$
											EndIf
											bmpf_txt(236+40,y+7,grp\name$+" ("+buildings+")",0)
											y=y+37
											totalbuildings=totalbuildings+buildings
										EndIf
									EndIf
								EndIf
							Next
							
							If totalbuildings=0 Then
								bmpf_txt(236,y+20,sm$(160),Cbmpf_red)
							EndIf
							
						Else
							
							For Tbui.Tbui=Each Tbui
								If Tbui\mode=2 Then
									If Tbui\group$=in_buildg$
										locked=0
										For Tx.Tx=Each Tx
											If Tx\mode=3 Then
												If Tx\key$=Tbui\id Then locked=1:Exit
											EndIf
										Next
										If locked=0 Then
											buildings=buildings+1
										EndIf
									EndIf
								EndIf
							Next
							
							For Tbui.Tbui=Each Tbui
								If Tbui\mode=2 Then
									If Tbui\group$=in_buildg$
										locked=0
										For Tx.Tx=Each Tx
											If Tx\mode=3 Then
												If Tx\key$=Tbui\id Then locked=1:Exit
											EndIf
										Next
										If locked=0 Then
											j=j+1
											If j>in_scr_scr Then
												buildingsvisible=buildingsvisible+1
												typ=Tbui\typ
												id=Tbui\id
												DrawBlock gfx_if_itemback,236,63+y
												starty=y
												x=0
												For Tbuireq.Tbui=Each Tbui
													If Tbuireq\id=Tbui\id Then
														If Tbuireq\mode=0 Then
															DrawBlock gfx_if_itemback,436+x,63+y
															DrawImage Ditem_iconh(Tbuireq\typ),436+x,63+y
															gui_iconboxc(436+x,63+y,Tbuireq\count)
															x=x+45
															If x>=270 Then
																x=0
																y=y+45
																If y>=470 Then Exit
															EndIf
														EndIf
													EndIf
												Next
												over=0
												If in_mx>=236 Then
													If in_mx<=738 Then
														If in_my>=63+starty Then
															If in_my<=y+45+63 Then
																over=1
																If typ>0 Then
																	gui_tt(in_mx,in_my,ss$(sm$(161),Dobject_name$(typ)))
																Else
																	gui_tt(in_mx,in_my,ss$(sm$(161),Dunit_name$(Abs(typ))))
																EndIf
																If in_mrelease(1) Then
																	in_buildg$=""
																	game_buildsetup_start(typ)
																EndIf
															EndIf
														EndIf
													EndIf
												EndIf
												If set_debug=1 Then
													Color 100,100,100
													Rect 236,63+starty,738-236,(y+45+63)-(63+starty),0
												EndIf
												If typ>0 Then
													gui_iconbox(236,63+starty,Dobject_iconh(typ),ss$(sm$(161),Dobject_name$(typ)),0)
													bmpf_txt_rect(236+45,63+starty,150,50,Dobject_name$(typ),over)
												Else
													gui_iconbox(236,63+starty,Dunit_iconh(Abs(typ)),ss$(sm$(161),Dunit_name$(Abs(typ))),0)
													bmpf_txt_rect(236+45,63+starty,150,50,Dunit_name$(Abs(typ)),over)
												EndIf
												y=y+50
												If y>=470 Then Exit
											EndIf
										EndIf
									EndIf
								EndIf
							Next
							
							If gui_ibutton(236,63+y,Cicon_left,sm$(131)) Then
								in_buildg$=""
							EndIf
							
							If gui_ibutton(742,63,Cicon_up,sm$(29),in_scr_scr>0) Then
								in_scr_scr=in_scr_scr-1
								If in_scr_scr<0 Then in_scr_scr=0
							EndIf
							If gui_ibutton(742,542,Cicon_down,sm$(30),in_scr_scr<=buildings-9) Then
								in_scr_scr=in_scr_scr+1
							EndIf
							
							If in_mzs#>0. And in_scr_scr>0 Then in_scr_scr=in_scr_scr-1
							If in_mzs#<0. And in_scr_scr<=buildings-9 Then in_scr_scr=in_scr_scr+1
							in_scr_scr=gui_scrollbar(742,63+37,542-63-42,buildings,in_scr_scr,9)
						
						EndIf
					
					
					Else
						
						For Tbui.Tbui=Each Tbui
							If Tbui\mode=2 Then
								locked=0
								For Tx.Tx=Each Tx
									If Tx\mode=3 Then
										If Tx\key$=Tbui\id Then locked=1:Exit
									EndIf
								Next
								If locked=0 Then
									buildings=buildings+1
								EndIf
							EndIf
						Next
						
						For Tbui.Tbui=Each Tbui
							If Tbui\mode=2 Then
								locked=0
								For Tx.Tx=Each Tx
									If Tx\mode=3 Then
										If Tx\key$=Tbui\id Then locked=1:Exit
									EndIf
								Next
								If locked=0 Then
									j=j+1
									If j>in_scr_scr Then
										buildingsvisible=buildingsvisible+1
										typ=Tbui\typ
										id=Tbui\id
										DrawBlock gfx_if_itemback,236,63+y
										starty=y
										x=0
										For Tbuireq.Tbui=Each Tbui
											If Tbuireq\id=Tbui\id Then
												If Tbuireq\mode=0 Then
													DrawBlock gfx_if_itemback,436+x,63+y
													DrawImage Ditem_iconh(Tbuireq\typ),436+x,63+y
													gui_iconboxc(436+x,63+y,Tbuireq\count)
													x=x+45
													If x>=270 Then
														x=0
														y=y+45
														If y>=470 Then Exit
													EndIf
												EndIf
											EndIf
										Next
										over=0
										If in_mx>=236 Then
											If in_mx<=738 Then
												If in_my>=63+starty Then
													If in_my<=y+45+63 Then
														over=1
														If typ>0 Then
															gui_tt(in_mx,in_my,ss$(sm$(161),Dobject_name$(typ)))
														Else
															gui_tt(in_mx,in_my,ss$(sm$(161),Dunit_name$(Abs(typ))))
														EndIf
														If in_mrelease(1) Then
															game_buildsetup_start(typ)
														EndIf
													EndIf
												EndIf
											EndIf
										EndIf
										If set_debug=1 Then
											Color 100,100,100
											Rect 236,63+starty,738-236,(y+45+63)-(63+starty),0
										EndIf
										If typ>0 Then
											gui_iconbox(236,63+starty,Dobject_iconh(typ),ss$(sm$(161),Dobject_name$(typ)),0)
											bmpf_txt_rect(236+45,63+starty,150,50,Dobject_name$(typ),over)
										Else
											gui_iconbox(236,63+starty,Dunit_iconh(Abs(typ)),ss$(sm$(161),Dunit_name$(Abs(typ))),0)
											bmpf_txt_rect(236+45,63+starty,150,50,Dunit_name$(Abs(typ)),over)
										EndIf
										y=y+50
										If y>=470 Then Exit
									EndIf
								EndIf
							EndIf
						Next
						
						If gui_ibutton(742,63,Cicon_up,sm$(29),in_scr_scr>0) Then
							in_scr_scr=in_scr_scr-1
							If in_scr_scr<0 Then in_scr_scr=0
						EndIf
						If gui_ibutton(742,542,Cicon_down,sm$(30),in_scr_scr<=buildings-9) Then
							in_scr_scr=in_scr_scr+1
						EndIf
						
						If in_mzs#>0. And in_scr_scr>0 Then in_scr_scr=in_scr_scr-1
						If in_mzs#<0. And in_scr_scr<=buildings-9 Then in_scr_scr=in_scr_scr+1
						in_scr_scr=gui_scrollbar(742,63+37,542-63-42,buildings,in_scr_scr,9)
						
						If j=0 Then
							bmpf_txt(236,63,sm$(160),Cbmpf_red)
						EndIf
						
					EndIf
				
				
				Case Cmenu_if_combine
					DrawImage gfx_icons,236,5,Cicon_inc
					bmpf_txt(236+37,10,sm$(152))
					
					x=0:y=0:j=0
					gentyp=0
					
					For Tcom.Tcom=Each Tcom
						If Tcom\gid=in_combi_gid Then
							j=j+1
							If j>in_scr_scr Then
								
								locked=0
								For Tx.Tx=Each Tx
									If Tx\mode=50 Then
										If Tx\key$=Tcom\aid$ Then
											locked=1
											Exit
										EndIf
									EndIf
								Next
								
								x=0
								c=0
								If Tcom\genname$="" Then
									For Tgen.Tcom=Each Tcom
										If Tgen\id=Tcom\id Then
											If Tgen\mode=1 Then
												DrawBlock gfx_if_itemback,236+x,83+y
												DrawImage Ditem_iconh(Tgen\typ),236+x,83+y
												gui_iconboxc(236+x,63+y,Tgen\count)
												x=x+45
												c=c+1
												If c=1 Then gentyp=Tgen\typ
											EndIf
										EndIf
									Next
									txt$=""
									For Tgen.Tcom=Each Tcom
										If Tgen\id=Tcom\id Then
											If Tgen\mode=1 Then
												If txt$="" Then
													txt$=Ditem_name$(Tgen\typ)
												Else
													txt$=txt$+", "+Ditem_name$(Tgen\typ)
												EndIf
											EndIf
										EndIf
									Next
								Else
									txt$=Tcom\genname$
									DrawBlock gfx_if_itemback,236+x,83+y
									DrawImage gfx_icons,236+x+4,83+y+4,Cicon_options
									x=x+45
									c=999
								EndIf
								
								over=0
								If in_mx>=236 Then
									If in_mx<=738 Then
										If in_my>=83+y Then
											If in_my<=83+y+55 Then
												over=1
												If c=999 Then
													gui_tt(in_mx,in_my,txt$)
												Else
													gui_tt(in_mx,in_my,ss$(sm$(199),txt$))
												EndIf
												If in_mrelease(1) Then
													combine_item(Cclass_unit,g_player,Tcom\id)
												EndIf
											EndIf
										EndIf
									EndIf
								EndIf
								
								If c>0 Then
									If locked=0 Then
										bmpf_txt_rect(236+x,83+y,150,50,txt$,over)
									Else
										bmpf_txt_rect(236+x,83+y,150,50,txt$,Cbmpf_red)
									EndIf
								EndIf
								
								x=0
								For Treq.Tcom=Each Tcom
									If Treq\id=Tcom\id Then
										If Treq\mode=0 Then
											exc=countstored_items(Cclass_unit,g_player,Treq\typ)
											
											DrawBlock gfx_if_itemback,436+x,83+y
											DrawImage Ditem_iconh(Treq\typ),436+x,83+y
											
											If exc>=Treq\count Then
												gui_iconboxc(436+x,83+y,Treq\count,1)
											Else
												If in_cursor Then gui_iconboxc(436+x,83+y,Treq\count,1,1)
											EndIf
											
											If y=0 Then
												If exc=0 Then
													bmpf_txt_c(436+x+20,63+y,"0",Cbmpf_red)
												Else
													bmpf_txt_c(436+x+20,63+y,exc,0)
												EndIf
											EndIf
											x=x+45
										EndIf
									EndIf
								Next
								
								If c=1 Then
									exc=countstored_items(Cclass_unit,g_player,gentyp)
									If exc=0 Then
										bmpf_txt_r(742-20,83+y,"0",Cbmpf_red)
									Else
										bmpf_txt_r(742-20,83+y,exc,over)
									EndIf
								Else
									bmpf_txt_r(742-20,83+y,"-",over)
								EndIf
								
								If locked=1 Then
									If in_cursor Then bmpf_txt(438,83+y,sm$(200),Cbmpf_red)
								EndIf
								
								y=y+55
								If y>=470 Then Exit
								
							EndIf
						EndIf
					Next
					
					If gui_ibutton(236,83+y,Cicon_left,sm$(142)) Then
						m_menu=Cmenu_if_items
						if_unselectitems()
						in_scr_scr=0
					EndIf
					
					msgy=600-50
					For Tmsg.Tmsg=Each Tmsg
						If ms-Tmsg\age>0 Then
							Delete Tmsg
						Else
							bmpf_txt(236,msgy,Tmsg\msg$,Tmsg\col)
							msgy=msgy-20
							If msgy<63 Then Exit
						EndIf
					Next
					
					
					If gui_ibutton(742,63,Cicon_up,sm$(29),in_scr_scr>0) Then
						in_scr_scr=in_scr_scr-1
						If in_scr_scr<0 Then in_scr_scr=0
					EndIf
					If gui_ibutton(742,542,Cicon_down,sm$(30),y>=225) Then
						in_scr_scr=in_scr_scr+1
					EndIf
					
				
				Case Cmenu_if_find
					DrawImage gfx_icons,236,5,Cicon_zoom
					bmpf_txt(236+37,10,"[[loc:engine.dd097e77dd73]]")
					
					typ=in_input$(0)
					
					
					DrawBlock gfx_if_itemback,236,63
					DrawImage Dobject_iconh(typ),236,63
					bmpf_txt(236+45,63,Dobject_name$(typ),Cbmpf_norm)
					If con_object(Int(in_input$(1))) Then
						gui_healthbar#(236+45,63+20,TCobject\health#,TCobject\health_max#)
					EndIf
					
					DrawImage gfx_winbar,215,108
					
					y=40+5+16+5
					
					For Tfind.Tfind=Each Tfind
						If Tfind\parent=typ Then
							
							DrawBlock gfx_if_itemback,236,63+y
							If gui_iconbox(236,63+y,Ditem_iconh(Tfind\typ),"[[loc:engine.eea47c9824fb]]"+Ditem_name$(Tfind\typ)+"[[loc:engine.647e5a1db396]]",0) Then
								search_object(Int(in_input$(1)),Tfind\typ)
							EndIf
							bmpf_txt(236+45,63+5+y,Ditem_name$(Tfind\typ),Cbmpf_norm)
							
							If Tfind\ratio>90 Then
								bmpf_txt(550,63+5+y,"[[loc:engine.54cf9ac9a1a9]]",Cbmpf_green)
							ElseIf Tfind\ratio>70 Then
								bmpf_txt(550,63+5+y,"[[loc:engine.16e487609d4e]]",Cbmpf_green)
							ElseIf Tfind\ratio>30 Then
								bmpf_txt(550,63+5+y,"[[loc:engine.317b32c14369]]",Cbmpf_yellow)
							ElseIf Tfind\ratio>10 Then
								bmpf_txt(550,63+5+y,"[[loc:engine.fea55ac7ed0b]]",Cbmpf_red)
							Else
								bmpf_txt(550,63+5+y,"[[loc:engine.d38e8531ad5b]]",Cbmpf_red)
							EndIf
							
							y=y+55
							If y>=470 Then Exit
							
						EndIf
					Next
					
					
				Case Cmenu_if_exchange
					If Not qol_storage_draw() Then
					DrawImage gfx_icons,236,5,Cicon_bag
					Select in_ex_class
						Case Cclass_object
							If con_object(in_ex_id) Then
								bmpf_txt(236+37,10,ss(sm$(202),Dobject_name$(TCobject\typ)) )
							EndIf
						Case Cclass_unit
							If con_unit(in_ex_id) Then
								bmpf_txt(236+37,10,ss(sm$(202),Dunit_name$(TCunit\typ)) )
							EndIf
						Default
							bmpf_txt(236+37,10,sm$(201))
					End Select
					
					For x=0 To 10
						For y=0 To 4
							DrawBlock gfx_if_itemback,236+x*45,63+y*45
						Next
					Next
					x=0:y=0:j=0
					restrictedex=0
					If in_ex_only<>"" Then restrictedex=1
					For Titem.Titem=Each Titem
						If Titem\parent_class=Cclass_unit Then
							If Titem\parent_id=g_player Then
								j=j+1
								If j>in_scr_scr Then
									typ=Titem\typ
									id=Titem\id
									If gui_iconbox(236+x,63+y,Ditem_iconh(typ),Ditem_name$(typ),id=in_ex_sel) Then
										in_ex_sel=id
									EndIf
									If gui_dndwatch(236+x,63+y) Then
										in_dnd=1
										in_dnd_pivotx=in_mx-(236+x)
										in_dnd_pivoty=in_my-(63+y)
										in_dnd_src=0
										in_dnd_typ=typ
										in_dnd_x=in_mx
										in_dnd_y=in_my
										in_dnd_show=0
									EndIf
									If restrictedex=1 Then
										If Instr(in_ex_only,"|"+typ+"|")=0 Then
											DrawBlock gfx_if_itemback,236+x,63+y
											If Ditem_icondh(typ)=0 Then Ditem_icondh(typ)=makedark(Ditem_iconh(typ))
											DrawImage Ditem_icondh(typ),236+x,63+y
											If id=in_ex_sel Then Color Abs(255.*Sin(in_ca#)),0,0:Rect 236+x,63+y,40,40,0
										EndIf
									EndIf
									gui_iconboxc(236+x,63+y,Titem\count)
									If id=in_ex_sel Then
										TCitem.Titem=Titem
									EndIf
									x=x+45
									If x>=495 Then
										x=0
										y=y+45
										If y>=225 Then Exit
									EndIf
								EndIf
							EndIf
						EndIf
					Next				
				
					If gui_ibutton(742,63,Cicon_up,sm$(29),in_scr_scr>0) Then
						in_scr_scr=in_scr_scr-11
						If in_scr_scr<0 Then in_scr_scr=0
					EndIf
					If gui_ibutton(742,63+180+10,Cicon_down,sm$(30),y>=225) Then
						in_scr_scr=in_scr_scr+11
					EndIf
					
					x=0
					For Titem.Titem=Each Titem
						If Titem\parent_class=Cclass_unit Then
							If Titem\parent_id=g_player Then
								x=x+(Ditem_weight(Titem\typ)*Titem\count)
							EndIf
						EndIf
					Next
					total=Dunit_maxweight(1)
					bmpf_txt(236,285,ss$(sm$(150),numsep$(x),numsep$(total),numsep$(total-x)),Cbmpf_tiny)
					
					perc=(Float(x)/Float(total))*100.
					DrawBlock gfx_if_barback,500,289
					DrawBlockRect gfx_bars,501,290,0,0,100-perc,5
					

					For x=0 To 10
						For y=0 To 3
							DrawBlock gfx_if_itemback,236+x*45,347+y*45
						Next
					Next
					x=0:y=0:j=0
					For Titem.Titem=Each Titem
						If Titem\parent_class=in_ex_class Then
							If Titem\parent_id=in_ex_id Then
								j=j+1
								If j>in_scr_scr2 Then
									typ=Titem\typ
									id=Titem\id
									If gui_iconbox(236+x,347+y,Ditem_iconh(typ),Ditem_name$(typ),id=in_ex_sel) Then
										in_ex_sel=id
									EndIf
									If gui_dndwatch(236+x,347+y) Then
										in_dnd=1
										in_dnd_pivotx=in_mx-(236+x)
										in_dnd_pivoty=in_my-(347+y)
										in_dnd_src=1
										in_dnd_typ=typ
										in_dnd_x=in_mx
										in_dnd_y=in_my
										in_dnd_show=0
									EndIf
									If restrictedex=1 Then
										If Instr(in_ex_only,"|"+typ+"|")=0 Then
											DrawBlock gfx_if_itemback,236+x,347+y
											If Ditem_icondh(typ)=0 Then Ditem_icondh(typ)=makedark(Ditem_iconh(typ))
											DrawImage Ditem_icondh(typ),236+x,347+y
											If id=in_ex_sel Then Color Abs(255.*Sin(in_ca#)),0,0:Rect 236+x,347+y,40,40,0
										EndIf
									EndIf
									gui_iconboxc(236+x,347+y,Titem\count)
									If id=in_ex_sel Then
										TCitem.Titem=Titem
									EndIf
									x=x+45
									If x>=495 Then
										x=0
										y=y+45
										If y>=180 Then Exit
									EndIf
								EndIf
							EndIf
						EndIf
					Next				
					
					If gui_ibutton(742,347-37,Cicon_x,"") Then 
						m_menu=Cmenu_if_items
						in_scr_scr=0
						in_scr_scr2=0
						For Titem.Titem=Each Titem
							If Titem\parent_class<>Cclass_unit Or Titem\parent_id<>g_player Then
								Titem\if_sel=0
							EndIf
						Next
					EndIf
					
					If gui_ibutton(742,347,Cicon_up,sm$(29),in_scr_scr2>0) Then
						in_scr_scr2=in_scr_scr2-11
						If in_scr_scr2<0 Then in_scr_scr2=0
					EndIf
					If gui_ibutton(742,347+135+10,Cicon_down,sm$(30),y>=135) Then
						in_scr_scr2=in_scr_scr2+11
					EndIf
					
					x=0
					For Titem.Titem=Each Titem
						If Titem\parent_class=in_ex_class Then
							If Titem\parent_id=in_ex_id Then
								x=x+(Ditem_weight(Titem\typ)*Titem\count)
							EndIf
						EndIf
					Next
					Select in_ex_class
						Case Cclass_object
							con_object(in_ex_id)
							total=Dobject_maxweight(TCobject\typ)
							bmpf_txt(236,524,ss$(sm$(150),numsep$(x),numsep$(total),numsep$(total-x)),Cbmpf_tiny)
						Case Cclass_unit
							con_unit(in_ex_id)
							total=Dunit_maxweight(TCunit\typ)
							bmpf_txt(236,524,ss$(sm$(150),numsep$(x),numsep$(total),numsep$(total-x)),Cbmpf_tiny)
					End Select
					
					perc=(Float(x)/Float(total))*100.
					DrawBlock gfx_if_barback,500,528
					DrawBlockRect gfx_bars,501,529,0,0,100-perc,5
					
									
					x=0
					If gui_ibutton(410+37,305,Cicon_ex1,sm$(165),in_ex_sel>0) Then x=1
					If gui_ibutton(410+37+37,305,Cicon_ex5,sm$(166),in_ex_sel>0) Then x=2
					If gui_ibutton(410+37+37+37,305,Cicon_exall,sm$(167),in_ex_sel>0) Then x=3
					If x>0 Then
						Select x
							Case 1 c=1
							Case 2 c=5
							Case 3 c=TCitem\count
						End Select
						If if_exchange_allowed(TCitem\typ) Then
							If TCitem\parent_id=g_player And TCitem\parent_class=Cclass_unit Then
								If in_ex_allowstore=1 Then
									exreturn=exchange_item(in_ex_class,in_ex_id,c)
									If exreturn=0 Then
										if_msg(sm$(181),Cbmpf_red)
										sfx sfx_fail
									ElseIf exreturn=-1 Then
										if_msg(sm$(181),Cbmpf_red)
										sfx sfx_fail
										speech("nospace")
									EndIf
									If exreturn<>2 Then
										in_ex_sel=0
										For Titem.Titem=Each Titem
											If Titem\parent_class=Cclass_unit Then
												If Titem\parent_id=g_player Then
													in_ex_sel=Titem\id
													Exit
												EndIf
											EndIf
										Next
									EndIf
								Else
									speech("negative")
								EndIf
							Else
								exreturn=exchange_item(Cclass_unit,g_player,c)
								If exreturn=0 Then
									if_msg(sm$(181),Cbmpf_red)
									sfx sfx_fail
									speech("nospace")
								ElseIf exreturn=-1 Then
									if_msg(sm$(181),Cbmpf_red)
									sfx sfx_fail
								EndIf
								If exreturn<>2 Then
									in_ex_sel=0
									For Titem.Titem=Each Titem
										If Titem\parent_class=in_ex_class Then
											If Titem\parent_id=in_ex_id Then
												in_ex_sel=Titem\id
												Exit
											EndIf
										EndIf
									Next
								EndIf
							EndIf
						Else
							speech("negative")	
						EndIf
					EndIf
					
					If gui_ibutton(742,542,Cicon_ok,sm$(5)) Then m_menu=0					
				
					EndIf

				Case Cmenu_if_map
					if_map()
					
					
				Case Cmenu_if_cracklock
					DrawImage gfx_icons,236,5,Cicon_bag
					bmpf_txt(236+37,10,pl_cl_title$)
					
					keyin$=""
					
					bmpf_txt_c(489+16,306,pc_cl_pos,Cbmpf_dark)
					
					i=0
					If pc_cl_mode>0 Then i=1
					If gui_ibutton(489-37,300,Cicon_left,"",i,0,203) Then
						keyin$="l"
						sfx(sfx_crack(0))
					EndIf
					
					i=0
					If pc_cl_mode>1 Then i=1
					If gui_ibutton(489+37,300,Cicon_right,"",i,0,205) Then
						keyin$="r"
						sfx(sfx_crack(1))
					EndIf
					
					i=0
					If pc_cl_mode>2 Then i=1
					If gui_ibutton(489,300-37,Cicon_up,"",i,0,200) Then
						keyin$="u"
						sfx(sfx_crack(2))
					EndIf
					
					i=0
					If pc_cl_mode>3 Then i=1
					If gui_ibutton(489,300+37,Cicon_down,"",i,0,208) Then
						keyin$="d"
						sfx(sfx_crack(3))
					EndIf
					
					If keyin$<>"" Then
						If Mid(pc_cl_code$,pc_cl_pos,1)=keyin$ Then
							pc_cl_pos=pc_cl_pos+1
							If pc_cl_pos>Len(pc_cl_code$) Then
								parse_emuevent(pc_cl_class,pc_cl_id,"cracklock_success")
								m_menu=0
							EndIf
						Else
							pc_cl_pos=1
							parse_emuevent(pc_cl_class,pc_cl_id,"cracklock_failure")
							sfx(sfx_fail)
						EndIf
					EndIf
				
				Case Cmenu_if_debugmenu
					DrawImage gfx_icons,236,5,Cicon_zoom
					bmpf_txt(236+37,10,"[[loc:engine.1a03bd2fd107]]")
					
					y=63
					bmpf_txt(236,y,"Options")
					
					y=y+20
					If gui_ibutton(236,y,Cicon_zoom,"[[loc:engine.5f75368293f2]]") Then
						con_debug()
					EndIf
					bmpf_txt(273,y,"[[loc:engine.5f75368293f2]]")
					bmpf_txt(273,y+16,"Debug Mode ON/OFF",Cbmpf_tiny)
					y=y+37
					If gui_ibutton(236,y,Cicon_zoom,"[[loc:engine.89822504c47c]]") Then
						con_debugtimes()
					EndIf
					bmpf_txt(273,y,"[[loc:engine.89822504c47c]]")
					bmpf_txt(273,y+16,"Time Info ON/OFF",Cbmpf_tiny)
					y=y+37
					If gui_ibutton(236,y,Cicon_zoom,"[[loc:engine.65db93e004fb]]") Then
						con_debugsequences()
					EndIf
					bmpf_txt(273,y,"[[loc:engine.65db93e004fb]]")
					bmpf_txt(273,y+16,"Sequence Info ON/OFF",Cbmpf_tiny)
					y=y+37
					If gui_ibutton(236,y,Cicon_zoom,"[[loc:engine.9f8a2bc261a7]]") Then
						con_debugmap()
					EndIf
					bmpf_txt(273,y,"[[loc:engine.9f8a2bc261a7]]")
					bmpf_txt(273,y+16,"[[loc:engine.fff56dec324d]]",Cbmpf_tiny)
					y=y+37
					If gui_ibutton(236,y,Cicon_zoom,"[[loc:engine.0ed0fcccb937]]") Then
						con_debugscript()
						con_open()
					EndIf
					bmpf_txt(273,y,"[[loc:engine.0ed0fcccb937]]")
					bmpf_txt(273,y+16,"[[loc:engine.0c4ca94aab31]]",Cbmpf_tiny)
					
					y=y+47
					bmpf_txt(236,y,"[[loc:engine.eea246820d9d]]")
					
					y=y+20
					If gui_ibutton(236,y,Cicon_ok,"Invulnerability / Revive") Then
						cheat_invulnerable()
						m_menu=0
						if_msg("< invulnerability / revive cheat >",Cbmpf_yellow)
					EndIf
					bmpf_txt(273,y,"Invulnerability / Revive")
					bmpf_txt(273,y+16,"Invulnerability ON/OFF, revive when death",Cbmpf_tiny)
					y=y+37
					If gui_ibutton(236,y,Cicon_ok,"[[loc:engine.2bf8fb35dc4e]]") Then
						cheat_needs()
						m_menu=0
						if_msg("[[loc:engine.b80db5a99452]]",Cbmpf_yellow)
					EndIf
					bmpf_txt(273,y,"[[loc:engine.2bf8fb35dc4e]]")
					bmpf_txt(273,y+16,"No hunger/thirst/exhaustion",Cbmpf_tiny)
					y=y+37
					If gui_ibutton(236,y,Cicon_ok,"[[loc:engine.fdd75d9192bd]]") Then
						cheat_states()
						m_menu=0
						if_msg("[[loc:engine.34287c32b548]]",Cbmpf_yellow)
					EndIf
					bmpf_txt(273,y,"[[loc:engine.fdd75d9192bd]]")
					bmpf_txt(273,y+16,"[[loc:engine.946150f6405b]]",Cbmpf_tiny)
					y=y+37
					If gui_ibutton(236,y,Cicon_ok,"[[loc:engine.5cebc460f100]]") Then
						in_item_sel=0
						in_item_scr=0
						m_menu=Cmenu_if_debugmenu_items
					EndIf
					bmpf_txt(273,y,"[[loc:engine.5cebc460f100]]")
					bmpf_txt(273,y+16,"[[loc:engine.d0942abe8ba5]]",Cbmpf_tiny)
					y=y+37
					If gui_ibutton(236,y,Cicon_ok,"[[loc:engine.1cd3737e5b2f]]") Then
						If cheat_build() Then m_menu=0:if_msg("[[loc:engine.d9101cb28e60]]",Cbmpf_yellow)
					EndIf
					bmpf_txt(273,y,"[[loc:engine.1cd3737e5b2f]]")
					bmpf_txt(273,y+16,"[[loc:engine.2235e70a1d90]]",Cbmpf_tiny)
					y=y+37
					If gui_ibutton(236,y,Cicon_ok,"[[loc:engine.98025a5b1a8b]]") Then
						gui_tb_loadstring("","")
						in_scr_scr=0
						m_menu=Cmenu_if_debugmenu_script
					EndIf
					bmpf_txt(273,y,"[[loc:engine.98025a5b1a8b]]")
					bmpf_txt(273,y+16,"[[loc:engine.0f69fa8c94e4]]",Cbmpf_tiny)
				
				Case Cmenu_if_debugmenu_items
					DrawImage gfx_icons,236,5,Cicon_zoom
					bmpf_txt(236+37,10,"[[loc:engine.02baca8b3656]]")
					
					x=0:y=0:j=0
					x=236-5
					y=63
					For i=0 To Citem_count
						If Ditem_name$(i)<>"" Then
							j=j+1
							If j>in_item_scr Then
								DrawBlock gfx_if_itemback,5+x,y
								click=gui_iconbox(5+x,y,Ditem_iconh(i),Ditem_name$(i)+" ("+i+") -> left click +1 / right click +10",i=in_item_sel)
								If click>0 Then
									If click=1 Then
										cheat_item(i,1)
									ElseIf click=2 Then
										cheat_item(i,10)
									EndIf
								EndIf
								x=x+45
								If x>=495+231 Then
									x=236-5
									y=y+45
									If y>=540 Then Exit
								EndIf
							EndIf
						EndIf
					Next
							
					If gui_ibutton(742,63,Cicon_up,sm$(29),in_item_scr>0) Then
						in_item_scr=in_item_scr-11
						If in_item_scr<0 Then in_item_scr=0
					EndIf
					If gui_ibutton(742,542,Cicon_down,sm$(30),y>=540) Then
						in_item_scr=in_item_scr+11
					EndIf
					If in_mzs#>0. And in_item_scr>0 Then
						in_item_scr=in_item_scr-11
						If in_scr_scr<0 Then in_scr_scr=0
					EndIf
					If in_mzs#<0. And y>540 Then in_item_scr=in_item_scr+11
				
				Case Cmenu_if_debugmenu_script
					DrawImage gfx_icons,236,5,Cicon_zoom
					bmpf_txt(236+37,10,"[[loc:engine.dd7c8e12f442]]")
					
					in_inputfocus=1337
					
					gui_tb(236,85,538,29,1,1)
					
					If gui_ibutton(742,542,Cicon_ok, sm$(5)) Then
						cheatscript$=gui_tb_savestring$("")
						cheat_script(cheatscript$)
						m_menu=0
					EndIf
					If gui_ibutton(742-37,542,Cicon_x,sm$(3)) Then
						m_menu=0
					EndIf
					
					If gui_ibutton(236,542,Cicon_x,se$(141)) Then
						gui_tb_loadstring("","")
					EndIf
					
					If gui_ibutton(236+37+37,542,Cicon_load,se$(177)) Then
						If gui_win_file(se$(177),set_rootdir$,"",0,0) Then
							gui_tb_loadfile(in_win_path$+in_win_file$)
						EndIf
					EndIf
					If gui_ibutton(236+37+37+37,542,Cicon_save,se$(178)) Then
						If gui_win_file(se$(178),set_rootdir$,"",0,1) Then
							gui_tb_savefile(in_win_path$+in_win_file$)
						EndIf
					EndIf
					
					gui_tb_scripthelper_list()
					If gui_ibutton(236+37+37+37+5+37,542,Cicon_inc,se$(179)) Then
						gui_win_command()
					EndIf 
					
					
			End Select
		
		
		Else
			Select m_menu
				
				Case Cmenu_if_clickscreen
					ClsColor 0,0,0
					Cls
					
					If in_clickscreenimage<>0 Then
						DrawImage in_clickscreenimage,(set_scrx/2)-(ImageWidth(in_clickscreenimage)/2),(set_scry/2)-(ImageHeight(in_clickscreenimage)/2)
					EndIf
				
					For Tcscr.Tcscr=Each Tcscr
						Select Tcscr\typ
							Case 1
								cscrfont=Tcscr\col
								If cscrfont<0 Then cscrfont=0
								Select Tcscr\align
									Case 0,1 x=Tcscr\x
									Case 2 x=Tcscr\x-(bmpf_len(Tcscr\txt$,cscrfont)/2)
									Case 3 x=Tcscr\x-bmpf_len(Tcscr\txt$,cscrfont)
									Default x=Tcscr\x
								End Select
								If gui_txtb(x,Tcscr\y,Tcscr\txt$,1,Tcscr\col,Tcscr\tooltip$) Then
									If parse_isevent(Tcscr\event$) Then
										parse_globalevent(Tcscr\event$,"Script Text Click")
									Else
										parse_task(0,0,"click","Script Text Click",Tcscr\event$)
									EndIf
								EndIf
							Case 2
								If Tcscr\image<>0 Then
									DrawImage Tcscr\image,Tcscr\x,Tcscr\y
									If in_mx>=Tcscr\x Then
										If in_my>=Tcscr\y Then 
											If in_mx<=Tcscr\x+ImageWidth(Tcscr\image) Then
												If in_my<=Tcscr\y+ImageHeight(Tcscr\image) Then
													If Tcscr\tooltip$<>"" Then
														gui_tt(in_mx,in_my,Tcscr\tooltip$)
													EndIf
													If in_mrelease(1) Then
														play(sfx_click)
														flushinput()
														If parse_isevent(Tcscr\event$) Then
															parse_globalevent(Tcscr\event$,"Script Image Click")
														Else
															parse_task(0,0,"click","Script Image Click",Tcscr\event$)
														EndIf
													EndIf
												EndIf
											EndIf
										EndIf
									EndIf
								EndIf
						End Select
					Next
					
					If in_clickscreenx Then
						If gui_ibutton(set_scrx-37,5,Cicon_x,sm$(3),1,0,1) Then
							m_menu=0
						EndIf
					EndIf
				
				Case Cmenu_if_buildsetup
					DrawImage gfx_win,215,-537
					If gui_ibutton(742,5,Cicon_x,sm$(3)) Then game_buildsetup_end()
					DrawImage gfx_icons,236,5,Cicon_build
					If in_buildsetup_typ>0 Then
						bmpf_txt(236+37,10, ss$(sm$(161),Dobject_name$(in_buildsetup_typ)))
					Else
						bmpf_txt(236+37,10, ss$(sm$(161),Dunit_name$(Abs(in_buildsetup_typ))))
					EndIf
					
					game_buildsetup_update()
				
				Case Cmenu_if_selplace
					DrawImage gfx_win,215,-537
					DrawImage gfx_icons,236,5,Cicon_select
					bmpf_txt(236+37,10,pc_sp_txt$)
					If gui_ibutton(742,5,Cicon_x,sm$(3)) Then
						m_menu=0
					Else
						If in_mhit(1) Then
							CameraPick(cam,in_mx,in_my)
							pc_sp_x#=PickedX()
							pc_sp_y#=PickedY()
							pc_sp_z#=PickedZ()
							parse_emuevent(pc_sp_class,pc_sp_id,"selectplace")
						EndIf
					EndIf
				
				
				Case Cmenu_if_debug
					DrawImage gfx_win,215,-537
					If gui_ibutton(742,5,Cicon_x,sm$(3)) Then m_menu=0
					DrawImage gfx_icons,236,5,Cicon_zoom
					bmpf_txt(236+37,10,"[[loc:engine.1a03bd2fd107]]")
				
					Select set_debug_menu
						Case 1
							debug_map()
						
					End Select
				
				
				Case Cmenu_if_chat
					bmpf_txt(5,(set_scry/2)+25,sm$(162)+": "+in_chat$,Cbmpf_yellow)
					in_chat$=gui_inputstr$(in_chat$,350)
					
					If in_keyhit(Ckey_chat) Then
						in_chat$=ntrim$(in_chat$)
						in_lastchat$=in_chat$
						If udp<>0 Then
							If Len(in_chat$)>0 Then
								If m_section=Csection_game_mp Then
									For Tudp_con.Tudp_con=Each Tudp_con
										udp_send(Tudp_con\ip,Tudp_con\port,100,1)
										udp_w_byte(1)
										udp_w_byte(g_player)
										udp_w_txt(in_chat$)
									Next
								EndIf
								con_unit(g_player)
								if_msg(TCunit\player_name$+": "+in_chat$,0,Cchatshow)
							EndIf
						EndIf		
						FlushKeys()
						m_menu=0
						in_keyhit(Ckey_chat)=0
					EndIf
				
				Case Cmenu_if_movie
				
				Default

			End Select
		
		
		EndIf
	
	EndIf
	
	
End Function


Function if_msg(msg$,col=0,showtime=3000)
	Tmsg.Tmsg=New Tmsg
	Tmsg\msg$=msg$
	Tmsg\col=col
	Tmsg\age=ms+showtime
	Tmsg\scroll#=0.1
	Insert Tmsg Before First Tmsg
End Function


Function if_unselectitems()
	For Titem.Titem=Each Titem
		Titem\if_sel=0
	Next
End Function


Function if_selectitem(id,typ=-1)
	For Titem.Titem=Each Titem
		If Titem\id=id Or Titem\typ=typ Then
			Titem\if_sel=1
			Return 1
		EndIf
	Next
	Return 0
End Function


Function if_quickslot(forceslot=0,msg=0)
	FlushKeys()
	Local i,found
	For Titem.Titem=Each Titem
		If Titem\if_sel=1 Then
			If forceslot>0 Then
				If in_quickslot(forceslot)=Titem\typ Then
					in_quickslot(forceslot)=0
					If msg=1 Then
						if_msg(ss$(sm$(198),Ditem_name$(Titem\typ)))
					EndIf
				Else
					in_quickslot(forceslot)=Titem\typ
					If msg=1 Then
						if_msg(ss$(sm$(197),Ditem_name$(Titem\typ),forceslot))
					EndIf
				EndIf
				For i=1 To 9
					If i<>forceslot Then
						If in_quickslot(i)=Titem\typ Then in_quickslot(i)=0
					EndIf
				Next
			Else
				found=0
				For i=1 To 9
					If in_quickslot(i)=Titem\typ Then
						in_quickslot(i)=0
						found=1
						If msg=1 Then
							if_msg(ss$(sm$(198),Ditem_name$(Titem\typ)))
						EndIf
					EndIf
				Next
				If found=0 Then
					For i=1 To 9
						If in_quickslot(i)=0 Then
							in_quickslot(i)=Titem\typ
							found=1
							If msg=1 Then
								if_msg(ss$(sm$(197),Ditem_name$(Titem\typ),i))
							EndIf
							Exit
						EndIf
					Next
					If found=0 Then
						in_quickslot(1)=Titem\typ
					EndIf
				EndIf
			EndIf
			if_unselectitems()
			Return 1
		EndIf
	Next
	if_unselectitems()
	Return 0
End Function


Function if_exchange(class,id,allowstore=0,only$="")
	in_ex_class=class
	in_ex_id=id
	in_ex_mode=0
	in_ex_allowstore=allowstore
	in_ex_only$=only$
	m_menu=Cmenu_if_exchange
	in_scr_scr=0
	in_scr_scr2=0
	in_ex_sel=0
	For Titem.Titem=Each Titem
		If Titem\parent_class=class Then
			If Titem\parent_id=id Then
				in_ex_sel=Titem\id
				Return 1
			EndIf
		EndIf
	Next
	For Titem.Titem=Each Titem
		If Titem\parent_class=Cclass_unit Then
			If Titem\parent_id=g_player Then
				in_ex_sel=Titem\id
				Return 1
			EndIf
		EndIf
	Next
End Function

Function if_exchange_allowed(typ)
	If in_ex_only="" Then
		Return 1
	Else
		If Instr(in_ex_only,"|"+typ+"|")>0 Then Return 1
	EndIf
	Return 0
End Function


Function if_centerpos()
	FlushMouse()
	in_mhit(1)=0
	in_mdown(1)=0
	in_mrelease(1)=0
	x=set_scrx/2
	y=set_scry/2
	MoveMouse x,y
	in_mx=x
	in_my=y
End Function


Function if_map()
	DrawImage gfx_icons,236,5,Cicon_map
		bmpf_txt(236+37,10,sm$(169))
		
		If map_mapimage=0 Then
			genmap(256)
		EndIf
		
		Local cx=236+15+256
		Local cy=50+256
		If m_section=Csection_editor Then
			Color 0,0,0
			Rect cx-(128+64),cy-(128+64),256+128,256+128,1
		EndIf
		DrawBlock map_mapimage,cx-128,cy-128
		
		fac#=Float(Float(ter_size*Cworld_size)/Float(256))
		
		For Tinfo.Tinfo=Each Tinfo
			If Tinfo\typ=36 Then
				If Tinfo\ints[1]=1 Or (m_section=Csection_editor And in_cursor=1) Then
					px=Float(EntityX(Tinfo\h)/-fac#)
					py=Float(EntityZ(Tinfo\h)/fac#)
					DrawImage gfx_arrows,cx+px,cy+py,Tinfo\ints[0]
				EndIf
			EndIf
		Next
		
		px=Float(EntityX(cam)/-fac#)
		py=Float(EntityZ(cam)/fac#)
		
		If Abs(px)>127 Or Abs(py)>127 Then
			If px>127 Then
				px=127
			ElseIf px<-127 Then
				px=-127
			EndIf
			If py>127 Then
				py=127
			ElseIf py<-127 Then
				py=-127
			EndIf
			
			sx=(px+128+32)/32
			sy=(py+128+32)/32
			
			Select sx
				Case 0,1,2
					Select sy
						Case 0,1,2 frame=8
						Case 3,4,5,6 frame=7
						Case 7,8 frame=6
					End Select
				Case 3,4,5,6
					Select sy
						Case 0,1,2 frame=1
						Case 3,4,5,6 frame=0
						Case 7,8 frame=5
					End Select
				Case 7,8
					Select sy
						Case 0,1,2 frame=2
						Case 3,4,5,6 frame=3
						Case 7,8 frame=4
					End Select
			End Select
			
			DrawImage gfx_arrows,cx+px,cy+py,frame
		Else
			DrawImage gfx_arrows,cx+px,cy+py,0
		EndIf
		
		If in_compass=1 Then
			Color 0,230,0
			Line cx+px,cy+py,(cx+px)+Sin(EntityYaw(cam))*10,(cy+py)+Cos(EntityYaw(cam))*10
		EndIf
		
		
		If Abs(in_mx-(cx+px))<8 Then
			If Abs(in_my-(cy+py))<8 Then
				bmpf_txt_c(cx+px,cy+py-20,sm$(170),Cbmpf_tiny)
			EndIf
		EndIf
		
		For Tinfo.Tinfo=Each Tinfo
			If Tinfo\typ=36 Then
				If Tinfo\ints[1]=1 Or m_section=Csection_editor Then
					px=Float(EntityX(Tinfo\h)/-fac#)
					py=Float(EntityZ(Tinfo\h)/fac#)
					If Abs(in_mx-(cx+px))<8 Then
						If Abs(in_my-(cy+py))<8 Then
							If Tinfo\strings[0]<>"" Then
								bmpf_txt_c(cx+px,cy+py-20,Tinfo\strings[0],Cbmpf_tiny)
							EndIf
						EndIf
					EndIf
				EndIf
			EndIf
		Next
		
		If m_section=Csection_editor Then
			If in_mx>=cx-(128+64) Then
				If in_mx<=cx+(128+64)
					If in_my>=cy-(128+64) Then
						If in_my<=cy+(128+64) Then
							in_cursorf=2
							If in_mdown(1) Then
								PositionEntity cam,Float(cx-in_mx)*fac#,0,Float(in_my-cy)*fac#
								PositionEntity cam,EntityX(cam),e_tery(EntityX(cam),EntityZ(cam))+50,EntityZ(cam)
							EndIf
						EndIf
					EndIf
				EndIf
			EndIf
		EndIf
		
		If gui_ibutton(742,542,Cicon_ok,sm$(5)) Then m_menu=0
End Function


Function if_tutorial()
	If m_menu=0 Or m_menu>=Cmenu_if_buildsetup Then
		hide=0
	Else
		If in_my>=set_scry-150 Then hide=1
	EndIf
	If hide=1 Then
		in_tut_y#=in_tut_y#+(10.*f#)
		If in_tut_y#>150. Then in_tut_y#=150.
	Else
		in_tut_y#=in_tut_y#-(10.*f#)
		If in_tut_y<0. Then in_tut_y#=0.
	EndIf
	DrawImage gfx_tutor,0,set_scry+in_tut_y#
	bmpf_txt_rect(200,set_scry-120+in_tut_y#,set_scrx-200-140,120,in_tut_txt$,1,0)
End Function


Function if_lastentry()
	Local i
	For Tx.Tx=Each Tx
		If Tx\mode=2 Then
			i=i+1
		EndIf
	Next
	in_opt(0)=i
	If i>4 Then in_scr_scr=i-4
End Function


Function if_drop()
	If in_mx<215 Or in_mx>795 Or in_my>595 Then
		If in_dnd_src=0 Then
			If get_stored_item(in_dnd_typ,Cclass_unit,g_player) Then
			
				p_skipevent=0
				If set_parsecache(Cclass_item,TCitem\id,"drop") Then parse()
				If Instr(Ditem_scriptk$(TCitem\typ),"drop") Then
					parse_env(Cclass_item,TCitem\id,"drop")
					loadstring_parsecache(Ditem_script(TCitem\typ))
					parse()
				EndIf
				If p_skipevent=0 Then
					If p_add2d(in_mx-in_dnd_pivotx,in_my-in_dnd_pivoty,-2,TCitem\typ) Then
						TCp\fx#=0
						TCp\fy#=0
					EndIf
					If unstore_item(TCitem\id,TCitem\count,1) Then merge_item(TCitem\id)
				EndIf
				
			EndIf
		EndIf
	EndIf

	If m_menu=Cmenu_if_exchange Then
		If in_mx>=236 And in_mx<=725 And in_my>=63 And in_my<=282 Then
			If in_dnd_src=1 Then
				
				If get_stored_item(in_dnd_typ,in_ex_class,in_ex_id) Then
					If if_exchange_allowed(in_dnd_typ) Then
						exreturn=exchange_item(Cclass_unit,g_player,TCitem\count)
						If exreturn=0 Then
							if_msg(sm$(181),Cbmpf_red)
							sfx sfx_fail
							speech("nospace")
						ElseIf exreturn=-1 Then
							if_msg(sm$(181),Cbmpf_red)
							sfx sfx_fail
						EndIf
						If exreturn<>2 Then
							in_ex_sel=0
							For Titem.Titem=Each Titem
								If Titem\parent_class=in_ex_class Then
									If Titem\parent_id=in_ex_id Then
										in_ex_sel=Titem\id
										Exit
									EndIf
								EndIf
							Next
						EndIf
					Else
						speech("negative")
					EndIf
				EndIf
			
			EndIf
		ElseIf in_mx>=236 And in_mx<=725 And in_my>=347 And in_my<=521 Then
			If in_dnd_src=0 Then
				
				If get_stored_item(in_dnd_typ,Cclass_unit,g_player)
					If in_ex_allowstore=1 Then
						If if_exchange_allowed(in_dnd_typ) Then
							exreturn=exchange_item(in_ex_class,in_ex_id,TCitem\count)
							If exreturn=0 Then
								if_msg(sm$(181),Cbmpf_red)
								sfx sfx_fail
							ElseIf exreturn=-1 Then
								if_msg(sm$(181),Cbmpf_red)
								sfx sfx_fail
								speech("nospace")
							EndIf
							If exreturn<>2 Then
								in_ex_sel=0
								For Titem.Titem=Each Titem
									If Titem\parent_class=Cclass_unit Then
										If Titem\parent_id=g_player Then
											in_ex_sel=Titem\id
											Exit
										EndIf
									EndIf
								Next
							EndIf
						Else
							speech("negative")
						EndIf
					Else
						speech("negative")
					EndIf
				EndIf
			
			EndIf
		EndIf
		
	EndIf
	
	in_dnd=0
	in_dnd_src=0
	in_dnd_typ=0
End Function