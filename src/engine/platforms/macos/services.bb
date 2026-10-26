; Native macOS adapters for the unchanged shared game.
; Native pointers are never exposed as Blitz integers.
Global mac_file_path$,mac_file_error,mac_clipboard$

Function mac_service$(operation$,value$="")
 Return SystemProperty("StrandedMac:"+operation$+Chr(31)+value$)
End Function

Function mac_words(bank,value$)
 Local separator=Instr(value$,Chr(31))
 If separator=0 Then Return False
 PokeInt bank,0,Int(Left(value$,separator-1))
 PokeInt bank,4,Int(Mid(value$,separator+1))
 Return True
End Function

Function modern_counter_frequency(bank)
 PokeInt bank,0,1000000:PokeInt bank,4,0
 Return True
End Function

Function modern_counter(bank)
 Return mac_words(bank,mac_service("counter"))
End Function

Function modern_interrupt_time(bank)
 mac_words(bank,mac_service("walltime"))
End Function

Function modern_sleep(milliseconds)
 Delay milliseconds
End Function

Function modern_dpi_aware()
 Return True
End Function

Function modern_mouse_capture(enabled)
 ; Capture remains managed by the native macOS runtime.
 Return True
End Function

Function modern_win_foreground()
 Return Int(mac_service("focus"))
End Function

Function modern_win_iconic(window)
 Return 0
End Function

Function modern_win_borderless()
 ; Preserve the native Mac presentation path.
 modern_win_style(1,-16,$90000000)
 Return modern_win_position(1,-2,0,0,modern_win_metric(0),modern_win_metric(1),$30)
End Function

Function modern_win_style(window,index,style)
 Return Int(mac_service("borderless"))
End Function

Function modern_win_metric(index)
 If index=0 Then Return Int(mac_service("width"))
 Return Int(mac_service("height"))
End Function

Function modern_win_position(window,insertAfter,x,y,width,height,flags)
 If window=2 Then
  mac_service("search-position",x+Chr(31)+y+Chr(31)+width+Chr(31)+height)
 Else
  mac_service("window-position",x+Chr(31)+y+Chr(31)+width+Chr(31)+height)
 EndIf
 Return True
End Function

Function modern_file_open(name$,access,share,security,creation,attributes,template)
 Return Int(mac_service("file-open",name$))
End Function

Function modern_file_flush_handle(fileHandle)
 Return Int(mac_service("file-flush",fileHandle))
End Function

Function modern_handle_close(fileHandle)
 Return Int(mac_service("file-close",fileHandle))
End Function

Function modern_file_copy(source$,destination$,failIfExists)
 Return Int(mac_service("copy",source$+Chr(31)+destination$+Chr(31)+failIfExists))
End Function

Function modern_file_move(source$,destination$,flags)
 Return Int(mac_service("move",source$+Chr(31)+destination$))
End Function

Function modern_file_find(name$,bank)
 Local stamp$=mac_service("stamp",name$),words=CreateBank(8)
 If mac_words(words,stamp$)=0 Then FreeBank words:Return -1
 PokeInt bank,20,PeekInt(words,0):PokeInt bank,24,PeekInt(words,4)
 FreeBank words
 Return 1
End Function

Function modern_find_close(fileHandle)
 Return True
End Function

Function modern_last_error()
 Return Int(mac_service("error"))
End Function

Function mac_wide$(bank)
 Local text$,i,code
 For i=0 To BankSize(bank)/2-1
  code=PeekShort(bank,i*2) And 65535
  If code=0 Then Exit
  text$=text$+qol_utf8(code)
 Next
 Return text$
End Function

Function qol_win_create(exstyle,classbank,titlebank,style,x,y,width,height,parent,menu,instance,param)
 mac_service("search-open",mac_wide(titlebank))
 Return 2
End Function

Function qol_win_destroy(window)
 mac_service("search-close")
 Return True
End Function

Function qol_win_text(window,bank,length)
 Local text$=mac_service("search-text"),pos=1,n,code
 While pos<=Len(text$) And n<length-1
  code=ko_code(text$,pos):PokeShort bank,n*2,code
  pos=ko_next(text$,pos-1)+1:n=n+1
 Wend
 PokeShort bank,n*2,0
 Return n
End Function

Function qol_win_settext(window,bank)
 mac_service("search-set",mac_wide(bank))
 Return True
End Function

Function qol_win_message(window,message,wparam,lparam)
 mac_service("search-select",wparam+Chr(31)+lparam)
 Return True
End Function

Function qol_win_focus(window)
 mac_service("search-focus",window)
 Return True
End Function

Function qol_win_getfocus()
 Return Int(mac_service("control-focus"))
End Function

Function qol_win_client(window,bank)
 PokeInt bank,0,0:PokeInt bank,4,0
 PokeInt bank,8,Int(mac_service("canvas-width"))
 PokeInt bank,12,Int(mac_service("canvas-height"))
 Return True
End Function

Function qol_native_attach(edit,parent)
 Return True
End Function

Function qol_native_detach()
End Function

Function OpenClipboard(window)
 Return True
End Function

Function CloseClipboard()
 Return True
End Function

Function EmptyClipboard()
 mac_clipboard$=""
 Return True
End Function

Function ExamineClipboard(format)
 mac_clipboard$=mac_service("clipboard-get")
 Return Len(mac_clipboard$)>0
End Function

Function GetClipboardData$(format)
 Return mac_clipboard$
End Function

Function SetClipboardData(format,text$)
 mac_service("clipboard-set",text$)
 Return True
End Function

; Networking is outside the initial native runtime; retain connection-failure behavior.
; Return the documented connection-failure result so callers take their error path.
Function OpenTCPStream(host$,port)
 Return 0
End Function

Function CloseTCPStream(stream)
End Function

Function TCPTimeouts(connectTimeout,readTimeout)
End Function

Function DottedIP$(ip)
 Return ((ip Shr 24) And 255)+"."+((ip Shr 16) And 255)+"."+((ip Shr 8) And 255)+"."+(ip And 255)
End Function

Function CreateUDPStream(port=0)
 Return 0
End Function

Function CloseUDPStream(stream)
End Function

Function SendUDPMsg(stream,ip,port)
 Return 0
End Function

Function RecvUDPMsg(stream)
 Return 0
End Function

Function UDPMsgIP(stream)
 Return 0
End Function

Function UDPMsgPort(stream)
 Return 0
End Function

Function UDPStreamPort(stream)
 Return 0
End Function

Function TCPStreamIP(stream)
 Return 0
End Function
