
Context "Legacy"

value = 1

Gosub label1
Goto finish

.label1
value = 2
Return

If value=1 Then
	Gosub label1
	Goto finish
End If

value = 3

.finish

Expect value = 2, "value = 2"
