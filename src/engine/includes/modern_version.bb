; Product build label. Cversion remains the original map/script compatibility version.
Global modern_version$="development"

Function modern_version_load()
 modern_version$="development"
 Local stream=ReadFile(set_rootdir$+"core\version.txt")
 If stream=0 Then Return
 Local label$=Trim(ReadLine(stream)),i,value
 CloseFile stream
 If Len(label$)=0 Or Len(label$)>128 Then Return
 For i=1 To Len(label$)
  value=Asc(Mid(label$,i,1))
  If Not ((value>=48 And value<=57) Or (value>=97 And value<=122) Or value=45 Or value=46) Then Return
 Next
 modern_version$=label$
End Function
