Function grass_map()
	grass_free()
	Local fade_start
	Local alpha#
	Select set_grass
		Case 0 grass_c=0															
		Case 1 grass_c=10:grass_dist=23:fade_start=grass_dist*1:alpha=0.45
		Case 2 grass_c=20:grass_dist=22:fade_start=grass_dist*2:alpha=0.43
		Case 3 grass_c=40:grass_dist=21:fade_start=grass_dist*3:alpha=0.41
		Case 4 grass_c=80:grass_dist=20:fade_start=grass_dist*5:alpha=0.39
	End Select
	Dim grass_h(grass_c,grass_c)
	For x=0 To grass_c
		For y=0 To grass_c
			grass_h(x,y)=CopyEntity(gfx_grasspread)
			ShowEntity grass_h(x,y)
			EntityFX grass_h(x,y),16
			EntityColor grass_h(x,y),0,200,0
			EntityAutoFade grass_h(x,y),fade_start,grass_dist*(grass_c/2)
			EntityAlpha grass_h(x,y),alpha
			EntityTexture grass_h(x,y),gfx_grasspreadtex
			HideEntity grass_h(x,y)
		Next
	Next
	grass_mapsize=TextureHeight(ter_tex_color)
	Dim grass_rgb(grass_mapsize,grass_mapsize,3)
	SetBuffer TextureBuffer(ter_tex_color)
	LockBuffer TextureBuffer(ter_tex_color)
	For x=0 To grass_mapsize-1
		For y=0 To grass_mapsize-1
			rgb=ReadPixelFast(x,y)
			grass_rgb(x,y,0)=(rgb And $FF0000)/$10000
			grass_rgb(x,y,1)=(rgb And $FF00)/$100
			grass_rgb(x,y,2)=rgb And $FF
			grass_rgb(x,y,3)=1
		Next
	Next
	UnlockBuffer TextureBuffer(ter_tex_color)
	SetBuffer BackBuffer()
	grass_f#=Float(Float(Float(ter_size)/Float(grass_mapsize))*Float(Cworld_size))
	grass_x=-2147483648
	grass_y=-2147483648
	grass_spread()
End Function


Function grass_heightmap()
	Local size=ter_size
	Local ctexsize=TextureHeight(ter_tex_color)
	Local texsize=ctexsize-1
	Local rx=-((size/2)*Cworld_size)
	Local ry=-((size/2)*Cworld_size)
	Local fac#=Float(Float(size*Cworld_size)/Float(ctexsize))
	For x=0 To texsize
		For y=0 To texsize
			grass_rgb(x,y,3)=1
		
			h#=Float(TerrainY(ter,rx+(x*fac#),0,ry+(y*fac#))/Float(Cworld_height))+0.5
			
			If h#<0.54 Then
				If h#<0.52 Then
					grass_rgb(x,y,3)=0
				Else
					If Rand(3)<>1 Then grass_rgb(x,y,3)=0
				EndIf
			ElseIf h#>0.57 Then
				If Rand(10)<>1 Then grass_rgb(x,y,3)=0
			EndIf
		Next
	Next
	grass_x=-2147483648
	grass_y=-2147483648
End Function


Function grass_spread()
	grass_wind#=(grass_wind#+2.*f#) Mod 360
	RotateTexture gfx_grasspreadtex,0+Sin(grass_wind#)*1.5
	If set_grass>0 Then
		Local half=-(grass_c/2)
		Local camx=EntityX(cam)
		Local camz=EntityZ(cam)
		Local terhalf=(ter_size/2)*Cworld_size
		Local update=0
		If grass_x<>((Int(camx)/grass_dist)+half)*grass_dist Then
			update=1
		ElseIf grass_y<>((Int(camz)/grass_dist)+half)*grass_dist Then
			update=1
		EndIf
		If update=1 Then
			grass_x=((Int(camx)/grass_dist)+half)*grass_dist
			grass_y=((Int(camz)/grass_dist)+half)*grass_dist
			For x=0 To grass_c
				For y=0 To grass_c
					gx#=((Int(camx)/grass_dist)+half+x)*grass_dist
					gz#=((Int(camz)/grass_dist)+half+y)*grass_dist
					If Int(gx#) Mod 2=0 Then gx#=gx#+grass_dist/2
					If Int(gz#) Mod 2=0 Then gz#=gz#+grass_dist/2
					gy#=TerrainY(ter,gx#,0,gz#)
					PositionEntity grass_h(x,y),gx#,gy#,gz#
					gcx=((gx+terhalf)/grass_f#)
					gcy=((gz+terhalf)/grass_f#)
					If gcx>=0 And gcx<=grass_mapsize And gcy>=0 And gcy<=grass_mapsize Then
						If grass_rgb(gcx,gcy,3)=1 Then
							EntityColor grass_h(x,y),grass_rgb(gcx,gcy,0),grass_rgb(gcx,gcy,1),grass_rgb(gcx,gcy,2)
							ShowEntity(grass_h(x,y))
						Else
							HideEntity(grass_h(x,y))
						EndIf
					Else
						HideEntity(grass_h(x,y))
					EndIf
				Next
			Next
		EndIf
	EndIf
End Function


Function grass_free()
	For x=0 To grass_c
		For y=0 To grass_c
			If grass_h(x,y)<>0 Then FreeEntity grass_h(x,y):grass_h(x,y)=0
		Next
	Next
	grass_x=-2147483648
	grass_y=-2147483648
End Function

Function grass_colors(r,g,b)
	grass_x=-2147483648
	For x=0 To grass_mapsize-1
		For y=0 To grass_mapsize-1
			grass_rgb(x,y,0)=r
			grass_rgb(x,y,1)=g
			grass_rgb(x,y,2)=b
		Next
	Next
End Function
