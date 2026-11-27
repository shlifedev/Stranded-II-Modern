Function tcp_loadfile(server$,path$,localfile$)
	p_return$=0
	Local file
	Local tcp
	Local in$,byte
	Local size,gotsize
	server$=Replace(server$,"\","/")
	path$=Replace(path$,"\","/")
	If Instr(localfile$,":") Then
		con_add("ERROR: Absolute path '"+localfile$+"' for loadfile command is not allowed",Cbmpf_red)
		Return 0
	EndIf
	tcp=OpenTCPStream(server$,80)
	If tcp<>0 Then
		WriteLine tcp,"GET http://"+server$+"/"+path$+" HTTP/1.1"
		WriteLine tcp,"Host: "+server$
		WriteLine tcp,"User-Agent: Stranded II"
		WriteLine tcp,"Accept: */*"
		WriteLine tcp,""
		While Not Eof(tcp)
			in$=ReadLine(tcp)
			If Lower(Left(in$,15))="content-length:" Then
				in$=Mid(in$,16,-1)
				in$=Trim(in$)
				size=Int(in$)
				gotsize=1
			EndIf
			If in$="" Then Exit
		Wend
		If gotsize=1 Then
			file=WriteFile(localfile$)
			If file<>0 Then
				For i=1 To size
					If Not Eof(tcp) Then
						byte=ReadByte(tcp)
						WriteByte file,byte
					Else
						con_add("ERROR: Unable to finish download of 'http://"+server$+"/"+path$+"' ("+i+" of "+size+" Bytes)",Cbmpf_red)
						i=0
						Exit
					EndIf
				Next
				CloseFile file
				If i=size Then p_return$=1
			Else
				con_add("ERROR: Unable to write '"+localfile$+"'",Cbmpf_red)
			EndIf
		Else
			con_add("ERROR: Unable to download 'http://"+server$+"/"+path$+"' (file not found or invalid http header)",Cbmpf_red)
		EndIf
		CloseTCPStream tcp
	Else
		con_add("ERROR: Unable to connect to '"+server$+"'",Cbmpf_red)
	EndIf
End Function


Function tcp_callscript(server$,path$)
	p_return$=0
	Local tcp
	server$=Replace(server$,"\","/")
	path$=Replace(path$,"\","/")
	pv_buffer$=""
	tcp=OpenTCPStream(server$,80)
	If tcp<>0 Then
		WriteLine tcp,"GET http://"+server$+"/"+path$+" HTTP/1.1"
		WriteLine tcp,"Host: "+server$
		WriteLine tcp,"User-Agent: Stranded II"
		WriteLine tcp,"Accept: */*"
		WriteLine tcp,""
		p_return$=1
		While Not Eof(tcp)
			in$=ReadLine(tcp)
			If in$="" Then Exit
		Wend
		While Not Eof(tcp)
			in$=ReadLine(tcp)
			pv_buffer$=pv_buffer$+in$+""
		Wend
		CloseTCPStream tcp
	Else
		con_add("ERROR: Unable to connect to '"+server$+"'",Cbmpf_red)
	EndIf
End Function