; Real browser WebGPU timestamps and draws, with no game assets or player data.
Graphics3D 640,480,32,2
SetBuffer BackBuffer()
Global camera=CreateCamera(),sun=CreateLight(1),cube=CreateCube()
CameraRange camera,1,100
PositionEntity cube,0,0,6
RotateEntity sun,45,20,0
DebugUiProfileRecording 1
DebugUiProfileContext "fixture=browser resolution=640x480 shadows=1",0.
DebugUiProfileBoundary()
For frame=1 To 36
 If frame=36 Then DebugUiProfileRecording 0
 DebugUiProfileBegin 4
 RenderWorldShadows camera,sun,30
 DebugUiProfileEnd 4
 DebugUiProfileBegin 7
 RenderWorld()
 DebugUiProfileEnd 7
 Flip False
 DebugUiProfileBoundary()
Next
; Allow pending readbacks to finish without recording additional frames.
For frame=1 To 8:Flip False:Next
WriteStdout DebugUiProfileSnapshot()+Chr(10)+"PROFILER_FIXTURE_DONE"+Chr(10)
DebugUiBenchmarkBegin "browser trial","fixture=browser shadows=0",0,1,80
For frame=1 To 1000
 DebugUiProfileBegin 7:RenderWorld():DebugUiProfileEnd 7
 Flip False:DebugUiProfileBoundary()
 If DebugUiBenchmarkState()=5 Then Exit
Next
For frame=1 To 8:Flip False:Next
WriteStdout "BENCHMARK_REPORT "+DebugUiBenchmarkReport()+Chr(10)
DebugUiBenchmarkExport "",1
DebugUiBenchmarkBegin "cancelled","fixture=browser",1,1,8000
For frame=1 To 3:RenderWorld():Flip False:DebugUiProfileBoundary():Next
DebugUiProfileInvalidate()
WriteStdout "BENCHMARK_CANCEL "+DebugUiBenchmarkReport()+Chr(10)+"BENCHMARK_FIXTURE_DONE"+Chr(10)
EndGraphics()
End
