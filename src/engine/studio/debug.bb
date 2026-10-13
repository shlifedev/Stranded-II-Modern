; Read-only diagnostics, emitted outside the parser. No game variables are set.
Function studio_json$(value$)
 Local result$=Chr(34),i,c,ch$
 For i=1 To Len(value$)
  ch$=Mid(value$,i,1):c=Asc(ch$)
  If ch$=Chr(34) Or ch$=Chr(92) Then
   result$=result$+Chr(92)+ch$
  ElseIf c>=0 And c<32 Then
   result$=result$+Chr(92)+"u00"+Right(Hex(c),2)
  Else
   result$=result$+ch$
  EndIf
 Next
 Return result$+Chr(34)
End Function

Function studio_debug_reset(enabled)
 p_trace_enabled=enabled
 Delete Each TparseTrace
 p_trace_count=0
End Function

Function studio_debug_flush()
 Local payload$,values$,count=0,scope$
 For trace.TparseTrace=Each TparseTrace
  payload$="{"+studio_json("kind")+":"+studio_json(trace\category$)
  payload$=payload$+","+studio_json("event")+":"+studio_json(trace\event$)
  payload$=payload$+","+studio_json("info")+":"+studio_json(trace\info$)
  payload$=payload$+","+studio_json("message")+":"+studio_json(trace\message$)
  payload$=payload$+","+studio_json("code")+":"+studio_json(trace\code$)
  payload$=payload$+","+studio_json("script")+":"+studio_json(trace\source$)
  payload$=payload$+","+studio_json("class")+":"+trace\class+","+studio_json("id")+":"+trace\id
  payload$=payload$+","+studio_json("row")+":"+trace\row+","+studio_json("column")+":"+trace\column+"}"
  web_service("studio-event","debug"+Chr(31)+payload$)
  Delete trace
 Next
 p_trace_count=0
 If studio_playing=0 Or (gframe Mod 30)<>0 Then Return
 For variable.Tx=Each Tx
  scope$=""
  If variable\mode=1 Then scope$="global"
  If variable\mode=4 And variable\parent_class=studio_class And variable\parent_id=studio_id Then scope$="selected"
  If scope$<>"" Then
   If count>0 Then values$=values$+","
   values$=values$+"["+studio_json(scope$)+","+studio_json(variable\key$)+","+studio_json(Left(variable\value$,1024))+"]"
   count=count+1
   If count>=256 Then Exit
  EndIf
 Next
 payload$="{"+studio_json("kind")+":"+studio_json("variables")+","+studio_json("class")+":"+studio_class+","+studio_json("id")+":"+studio_id+","+studio_json("values")+":["+values$+"]}"
 web_service("studio-event","debug"+Chr(31)+payload$)
End Function
