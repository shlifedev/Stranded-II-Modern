; Optional shared renderer features. Settings are local to each edition.
Const Cremaster_water=0,Cremaster_light=1,Cremaster_shadows=2,Cremaster_air=3,Cremaster_aa=4,Cremaster_count=5
Dim remaster_enabled(Cremaster_count-1),remaster_pending(Cremaster_count-1),remaster_keys$(Cremaster_count-1),remaster_labels$(Cremaster_count-1)

Function remaster_init()
 remaster_keys(0)="render_water":remaster_labels(0)="[[loc:render.water]]"
 remaster_keys(1)="render_lighting":remaster_labels(1)="[[loc:render.lighting]]"
 remaster_keys(2)="render_shadows":remaster_labels(2)="[[loc:render.shadows]]"
 remaster_keys(3)="render_atmosphere":remaster_labels(3)="[[loc:render.atmosphere]]"
 remaster_keys(Cremaster_aa)="render_antialias":remaster_labels(Cremaster_aa)="[[loc:render.antialias]]"
 remaster_enabled(Cremaster_aa)=1
End Function

Function remaster_option(key$,value$)
 If key$=remaster_keys(Cremaster_aa) Then
  Select value$
   Case "0","1","4":remaster_enabled(Cremaster_aa)=Int(value$)
   Default:remaster_enabled(Cremaster_aa)=1
  End Select
  Return
 EndIf
 For i=0 To Cremaster_count-1
  If key$=remaster_keys(i) And (value$="0" Or value$="1") Then remaster_enabled(i)=Int(value$)
 Next
End Function

Function remaster_setting_owned(key$)
 For i=0 To Cremaster_count-1
  If key$=remaster_keys(i) Then Return True
 Next
 Return False
End Function

Function remaster_settings_reset()
 For i=0 To Cremaster_count-1:remaster_pending(i)=remaster_enabled(i):Next
End Function

Function remaster_settings_write(stream)
 For i=0 To Cremaster_count-1:WriteLine stream,remaster_keys(i)+"="+remaster_pending(i):Next
End Function

Function remaster_settings_apply()
 Local waterchanged=(remaster_enabled(0)<>remaster_pending(0))
 For i=0 To Cremaster_count-1:remaster_enabled(i)=remaster_pending(i):Next
 AntiAlias remaster_enabled(Cremaster_aa)
 If waterchanged Then
  If env_watertex<>0 Then FreeTexture env_watertex:env_watertex=0
  If env_sea<>0 Then e_environment_setup_water()
 EndIf
 If env_light<>0 Then
  If remaster_enabled(1)=0 And remaster_enabled(2)=0 Then
   RotateEntity env_light,0,0,0:LightColor env_light,50,50,50
  EndIf
 EndIf
 If env_sky<>0 Then e_environment_update_light()
End Function

Function remaster_options_nav()
 Local y=205
 If game_scriptkeys>0 Then y=255
 If gui_button(5,y,"[[loc:render.tab]]") Then menu_set(56)
End Function

Function remaster_options_draw()
 DrawImage gfx_win,215,0:DrawImage gfx_icons,236,5,Cicon_options
 bmpf_txt(273,10,"[[loc:render.tab]]"):DrawImage gfx_winbar,215,42
 Local i,label$,direction,index
 For i=0 To Cremaster_count-1
  bmpf_txt(236,66+i*62,remaster_labels(i))
  label$="[[loc:qol.off]]"
  If i=Cremaster_aa Then
   If remaster_pending(i)=1 Then label$="FXAA"
   If remaster_pending(i)=4 Then label$="MSAA 4x"
   direction=modern_choice(636,62+i*62,140,label$)
   If direction Then
    index=remaster_pending(i)
    If index=4 Then index=2
    index=(index+direction+3) Mod 3
    If index=2 Then index=4
    remaster_pending(i)=index
   EndIf
  Else
   If remaster_pending(i) Then label$="[[loc:qol.on]]"
   If modern_choice(636,62+i*62,140,label$) Then remaster_pending(i)=1-remaster_pending(i)
  EndIf
 Next
 bmpf_txt_rect(236,340,530,170,"[[loc:render.help]]",Cbmpf_tiny)
End Function

Function remaster_frame()
 RenderEffects remaster_enabled(1) Or remaster_enabled(2),remaster_enabled(3),Float(gt Mod 3600000)/1000.
 If remaster_enabled(1) Or remaster_enabled(2) Then
  Local hour#=Float(map_hour)+Float(map_minute)/60.,elevation#=Sin((hour-6.)*15.)
  Local strength#=Max(0.,elevation),pitch#=15.+Abs(elevation)*50.
  RotateEntity env_light,pitch,(-60.+(hour-12.)*8.),0
  If elevation>0. Then
   LightColor env_light,45.+strength*75.,36.+strength*82.,27.+strength*88.
  Else
   LightColor env_light,16,20,27
  EndIf
  AmbientLight Max(0,env_col(0)-55)*0.9,Max(0,env_col(1)-55)*0.9,Max(0,env_col(2)-55)*0.9
 EndIf
End Function

Function remaster_shadow_frame()
 Local distance#=0.
 If remaster_enabled(Cremaster_shadows) And map_hour>=6 And map_hour<18 Then distance=1000.
 ; Main-camera culling must not remove off-screen trees from the light's view.
 ; Only undo view culling during capture; distance limits, script alpha and
 ; collision meshes retain their existing behavior outside this render pass.
 Local caster.Tobject,brush
 If distance>0. Then
  For caster.Tobject=Each Tobject
   caster\shadow_restore=0
   If caster\view_cull<>0 And caster\alpha>0. Then
    brush=GetEntityBrush(caster\h)
    caster\shadow_alpha=GetBrushAlpha(brush)
    FreeBrush brush
    caster\shadow_restore=caster\view_cull
    If caster\view_cull=1 Then ShowEntity caster\h
    EntityAlpha caster\h,caster\alpha
   EndIf
  Next
 EndIf
 RenderWorldShadows cam,env_light,distance
 If distance>0. Then
  For caster.Tobject=Each Tobject
   If caster\shadow_restore<>0 Then
    EntityAlpha caster\h,caster\shadow_alpha
    If caster\shadow_restore=1 Then HideEntity caster\h
    caster\shadow_restore=0
   EndIf
  Next
 EndIf
End Function
