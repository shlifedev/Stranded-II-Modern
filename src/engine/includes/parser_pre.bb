Function preparse()
	Local l,i,tot$,sub$,onend
	
	map_briefing_key$=preparse_string$(map_briefing$)
	
	For Tx.Tx=Each Tx
		If Tx\mode=0 Then
			Tx\key$=preparse_string$(Tx\value$)
		EndIf
	Next
	
	For Tinfo.Tinfo=Each Tinfo
		If Tinfo\typ=37 Then
			For Tx.Tx=Each Tx
				If Tx\mode=0 Then
					If Tx\parent_class=Cclass_info
						If Tx\parent_id=Tinfo\id Then
							Tx\key$=""
						EndIf
					EndIf
				EndIf
			Next
		EndIf
	Next
	
End Function


Function preparse_string$(txt$)
	Local l,i,s,sub$,onend,key$
	
	If Instr(txt$,"on:")>0 Then
		l=Len(txt$)
		For i=1 To l
			sub$=Mid(txt$,i,3)
			If sub$="on:" Then
				i=i+3
				onend=parse_end(txt$,i)
				sub$=Mid(txt$,i,onend-i)
				key$=key$+""+sub$
			EndIf
		Next
	EndIf
	
	Return key$
End Function