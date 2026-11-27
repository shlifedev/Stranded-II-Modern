Function seq_blend()
	If seq_blend=1 Then
		If Int(seq_blendp#)<80 Then
			seq_blendp#=seq_blendp+(2.*f#)
			If seq_blendp#>80. Then seq_blendp#=80.
		EndIf
	Else
		If Int(seq_blendp#)>0 Then
			seq_blendp#=seq_blendp-(2.*f#)
		EndIf
	EndIf
	If seq_cls=1 Then
		ClsColor seq_cls_r,seq_cls_g,seq_cls_b
		Cls
		ClsColor 0,0,0
	EndIf
	If seq_img<>0 Then
		If seq_img_masked=1 Then
			DrawImage seq_img,set_scrx/2,set_scry/2
		Else
			DrawBlock seq_img,set_scrx/2,set_scry/2
		EndIf
	EndIf
	If Int(seq_blendp#)>0 Then
		Color 0,0,0
		Rect 0,0,set_scrx,seq_blendp#,1
		Rect 0,set_scry-seq_blendp#,set_scrx,seq_blendp#,1
	EndIf
	If m_section=Csection_game_sp Then 
		If m_menu=Cmenu_if_movie
			If seq_msg$(0,0)<>"" Then bmpf_txt_c(set_scrx/2,set_scry-50,seq_msg$(0,0),seq_msg$(0,1))
			If seq_msg$(1,0)<>"" Then bmpf_txt_c(set_scrx/2,30,seq_msg$(1,0),seq_msg$(1,1))
			If seq_msg$(2,0)<>"" Then bmpf_txt_c(set_scrx/2,set_scry/2-10,seq_msg$(2,0),seq_msg$(2,1))
		EndIf
	EndIf
End Function


Function seq_start()
	seq_blend=1
	seq_skipable=0
	If parse_param() Then
		seq_blend=param()
		If parse_comma(0) Then
			seq_skipable=Int(param())
			If seq_skipable<>1 Then
				seq_skipable=0
			EndIf
		EndIf
	EndIf
	seq_cpoint=0
	seq_cpoint_class=0
	seq_cpoint_id=0
	Dim seq_msg$(2,1)
	seq_tmod=1
	seq_tabs=1
	seq_tlast=0
	seq_cls=0
	seq_cstart=0
	seq_cend=0
	seq_cstartt=0
	seq_cendt=0
	seq_hideplayer=1
	seq_flw_class=0
	If seq_img<>0 Then
		FreeImage seq_img
		seq_img=0
	EndIf
	For Tseq.Tseq=Each Tseq
		Delete Tseq
	Next
	p_kill2d()
	If m_section=Csection_game_sp Then m_menu=Cmenu_if_movie
	seq_start=modern_clock()
	If set_debug=1 Then
		set_debug_seq=1
	EndIf
End Function


Function seq_end()
	If m_menu=Cmenu_if_movie Then m_menu=0
	seq_blend=0
	seq_cls=0
End Function


Function seq_update()
	
	seq_cammovement()
	seq_camrotation()
	
	If seq_hideplayer=1 Then
		HideEntity g_cplayer\mh
	Else
		ShowEntity g_cplayer\mh
	EndIf
	
	Local time=(modern_clock()-seq_start)+1
	For Tseq.Tseq=Each Tseq
		If time>Tseq\t Then
			
			If set_debug=1 Then
				If set_debug_seq=1 Then con_add("Sequence: "+Tseq\event$+" @ "+Tseq\t+" ms",Cbmpf_yellow)
			EndIf
			
			Select Tseq\event$
				Case "end" seq_end()
				Case "msg"
					seq_msg$(Tseq\in[1],0)=Tseq\txt$[0]
					seq_msg$(Tseq\in[1],1)=Tseq\in[0]
				Case "msgclear"
					If Tseq\in[0]=-1 Then
						seq_msg$(0,0)="":seq_msg$(1,0)="":seq_msg$(2,0)=""
					Else
						seq_msg$(Tseq\in[0],0)=""
					EndIf
				Case "sound"
					sfx_play(Tseq\txt$[0],Tseq\fl[0],Tseq\fl[1],Tseq\in[0])
				Case "bar" seq_blend=Tseq\in[0]
				Case "hidebar" seq_blend=0:seq_blendp#=0
				Case "showbar" seq_blend=1:seq_blendp#=80
				
				Case "event"
					If Tseq\in[0]=0 Then
						parse_globalevent(Tseq\txt$[0],"performed by seqevent")
					Else
						parse_emuevent(Tseq\in[0],Tseq\in[1],Tseq\txt$[0])
					EndIf	
				Case "script"
					parse_task(0,0,"sequence","triggered by seqscript command",Tseq\txt$[0])
				Case "hideplayer"
					seq_hideplayer=Tseq\in[0]
				
				Case "setcam"
					If con_info(Tseq\in[0]) Then
						PositionEntity cam,EntityX(TCinfo\h),EntityY(TCinfo\h),EntityZ(TCinfo\h)
						RotateEntity cam,EntityPitch(TCinfo\h),EntityYaw(TCinfo\h),0
						PositionEntity seq_cpoint_p,EntityX(cam),EntityY(cam),EntityZ(cam)
						RotateEntity seq_cpoint_p,EntityPitch(cam),EntityYaw(cam),EntityRoll(cam)
						MoveEntity  seq_cpoint_p,0,0,100000
						seq_cstart=0
						seq_cend=0
					Else
						con_add("ERROR: "+Tseq\event$+" @ "+Tseq\t+" ms - info "+Tseq\in[0]+" does not exist",Cbmpf_red)
					EndIf
				Case "movecam"
					If seq_cstart=0 Then seq_cstart=CreatePivot()
					If seq_cend=0 Then seq_cend=CreatePivot()
					seq_cstartt=Tseq\t
					seq_cendt=Tseq\in[0]
					PositionEntity seq_cstart,EntityX(cam),EntityY(cam),EntityZ(cam)
					RotateEntity seq_cstart,EntityPitch(cam),EntityYaw(cam),0
					If con_info(Tseq\in[1]) Then
						PositionEntity seq_cend,EntityX(TCinfo\h),EntityY(TCinfo\h),EntityZ(TCinfo\h)
						RotateEntity seq_cend,EntityPitch(TCinfo\h),EntityYaw(TCinfo\h),0
					EndIf
					PositionEntity seq_cpoint_p,EntityX(cam),EntityY(cam),EntityZ(cam)
					RotateEntity seq_cpoint_p,EntityPitch(cam),EntityYaw(cam),EntityRoll(cam)
					MoveEntity  seq_cpoint_p,0,0,100000
					seq_cammovement()
					seq_camrotation()	
				Case "cammode"
					seq_cpoint=Tseq\in[0]
					seq_cpoint_class=Tseq\in[1]
					seq_cpoint_id=Tseq\in[2]
					PositionEntity seq_cpoint_p,EntityX(cam),EntityY(cam),EntityZ(cam)
					RotateEntity seq_cpoint_p,EntityPitch(cam),EntityYaw(cam),EntityRoll(cam)
					MoveEntity  seq_cpoint_p,0,0,100000
					seq_camrotation()
				Case "flash"
					p_add(0,0,0,Cp_flash,Tseq\fl#[0],Tseq\fl#[1])
					EntityColor TCp\h,Tseq\in[0],Tseq\in[1],Tseq\in[2]
					
				Case "fade"
					p_add(0,0,0,Cp_seqfade)
					EntityColor TCp\h,Tseq\in[1],Tseq\in[2],Tseq\in[3]
					TCp\x#=Tseq\t
					TCp\y#=Tseq\in[0]
					TCp\fx#=Tseq\in[4]
					Select Tseq\in[4]
						Case 0 EntityAlpha TCp\h,0.
						Case 1 EntityAlpha TCp\h,0.
						Case 2 EntityAlpha TCp\h,1.
					End Select
					
				Case "cls"
					seq_cls=Tseq\in[0]
					seq_cls_r=Tseq\in[1]
					seq_cls_g=Tseq\in[2]
					seq_cls_b=Tseq\in[3]
					
				Case "image"
					If seq_img<>0 Then FreeImage seq_img
					If Tseq\txt$[0]="0" Or Tseq\txt$[0]="" Then
						seq_img=0
					Else
						seq_img=load_image(Tseq\txt$[0],Tseq\in[0])
						seq_img_masked=Tseq\in[0]
						If seq_img<>0 Then
							MidHandle seq_img
						EndIf
					EndIf
					
				Case "itxt"
					If seq_img<>0 Then
						SetBuffer ImageBuffer(seq_img)
						Select Tseq\in[3]
							Case 0
								bmpf_txt(Tseq\in[0],Tseq\in[1],Tseq\txt$[0],Tseq\in[2])
							Case 1
								bmpf_txt_c(Tseq\in[0],Tseq\in[1],Tseq\txt$[0],Tseq\in[2])
							Case 2
								bmpf_txt_r(Tseq\in[0],Tseq\in[1],Tseq\txt$[0],Tseq\in[2])
							Default
								con_add("seqimagetext invalid alignment (only 0,1,2 allowed)",Cbmpf_red)
						End Select
						SetBuffer BackBuffer()
					EndIf
				Case "camfollow"
					If Tseq\in[0]=0 Then
						seq_flw_class=0
					Else
						seq_cpoint=1
						seq_cpoint_class=Tseq\in[0]
						seq_cpoint_id=Tseq\in[1]
						PositionEntity seq_cpoint_p,EntityX(cam),EntityY(cam),EntityZ(cam)
						RotateEntity seq_cpoint_p,EntityPitch(cam),EntityYaw(cam),EntityRoll(cam)
						MoveEntity  seq_cpoint_p,0,0,100000
						seq_camrotation()
						seq_flw_class=Tseq\in[0]
						seq_flw_id=Tseq\in[1]
						seq_flw_x#=Tseq\fl#[0]
						seq_flw_y#=Tseq\fl#[1]
						seq_flw_z#=Tseq\fl#[2]
						seq_cammovement()

					EndIf
				
				Default con_add("INTERNAL ERROR: invalid event '"+Tseq\event$+"' for seq_update",Cbmpf_red)
				
			End Select
			Delete Tseq
		EndIf
	Next
	
End Function




Function seq_cammovement()
	If seq_cstart<>0 Then
		If seq_cend<>0 Then
			
			Local x1#=EntityX(seq_cstart)
			Local y1#=EntityY(seq_cstart)
			Local z1#=EntityZ(seq_cstart)
			Local x2#=EntityX(seq_cend)
			Local y2#=EntityY(seq_cend)
			Local z2#=EntityZ(seq_cend)
			
			Local time=(modern_clock()-seq_start)
			Local t1=time-seq_cstartt
			Local t2=seq_cendt-seq_cstartt
			Local perc#=Float(Float(t1)/Float(t2))
			If perc#<0. Then
				perc#=0.
			ElseIf perc#>1. Then
				perc#=1.
			EndIf
			
			Local dx#=x2#-x1#
			Local dy#=y2#-y1#
			Local dz#=z2#-z1#
			PositionEntity cam,x1+(dx*perc#),y1+(dy*perc#),z1+(dz*perc#)
			
			If time>=seq_cendt Then
				FreeEntity seq_cstart:seq_cstart=0
				FreeEntity seq_cend:seq_cend=0
			EndIf
			
		EndIf
	EndIf
	
	If seq_flw_class<>0 Then
		If parent_pos(seq_flw_class,seq_flw_id) Then
			tmp_x#=tmp_x#+seq_flw_x#
			tmp_y#=tmp_y#+seq_flw_y#
			tmp_z#=tmp_z#+seq_flw_z#
			PositionEntity cam,tmp_x#,tmp_y#,tmp_z#
		EndIf
	EndIf
	
	If (EntityY(cam)-30)<e_tery(EntityX(cam),EntityZ(cam)) Then
		PositionEntity cam,EntityX(cam),e_tery(EntityX(cam),EntityZ(cam))+30,EntityZ(cam)
	EndIf
End Function


Function seq_camrotation()
	Select seq_cpoint
		Case 0
			center=CreatePivot()
			PointEntity cam,center
			FreeEntity center
		Case 1
			h=parent_h(seq_cpoint_class,seq_cpoint_id)
			If h<>0 Then
				PointEntity cam,h
			EndIf
		Case 2
			If seq_cend<>0 Then
					Local rt=CreatePivot()
					PositionEntity rt,EntityX(seq_cend),EntityY(seq_cend),EntityZ(seq_cend)
					RotateEntity rt,EntityPitch(seq_cend),EntityYaw(seq_cend),EntityRoll(seq_cend)
					MoveEntity rt,0,0,100000
					x1#=EntityX(seq_cpoint_p)
					y1#=EntityY(seq_cpoint_p)
					z1#=EntityZ(seq_cpoint_p)
					x2#=EntityX(rt)
					y2#=EntityY(rt)
					z2#=EntityZ(rt)
					dx#=x2#-x1#
					dy#=y2#-y1#
					dz#=z2#-z1#
					Local time=(modern_clock()-seq_start)
					Local t1=time-seq_cstartt
					Local t2=seq_cendt-seq_cstartt
					Local perc#=Float(Float(t1)/Float(t2))
					If perc#<0. Then
						perc#=0.
					ElseIf perc#>1. Then
						perc#=1.
					EndIf
					PositionEntity rt,x1+(dx*perc#),y1+(dy*perc#),z1+(dz*perc#)
					PointEntity cam,rt
					FreeEntity rt
			EndIf

			
		Case 3
		
		Case 4
			
			If set_xinvert=1 Then
				in_mxs#=-in_mxs#
			EndIf
			If in_mxs<>0 Then RotateEntity cam,EntityPitch(cam),EntityYaw(cam)+(in_mxs#*set_msens#),EntityRoll(cam)
			If in_mys<>0 Then
				in_mys#=(in_mys#*set_msens#)*set_minvert
				If in_mys>0 Then
					If EntityPitch(cam)+in_mys>85
						in_mys=85-EntityPitch(cam)
					EndIf
					RotateEntity cam,EntityPitch(cam)+in_mys,EntityYaw(cam),EntityRoll(cam)
				ElseIf in_mys<0 Then
					If EntityPitch(cam)+in_mys<-85
						in_mys=-85-EntityPitch(cam)
					EndIf
					RotateEntity cam,EntityPitch(cam)+in_mys,EntityYaw(cam),EntityRoll(cam)
				EndIf
			EndIf
			
			
	End Select
End Function



Function seq_addevent(event$)
	Tseq.Tseq=New Tseq
	Local t=param()
	If seq_tabs=1 Then
		Tseq\t=t*seq_tmod
	Else
		Tseq\t=seq_tlast+(t*seq_tmod)
	EndIf
	seq_tlast=Tseq\t
	Tseq\event$=event$
	Select event$
		Case "end"
		Case "msg"
			parse_comma(1)
			Tseq\txt$[0]=param()
			Tseq\in[0]=0:Tseq\in[1]=0
			If parse_comma(0) Then Tseq\in[0]=param()
			If parse_comma(0) Then Tseq\in[1]=param()
		Case "msgclear"
			Tseq\in[0]=-1
			If parse_comma(0) Then Tseq\in[0]=param()
		Case "sound"
			Tseq\fl#[0]=1.
			Tseq\fl#[1]=0.
			Tseq\in[0]=0
			parse_comma(1)
			Tseq\txt$[0]=param()
			If parse_comma(0) Then Tseq\fl#[0]=param()
			If parse_comma(0) Then Tseq\fl#[1]=param()
			If parse_comma(0) Then Tseq\in[0]=param()
		Case "event"
			parse_comma(1)
			Tseq\txt$[0]=param()
			If parse_comma(0) Then
				class$=param()
				Tseq\in[0]=parse_getclass(class$)
				parse_comma(1)
				Tseq\in[1]=param()
			EndIf
		Case "script"
			parse_comma(1)
			source$=params()
			txt$=0
			If Int(source$)<>0 Then
				For Tx.Tx=Each Tx
					If Tx\parent_class=Cclass_info Then
						If Tx\parent_id=Int(source$) Then
							txt$=Tx\value$
							Exit
						EndIf
					EndIf
				Next
			Else
				txt$=parse_loadscript$(source$)
			EndIf
			If txt$=0 Or txt$="" Then
				parse_error("txtsource",source$)
			EndIf
			Tseq\txt$[0]=txt$
		Case "hideplayer"
			Tseq\in[0]=1
			If parse_comma(1) Then
				Tseq\in[0]=param()
			EndIf
		
		Case "bar" If parse_comma(0) Then Tseq\in[0]=param()
		Case "hidebar"
		Case "showbar"
		
		Case "setcam"
			parse_comma(1):Tseq\in[0]=param()
		Case "movecam"
			parse_comma(1):Tseq\in[0]=param()
			parse_comma(1):Tseq\in[1]=param()
		Case "campath"
			Tseq\event$="movecam"
			parse_comma(1):steptime=param()
			Tseq\in[0]=t+steptime
			parse_comma(1):Tseq\in[1]=param()
			i=1
			While parse_comma(0)
				Tseq.Tseq=New Tseq
				Tseq\event$="movecam"
				Tseq\t=t+steptime*(i)
				i=i+1
				Tseq\in[0]=t+(steptime*i)
				Tseq\in[1]=param()
			Wend
		Case "timedcampath"
			Tseq\event$="movecam"
			parse_comma(1):steptime=Int(param())
			Tseq\in[0]=t+steptime
			parse_comma(1):Tseq\in[1]=param()
			i=1
			While parse_comma(0)
				Tseq.Tseq=New Tseq
				Tseq\event$="movecam"
				Tseq\t=Int(t+Int(steptime))
				steptime=Int(steptime)+Int(param())
				Tseq\in[0]=Int(t)+Int(steptime)
				parse_comma(1)
				Tseq\in[1]=param()
			Wend	
		Case "cammode"
			parse_comma(1)
			Tseq\in[0]=param()
			If parse_comma(0) Then
				class$=param()
				classint=parse_getclass(class$)
				If classint=-1 Then
					classint=p_env_class
					id=p_env_id
				Else
					parse_comma(1)
					id=param()
				EndIf	
				Tseq\in[1]=classint
				Tseq\in[2]=id
			EndIf
			
		Case "flash"
			Tseq\in[0]=255:Tseq\in[1]=255:Tseq\in[2]=255
			Tseq\fl#[0]=0.05
			Tseq\fl#[1]=1.1
			If parse_comma(0) Then Tseq\in[0]=param()
			If parse_comma(0) Then Tseq\in[1]=param()
			If parse_comma(0) Then Tseq\in[2]=param()
			If parse_comma(0) Then Tseq\fl#[0]=param()
			If parse_comma(0) Then Tseq\fl#[1]=param()
			
		Case "fade"
			parse_comma()
			Tseq\in[1]=255:Tseq\in[2]=255:Tseq\in[3]=255
			Tseq\in[4]=0
			Tseq\in[0]=param()
			If parse_comma(0) Then Tseq\in[1]=param()
			If parse_comma(0) Then Tseq\in[2]=param()
			If parse_comma(0) Then Tseq\in[3]=param()
			If parse_comma(0) Then Tseq\in[4]=param()
			
		Case "cls"
			parse_comma()
			Tseq\in[1]=0:Tseq\in[2]=0:Tseq\in[3]=0
			Tseq\in[0]=param()
			If parse_comma(0) Then Tseq\in[1]=param()
			If parse_comma(0) Then Tseq\in[2]=param()
			If parse_comma(0) Then Tseq\in[3]=param()
			
		Case "image"
			parse_comma()
			Tseq\in[0]=0
			Tseq\txt$[0]=param()
			If parse_comma(0) Then Tseq\in[0]=param()
			
		Case "itxt"
			parse_comma()
			Tseq\txt$[0]=param()
			parse_comma(1)
			Tseq\in[0]=param()
			parse_comma(1)
			Tseq\in[1]=param()
			If parse_comma(0) Then
				Tseq\in[2]=param()
			EndIf
			If parse_comma(0) Then
				Tseq\in[3]=param()
			EndIf
		Case "camfollow"
			parse_comma()
			class$=param()
			If class$<>"0" Then
				classint=parse_getclass(class$)
				If classint=-1 Then
					classint=p_env_class
					id=p_env_id
				Else
					parse_comma(1)
					id=param()
				EndIf	
				Tseq\in[0]=classint
				Tseq\in[1]=id
				parse_comma()
				Tseq\fl#[0]=param()
				parse_comma()
				Tseq\fl#[1]=param()
				parse_comma()
				Tseq\fl#[2]=param()
			Else
				Tseq\in[0]=0
			EndIf
		
		Default con_add("INTERNAL ERROR: invalid event '"+event$+"' for seq_addevent",Cbmpf_red)
			
	End Select
End Function