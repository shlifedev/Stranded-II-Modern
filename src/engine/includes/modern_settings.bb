Global modern_saved_fps,modern_saved_borderless,modern_saved_width,modern_saved_height,modern_saved_native
Global modern_pending_fps,modern_pending_borderless,modern_pending_width,modern_pending_height,modern_pending_native
Global modern_mode_count
Global modern_fitted_width,modern_fitted_height
Dim modern_modes_w(255),modern_modes_h(255)

; Legacy Direct3D devices can reject a render target above 2048 pixels.
; Keep the aspect ratio where the original minimum 800x600 UI permits it.
Function modern_fit_resolution(width,height)
 If width<800 Or height<600 Or width>8192 Or height>8192 Then width=1280:height=720
 Local scale#=1.
 If width>2048 Then scale#=2048./Float(width)
 If Float(height)*scale#>2048. Then scale#=2048./Float(height)
 modern_fitted_width=Floor(Float(width)*scale#)
 modern_fitted_height=Floor(Float(height)*scale#)
 If modern_fitted_width<800 Then modern_fitted_width=800
 If modern_fitted_height<600 Then modern_fitted_height=600
End Function

Function modern_resolve_resolution()
 If modern_native Then
  modern_width=modern_win_metric(0):modern_height=modern_win_metric(1)
 EndIf
 modern_fit_resolution(modern_width,modern_height)
 modern_width=modern_fitted_width:modern_height=modern_fitted_height
End Function

Function modern_capture_settings()
 modern_saved_fps=modern_fps:modern_saved_borderless=modern_borderless
 modern_saved_width=modern_width:modern_saved_height=modern_height:modern_saved_native=modern_native
 modern_settings_reset()
End Function

Function modern_settings_reset()
 modern_pending_fps=modern_saved_fps:modern_pending_borderless=modern_saved_borderless
 modern_pending_width=modern_saved_width:modern_pending_height=modern_saved_height:modern_pending_native=modern_saved_native
End Function

Function modern_add_mode(width,height)
 If width<800 Or height<600 Or width>2048 Or height>2048 Then Return
 Local i
 For i=1 To modern_mode_count
  If modern_modes_w(i)=width And modern_modes_h(i)=height Then Return
 Next
 If modern_mode_count=255 Then Return
 modern_mode_count=modern_mode_count+1
 modern_modes_w(modern_mode_count)=width:modern_modes_h(modern_mode_count)=height
End Function

Function modern_modes_init()
 modern_mode_count=0
 modern_add_mode(800,600):modern_add_mode(1280,720)
 modern_add_mode(modern_pending_width,modern_pending_height)
 Local i
 For i=1 To CountGfxModes3D()
  modern_add_mode(GfxModeWidth(i),GfxModeHeight(i))
 Next
 modern_add_mode(modern_win_metric(0),modern_win_metric(1))
End Function

Function modern_cycle_resolution(direction)
 Local current,i
 If modern_pending_native=0 Then
  For i=1 To modern_mode_count
   If modern_modes_w(i)=modern_pending_width And modern_modes_h(i)=modern_pending_height Then current=i:Exit
  Next
 EndIf
 current=current+direction
 If current<0 Then current=modern_mode_count
 If current>modern_mode_count Then current=0
 modern_pending_native=(current=0)
 If current>0 Then modern_pending_width=modern_modes_w(current):modern_pending_height=modern_modes_h(current)
End Function

Function modern_options_save()
 Local path$=set_rootdir$+"core\modern.ini",stream=ReadFile(path$),line$,eq,key$,content$
 If stream Then
  While Not Eof(stream)
   line$=ReadLine(stream):eq=Instr(line$,"="):key$=""
   If eq>0 Then key$=Lower(Trim(Left(line$,eq-1)))
   Select key$
    Case "fps","display","resolution","render_width","render_height","language"
    Default:content$=content$+line$+Chr(13)+Chr(10)
   End Select
  Wend
  CloseFile stream
 EndIf
 stream=WriteFile(path$+".tmp")
 If stream=0 Then Return False
 WriteLine stream,content$+"fps="+modern_pending_fps
 If modern_pending_borderless Then WriteLine stream,"display=borderless" Else WriteLine stream,"display=windowed"
 If modern_pending_native Then WriteLine stream,"resolution=desktop" Else WriteLine stream,"resolution=custom"
 WriteLine stream,"render_width="+modern_pending_width
 WriteLine stream,"render_height="+modern_pending_height
 WriteLine stream,"language="+loc_pending$
 CloseFile stream
 If modern_commit_file(path$,path$+".tmp")=0 Then Return False
 modern_saved_fps=modern_pending_fps:modern_saved_borderless=modern_pending_borderless
 modern_saved_width=modern_pending_width:modern_saved_height=modern_pending_height:modern_saved_native=modern_pending_native
 loc_saved$=loc_pending$
 Return True
End Function

; Compact selectors fit the original 800x600 options layout.
; Left click advances, right click selects the previous value.
Function modern_choice(x,y,width,txt$)
 Local over=(in_mx>=x And in_mx<x+width And in_my>=y And in_my<y+20)
 Color 20,20,20:Rect x,y,width,20,1
 Color 150,150,150:Rect x,y,width,20,0
 bmpf_txt(x+5,y+2,bmpf_trim(txt$,over,width-28),over)
 bmpf_txt(x+width-15,y+2,">",over)
 If over Then
  If in_mrelease(1) Then play(sfx_click):flushinput():Return 1
  If in_mrelease(2) Then play(sfx_click):flushinput():Return -1
 EndIf
 Return 0
End Function

Function modern_options_draw()
 Local direction,label$,index
 bmpf_txt(236,59,"[[loc:modern.resolution]]")
 label$=modern_pending_width+"x"+modern_pending_height
 If modern_pending_native Then
  modern_fit_resolution(modern_win_metric(0),modern_win_metric(1))
  label$=loc_resolve("[[loc:modern.desktop]]")+" ("+modern_fitted_width+"x"+modern_fitted_height+")"
 EndIf
 direction=modern_choice(436,55,340,label$)
 If direction Then modern_cycle_resolution(direction)
 bmpf_txt(236,81,"[[loc:modern.display]]")
 label$="[[loc:modern.windowed]]"
 If modern_pending_borderless Then label$="[[loc:modern.borderless]]"
 If modern_choice(436,77,340,label$) Then modern_pending_borderless=1-modern_pending_borderless
 bmpf_txt(236,103,"[[loc:modern.fps]]")
 direction=modern_choice(436,99,340,modern_pending_fps+" FPS")
 If direction Then
  Select modern_pending_fps
   Case 60:index=0
   Case 120:index=1
   Default:index=2
  End Select
  index=(index+direction+3) Mod 3
  Select index
   Case 0:modern_pending_fps=60
   Case 1:modern_pending_fps=120
   Case 2:modern_pending_fps=144
  End Select
 EndIf
End Function
