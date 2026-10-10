;############################################ Collisions

;Set Collisions
col_set()
Function col_set()
	Collisions Cworld_unitcol,Cworld_col,2,3		;UNITS -> WORLD (Radius->Polygon, slide)
	Collisions Cworld_playercol,Cworld_col,2,2	;PLAYER -> WORLD (allow vertical wall sliding)
	;Collisions Cworld_unitcol,Cworld_unitcol,1,3	;UNITS -> UNITS (Radius->Radius, slide)
	Collisions Cworld_procol,Cworld_col,2,3			;PROJECTILES -> WORLD (Radius->Polygon, slide)
	Collisions Cworld_procol,Cworld_unitcol,1,3		;PROJECTILES -> UNITS (Radius->Radius, slide)
	Collisions Cworld_procol,Cworld_playercol,1,3	;PROJECTILES -> controlled player
	Collisions Cworld_procol,Cworld_itemcol,2,1		;PROJECTILES -> ITEMS (Radius->Polygon, stop)
	Collisions Cworld_itemcol,Cworld_col,2,3		;ITEMS -> WORLD (Radius->Polygon, slide)
End Function


; Remove only zero-area triangles from a loaded static mesh. In particular,
; rock04.b3d contains faces such as (29,16,29); Blitz normalizes their zero
; normal and poisons the entire swept collision. Keep vertices, normals,
; UVs, brushes, valid triangle order and the source files unchanged.
Function col_prepare_mesh(mesh)
 Local si,surface,tri,count,a,b,c,kept,bank,removed
 Local ax#,ay#,az#,bx#,by#,bz#,nx#,ny#,nz#
 For si=1 To CountSurfaces(mesh)
  surface=GetSurface(mesh,si)
  count=CountTriangles(surface)
  If count>0 Then
   bank=CreateBank(count*12):kept=0
   For tri=0 To count-1
    a=TriangleVertex(surface,tri,0):b=TriangleVertex(surface,tri,1):c=TriangleVertex(surface,tri,2)
    ax#=VertexX(surface,b)-VertexX(surface,a):ay#=VertexY(surface,b)-VertexY(surface,a):az#=VertexZ(surface,b)-VertexZ(surface,a)
    bx#=VertexX(surface,c)-VertexX(surface,a):by#=VertexY(surface,c)-VertexY(surface,a):bz#=VertexZ(surface,c)-VertexZ(surface,a)
    nx#=ay#*bz#-az#*by#:ny#=az#*bx#-ax#*bz#:nz#=ax#*by#-ay#*bx#
    If nx#<>0. Or ny#<>0. Or nz#<>0. Then
     PokeInt bank,kept*12,a:PokeInt bank,kept*12+4,b:PokeInt bank,kept*12+8,c
     kept=kept+1
    EndIf
   Next
   If kept<count Then
    ClearSurface surface,0,1
    For tri=0 To kept-1
     AddTriangle surface,PeekInt(bank,tri*12),PeekInt(bank,tri*12+4),PeekInt(bank,tri*12+8)
    Next
    removed=removed+count-kept
   EndIf
   FreeBank bank
  EndIf
 Next
 Return removed
End Function


;### Collision Center = Bottom?
Function col_bottom(h,radius#)
	Local c=CountCollisions(h)
	Local x#,y#,z#
	x#=EntityX(h)
	y#=EntityY(h)
	z#=EntityZ(h)
	If c>0 Then
		For i=1 To c
		Next
	EndIf
End Function


; Player movement is collected after rendering and resolved at the next
; UpdateWorld. Terrain correction must be part of that same swept movement.
Const Cplayer_contact_epsilon#=0.05
Global col_player_h,col_player_valid,col_player_grounded,col_player_ceiling,col_player_support
Global col_player_x#,col_player_y#,col_player_z#
Global col_player_request_x#,col_player_request_y#,col_player_request_z#,col_player_lift#
Global col_player_log_time,col_player_rejected
Global col_player_contact_log$

Function col_player_reset()
 ; EntityType resets Blitz's previous transform, so change it only when
 ; entering/leaving player control, never around every collision update.
 If col_player_h<>0 Then
  For unit.Tunit=Each Tunit
   If unit\h=col_player_h Then
    If GetEntityType(unit\h)=Cworld_playercol Then EntityType unit\h,Cworld_unitcol
    Exit
   EndIf
  Next
 EndIf
 col_player_h=0
 col_player_valid=0
 col_player_grounded=0
 col_player_ceiling=0
 col_player_support=0
 col_player_rejected=0
 col_player_contact_log$=""
End Function

Function col_player_managed(unit.Tunit)
 If unit=Null Then Return 0
 If unit<>g_cplayer Then Return 0
 If g_drive<>0 Or in_tpforce>0 Then Return 0
 If unit\health#<=0. Or unit\freeze<>0 Then Return 0
 If Dunit_behaviour(unit\typ)<>0 Then Return 0
 If Dunit_col(unit\typ)<>Cworld_unitcol Then Return 0
 If GetEntityType(unit\h)<>Cworld_unitcol And GetEntityType(unit\h)<>Cworld_playercol Then Return 0
 Return 1
End Function

Function col_player_prepare()
 If Not col_player_managed(g_cplayer) Then
  col_player_reset()
  Return
 EndIf
 Local h=g_cplayer\h
 If col_player_h<>h Then
  col_player_reset()
  col_player_h=h
 EndIf
 If GetEntityType(h)<>Cworld_playercol Then EntityType h,Cworld_playercol
 col_player_request_x#=EntityX(h)
 col_player_request_y#=EntityY(h)
 col_player_request_z#=EntityZ(h)
 Local floor#=e_tery(EntityX(h),EntityZ(h))+Dunit_colyr#(g_cplayer\typ)
 col_player_lift#=0.
 If EntityY(h)<floor# Then
  col_player_lift#=floor#-EntityY(h)
  PositionEntity h,EntityX(h),floor#,EntityZ(h)
 EndIf
End Function

; Retain only contacts that still touch the final ellipsoid, rather than
; contacts passed earlier during a slide. Blitz stores normals in the
; vertically scaled sphere space; transform them back before measuring gaps.
Function col_player_contacts()
 Local h=g_cplayer\h,typ=g_cplayer\typ
 Local rx#=Dunit_colxr#(typ),ry#=Dunit_colyr#(typ)
 Local i,nx#,ny#,nz#,length#,gap#,radius#,cy#
 col_player_ceiling=0
 col_player_support=0
 col_player_contact_log$=""
 For i=1 To CountCollisions(h)
  If GetEntityType(CollisionEntity(h,i))=Cworld_col Then
   If modern_diagnostics And ms-col_player_log_time>=250 And i<=4 Then
    col_player_contact_log$=col_player_contact_log$+" object="+get_object(CollisionEntity(h,i))+" point="+CollisionX(h,i)+","+CollisionY(h,i)+","+CollisionZ(h,i)+" normal="+CollisionNX(h,i)+","+CollisionNY(h,i)+","+CollisionNZ(h,i)
   EndIf
   nx#=CollisionNX(h,i):ny#=CollisionNY(h,i)*rx#/ry#:nz#=CollisionNZ(h,i)
   length#=Sqr(nx#*nx#+ny#*ny#+nz#*nz#)
   If length#>0.00001 Then
    nx#=nx#/length#:ny#=ny#/length#:nz#=nz#/length#
    radius#=Sqr((rx#*nx#)^2+(ry#*ny#)^2+(rx#*nz#)^2)
    cy#=CollisionY(h,i)
    gap#=(EntityX(h)-CollisionX(h,i))*nx#+(EntityY(h)-cy#)*ny#+(EntityZ(h)-CollisionZ(h,i))*nz#-radius#
    If Abs(gap#)<=Cplayer_contact_epsilon# Then
     If ny#>0.00001 And cy#<=EntityY(h)-ry#/1.5+Cplayer_contact_epsilon# Then
      col_player_grounded=1
      col_player_support=CollisionEntity(h,i)
     ElseIf ny#< -0.00001 And cy#>=EntityY(h)+ry#/1.5-Cplayer_contact_epsilon# Then
      col_player_ceiling=1
     EndIf
    EndIf
   EndIf
  EndIf
 Next
End Function

Function col_player_finish()
 If col_player_h=0 Then Return
 If Not col_player_managed(g_cplayer) Then
  col_player_reset()
  Return
 EndIf
 Local h=g_cplayer\h
 Local floor#=e_tery(EntityX(h),EntityZ(h))+Dunit_colyr#(g_cplayer\typ)
 Local oldground=col_player_grounded,oldsupport=col_player_support
 Local oldx#=col_player_x#,oldy#=col_player_y#,oldz#=col_player_z#
 col_player_rejected=0
 col_player_grounded=(EntityY(h)<=floor#+Cplayer_contact_epsilon#)
 col_player_contacts()
 ; The support probe must not create downhill drift while standing still.
 ; Reuse the verified resting pose only if the probe still finds support.
 If col_player_valid And oldground And col_player_grounded Then
  If col_player_request_x#=oldx# And col_player_request_z#=oldz# Then
   If col_player_request_y#<=oldy# And col_player_request_y#>=oldy#-Cplayer_contact_epsilon#*f#-0.001 Then
    If oldy#>=e_tery(oldx#,oldz#)+Dunit_colyr#(g_cplayer\typ)-Cplayer_contact_epsilon# Then
     PositionEntity h,oldx#,oldy#,oldz#
     ResetEntity h
     floor#=e_tery(oldx#,oldz#)+Dunit_colyr#(g_cplayer\typ)
    EndIf
   EndIf
  EndIf
 EndIf
 ; Never lift a resolved position into a rock. Reject this move while
 ; retaining the last verified position from which retreat is possible.
 If EntityY(h)<floor#-Cplayer_contact_epsilon# Then
  col_player_rejected=1
  If col_player_valid Then
   PositionEntity h,col_player_x#,col_player_y#,col_player_z#
   ResetEntity h
   col_player_grounded=oldground
   col_player_support=oldsupport
   col_player_ceiling=0
  Else
   ; No verified history after load/teleport: do not invent a recovery point.
   col_player_grounded=0
  EndIf
 Else
  col_player_x#=EntityX(h):col_player_y#=EntityY(h):col_player_z#=EntityZ(h)
  col_player_valid=1
 EndIf
 If modern_diagnostics Then
  If ms-col_player_log_time>=250 Then
   col_player_log_time=ms
   modern_log("COLLISION requested="+(col_player_request_x#-oldx#)+","+(col_player_request_y#-oldy#)+","+(col_player_request_z#-oldz#)+" actual="+(EntityX(h)-oldx#)+","+(EntityY(h)-oldy#)+","+(EntityZ(h)-oldz#)+" lift="+col_player_lift#+" ground="+col_player_grounded+" ceiling="+col_player_ceiling+" rejected="+col_player_rejected+col_player_contact_log$)
  EndIf
 EndIf
End Function

Function col_player_landsound()
 Local material=-1
 If get_object(col_player_support)<>-1 Then
  material=Dobject_mat(TCobject\typ)
 ElseIf get_item(col_player_support)<>-1 Then
  material=Ditem_mat(TCitem\typ)
 ElseIf col_player_support<>0 Then
  For unit.Tunit=Each Tunit
   If unit\vh=col_player_support Then material=Dunit_mat(unit\typ):Exit
  Next
 EndIf
 If material>=0 Then
  material_stepsfx(material)
 ElseIf m_menu<>Cmenu_if_movie Then
  sfx(sfx_step(Rand(0,3)))
 EndIf
 g_player_mst=ms
End Function

; Queue vertical movement using the same rates as the original land/swim
; physics. Grounding comes from the resolved frame, never from a wall hit.
Function col_player_physics()
 Local unit.Tunit=g_cplayer
 Local h=unit\h,par
 If EntityY(h)<=0.-Dunit_colyr#(unit\typ)/2. Then
  unit\phy_ft=gt:unit\phy_fall=gt:unit\phy_jump#=0.
  Return
 EndIf
 If gt-unit\phy_jump#<0. Then
  If col_player_ceiling Then
   unit\phy_jump#=0.
  Else
   TranslateEntity h,0,4.*(unit\phy_jump#-gt)/Float(game_jumptime)*f#,0
  EndIf
  unit\phy_fall=gt
  unit\phy_fally#=EntityY(h)
 Else
  If col_player_grounded Then
   If gt-unit\phy_fall>300 Then col_player_landsound()
   unit\phy_ft=gt:unit\phy_fall=gt:unit\phy_fally#=EntityY(h)
   ; A tiny downward sweep preserves contact with object floors at rest.
   TranslateEntity h,0,-Cplayer_contact_epsilon#*f#,0
  Else
   par=gt-unit\phy_fall
   If par>Cworld_facct Then par=Cworld_facct
   TranslateEntity h,0,-Cworld_g#*f#*3.*Float(par)/Float(Cworld_facct),0
  EndIf
 EndIf
End Function

; Side contacts are not ceilings and must not prohibit an otherwise valid jump.
Function col_jump()
 If col_player_managed(TCunit) Then Return Not col_player_ceiling
 Local c=CountCollisions(TCunit\h)
 Local i,h
 For i=1 To c
  h=CollisionEntity(TCunit\h,i)
  If get_object(h)<>-1 Then
   If Dobject_col(TCobject\typ)<>4 Then
    If CollisionY(TCunit\h,i)>EntityY(TCunit\h)-Dunit_colyr#(TCunit\typ)/1.5 Then Return 0
   EndIf
  EndIf
 Next
 Return 1
End Function