; Native GPU smoke: a lit mesh, 2D overlay and GPU readback.
AppTitle "Stranded II - native Metal verification"
Graphics3D 640,480,32,2
SetBuffer BackBuffer()
camera=CreateCamera()
CameraClsColor camera,24,36,54
PositionEntity camera,0,0,-5
light=CreateLight()
RotateEntity light,25,-35,0
cube=CreateCube()
EntityColor cube,240,170,50
RotateEntity cube,25,40,10
For frame=1 To 4
 RenderWorld
 Color 80,210,190
 Rect 24,24,180,12,True
 Flip
Next
RenderWorld
Color 80,210,190
Rect 24,24,180,12,True
LockBuffer BackBuffer()
background=ReadPixelFast(2,2,BackBuffer()) And $FFFFFF
center=ReadPixelFast(320,240,BackBuffer()) And $FFFFFF
overlay=ReadPixelFast(30,30,BackBuffer()) And $FFFFFF
UnlockBuffer BackBuffer()
If center=background Then RuntimeError "Native mesh did not render"
If overlay<>$50D2BE Then RuntimeError "Native 2D/readback mismatch"
If SaveBuffer(BackBuffer(),"native-metal.bmp")=0 Then RuntimeError "Native screenshot failed"
output=WriteFile("native-metal.txt")
WriteLine output,"background="+background
WriteLine output,"center="+center
WriteLine output,"overlay="+overlay
CloseFile output
FreeEntity cube
FreeEntity light
FreeEntity camera
EndGraphics
End
