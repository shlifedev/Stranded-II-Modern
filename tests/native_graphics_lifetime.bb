; A scene must release its GPU listeners before EndGraphics closes the device.
; Reopening also detects scene objects retained by a static renderer driver.
AppTitle "Stranded II - graphics lifetime verification"
For iteration=1 To 2
 Graphics3D 320,240,32,2
 SetBuffer BackBuffer()
 camera=CreateCamera()
 cube=CreateCube()
 PositionEntity cube,0,0,5
 RenderWorld
 Flip
 EndGraphics
Next
; Exercise runtime teardown with a live scene as well as explicit EndGraphics.
Graphics3D 320,240,32,2
camera=CreateCamera()
RenderWorld
Flip
End
