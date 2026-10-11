; Same source is executed by Blitz3D 1.118 and the wasm compiler.
; Binary output catches ABI, integer-width, string and save-format regressions.
Global output=WriteFile("semantics.bin")
Global big=$7FFFFFFF,negative=$80000000
WriteInt output,big+1
WriteInt output,negative-1
WriteInt output,(big+1)<0
WriteInt output,($7FFFFFFF+1)<0
WriteInt output,negative Shr 24
WriteInt output,negative Sar 24
WriteInt output,negative Xor $FFFFFFFF
WriteInt output,-7 Mod 3
WriteInt output,Int(2.5)
WriteInt output,Int(-2.5)
WriteInt output,Int(2.9)
Global fractional#=2.5,other#=-2.5
WriteInt output,Int(fractional#)
WriteInt output,Int(other#)
WriteInt output,fractional#>other#
Global left$="a",right$="b"
WriteInt output,left$<right$
WriteInt output,Not 0
; Conditions must test nonzero, not only the least significant bit.
Dim conditions(8)
conditions(0)=0:conditions(1)=1:conditions(2)=2:conditions(3)=-1
conditions(4)=-2:conditions(5)=$80000000:conditions(6)=4
conditions(7)=6:conditions(8)=$7FFFFFFF
Global condition,iterations
For conditionIndex=0 To 8
 condition=conditions(conditionIndex):iterations=0
 While condition
  iterations=iterations+1:Exit
 Wend
 WriteInt output,iterations
 iterations=0
 Repeat
  iterations=iterations+1
  If iterations=2 Then Exit
 Until condition
 WriteInt output,iterations
 If condition Then WriteInt output,1 Else WriteInt output,0
Next
WriteFloat output,0.1
Global rounded#=16777216.
rounded#=rounded#+1.
WriteFloat output,rounded#
WriteFloat output,Float(big)
WriteShort output,65535
WriteByte output,255
WriteString output,"한글 / Ä / $wood"
WriteString output,Chr(31)+Chr(30)+Chr(29)+Chr(255)
Global bank=CreateBank(13)
PokeInt bank,1,$12345678
PokeFloat bank,5,1.25
PokeShort bank,9,65535
PokeByte bank,11,255
WriteInt output,PeekInt(bank,1)
WriteFloat output,PeekFloat(bank,5)
WriteShort output,PeekShort(bank,9)
WriteByte output,PeekByte(bank,11)
Type Snapshot
 Field integer,real#,name$,scores[3]
End Type
Global snapshot.Snapshot=New Snapshot
snapshot\integer=-123456789
snapshot\real#=0.25
snapshot\name$="save"
snapshot\scores[0]=11:snapshot\scores[3]=44
WriteInt output,snapshot\integer
WriteFloat output,snapshot\real#
WriteString output,snapshot\name$
WriteInt output,snapshot\scores[0]
WriteInt output,snapshot\scores[3]
Global web_probe$=SystemProperty("StrandedWeb:probe")
If web_probe$<>"" Then
 WriteInt output,Int(web_probe$)
Else
 WriteInt output,42
EndIf
Delete snapshot
; The script/sequence queues append and delete entries during traversal.
Type EventProbe
 Field id
End Type
Global eventProbe.EventProbe,newEvent.EventProbe
For eventIndex=1 To 3
 newEvent=New EventProbe:newEvent\id=eventIndex
Next
For eventProbe=Each EventProbe
 WriteInt output,eventProbe\id
 If eventProbe\id=2 Then newEvent=New EventProbe:newEvent\id=4
 Delete eventProbe
Next
; SCRIPT_CHECKS
FreeBank bank
CloseFile output
End
