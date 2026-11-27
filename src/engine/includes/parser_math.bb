Function parse_math$(txt$)
	Local otxt$=txt$
	If Instr(txt$,Chr(34))=0 Then
		txt$=Replace(txt$," ","")
	Else
		txt$=trimexquote$(txt$)
	EndIf
	Local bro
	Local brc
	Local req=0
	Local s,i,l
	l=Len(txt$)
	For i=1 To l
		s=Asc(Mid(txt$,i,1))
		If (s<48 Or s>57) Then
			If s<>46 Then
				req=1
				Select s
					Case 40 bro=bro+1
					Case 41 brc=brc+1
				End Select
			EndIf
		EndIf
	Next
	If req=0 Then Return txt$
	If bro<>brc Then parse_error("brackets"):Return 0
	If bro=0 Then
		Return parse_math_st(txt$)
	Else
		l=Len(txt$)
		combracket=0
		comstart=0
		For i=1 To l
			s=Asc(Mid(txt$,i,1))
			If s=40 Then
				If i>1 Then
					If parse_math_op(Mid(txt$,1,i-1),2)=0 Then
						combracket=1
						parse_math_getcom(Mid(txt$,1,i-1))
						If p_com$<>"" Then
							comstart=i-Len(p_com$)
							p_internaltxt$=Mid(txt$,i+1,-1)
							parse_commands(0,1)
						Else
							combracket=0
						EndIf
					EndIf
				EndIf
			ElseIf s=41 Then
				If combracket=1 Then
					combracket=0
					If comstart>1 Then
						txtleft$=Left(txt$,comstart-1)
					Else
						txtleft$=""
					EndIf
					If i<l Then
						txtright$=Mid(txt$,i+1,-1)
					Else
						txtright$=""
					EndIf
					txt$=txtleft$+p_return$+txtright$
					l=Len(txt$)
					i=0
				EndIf
			EndIf
		Next
		Local depth,maxdepth
		While Instr(txt$,"(")>0 
			l=Len(txt$)
			depth=0
			maxdepth=0
			For i=1 To l
				s=Asc(Mid(txt$,i,1))
				If s=40 Or s=41 Then
					If s=40 Then
						depth=depth+1
						If depth>maxdepth Then maxdepth=depth
					Else
						depth=depth-1
					EndIf
				EndIf
			Next
			depth=0
			Local brs
			Local bre
			For i=1 To l
				s=Asc(Mid(txt$,i,1))
				If s=40 Or s=41 Then
					If s=40 Then
						depth=depth+1
						If depth=maxdepth Then brs=i+1
					Else
						If depth=maxdepth Then bre=i-1:Exit
						depth=depth-1
					EndIf
				EndIf
			Next
			Local deepc$=Mid(txt$,brs,(bre+1)-brs)
			Local deepv$=parse_math_st(deepc$)
			If brs-1=1 Then
				txt$=Mid(txt$,Len(deepc$)+3,-1)
				txt$=Str(deepv)+txt$
			Else
				txt$=Replace(txt$,Mid(txt$,brs-2,1)+"("+deepc$+")",Mid(txt$,brs-2,1)+Str(deepv$))
			EndIf
		Wend
		Return parse_math_st(txt$)
	EndIf
End Function






Function parse_math_st$(txt$)
	Local l=Len(txt$)
	Local i
	
	For i=1 To l
		If i=1 Then
			If Mid(txt$,1,1)="-" Then i=2
		EndIf
		If parse_math_op(Mid(txt$,i,3))>0 Or i=l Then
			If i=l Then
				p_m_base=Mid(txt$,1,i)
				Return p_m_base
			Else
				p_m_base=Mid(txt$,1,i-1)
				Exit
			EndIf
		EndIf
	Next
	
	If i=1 Then
		If txt$<>"" Then
			parse_error("bwo",txt$)
			Return 0
		Else
			Return 0
		EndIf
	EndIf
		
	For Tpm.Tpm=Each Tpm
		Delete Tpm
	Next
	Local pos
	Local op
	Local ns,ne
	Local number$
	Local pmt$
	For i=(i-1) To l
		op=parse_math_op(Mid(txt$,i,3))
		If op>0 Or i=l Then
			If ns=0 Then
				ns=i
				If i+Len(p_m_o$)<l Then
					mot$=p_m_o$
					If parse_math_op(Mid(txt$,i+Len(p_m_o$),3))>0 Then
						i=i+Len(mot$)
						If p_m_o$<>"-" Then
							parse_error("operators",txt$)
						EndIf
					EndIf
				EndIf
			Else
				ne=i-1
				If i=l Then ne=l
				parse_math_op(Mid(txt$,ns,3))
				ns=ns+Len(p_m_o$)
				number$=Mid(txt$,ns,(ne+1)-(ns))
				pos=pos+1
				Tpm.Tpm=New Tpm
				Tpm\typ=var_type(number$)
				Tpm\pos=pos
				Tpm\txt$=p_m_o$
				Tpm\value$=number$
				ns=0
				If i=l Then Exit
			EndIf
		EndIf
	Next
	
	p_m_value=p_m_base
	Local valuetype
	Local prev$
	Local prevtype
	
	For Tpm.Tpm=Each Tpm
		Select Tpm\txt$
			Case "*"
				prev=parse_math_prev(Tpm\pos)
				prevtype=var_type(prev)
				valuetype=var_type(Tpm\value)
				ovaluetype=var_type(p_m_value)
				If p_m_basec=0 Then
					If prevtype+valuetype+ovaluetype=0 Then
						p_m_value=Int(p_m_value)+(Int(prev)*Int(Tpm\value))
					Else
						p_m_value=Float(p_m_value)+(Float(prev)*Float(Tpm\value))
					EndIf
				Else
					If prevtype+valuetype+ovaluetype=0 Then
						p_m_value=(Int(prev)*Int(Tpm\value))
					Else
						p_m_value=(Float(prev)*Float(Tpm\value))
					EndIf
				EndIf
				Delete Tpm
			Case "/"
				prev=parse_math_prev(Tpm\pos)
				prevtype=var_type(prev)
				valuetype=var_type(Tpm\value)
				ovaluetype=var_type(p_m_value)
				If Tpm\value=0 Then parse_error("zerodiv","'"+prev+"/"+Tpm\value+"'")
				If p_m_basec=0 Then
					If prevtype+valuetype+ovaluetype=0 Then
						p_m_value=Int(p_m_value)+(Int(prev)/Int(Tpm\value))
					Else
						p_m_value=Float(p_m_value)+(Float(prev)/Float(Tpm\value))
					EndIf
				Else
					If prevtype+valuetype+ovaluetype=0 Then
						p_m_value=(Int(prev)/Int(Tpm\value))
					Else
						p_m_value=(Float(prev)/Float(Tpm\value))
					EndIf
				EndIf
				Delete Tpm
		End Select
	Next
	
	For Tpm.Tpm=Each Tpm
		Select Tpm\txt$
			Case "+"
				valuetype=var_type(Tpm\value)
				ovaluetype=var_type(p_m_value)
				If valuetype+ovaluetype=0 Then
					p_m_value=Int(p_m_value)+Int(Tpm\value)
				Else
					If valuetype=2 Or ovaluetype=2 Then
						p_m_value=p_m_value+Tpm\value
					Else
						p_m_value=Float(p_m_value)+Float(Tpm\value)
					EndIf
				EndIf
				Delete Tpm
			Case "-"
				valuetype=var_type(Tpm\value)
				ovaluetype=var_type(p_m_value)
				If valuetype+ovaluetype=0 Then
					p_m_value=Int(p_m_value)-Int(Tpm\value)
				Else
					p_m_value=Float(p_m_value)-Float(Tpm\value)
				EndIf
				Delete Tpm
		End Select
	Next
	
	For Tpm.Tpm=Each Tpm
		Select Tpm\txt$
			Case "=="
				If p_m_value=Tpm\value Then
					p_m_value=1
				Else
					valuetype=var_type(Tpm\value)
					ovaluetype=var_type(p_m_value)
					If valuetype=1 Or ovaluetype=1 Then
						If Float(p_m_value)=Float(Tpm\value) Then
							p_m_value=1
						Else
							p_m_value=0
						EndIf
					Else
						p_m_value=0
					EndIf
				EndIf
				Delete Tpm
			Case "!="
				valuetype=var_type(Tpm\value)
				ovaluetype=var_type(p_m_value)
				If valuetype+ovaluetype=0 Then
					If Int(p_m_value)<>Int(Tpm\value) Then
						p_m_value=1
					Else
						p_m_value=0
					EndIf					
				ElseIf valuetype=1 Or ovaluetype=1 Then
					If Float(p_m_value)<>Float(Tpm\value) Then
						p_m_value=1
					Else
						p_m_value=0
					EndIf
				Else
					If p_m_value<>Tpm\value Then
						p_m_value=1
					Else
						p_m_value=0
					EndIf					
				EndIf
				Delete Tpm
			Case ">"
				valuetype=var_type(Tpm\value)
				ovaluetype=var_type(p_m_value)
				If valuetype+ovaluetype=0 Then
					If Int(p_m_value)>Int(Tpm\value) Then
						p_m_value=1
					Else
						p_m_value=0
					EndIf
				Else
					If Float(p_m_value)>Float(Tpm\value) Then
						p_m_value=1
					Else
						p_m_value=0
					EndIf					
				EndIf
				Delete Tpm
			Case "<"
				valuetype=var_type(Tpm\value)
				ovaluetype=var_type(p_m_value)
				If valuetype+ovaluetype=0 Then
					If Int(p_m_value)<Int(Tpm\value) Then
						p_m_value=1
					Else
						p_m_value=0
					EndIf
				Else
					If Float(p_m_value)<Float(Tpm\value) Then
						p_m_value=1
					Else
						p_m_value=0
					EndIf					
				EndIf
				Delete Tpm
			Case ">=","=>"
				valuetype=var_type(Tpm\value)
				ovaluetype=var_type(p_m_value)
				If valuetype+ovaluetype=0 Then
					If Int(p_m_value)>=Int(Tpm\value) Then
						p_m_value=1
					Else
						p_m_value=0
					EndIf
				Else
					If Float(p_m_value)>=Float(Tpm\value) Then
						p_m_value=1
					Else
						p_m_value=0
					EndIf					
				EndIf
				Delete Tpm
			Case "<=","=<"
				valuetype=var_type(Tpm\value)
				ovaluetype=var_type(p_m_value)
				If valuetype+ovaluetype=0 Then
					If Int(p_m_value)<=Int(Tpm\value) Then
						p_m_value=1
					Else
						p_m_value=0
					EndIf
				Else
					If Float(p_m_value)<=Float(Tpm\value) Then
						p_m_value=1
					Else
						p_m_value=0
					EndIf					
				EndIf
				Delete Tpm
			Case "&&","and"
				If (Int(p_m_value)<>0) And (Int(Tpm\value)<>0) Then
					p_m_value=1
				Else
					p_m_value=0
				EndIf
				Delete Tpm
			Case "||","or"
				If (Int(p_m_value)<>0) Or (Int(Tpm\value)<>0) Then
					p_m_value=1
				Else
					p_m_value=0
				EndIf
				Delete Tpm
			Case "xor"
				If (Int(p_m_value)<>0) Xor (Int(Tpm\value)<>0) Then
					p_m_value=1
				Else
					p_m_value=0
				EndIf
				Delete Tpm	
			Default
				parse_error("'"+Tpm\txt$+"' is no allowed mathematical or logical operator of SII script")
		End Select
	Next
	
	Return p_m_value
End Function


Function parse_math_op(txt$,dir=1)
	Local sub$
	If dir=1 Then
		sub$=Left(txt$,2)
	Else
		sub$=Right(txt$,2)
	EndIf
	p_m_o$=sub$
	Select sub$
		Case "==" Return 5
		Case ">=" Return 5
		Case "<=" Return 5
		Case "=>" Return 5
		Case "=<" Return 5
		Case "!=" Return 5
		Case "&&" Return 5
		Case "||" Return 5
		Case "or" Return 5
	End Select
	If dir=1 Then
		sub$=Left(txt$,1)
	Else
		sub$=Right(txt$,1)
	EndIf
	p_m_o$=sub$
	Select sub$
		Case "+" Return 1
		Case "-" Return 2
		Case "*" Return 3
		Case "/" Return 4
		Case ">" Return 5
		Case "<" Return 5
	End Select
	If dir=1 Then
		sub$=Left(txt$,3)
	Else
		sub$=Right(txt$,3)
	EndIf
	p_m_o$=sub$
	Select sub$
		Case "and" Return 5
		Case "xor" Return 5
	End Select
	p_m_o$=""
	Return 0
End Function

Function parse_math_prev$(pos)
	Local temp$
	Local tpos=pos-1
	For Tpm.Tpm=Each Tpm
		If Tpm\typ=0 Then
			If Tpm\pos=tpos Then
				p_m_basec=0
				temp=Tpm\value
				Delete Tpm
				Return temp
			EndIf
		EndIf
	Next
	p_m_basec=1
	Return p_m_value
End Function

Function parse_math_getcom(txt$)
	If txt$="(" Then p_com$="":Return 0
	Local l=Len(txt$)
	Local s
	p_com$=""
	For i=l To 1 Step -1
		s=Asc(Mid(txt$,i,1))
		If (s<97 Or s>122) And s<>95 Then
			p_com$=Mid(txt$,i+1,-1)
			Return 1
		EndIf
	Next
	p_com$=txt$
	Return 0
End Function