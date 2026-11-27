Function udp_ip(ip$)
	If Lower(ip$)="local" Then
		ip$="127.0.0.1"
	ElseIf Instr(Lower(ip$),"://") Or Instr(Lower(ip$),"www") Then
		If Instr(Lower(ip$),"://") Then
			sep=Instr(ip$,":")
			prot$=Left(ip$,sep-1)
			prot$=Lower(prot$)
			Select prot$
				Case "http" port=80
				Case "https" port=443
				Case "ftp" port=21
				Case "ssh" port=22
				Case "irc" port=6667
				Default port=80
			End Select
		Else
			port=80
		EndIf
		Local tcps=OpenTCPStream(ip$,port)
		If tcps<>0 Then
			ip$=TCPStreamIP(tcps)
			ip$=DottedIP(ip$)
			CloseTCPStream(tcps)
		EndIf
	EndIf
	If ip$="" Then ip$="127.0.0.1"
	a1=Instr(ip$,".")
	If a1=0 Then Return 0
	a2=Instr(ip$,".",a1+1)
	If a2=0 Then Return 0
	a3=Instr(ip$,".",a2+1)
	If a3=0 Then Return 0
	a1=Int(Left(ip$,Instr(ip$,".")-1)):ip$=Right(ip$,Len(ip$)-Instr(ip$,"."))
	a2=Int(Left(ip$,Instr(ip$,".")-1)):ip$=Right(ip$,Len(ip$)-Instr(ip$,"."))
	a3=Int(Left(ip$,Instr(ip$,".")-1)):ip$=Right(ip$,Len(ip$)-Instr(ip$,"."))
	a4=Int(ip$)
	Return (a1 Shl 24)+(a2 Shl 16)+(a3 Shl 8)+a4
End Function


Function udp_int(ip$)
	a1=Int(Left(ip$,Instr(ip$,".")-1)):ip$=Right(ip$,Len(ip$)-Instr(ip$,"."))
	a2=Int(Left(ip$,Instr(ip$,".")-1)):ip$=Right(ip$,Len(ip$)-Instr(ip$,"."))
	a3=Int(Left(ip$,Instr(ip$,".")-1)):ip$=Right(ip$,Len(ip$)-Instr(ip$,"."))
	a4=Int(ip$)
	Return (a1 Shl 24)+(a2 Shl 16)+(a3 Shl 8)+a4
End Function


Function udp_getip()
	tcps=OpenTCPStream("usgn.unrealsoftware.de",80)
	If tcps<>0 Then
		WriteLine tcps,"GET http://usgn.unrealsoftware.de/getip.php"
		in$=ReadLine(tcps)
		udp_ip=udp_ip(in$)
		CloseTCPStream(tcps)
		Return 1
	EndIf
	Return 0
End Function


Function udp_clear()
	If udp<>0 Then
		CloseUDPStream(udp)
		udp=0
	EndIf
	udp_port=0
	udp_ip=0
	For Tudp_con.Tudp_con=Each Tudp_con
		Delete Tudp_con
	Next
	For Tudp_ipq.Tudp_ipq=Each Tudp_ipq
		FreeBank Tudp_ipq\db
		Delete Tudp_ipq
	Next
	For Tudp_spq.Tudp_spq=Each Tudp_spq
		FreeBank Tudp_spq\db
		Delete Tudp_spq
	Next
	For Tudp_msg.Tudp_msg=Each Tudp_msg
		FreeBank Tudp_msg\db
		Delete Tudp_msg
	Next
	For Tudp_cest.Tudp_cest=Each Tudp_cest
		Delete Tudp_cest
	Next
	For Tudp_resend.Tudp_resend=Each Tudp_resend
		FreeBank Tudp_resend\db
		Delete Tudp_resend
	Next
End Function


Function udp_setup(port=0)
	udp_clear()
	udp=CreateUDPStream(port)
	If udp=0 Then Return 0
	udp_port=UDPStreamPort(udp)
End Function


Function udp_join(ip,port,timeout=10000,rate=500)
	lastsend=0
	start=MilliSecs()
	While MilliSecs()-start<timeout
		
		While RecvUDPMsg(udp)
			avail=ReadAvail(udp)
			If avail=1 Then
				fip=UDPMsgIP(udp)
				fport=UDPMsgPort(udp)
				id=ReadByte(udp)
				Select id
					Case Cudp_cest_ack,Cudp_cest_exists
						For Tudp_con.Tudp_con=Each Tudp_con
							If Tudp_con\ip=fip Then
								If Tudp_con\port=fport Then
									Delete Tudp_con
								EndIf
							EndIf
						Next
						Tudp_con.Tudp_con=New Tudp_con
						udp_con_setup(Tudp_con,fip,fport)
						udp_hostip=fip
						udp_hostport=fport
						Return 1
					Default Return id
				End Select
			EndIf
		Wend
			
		If MilliSecs()-lastsend>rate Then
			WriteByte udp,Cudp_cest_req
			SendUDPMsg udp,ip,port
			lastsend=MilliSecs()
		EndIf
	
	Wend
	Return Cudp_cest_timeout
End Function


Function udp_con_setup(con.Tudp_con,ip,port)
	con\ip=ip
	con\port=port
	con\iid=2
	con\sid=0
	con\ack=MilliSecs()
	con\lastaction=MilliSecs()
	con\resend=0
End Function


Function udp_con_delete(con.Tudp_con)
	Delete con
End Function


Function udp_update()
	udp_ms=MilliSecs()
	
	udp_process_queued()
	
	udp_get()
	
	For Tudp_msg.Tudp_msg=Each Tudp_msg
		packetexists=0
		For Tudp_spq.Tudp_spq=Each Tudp_spq
			If Tudp_spq\sended=0 Then
				If Tudp_spq\ip=Tudp_msg\ip Then
					If Tudp_spq\port=Tudp_msg\port Then
						packetexists=1
						size=BankSize(Tudp_msg\db)
						oldsize=BankSize(Tudp_spq\db)
						If Tudp_msg\system=1 Then
							ResizeBank Tudp_spq\db,oldsize+size
							CopyBank Tudp_spq\db,0,Tudp_spq\db,size,oldsize
							CopyBank Tudp_msg\db,0,Tudp_spq\db,0,size
						Else
							ResizeBank Tudp_spq\db,oldsize+size
							CopyBank Tudp_msg\db,0,Tudp_spq\db,oldsize,size
						EndIf
						If Tudp_msg\reliable=1 Then Tudp_spq\reliable=1
					EndIf
				EndIf
			EndIf
		Next
		If packetexists=0 Then
			Tudp_spq.Tudp_spq=New Tudp_spq
			Tudp_spq\ip=Tudp_msg\ip
			Tudp_spq\port=Tudp_msg\port
			size=BankSize(Tudp_msg\db)
			Tudp_spq\db=CreateBank(size)
			CopyBank Tudp_msg\db,0,Tudp_spq\db,0,size
			Tudp_spq\reliable=Tudp_msg\reliable
			Tudp_spq\time=udp_ms
		EndIf
		FreeBank Tudp_msg\db
		Delete Tudp_msg
	Next
	
	If udp_ms-udp_sendtimer>=udp_sendrate Then
		udp_sendtimer=udp_ms
		For Tudp_spq.Tudp_spq=Each Tudp_spq
			If Tudp_spq\sended=0 Then
				foundcon=0
				For Tudp_con.Tudp_con=Each Tudp_con
					If Tudp_con\ip=Tudp_spq\ip Then
						If Tudp_con\port=Tudp_spq\port Then
							foundcon=1
							Exit
						EndIf
					EndIf
				Next
				If foundcon=0 Then
					Tudp_con.Tudp_con=New Tudp_con
					udp_con_setup(Tudp_con,Tudp_spq\ip,Tudp_spq\port)
				EndIf
				reliableid=0
				If Tudp_spq\reliable=1 Then
					Tudp_con\sid=Tudp_con\sid+2
					If Tudp_con\sid>Cudp_win_limit Then
						Tudp_con\sid=0
					EndIf
					reliableid=Tudp_con\sid
				Else
					reliableid=Tudp_con\sid+1
				EndIf
				WriteShort udp,reliableid
				size=BankSize(Tudp_spq\db)
				For i=0 To size-1
					If Cudp_encr=0 Then
						WriteByte udp,PeekByte(Tudp_spq\db,i)
					Else
						byte=PeekByte(Tudp_spq\db,i)
						byte=byte Xor Cudp_encrk
						WriteByte udp,byte
					EndIf
				Next
				SendUDPMsg udp,Tudp_con\ip,Tudp_con\port
				Tudp_spq\sended=1
				Tudp_spq\id=Tudp_con\sid
				If Tudp_spq\reliable=0 Then
					FreeBank Tudp_spq\db
					Delete Tudp_spq
				EndIf
			EndIf
		Next
		
		For Tudp_cest.Tudp_cest=Each Tudp_cest
			WriteByte udp,Tudp_cest\msg
			SendUDPMsg udp,Tudp_cest\ip,Tudp_cest\port
			Delete Tudp_cest
		Next
		
		For Tudp_resend.Tudp_resend=Each Tudp_resend
			WriteShort udp,Tudp_resend\id
			size=BankSize(Tudp_resend\db)
			For i=0 To size-1
				If Cudp_encr=0 Then
					WriteByte udp,PeekByte(Tudp_resend\db,i)
				Else
					byte=PeekByte(Tudp_resend\db,i)
					byte=byte Xor Cudp_encrk
					WriteByte udp,byte
				EndIf
			Next
			SendUDPMsg udp,Tudp_resend\ip,Tudp_resend\port
			FreeBank Tudp_resend\db
			Delete Tudp_resend
		Next
	EndIf
	
End Function


Function udp_get()
	While RecvUDPMsg(udp)
		avail=ReadAvail(udp)
		If avail>0 Then
			
			fip=UDPMsgIP(udp)
			fport=UDPMsgPort(udp)
			
			If avail=1 Then
				
				id=ReadByte(udp)
				
				Select id
					
					Case Cudp_cest_req
						If udp_host=1
							foundcon=0
							For Tudp_con.Tudp_con=Each Tudp_con
								If Tudp_con\ip=fip Then
									If Tudp_con\port=fport Then
										foundcon=1
										Exit
									EndIf
								EndIf
							Next
							Tudp_cest.Tudp_cest=New Tudp_cest
							Tudp_cest\ip=fip
							Tudp_cest\port=fport
							If foundcon=1 Then
								Tudp_cest\msg=Cudp_cest_exists
							Else
								Tudp_cest\msg=Cudp_cest_ack
								Tudp_con.Tudp_con=New Tudp_con
								udp_con_setup(Tudp_con,fip,fport)
							EndIf
						Else
							Tudp_cest.Tudp_cest=New Tudp_cest
							Tudp_cest\ip=fip
							Tudp_cest\port=fport
							Tudp_cest\msg=Cudp_cest_nohost
						EndIf
						
					Case 100
						If udp_host=1 Then
							WriteByte udp,101
							WriteString udp,"Bla"
							WriteString udp,"mp_sawmp"
							WriteByte udp,1
							WriteByte udp,4
							WriteByte udp,0
							SendUDPMsg udp,fip,fport
						EndIf
						
					
					Default
				
				End Select
			
			
			ElseIf avail>2 Then
				
				foundcon=0
				For Tudp_con.Tudp_con=Each Tudp_con
					If Tudp_con\ip=fip Then
						If Tudp_con\port=fport Then
							
							Tudp_con\lastaction=udp_ms
							
							id=ReadShort(udp)
							
							foundpacket=0
							For Tudp_ipq.Tudp_ipq=Each Tudp_ipq
								If Tudp_ipq\ip=fip Then
									If Tudp_ipq\port=fport Then
										If Tudp_ipq\id=id Then
											foundpacket=1
											Exit
										EndIf
									EndIf
								EndIf
							Next
							If foundpacket=0 Then
								Tudp_ipq.Tudp_ipq=New Tudp_ipq
								Tudp_ipq\ip=fip
								Tudp_ipq\port=fport
								Tudp_ipq\id=id
								Tudp_ipq\time=udp_ms
								If id Mod 2=0 Then Tudp_ipq\reliable=1								
								inbyte=ReadByte(udp)
								If Cudp_encr=1 Then inbyte=inbyte Xor Cudp_encrk
								If inbyte>=Cudp_sys Then Tudp_ipq\system=1
								Tudp_ipq\db=CreateBank(avail-2)
								PokeByte(Tudp_ipq\db,0,inbyte)
								For i=1 To (avail-3)
									inbyte=ReadByte(udp)
									If Cudp_encr=0 Then
										PokeByte(Tudp_ipq\db,i,inbyte)
									Else
										inbyte=inbyte Xor Cudp_encrk
										PokeByte(Tudp_ipq\db,i,inbyte)
									EndIf
								Next
								If Tudp_ipq\system=1 Then
									udp_system(Tudp_ipq)
								EndIf	
							EndIf	
							
							foundcon=1
							Exit
						EndIf
					EndIf
				Next
				
				If foundcon=0 Then
					While ReadAvail(udp)>0
						ReadByte(udp)
					Wend
				EndIf
				
				
			Else
			
				While ReadAvail(udp)>0
					ReadByte(udp)
				Wend
			
			EndIf
		
		EndIf
	Wend
End Function


Function udp_system(in.Tudp_ipq)
	size=BankSize(in\db)
	For i=0 To size-1
		msgid=PeekByte(in\db,i)
		If msgid>=Cudp_sys Then
			Select msgid
				Case Cudp_sys_ping
					
				Case Cudp_sys_req
					id=PeekShort(in\db,i+1)
					i=i+2
					
					lowest=10000
					highest=0
					
					For Tudp_spq.Tudp_spq=Each Tudp_spq
						If Tudp_spq\ip=in\ip Then
							If Tudp_spq\port=in\port Then
								If Tudp_spq\sended=1 Then
									If udp_windowing(id-1,Tudp_spq\id)=0 Then
										
										If Tudp_spq\id<lowest Then lowest=Tudp_spq\id
										If Tudp_spq\id>highest Then highest=Tudp_spq\id
										
										DebugLog "discard "+Tudp_spq\id
										
										FreeBank Tudp_spq\db
										Delete Tudp_spq
										
									ElseIf Tudp_spq\id=id Then
										Tudp_resend.Tudp_resend=New Tudp_resend
										Tudp_resend\ip=Tudp_spq\ip
										Tudp_resend\port=Tudp_spq\port
										Tudp_resend\id=id
										size=BankSize(Tudp_spq\db)
										Tudp_resend\db=CreateBank(size)
										CopyBank Tudp_spq\db,0,Tudp_resend\db,0,size
									EndIf
								EndIf
							EndIf
						EndIf
					Next
					
					lastsys$="!!! REQ "+id+" -> discard "+lowest+"-"+highest
					
				Case Cudp_sys_ack
					id=PeekShort(in\db,i+1)
					i=i+2
					
					lowest=10000
					highest=0
					
					For Tudp_spq.Tudp_spq=Each Tudp_spq
						If Tudp_spq\ip=in\ip Then
							If Tudp_spq\port=in\port Then
								If Tudp_spq\sended=1 Then
									If udp_windowing(id,Tudp_spq\id)=0 Then
										
										If Tudp_spq\id<lowest Then lowest=Tudp_spq\id
										If Tudp_spq\id>highest Then highest=Tudp_spq\id
										
										DebugLog "discard "+Tudp_spq\id
										
										FreeBank Tudp_spq\db
										Delete Tudp_spq
										
									EndIf
								EndIf
							EndIf
						EndIf
					Next
					
					lastsys$="ACK "+id+" -> discard "+lowest+"-"+highest
					
				Default
			End Select
		Else
			i=i
			Exit
		EndIf
	Next
	If i>=size-1 Then
		FreeBank in\db
		Delete in
	Else
		CopyBank in\db,i,in\db,0,size-i
		ResizeBank in\db,size-1
		in\system=0
	EndIf
End Function


Function udp_process_queued()
	Local lo.Tudp_ipq
	For Tudp_con.Tudp_con=Each Tudp_con
		ip=Tudp_con\ip
		port=Tudp_con\port
		c=0
		For Tudp_ipq.Tudp_ipq=Each Tudp_ipq
			If Tudp_ipq\ip=ip Then
				If Tudp_ipq\port=port Then
					Tudp_ipq\checked=0
					c=c+1
				EndIf
			EndIf
		Next
		If c>0 Then
			For i=1 To c
				lowest=-1
				For Tudp_ipq.Tudp_ipq=Each Tudp_ipq
					If Tudp_ipq\ip=ip Then
						If Tudp_ipq\port=port Then
							If Tudp_ipq\checked=0 Then
								If lowest=-1 Then
									lowest=Tudp_ipq\id
									lo.Tudp_ipq=Tudp_ipq
								Else
									If Tudp_ipq\id<lowest Then
										lowest=Tudp_ipq\id
										lo.Tudp_ipq=Tudp_ipq
									EndIf
								EndIf
							EndIf
						EndIf
					EndIf
				Next
				
				lo\checked=1
				
				If lo\reliable=1 Then
					If lo\id=Tudp_con\iid Then
						udp_process(lo)
						Tudp_con\iid=Tudp_con\iid+2
						If Tudp_con\iid>Cudp_win_limit Then
							Tudp_con\iid=2
							For old.Tudp_ipq=Each Tudp_ipq
								If old\ip=ip Then
									If old\port=port Then
										If old\id>Cudp_win_limit/2 Then
											FreeBank old\db
											Delete old
										EndIf
									EndIf
								EndIf
							Next
						EndIf
						FreeBank lo\db
						Delete lo
						Tudp_con\resend=0
					ElseIf lo\id<Tudp_con\iid Then
						FreeBank lo\db
						Delete lo
					Else
						If Tudp_con\resend=0 Then
							Tudp_con\resend=udp_ms+udp_request
							DebugLog "request "+Tudp_con\iid
						EndIf
						Exit
					EndIf
				Else
					con_add("process unreliable packet")
					udp_process(lo)
					FreeBank lo\db
					Delete lo
				EndIf
					
			Next
		EndIf
		If udp_ms-Tudp_con\lastaction>=udp_timeout Then
			udp_con_delete(Tudp_con)
		Else
			If Tudp_con\resend=0 Then
				If udp_ms-Tudp_con\ack>=udp_ack Then
					Tudp_con\ack=udp_ms
					udp_send(Tudp_con\ip,Tudp_con\port,Cudp_sys_ack,0)
					udp_w_short(Tudp_con\iid)
				EndIf
			Else
				If udp_ms-Tudp_con\resend>=0 Then
					Tudp_con\resend=udp_ms+udp_rerequest
					udp_send(Tudp_con\ip,Tudp_con\port,Cudp_sys_req,0)
					udp_w_short(Tudp_con\iid)
				EndIf
			EndIf
		EndIf
	Next
End Function


Function udp_windowing(id1,id2)
	If id2=-1 Then Return 1
	If id1>=Cudp_win_limit-Cudp_win_range Then
		If id2>id1 Then
			Return 1
		Else
			If id2<=Cudp_win_range-(Cudp_win_limit-id1) Then
				Return 1
			Else
				Return 0
			EndIf
		EndIf
	ElseIf id1<Cudp_win_range Then
		If id2>id1 Then
			If (id2-id1)<=Cudp_win_range Then
				Return 1
			Else
				Return 0
			EndIf
		Else
			Return 0
		EndIf
	Else
		If id2>id1 Then
			If (id2-id1)<=Cudp_win_range Then
				Return 1
			Else
				Return 0
			EndIf
		Else
			Return 0
		EndIf
	EndIf
End Function
