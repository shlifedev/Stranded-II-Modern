Function editor_ini(menu=Cmenu_ed_new)
	e_clear()

	in_opt(0)=0
	in_opt(3)=0
	in_opt(4)=0
	m_menu=menu

	For i=0 To Cobject_count
		If Dobject_name$(i)<>"" Then in_object_sel=i:Exit
	Next
	For i=0 To Cunit_count
		If Dunit_name$(i)<>"" Then in_unit_sel=i:Exit
	Next
	For i=0 To Citem_count
		If Ditem_name$(i)<>"" Then in_item_sel=i:Exit
	Next
	For i=0 To Cinfo_count
		If Dinfo_name$(i)<>"" Then in_info_sel=i:Exit
	Next
	
	For g.Tgroup=Each Tgroup
		c=0
		Select g\class
			Case Cclass_object
				For i=0 To Cobject_count
					If Dobject_ined(i)=1 Then
						If Dobject_group(i)=g\group Then
							c=c+1
						EndIf
					EndIf
				Next
			Case Cclass_unit
				For i=0 To Cunit_count
					If Dunit_ined(i)=1 Then
						If Dunit_group(i)=g\group Then
							c=c+1
						EndIf
					EndIf
				Next
			Case Cclass_item
				For i=0 To Cobject_item
					If Ditem_ined(i)=1 Then
						If Ditem_group(i)=g\group Then
							c=c+1
						EndIf
					EndIf
				Next
			Case Cclass_info
				For i=0 To Cinfo_count
					If Dinfo_group(i)=g\group Then
						c=c+1
					EndIf
				Next
		End Select
		g\count=c
	Next
	
	editor_ini_sc()
	
	in_edrandrot=1
	editor_defaultmapsettings()
	
	editor_genmap()
	editor_gencolormap()
	grass_map()
	grass_heightmap()
	in_edmap$=""
	editor_resetcam()
	
	in_editor_sidebar=1
End Function


Function editor_ini_sc()
	For Tsc.Tsc=Each Tsc
		Delete Tsc
	Next
	Local in$,matchpos,match$
	Local stream=ReadFile(set_rootdir$+"core\scriptcommands.inf")
	If stream<>0 Then
		While Not Eof(stream)
			in$=ReadLine(stream)
			in$=Trim(in$)
			If in$<>"" Then
				split$(in$,"",3)
				Tsc.Tsc=New Tsc
				Tsc\cmd$=splits$(0)
				Tsc\groups$=splits$(1)
				Tsc\params$=splits$(2)
			EndIf
		Wend
		CloseFile(stream)
	Else
		con_add("ERROR: Unable to load 'core\scriptcommands.inf'",Cbmpf_red)
	EndIf
	For Tsgr.Tsgr=Each Tsgr
		Delete Tsgr
	Next
	For Tsc.Tsc=Each Tsc
		split(Tsc\groups$,",",5)
		For i=0 To 5
			If splits$(i)<>"" Then
				gexists=0
				group$=Trim(splits$(i))
				For Tsgr.Tsgr=Each Tsgr
					If Tsgr\group$=group$ Then
						gexists=1
						Exit
					EndIf
				Next
				If gexists=0 Then
					Tsgr.Tsgr=New Tsgr
					Tsgr\group$=group$
				EndIf
			EndIf
		Next
	Next
End Function


Function editor_quit()
	For Tsc.Tsc=Each Tsc
		Delete Tsc
	Next
	For Tsgr.Tsgr=Each Tsgr
		Delete Tsgr
	Next
	If in_editor_dummy<>0 Then
		FreeEntity in_editor_dummy
		in_editor_dummy=0
	EndIf
End Function


Function editor_picking(mode)
	Local objects=0
	Local units=0
	Local items=0
	Local infos=0
	Select mode
		Case Cclass_object objects=1
		Case Cclass_unit units=1
		Case Cclass_item items=1
		Case Cclass_info infos=1
	End Select
	For Tobject.Tobject=Each Tobject
		EntityPickMode Tobject\h,objects*2,1
	Next
	For Tunit.Tunit=Each Tunit
		EntityPickMode Tunit\h,units*2,1
	Next
	For Titem.Titem=Each Titem
		If Ditem_radius#(Titem\typ)=0. Then
			EntityPickMode Titem\h,items*2,1
		Else
			EntityPickMode Titem\h,items*1,1
		EndIf
	Next
	For Tinfo.Tinfo=Each Tinfo
		If Tinfo\h<>0 Then EntityPickMode Tinfo\h,infos*2,1
	Next
End Function


Function editor_genmap(ctexsize=128)
	e_clear()
	Local size
	Select in_opt(3)
		Case 0 size=16
		Case 1 size=32
		Case 3 size=64
		Case 4 size=128
		Case 6 size=256
		Case 7 size=512
		Default size=32
	End Select
	bmpf_loadscreen("[[loc:engine.306756757c58]]")
	ter_tex_color=CreateTexture(ctexsize,ctexsize)
	e_terrain(size,"generated")
	e_environment()
	
	bmpf_loadscreen("[[loc:engine.f2e50689aa63]]")
	Local seaground#=0.45
	For x=0 To size
		For z=0 To size
			ModifyTerrain ter,x,z,seaground#
		Next
	Next
	Local landground#=0.55
	Local hdif#=landground#-seaground#
	Local dist
	Local perc#
	Local c=(size/2)
	Local h=(size/2)-2
	Local coast=h
	Select size
		Case 16 coast=coast-1
		Case 32 coast=coast-3
		Case 64 coast=coast-5
		Default coast=coast-7
	End Select
	For x=2 To size-2
		For z=2 To size-2
			dist=Sqr((c-x)^2+(c-z)^2)
			If dist<h Then
				If dist>coast Then
					perc#=Float(dist-coast)/Float(h-coast)
					perc#=1.-perc#
					ModifyTerrain ter,x,z,seaground#+(perc#*hdif#)
				Else		
					ModifyTerrain ter,x,z,landground#
				EndIf
			EndIf
		Next
	Next
	Local transc=Rand(15,25)
	Local transmin=h/5
	Local transmax=h/2
	For i=1 To transc
		angle#=Rnd(0,360)
		x=c+Sin(angle#)*coast
		z=c-Cos(angle#)*coast
		If Rand(1,2)=1 Then
			editor_genmap_hill(x,z,Rand(transmin,transmax),size,landground#)
		Else
			editor_genmap_hill(x,z,Rand(transmin,transmax),size,seaground#)
		EndIf
	Next
	e_terraindistort(0.05)
	
	Select in_opt(4)
		Case 0
			For x=1 To size-1
				For z=1 To size-1
					If Rand(3)=1 Then e_terrainrandom(x,z,0.03)
				Next
			Next
			For i=1 To 5:e_terrainsmooth():Next
		Case 1,3,4
			For i=1 To 3:e_terrainsmooth():Next
			For x=1 To size-1
				For z=1 To size-1
					If Rand(3)=1 Then e_terrainrandom(x,z,0.05)
				Next
			Next
			For i=1 To 3:e_terrainsmooth():Next
		Case 2
			For x=4 To size-4
				For z=4 To size-4
					ModifyTerrain ter,x,z,0.6
				Next
			Next
			For i=1 To 5:e_terrainsmooth():Next
			For x=1 To size-1
				For z=1 To size-1
					If Rand(3)=1 Then e_terrainrandom(x,z,0.25)
				Next
			Next
			For i=1 To 3:e_terrainsmooth():Next
		Case 5
			For x=1 To size-1
				For z=1 To size-1
					If Rand(3)=1 Then e_terrainrandom(x,z,0.05)
				Next
			Next
			For x=1 To size-1
				For z=1 To size-1
					If Rand(5)=1 Then e_terrainrandom(x,z,0.2,-1)
				Next
			Next
			For i=1 To 2:e_terrainsmooth():Next
	End Select
End Function

Function editor_genmap_hill(cx,cz,size,tersize,h#)
	size=size/2
	If size<1 Then size=1
	For x=cx-size To cx+size
		For z=cz-size To cz+size
			If x>=2 And x<=tersize-2 Then
				If z>=2 And z<=tersize-2 Then
					dist=Sqr((cx-x)^2+(cz-z)^2)
					If dist<size Then
						ModifyTerrain ter,x,z,h#
					EndIf
				EndIf
			EndIf
		Next
	Next
End Function

Function editor_gencolormap(updatecolorset=1)
	If m_section=Csection_editor Then bmpf_loadscreen("[[loc:engine.d50435432421]]")
	Local ctexsize=TextureHeight(ter_tex_color)
	Local size=ter_size
	If updatecolorset=1 Then
		e_tercol(game_tercol(0,0),game_tercol(1,0),game_tercol(2,0),0)
		e_tercol(game_tercol(3,0),game_tercol(4,0),game_tercol(5,0),1)
		e_tercol(game_tercol(6,0),game_tercol(7,0),game_tercol(8,0),2)
		Select in_opt(4)
			Case 3
				e_tercol(game_tercol(0,1),game_tercol(1,1),game_tercol(2,1),0)
				e_tercol(game_tercol(3,1),game_tercol(4,1),game_tercol(5,1),1)
				e_tercol(game_tercol(6,1),game_tercol(7,1),game_tercol(8,1),2)
			Case 4
				e_tercol(game_tercol(0,2),game_tercol(1,2),game_tercol(2,2),0)
				e_tercol(game_tercol(3,2),game_tercol(4,2),game_tercol(5,2),1)
				e_tercol(game_tercol(6,2),game_tercol(7,2),game_tercol(8,2),2)
			Case 5
				e_tercol(game_tercol(0,3),game_tercol(1,3),game_tercol(2,3),0)
				e_tercol(game_tercol(3,3),game_tercol(4,3),game_tercol(5,3),1)
				e_tercol(game_tercol(6,3),game_tercol(7,3),game_tercol(8,3),2)
		End Select
	EndIf
	SetBuffer TextureBuffer(ter_tex_color)
	Local h#
	Local a#,r,g,b
	Local r2,g2,b2
	Local rgb
	Local texsize=ctexsize-1
	Local rx=-((size/2)*Cworld_size)
	Local ry=-((size/2)*Cworld_size)
	Local fac#=Float(Float(size*Cworld_size)/Float(ctexsize))
	r2=in_tercol(0,1):g2=in_tercol(1,1):b2=in_tercol(2,1)
	
	If m_section=Csection_editor Then UpdateWorld()
	
	LockBuffer TextureBuffer(ter_tex_color)
	For x=0 To texsize
		For y=0 To texsize
			h#=Float(TerrainY(ter,rx+(x*fac#),0,ry+(y*fac#))/Float(Cworld_height))+0.5
			
			a#=1
			r=in_tercol(0,1)
			g=in_tercol(1,1)
			b=in_tercol(2,1)
			
			If h#<0.52 Then
				If h#>=0.5 Then a#=((h#-0.5)/0.02)
				r=in_tercol(0,0):g=in_tercol(1,0):b=in_tercol(2,0)
				
			ElseIf h#>0.55 Then
				If h#<=0.60 Then a#=1.-((h#-0.55)/0.05)
				r=in_tercol(0,2):g=in_tercol(1,2):b=in_tercol(2,2)
			EndIf
			
			r=r+Rand(-5,5)
			g=g+Rand(-5,5)
			b=b+Rand(-5,5)
			If r<0 Then
				r=0
			ElseIf r>255 Then
				r=255
			EndIf
			If g<0 Then
				g=0
			ElseIf g>255 Then
				g=255
			EndIf
			If b<0 Then
				b=0
			ElseIf b>255 Then
				b=255
			EndIf
			
			If a#=1 Then
				rgb=255*$1000000 + r*$10000 + g*$100 + b
				WritePixelFast x,y,rgb
			Else
				editor_gencolormap_apm(r,g,b,r2,g2,b2,a#,x,y)
			EndIf
			
		Next
	Next
	
	UnlockBuffer TextureBuffer(ter_tex_color)
	SetBuffer BackBuffer()
End Function


Function editor_gencolormap_apm(r#,g#,b#,r2#,g2#,b2#,a#,x,y)
	Local nr=(r2#*a#)+(r#*(1.-a#))
	Local ng=(g2#*a#)+(g#*(1.-a#))
	Local nb=(b2#*a#)+(b#*(1.-a#))
	If nr<0 Then
		nr=0
	ElseIf nr>255 Then
		nr=255
	EndIf
	If ng<0 Then
		ng=0
	ElseIf ng>255 Then
		ng=255
	EndIf
	If nb<0 Then
		nb=0
	ElseIf nb>255 Then
		nb=255
	EndIf
	Local rgb=255*$1000000+nr*$10000+ng*$100+nb
	WritePixelFast x,y,rgb
End Function


Function editor_setdummy(class,typ)
	If in_editor_dummy<>0 Then FreeEntity in_editor_dummy
	Select class
		Case Cclass_object
			load_object_model(typ)
			in_editor_dummy=CopyEntity(Dobject_modelh(typ))
			ScaleEntity in_editor_dummy,Dobject_size#(typ,0),Dobject_size#(typ,1),Dobject_size#(typ,2)
		Case Cclass_unit
			in_editor_dummy=CopyEntity(Dunit_modelh(typ))
			ScaleEntity in_editor_dummy,Dunit_size#(typ,0),Dunit_size#(typ,1),Dunit_size#(typ,2)		
		Case Cclass_item
			load_item_model(typ)
			If Ditem_modelh(typ)<>0 Then
				in_editor_dummy=CopyEntity(Ditem_modelh(typ))
			Else
				in_editor_dummy=CopyEntity(game_defaultitemmodel)
			EndIf
			ScaleEntity in_editor_dummy,Ditem_size#(typ,0),Ditem_size#(typ,1),Ditem_size#(typ,2)
		Case Cclass_info
			in_editor_dummy=CreateCube()
			ScaleEntity in_editor_dummy,5,5,5
	End Select
	EntityFX in_editor_dummy,16
	EntityBlend in_editor_dummy,3
	EntityColor in_editor_dummy,0,255,0
End Function

Function editor_setmode(mode,picking=1)
	Select mode
		Case 0
			If picking Then editor_picking(0)
		Case Cclass_object
			If object_count=0 Then RuntimeError("No objects defined!")
			If picking Then editor_picking(Cclass_object)
			editor_setdummy(Cclass_object,in_object_sel)
		Case Cclass_unit
			If picking Then editor_picking(Cclass_unit)
			editor_setdummy(Cclass_unit,in_unit_sel)
		Case Cclass_item
			If item_count=0 Then RuntimeError("No items defined!")
			If picking Then editor_picking(Cclass_item)
			editor_setdummy(Cclass_item,in_item_sel)
		Case Cclass_info
			If picking Then editor_picking(Cclass_info)
			editor_setdummy(Cclass_info,in_info_sel)
	End Select
	FlushKeys()
End Function

Function editor_defaultmapsettings()
	map_day=1
	map_hour=9
	map_minute=0
	map_freezetime=0
	map_skybox$="sky"
	map_multiplayer=0
	map_climate=0
	map_music$="amb_jungle.mp3"
	map_fog(0)=255-50
	map_fog(1)=255-45
	map_fog(2)=255-10
	map_fog(3)=0
	map_briefing$=""
	map_password$=""
End Function


Function editor_textobuffer(tex)
	Local size=TextureHeight(tex)
	If in_editor_buffer<>0 Then FreeImage in_editor_buffer
	in_editor_buffer=CreateImage(size,size)
	LockBuffer TextureBuffer(tex)
	LockBuffer ImageBuffer(in_editor_buffer)
	For x=0 To size-1
		For y=0 To size-1
			CopyPixelFast x,y,TextureBuffer(tex),x,y,ImageBuffer(in_editor_buffer)
		Next
	Next
	UnlockBuffer TextureBuffer(tex)
	UnlockBuffer ImageBuffer(in_editor_buffer)
End Function


Function editor_buffertotex(tex)
	Local size=TextureHeight(tex)
	LockBuffer TextureBuffer(tex)
	LockBuffer ImageBuffer(in_editor_buffer)
	For x=0 To size-1
		For y=0 To size-1
			CopyPixelFast x,y,ImageBuffer(in_editor_buffer),x,y,TextureBuffer(tex)
		Next
	Next
	UnlockBuffer TextureBuffer(tex)
	UnlockBuffer ImageBuffer(in_editor_buffer)
End Function


Function editor_buffertoheightmap()
	Local size=ImageHeight(in_editor_buffer)
	SetBuffer ImageBuffer(in_editor_buffer)
	LockBuffer ImageBuffer(in_editor_buffer)
	For x=0 To size-1
		For y=0 To size-1
			rgb=ReadPixelFast(x,y)
			r=(rgb And $FF0000)/$10000
			h#=Float(r)/255.
			ModifyTerrain(ter,x,y,h#)
		Next
	Next
	UnlockBuffer ImageBuffer(in_editor_buffer)
	SetBuffer BackBuffer()
End Function


Function editor_heightmaptobuffer()
	Local size=TerrainSize(ter)
	If in_editor_buffer<>0 Then FreeImage in_editor_buffer
	in_editor_buffer=CreateImage(size,size)
	LockBuffer ImageBuffer(in_editor_buffer)
	For x=0 To size-1
		For y=0 To size-1
			h#=TerrainHeight(ter,x,y)
			r=h#*255.
			rgb=255*$1000000 + r*$10000 + r*$100 + r
			WritePixelFast x,y,rgb,ImageBuffer(in_editor_buffer)
		Next
	Next
	UnlockBuffer ImageBuffer(in_editor_buffer)
End Function

Function editor_grassmaptobuffer()
	grass_x=-2147483648
	SetBuffer TextureBuffer(ter_tex_color)
	LockBuffer TextureBuffer(ter_tex_color)
	For x=0 To grass_mapsize-1
		For y=0 To grass_mapsize-1
			rgb=ReadPixelFast(x,y)
			grass_rgb(x,y,0)=(rgb And $FF0000)/$10000
			grass_rgb(x,y,1)=(rgb And $FF00)/$100
			grass_rgb(x,y,2)=rgb And $FF
		Next
	Next
	UnlockBuffer TextureBuffer(ter_tex_color)
	Local size=grass_mapsize
	If in_editor_buffer<>0 Then FreeImage in_editor_buffer
	in_editor_buffer=CreateImage(size,size)
	SetBuffer ImageBuffer(in_editor_buffer)
	LockBuffer ImageBuffer(in_editor_buffer)
	For x=0 To size-1
		For y=0 To size-1
			If grass_rgb(x,y,3)=1 Then
				r=grass_rgb(x,y,0)
				g=grass_rgb(x,y,1)
				b=grass_rgb(x,y,2)
			Else
				r=255:g=0:b=255
			EndIf
			rgb=255*$1000000 + r*$10000 + g*$100 + b
			WritePixelFast x,y,rgb
		Next
	Next
	UnlockBuffer ImageBuffer(in_editor_buffer)
	SetBuffer BackBuffer()
End Function


Function editor_bufferwin(x,y,w,h)
	Local mx,my
	Color 0,0,0
	Rect x-1,y-1,w+2,h+2,1
	Local size=ImageHeight(in_editor_buffer)
	If in_edzoom>1 Then
		If in_editor_bufferz=0 Then
			in_editor_bufferz=CreateImage(size*in_edzoom,size*in_edzoom)
		ElseIf ImageHeight(in_editor_bufferz)<>size*in_edzoom Then
			FreeImage in_editor_bufferz
			in_editor_bufferz=CreateImage(size*in_edzoom,size*in_edzoom)
		EndIf
		For zx=0 To size
			For i=0 To in_edzoom
				CopyRect zx,0,1,size,zx*in_edzoom+i,0,ImageBuffer(in_editor_buffer),ImageBuffer(in_editor_bufferz)
			Next
		Next
		For zy=size To 0 Step -1
			For i=0 To in_edzoom
				CopyRect 0,zy,size*in_edzoom,1,0,zy*in_edzoom+i,ImageBuffer(in_editor_bufferz),ImageBuffer(in_editor_bufferz)
			Next
		Next
		DrawBlockRect in_editor_bufferz,x,y,in_editor_bufferx,in_editor_buffery,w,h
	Else
		DrawBlockRect in_editor_buffer,x,y,in_editor_bufferx,in_editor_buffery,w,h
	EndIf
	Local brush=in_edbrush
	If in_edbrush<3 Then brush=3
	If in_mx>=x Then
		If in_my>=y Then
			If in_mx<=x+w Then
				If in_my<=y+h Then
					If in_mzs#<>0 Then
						in_edbrush=in_edbrush+in_mzs#
						If in_edbrush>99 Then in_edbrush=99
						If in_edbrush<0 Then in_edbrush=0
					EndIf
					brush=in_edbrush
					If in_edbrush<3 Then brush=3
					If in_mdown(2) Then
						in_cursorf=2
						in_editor_bufferx=in_editor_bufferx+in_mxs
						in_editor_buffery=in_editor_buffery-in_mys
					Else
						in_cursorf=-1
						Color 255,255,255
						Oval in_mx-((brush/2)*in_edzoom),in_my-((brush/2)*in_edzoom),brush*in_edzoom,brush*in_edzoom,0
					EndIf
					
					mx=((in_mx-x)+in_editor_bufferx)/in_edzoom
					my=((in_my-y)+in_editor_buffery)/in_edzoom
					Select m_menu
						Case Cmenu_ed_heightmap,Cmenu_ed_heightmap+1
							If ((in_mdown(1) And ((in_mxs<>0) Or (in_mys<>0)))Or in_mhit(1)) Then
								grey=Float(in_slider(0))*2.55
								Select in_edtool
									Case 0
										editor_bufferpaint(mx,my,brush,grey,grey,grey,0,in_slider(10))
									Case 1
										editor_bufferpaint(mx,my,brush,grey,grey,grey,1,in_slider(10))
									Case 2
										editor_buffergamma(mx,my,brush,1,Float(in_slider(10))/10.)
									Case 3
										editor_buffergamma(mx,my,brush,0,Float(in_slider(10))/10.)
									Case 4
										editor_buffergetcolor(mx,my)
								End Select
							EndIf
						Case Cmenu_ed_colormap
							If ((in_mdown(1) And ((in_mxs<>0) Or (in_mys<>0)))Or in_mhit(1)) Then
								Select in_edtool
									Case 0
										editor_bufferpaint(mx,my,brush,in_col(0),in_col(1),in_col(2),0,in_slider(10))
									Case 1
										editor_bufferpaint(mx,my,brush,in_col(0),in_col(1),in_col(2),1,in_slider(10))
									Case 2
										editor_buffergamma(mx,my,brush,1,Float(in_slider(10))/10.)
									Case 3
										editor_buffergamma(mx,my,brush,0,Float(in_slider(10))/10.)
									Case 4
										editor_buffergetcolor(mx,my)
								End Select
							EndIf
						Case Cmenu_ed_grassmap
							If ((in_mdown(1) And ((in_mxs<>0) Or (in_mys<>0)))Or in_mhit(1)) Then
								editor_bufferpaintgrassmap(mx,my,brush)
							EndIf
					End Select
					
				EndIf
			EndIf
		EndIf
	EndIf
End Function

Function editor_bufferpaint(x,y,size,r,g,b,spray=0,a#)
	Local bsize=ImageHeight(in_editor_buffer)
	SetBuffer ImageBuffer(in_editor_buffer)
	LockBuffer ImageBuffer(in_editor_buffer)
	Local rad=size/2
	Local sx=x-rad
	Local sy=y-rad
	Local ex=x+rad
	Local ey=y+rad
	Local rx=x
	Local ry=y
	Local rgb,r2,g2,b2
	Local dist#, perc#
	a#=a#/100.
	a#=1.-a#
	For x=sx To ex
		For y=sy To ey
			If x>=0 Then
				If y>=0 Then
					If x<bsize Then
						If y<bsize Then
							dist#=Sqr((x-rx)^2+(y-ry)^2)
							If dist#<(rad) Then
								If (spray=0) Or Rand(10)=1 Then
									perc#=(dist#/Float(rad))
									If perc#<a# Then perc#=a#
									If perc#<0. Then perc#=0.
									If perc#>1. Then perc#=1.
									rgb=ReadPixelFast(x,y)
									r2=(rgb And $FF0000)/$10000
									g2=(rgb And $FF00)/$100
									b2=rgb And $FF
									editor_bufferpaint_apm(r,g,b,r2,g2,b2,perc#,x,y)
								EndIf
							EndIf
						EndIf
					EndIf
				EndIf
			EndIf
		Next
	Next
	UnlockBuffer ImageBuffer(in_editor_buffer)
	SetBuffer BackBuffer()
End Function


Function editor_buffergetcolor(x,y)
	SetBuffer ImageBuffer(in_editor_buffer)	
	GetColor x,y
	r=ColorRed()
	g=ColorGreen()
	b=ColorBlue()
	
	If m_menu=Cmenu_ed_colormap
		in_col(0)=r
		in_col(1)=g
		in_col(2)=b
	ElseIf m_menu=Cmenu_ed_heightmap
		in_slider(0)=Float(r)/2.55
	EndIf
	
	SetBuffer BackBuffer()
End Function

Function editor_buffergamma(x,y,size,mode,power#=10.)
	Local bsize=ImageHeight(in_editor_buffer)
	SetBuffer ImageBuffer(in_editor_buffer)
	LockBuffer ImageBuffer(in_editor_buffer)
	Local rad=size/2
	Local sx=x-rad
	Local sy=y-rad
	Local ex=x+rad
	Local ey=y+rad
	Local rx=x
	Local ry=y
	Local rgb,r,g,b
	Local dist#, perc#
	For x=sx To ex
		For y=sy To ey
			If x>=0 Then
				If y>=0 Then
					If x<bsize Then
						If y<bsize Then
							dist#=Sqr((x-rx)^2+(y-ry)^2)
							If dist#<(rad) Then
								If (spray=0) Or Rand(10)=1 Then
									perc#=(dist#/Float(rad))
									If perc#>1. Then perc#=1.
									perc#=1.-perc#
									rgb=ReadPixelFast(x,y)
									r=(rgb And $FF0000)/$10000
									g=(rgb And $FF00)/$100
									b=rgb And $FF
									If mode=1 Then
										r=r+power#*perc#
										g=g+power#*perc#
										b=b+power#*perc#
										If r>255 Then r=255
										If g>255 Then g=255
										If b>255 Then b=255
									Else
										r=r-power#*perc#
										g=g-power#*perc#
										b=b-power#*perc#
										If r<0 Then r=0
										If g<0 Then g=0
										If b<0 Then b=0
									EndIf
									rgb=255*$1000000+r*$10000+g*$100+b
									WritePixelFast x,y,rgb
								EndIf
							EndIf
						EndIf
					EndIf
				EndIf
			EndIf
		Next
	Next
	UnlockBuffer ImageBuffer(in_editor_buffer)
	SetBuffer BackBuffer()
End Function


Function editor_bufferpaintgrassmap(x,y,size)
	grass_x=-2147483648
	Local bsize=ImageHeight(in_editor_buffer)
	SetBuffer ImageBuffer(in_editor_buffer)
	LockBuffer ImageBuffer(in_editor_buffer)
	Local rad=size/2
	Local sx=x-rad
	Local sy=y-rad
	Local ex=x+rad
	Local ey=y+rad
	Local rx=x
	Local ry=y
	Local rgb,r,g,b
	Local dist#
	For x=sx To ex
		For y=sy To ey
			If x>=0 Then
				If y>=0 Then
					If x<bsize Then
						If y<bsize Then
							dist#=Sqr((x-rx)^2+(y-ry)^2)
							If dist#<(rad) Then
								If in_edtool=0 Then
									grass_rgb(x,y,3)=in_edgrassmode
								Else
									If Rand(5)=1 Then grass_rgb(x,y,3)=in_edgrassmode
								EndIf
								
								If grass_rgb(x,y,3)=1 Then
									r=grass_rgb(x,y,0)
									g=grass_rgb(x,y,1)
									b=grass_rgb(x,y,2)
								Else
									r=255:g=0:b=255
								EndIf
								rgb=255*$1000000+r*$10000+g*$100+b
								WritePixelFast x,y,rgb
							EndIf
						EndIf
					EndIf
				EndIf
			EndIf
		Next
	Next
	UnlockBuffer ImageBuffer(in_editor_buffer)
	SetBuffer BackBuffer()
End Function


Function editor_bufferpaint_apm(r#,g#,b#,r2#,g2#,b2#,a#,x,y)
	Local nr=(r2#*a#)+(r#*(1.-a#))
	Local ng=(g2#*a#)+(g#*(1.-a#))
	Local nb=(b2#*a#)+(b#*(1.-a#))
	Local rgb=255*$1000000+nr*$10000+ng*$100+nb
	WritePixelFast x,y,rgb
End Function


Function editor_getskybox$(name$)
	Local s
	s=Instr(name$,"_bk.")
	If s=0 Then s=Instr(name$,"_dn.")
	If s=0 Then s=Instr(name$,"_do.")
	If s=0 Then s=Instr(name$,"_fr.")
	If s=0 Then s=Instr(name$,"_lf.")
	If s=0 Then s=Instr(name$,"_rt.")
	If s=0 Then s=Instr(name$,"_up.")
	If s=0 Then
		Return ""
	Else
		Return Left(name$,s-1)
	EndIf
End Function

Function editor_settoterrain()
	Local x#,z#
	
	For Tobject.Tobject=Each Tobject
		x#=EntityX(Tobject\h)
		z#=EntityZ(Tobject\h)
		PositionEntity Tobject\h,x#,e_tery(x#,z#),z#
	Next
	
	For Tunit.Tunit=Each Tunit
		x#=EntityX(Tunit\h)
		z#=EntityZ(Tunit\h)
		PositionEntity Tunit\h,x#,e_tery(x#,z#),z#
	Next
	
	For Titem.Titem=Each Titem
		If Titem\parent_id=0 Then
			x#=EntityX(Titem\h)
			z#=EntityZ(Titem\h)
			PositionEntity Titem\h,x#,e_tery(x#,z#),z#
		EndIf
	Next

End Function


Function editor_resetcam()
	Local x=-(ter_size*Cworld_size)/2
	Local z=-(ter_size*Cworld_size)/2
	Local y=e_tery(x,z)
	If y<0 Then
		y=150
	Else
		y=y+150
	EndIf
	PositionEntity cam,x,y,z
	Local anglepiv=CreatePivot()
	PositionEntity anglepiv,0,e_tery(0,0),0
	PointEntity cam,anglepiv
	FreeEntity anglepiv
End Function


Function editor_range(rx#,ry#,rz#,range)
	If range=0 Then Return
	Color 0,Abs(255.*Cos(in_ca#)),0
	Local lx1,ly1,lx2,ly2
	Local p=CreatePivot()
	For i=(20.*(in_ca#/360.)) To 360 Step 20
		PositionEntity p,rx#,ry#,rz#
		RotateEntity p,0,i,0
		MoveEntity p,0,0,range
		CameraProject(cam,EntityX(p),EntityY(p),EntityZ(p))
		lx1=ProjectedX()
		If lx1>210 Then
			ly1=ProjectedY()
			If ly1>0 Then
				PositionEntity p,rx#,ry#,rz#
				RotateEntity p,0,i+5,0
				MoveEntity p,0,0,range
				CameraProject(cam,EntityX(p),EntityY(p),EntityZ(p))
				lx2=ProjectedX()
				If lx2>210 Then
					ly2=ProjectedY()
					If ly2>0 Then
						Line lx1,ly1,lx2,ly2
					EndIf
				EndIf
			EndIf
		EndIf
	Next
	FreeEntity p
End Function


Function editor_paintcm(x3d#,z3d#,size,r,g,b)
	Local sc3d#=Cworld_size*ter_size
	Local sc2d#=TextureHeight(ter_tex_color)
	Local tfac#=sc3d#/sc2d#
	Local x=(x3d#+(sc3d#/2.))/tfac#
	Local y=(z3d#+(sc3d#/2.))/tfac#
	
	Local bsize=Int(sc2d#)
	SetBuffer TextureBuffer(ter_tex_color)
	LockBuffer TextureBuffer(ter_tex_color)
	Local rad=size/2
	Local sx=x-rad
	Local sy=y-rad
	Local ex=x+rad
	Local ey=y+rad
	Local rx=x
	Local ry=y
	Local rgb,r2,g2,b2
	Local dist#, perc#
	a#=0.
	For x=sx To ex
		For y=sy To ey
			If x>=0 Then
				If y>=0 Then
					If x<bsize Then
						If y<bsize Then
							dist#=Sqr((x-rx)^2+(y-ry)^2)
							If dist#<(rad) Then
								perc#=(dist#/Float(rad))
								If perc#<a# Then perc#=a#
								If perc#<0. Then perc#=0.
								If perc#>1. Then perc#=1.
								rgb=ReadPixelFast(x,y)
								r2=(rgb And $FF0000)/$10000
								g2=(rgb And $FF00)/$100
								b2=rgb And $FF
								editor_bufferpaint_apm(r,g,b,r2,g2,b2,perc#,x,y)
							EndIf
						EndIf
					EndIf
				EndIf
			EndIf
		Next
	Next
	UnlockBuffer TextureBuffer(ter_tex_color)
	SetBuffer BackBuffer()	
End Function


Function editor_jumpto(class,id,dist#=50)
	h=parent_h(class,id)
	If h<>0 Then
		PositionEntity cam,EntityX(h),EntityY(h),EntityZ(h)
		TranslateEntity cam,-dist#,dist#*2.,-dist#
		PointEntity cam,h
		For i=-10 To 10 Step 5
			p_add(EntityX(h),EntityY(h)+i,EntityZ(h),Cp_shockwave,30-(Abs(i)*2),0.5)
			EntityColor TCp\h,0,255,0
		Next
	EndIf
	m_menu=0
End Function


Function editor_setfx(h)
	For i=-10 To 10 Step 5
		p_add(EntityX(h),EntityY(h)+i,EntityZ(h),Cp_shockwave,30-(Abs(i)*2),0.5)
		EntityColor TCp\h,0,255,0
	Next
End Function


Function editor_testmap(forcecheckmap=0)
	If forcecheckmap=0 Then editor_setmenu(Cmenu_ed_check)
	
	set_debug=1
	
	If forcecheckmap=1 Then
		m_section=Csection_game_sp
		set_debug_testmap=1
		set_debug_testmapfile$="editor\checkmap"
		load_map("maps\editor\checkmap.s2","")
		Return 1
	EndIf
	
	If in_edmap$<>"" Then
		If gui_msgdecide(se$(206),0,sm$(2),sm$(1)) Then
			If Instr(in_edmap$,":") Then
				save_map(in_edmap$+".s2","map",map_password$)
			Else
				save_map("maps\"+in_edmap$+".s2","map",map_password$)
			EndIf
			editor_quit()
			m_section=Csection_game_sp
			set_debug_testmap=1
			set_debug_testmapfile$=in_edmap$
			If Instr(in_edmap$,":") Then
				load_map(in_edmap$+".s2",map_password$)
			Else
				load_map("maps\"+in_edmap$+".s2",map_password$)
			EndIf
			Return 1
		EndIf
	EndIf
	
	If save_map("maps\editor\checkmap.s2","map","") Then
		editor_quit()
		m_section=Csection_game_sp
		set_debug_testmap=1
		set_debug_testmapfile$="editor\checkmap"
		load_map("maps\editor\checkmap.s2","")
		Return 1
	Else
		gui_msg(se$(205),Cbmpf_red)
	EndIf
End Function


Function editor_list(class,group$)
	If class<>ed_current_class Or group$<>ed_current_group$ Then
		ed_current_class=class
		ed_current_group=group
		ed_current_c=0
		For Tgroupl.Tgroupl=Each Tgroupl
			Delete Tgroupl
		Next
		group$=group$+","
		Local tmp$
		Select class
			Case Cclass_object
				For i=0 To Cobject_count
					If Dobject_name$(i)<>"" Then
						If Dobject_ined(i)<>0 Then
							tmp$=Dobject_group(i)+","
							tmp$=Replace(tmp$," ","")
							If Instr(tmp$,group$)>0 Or group$="," Then
								Tgroupl=New Tgroupl
								Tgroupl\id=i
								ed_current_c=ed_current_c+1
							EndIf
						EndIf
					EndIf
				Next
			Case Cclass_unit
				For i=0 To Cunit_count
					If Dunit_name$(i)<>"" Then
						If Dunit_ined(i)<>0 Then
							tmp$=Dunit_group(i)+","
							tmp$=Replace(tmp$," ","")
							If Instr(tmp$,group$)>0 Or group$="," Then
								Tgroupl=New Tgroupl
								Tgroupl\id=i
								ed_current_c=ed_current_c+1
							EndIf
						EndIf
					EndIf
				Next
			Case Cclass_item
				For i=0 To Citem_count
					If Ditem_name$(i)<>"" Then
						If Ditem_ined(i)<>0 Then
							tmp$=Ditem_group(i)+","
							tmp$=Replace(tmp$," ","")
							If Instr(tmp$,group$)>0 Or group$="," Then
								Tgroupl=New Tgroupl
								Tgroupl\id=i
								ed_current_c=ed_current_c+1
							EndIf
						EndIf
					EndIf
				Next
			Case Cclass_info
				For i=0 To Cinfo_count
					If Dinfo_name$(i)<>"" Then
						tmp$=Dinfo_group(i)+","
						tmp$=Replace(tmp$," ","")
						If Instr(tmp$,group$)>0 Or group$="," Then
							Tgroupl=New Tgroupl
							Tgroupl\id=i
							ed_current_c=ed_current_c+1
						EndIf
					EndIf
				Next
		End Select
	EndIf
End Function
