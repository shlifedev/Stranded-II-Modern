; Shared UTF-8 catalogs. References stay intact in scripts and saved games.
; Grammar: [[loc:key]] or [[loc:key|variable=HEX_UTF8_BYTES]].
Dim loc_heads(4095)
Global loc_language$="ko",loc_saved$="ko",loc_pending$="ko"
Global loc_language_count
Type Tlanguage
 Field code$,name$,index
End Type
Type Tlocale
 Field key$,value$,link
End Type

Function loc_hash(key$)
 Local h=0,i
 For i=1 To Len(key$)
  h=(h*33+Asc(Mid(key$,i,1))) And 4095
 Next
 Return h
End Function

Function loc_find.Tlocale(key$)
 Local entry.Tlocale=Object.Tlocale(loc_heads(loc_hash(key$)))
 While entry<>Null
  If entry\key$=key$ Then Return entry
  entry=Object.Tlocale(entry\link)
 Wend
 Return Null
End Function

Function loc_put(key$,value$)
 Local entry.Tlocale=loc_find(key$),h
 If entry=Null Then
  entry=New Tlocale:entry\key$=key$
  h=loc_hash(key$):entry\link=loc_heads(h):loc_heads(h)=Handle(entry)
 EndIf
 entry\value$=value$
End Function

Function loc_unescape$(value$)
 Local result$="",i=1,c$
 While i<=Len(value$)
  c$=Mid(value$,i,1)
  If c$=Chr(92) And i<Len(value$)
   i=i+1:c$=Mid(value$,i,1)
   Select c$
    Case "n":c$=Chr(31)
    Case "r":c$=""
    Case "t":c$=Chr(9)
   End Select
  EndIf
  result$=result$+c$:i=i+1
 Wend
 Return result$
End Function

Function loc_read(path$)
 Local stream=ReadFile(path$),line$,tab
 If stream=0 Then Return False
 While Not Eof(stream)
  line$=ReadLine(stream)
  If Left(line$,1)<>"#" Then
   tab=Instr(line$,Chr(9))
   If tab>1 Then loc_put(Left(line$,tab-1),loc_unescape(Mid(line$,tab+1)))
  EndIf
 Wend
 CloseFile stream
 Return True
End Function

Function loc_load(language$)
 Local i
 If Not loc_available(language$) Then language$="en"
 Delete Each Tlocale
 For i=0 To 4095:loc_heads(i)=0:Next
 If Not loc_read(set_rootdir$+"core\locales\en.tsv") Then RuntimeError "Missing English localization catalog"
 If language$<>"en" Then
  If Not loc_read(set_rootdir$+"core\locales\"+language$+".tsv") Then language$="en"
 EndIf
 loc_language$=language$
End Function

Function loc_boot()
 Local stream=ReadFile(set_rootdir$+"core\modern.ini"),line$,eq,value$
 loc_languages_load()
 loc_saved$="ko"
 If stream Then
  While Not Eof(stream)
   line$=Trim(ReadLine(stream)):eq=Instr(line$,"=")
   If eq>0 Then
    If Lower(Trim(Left(line$,eq-1)))="language" Then
     value$=Lower(Trim(Mid(line$,eq+1)))
     loc_saved$=value$
    EndIf
   EndIf
  Wend
  CloseFile stream
 EndIf
 loc_load(loc_saved$)
 loc_saved$=loc_language$:loc_pending$=loc_saved$
End Function

Function loc_available(code$)
 Local language.Tlanguage
 For language=Each Tlanguage
  If language\code$=code$ Then Return FileType(set_rootdir$+"core\locales\"+code$+".tsv")=1
 Next
 Return False
End Function

Function loc_languages_load()
 Delete Each Tlanguage:loc_language_count=0
 Local stream=ReadFile(set_rootdir$+"core\locales\languages.dat"),n,i,language.Tlanguage
 If stream=0 Then RuntimeError "Language registry missing. Run scripts/dev.py localize."
 n=ReadInt(stream)
 If n<1 Or n>1024 Then RuntimeError "Invalid language registry"
 For i=1 To n
  language=New Tlanguage
  language\code$=ReadString(stream):language\name$=ReadString(stream)
  If FileType(set_rootdir$+"core\locales\"+language\code$+".tsv")=1 Then
   language\index=loc_language_count:loc_language_count=loc_language_count+1
  Else
   Delete language
  EndIf
 Next
 CloseFile stream
 If Not loc_available("en") Then RuntimeError "Missing English localization catalog"
End Function

Function loc_name$(code$)
 Local language.Tlanguage
 For language=Each Tlanguage
  If language\code$=code$ Then Return language\name$
 Next
 Return code$
End Function

Function loc_cycle(direction)
 Local language.Tlanguage,index=0
 If loc_language_count=0 Then Return
 For language=Each Tlanguage
  If language\code$=loc_pending$ Then index=language\index:Exit
 Next
 index=(index+direction+loc_language_count) Mod loc_language_count
 For language=Each Tlanguage
  If language\index=index Then loc_pending$=language\code$:Return
 Next
End Function

; Save only the language setting. Preserve frame/display settings and comments.
Function loc_save()
 Local path$=set_rootdir$+"core\modern.ini",stream=ReadFile(path$)
 Local content$="",line$,eq
 If stream Then
  While Not Eof(stream)
   line$=ReadLine(stream):eq=Instr(line$,"=")
   If eq>0 Then
    If Lower(Trim(Left(line$,eq-1)))="language" Then line$=""
   EndIf
   content$=content$+line$+Chr(13)+Chr(10)
  Wend
  CloseFile stream
 EndIf
 stream=WriteFile(path$+".tmp")
 If stream=0 Then Return False
 WriteLine stream,content$+"language="+loc_pending$
 CloseFile stream
 CopyFile path$+".tmp",path$
 If FileSize(path$)<>FileSize(path$+".tmp") Then Return False
 DeleteFile path$+".tmp"
 loc_saved$=loc_pending$
 Return True
End Function

Function loc_get$(key$)
 Local entry.Tlocale=loc_find(key$),stream,path$
 If entry<>Null Then Return entry\value$
 ; Cache the diagnostic so a missing label does not flood the log each frame.
 path$=set_rootdir$+"core\localization.log"
 stream=OpenFile(path$)
 If stream=0 Then stream=WriteFile(path$)
 If stream Then
  SeekFile stream,FileSize(path$)
  WriteLine stream,"Missing localization key: "+key$
  CloseFile stream
 EndIf
 loc_put(key$,"[missing: "+key$+"]")
 Return "[missing: "+key$+"]"
End Function

Function loc_hex$(value$)
 Local result$="",i
 For i=1 To Len(value$)
  result$=result$+Right(Hex(Asc(Mid(value$,i,1))),2)
 Next
 Return result$
End Function

Function loc_unhex$(value$)
 Local result$="",i,a,b,digits$="0123456789ABCDEF"
 If Len(value$) Mod 2 Then Return ""
 For i=1 To Len(value$) Step 2
  a=Instr(digits$,Upper(Mid(value$,i,1)))-1
  b=Instr(digits$,Upper(Mid(value$,i+1,1)))-1
  If a<0 Or b<0 Then Return ""
  result$=result$+Chr(a*16+b)
 Next
 Return result$
End Function

Function loc_varbyte(c)
 Return (c>=48 And c<=57) Or (c>=65 And c<=90) Or (c>=97 And c<=122) Or c=95
End Function

Function loc_escaped(text$,pos)
 If pos<=1 Then Return False
 Return Mid(text$,pos-1,1)=Chr(92)
End Function

Function loc_substitute$(template$,bindings$)
 Local result$="",i=1,j,name$,pos,ending,hexvalue$
 While i<=Len(template$)
  If Mid(template$,i,1)="$" And loc_escaped(template$,i)=0 Then
   j=i+1
   While j<=Len(template$)
    If Not loc_varbyte(Asc(Mid(template$,j,1))) Then Exit
    j=j+1
   Wend
   name$=Mid(template$,i+1,j-i-1)
   pos=Instr(bindings$,"|"+name$+"=")
   If pos>0 And name$<>"" Then
    pos=pos+Len(name$)+2:ending=Instr(bindings$,"|",pos)
    If ending=0 Then ending=Len(bindings$)+1
    result$=result$+loc_unhex(Mid(bindings$,pos,ending-pos)):i=j
   Else
    result$=result$+Mid(template$,i,1):i=i+1
   EndIf
  Else
   result$=result$+Mid(template$,i,1):i=i+1
  EndIf
 Wend
 Return result$
End Function

Function loc_resolve$(text$,depth=0)
 If depth>=8 Then Return text$
 Local result$="",cursor=1,start,ending,key$,body$,bar,value$
 Repeat
  start=Instr(text$,"[[loc:",cursor)
  If start=0 Then Exit
  ending=Instr(text$,"]]",start+6)
  If ending=0 Then Exit
  body$=Mid(text$,start+6,ending-start-6):bar=Instr(body$,"|")
  key$=body$:If bar Then key$=Left(body$,bar-1)
  value$=loc_get(key$)
  If bar Then value$=loc_substitute(value$,Mid(body$,bar))
  result$=result$+Mid(text$,cursor,start-cursor)+loc_resolve(value$,depth+1)
  cursor=ending+2
 Forever
 Return result$+Mid(text$,cursor)
End Function

; Capture script variables at command evaluation, without baking in a language.
; Hex encoding protects commas, quotes, dollar signs and reference delimiters.
Function loc_bind$(text$)
 Local result$="",cursor=1,start,ending,key$,template$,bindings$,i,j,name$
 Repeat
  start=Instr(text$,"[[loc:",cursor)
  If start=0 Then Exit
  ending=Instr(text$,"]]",start+6)
  If ending=0 Then Exit
  key$=Mid(text$,start+6,ending-start-6):bindings$=""
  If Instr(key$,"|")=0 Then
   ; Both catalogs share the validated placeholder contract.
   template$=loc_get(key$):i=1
   While i<=Len(template$)
    If Mid(template$,i,1)="$" And loc_escaped(template$,i)=0 Then
     j=i+1
     While j<=Len(template$)
      If Not loc_varbyte(Asc(Mid(template$,j,1))) Then Exit
      j=j+1
     Wend
     name$=Mid(template$,i+1,j-i-1)
     If name$<>"" And name$<>"img" And Left(name$,4)<>"key_" Then
      If Instr(bindings$,"|"+name$+"=")=0 Then bindings$=bindings$+"|"+name$+"="+loc_hex(var_get(name$))
     EndIf
     i=j
    Else
     i=i+1
    EndIf
   Wend
  EndIf
  result$=result$+Mid(text$,cursor,ending-cursor)+bindings$+"]]"
  cursor=ending+2
 Forever
 Return result$+Mid(text$,cursor)
End Function
