Function focus_update()
	If m_menu=0 Then
		If ms-in_fo_timer>300 Then
			in_fo_timer=ms
			in_focus=0
			
			If pc_typ<>0 Then
				in_focus=pc_typ	
			EndIf
			
		EndIf
	EndIf
End Function
	

Function focus_draw()
	If in_focus<>0 Then
		Select in_focus	
			Case 1
				bmpf_txt_c(in_fo_x,in_fo_y,in_fo_txt$)
				If in_perc>-1 Then
					x=set_scrx/2-51
					y=55
				EndIf
				
			Case Cstate_pc_build
				game_buildfocus(pc_child)
				If (gt-pc_gt)>=3000 Then
					pc_typ=0:in_focus=0
				EndIf
				
			Case Cstate_pc_dig
				bmpf_txt_c(set_scrx/2,35,sm$(178))
				x=set_scrx/2-51:y=55
				perc=Float(Float(gt-pc_gt)/Float(game_digtime))*100.
				DrawBlock gfx_if_barback,x,y
				DrawBlockRect gfx_bars,x+1,y+1,0,0,perc,5
				If in_t500go Then sfx sfx_dig
				If (gt-pc_gt)>=game_digtime Then
					pc_typ=0:in_focus=0
					game_dig()
				EndIf
			
			Case Cstate_pc_fish
				bmpf_txt_c(set_scrx/2,35,sm$(179))
				x=set_scrx/2-51:y=55
				perc=Float(Float(gt-pc_gt)/Float(game_fishtime))*100.
				DrawBlock gfx_if_barback,x,y
				DrawBlockRect gfx_bars,x+1,y+1,0,0,perc,5
				If (gt-pc_gt)>=game_fishtime Then
					pc_typ=0:in_focus=0
					game_fish()
				EndIf
				
			Case Cstate_pc_custom
				bmpf_txt_c(set_scrx/2,35,in_pc_custom$)
				x=set_scrx/2-51:y=55
				perc=Float(Float(gt-pc_gt)/Float(in_pc_customt))*100.
				DrawBlock gfx_if_barback,x,y
				DrawBlockRect gfx_bars,x+1,y+1,0,0,perc,5
				If (gt-pc_gt)>=in_pc_customt Then
					pc_typ=0
					in_focus=0
					If in_pc_menu<>0 Then
						m_menu=in_pc_menu
						MoveMouse(in_pc_menu_x,in_pc_menu_y)
						in_pc_menu=0
					EndIf
					If in_pc_event$<>"" Then
						parse_globalevent(in_pc_event$)
					EndIf
				EndIf
				
				
		End Select
	EndIf
End Function
