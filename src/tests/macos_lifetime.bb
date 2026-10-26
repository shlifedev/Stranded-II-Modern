; Reused Type storage must clear every byte of native pointer slots.
Type Churn
 Field value,text$,other$,child.Churn
End Type
For pass=1 To 100
 For i=1 To 200
  record.Churn=New Churn
  If record\text<>"" Or record\other<>"" Or record\child<>Null Then RuntimeError "Uninitialized native Type"
  record\value=i:record\text="한글 "+i:record\other="object "+pass
 Next
 total=0
 For record=Each Churn
  total=total+record\value
  Delete record
 Next
 If total<>20100 Then RuntimeError "Native Type iteration mismatch"
Next
End
