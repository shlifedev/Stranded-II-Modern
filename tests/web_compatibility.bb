; Actual engine/renderer, isolated assets and synthetic sequence time. No GUI input.
Global compat_report=WriteFile(set_rootdir$+"web-compatibility.txt"),compat_failures
Global compat_clock,compat_phase$,compat_start,compat_shipx#,compat_shipz#

Function compat_check(ok,label$)
 WriteLine compat_report,"CHECK "+label$
 If Not ok Then compat_failures=compat_failures+1:WriteLine compat_report,"FAIL "+label$
End Function

Function compat_pixel(x,y,rgb,label$)
 Local actual=ReadPixel(x,y,BackBuffer()) And $FFFFFF
 compat_check(actual=rgb,label$)
 If actual<>rgb Then WriteLine compat_report,"PIXEL "+Hex(actual)+" expected "+Hex(rgb)
End Function

Function compat_event(event$,time)
 If compat_phase$<>"" Then WriteLine compat_report,"EVENT "+compat_phase$+" "+time+" "+event$
End Function

Function compat_position(label$,entity)
 WriteLine compat_report,"POSITION "+label$+" "+EntityX(entity)+" "+EntityY(entity)+" "+EntityZ(entity)
End Function

Function compat_sound(path$)
 Local sound=LoadSound(path$)
 compat_check(sound<>0,"decode "+path$)
 If sound<>0 Then FreeSound sound
End Function

; Colour keys must work with GPU-created images as well as file-backed atlases.
Global compat_image=CreateImage(4,4)
SetBuffer ImageBuffer(compat_image):ClsColor 255,0,255:Cls
Color 255,255,255:Rect 1,1,2,2,1
MaskImage compat_image,255,0,255
SetBuffer BackBuffer():ClsColor 17,34,51:Cls
DrawImageRect compat_image,0,0,0,0,4,4
DrawBlockRect compat_image,4,0,0,0,4,4
DrawImage compat_image,8,0
DrawBlock compat_image,12,0
compat_pixel(0,0,$112233,"DrawImageRect colour key")
compat_pixel(1,1,$FFFFFF,"DrawImageRect foreground")
compat_pixel(4,0,$FF00FF,"DrawBlockRect ignores colour key")
compat_pixel(8,0,$112233,"DrawImage colour key")
compat_pixel(12,0,$FF00FF,"DrawBlock ignores colour key")
Global compat_copy=CopyImage(compat_image)
DrawImage compat_copy,16,0
compat_pixel(16,0,$112233,"CopyImage retains colour key")
MaskImage compat_image,255,255,255
DrawImage compat_image,20,0
compat_pixel(20,0,$FF00FF,"changing key restores old key pixels")
compat_pixel(21,1,$112233,"changed colour key")
LockBuffer ImageBuffer(compat_copy)
WritePixelFast 1,1,$FF00FF,ImageBuffer(compat_copy)
UnlockBuffer ImageBuffer(compat_copy)
DrawImageRect compat_copy,24,0,0,0,4,4
compat_pixel(25,1,$112233,"colour key after LockBuffer write")
; Crop a real glyph atlas: every magenta source pixel must preserve the backdrop.
Local glyphIndex=ko_index($AC00)-1
Local glyph=ko_pages(0,glyphIndex/1024),maskedPixels,inkPixels
Local glyphX=(glyphIndex Mod 32)*ko_cellw,glyphY=((glyphIndex Mod 1024)/32)*ko_cellh
Local animated=LoadAnimImage(set_rootdir$+"core\locales\ko\0_"+(glyphIndex/1024)+".bmp",ko_cellw,ko_cellh,glyphIndex Mod 1024,1)
compat_check(animated<>0,"load animated atlas frame")
If animated<>0 Then MaskImage animated,255,0,255:DrawImage animated,32,16
If glyph<>0 Then
 DrawImageRect glyph,0,16,glyphX,glyphY,ko_cellw,ko_cellh
 LockBuffer ImageBuffer(glyph):LockBuffer BackBuffer()
 For gy=0 To ko_cellh-1
  For gx=0 To ko_cellw-1
   Local sourcePixel=ReadPixelFast(gx+glyphX,gy+glyphY,ImageBuffer(glyph)) And $FFFFFF
   Local targetPixel=ReadPixelFast(gx,gy+16,BackBuffer()) And $FFFFFF
   Local animatedPixel=ReadPixelFast(gx+32,gy+16,BackBuffer()) And $FFFFFF
   If sourcePixel=$FF00FF Then
    maskedPixels=maskedPixels+1
    If targetPixel<>$112233 Then compat_failures=compat_failures+1
    If animatedPixel<>$112233 Then compat_failures=compat_failures+1
   Else
    inkPixels=inkPixels+1
    If targetPixel<>sourcePixel Then compat_failures=compat_failures+1
    If animatedPixel<>sourcePixel Then compat_failures=compat_failures+1
   EndIf
  Next
 Next
 UnlockBuffer BackBuffer():UnlockBuffer ImageBuffer(glyph)
EndIf
compat_check(glyph<>0 And maskedPixels>0 And inkPixels>0,"real Korean atlas crop")
If animated<>0 Then FreeImage animated
FreeImage compat_image:FreeImage compat_copy

; A copied frame must keep its top/bottom orientation when reused in 3D.
; This is the same BackBuffer -> TextureBuffer -> sprite path as motion blur.
HideEntity cam
Local frameCamera=CreateCamera(),frameSprite=CreateSprite(frameCamera)
Local frameTexture=CreateTexture(64,64,1+16+32+256)
CameraViewport frameCamera,0,0,64,64
CameraClsColor frameCamera,0,0,0
PositionEntity frameSprite,0,0,4:ScaleSprite frameSprite,4,4
SpriteViewMode frameSprite,2:EntityFX frameSprite,1
EntityTexture frameSprite,frameTexture
ClsColor 0,0,0:Cls
Color 255,0,0:Rect 0,0,64,32,1
Color 0,0,255:Rect 0,32,64,32,1
CopyRect 0,0,64,64,0,0,BackBuffer(),TextureBuffer(frameTexture)
RenderWorld()
compat_pixel(16,16,$FF0000,"copied texture keeps top row in 3D")
compat_pixel(16,48,$0000FF,"copied texture keeps bottom row in 3D")
; Repeated feedback must not blend a vertically mirrored previous frame.
EntityAlpha frameSprite,0.5
For frameTick=1 To 3
 CopyRect 0,0,64,64,0,0,BackBuffer(),TextureBuffer(frameTexture)
 CameraClsColor frameCamera,0,0,0:RenderWorld()
 Local topRed=(ReadPixel(16,16,BackBuffer()) Shr 16) And 255
 Local topBlue=ReadPixel(16,16,BackBuffer()) And 255
 Local bottomRed=(ReadPixel(16,48,BackBuffer()) Shr 16) And 255
 Local bottomBlue=ReadPixel(16,48,BackBuffer()) And 255
 compat_check(topRed>0 And topBlue=0 And bottomBlue>0 And bottomRed=0,"motion blur feedback orientation "+frameTick)
Next
FreeEntity frameCamera:FreeTexture frameTexture:ShowEntity cam
ClsColor 0,0,0
set_musicvolume#=0:set_fxvolume#=0
; AUDIO_CHECKS

; Menu scripts must initialise the moving camera and restart their 80 s cycle.
compat_phase$="menu"
menu_start():parse_all():seq_update()
compat_check(seq_cstart<>0 And seq_cend<>0,"menu camera path starts")
compat_check(seq_cpoint=2,"menu camera mode")
compat_position("menu-start",cam)
For compat_tick=1 To 801
 compat_clock=compat_clock+100:f#=5
 seq_update():parse_all()
Next
compat_check(seq_start>=80000,"menu sequence restarts")

; Run the original adventure script and paths with controlled 20 ms steps.
compat_phase$="intro"
m_section=Csection_game_sp:m_menu=0:g_unpaused=1
compat_check(load_map("maps\adventure\map01.s2","")=1,"load original intro map")
parse_all():seq_update()
compat_check(m_menu=Cmenu_if_movie,"intro takes camera control")
compat_check(con_unit(100),"intro ship exists")
compat_shipx#=EntityX(TCunit\h):compat_shipz#=EntityZ(TCunit\h)
compat_start=compat_clock
For compat_tick=1 To 2651
 compat_clock=compat_start+compat_tick*20:f#=1:gt=compat_tick*20
 seq_update():up_update():parse_all():modern_process_saves()
 If compat_tick=50 Then
  con_unit(100)
  compat_check(Abs(EntityX(TCunit\h)-compat_shipx#)+Abs(EntityZ(TCunit\h)-compat_shipz#)>0.01,"ship follows its original path")
 EndIf
 If compat_tick=1 Or compat_tick=451 Or compat_tick=1251 Or compat_tick=1751 Then
  compat_position("intro-"+compat_tick,cam)
  RenderWorld():Flip False
 EndIf
Next
compat_check(Lower(map_path$)="maps\adventure\map02.s2","intro loads first playable island")
compat_check(m_menu<>Cmenu_if_movie,"island returns player control")
compat_check(p_error=0,"adventure scripts complete without parser errors")
compat_position("island-player",g_cplayer\h)
compat_phase$="skip"
m_menu=0:load_map("maps\adventure\map01.s2",""):parse_all():seq_update()
parse_globalevent("skipsequence"):parse_all():modern_process_saves()
compat_check(Lower(map_path$)="maps\adventure\map02.s2","skip uses original map transition")
WriteLine compat_report,"FAILURES "+compat_failures
CloseFile compat_report
e_clear():EndGraphics():End
