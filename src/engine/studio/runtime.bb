; Studio owns its frame loop; standard game/editor entrypoints are unchanged.
; Project mutations are committed by the common CLI/UI core. The engine picks,
; renders and plays those native maps, and applies transient drag previews.
Global studio_playing,studio_loaded,studio_class,studio_id,studio_handle
Global studio_firstframe
Global studio_command$,studio_path$="maps\studio.s2"
Global studio_selection$="",studio_out$

Function studio_arg$(command$,index)
 Local p=1,n=0,q
 While True
  q=Instr(command$,Chr(31),p)
  If q=0 Then q=Len(command$)+1
  If n=index Then Return Mid(command$,p,q-p)
  If q>Len(command$) Then Return ""
  p=q+1:n=n+1
 Wend
End Function

Function studio_find(class,id)
 Select class
  Case Cclass_object
   For o.Tobject=Each Tobject
    If o\id=id Then Return o\h
   Next
  Case Cclass_unit
   For u.Tunit=Each Tunit
    If u\id=id Then Return u\h
   Next
  Case Cclass_item
   For it.Titem=Each Titem
    If it\id=id Then Return it\h
   Next
  Case Cclass_info
   For inf.Tinfo=Each Tinfo
    If inf\id=id Then Return inf\h
   Next
 End Select
 Return 0
End Function

Include "studio\scene.bb"
Include "studio\placement.bb"

Function studio_load(play=0,keepcamera=0)
 studio_placement_clear()
 studio_drag_cancel()
 Local cx#=EntityX(cam),cy#=EntityY(cam),cz#=EntityZ(cam)
 Local pitch#=EntityPitch(cam),yaw#=EntityYaw(cam)
 studio_handle=0
 studio_playing=play
 studio_firstframe=0
 If play Then
  HidePointer()
  m_section=Csection_game_sp
  set_debug=1
 Else
  m_section=Csection_editor
 EndIf
 m_menu=0
 studio_loaded=load_map(studio_path$,"")
 If studio_loaded=0 Then
  studio_playing=0
  web_service("studio-event","error"+Chr(31)+"Map load failed")
  Return
 EndIf
 If play=0 Then
  m_menu=0
  in_editor_sidebar=0
  If keepcamera Then
   PositionEntity cam,cx,cy,cz
   RotateEntity cam,pitch,yaw,0
  Else
   editor_resetcam()
  EndIf
  ; All supported entity classes are pickable at once.
  For o.Tobject=Each Tobject
   EntityPickMode o\h,2,1
  Next
  For u.Tunit=Each Tunit
   EntityPickMode u\h,2,1
  Next
  For it.Titem=Each Titem
   EntityPickMode it\h,2,1
  Next
  For inf.Tinfo=Each Tinfo
   If inf\h<>0 Then EntityPickMode inf\h,2,1
  Next
  studio_handle=studio_find(studio_class,studio_id)
 EndIf
 modern_reset_clock()
 ; Present the loaded game once before waiting for mouse capture. Keep the
 ; engine and its resource definitions alive across editor/play transitions.
 If play Then blackframe=0:studio_firstframe=1
 web_service("studio-event","loaded"+Chr(31)+play+Chr(31)+ter_size)
End Function

Function studio_restore_picking()
 For po.Tobject=Each Tobject
  EntityPickMode po\h,2,1
 Next
 For pu.Tunit=Each Tunit
  EntityPickMode pu\h,2,1
  EntityPickMode pu\mh,2,1
 Next
 For pit.Titem=Each Titem
  EntityPickMode pit\h,2,1
 Next
 For pn.Tinfo=Each Tinfo
  If pn\h<>0 Then EntityPickMode pn\h,2,1
 Next
End Function

Function studio_terrain_pick(x#,y#)
 editor_picking(0)
 ; Units have a separate pickable visual model below their collision pivot.
 For pu.Tunit=Each Tunit
  EntityPickMode pu\mh,0
 Next
 CameraPick cam,x,y
 Local hit=(PickedEntity()=ter)
 studio_restore_picking()
 Return hit
End Function

Function studio_pick(x#,y#,token$,terrainonly=0,placeclass=0,placetype=0,placesnap#=0)
 If studio_loaded=0 Or studio_playing Then Return
 If placeclass>0 Then
  Local hit=studio_placement_sample(placeclass,placetype,x,y,placesnap)
  web_service("studio-event","pick"+Chr(31)+token$+Chr(31)+0+Chr(31)+0+Chr(31)+studio_place_x+Chr(31)+studio_place_y+Chr(31)+studio_place_z+Chr(31)+hit)
  Return
 EndIf
 If terrainonly Then
  studio_terrain_pick(x,y)
 Else
  CameraPick cam,x,y
 EndIf
 Local picked=PickedEntity(),class=0,id=0
 Local px#=PickedX(),py#=PickedY(),pz#=PickedZ()
 For o.Tobject=Each Tobject
  If o\h=picked Then class=Cclass_object:id=o\id:Exit
 Next
 If class=0 Then
  For u.Tunit=Each Tunit
   If u\h=picked Or u\mh=picked Then class=Cclass_unit:id=u\id:Exit
  Next
 EndIf
 If class=0 Then
  For it.Titem=Each Titem
   If it\h=picked Then class=Cclass_item:id=it\id:Exit
  Next
 EndIf
 If class=0 Then
  For inf.Tinfo=Each Tinfo
   If inf\h=picked And picked<>0 Then class=Cclass_info:id=inf\id:Exit
  Next
 EndIf
 web_service("studio-event","pick"+Chr(31)+token$+Chr(31)+class+Chr(31)+id+Chr(31)+px+Chr(31)+py+Chr(31)+pz+Chr(31)+(picked<>0))
End Function

Function studio_process(command$)
 Local op$=studio_arg(command$,0),h
 Select op$
  Case "load"
   studio_load(0,Int(studio_arg(command$,1)))
  Case "play"
   ; Capture the exact editor pose before replacing the world with gameplay.
   studio_process("pose")
   studio_load(1)
  Case "stop"
   studio_load(0)
  Case "select"
   studio_class=Int(studio_arg(command$,1))
   studio_id=Int(studio_arg(command$,2))
   studio_handle=studio_find(studio_class,studio_id)
  Case "focus"
   h=studio_find(Int(studio_arg(command$,1)),Int(studio_arg(command$,2)))
   If h<>0 Then
    PositionEntity cam,EntityX(h),EntityY(h)+120,EntityZ(h)-180
    PointEntity cam,h
   EndIf
  Case "pick"
   studio_pick(Float(studio_arg(command$,1)),Float(studio_arg(command$,2)),studio_arg(command$,3),Int(studio_arg(command$,4)),Int(studio_arg(command$,5)),Int(studio_arg(command$,6)),Float(studio_arg(command$,7)))
  Case "camera"
   If studio_loaded And studio_playing=0 Then
    RotateEntity cam,Float(studio_arg(command$,1)),Float(studio_arg(command$,2)),0
    MoveEntity cam,Float(studio_arg(command$,3))*f#,Float(studio_arg(command$,4))*f#,Float(studio_arg(command$,5))*f#
   EndIf
  Case "pose"
   web_service("studio-event","camera"+Chr(31)+EntityPitch(cam)+Chr(31)+EntityYaw(cam)+Chr(31)+EntityX(cam)+Chr(31)+EntityY(cam)+Chr(31)+EntityZ(cam))
  Case "patch"
   studio_patch(studio_arg(command$,1))
  Case "drag-begin"
   studio_drag_begin(command$)
  Case "drag-move"
   studio_drag_move(Float(studio_arg(command$,1)),Float(studio_arg(command$,2)),Float(studio_arg(command$,3)),Int(studio_arg(command$,4)))
  Case "drag-cancel"
   studio_drag_cancel()
  Case "restore-pose"
   PositionEntity cam,Float(studio_arg(command$,1)),Float(studio_arg(command$,2)),Float(studio_arg(command$,3))
   RotateEntity cam,Float(studio_arg(command$,4)),Float(studio_arg(command$,5)),0

 End Select
End Function

Function studio_render()
 g_unpaused=1
 getinput()
 ShowPointer()
 If studio_loaded=0 Then
  ClsColor 25,29,34:Cls
  Return
 EndIf
 HideEntity env_ground
 e_terrainground(0)
 UpdateWorld(f#)
 cull()
 e_environment_update_water()
 studio_placement_update()
 RenderWorld()
 mb_update()
 e_environment_water2dfx()
 grass_spread()
 e_environment_update()
 update_state()
 draw_info()
 studio_projection()
 If (gframe Mod 6)=0 Then studio_process("pose")
 p_update()
End Function

; The host supplies the immutable authoring snapshot before callMain starts.
m_section=Csection_editor
m_menu=0
editor_defaultmapsettings()
web_service("studio-event","ready")
While Not m_performquit
 studio_command$=web_service("studio-poll")
 If studio_command$<>"" Then studio_process(studio_command$)
 If studio_playing Then
  Select m_section
   Case Csection_game_sp
    game()
   Default
    menu()
  End Select
 Else
  studio_render()
 EndIf
 gframe=gframe+1
 modern_present()
 qol_present()
 If studio_firstframe Then
  studio_firstframe=0
  web_service("studio-event","playing")
 EndIf
Wend
qol_shutdown()
studio_placement_clear()
e_clear()
EndGraphics()
End
