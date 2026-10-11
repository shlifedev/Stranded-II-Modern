; Appended to web_semantics.bb by verify_macos.py before closing its output.
; Keep this fixture runnable by the original Windows Blitz3D compiler too.

Restore native_abi_data
Read abiInteger,abiFloat#,abiString$,abiLast
WriteInt output,abiInteger
WriteFloat output,abiFloat#
WriteString output,abiString$
WriteInt output,abiLast
Restore native_abi_tail
Read abiTail$
WriteString output,abiTail$

Dim abiNumbers(2,3)
Dim abiStrings$(2,3)
For abiY=0 To 3
 For abiX=0 To 2
  abiNumbers(abiX,abiY)=abiX+abiY*100
  abiStrings$(abiX,abiY)=Str(abiNumbers(abiX,abiY))
 Next
Next
For abiY=0 To 3
 For abiX=0 To 2
  WriteInt output,abiNumbers(abiX,abiY)
  WriteString output,abiStrings$(abiX,abiY)
 Next
Next

Type NativeRecord
 Field firstValue,number#,text$,lastValue,values[11],names$[11],child.NativeRecord
End Type
Global abiRecord.NativeRecord=New NativeRecord
abiRecord\firstValue=$76543210:abiRecord\number=1.5:abiRecord\text="한글"
abiRecord\lastValue=-123:abiRecord\child=New NativeRecord
abiRecord\child\firstValue=789
For abiY=0 To 3
 For abiX=0 To 2
  abiRecord\values[abiX+abiY*3]=abiX+abiY*100
  abiRecord\names[abiX+abiY*3]=Str(abiRecord\values[abiX+abiY*3])
 Next
Next
WriteInt output,abiRecord\firstValue
WriteFloat output,abiRecord\number
WriteString output,abiRecord\text
WriteInt output,abiRecord\lastValue
WriteInt output,abiRecord\child\firstValue
For abiY=0 To 3
 For abiX=0 To 2
  WriteInt output,abiRecord\values[abiX+abiY*3]
  WriteString output,abiRecord\names[abiX+abiY*3]
 Next
Next
Global abiHandle=Handle(abiRecord),abiLookup.NativeRecord=Object.NativeRecord(abiHandle)
WriteInt output,(abiLookup=abiRecord)
Delete abiRecord\child
Delete abiRecord
abiLookup=Object.NativeRecord(abiHandle)
WriteInt output,(abiLookup=Null)

Global abiBank=CreateBank(16),abiOtherBank=CreateBank(16)
PokeInt abiBank,3,$87654321:PokeFloat abiBank,7,0.125
CopyBank abiBank,3,abiOtherBank,2,8
WriteInt output,PeekInt(abiOtherBank,2)
WriteFloat output,PeekFloat(abiOtherBank,6)
WriteInt output,(abiBank<>abiOtherBank)
FreeBank abiBank:FreeBank abiOtherBank

Global abiCalls
Gosub native_abi_sub
Gosub native_abi_sub
WriteInt output,abiCalls
Goto native_abi_end
.native_abi_sub
abiCalls=abiCalls+1
Return
.native_abi_data
Data -2147483647,0.125,"한글 Data",123456789
.native_abi_tail
Data "restore"
.native_abi_end
