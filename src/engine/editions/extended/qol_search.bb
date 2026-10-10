; A Unicode child EDIT owns text/IME input; no ANSI GetKey text conversion.
Global qol_search_hwnd,qol_search_buffer=CreateBank(1024),qol_search_rect=CreateBank(16)
Global qol_query$,qol_search_previous$
Global qol_search_client_width,qol_search_client_height

Function qol_utf8$(code)
 If code<128 Then Return Chr(code)
 If code<2048 Then Return Chr(192+(code/64))+Chr(128+(code Mod 64))
 Return Chr(224+(code/4096))+Chr(128+((code/64) Mod 64))+Chr(128+(code Mod 64))
End Function

Function qol_wide(text$)
 Local pos=1,n,code
 While pos<=Len(text$) And n<510
  code=ko_code(text$,pos):PokeShort qol_search_buffer,n*2,code
  pos=ko_next(text$,pos-1)+1:n=n+1
 Wend
 PokeShort qol_search_buffer,n*2,0
 Return qol_search_buffer
End Function

Function qol_search_set(text$)
 qol_query$=text$:qol_search_previous$=text$
 If qol_search_hwnd Then qol_win_settext(qol_search_hwnd,qol_wide(text$))
End Function

Function qol_search_open()
 If qol_search_hwnd Or modern_hwnd=0 Then Return
 Local classbank=CreateBank(10)
 PokeShort classbank,0,69:PokeShort classbank,2,68:PokeShort classbank,4,73:PokeShort classbank,6,84
 ; WS_CHILD | WS_VISIBLE | WS_BORDER | ES_AUTOHSCROLL.
 qol_search_hwnd=qol_win_create(0,classbank,qol_wide(qol_query$),$50800080,236,55,405,26,modern_hwnd,0,0,0)
 FreeBank classbank
 If qol_search_hwnd Then
  If qol_native_attach(qol_search_hwnd,modern_hwnd)=0 Then
   qol_win_destroy(qol_search_hwnd):qol_search_hwnd=0
  EndIf
 EndIf
 If qol_search_hwnd Then
  qol_win_message(qol_search_hwnd,$C5,128,0) ; EM_LIMITTEXT
  qol_search_client_width=0:qol_search_client_height=0
  qol_present()
 Else
  con_add("QOL: Unicode search control could not be created")
 EndIf
End Function

Function qol_search_close()
 If qol_search_hwnd Then
  qol_native_detach()
  qol_win_destroy(qol_search_hwnd):qol_search_hwnd=0
  qol_win_focus(modern_hwnd):in_inputfocus=-1:flushinput()
 EndIf
End Function

Function qol_search_update()
 Local n=qol_win_text(qol_search_hwnd,qol_search_buffer,511),text$,i,code
 For i=0 To n-1
  code=PeekShort(qol_search_buffer,i*2) And 65535
  If code>=32 And (code<55296 Or code>57343) Then text$=text$+qol_utf8(code)
 Next
 If text$<>qol_query$ Then qol_query$=text$:qol_recipe_scroll=0
 If qol_win_getfocus()=qol_search_hwnd Then
  in_inputfocus=9
  ; Enter/Escape/Tab and IME composition are handled by the native control.
  If in_mrelease(1) And (in_mx<236 Or in_mx>641 Or in_my<55 Or in_my>81) Then
   qol_win_focus(modern_hwnd):in_inputfocus=-1
  EndIf
 ElseIf in_inputfocus=9 Then
  in_inputfocus=-1:flushinput()
 EndIf
End Function

Function qol_present()
 If qol_search_hwnd Then
  If m_menu<>28 Or m_section<>Csection_game_sp Then qol_search_close():Return
  qol_win_client(modern_hwnd,qol_search_rect)
  Local w=PeekInt(qol_search_rect,8),h=PeekInt(qol_search_rect,12)
  If w<>qol_search_client_width Or h<>qol_search_client_height Then
   modern_win_position(qol_search_hwnd,0,236*w/set_scrx,55*h/set_scry,405*w/set_scrx,26*h/set_scry,$14)
   qol_search_client_width=w:qol_search_client_height=h
  EndIf
  ; WS_CLIPCHILDREN protects the EDIT from the game's presentation blit.
  ; Let Windows repaint only on changes; never erase it on every frame.
 EndIf
End Function
