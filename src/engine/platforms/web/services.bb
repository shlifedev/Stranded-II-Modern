; Browser implementations of the OS services used by the shared game.
; SystemProperty's StrandedWeb namespace is provided by the pinned web runtime.
Global web_file_path$,web_file_error,web_clipboard$

Function web_service$(operation$,value$="")
 Return SystemProperty("StrandedWeb:"+operation$+Chr(31)+value$)
End Function

Function web_words(bank,value$)
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
 Return web_words(bank,web_service("counter"))
End Function

Function modern_interrupt_time(bank)
 web_words(bank,web_service("walltime"))
End Function

Function modern_sleep(milliseconds)
 Delay milliseconds
End Function

Function modern_dpi_aware()
 Return True
End Function

Function modern_mouse_capture(enabled)
 ; The browser shell owns capture through an explicit user gesture.
 Return True
End Function

Function modern_win_foreground()
 ; Let the original loading screen and main menu render before a user gesture.
 ; Gameplay still pauses without capture; browser focus is required in both.
 If m_section=Csection_menu Then Return Int(web_service("focus","menu"))
 Return Int(web_service("focus","play"))
End Function

Function modern_win_iconic(window)
 Return 0
End Function

Function modern_win_style(window,index,style)
 ; Browser window chrome belongs to the browser; the canvas owns presentation.
 Return True
End Function

Function modern_win_metric(index)
 If index=0 Then Return Int(web_service("width"))
 Return Int(web_service("height"))
End Function

Function modern_win_position(window,insertAfter,x,y,width,height,flags)
 If window=2 Then web_service("search-position",x+Chr(31)+y+Chr(31)+width+Chr(31)+height)
 Return True
End Function

Function modern_file_open(name$,access,share,security,creation,attributes,template)
 web_file_path$=name$
 If FileType(name$)<>1 Then web_file_error=2:Return -1
 Return 1
End Function

Function modern_file_flush_handle(fileHandle)
 Local ok=Int(web_service("sync"))
 If ok=0 Then web_file_error=5
 Return ok
End Function

Function modern_handle_close(fileHandle)
 Return True
End Function

Function modern_file_copy(source$,destination$,failIfExists)
 Local ok=Int(web_service("copy",source$+Chr(31)+destination$+Chr(31)+failIfExists))
 If ok=0 Then web_file_error=5
 Return ok
End Function

Function modern_file_move(source$,destination$,flags)
 Local ok=Int(web_service("move",source$+Chr(31)+destination$))
 If ok=0 Then web_file_error=5
 Return ok
End Function

Function modern_file_find(name$,bank)
 Local stamp$=web_service("stamp",name$),words=CreateBank(8)
 If web_words(words,stamp$)=0 Then FreeBank words:Return -1
 PokeInt bank,20,PeekInt(words,0):PokeInt bank,24,PeekInt(words,4)
 FreeBank words
 Return 1
End Function

Function modern_find_close(fileHandle)
 Return True
End Function

Function modern_last_error()
 Return web_file_error
End Function

Function web_wide$(bank)
 Local text$,i,code
 For i=0 To BankSize(bank)/2-1
  code=PeekShort(bank,i*2) And 65535
  If code=0 Then Exit
  text$=text$+qol_utf8(code)
 Next
 Return text$
End Function

Function qol_win_create(exstyle,classbank,titlebank,style,x,y,width,height,parent,menu,instance,param)
 web_service("search-open",web_wide(titlebank))
 Return 2
End Function

Function qol_win_destroy(window)
 web_service("search-close")
 Return True
End Function

Function qol_win_text(window,bank,length)
 Local text$=web_service("search-text"),pos=1,n,code
 While pos<=Len(text$) And n<length-1
  code=ko_code(text$,pos):PokeShort bank,n*2,code
  pos=ko_next(text$,pos-1)+1:n=n+1
 Wend
 PokeShort bank,n*2,0
 Return n
End Function

Function qol_win_settext(window,bank)
 web_service("search-set",web_wide(bank))
 Return True
End Function

Function qol_win_message(window,message,wparam,lparam)
 Return True
End Function

Function qol_win_focus(window)
 web_service("search-focus",window)
 Return True
End Function

Function qol_win_getfocus()
 Return Int(web_service("control-focus"))
End Function

Function qol_win_client(window,bank)
 PokeInt bank,0,0:PokeInt bank,4,0
 PokeInt bank,8,Int(web_service("canvas-width"))
 PokeInt bank,12,Int(web_service("canvas-height"))
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
 web_clipboard$=""
 Return True
End Function

Function ExamineClipboard(format)
 web_clipboard$=GetClipboardContents()
 Return Len(web_clipboard$)>0
End Function

Function GetClipboardData$(format)
 Return web_clipboard$
End Function

Function SetClipboardData(format,text$)
 SetClipboardContents text$
 Return True
End Function

; Legacy editor downloads require raw TCP, which browsers do not expose.
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
