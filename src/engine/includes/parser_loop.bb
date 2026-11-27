Function parse_loop()
	If p_loopmode=1 Then
		parse_error("alreadylooping")
	Else
		p_looptype=""
		p_loopargs=""
		parse_skipspace()
		If Mid(p_l$,p_p,1)<>"(" Then
			parse_error("expect","'('")
			Return 0
		Else
			p_p=p_p+1
			p_looptype$=param()
			Select p_looptype$
				Case "count"
					parse_comma(1)
					p_loopargs$=Abs(Int(param()))
				Case "objects","object",Cclass_object
					p_looptype$="objects"
					If parse_comma(0) Then p_loopargs$=Int(param())
				Case "units","unit",Class_unit
					p_looptype$="units"
					If parse_comma(0) Then p_loopargs$=Int(param())
				Case "items","item",Cclass_item
					p_looptype$="items"
					If parse_comma(0) Then p_loopargs$=Int(param())
				Case "infos","info",Cclass_info
					p_looptype$="infos"
					If parse_comma(0) Then p_loopargs$=Int(param())
				Case "states","state",Cclass_state
					p_looptype$="states"
				Default
					parse_error("looptype",p_looptype$)
					Return 0
			End Select
		EndIf
		parse_skipspace()
		If Mid(p_l$,p_p,1)=")" Then
			p_p=p_p+1
		Else
			parse_error("expecting","')'")
			Return 0
		EndIf
		p_loopmode=1
		Return 1
	EndIf
End Function


Function parse_loop_check()
	Select p_looptype$
		Case "count"
			p_loopi=p_loopi+1
			If p_loopi>Int(p_loopargs$) Then
				p_loopmode=0
				parse_closebrace()
			EndIf
		Case "objects"
			parse_loop_objects()
		Case "units"
			parse_loop_units()
		Case "items"
			parse_loop_items()
		Case "infos"
			parse_loop_infos()
		Case "states"
			If p_loopi=0 Then
				p_loop_state=First Tstate
			Else
				p_loop_state=After p_loop_state
			EndIf
			If p_loop_state=Null Then
				p_loopi=0
				p_loopmode=0
				parse_closebrace()
			Else
				p_loopi=p_loop_state\typ
			EndIf
		Default
			parse_error("Internal error. Unknown loop type '"+p_looptype$+"'")
	End Select
End Function


Function parse_loop_objects()
	Local args=Int(p_loopargs$)
	Local f=0
	If args=0 Then
		If p_loopi=0 Then
			p_loop_object=First Tobject
		Else
			p_loop_object=After p_loop_object
		EndIf
		If p_loop_object=Null Then
			p_loopi=0
			p_loopmode=0
			parse_closebrace()
		Else
			p_loopi=p_loop_object\id
		EndIf
	Else
		f=0
		For x.Tobject=Each Tobject
			If x\typ=args Then
				If x\id>p_loopi Then
					p_loopi=x\id
					f=1
					Exit
				EndIf 
			EndIf
		Next
		If f=0 Then
			p_loopi=0
			p_loopmode=0
			parse_closebrace()
		EndIf
	EndIf
End Function

Function parse_loop_units()
	Local args=Int(p_loopargs$)
	Local f=0
	If args=0 Then
		If p_loopi=0 Then
			p_loop_unit=First Tunit
		Else
			p_loop_unit=After p_loop_unit
		EndIf
		If p_loop_unit=Null Then
			p_loopi=0
			p_loopmode=0
			parse_closebrace()
		Else
			p_loopi=p_loop_unit\id
		EndIf
	Else
		f=0
		For x.Tunit=Each Tunit
			If x\typ=args Then
				If x\id>p_loopi Then
					p_loopi=x\id
					f=1
					Exit
				EndIf 
			EndIf
		Next
		If f=0 Then
			p_loopi=0
			p_loopmode=0
			parse_closebrace()
		EndIf
	EndIf
End Function

Function parse_loop_items()
	Local args=Int(p_loopargs$)
	Local f=0
	If args=0 Then
		If p_loopi=0 Then
			p_loop_item=First Titem
		Else
			p_loop_item=After p_loop_item
		EndIf
		If p_loop_item=Null Then
			p_loopi=0
			p_loopmode=0
			parse_closebrace()
		Else
			p_loopi=p_loop_item\id
		EndIf
	Else
		f=0
		For x.Titem=Each Titem
			If x\typ=args Then
				If x\id>p_loopi Then
					p_loopi=x\id
					f=1
					Exit
				EndIf 
			EndIf
		Next
		If f=0 Then
			p_loopi=0
			p_loopmode=0
			parse_closebrace()
		EndIf
	EndIf
End Function

Function parse_loop_infos()
	Local args=Int(p_loopargs$)
	Local f=0
	If args=0 Then
		If p_loopi=0 Then
			p_loop_info=First Tinfo
		Else
			p_loop_info=After p_loop_info
		EndIf
		If p_loop_info=Null Then
			p_loopi=0
			p_loopmode=0
			parse_closebrace()
		Else
			p_loopi=p_loop_info\id
		EndIf
	Else
		f=0
		For x.Tinfo=Each Tinfo
			If x\typ=args Then
				If x\id>p_loopi Then
					p_loopi=x\id
					f=1
					Exit
				EndIf 
			EndIf
		Next
		If f=0 Then
			p_loopi=0
			p_loopmode=0
			parse_closebrace()
		EndIf
	EndIf
End Function