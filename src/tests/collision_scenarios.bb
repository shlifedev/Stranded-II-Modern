; Runs inside the actual engine, with disposable geometry and no GUI input.
Global probe_report=WriteFile(set_rootdir$+"collision-smoke.txt"),probe_failures
Global probe_elapsed#,probe_dt#,probe_floor_failures,probe_rejections
Global probe_case$,probe_origin_x#,probe_origin_y#,probe_origin_z#

Function probe_check(ok,label$)
 WriteLine probe_report,"CHECK "+probe_case$+" "+label$
 If Not ok Then
  probe_failures=probe_failures+1:WriteLine probe_report,"FAIL "+probe_case$+" "+label$
  WriteLine probe_report,"DETAIL xyz="+EntityX(g_cplayer\h)+","+EntityY(g_cplayer\h)+","+EntityZ(g_cplayer\h)+" origin="+probe_origin_x#+","+probe_origin_y#+" ground="+col_player_grounded+" head="+col_player_ceiling+" rejected="+probe_rejections+" jump="+g_cplayer\phy_jump#+" jumptime="+game_jumptime+" gt="+gt
  WriteLine probe_report,"DETAIL floor="+e_tery(EntityX(g_cplayer\h),EntityZ(g_cplayer\h))+" ry="+Dunit_colyr#(g_cplayer\typ)+" managed="+col_player_managed(g_cplayer)+" contacts="+CountCollisions(g_cplayer\h)+" req="+col_player_request_x#+","+col_player_request_y#+","+col_player_request_z#
  For ci=1 To CountCollisions(g_cplayer\h)
   WriteLine probe_report,"CONTACT xyz="+CollisionX(g_cplayer\h,ci)+","+CollisionY(g_cplayer\h,ci)+","+CollisionZ(g_cplayer\h,ci)+" n="+CollisionNX(g_cplayer\h,ci)+","+CollisionNY(g_cplayer\h,ci)+","+CollisionNZ(g_cplayer\h,ci)
   cs=CollisionSurface(g_cplayer\h,ci):ctri=CollisionTriangle(g_cplayer\h,ci)
   If cs<>0 Then
    For cv=0 To 2
     cvi=TriangleVertex(cs,ctri,cv)
     WriteLine probe_report,"VERTEX "+cvi+" "+VertexX(cs,cvi)+","+VertexY(cs,cvi)+","+VertexZ(cs,cvi)
    Next
   EndIf
  Next
 EndIf
 CloseFile probe_report
 probe_report=OpenFile(set_rootdir$+"collision-smoke.txt")
 SeekFile probe_report,FileSize(set_rootdir$+"collision-smoke.txt")
End Function

Function probe_scene(slope#,height#=256.)
 e_clear()
 m_section=Csection_game_sp:m_menu=0:g_unpaused=1:g_drive=0
 ter=CreateTerrain(64)
 ScaleEntity ter,64,3200,64
 PositionEntity ter,-2048,-1600,-2048
 ter_groundy#=-1500.
 For xx=0 To 64
  For zz=0 To 64
   ModifyTerrain ter,xx,zz,(height#+slope#*(xx*64.-2048.)+1600.)/3200.
  Next
 Next
 tmp_loading=1
 set_unit(1,1,-200,0)
 tmp_loading=0
 game_setplayer(1)
 probe_place(-200,0)
 probe_elapsed#=1000.:gt=1000:ms=1000
 g_cplayer\phy_ft=gt:g_cplayer\phy_fall=gt:g_cplayer\phy_jump#=0.
 probe_floor_failures=0:probe_rejections=0
End Function

Function probe_place(x#,z#,above#=0.)
 pos_unit(g_cplayer,x#,e_tery(x#,z#)+Dunit_colyr#(g_cplayer\typ)+above#,z#)
 ResetEntity g_cplayer\h
 col_player_prepare():UpdateWorld(0):col_player_finish()
End Function

Function probe_tick(dx#=0.,dz#=0.)
 f#=probe_dt#/20.
 probe_elapsed#=probe_elapsed#+probe_dt#:gt=Int(probe_elapsed#):ms=gt
 TCunit=g_cplayer
 update_unit()
 If dx#<>0. Then game_move(Abs(dx#),180.*(dx#<0.))
 If dz#<>0. Then game_move(Abs(dz#),90.+180.*(dz#<0.))
 col_player_prepare():UpdateWorld(f#):col_player_finish()
 If EntityY(g_cplayer\h)<e_tery(EntityX(g_cplayer\h),EntityZ(g_cplayer\h))+17.-0.051 Then probe_floor_failures=probe_floor_failures+1
 If col_player_rejected Then probe_rejections=probe_rejections+1
End Function

Function probe_walk(duration#,dx#,dz#=0.)
 Local steps=Int(Floor(duration#/probe_dt#+0.5))
 For stepindex=1 To steps
  probe_tick(dx#,dz#)
 Next
End Function

Function probe_box(x#,y#,z#,sx#,sy#,sz#)
 Local box=CreateCube()
 ScaleEntity box,sx#,sy#,sz#
 PositionEntity box,x#,y#,z#
 EntityType box,Cworld_col
 ResetEntity box
 ; Register with map cleanup, just like real scenery.
 Local obj.Tobject=New Tobject
 obj\h=box:obj\typ=63
 Return box
End Function

; The unmodified official rock contains repeated-vertex triangles. Sanitizing
; a fresh load must remove them without changing vertices or changing again.
probe_case$="mesh sanitation"
probe_scene(0.)
probe_raw=load_mesh(Dobject_model$(67))
probe_vertices=0:probe_triangles=0
For probe_surface=1 To CountSurfaces(probe_raw)
 probe_vertices=probe_vertices+CountVertices(GetSurface(probe_raw,probe_surface))
 probe_triangles=probe_triangles+CountTriangles(GetSurface(probe_raw,probe_surface))
Next
probe_removed=col_prepare_mesh(probe_raw)
probe_check(probe_removed>0,"official rock04 has zero-area faces")
WriteLine probe_report,"REMOVED rock04 zero-area triangles="+probe_removed
probe_aftervertices=0:probe_aftertriangles=0
For probe_surface=1 To CountSurfaces(probe_raw)
 probe_aftervertices=probe_aftervertices+CountVertices(GetSurface(probe_raw,probe_surface))
 probe_aftertriangles=probe_aftertriangles+CountTriangles(GetSurface(probe_raw,probe_surface))
Next
probe_check(probe_vertices=probe_aftervertices And probe_triangles-probe_removed=probe_aftertriangles,"sanitation preserves vertices and valid triangle count")
probe_check(col_prepare_mesh(probe_raw)=0,"sanitation is idempotent")
FreeEntity probe_raw

For probe_rate=0 To 3
 Select probe_rate
  Case 0 probe_dt#=1000./60.
  Case 1 probe_dt#=1000./120.
  Case 2 probe_dt#=1000./144.
  Case 3 probe_dt#=100.
 End Select
 probe_case$="dt="+probe_dt#
 probe_scene(0.)
 probe_walk(2000.,1.6)
 probe_check(Abs(EntityX(g_cplayer\h)+40.)<0.1,"flat speed remains 80 units/second")
 probe_check(probe_floor_failures=0,"flat terrain floor")

 ; A steep hill meets an overhanging obstacle: lifting must not enter it.
 probe_scene(0.65)
 probe_box(0,276,0,32,10,80)
 probe_walk(4000.,1.6)
 probe_origin_x#=EntityX(g_cplayer\h)
 probe_origin_y#=EntityY(g_cplayer\h)
 probe_walk(500.,0.)
 probe_check(Abs(EntityX(g_cplayer\h)-probe_origin_x#)<0.1,"contact has no horizontal idle push")
 probe_check(Abs(EntityY(g_cplayer\h)-probe_origin_y#)<0.1,"contact has no vertical idle oscillation")
 probe_walk(2000.,-1.6)
 probe_check(EntityX(g_cplayer\h)<probe_origin_x#-100.,"retreat from slope/overhang")
 probe_check(probe_floor_failures=0,"slope never commits underground position")

 probe_scene(0.4,768.)
 probe_box(0,790,20,150,80,10)
 probe_box(0,790,-20,150,80,10)
 probe_place(-180,0)
 probe_walk(3000.,1.2,1.2)
 probe_check(EntityX(g_cplayer\h)>-30.,"slide along narrow corridor")
 probe_origin_x#=EntityX(g_cplayer\h)
 probe_walk(2000.,-1.6)
 probe_check(EntityX(g_cplayer\h)<probe_origin_x#-100.,"retreat between two obstacles")
 probe_check(probe_floor_failures=0,"corridor stays above terrain")

 ; Original rock models/scales, several approach directions on a hill.
 For rocktyp=63 To 69
  For approach=0 To 2
   probe_scene(0.5,768.)
   tmp_loading=1:set_object(-1,rocktyp,0,0,approach*45):tmp_loading=0
   ResetEntity TCobject\h
   probe_extent#=(MeshWidth(TCobject\h)+MeshDepth(TCobject\h))*Dobject_size#(rocktyp,0)+100.
   probe_place(-probe_extent#,0.)
   probe_hit=0
   For probe_attempt=1 To Int((probe_extent#+100.)/(1.6*probe_dt#/20.))
    probe_tick(1.6)
    If CountCollisions(g_cplayer\h)>0 Or col_player_rejected Then probe_hit=1:Exit
   Next
   probe_check(probe_hit,"rock "+rocktyp+" approach "+approach+" reaches surface")
   probe_walk(500.,1.6)
   ; Allow an airborne approach to finish landing before testing rest.
   probe_walk(500.,0.)
   probe_origin_x#=EntityX(g_cplayer\h)
   probe_origin_z#=EntityZ(g_cplayer\h)
   probe_wasgrounded=col_player_grounded
   probe_walk(500.,0.)
   If probe_wasgrounded Then probe_check(Abs(EntityX(g_cplayer\h)-probe_origin_x#)+Abs(EntityZ(g_cplayer\h)-probe_origin_z#)<0.1,"rock "+rocktyp+" stable supported idle")
   probe_origin_x#=EntityX(g_cplayer\h)
   probe_walk(2000.,-1.6)
   probe_check(EntityX(g_cplayer\h)<probe_origin_x#-80.,"rock "+rocktyp+" approach "+approach+" retreat")
   probe_check(probe_floor_failures=0,"rock "+rocktyp+" terrain constraint")
  Next
 Next

 ; A side wall cannot count as a floor or cancel upward movement.
 probe_scene(0.)
 probe_box(0,340,0,20,100,80)
 probe_place(-28.01,0,60.)
 probe_walk(200.,1.6)
 probe_check(col_player_grounded=0,"airborne wall is not ground")
 probe_check(gt-g_cplayer\phy_ft>100,"wall does not refresh grounded timer")
 probe_place(-28.01,0)
 probe_walk(100.,1.6)
 probe_check(col_jump(),"side wall permits jump")
 g_cplayer\phy_jump#=gt+game_jumptime
 probe_origin_y#=EntityY(g_cplayer\h)
 probe_walk(100.,1.6)
 probe_check(EntityY(g_cplayer\h)>probe_origin_y#+2.,"wall does not cancel jump ascent")

 ; Object support remains stable across many frames, including long frames.
 probe_scene(0.)
 probe_box(-100,280,0,60,10,60)
 probe_place(-100,0,90.)
 probe_walk(4000.,0.)
 probe_check(Abs(EntityY(g_cplayer\h)-307.)<0.1 And col_player_grounded,"land on object top")
 probe_origin_y#=EntityY(g_cplayer\h)
 probe_walk(1000.,0.)
 probe_check(Abs(EntityY(g_cplayer\h)-probe_origin_y#)<0.1,"object floor remains stable at rest")

 probe_scene(0.)
 probe_box(-100,307,0,60,5,60)
 probe_place(-100,0)
 g_cplayer\phy_jump#=gt+game_jumptime
 probe_walk(250.,0.)
 probe_check(g_cplayer\phy_jump#<=gt,"ceiling cancels ascent")
 probe_check(EntityY(g_cplayer\h)<=285.1,"ceiling is not penetrated")

 ; Clearing history on intentional reposition prevents stale rollback.
 probe_scene(0.5)
 probe_walk(100.,1.6)
 probe_place(500,0)
 probe_walk(100.,-1.6)
 probe_check(EntityX(g_cplayer\h)>480.,"teleport clears old verified position")
 game_setplayer(1)
 probe_check(col_player_valid=0,"player selection clears contact history")

 probe_scene(0.1,-50.)
 probe_place(-100,0,25.)
 probe_origin_y#=EntityY(g_cplayer\h)
 probe_walk(1000.,0.)
 probe_check(Abs(EntityY(g_cplayer\h)-probe_origin_y#)<0.1,"swimming has no land gravity")
Next

probe_case$="control lifecycle"
probe_scene(0.)
probe_place(-100,0)
probe_dt#=1000./144.
probe_tick()
probe_check(GetEntityType(g_cplayer\h)=Cworld_playercol,"controlled player uses full sliding")
tmp_loading=1:set_unit(2,1,300,0):tmp_loading=0
probe_check(GetEntityType(TCunit\h)=Cworld_unitcol,"other units retain original collision response")
g_drive=2:col_player_prepare()
probe_check(col_player_valid=0 And GetEntityType(g_cplayer\h)=Cworld_unitcol,"vehicle entry restores unit response and clears history")
g_drive=0:probe_tick()
g_cplayer\freeze=1:col_player_prepare()
probe_check(col_player_valid=0 And GetEntityType(g_cplayer\h)=Cworld_unitcol,"freeze clears pending contact history")
g_cplayer\freeze=0:probe_tick()
in_tpforce=3:col_player_prepare()
probe_check(col_player_valid=0,"script teleport clears verified position")
in_tpforce=0:probe_tick()

probe_projectile=CreatePivot()
EntityRadius probe_projectile,3
EntityType probe_projectile,Cworld_procol
PositionEntity probe_projectile,-150,EntityY(g_cplayer\h),0
ResetEntity probe_projectile
PositionEntity probe_projectile,-90,EntityY(g_cplayer\h),0
col_player_prepare():UpdateWorld(0):col_player_finish()
probe_check(EntityCollided(probe_projectile,Cworld_playercol)=g_cplayer\h,"projectiles still collide with player")
FreeEntity probe_projectile

WriteLine probe_report,"FAILURES "+probe_failures
CloseFile probe_report
e_clear()
EndGraphics()
End
