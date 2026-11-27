Function set_skill(skill$,value,caption$="")
	For Tx.Tx=Each Tx
		If Tx\mode=5 Then
			If Tx\key$=skill$ Then
				Tx\value$=value
				If caption$<>"" Then Tx\stuff$=caption$
				Return 2
			EndIf
		EndIf
	Next
	Tx.Tx=New Tx
	Tx\mode=5
	Tx\key$=skill$
	Tx\value$=value
	Tx\stuff$=caption$
	Return 1
End Function


Function inc_skill(skill$,modify,caption$="")
	For Tx.Tx=Each Tx
		If Tx\mode=5 Then
			If Tx\key$=skill$ Then
				Tx\value$=Int(Tx\value$)+modify
				Tx\stuff$=caption$
				Return 1
			EndIf
		EndIf
	Next
	Tx.Tx=New Tx
	Tx\mode=5
	Tx\key$=skill$
	Tx\value$=modify
	Tx\stuff$=caption$
	Return 0
End Function


Function free_skill(skill$)
	For Tx.Tx=Each Tx
		If Tx\mode=5 Then
			If Tx\key$=skill$ Then
				Delete Tx
				Return 1
			EndIf
		EndIf
	Next
	Return 0	
End Function


Function got_skill(skill$)
	For Tx.Tx=Each Tx
		If Tx\mode=5 Then
			If Tx\key$=skill$ Then
				Return 1
			EndIf
		EndIf
	Next
	Return 0
End Function


Function skill_value(skill$)
	For Tx.Tx=Each Tx
		If Tx\mode=5 Then
			If Tx\key$=skill$ Then
				Return Int(Tx\value$)
			EndIf
		EndIf
	Next
	Return 0	
End Function

Function skill_name(skill$,name$)
	For Tx.Tx=Each Tx
		If Tx\mode=5 Then
			If Tx\key$=skill$ Then
				Tx\stuff$=name$
				Return 1
			EndIf
		EndIf
	Next
	Return 0	
End Function