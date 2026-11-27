Function cull()
	ccull=MilliSecs()
	con_unit(g_player)
	
	;Object
	c=0
	cv=0
	ct=MilliSecs()
	For TCobject.Tobject=Each Tobject
		c=c+1
		TCobject\view_cull=0
		If TCobject\ch=0 Then
			dist=EntityDistance(TCobject\h,cam)
			If dist>Dobject_afc(TCobject\typ) Then
				HideEntity TCobject\h
			Else
				If EntityInView(TCobject\h,cam) Then
					cv=cv+1
					ShowEntity TCobject\h
					EntityAlpha TCobject\h,TCobject\alpha#
					If Dobject_swayspeed#(TCobject\typ)<>0. Then
						If set_windsway Then
							h=TCobject\h
							angle#=TCobject\windsway#
							angle#=(angle#+(Dobject_swayspeed#(TCobject\typ)*f#)) Mod 360
							TCobject\windsway#=angle#
							RotateEntity h,EntityPitch(h),EntityYaw(h),Sin(angle#)*Dobject_swaypower#(TCobject\typ)
						EndIf
					EndIf
					If Dobject_active(TCobject\typ)=1 And bench_active=0 Then
						update_object_behaviour()
					EndIf
				ElseIf dist<500 Then
					ShowEntity TCobject\h
					EntityAlpha TCobject\h,0
					TCobject\view_cull=2
				Else
					HideEntity TCobject\h
					TCobject\view_cull=1
				EndIf
			EndIf
		Else
			dist=EntityDistance(TCobject\h,cam)
			If dist>Dobject_afc(TCObject\typ) Then
				HideEntity TCobject\h
				If Dobject_col(TCobject\typ)<>3 Then HideEntity TCobject\ch
			Else
				If EntityInView(TCobject\h,cam) Then
					cv=cv+1
					ShowEntity TCobject\h
					ShowEntity TCobject\ch
					If Dobject_swayspeed#(TCobject\typ)<>0. Then
						If set_windsway Then
							h=TCobject\h
							angle#=TCobject\windsway#
							angle#=(angle#+(Dobject_swayspeed#(TCobject\typ)*f#)) Mod 360
							TCobject\windsway#=angle#
							RotateEntity h,EntityPitch(h),EntityYaw(h),Sin(angle#)*Dobject_swaypower#(TCobject\typ)
						EndIf
					EndIf
					If Dobject_active(TCobject\typ)=1 And bench_active=0 Then
						update_object_behaviour()
					EndIf
				ElseIf dist<500 Then
					HideEntity TCobject\h
					ShowEntity TCobject\ch
					TCobject\view_cull=1
				Else
					HideEntity TCobject\h
					If Dobject_col(TCobject\typ)<>3 Then HideEntity TCobject\ch
					TCobject\view_cull=1
				EndIf
			EndIf
		EndIf
	Next
	If set_debug_rt Then
		set_debug_rt_objects$=(MilliSecs()-ct)+" ms Objects ("+cv+"/"+c+")"
	EndIf
	
	;Unit
	c=0
	cv=0
	ct=MilliSecs()
	If m_section<>Csection_editor Then
		For TCunit.Tunit=Each Tunit
			c=c+1
			If TCunit\id<>g_player Then
				If EntityDistance(TCunit\h,cam)>Dunit_afc(TCunit\typ) Then
					HideEntity TCunit\h
				Else
					cv=cv+1
					ShowEntity TCunit\h
					If g_unpaused And (bench_active=0 Or bench_ai) Then update_unit()
				EndIf
			Else
				If g_unpaused And bench_active=0 Then update_unit()
			EndIf
		Next
	Else
		For TCunit.Tunit=Each Tunit
			c=c+1
			If EntityDistance(TCunit\h,cam)>Dunit_afc(TCunit\typ) Then
				HideEntity TCunit\h
			Else
				If EntityInView(TCunit\h,cam) Then
					cv=cv+1
					ShowEntity TCunit\h
				Else
					HideEntity TCunit\h
				EndIf
			EndIf
		Next
	EndIf
	If set_debug_rt Then
		set_debug_rt_units$=(MilliSecs()-ct)+" ms Units ("+cv+"/"+c+")"
	EndIf
	
	If bench_active Then Return
	If m_section<>Csection_editor Then
		If in_t1000go Then
			If g_unpaused=1 Then
				c=0
				cv=0
				ct=MilliSecs()
				For Tinfo.Tinfo=Each Tinfo
					c=c+1
					If Tinfo\typ>=10 Then
						If Tinfo\typ<30 Then
							cv=cv+1
							TCinfo.Tinfo=Tinfo
							update_info()
						ElseIf Tinfo\typ>40 Then
							cv=cv+1
							TCinfo.Tinfo=Tinfo
							update_info()
						EndIf
					EndIf
				Next
				If map_timeupdate=1 Then
					g_sleep=0
					map_timeupdate=0
				EndIf
			EndIf
		EndIf
	Else
		For Tinfo.Tinfo=Each Tinfo
			If EntityDistance(Tinfo\h,cam)<2000 Then
				If EntityInView(Tinfo\h,cam) Then
					ShowEntity Tinfo\h
				Else
					HideEntity Tinfo\h
				EndIf
			Else
				HideEntity Tinfo\h
			EndIf
		Next
	EndIf
	If set_debug_rt Then
		set_debug_rt_infos$=(MilliSecs()-ct)+" ms Infos ("+cv+"/"+c+")"
	EndIf
	
	If m_section<>Csection_editor Then
		timer_update()
	EndIf
	
	parse_all()
	
	modern_process_saves()
	
	If set_debug_rt Then
		set_debug_rt_cull$=(MilliSecs()-ccull)+" ms Cull"
	EndIf
End Function


Function cull_items()
	c=0
	cv=0
	ct=MilliSecs()
	If m_section<>Csection_editor Then
		For Titem.Titem=Each Titem
			c=c+1
			If Titem\parent_class=0 Or Titem\parent_mode=Cp_out Then
				If EntityDistance(Titem\h,cam)>Ditem_afc(Titem\typ) Then
					HideEntity Titem\h
					Titem\phy_fall=gt
				Else
					
					If Titem\parent_class=0 Then
						cv=cv+1
						ShowEntity Titem\h
						
						If g_unpaused Then
							
							If Titem\phy_pause<=0 Then
							
								par=gt-Titem\phy_fall
								If par>Cworld_facct Then par=Cworld_facct
								tot=Cworld_facct
								perc#=Float(Float(par)/Float(tot))
								swim=Dmat_swim(Ditem_mat(Titem\typ))
								If swim<>0 Then
									If swim=1 Then
										If EntityY(Titem\h)>-1 Then
											TranslateEntity Titem\h,0,-(Cworld_g#*f#*(1.5*perc#)),0
										ElseIf EntityY(Titem\h)<>-1 Then
											TranslateEntity Titem\h,0,0.45*f#,0
											If EntityY(Titem\h)>-1 Then
												PositionEntity Titem\h,EntityX(Titem\h),-1,EntityZ(Titem\h)
												Titem\phy_fall=gt
											EndIf
										Else
											RotateEntity Titem\h,Sin(in_wa#+(Titem\id*20)),EntityYaw(Titem\h),Cos(in_wa#+(Titem\id*20))
										EndIf
									ElseIf swim=2 Then
										If EntityY(Titem\h)>-1 Then
											TranslateEntity Titem\h,0,-(Cworld_g#*f#*(1.5*perc#)),0
										Else
											Titem\phy_fall=gt
											RotateEntity Titem\h,Sin(in_wa#+(Titem\id*20)),EntityYaw(Titem\h),Cos(in_wa#+(Titem\id*20))
										EndIf
									EndIf
								Else
									TranslateEntity Titem\h,0,-(Cworld_g#*f#*(1.5*perc#)),0
								EndIf
								
								tery#=e_tery(EntityX(Titem\h),EntityZ(Titem\h))
								If EntityY(Titem\h)<(tery#+3) Then
									PositionEntity Titem\h,EntityX(Titem\h),tery#+1.,EntityZ(Titem\h)
									Titem\phy_fall=gt
									If swim=0 Then
										Titem\phy_pause=1
									EndIf
								EndIf
								
								If EntityCollided(Titem\h,Cworld_col)<>0 Then
									Titem\phy_fall=gt
									If swim=0 Then
										If Titem\phy_pause>=0 Then
											Titem\phy_pause=1
										Else
											Titem\phy_pause=Titem\phy_pause+1
										EndIf
									EndIf
								EndIf
							
							Else
								
								If Not EntityInView(Titem\h,cam) Then	
									HideEntity Titem\h
								EndIf
								
							EndIf
						
						Else
							
							If Not EntityInView(Titem\h,cam) Then	
								HideEntity Titem\h
							EndIf
								
						EndIf
					Else
						If EntityInView(Titem\h,cam) Then
							cv=cv+1
							ShowEntity Titem\h
						Else
							HideEntity Titem\h
						EndIf
						Titem\phy_fall=gt
					EndIf
	
				EndIf
			Else
				HideEntity Titem\h
				Titem\phy_fall=gt
			EndIf
		Next
	Else
		For Titem.Titem=Each Titem
			c=c+1
			If Titem\parent_class=0 Or Titem\parent_mode=Cp_out Then
				If EntityDistance(Titem\h,cam)>Ditem_afc(Titem\typ) Then
					HideEntity Titem\h
					Titem\phy_fall=gt
				Else
					
					If Titem\parent_class=0 Then
						cv=cv+1
						ShowEntity Titem\h
					Else
						If EntityInView(Titem\h,cam) Then
							cv=cv+1
							ShowEntity Titem\h
						Else
							HideEntity Titem\h
						EndIf
						Titem\phy_fall=gt
					EndIf
				EndIf
			Else
				HideEntity Titem\h
				Titem\phy_fall=gt
			EndIf
		Next
	EndIf
	If set_debug_rt Then
		set_debug_rt_items$=(MilliSecs()-ct)+" ms Items ("+cv+"/"+c+")"
	EndIf
End Function
