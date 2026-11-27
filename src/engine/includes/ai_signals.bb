Function ai_signal(class,id,mode,range=300)
	Local oldunit.Tunit=TCunit
	Local target=parent_h(class,id)
	If target<>0 Then
		For Tunit.Tunit=Each Tunit
			If Tunit\health>0 Then
				If EntityDistance(Tunit\h,target)<range Then
					TCunit.Tunit=Tunit
					
					TCunit\ai_target_class=class
					TCunit\ai_target_id=id
					
					Select mode
						Case ai_food
							If TCunit\ai_mode<>ai_getfood Then
								ai_mode(ai_getfood,20000)
							EndIf
						Case ai_attract
							If TCunit\ai_mode<>ai_movetarget Then
								ai_mode(ai_movetarget,20000)
							EndIf
						Case ai_distract
							If TCunit\ai_mode<>ai_flee Then
								ai_mode(ai_flee,20000)
							EndIf
					End Select
				
				EndIf
			EndIf
		Next
	EndIf
	TCunit.Tunit=oldunit
End Function


Function ai_typesignal(class,id,mode,typ,range=300)
	Local oldunit.Tunit=TCunit
	Local target=parent_h(class,id)
	If target<>0 Then
		For Tunit.Tunit=Each Tunit
			If Tunit\health>0 Then
				If Tunit\typ=typ Then
					If EntityDistance(Tunit\h,target)<range Then
						TCunit.Tunit=Tunit
						
						TCunit\ai_target_class=class
						TCunit\ai_target_id=id
						
						Select mode
							Case ai_food
								If TCunit\ai_mode<>ai_getfood Then
									ai_mode(ai_getfood,10000)
								EndIf
							Case ai_attract
								If TCunit\ai_mode<>ai_movetarget Then
									ai_mode(ai_movetarget,10000)
								EndIf
							Case ai_distract
								If TCunit\ai_mode<>ai_flee Then
									ai_mode(ai_flee,10000)
								EndIf
						End Select
					
					EndIf
				EndIf
			EndIf
		Next
	EndIf
	TCunit.Tunit=oldunit
End Function


Function ai_behavioursignal(class,id,mode,typ,range=300)
	Local oldunit.Tunit=TCunit
	Local target=parent_h(class,id)
	If target<>0 Then
		For Tunit.Tunit=Each Tunit
			If Tunit\health>0 Then
				If Dunit_behaviour(Tunit\typ)=typ Then
					If EntityDistance(Tunit\h,target)<range Then
						TCunit.Tunit=Tunit
						
						TCunit\ai_target_class=class
						TCunit\ai_target_id=id
						
						Select mode
							Case ai_food
								If TCunit\ai_mode<>ai_getfood Then
									ai_mode(ai_getfood,10000)
								EndIf
							Case ai_attract
								If TCunit\ai_mode<>ai_movetarget Then
									ai_mode(ai_movetarget,10000)
								EndIf
							Case ai_distract
								If TCunit\ai_mode<>ai_flee Then
									ai_mode(ai_flee,10000)
								EndIf
						End Select
						
						TCunit\ai_duration=10000
						TCunit\ai_timer=gt
						
					EndIf
				EndIf
			EndIf
		Next
	EndIf
	TCunit.Tunit=oldunit
End Function

Function ai_unitmode(unitid,mode,targetclass=0,targetid=0)
	If con_unit(unitid) Then
		ai_mode(mode)
		TCunit\ai_target_class=targetclass
		TCunit\ai_target_id=targetid
	EndIf
End Function