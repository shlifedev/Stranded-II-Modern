Function game_cd()
	parse_globalevent("changeday")

	For TCobject.Tobject=Each Tobject
		game_cd_object()
	Next
	For TCunit.Tunit=Each Tunit
		game_cd_unit()
	Next
	For TCitem.Titem=Each Titem
		game_cd_item()
	Next
	
	Select map_climate
		Case 0
			If Rand(1,100)<=map_rainratio Then
				e_environment_setweather(1)
			Else
				e_environment_setweather(0)
			EndIf
		Case 1
			If Rand(1,100)<=map_snowratio Then
				e_environment_setweather(2)
			Else
				e_environment_setweather(0)
			EndIf
		Case 2 e_environment_setweather(0)
		Case 3 e_environment_setweather(1)
		Case 4 e_environment_setweather(2)
		Case 5 e_environment_setweather(3)
	End Select
	
	For Tinfo.Tinfo=Each Tinfo
		If Tinfo\typ=45 Then
			info_spawncontrol(Tinfo)
		EndIf
	Next
End Function


Function game_cd_object()
	If TCobject\daytimer<0 Then
		If Dobject_growtime(TCobject\typ)>0 Then
			TCobject\daytimer=TCobject\daytimer+1
			grow_object(TCobject)
		EndIf
	Else
		If Dobject_spawn$(TCobject\typ)<>"" Then
			TCobject\daytimer=TCobject\daytimer+1
			split(Dobject_spawn$(TCobject\typ))
			s_item=Int(splits$(0))
			s_rate=Int(splits$(1))
			s_xzr#=Float(splits$(2))
			s_yr#=Float(splits$(3))
			s_yo#=Float(splits$(4))
			s_limit=Int(splits$(5))
			s_count=Int(splits$(6))
			If TCobject\daytimer>=s_rate Then
				TCobject\daytimer=0
				If countstored_items(Cclass_object,TCobject\id,s_item,1)<s_limit Then
					set_item(-1,s_item, 0,0,0 , s_count)		
					store_item(TCitem\id,Cclass_object,TCobject\id,Cpm_out, EntityX(TCobject\h) , EntityY(TCobject\h)+Rnd(-s_yr#,s_yr#)+s_yo# , EntityZ(TCobject\h))
					RotateEntity TCitem\h,0,Rand(360),0
					MoveEntity TCitem\h,0,0,Rnd((s_xzr#/2.5),s_xzr#)
					RotateEntity TCitem\h,-90,Rand(360),0
				EndIf
			EndIf
		EndIf
		If Dobject_healthchange(TCobject\typ)<>0 Then
			If Dobject_healthchange(TCobject\typ)<0 Then
				If get_state(Cstate_invulnerability,Cclass_object,TCobject\id)=0 Then
					TCobject\health#=TCobject\health#+Dobject_healthchange#(TCobject\typ)
					If TCobject\health#<=0. Then
						damage_object(0,1)
					EndIf
				EndIf
			Else
				TCobject\health#=TCobject\health#+Dobject_healthchange#(TCobject\typ)
				If TCobject\health#>TCobject\health_max# Then
					TCobject\health#=TCobject\health_max#
				EndIf
			EndIf
		EndIf
	EndIf
End Function


Function game_cd_unit()
	If TCunit\health#<=0. Then
		If TCunit\id>=100 Then
			free_unit(TCunit\id)
		EndIf
	Else
		health_unit(Dunit_healthchange#(TCunit\typ))
	EndIf
End Function


Function game_cd_item()
	If TCitem\parent_class=0 Then
		If Ditem_healthchange(TCitem\typ)<>0 Then
			If Ditem_healthchange(TCitem\typ)<0 Then
				If get_state(Cstate_invulnerability,Cclass_item,TCitem\id)=0 Then
					TCitem\health#=TCitem\health#+Ditem_healthchange#(TCitem\typ)
					If TCitem\health#<=0. Then
						free_item(TCitem\id)
					EndIf
				EndIf
			Else
				TCitem\health#=TCitem\health#+Ditem_healthchange#(TCitem\typ)
				If TCitem\health#>Ditem_health#(TCitem\typ) Then
					TCitem\health#=Ditem_health#(TCitem\typ)
				EndIf
			EndIf
		EndIf
	EndIf
End Function