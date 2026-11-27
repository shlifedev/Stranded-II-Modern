Function parse_openbrace()
	Local ps=p_p
	For row=p_pl To p_lines-1
		l=Len(Dpc$(row))
		For col=ps To l
			
			txt$=Mid(Dpc$(row),col,1)
			
			If txt$<>" " Then
				If txt$="{" Then
					parse_setl(row)
					p_p=col
					Return 1
				Else
					Return 0
				EndIf
			EndIf
		
		Next
		ps=1
	Next
	parse_error("expect","opening brace '{'")
	Return 0
End Function


Function parse_closebrace(equalcount=1)
	Local instring=0
	Local comment=0
	Local brace=1
	Local ps=p_p
	
	For row=p_pl To p_lines-1
		l=Len(Dpc$(row))
		For col=ps To l
			
			txt$=Mid(Dpc$(row),col,1)
			
			If txt$="/" Or txt$="*" Then
				If Mid(Dpc$(row),col,2)="//" Then Exit
				If comment=1 Then
					If Mid(Dpc$(row),col,2)="*/" Then comment=0
				Else
					If Mid(Dpc$(row),col,2)="/*" Then comment=1
				EndIf
			
			ElseIf txt$=Chr(34) Then
				instring=1-instring
			EndIf
			
			If comment=0 Then
				If instring=0 Then
					If txt$="{" Then
						brace=brace+1
					ElseIf txt$="}" Then
						brace=brace-1
						If brace<=0 Then
							If equalcount=1 And brace<0 Then
								Return 0
							Else
								parse_setl(row)
								p_p=col
								Return 1
							EndIf
						EndIf
					EndIf
				EndIf
			EndIf
		
		Next
		ps=1
	Next
	Return 0	
End Function