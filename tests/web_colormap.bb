; Run the actual generator with a CPU buffer and fixed terrain height bands.
; Count full-buffer transfers as the WebGPU canvas does, without opening a GUI.
Const Cworld_size=64,Cworld_height=3200,Csection_editor=3
Global m_section=3,ter=1,ter_size=32,ter_tex_color=1
Global probeBuffer=2,probeLocks,probeTransfers,probeWrites,probeInvalid,probeColourErrors
Dim in_opt(5):Dim in_tercol(2,2):Dim game_tercol(8,3)
Dim probePixels(127,127):Dim probeExpected(4,2)
game_tercol(0,0)=230:game_tercol(1,0)=223:game_tercol(2,0)=145
game_tercol(3,0)=69:game_tercol(4,0)=138:game_tercol(5,0)=13
game_tercol(6,0)=160:game_tercol(7,0)=172:game_tercol(8,0)=125
probeExpected(0,0)=230:probeExpected(0,1)=223:probeExpected(0,2)=145
probeExpected(1,0)=167:probeExpected(1,1)=190:probeExpected(1,2)=93
probeExpected(2,0)=69:probeExpected(2,1)=138:probeExpected(2,2)=13
probeExpected(3,0)=92:probeExpected(3,1)=147:probeExpected(3,2)=41
probeExpected(4,0)=160:probeExpected(4,1)=172:probeExpected(4,2)=125

Function probe_TextureHeight(texture)
 Return 128
End Function
Function probe_TextureBuffer(texture)
 Return 1
End Function
Function probe_BackBuffer()
 Return 2
End Function
Function probe_SetBuffer(buffer)
 probeBuffer=buffer
End Function
Function probe_LockBuffer(buffer)
 If probeLocks=0 Then probeTransfers=probeTransfers+1
 probeLocks=probeLocks+1
End Function
Function probe_UnlockBuffer(buffer)
 probeLocks=probeLocks-1
 If probeLocks=0 Then probeTransfers=probeTransfers+1
End Function
Function probe_WritePixel(x,y,rgb)
 If probeLocks=0 Then probeTransfers=probeTransfers+2
 probePixels(x,y)=rgb:probeWrites=probeWrites+1
End Function
Function probe_WritePixelFast(x,y,rgb)
 If probeLocks<1 Or probeBuffer<>1 Then probeInvalid=probeInvalid+1
 probePixels(x,y)=rgb:probeWrites=probeWrites+1
End Function
Function probe_TerrainY#(terrain,x#,y#,z#)
 Select Floor((x#+1024.)*5./2048.)
  Case 0:Return -50.
  Case 1:Return 25.
  Case 2:Return 100.
  Case 3:Return 200.
  Case 4:Return 400.
 End Select
End Function
Function probe_Rand(low,high)
 Return 0
End Function
Function probe_UpdateWorld()
End Function
Function bmpf_loadscreen(message$)
End Function
Function e_tercol(r,g,b,id)
 in_tercol(0,id)=r:in_tercol(1,id)=g:in_tercol(2,id)=b
End Function

; COLORMAP_FUNCTIONS

editor_gencolormap()
For probeX=0 To 127
 For probeY=0 To 127
  Local probeBand=(probeX*5)/128,probeRGB=probePixels(probeX,probeY)
  If Abs(((probeRGB Shr 16) And 255)-probeExpected(probeBand,0))>1 Then probeColourErrors=probeColourErrors+1
  If Abs(((probeRGB Shr 8) And 255)-probeExpected(probeBand,1))>1 Then probeColourErrors=probeColourErrors+1
  If Abs((probeRGB And 255)-probeExpected(probeBand,2))>1 Then probeColourErrors=probeColourErrors+1
 Next
Next
WriteInt output,probeTransfers
WriteInt output,probeWrites
WriteInt output,probeInvalid
WriteInt output,probeColourErrors
WriteInt output,probeLocks
WriteInt output,probeBuffer
