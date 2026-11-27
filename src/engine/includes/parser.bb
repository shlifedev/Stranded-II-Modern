Function parse(noskip=0)
	If m_section=Csection_editor
		If noskip=0 Then
			clear_parsecache()
			Return 0
		EndIf
	EndIf
	
	If p_parseinprogress=1 Then
		m$="Crossparsing Error"+Chr(13)
		m$=m$+"Crossparsing Event Data (triggerd by a Script)"+Chr(13)
		m$=m$+"Event: "+p_env_event$+" "+p_env_info$+Chr(13)
		m$=m$+"Class: "+p_env_class+Chr(13)
		m$=m$+"ID: "+p_env_id+Chr(13)
		m$=m$+"Avoid scripts which trigger events which need"+Chr(13)
		m$=m$+"to be parsed instantly."
		RuntimeError(m$)
	EndIf
	p_parseinprogress=1
	
	Local i
	Local comment
		
	p_error=0
	p_noerror=0
	
	p_bracelevel=0
	p_bracemode=0
	p_bracetype$=""
	p_bracepar$=""
	
	p_loopmode=0
	p_looprow=0
	p_loopcol=0
	p_looptype=""
	p_loopargs=""
	p_looplevel=0
	p_loopi=0
	
	p_m_ol=0
	p_m_or=0
	p_m_base=0
	p_m_value=0
	p_m_basec=0
	p_m_o$=""
	
	p_elseif=0
	p_varname$=""
	
	
	For Tpj.Tpj=Each Tpj
		Delete Tpj
	Next
	
	For Tplj.Tplj=Each Tplj
		Delete Tplj
	Next
	
	p_lines=0
	For Tpc.Tpc=Each Tpc
		p_lines=p_lines+1
	Next
	Dim Dpc$(p_lines-1)
	For Tpc.Tpc=Each Tpc
		Dpc$(i)=Tpc\txt$
		i=i+1
		Delete Tpc
	Next
	
	If p_trace_enabled Then parse_trace("event")
	p_pl=0
	While p_pl<(p_lines)
	
		p_l$=Dpc$(p_pl)
		p_len=Len(p_l$)
		p_p=1
		
		While p_p<=p_len 
			If parse_skipspace() Then
			
				twof$=Mid(p_l$,p_p,2)
				If comment=1 Then
					If twof$="*/" Then
						comment=0
						p_p=p_p+2
						parse_skipspace()
					Else
						If Instr(p_l$,"*/") Then
							p_p=p_p+1
						Else
							p_p=p_len+1
						EndIf
					EndIf
				Else
					If twof$="//" Then p_p=p_len+1
					If twof$="/*" Then comment=1
				EndIf
				
				If comment=0 Then
					If p_p<=p_len Then
					
					
						If parse_com() Then					
							If p_bracemode=0 Then
							
								Local ps,pe
								Select p_com$
									
									
									Case "$"
										Select var_parse$()
											Case "="
												var_set(p_varname$,param$())
											Case "+="
												var_modify$(p_varname$,param$())
											Case "++"
												var_modify$(p_varname$,1)
											Case "-="
												var_modify$(p_varname$,("-"+param$()))
											Case "--"
												var_modify$(p_varname$,-1)
										End Select
										parse_semi()
										
									Case "@" 
										If p_noerror>0 Then
											p_noerror=0
											parse_error("'@' sequences are not allowed. Max. one '@' per command")
										Else
											p_noerror=2
											p_p=p_p+1
										EndIf
																		
									Case "on"
										If Mid(p_l$,p_p,1)<>":" Then
											parse_error("expect","':'")
										Else
											ps=p_p+1
											pe=parse_end(p_l$,ps)
											p_bracepar$=Mid(p_l$,ps,pe-ps)
											p_bracemode=1
											p_bracetype$="on"
											p_p=pe
											parse_openbrace()
										EndIf
										If p_bracelevel<>0 Then
											parse_error("'on' must not be subordinated to conditions")
										EndIf
										
									Case "if"
										p_bracepar$=param$()
										p_bracemode=1
										p_bracetype$="if"
										parse_openbrace()
										
									Case "elseif"
										p_rootif=parse_getjpbool(p_bracelevel+1)
										If p_rootif=1 Then
											param$()
											p_bracepar$=0
										Else
											p_bracepar$=param$()
										EndIf
										p_bracemode=1
										p_bracetype$="elseif"
										parse_openbrace()
										
									Case "else"
										p_bracepar$=parse_getjpbool(p_bracelevel+1)
										p_bracemode=1
										p_bracetype$="else"
										parse_openbrace()
									
									Case "skip"
										p_parseinprogress=0
										Return 1
										
									Case "loop"
										parse_loop()
										p_bracemode=1
										p_bracetype$="loop"
										parse_openbrace()
										p_looprow=p_pl
										p_loopcol=p_p
										p_looplevel=p_bracelevel
										p_loopi=0
										
									Default parse_commands()
								
								End Select
								
							Else
								parse_error("expect","'{'")
							EndIf
							
							p_noerror=p_noerror-1
							
							
						Else
						
							sign=Asc(Mid(p_l$,p_p,1))
							
							If sign=123
								If p_bracemode=1 Then
									p_bracemode=0
									p_bracelevel=p_bracelevel+1
									parse_setjp(p_pl,p_p,p_bracelevel,p_bracetype$,p_bracepar$)
									p_p=p_p+1
									Select p_bracetype$
										Case "on"
											If p_env_event$<>p_bracepar$ Then
												parse_closebrace()
											EndIf
										Case "if"
											If Int(p_bracepar$)=0 Then parse_closebrace()
										Case "elseif"
											If Int(p_bracepar$)=0 Then parse_closebrace()
											If p_rootif=1 Then parse_freelastjp()
										Case "else"
											If Int(p_bracepar$)=1 Then parse_closebrace()
										Case "loop"
											parse_loop_check()
										Default
											parse_error("internal parsing error. unknwon brace type.")
									End Select
								Else
									parse_error("wrongbrace")
								EndIf
								
							ElseIf sign=125 Then
								p_bracelevel=p_bracelevel-1
								If p_bracelevel<0 Then parse_error("brackets")
								p_p=p_p+1
								If p_loopmode=1 Then
									If p_looplevel=p_bracelevel Then
										parse_setl(p_looprow)
										p_p=p_loopcol
										p_bracemode=1
										p_bracetype$="loop"
									EndIf
								EndIf
							
							Else
								parse_error("unexpected",Chr(sign))
								If p_bracemode=1 Then
									parse_error("Expecting '{' for "+p_bracetype)
								EndIf
							EndIf
							
						EndIf
						
						
						If p_error=1 Then
							p_parseinprogress=0
							Return 0
						EndIf
				
						
					EndIf
				EndIf
				
			EndIf
		Wend
		
		p_pl=p_pl+1
	Wend
	
	p_parseinprogress=0
	Return 1
End Function
