Function e_environment(update=1)

	If env_sea<>0 Then FreeEntity env_sea
	env_sea=CreatePlane(10)
	EntityColor env_sea, 80,255,240
	EntityAlpha env_sea,0.25
	PositionEntity env_sea,0,1,0
	If env_sea2<>0 Then FreeEntity env_sea2
	env_sea2=CreatePlane()
	EntityTexture env_sea2,gfx_water
	ScaleTexture gfx_water,200,200
	RotateEntity env_sea2,0,0,0
	
	If env_sky<>0 Then FreeEntity env_sky
	If map_skybox$="" Then
		env_sky=e_skybox("skies\sky")
	Else
		env_sky=e_skybox("skies\"+map_skybox$)
	EndIf
	EntityFX env_sky,1+8
	EntityOrder env_sky,3
	PositionEntity env_sky,0,500,0
	
	For Tlensf.Tlensf=Each Tlensf
		FreeEntity Tlensf\h
		Delete Tlensf
	Next
	If env_lenspivot<>0 Then FreeEntity env_lenspivot
	env_lenspivot=CreatePivot(cam)
	quot# = Float(set_scry)/set_scrx
	PositionEntity env_lenspivot, -1, quot, 1
	scale# = 2.0/set_scrx
	ScaleEntity env_lenspivot, scale, -scale, scale
	If env_lenssun<>0 Then FreeEntity env_lenssun
	env_lenssun=CreateCube()
	ScaleEntity env_lenssun,30,30,30
	EntityAlpha env_lenssun,0
	EntityRadius env_lenssun,30
	EntityPickMode env_lenssun,1,1
	e_addflare(1,0.125,0.4,-1,1)
	e_addflare(0,0.3,0.1,-2,2)
	e_addflare(1,0.25,0.3,-3,3)
	e_addflare(1,0.125,0.3,-4,8)
	e_addflare(0,0.4,0.2,-4,-3)
	e_addflare(1,0.2,0.2,-5,-2)
	
	If env_moon<>0 Then FreeEntity env_moon
	env_moon=CreateSprite()
	EntityTexture env_moon,gfx_moon
	ScaleSprite env_moon,200,200
	EntityFX env_moon,1+8
	
	If env_wbox<>0 Then FreeEntity env_wbox
	env_wbox=CreateCube()
	ScaleMesh env_wbox,1000,1000,1000
	EntityColor env_wbox,100,100,100
	FlipMesh env_wbox
	EntityFX env_wbox,1+8
	EntityOrder env_wbox,3
	PositionEntity env_wbox,0,500,0
	
	If env_ground<>0 Then FreeEntity env_ground
	env_ground=CreatePlane(1)
	EntityColor env_ground,255,0,0
	EntityFX env_ground,16
	EntityTexture env_ground,gfx_tergrounddirt
	Select set_terrain
		Case 0
			EntityColor env_ground, 120,120,120
		Case 1
			EntityColor env_ground, 240,240,240
		Case 2
			EntityColor env_ground, 120,120,120
	End Select
	e_terrainground()
		
	in_tgametime=gt
	
	Select map_climate
		Case 0
		Case 1
		Case 2 env_cweather=0
		Case 3 env_cweather=1
		Case 4 env_cweather=2
		Case 5 env_cweather=3
	End Select
	If env_cweather<>0 Then
		env_wa#=0.75
	Else
		env_wa#=0.
	EndIf
	
	
	If update Then
		e_environment_update()
		e_environment_update_light()
		e_environment_setup_water()
	EndIf
End Function





Function e_environment_update()
	
	If m_section<>Csection_editor Then
		If Not map_freezetime Then
			If (gt-in_tgametime)>=Cworld_minute_ms Then
				If map_timeupdate=0 Then
					map_lday=map_day
					map_lhour=map_hour
					map_lminute=map_minute
					map_lminute=map_lminute+1
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
				map_minute=map_minute+1
				If map_minute>=60 Then
					map_minute=0
					map_hour=map_hour+1
					If map_hour>=24 Then
						map_hour=0
						map_day=map_day+1
						game_cd()
					EndIf
				EndIf
				e_environment_update_light()
			EndIf
		EndIf
	EndIf
	
	PositionEntity env_sky,EntityX(cam),EntityY(cam),EntityZ(cam)
	
	PositionEntity env_wbox,EntityX(cam),EntityY(cam),EntityZ(cam)
	If env_cweather<>0 Then
		env_wa#=env_wa#+(0.002*f#)
		If env_wa#>0.75 Then env_wa#=0.75
	Else
		env_wa#=env_wa#-(0.002*f#)
		If env_wa#<0.0 Then env_wa#=0.0
	EndIf
	If env_wa#<=0. Then
		HideEntity env_wbox
	Else
		ShowEntity env_wbox
		EntityAlpha env_wbox,env_wa#
	EndIf
	
	
	lensaf#=1.0
	If g_dive=0 Then	
		Select env_cweather
			Case 0
				If env_weatherchan<>0 Then
					If ChannelPlaying(env_weatherchan) Then
						StopChannel env_weatherchan
						env_weatherchan=0
					EndIf
				EndIf
			Case 1
				lensaf#=0.35
				
				If env_weatherchan=0 Then
					env_weatherchan=sfx_emit(sfx_rain,cam)
				Else
					If ChannelPlaying(env_weatherchan)=0 Then
						env_weatherchan=sfx(sfx_rain)
					EndIf
				EndIf
				If g_unpaused=1 Then
					If in_t2000go=1 Then
						If Rand(10)=1 Then
							sfx(sfx_thunder(Rand(0,2)))
							If Rand(5)<>1 Then
								p_add(0,0,0,Cp_flash,Rnd(0.1,0.3),Rnd(0.5,1))
								EntityColor TCp\h,255,255,255
								EntityBlend TCp\h,3
							EndIf
						EndIf
					EndIf
				EndIf
					
				If g_unpaused=1 Then
					If in_gt20go Then
						For i=1 To 3
							p_add(EntityX(cam)+Rand(-150,150),0,EntityZ(cam)+Rand(-100,100),Cp_rain,Rnd(20,30))
						Next
					EndIf
				EndIf
					
			Case 2
				lensaf#=0.35
			
				If env_weatherchan<>0 Then
					If ChannelPlaying(env_weatherchan) Then
						StopChannel env_weatherchan
						env_weatherchan=0
					EndIf
				EndIf
				
				If g_unpaused=1 Then
					If in_gt20go Then
						For i=1 To 2
							p_add(EntityX(cam)+Rand(-200,200),0,EntityZ(cam)+Rand(-100,100),Cp_snow,Rnd(30,50))
						Next
					EndIf
				EndIf
					
			Case 3
				lensaf#=0.4
			
				If env_weatherchan<>0 Then
					If ChannelPlaying(env_weatherchan) Then
						StopChannel env_weatherchan
						env_weatherchan=0
					EndIf
				EndIf
				
				If g_unpaused=1 Then
					If in_t1000go=1 Then
						If Rand(5)=1 Then
							sfx sfx_thunder(Rand(0,2))
							If Rand(5)<>1 Then
								p_add(0,0,0,Cp_flash,Rnd(0.08,0.3),Rnd(0.6,1.3))
								EntityColor TCp\h,255,255,255
								EntityBlend TCp\h,3
							EndIf
						EndIf
					EndIf
				EndIf
					
		End Select
	Else
		lensaf#=0.3
	
		If env_weatherchan<>0 Then
			If ChannelPlaying(env_weatherchan) Then
				StopChannel env_weatherchan
				env_weatherchan=0
			EndIf
		EndIf
	EndIf
	
	If map_hour>=19 Then
		If map_hour=19 Then
			a#=Float(map_minute)/60.0
			EntityAlpha env_moon,a#
		Else
			a#=1.0
			EntityAlpha env_moon,a#
		EndIf
		PositionEntity env_moon,EntityX(cam)-2000,EntityY(cam)+1000,EntityZ(cam)-1500
		ShowEntity env_moon
	ElseIf map_hour<=7 Then
		If map_hour=7 Then
			a#=Float(60-map_minute)/60.0
			EntityAlpha env_moon,a#
		Else
			a#=1.0
			EntityAlpha env_moon,a#
		EndIf
		PositionEntity env_moon,EntityX(cam)-2000,EntityY(cam)+1000,EntityZ(cam)-1500
		ShowEntity env_moon
	Else
		HideEntity env_moon
	EndIf
	
	ShowEntity env_lenssun
	If set_lightfx=1 And set_flarefx=1 Then
	
		If map_hour>=18 Then
			If map_hour=18 Then
				a#=Float(60-map_minute)/60.0
				lensaf#=lensaf#*a#
			Else
				lensaf#=0.0
			EndIf
		EndIf
		If map_hour<=7 Then
			If map_hour=7 Then
				a#=Float(map_minute)/60.0
				lensaf#=lensaf#*a#
			Else
				lensaf#=0.0
			EndIf
		EndIf
		
		showf=0
		
		PositionEntity env_lenssun,EntityX(cam)-2000,EntityY(cam)+1000,EntityZ(cam)-1500
		If remaster_enabled(Cremaster_light) Or remaster_enabled(Cremaster_shadows) Then
			TFormVector 0,0,-3000,env_light,0
			PositionEntity env_lenssun,EntityX(cam)+TFormedX(),EntityY(cam)+TFormedY(),EntityZ(cam)+TFormedZ()
		EndIf
		
		If EntityInView(env_lenssun,cam) Then
			If EntityVisible(cam,env_lenssun) = True
				
				showf=1
				
				CameraProject cam, EntityX(env_lenssun,1),EntityY(env_lenssun,1),EntityZ(env_lenssun,1)
			    lx=ProjectedX()
			    ly=ProjectedY()
				cx=set_scrx/2
				cy=set_scry/2
		
				vx=cx-lx
				vy=cy-ly
				If vx=0 And vy=0
					distance#=1.0
				Else
					distance#=Sqr( (vx*vx)+(vy*vy) )
				EndIf
				nx# = vx / distance#
				ny# = vy / distance#
				
				For fl.Tlensf=Each Tlensf
					fx = cx - (nx#*(distance/fl\offset))
					fy = cy - (ny#*(distance/fl\offset))
					PositionEntity fl\h,fx,fy,1
					If fl\ca#<fl\ra# Then
						fl\ca#=fl\ca#+(0.02*f#)
						If fl\ca#>fl\ra# Then fl\ca#=fl\ra#
					EndIf
					EntityAlpha fl\h,fl\ca#*lensaf#
					ShowEntity fl\h
				Next
			
			EndIf
		EndIf
		If showf=0 Then
			For fl.Tlensf=Each Tlensf
				If fl\ca#>0.0 Then
					ShowEntity fl\h
					fl\ca#=fl\ca#-(0.03*f#)
					If fl\ca#<0.0 Then fl\ca#=0.
					EntityAlpha fl\h,fl\ca#*lensaf#
				Else
					HideEntity fl\h
				EndIf
			Next
		EndIf
	Else
		For fl.Tlensf=Each Tlensf
			HideEntity fl\h
		Next
	EndIf
	HideEntity env_lenssun	

End Function


Function e_environment_update_light()
	Local h1,h2
	Local rf,gf,bf
	Local ra,ga,ba
	Local percent#
	h1=map_hour
	h2=map_hour+1
	If h2>=24 Then h2=0
	percent#=Float(Float(map_minute)/59.)
	rf=(Dlightcycle(h1,0)*(1.-percent#)) + (Dlightcycle(h2,0)*(percent#))
	gf=(Dlightcycle(h1,1)*(1.-percent#)) + (Dlightcycle(h2,1)*(percent#))
	bf=(Dlightcycle(h1,2)*(1.-percent#)) + (Dlightcycle(h2,2)*(percent#))
	If in_skyoverride(0)=1 Then
		If in_skyoverride(4)=0 Then
			rf=in_skyoverride(1)
			gf=in_skyoverride(2)
			bf=in_skyoverride(3)
		Else
			p1#=Float(in_skyoverride(4))/100.0
			If p1#<0.0 Then p1#=0.0
			If p1#>1.0 Then p1#=1.0
			p2#=1.-p1#
			rf=Float(in_skyoverride(1))*p2#+Float(rf)*p1#
			gf=Float(in_skyoverride(2))*p2#+Float(gf)*p1#
			bf=Float(in_skyoverride(3))*p2#+Float(bf)*p1#		
		EndIf
	EndIf
	env_col(0)=rf
	env_col(1)=gf
	env_col(2)=bf
	EntityColor env_sky,rf,gf,bf
	ra=rf-55:If ra<0 Then ra=0
	ga=gf-55:If ga<0 Then ga=0
	ba=bf-55:If ba<0 Then ba=0
	AmbientLight ra,ga,ba
	grey=(rf+gf+bf)/3
	grey=grey-85
	If grey<0 Then grey=0
	EntityColor env_wbox,grey,grey,grey
End Function


Function e_environment_update_water(forced=0)
	remaster_frame()
 DebugUiProfileBegin 4:remaster_shadow_frame():DebugUiProfileEnd 4
	env_seascr#=(env_seascr#+0.0003*f#) Mod 128
	PositionTexture gfx_water,env_seascr#,env_seascr#
	
	env_seasinus#=(env_seasinus#+2.*f#) Mod 360
	
	size=TextureHeight(gfx_water)
	isin=env_seasinus#
	SetBuffer TextureBuffer(gfx_water)
	If gfx_waterimg_custom<>0 Then
		DrawBlock gfx_waterimg_custom,0,0
	Else
		DrawBlock gfx_waterimg,0,0
	EndIf
	Local waterdetail=set_water
	If remaster_enabled(Cremaster_water) Then waterdetail=0
	Select waterdetail
		Case 1
			For x=0 To size Step 4
				CopyRect x,0,4,size,x,Sin(isin+Float(Float(x)*2.8125))*3.
			Next
			For y=0 To size Step 4
				CopyRect 0,y,size,4,Sin(isin+Float(Float(y)*2.8125))*3.,y
			Next
		Case 2
			For x=0 To size Step 2
				CopyRect x,0,2,size,x,Sin(isin+Float(Float(x)*2.8125))*3.
			Next
			For y=0 To size Step 2
				CopyRect 0,y,size,2,Sin(isin+Float(Float(y)*2.8125))*3.,y
			Next
		Case 3,4,5,6
			For x=0 To size
				CopyRect x,0,1,size,x,Sin(isin+Float(Float(x)*2.8125))*3.
			Next
			For y=0 To size
				CopyRect 0,y,size,1,Sin(isin+Float(Float(y)*2.8125))*3.,y
			Next
	End Select	
	SetBuffer BackBuffer()
	
	If EntityY(cam)>0 Then
		
		RotateEntity env_sea2,0,0,0
		EntityFX env_sky,1+8
		EntityFX env_wbox,1+8
		
		If g_dive=1 Then
			g_dive=0
			sfx sfx_splash2
			If gt-g_airtimer>1500 Then
				sfx sfx_gasp
			EndIf
		EndIf
		
		g_airtimer=gt
			
		If set_fog=1 Or map_fog(3)>0 Then
			CameraFogMode cam,1
			CameraFogRange cam,(500.*set_viewfac#)-250,(500.*set_viewfac#)+600-250
			wr=env_col(0)-(255-map_fog(0)):If wr<0 Then wr=0
			wg=env_col(1)-(255-map_fog(1)):If wg<0 Then wg=0
			wb=env_col(2)-(255-map_fog(2)):If wb<0 Then wb=0
			CameraFogColor cam,wr,wg,wb
		Else
			CameraFogMode cam,0
		EndIf
		
		
		If m_section<>Csection_editor Then
			If gt-in_waverate>game_waverate Then
				in_waverate=gt
				If g_unpaused Then
					closewave=0
					If set_effects>0 Then
						If set_effects=1 Then
							For Twave.Twave=Each Twave
								If Rand(2)=1 Then
									dist=EntityDistance(cam,Twave\h)
									If dist<1000
										If dist<300 Then
											closewave=1
											p_add(Twave\x,0,Twave\z,Cp_wave,Rand(20,30),Twave\dir)
										ElseIf EntityInView(Twave\h,cam) Then
											p_add(Twave\x,0,Twave\z,Cp_wave,Rand(20,30),Twave\dir)
										EndIf
									EndIf
								EndIf
							Next
						Else
							For Twave.Twave=Each Twave
								dist=EntityDistance(cam,Twave\h)
								If dist<1500
									If dist<300 Then
										closewave=1
										p_add(Twave\x,0,Twave\z,Cp_wave,Rand(20,30),Twave\dir)
									ElseIf EntityInView(Twave\h,cam) Then
										p_add(Twave\x,0,Twave\z,Cp_wave,Rand(20,30),Twave\dir)
									EndIf
								EndIf
							Next
						EndIf
					EndIf
					If closewave=1 Then
						sfx(sfx_wave(Rand(0,2)),Rnd(0.3,0.6))
					EndIf
				EndIf
			EndIf
		EndIf
		
		If set_water_texsize>0 And (bench_active=0 Or bench_reflection<>0)
			If ((ms-set_water_updatet)>=set_water_updaterate) Or forced=1 Then
				set_water_updatet=ms
				
				If 1=1
					
					HideEntity env_sea
					HideEntity env_sea2
					HideEntity cam
					ShowEntity env_watercam
					
					PositionEntity env_watercam,EntityX(cam),-EntityY(cam),EntityZ(cam)
					size=TextureHeight(env_watertex)
					CameraClsMode env_watercam,1,1
				   	CameraViewport env_watercam,0,0,size,size
					
					If remaster_enabled(Cremaster_water) Then
						DebugUiProfileBegin 6:RenderWorldPlanar cam,env_watertex,0:DebugUiProfileEnd 6
					Else
						DebugUiProfileBegin 6:RenderWorldCube env_watercam,env_watertex:DebugUiProfileEnd 6
					EndIf

					EntityTexture env_sea,env_watertex
					
					ShowEntity env_sea
					ShowEntity env_sea2
					ShowEntity cam
					HideEntity env_watercam
					
				EndIf
				
			EndIf
		EndIf
		
	Else
		
		RotateEntity env_sea2,0,0,-180
		EntityFX env_sky,1
		EntityFX env_wbox,1
		
		If g_dive=0 Then
			sfx sfx_startdive
			g_dive=1
		EndIf
		
		CameraFogMode cam,1
		CameraFogRange cam,1,500
		wr=env_col(0)-env_wcol(0):If wr<0 Then wr=0
		wg=env_col(1)-env_wcol(1):If wg<0 Then wg=0
		wb=env_col(2)-env_wcol(2):If wb<0 Then wb=0
		CameraFogColor cam,wr,wg,wb
		
		If g_unpaused And in_gt20go Then
			If Rand(10)=1 Then
				p_add(EntityX(cam)+Rnd(-5,5),EntityY(cam)-10,EntityZ(cam)+Rnd(-5,5),10,Rnd(1,3))
			EndIf
			If set_effects>0 Then
				If set_effects=1 Then
					If Rand(4)=1 Then
						y=EntityY(cam)+Rand(-100,100)
						If y>-20 Then y=-Rand(20,70)
						p_add(EntityX(cam)+Rand(-200,200),y,EntityZ(cam)+Rnd(-200,200),Cp_hover,Rnd(0.3,5))
					EndIf
				Else
					If Rand(2)=1 Then
						y=EntityY(cam)+Rand(-100,100)
						If y>-20 Then y=-Rand(20,70)
						p_add(EntityX(cam)+Rand(-200,200),y,EntityZ(cam)+Rnd(-200,200),Cp_hover,Rnd(0.3,5))
					EndIf
				EndIf
			EndIf
		EndIf
	
	EndIf
	
	If Abs(EntityY(cam))<1.3 Then
		If EntityY(cam)>0. Then
			PositionEntity cam,EntityX(cam),1.3,EntityZ(cam)
		Else
			PositionEntity cam,EntityX(cam),-1.3,EntityZ(cam)
		EndIf
	EndIf
End Function


Function e_environment_setup_water()
	Local waterflags=1+128+256
	EntityFX env_sea,262144:EntityFX env_sea2,262144
	EntityColor env_sea,80,255,240:EntityAlpha env_sea,0.25
	If remaster_enabled(Cremaster_water) Then
		waterflags=1+16+32
		EntityFX env_sea,65536:EntityAlpha env_sea,1
		If set_water>0 Then EntityFX env_sea2,131072
	EndIf
	Select set_water
		Case 0 set_water_texsize=0
		Case 1 set_water_texsize=0
		Case 2 set_water_texsize=0
		Case 3 set_water_texsize=0
		Case 4 set_water_texsize=64 : set_water_updaterate=2
		Case 5 set_water_texsize=128 : set_water_updaterate=1
		Case 6 set_water_texsize=256 : set_water_updaterate=0
	End Select
	If set_water_texsize>0 Then
		ShowEntity env_sea
		If env_watertex=0 Then
			env_watertex=CreateTexture(set_water_texsize,set_water_texsize,waterflags)
		Else
			If TextureHeight(env_watertex)<>set_water_texsize Then
				FreeTexture env_watertex
				env_watertex=CreateTexture(set_water_texsize,set_water_texsize,waterflags)
			EndIf
		EndIf
		If env_watercam=0 Then
			env_watercam=CreateCamera()
			CameraRange env_watercam,1,1500
			HideEntity env_watercam
		EndIf
	Else
		HideEntity env_sea
		If env_watertex<>0 Then FreeTexture env_watertex:env_watertex=0
		If env_watercam<>0 Then FreeEntity env_watercam:env_watercam=0
	EndIf
	
	For Twave.Twave=Each Twave
		FreeEntity Twave\h
		Delete Twave
	Next
	sizef=16
	size=(ter_size*Cworld_size)/sizef
	Dim env_wavematrix(size,size)
	Dim env_wavematrixy#(size,size)
	Local os=(ter_size/2)*Cworld_size
	Local h#
	Local hxp#,hxm#
	Local hzp#,hzm#
	Local x,z
	Local cnt
	For x=-(ter_size/2)*Cworld_size To (ter_size/2)*Cworld_size Step 8
		For z=-(ter_size/2)*Cworld_size To (ter_size/2)*Cworld_size Step 8
			h#=TerrainY(ter,x,0,z)
			env_wavematrixy((x+os)/sizef,(z+os)/sizef)=h#
			If Abs(h#)<5 Then
				env_wavematrix((x+os)/sizef,(z+os)/sizef)=1
				cnt=cnt+1
			EndIf
		Next
	Next
	If set_debug=1 Then con_add("Wave Spawns (PreCleanup): "+cnt)
	Local rabs#
	For x=1 To size-1
		For z=1 To size-1
			If env_wavematrix(x,z)=1 Then
				rabs#=env_wavematrixy#(x,z)
				
				If Abs(env_wavematrixy(x-1,z))>rabs# Then
					env_wavematrix(x-1,z)=0
				EndIf
				
				If Abs(env_wavematrixy(x+1,z))>rabs# Then
					env_wavematrix(x+1,z)=0
				EndIf
				
				If Abs(env_wavematrixy(x,z-1))>rabs# Then
					env_wavematrix(x,z-1)=0
				EndIf
				
				If Abs(env_wavematrixy(x,z+1))>rabs# Then
					env_wavematrix(x,z+1)=0
				EndIf
				
				If Abs(env_wavematrixy(x-1,z-1))>rabs# Then
					env_wavematrix(x-1,z-1)=0
				EndIf
				
				If Abs(env_wavematrixy(x+1,z+1))>rabs# Then
					env_wavematrix(x+1,z+1)=0
				EndIf
				
				If Abs(env_wavematrixy(x+1,z-1))>rabs# Then
					env_wavematrix(x+1,z-1)=0
				EndIf
				
				If Abs(env_wavematrixy(x-1,z+1))>rabs# Then
					env_wavematrix(x-1,z+1)=0
				EndIf
			
			EndIf
		Next
	Next
	Local a
	Local hdifa
	Local hdifv#
	Local h1#
	Local h2#
	cnt=0
	For x=0 To size
		For z=0 To size
			If env_wavematrix(x,z)=1 Then
				
				Twave.Twave=New Twave
				Twave\x#=-os+(x*sizef)+(sizef/2)
				Twave\z#=-os+(z*sizef)+(sizef/2)
				
				c=CreatePivot()
				HideEntity c
				Twave\h=c
				
				hdifa=0
				hdifv#=0
				For a=0 To 360 Step 30
					RotateEntity c,0,a,0
					PositionEntity c,Twave\x#,0,Twave\z#
					MoveEntity c,0,0,8
					h1#=TerrainY(ter,EntityX(c),0,EntityZ(c))
					MoveEntity c,0,0,-(8*2)
					h2#=TerrainY(ter,EntityX(c),0,EntityZ(c))
					If Abs(h1#-h2#)>Abs(hdifv#) Then
						hdifa=a
						hdifv#=h1#-h2#
					EndIf
				Next
				If hdifv#<0. Then 
					hdifa=hdifa+180
				EndIf
				PositionEntity c,Twave\x#,0,Twave\z#
				RotateEntity c,0,hdifa,0
				Twave\dir=hdifa
				
				cnt=cnt+1
			EndIf
		Next
	Next
	For Twave.Twave=Each Twave
		x=EntityX(Twave\h)
		z=EntityZ(Twave\h)
		free=0
		RotateEntity Twave\h,0,Twave\dir,0
		For i=0 To game_minwavespace Step 10
			MoveEntity Twave\h,0,0,-10
			If e_tery(EntityX(Twave\h),EntityZ(Twave\h))>0.0 Then
				free=1
				Exit
			EndIf
		Next
		If free=1 Then
			FreeEntity Twave\h
			Delete Twave
		Else
			PositionEntity Twave\h,x,0,z
		EndIf
	Next
	Dim env_wavematrix(0,0)
	Dim env_wavematrixy#(0,0)
	If set_debug=1 Then con_add("Wave Spawns: "+cnt)
End Function


Function e_environment_water2dfx()
	If set_2dfx Then
		If EntityY(cam)<0 Then
			env_watersin#=env_watersin#+(6.*f#)
			ws#=env_watersin#
			Select set_water
				Case 0,1
					For y=0 To set_scry Step 20
						CopyRect 0,y,set_scrx,20,Sin(ws#)*6.,y
						ws#=ws#+20.
					Next
				Case 2,3,4
					For y=0 To set_scry Step 10
						CopyRect 0,y,set_scrx,10,Sin(ws#)*6.,y
						ws#=ws#+15.
					Next
				Case 5,6
					For y=0 To set_scry Step 5
						CopyRect 0,y,set_scrx,10,Sin(ws#)*6.,y
						ws#=ws#+7.
					Next
			End Select
		EndIf
	EndIf
End Function


Function e_environment_setweather(weather)
	env_cweather=weather
	If weather=1 Or weather=2 Then
		For Tstate.Tstate=Each Tstate
			If Tstate\typ=Cstate_fire Then
				free_state(Tstate\typ,Tstate\parent_class,Tstate\parent_id)
			EndIf
		Next
	EndIf
End Function


Function e_terrain(size,tex_color$)

	Local texsize
	Local scalefac#
	Local sizecheck#
	sizecheck#=Log(size)/Log(2)
	If Int(sizecheck#)<>sizecheck# Or size<16 Then RuntimeError("'"+size+"' is an invalid Terrainsize! ")
	ter_size=size

	If ter<>0 Then FreeEntity ter
	ter=CreateTerrain(ter_size)
	Select set_ter
		Case 0 TerrainDetail ter,5000,1
		Case 1 TerrainDetail ter,7500,1
		Case 2 TerrainDetail ter,10000,1
	End Select
	ScaleEntity ter,Cworld_size,Cworld_height,Cworld_size
	PositionEntity ter,-(Cworld_size*ter_size/2),-(Cworld_height/2),-(Cworld_size*ter_size/2)
	EntityPickMode ter,2,1
	TerrainShading ter,1
	For x=0 To ter_size
	For z=0 To ter_size
		ModifyTerrain(ter,x,z,Rnd(0.5,0.52))
		If Rand(50)=1 ModifyTerrain(ter,x,z,1)
	Next
	Next
	
	If tex_color$<>"generated" Then
		If ter_tex_color<>0 Then FreeTexture ter_tex_color
		If (FileType(tex_color$)=1) Then
			ter_tex_color=LoadTexture(tex_color$)
			EntityTexture ter,ter_tex_color,0,0
		Else
			ter_tex_color=CreateTexture(64,64)
			EntityTexture ter,ter_tex_color,0,0
		EndIf
	Else
		EntityTexture ter,ter_tex_color,0,0
	EndIf
	texsize=TextureHeight(ter_tex_color)
	scalefac#=Float(Float(Float(ter_size)/Float(texsize))*Float(Cworld_size))*(Float(texsize)/Float(Cworld_size))
	ScaleTexture ter_tex_color,scalefac#,-scalefac#
		
	Select set_terrain
		Case 0
			EntityColor ter, 120,120,120
			TextureBlend ter_tex_color,1
		Case 1
			EntityColor ter, 240,240,240
			EntityTexture ter,gfx_terraindirt,0,1
		Case 2
			EntityColor ter, 120,120,120
			EntityTexture ter,gfx_terraindirt,0,1
			EntityTexture ter,gfx_terstruc,0,2
	End Select
	
	grass_map()
	
End Function


Function e_terrainsmooth()
	Local h#
	For x=1 To ter_size-1
		For z=1 To ter_size-1
			h#=TerrainHeight(ter,x,z)*8.
			h#=h#+TerrainHeight(ter,x-1,z-1)
			h#=h#+TerrainHeight(ter,x-1,z)*2.
			h#=h#+TerrainHeight(ter,x-1,z+1)
			h#=h#+TerrainHeight(ter,x+1,z-1)
			h#=h#+TerrainHeight(ter,x+1,z)*2.
			h#=h#+TerrainHeight(ter,x+1,z+1)
			h#=h#+TerrainHeight(ter,x,z-1)*2.
			h#=h#+TerrainHeight(ter,x,z+1)*2.
			h#=h#/20.
			ModifyTerrain(ter,x,z,h#)
		Next
	Next
End Function


Function e_terraindistort(power#)
	Local h#
	For x=1 To ter_size-1
		For z=1 To ter_size-1
			If Rand(3)=1 Then
				h#=TerrainHeight(ter,x,z)
				h#=h#+Rnd(-power#,power#)
				ModifyTerrain(ter,x,z,h#)
			EndIf
		Next
	Next
End Function


Function e_terrainrandom(x,z,max#=0.3,mode=0)
	Local h#=TerrainHeight(ter,x,z)
	Select mode
		Case -1 ModifyTerrain(ter,x,z,h#-Rnd(0,max#))
		Case 0 ModifyTerrain(ter,x,z,h#+Rnd(-max#,max#))
		Case 1 ModifyTerrain(ter,x,z,h#+Rnd(0,max#))
	End Select
End Function


Function e_terrainhm(file$,scale#)
	If scale#<-1. Then scale#=-1.
	If scale>1. Then scale#=1.
	bmpf_loadscreen("[[loc:engine.df45ad701fee]]")
	Local temp=LoadImage(file$)
	If temp=0 Then Return 0
	bmpf_loadscreen("[[loc:engine.f40b9c803f82]]")
	ResizeImage temp,ter_size,ter_size
	bmpf_loadscreen("[[loc:engine.5193d83e23f6]]")
	SetBuffer ImageBuffer(temp)
	LockBuffer ImageBuffer(temp)
	For x=0 To ter_size-1
		For y=0 To ter_size-1
			rgb=ReadPixelFast(x,y)
			r=(rgb And $FF0000)/$10000
			ModifyTerrain(ter,x,y,scale#*Float(Float(r)/255.))
		Next
	Next
	UnlockBuffer ImageBuffer(temp)
	SetBuffer BackBuffer()
	FreeImage temp
	Return 1
End Function


Function e_terraincm(file$,loadscreen=1)
	If loadscreen=1 Then bmpf_loadscreen("Loading Colormap")
	If (FileType(file$)=1) Then
		If ter_tex_color<>0 Then FreeTexture ter_tex_color
		ter_tex_color=LoadTexture(file$)
		EntityTexture ter,ter_tex_color,0,0
	Else
		Return 0
	EndIf
	texsize=TextureHeight(ter_tex_color)
	scalefac#=Float(Float(Float(ter_size)/Float(texsize))*Float(Cworld_size))*(Float(texsize)/Float(Cworld_size))
	ScaleTexture ter_tex_color,scalefac#,-scalefac#
	Return 1
End Function


Function e_terraincmscale(newscale)
	bmpf_loadscreen("[[loc:engine.d28d2fe1eabe]]")
	Local p2_num,p2_c
	p2_num=newscale
	p2_c=0
	While p2_num>1
		p2_num=p2_num/2
		p2_c=p2_c+1
	Wend
	If Abs(newscale-(2^p2_c))<Abs(newscale-(2^(p2_c+1))) Then
		newscale=2^p2_c
	Else
		newscale=2^(p2_c+1)
	EndIf
	If newscale<8 Then newscale=8:gui_msg("[[loc:engine.b34e96a411e9]]")
	If newscale>4096 Then newscale=4096:gui_msg("[[loc:engine.12415a055a32]]")
	editor_textobuffer(ter_tex_color)
	ResizeImage in_editor_buffer,newscale,newscale
	FreeTexture ter_tex_color
	ter_tex_color=CreateTexture(newscale,newscale)
	If ter_tex_color=0 Then RuntimeError("Failed to create "+newsize+"x"+newsize+" terrain texture")
	editor_buffertotex(ter_tex_color)
	grass_map()
	grass_heightmap()
	editor_textobuffer(ter_tex_color)
	p_kill2d()
	texsize=TextureHeight(ter_tex_color)
	scalefac#=Float(Float(Float(ter_size)/Float(texsize))*Float(Cworld_size))*(Float(texsize)/Float(Cworld_size))
	ScaleTexture ter_tex_color,scalefac#,-scalefac#
	EntityTexture ter,ter_tex_color,0,0
	Return newscale
End Function


Function e_terraincompleteh(change#)
	For x=1 To ter_size-1
		For z=1 To ter_size-1
			h#=TerrainHeight(ter,x,z)
			h#=h#+change#
			If h#>1. Then h#=1.
			If h#<0. Then h#=0.
			ModifyTerrain(ter,x,z,h#)
		Next
	Next
End Function


Function e_terrainborder(absh#,change#=0)
	If absh#<>-1. Then
		For x=0 To ter_size		
			ModifyTerrain(ter,x,0,absh#)
			ModifyTerrain(ter,x,ter_size,absh#)
			ModifyTerrain(ter,0,x,absh#)
			ModifyTerrain(ter,ter_size,x,absh#)
		Next
	Else
		h#=TerrainHeight(ter,0,0)
		h#=h#+change#
		If h#>1. Then h#=1.
		If h#<0. Then h#=0.
		For x=0 To ter_size		
			ModifyTerrain(ter,x,0,h#)
			ModifyTerrain(ter,x,ter_size,h#)
			ModifyTerrain(ter,0,x,h#)
			ModifyTerrain(ter,ter_size,x,h#)
		Next
	EndIf
End Function


Function e_terraintrans(rx,rz,change#,r=1)
	Local h#,perc#,dist#
	For x=(rx-r) To (rx+r)
		For z=(rz-r) To (rz+r)
			dist#=Sqr((x-rx)^2+(z-rz)^2)
			If dist#<r Then
				If x>0 And x<ter_size Then
					If z>0 And z<ter_size Then
						perc#=(dist#/Float(r))
						perc#=1.-perc#
						h#=TerrainHeight(ter,x,z)
						h#=h#+(change#*perc#)
						If h#>1. Then h#=1.
						If h#<0. Then h#=0.
						ModifyTerrain(ter,x,z,h#)
					EndIf
				EndIf
			EndIf
		Next
	Next
End Function


Function e_terrainflatten(rx,rz,r=1)
	Local h#,perc#,dist#
	Local c#=TerrainHeight(ter,rx,rz)
	For x=(rx-r) To (rx+r)
		For z=(rz-r) To (rz+r)
			dist#=Sqr((x-rx)^2+(z-rz)^2)
			If dist#<r Then
				If x>0 And x<ter_size Then
					If z>0 And z<ter_size Then
						perc#=(dist#/Float(r))
						If perc#>1.0 Then perc#=1.0
						h#=(TerrainHeight(ter,x,z))*perc#
						h#=h#+(c#*(1.-perc#))
						If h#>1. Then h#=1.
						If h#<0. Then h#=0.
						If Abs(h#-c#)<0.002 Then
							h#=c#
						EndIf
						ModifyTerrain(ter,x,z,h#)
					EndIf
				EndIf
			EndIf
		Next
	Next
End Function


Function e_terrainsmoothp(rx,rz,r=1)
	Local h#,perc#,dist#
	For x=(rx-r) To (rx+r)
		For z=(rz-r) To (rz+r)
			dist#=Sqr((x-rx)^2+(z-rz)^2)
			If dist#<r Then
				If x>1 And x<ter_size-1 Then
					If z>1 And z<ter_size-1 Then
						h#=TerrainHeight(ter,x,z)*8.
						h#=h#+TerrainHeight(ter,x-1,z-1)
						h#=h#+TerrainHeight(ter,x-1,z)*2.
						h#=h#+TerrainHeight(ter,x-1,z+1)
						h#=h#+TerrainHeight(ter,x+1,z-1)
						h#=h#+TerrainHeight(ter,x+1,z)*2.
						h#=h#+TerrainHeight(ter,x+1,z+1)
						h#=h#+TerrainHeight(ter,x,z-1)*2.
						h#=h#+TerrainHeight(ter,x,z+1)*2.
						h#=h#/20.
						ModifyTerrain(ter,x,z,h#)
					EndIf
				EndIf
			EndIf
		Next
	Next
End Function


Function e_terraindistortp(rx,rz,change#,r=1)
	Local h#,perc#,dist#
	For x=(rx-r) To (rx+r)
		For z=(rz-r) To (rz+r)
			dist#=Sqr((x-rx)^2+(z-rz)^2)
			If dist#<r Then
				If x>0 And x<ter_size Then
					If z>0 And z<ter_size Then
						perc#=(dist#/Float(r))
						perc#=1.-perc#
						h#=TerrainHeight(ter,x,z)
						h#=h#+(Rnd(0,1)*(change#*perc#))
						If h#>1. Then h#=1.
						If h#<0. Then h#=0.
						ModifyTerrain(ter,x,z,h#)
					EndIf
				EndIf
			EndIf
		Next
	Next
End Function


Function e_terrainground(fx=1)
	ter_groundy=TerrainY(ter,-((ter_size/2)*Cworld_size),0,-((ter_size/2)*Cworld_size))
	PositionEntity env_ground,0,ter_groundy,0
	If fx=1 Then
		LockBuffer TextureBuffer(ter_tex_color)
		Local rgb=ReadPixelFast(0,0,TextureBuffer(ter_tex_color))
		UnlockBuffer TextureBuffer(ter_tex_color)
		Local r=(rgb And $FF0000)/$10000
		Local g=(rgb And $FF00)/$100
		Local b=rgb And $FF
		EntityColor env_ground,Float(r)*0.85,Float(g)*0.85,Float(b)*0.85
		EntityType env_ground,Cworld_col
		EntityPickMode env_ground,2,1
		If ter_groundy<=0 Then
			EntityOrder env_ground,1
		Else
			EntityOrder env_ground,0
		EndIf
	EndIf
End Function


Function e_tercol(r,g,b,id)
	in_tercol(0,id)=r
	in_tercol(1,id)=g
	in_tercol(2,id)=b
End Function


;### Skybox (Borrowed from Blitz 3D Castle Sample... ;) - and slightly modified)
Function e_skybox(file$)
	For i=0 To 5
		If env_skytex(i)<>0 Then FreeTexture env_skytex(i)
	Next
	Local ext$=""
	If FileType(file$+"_fr.jpg")=1 Then
		ext$="jpg"
	ElseIf FileType(file$+"_fr.jpeg")=1 Then
		ext$="jpeg"
	ElseIf FileType(file$+"_fr.bmp")=1 Then
		ext$="bmp"
	ElseIf FileType(file$+"_fr.tga")=1 Then
		ext$="tga"
	ElseIf FileType(file$+"_fr.png")=1 Then
		ext$="png"
	EndIf
	m=CreateMesh()
	env_skytex(0)=LoadTexture(file$+"_fr."+ext$,49)
	b=CreateBrush()
	If env_skytex(0)<>0 Then
		BrushTexture b,env_skytex(0)
	EndIf
	sf=CreateSurface( m,b )
	AddVertex sf,-1,+1,-1,0,0:AddVertex sf,+1,+1,-1,1,0
	AddVertex sf,+1,-1,-1,1,1:AddVertex sf,-1,-1,-1,0,1
	AddTriangle sf,0,1,2:AddTriangle sf,0,2,3
	FreeBrush b
	env_skytex(1)=LoadTexture(file$+"_lf."+ext$,49)
	b=CreateBrush()
	If env_skytex(1)<>0 Then
		BrushTexture b,env_skytex(1)
	EndIf
	sf=CreateSurface( m,b )
	AddVertex sf,+1,+1,-1,0,0:AddVertex sf,+1,+1,+1,1,0
	AddVertex sf,+1,-1,+1,1,1:AddVertex sf,+1,-1,-1,0,1
	AddTriangle sf,0,1,2:AddTriangle sf,0,2,3
	FreeBrush b
	env_skytex(2)=LoadTexture(file$+"_bk."+ext$,49)
	b=CreateBrush()
	If env_skytex(2)<>0 Then
		BrushTexture b,env_skytex(2)
	EndIf
	sf=CreateSurface( m,b )
	AddVertex sf,+1,+1,+1,0,0:AddVertex sf,-1,+1,+1,1,0
	AddVertex sf,-1,-1,+1,1,1:AddVertex sf,+1,-1,+1,0,1
	AddTriangle sf,0,1,2:AddTriangle sf,0,2,3
	FreeBrush b
	env_skytex(3)=LoadTexture(file$+"_rt."+ext$,49)
	b=CreateBrush()
	If env_skytex(3)<>0 Then
		BrushTexture b,env_skytex(3)
	EndIf
	sf=CreateSurface( m,b )
	AddVertex sf,-1,+1,+1,0,0:AddVertex sf,-1,+1,-1,1,0
	AddVertex sf,-1,-1,-1,1,1:AddVertex sf,-1,-1,+1,0,1
	AddTriangle sf,0,1,2:AddTriangle sf,0,2,3
	FreeBrush b
	env_skytex(4)=LoadTexture(file$+"_up."+ext$,49)
	b=CreateBrush()
	If env_skytex(4)<>0 Then
		BrushTexture b,env_skytex(4)
	EndIf
	sf=CreateSurface( m,b )
	AddVertex sf,-1,+1,+1,0,1:AddVertex sf,+1,+1,+1,0,0
	AddVertex sf,+1,+1,-1,1,0:AddVertex sf,-1,+1,-1,1,1
	AddTriangle sf,0,1,2:AddTriangle sf,0,2,3
	FreeBrush b
	env_skytex(5)=LoadTexture(file$+"_dn."+ext$,49)
	b=CreateBrush()
	If env_skytex(5)<>0 Then
		BrushTexture b,env_skytex(5)
	EndIf
	sf=CreateSurface( m,b )
	AddVertex sf,-1,-1,-1,1,0:AddVertex sf,+1,-1,-1,1,1
	AddVertex sf,+1,-1,+1,0,1:AddVertex sf,-1,-1,+1,0,0
	AddTriangle sf,0,1,2:AddTriangle sf,0,2,3
	FreeBrush b
	ScaleMesh m,1000,1000,1000
	FlipMesh m
	Return m
End Function


Function e_tery#(x#,z#)
	Local y#=TerrainY(ter,x#,0,z#)
	If y#<ter_groundy# Then Return ter_groundy#
	Return y#
End Function

Function e_gc(h,add#)
	Return
	Local y#=TerrainY#(ter,EntityX#(h),0,EntityZ#(h))
	If y#<ter_groundy#+add# Then y#=ter_groundy#
	hy#=EntityY#(h)
	If hy#<y#+add# Then
		PositionEntity h,EntityX#(h),y#+add#,EntityZ#(h),0
	EndIf
End Function


Function e_terpitch#(x#,z#,s#=1.)
	p1=CreatePivot()
	p2=CreatePivot()
	PositionEntity p1,x,e_tery(x,z-s),z-s
	PositionEntity p2,x,e_tery(x,z+s),z+s	
	PointEntity p1,p2
	angle#=EntityPitch(p1)
	FreeEntity p1
	FreeEntity p2
	Return angle#
End Function


Function e_terroll#(x#,z#,s#=1.)
	p1=CreatePivot()
	p2=CreatePivot()
	PositionEntity p1,x+s,e_tery(x+s,z),z
	PositionEntity p2,x-s,e_tery(x-s,z),z
	PointEntity p1,p2
	angle#=EntityPitch(p1)
	FreeEntity p1
	FreeEntity p2
	Return angle#
End Function


Function e_teralign(h,s#=1.)
	Local x#=EntityX(h)
	Local z#=EntityZ(h)
	Local p1=CreatePivot()
	Local p2=CreatePivot()
	PositionEntity p1,x,e_tery(x,z-s),z-s
	PositionEntity p2,x,e_tery(x,z+s),z+s
	PointEntity p1,p2
	pitch#=EntityPitch(p1)
	PositionEntity p1,x+s,e_tery(x+s,z),z
	PositionEntity p2,x-s,e_tery(x-s,z),z
	PointEntity p1,p2
	roll#=EntityPitch(p1)
	FreeEntity p1
	FreeEntity p2
	RotateEntity h,pitch#,0,roll#
End Function


Function e_addflare(sprite,scale#,alpha#,order,offset#)
	fl.Tlensf=New Tlensf
	fl\h=CreateSprite(env_lenspivot)
	EntityTexture fl\h,gfx_p_flare(sprite)
	ScaleSprite fl\h,scale#,scale#
	EntityAlpha fl\h,0.0
	EntityOrder fl\h,order
	EntityBlend fl\h,3
	HideEntity fl\h
	fl\ra#=alpha#
	fl\ca#=0
	fl\offset#=offset#
End Function
