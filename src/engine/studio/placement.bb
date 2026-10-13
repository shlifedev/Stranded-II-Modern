; A private model copy: no map record, ID, script, collision or save state.
Global studio_place_h,studio_place_class,studio_place_type
Global studio_place_x#,studio_place_y#,studio_place_z#

Function studio_placement_clear()
 If studio_place_h<>0 Then FreeEntity studio_place_h
 studio_place_h=0:studio_place_class=0:studio_place_type=0
End Function

Function studio_placement_valid(class,typ)
 If typ<1 Then Return 0
 Select class
  Case Cclass_object
   If typ>Cobject_count Then Return 0
   Return Len(Dobject_name$(typ))>0
  Case Cclass_unit
   If typ>Cunit_count Then Return 0
   Return Len(Dunit_name$(typ))>0
  Case Cclass_item
   If typ>Citem_count Then Return 0
   Return Len(Ditem_name$(typ))>0
  Case Cclass_info
   If typ>Cinfo_count Then Return 0
   Return Len(Dinfo_name$(typ))>0
 End Select
 Return 0
End Function

Function studio_placement_style(h)
 EntityPickMode h,0
 Local kind$=EntityClass$(h),j
 If kind="Mesh" Or kind="MD2" Or kind="Sprite" Then
  ; Entity overrides leave the cached source mesh/material untouched.
  EntityColor h,40,255,100
  EntityAlpha h,0.4
  EntityBlend h,1
  EntityFX h,17
  EntityShininess h,0
 EndIf
 For j=1 To CountChildren(h)
  studio_placement_style(GetChild(h,j))
 Next
End Function

Function studio_placement_model(class,typ)
 If studio_place_class=class And studio_place_type=typ Then Return
 studio_placement_clear()
 studio_place_class=class:studio_place_type=typ
 Select class
  Case Cclass_object
   load_object_model(typ)
   If Dobject_modelh(typ)=0 Then Return
   studio_place_h=CopyEntity(Dobject_modelh(typ))
   ScaleEntity studio_place_h,Dobject_size(typ,0),Dobject_size(typ,1),Dobject_size(typ,2)
  Case Cclass_unit
   If Dunit_modelh(typ)=0 Then Return
   studio_place_h=CopyEntity(Dunit_modelh(typ))
   ScaleEntity studio_place_h,Dunit_size(typ,0),Dunit_size(typ,1),Dunit_size(typ,2)
   Animate studio_place_h,2,Dunit_ani_move(typ,0),Dunit_ani_move(typ,1)
  Case Cclass_item
   load_item_model(typ)
   If Ditem_modelh(typ)<>0 Then
    studio_place_h=CopyEntity(Ditem_modelh(typ))
   Else
    studio_place_h=CopyEntity(game_defaultitemmodel)
   EndIf
   ScaleEntity studio_place_h,Ditem_size(typ,0),Ditem_size(typ,1),Ditem_size(typ,2)
  Case Cclass_info
   studio_place_h=CreateCube()
   ScaleEntity studio_place_h,5,5,5
 End Select
 If studio_place_h<>0 Then
  EntityType studio_place_h,0,1
  studio_placement_style(studio_place_h)
  HideEntity studio_place_h
 EndIf
End Function

; Both hover and click sample the same ray, snapped X/Z and final ground Y.
; Unit map Y describes its collision pivot, above the visual model origin.
Function studio_placement_sample(class,typ,x#,y#,snap#)
 If studio_placement_valid(class,typ)=0 Then Return 0
 If studio_terrain_pick(x,y)=0 Then Return 0
 studio_place_x=PickedX():studio_place_z=PickedZ()
 If snap>0 Then
  studio_place_x=Floor(studio_place_x/snap+0.5)*snap
  studio_place_z=Floor(studio_place_z/snap+0.5)*snap
 EndIf
 If Abs(studio_place_x)>Float(ter_size)*32 Or Abs(studio_place_z)>Float(ter_size)*32 Then Return 0
 studio_place_y=e_tery(studio_place_x,studio_place_z)
 If class=Cclass_object Then
  If Dobject_align(typ)=1 And studio_place_y<1 Then studio_place_y=1
 ElseIf class=Cclass_unit Then
  If Dunit_align(typ)=1 Then studio_place_y=1
  studio_place_y=studio_place_y+Dunit_colyr(typ)
 EndIf
 Return 1
End Function

Function studio_placement_update()
 Local state$=web_service("studio-hover")
 If state="" Or studio_playing Or studio_loaded=0 Then
  If studio_place_h<>0 Then HideEntity studio_place_h
  Return
 EndIf
 Local class=Int(studio_arg(state,0)),typ=Int(studio_arg(state,1))
 If studio_placement_sample(class,typ,Float(studio_arg(state,2)),Float(studio_arg(state,3)),Float(studio_arg(state,4)))=0 Then
  If studio_place_h<>0 Then HideEntity studio_place_h
  Return
 EndIf
 studio_placement_model(class,typ)
 If studio_place_h=0 Then Return
 Local y#=studio_place_y
 If class=Cclass_unit Then y=y-Dunit_colyr(typ)
 PositionEntity studio_place_h,studio_place_x,y,studio_place_z
 RotateEntity studio_place_h,0,0,0
 If class=Cclass_object Then
  If Dobject_align(typ)=2 Then RotateEntity studio_place_h,groundpitch(studio_place_h,60),0,0
 EndIf
 ShowEntity studio_place_h
End Function
