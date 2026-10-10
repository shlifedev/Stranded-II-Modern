; Rates are expressed per original 20 ms update (50 Hz).
Function modern_blend#(rate#,steps#)
 If steps#<=0. Then Return 0.
 If rate#>=1. Then Return 1.
 If rate#<=0. Then Return 0.
 Return 1.-((1.-rate#)^steps#)
End Function

Function modern_delta(elapsed)
 If elapsed<0 Then Return 0
 If elapsed>100 Then Return 100
 Return elapsed
End Function
