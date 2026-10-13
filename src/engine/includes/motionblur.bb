;############################################ Setup Event Blur
Function mb_setup()
	;Original camera sprite and accumulated frame texture, used only by effects.
	mb_sprite=CreateSprite(cam)
	SpriteViewMode mb_sprite,2
	PositionEntity mb_sprite,0,0,10
	EntityOrder mb_sprite,-200
	Local ratio#=Float(set_scry)/Float(set_scrx)
	ScaleSprite mb_sprite,10.,ratio#*10.
	EntityAlpha mb_sprite,0.
	HideEntity mb_sprite
	mb_tex=CreateTexture(set_scrx,set_scry,1+16+32+256)
	If mb_tex=0 Then
		con_add("ERROR: Failed to setup motion blur buffer",Cbmpf_red)
	Else
		TextureBlend mb_tex,2
		ScaleTexture mb_tex,(Float TextureWidth(mb_tex)/Float set_scrx),(Float TextureHeight(mb_tex)/Float set_scry)
		EntityTexture mb_sprite,mb_tex
	EndIf
End Function

;Hide the old frame so the next effect starts from a fresh capture.
Function mb_reset()
	If mb_sprite<>0 Then HideEntity mb_sprite
End Function

;############################################ Update Event Blur
Function mb_update()
	If mb_sprite=0 Then Return
	If in_scriptblur#>0. Then mb_override(in_scriptblur#)
	Local strength#=set_mb_alpha_override#
	If strength#<=0. Or mb_tex=0 Then
		mb_reset()
		Return
	EndIf
	If strength#>Cpeakblur# Then strength#=Cpeakblur#
	;RenderWorld already drew the previous capture. Keep the original feedback.
	CopyRect 0,0,set_scrx,set_scry,0,0,BackBuffer(),TextureBuffer(mb_tex)
	EntityAlpha mb_sprite,strength#
	ShowEntity mb_sprite
End Function

;############################################ Event Blur Alpha Override
Function mb_override(a#)
	If a#>set_mb_alpha_override# Then set_mb_alpha_override#=a#
End Function
