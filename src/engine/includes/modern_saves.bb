Global modern_saving,modern_continue_path$

; Cheap preflight for the legacy format: header, mode, preview, end marker.
; This is not a complete parser for arbitrary corrupt or malicious files.
Function modern_save_valid(path$)
 If FileType(path$)<>1 Then Return False
 Local size=FileSize(path$)
 If size<21000 Then Return False
 Local stream=ReadFile(path$),ok=False,line$,tail$
 If stream=0 Then Return False
 line$=ReadLine(stream)
 If Left(line$,15)="### Stranded II" Then
  ReadLine stream:ReadLine stream:ReadLine stream
  line$=ReadLine(stream)
  If line$="default" Then
   line$=ReadLine(stream)
   If line$="sav" Then
    SeekFile stream,size-80
    While Not Eof(stream)
     tail$=tail$+Chr(ReadByte(stream))
    Wend
    ok=(Instr(tail$,"### EOF Map File")>0 And Instr(tail$,"www.unrealsoftware.de")>0)
   EndIf
  EndIf
 EndIf
 CloseFile stream
 Return ok
End Function

Function modern_autosave_path$(slot)
 If slot=1 Then Return "saves\AUTOSAVE.sav"
 Return "saves\AUTOSAVE_"+slot+".sav"
End Function

Function modern_autosave_target$()
 Local infoBank=CreateBank(320),oldlow,oldhigh,chosen$,path$,i
 For i=1 To 5
  path$=modern_autosave_path(i)
  If FileType(path$)=0 Then FreeBank infoBank:Return path$
  If modern_stamp(path$,infoBank) Then
   If chosen$="" Then
    chosen$=path$:oldlow=PeekInt(infoBank,20):oldhigh=PeekInt(infoBank,24)
   ElseIf modern_stamp_newer(oldlow,oldhigh,PeekInt(infoBank,20),PeekInt(infoBank,24)) Then
    chosen$=path$:oldlow=PeekInt(infoBank,20):oldhigh=PeekInt(infoBank,24)
   EndIf
  EndIf
 Next
 FreeBank infoBank
 Return chosen$
End Function

Function modern_latest_save$(autosaves=0)
 Local infoBank=CreateBank(320),bestlow,besthigh,chosen$,path$,file$,i,dir
 If autosaves Then
  For i=1 To 5
   path$=modern_autosave_path(i)
   If modern_save_valid(path$) Then
    If modern_stamp(path$,infoBank) Then
     If chosen$="" Or modern_stamp_newer(PeekInt(infoBank,20),PeekInt(infoBank,24),bestlow,besthigh) Then
      chosen$=path$:bestlow=PeekInt(infoBank,20):besthigh=PeekInt(infoBank,24)
     EndIf
    EndIf
   EndIf
  Next
 Else
  dir=ReadDir("saves")
  If dir Then
   Repeat
    file$=NextFile(dir)
    If file$="" Then Exit
    If Lower(Right(file$,4))=".sav" Then
     path$="saves\"+file$
     If modern_save_valid(path$) Then
      If modern_stamp(path$,infoBank) Then
       If chosen$="" Or modern_stamp_newer(PeekInt(infoBank,20),PeekInt(infoBank,24),bestlow,besthigh) Then
        chosen$=path$:bestlow=PeekInt(infoBank,20):besthigh=PeekInt(infoBank,24)
       EndIf
      EndIf
     EndIf
    EndIf
   Forever
   CloseDir dir
  EndIf
 EndIf
 FreeBank infoBank
 Return chosen$
End Function

Function modern_queue_save(mode)
 Local request.Tin_quicksave
 For request=Each Tin_quicksave
  If request\mode=mode Then Return
 Next
 request=New Tin_quicksave
 request\mode=mode
 in_quicksave=1
End Function

Function modern_process_saves()
 If in_quicksave=0 Or modern_saving Then Return
 Local request.Tin_quicksave,mode,path$,skills,items,vars,diary,states,locks,count
 request=First Tin_quicksave
 While request<>Null
  mode=request\mode:path$=request\map$
  skills=request\skills:items=request\items:vars=request\vars
  diary=request\diary:states=request\states:locks=request\locks
  Delete request
  Select mode
   Case 1
    If game_m_loadsave Then save_map("saves\QUICKSAVE.sav","sav","")
   Case 2
    If game_m_loadsave Then
     If FileType("saves\QUICKSAVE.sav")=1 Then
      load_map("saves\QUICKSAVE.sav","")
      Exit
     Else
      if_msg("[[loc:modern.no_quicksave]]",Cbmpf_red)
     EndIf
    EndIf
   Case 3
    If game_m_loadsave Then
     path$=modern_autosave_target()
     If path$<>"" Then
      save_map(path$,"sav","")
     Else
      gui_msg("[[loc:modern.save_failed]]",Cbmpf_red)
     EndIf
     For Tp.Tp=Each Tp
      If Tp\typ=-1 Then Delete Tp
     Next
    EndIf
   Case 4
    If game_m_loadsave Then
     path$=modern_latest_save(1)
     If path$<>"" Then load_map(path$,""):Exit
    EndIf
   Case 5
    load_map(path$,"")
    Exit
   Case 6
    If save_map(path$,"map","",skills,items,vars,diary,states,locks)=0 Then parse_error("Failed to save "+path$)
    For Tp.Tp=Each Tp
     If Tp\typ=-1 Then Delete Tp
    Next
  End Select
  count=count+1
  If count>=64 Then Exit
  ; Loading clears the world and queue. Never keep an iterator into freed data.
  request=First Tin_quicksave
 Wend
 request=First Tin_quicksave
 in_quicksave=(request<>Null)
End Function
