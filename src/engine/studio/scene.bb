; Scene-only changes. Native .s2 remains authoritative for play/export.
Type TstudioDrag
 Field class,id,h,x#,y#,z#,pitch#,yaw#,ground#
End Type
Global studio_dragging

Function studio_position(class,id,x#,y#,z#)
 Local h=studio_find(class,id)
 If h=0 Then Return
 If class=Cclass_unit Then
  con_unit(id):pos_unit(TCunit,x,y,z)
 Else
  PositionEntity h,x,y,z
 EndIf
 If class=Cclass_object Then
  con_object(id)
  PositionEntity h,x,e_tery(x,z),z
  If Dobject_align(TCobject\typ)=1 And EntityY(h)<1 Then PositionEntity h,x,1,z
  If Dobject_align(TCobject\typ)=2 Then RotateEntity h,groundpitch(h,60),EntityYaw(h),0
 EndIf
End Function

Function studio_drag_begin(command$)
 studio_drag_cancel()
 Local count=Int(studio_arg(command$,1)),j,class,id,h
 For j=0 To count-1
  class=Int(studio_arg(command$,2+j*2)):id=Int(studio_arg(command$,3+j*2))
  h=studio_find(class,id)
  If h<>0 Then
   d.TstudioDrag=New TstudioDrag
   d\class=class:d\id=id:d\h=h
   d\x=EntityX(h):d\y=EntityY(h):d\z=EntityZ(h)
   d\pitch=EntityPitch(h):d\yaw=EntityYaw(h):d\ground=e_tery(d\x,d\z)
  EndIf
 Next
 studio_dragging=1
End Function

Function studio_drag_move(dx#,dy#,dz#,terrain)
 If studio_dragging=0 Then Return
 For d.TstudioDrag=Each TstudioDrag
  Local y#=d\y+dy
  If terrain Then y=y+e_tery(d\x+dx,d\z+dz)-d\ground
  studio_position(d\class,d\id,d\x+dx,y,d\z+dz)
 Next
End Function

Function studio_drag_cancel()
 For d.TstudioDrag=Each TstudioDrag
  studio_position(d\class,d\id,d\x,d\y,d\z)
  RotateEntity d\h,d\pitch,d\yaw,0
  Delete d
 Next
 studio_dragging=0
End Function

Function studio_patch(token$)
 If studio_playing Or studio_loaded=0 Then Return
 Local stream=ReadFile("maps\studio.patch")
 If stream=0 Then
  web_service("studio-event","error"+Chr(31)+"Scene patch missing")
  Return
 EndIf
 If ReadInt(stream)<>1 Then
  CloseFile stream
  web_service("studio-event","error"+Chr(31)+"Scene patch version mismatch")
  Return
 EndIf
 ; Host validates and stages the complete packet at this frame boundary.
 studio_drag_cancel()
 Local oldloading=tmp_loading,count,j,class,id,typ,h,x#,y#,z#,yaw#,pit#,vars$,changed
 tmp_loading=1
 count=ReadInt(stream)
 For j=1 To count
  class=ReadByte(stream):id=ReadInt(stream)
  Select class
   Case Cclass_object:free_object(id)
   Case Cclass_unit:free_unit(id)
   Case Cclass_item:free_item(id)
   Case Cclass_info:free_info(id)
  End Select
 Next
 count=ReadInt(stream)
 For j=1 To count
  Local gx=ReadInt(stream),gz=ReadInt(stream),height#=ReadFloat(stream)
  ModifyTerrain ter,gx,gz,height
 Next
 If count>0 Then
  e_terrainground(0)
  For o.Tobject=Each Tobject
   studio_position(Cclass_object,o\id,EntityX(o\h),EntityY(o\h),EntityZ(o\h))
  Next
 EndIf
 count=ReadInt(stream)
 For j=1 To count
  class=ReadByte(stream):id=ReadInt(stream):typ=ReadInt(stream)
  h=studio_find(class,id)
  x=ReadFloat(stream)
  If class=Cclass_object Then
   z=ReadFloat(stream):yaw=ReadFloat(stream)
   If h=0 Then set_object(id,typ,x,z,yaw)
   con_object(id)
   TCobject\health=ReadFloat(stream):TCobject\health_max=ReadFloat(stream):TCobject\daytimer=ReadInt(stream)
   ScaleEntity TCobject\h,Dobject_size(typ,0),Dobject_size(typ,1),Dobject_size(typ,2)
   If TCobject\daytimer<0 Then grow_object(TCobject)
   RotateEntity TCobject\h,0,yaw,0
   studio_position(class,id,x,0,z)
  Else
   y=ReadFloat(stream):z=ReadFloat(stream)
   Select class
    Case Cclass_unit
     If h=0 Then set_unit(id,typ,x,z)
     con_unit(id):pos_unit(TCunit,x,y,z)
     yaw=ReadFloat(stream):RotateEntity TCunit\h,0,yaw,0
     TCunit\health=ReadFloat(stream):TCunit\health_max=ReadFloat(stream)
     TCunit\hunger=ReadFloat(stream):TCunit\thirst=ReadFloat(stream):TCunit\exhaustion=ReadFloat(stream)
     TCunit\ai_cx=ReadFloat(stream):TCunit\ai_cz=ReadFloat(stream)
    Case Cclass_item
     If h=0 Then set_item(id,typ,x,y,z,1,1)
     con_item(id):PositionEntity TCitem\h,x,y,z
     yaw=ReadFloat(stream):RotateEntity TCitem\h,0,yaw,0
     TCitem\health=ReadFloat(stream):TCitem\count=ReadInt(stream)
     TCitem\parent_class=ReadByte(stream):TCitem\parent_mode=ReadByte(stream):TCitem\parent_id=ReadInt(stream)
     If TCitem\parent_mode=Cpm_out And TCitem\parent_class<>0 Then RotateEntity TCitem\h,-90,yaw,0
    Case Cclass_info
     If h=0 Then set_info(id,typ,x,y,z)
     con_info(id):PositionEntity TCinfo\h,x,y,z
     pit=ReadFloat(stream):yaw=ReadFloat(stream):RotateEntity TCinfo\h,pit,yaw,0
     vars=ReadString(stream):changed=(TCinfo\vars<>vars Or h=0)
     TCinfo\vars=vars
     If changed Then
      If typ=47 Then
       If TCinfo\ints[1]<>0 Then StopChannel TCinfo\ints[1]
       If TCinfo\ints[0]<>0 Then FreeSound TCinfo\ints[0]
       TCinfo\ints[0]=0:TCinfo\ints[1]=0
      EndIf
      info_stv()
      Select typ
       Case 5:info_setupsprite(TCinfo)
       Case 47:info_setuploudspeaker(TCinfo)
      End Select
     EndIf
     RotateEntity TCinfo\h,pit,yaw,0
   End Select
  EndIf
  h=studio_find(class,id)
  If h<>0 Then EntityPickMode h,2,1
 Next
 CloseFile stream
 serialpeak_object():serialpeak_unit():serialpeak_item():serialpeak_info()
 tmp_loading=oldloading
 studio_handle=studio_find(studio_class,studio_id)
 web_service("studio-event","applied"+Chr(31)+token$)
End Function

Function studio_project$(x#,y#,z#)
 If CameraProject(cam,x,y,z)=0 Then Return "0"+Chr(31)+"0"+Chr(31)+"0"
 ; ProjectedZ is the near plane in Blitz, not the point's view depth.
 TFormPoint x,y,z,0,cam
 Return ProjectedX()+Chr(31)+ProjectedY()+Chr(31)+TFormedZ()
End Function

Function studio_projection()
 If studio_handle=0 Then Return
 Local x#=EntityX(studio_handle),y#=EntityY(studio_handle),z#=EntityZ(studio_handle)
 Local out$="gizmo"+Chr(31)+studio_project(x,y,z)
 out=out+Chr(31)+studio_project(x+64,y,z)
 out=out+Chr(31)+studio_project(x,y+64,z)
 out=out+Chr(31)+studio_project(x,y,z+64)
 web_service("studio-event",out)
End Function
