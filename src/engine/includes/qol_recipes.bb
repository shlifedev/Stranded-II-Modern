Type Tqolrecipe
 Field id,combi,icon,category,title$,method$,condition$,key$,source$
End Type
Type Tqolpart
 Field recipe,mode,typ,count,keep
End Type
Type Tqolmatch
 Field recipe.Tqolrecipe
End Type
Type Tqolhistory
 Field query$,category,ready,item,relation,selected,scroll
End Type
Type Tqolselection
 Field itemid
End Type
Global qol_recipe_serial,qol_recipe_selected,qol_recipe_scroll,qol_detail_scroll
Global qol_recipe_category,qol_recipe_ready,qol_recipe_item,qol_recipe_relation

Function qol_recipe.Tqolrecipe(id)
 For recipe.Tqolrecipe=Each Tqolrecipe
  If recipe\id=id Then Return recipe
 Next
 Return Null
End Function

Function qol_part(recipe,mode,typ,count,keep=0)
 If typ<=0 Or typ>Citem_count Then Return
 If Ditem_name$(typ)="" Or count<1 Then Return
 Local part.Tqolpart=New Tqolpart
 part\recipe=recipe:part\mode=mode:part\typ=typ:part\count=count:part\keep=keep
End Function

Function qol_guides_load()
 Local recipe.Tqolrecipe,part.Tqolpart,head.Tcom,entry.Tcom
 For recipe=Each Tqolrecipe:Delete recipe:Next
 For part=Each Tqolpart:Delete part:Next
 qol_recipe_serial=0
 For head=Each Tcom
  If head\mode=2 Then
   recipe=New Tqolrecipe:qol_recipe_serial=qol_recipe_serial+1
   recipe\id=qol_recipe_serial:recipe\combi=head\id
   recipe\key$=head\aid$:recipe\title$=head\genname$
   For entry=Each Tcom
    If entry\id=head\id And entry\mode<2 Then
     qol_part(recipe\id,entry\mode,entry\typ,entry\count,1-entry\del)
     If entry\mode=1 And recipe\icon=0 Then recipe\icon=entry\typ
    EndIf
   Next
   recipe\category=qol_category(recipe\icon)
   If recipe\title$<>"" Then
    recipe\category=8:recipe\icon=0
   Else
    recipe\title$=Ditem_name$(recipe\icon)
   EndIf
   recipe\condition$="[[loc:qol.script_conditions]]"
  EndIf
 Next
 Local f=ReadFile("sys\qol_guides.inf"),line$,eq,key$,value$
 recipe=Null
 If f=0 Then con_add("QOL: sys/qol_guides.inf missing; run setup for this edition"):Return
 While (Eof(f)=0)
  line$=Trim(ReadLine(f)):eq=Instr(line$,"=")
  If eq>1 And Left(line$,1)<>"#" Then
   key$=Trim(Left(line$,eq-1)):value$=Trim(Mid(line$,eq+1))
   If key$="guide" Then
    recipe=New Tqolrecipe:qol_recipe_serial=qol_recipe_serial+1
    recipe\id=qol_recipe_serial:recipe\key$=value$
   ElseIf recipe<>Null Then
    Select key$
     Case "title":recipe\title$=value$
     Case "method":recipe\method$=value$
     Case "condition":recipe\condition$=value$
     Case "source":recipe\source$=value$
     Case "req","gen"
      split(value$,",",2)
      qol_part(recipe\id,key$="gen",Int(splits(0)),Int(splits(1)),Int(splits(2)))
      If key$="gen" And recipe\icon=0 Then recipe\icon=Int(splits(0)):recipe\category=qol_category(recipe\icon)
    End Select
   EndIf
  EndIf
 Wend
 CloseFile f
End Function

Function qol_recipe_locked(recipe.Tqolrecipe)
 If recipe\combi=0 Then Return False
 For lock.Tx=Each Tx
  If lock\mode=50 And lock\key$=recipe\key$ Then Return True
 Next
 Return False
End Function

Function qol_recipe_materials(recipe.Tqolrecipe)
 For part.Tqolpart=Each Tqolpart
  If part\recipe=recipe\id And part\mode=0 Then
   If qol_count(part\typ,Cclass_unit,g_player)<part\count Then Return False
  EndIf
 Next
 Return True
End Function

Function qol_recipe_matches(recipe.Tqolrecipe)
 If qol_recipe_category And recipe\category<>qol_recipe_category Then Return False
 If qol_recipe_ready And (qol_recipe_materials(recipe)=0) Then Return False
 Local related=(qol_recipe_item=0),text$=ko_localize(recipe\title$)
 For part.Tqolpart=Each Tqolpart
  If part\recipe=recipe\id Then
   text$=text$+" "+ko_localize(Ditem_name$(part\typ))
   If part\typ=qol_recipe_item Then
    If qol_recipe_relation=0 Or (qol_recipe_relation=1 And part\mode=1) Or (qol_recipe_relation=2 And part\mode=0) Then related=True
   EndIf
  EndIf
 Next
 If Not related Then Return False
 If qol_query$<>"" And Instr(Lower(text$),Lower(qol_query$))=0 Then Return False
 Return True
End Function

Function qol_history_push()
 Local history.Tqolhistory=New Tqolhistory
 history\query$=qol_query$:history\category=qol_recipe_category:history\ready=qol_recipe_ready
 history\item=qol_recipe_item:history\relation=qol_recipe_relation
 history\selected=qol_recipe_selected:history\scroll=qol_recipe_scroll
End Function

Function qol_recipe_back()
 Local history.Tqolhistory=Last Tqolhistory
 If history=Null Then
  m_menu=Cmenu_if_items:qol_search_close():flushinput():Return
 EndIf
 qol_search_set(history\query$):qol_recipe_category=history\category:qol_recipe_ready=history\ready
 qol_recipe_item=history\item:qol_recipe_relation=history\relation
 qol_recipe_selected=history\selected:qol_recipe_scroll=history\scroll:qol_detail_scroll=0
 Delete history:flushinput()
End Function

Function qol_recipes_open(typ)
 For history.Tqolhistory=Each Tqolhistory:Delete history:Next
 qol_search_set(""):qol_recipe_item=typ:qol_recipe_relation=0
 qol_recipe_category=0:qol_recipe_ready=0:qol_recipe_scroll=0:qol_detail_scroll=0:qol_recipe_selected=0
 m_menu=28:in_inputfocus=-1:in_dnd=0:flushinput()
End Function

Function qol_recipe_navigate(typ,relation)
 qol_history_push():qol_search_set("")
 qol_recipe_item=typ:qol_recipe_relation=relation
 qol_recipe_category=0:qol_recipe_ready=0:qol_recipe_scroll=0:qol_detail_scroll=0:qol_recipe_selected=0
 flushinput()
End Function

Function qol_craft(recipe.Tqolrecipe)
 If recipe=Null Then Return False
 If recipe\combi=0 Or qol_recipe_locked(recipe) Or (qol_recipe_materials(recipe)=0) Then Return False
 If in_focus>99 And in_focus<>Cstate_pc_build Then Return False
 Local item.Titem,part.Tqolpart,selection.Tqolselection,result
 For selection=Each Tqolselection:Delete selection:Next
 For item=Each Titem
  If item\if_sel Then selection=New Tqolselection:selection\itemid=item\id
 Next
 if_unselectitems()
 For part=Each Tqolpart
  If part\recipe=recipe\id And part\mode=0 Then
   If get_stored_item(part\typ,Cclass_unit,g_player) Then TCitem\if_sel=1
  EndIf
 Next
 result=combine_item(Cclass_unit,g_player,recipe\combi)
 if_unselectitems()
 For selection=Each Tqolselection
  item=qol_item(selection\itemid)
  If item<>Null Then item\if_sel=1
  Delete selection
 Next
 flushinput()
 Return result
End Function

Function qol_category_name$(category)
 Select category
  Case 1:Return "[[loc:qol.cat.tools]]"
  Case 2:Return "[[loc:qol.cat.weapons]]"
  Case 3:Return "[[loc:qol.cat.ammo]]"
  Case 4:Return "[[loc:qol.cat.food]]"
  Case 5:Return "[[loc:qol.cat.potions]]"
  Case 6:Return "[[loc:qol.cat.materials]]"
  Case 7:Return "[[loc:qol.cat.misc]]"
  Case 8:Return "[[loc:qol.cat.actions]]"
 End Select
 Return "[[loc:qol.cat.all]]"
End Function

Function qol_recipe_draw()
 If (qol_enabled(Cqol_recipes)=0) Then m_menu=Cmenu_if_items:Return
 qol_search_open()
 DrawImage gfx_icons,236,5,Cicon_inc:bmpf_txt(273,10,"[[loc:qol.recipes]]")
 If qol_button(606,7,125,"[[loc:qol.back]]") Or (in_escape And in_inputfocus=-1) Then qol_recipe_back():Return
 If qol_search_hwnd=0 Then bmpf_txt(236,59,"[[loc:qol.search_unavailable]]",Cbmpf_tiny)
 If qol_button(650,55,125,"[[loc:qol.clear_search]]") Then qol_search_set(""):qol_recipe_scroll=0:flushinput()
 Local direction=modern_choice(236,89,190,qol_category_name(qol_recipe_category))
 If direction Then qol_recipe_category=(qol_recipe_category+direction+9) Mod 9:qol_recipe_scroll=0
 If qol_button(436,87,160,"[[loc:qol.materials_ready]]",1,qol_recipe_ready) Then qol_recipe_ready=1-qol_recipe_ready:qol_recipe_scroll=0:flushinput()
 If qol_button(606,87,169,"[[loc:qol.clear_filters]]") Then qol_recipe_item=0:qol_recipe_category=0:qol_recipe_ready=0:qol_recipe_scroll=0:flushinput()
 Local recipe.Tqolrecipe,match.Tqolmatch,other.Tqolmatch,total,n,y,selectedfound,navigate,relation
 For match=Each Tqolmatch:Delete match:Next
 For recipe=Each Tqolrecipe
  If qol_recipe_matches(recipe) Then
   match=New Tqolmatch:match\recipe=recipe:total=total+1
   If recipe\id=qol_recipe_selected Then selectedfound=True
   For other=Each Tqolmatch
    If other=match Then Exit
    If Lower(ko_localize(recipe\title$))<Lower(ko_localize(other\recipe\title$)) Then Insert match Before other:Exit
   Next
  EndIf
 Next
 If Not selectedfound Then
  match=First Tqolmatch:qol_recipe_selected=0:qol_detail_scroll=0
  If match<>Null Then qol_recipe_selected=match\recipe\id
 EndIf
 qol_recipe_scroll=qol_max(0,qol_min(qol_recipe_scroll,qol_max(0,total-7)))
 For match=Each Tqolmatch
  If n>=qol_recipe_scroll And n<qol_recipe_scroll+7 Then
   recipe=match\recipe:y=123+(n-qol_recipe_scroll)*55
   If qol_button(236,y,190,recipe\title$,1,recipe\id=qol_recipe_selected) Then qol_recipe_selected=recipe\id:qol_detail_scroll=0:flushinput()
   Local status$="[[loc:qol.materials_missing]]"
   If qol_recipe_materials(recipe) Then status$="[[loc:qol.materials_ready]]"
   If recipe\combi=0 Then status$="[[loc:qol.guide]]"
   If qol_recipe_locked(recipe) Then status$="[[loc:qol.locked]]"
   bmpf_txt(241,y+27,status$,Cbmpf_tiny)
  EndIf
  n=n+1
 Next
 If total=0 Then bmpf_txt_rect(236,135,190,90,"[[loc:qol.no_results]]",Cbmpf_tiny)
 If qol_button(236,517,90,"[[loc:qol.previous]]",qol_recipe_scroll>0) Then qol_recipe_scroll=qol_max(0,qol_recipe_scroll-7):flushinput()
 If qol_button(336,517,90,"[[loc:qol.next]]",total>qol_recipe_scroll+7) Then qol_recipe_scroll=qol_min(total-7,qol_recipe_scroll+7):flushinput()
 bmpf_txt(236,551,ss$(loc_resolve("[[loc:qol.result_count]]"),Str(total)),Cbmpf_tiny)
 If in_mx<436 Then
  If in_mzs>0 Then qol_recipe_scroll=qol_max(0,qol_recipe_scroll-1)
  If in_mzs<0 Then qol_recipe_scroll=qol_min(qol_max(0,total-7),qol_recipe_scroll+1)
 Else
  If in_mzs>0 Then qol_detail_scroll=qol_max(0,qol_detail_scroll-36)
  If in_mzs<0 Then qol_detail_scroll=qol_detail_scroll+36
 EndIf
 recipe=qol_recipe(qol_recipe_selected)
 If recipe=Null Then Return
 Color 127,98,53:Line 440,118,440,576
 bmpf_txt_rect(456,119,318,42,recipe\title$)
 Local part.Tqolpart,label$,have,col,base=174-qol_detail_scroll,lines,partclick
 ; The detail area scrolls independently. Clicks outside it cannot navigate.
 Viewport 450,170,326,342
 For part=Each Tqolpart
  If part\recipe=recipe\id Then
   If part\mode=0 Or recipe\category<>8 Then
    label$=ko_localize(Ditem_name$(part\typ))+" x"+part\count
    col=Cbmpf_norm
    If part\mode=0 Then
     have=qol_count(part\typ,Cclass_unit,g_player)
     label$=ko_localize(Ditem_name$(part\typ))+" "+have+"/"+part\count
     If have<part\count Then col=Cbmpf_red
     If part\keep Then label$=label$+" "+loc_resolve("[[loc:qol.kept]]")
    Else
     label$=loc_resolve("[[loc:qol.result]]")+" "+label$
    EndIf
    DrawImage Ditem_iconh(part\typ),456,base
    bmpf_txt(501,base+10,bmpf_trim(label$,col,268),col)
    If base>=170 And base+36<=512 Then
     If in_mx>=456 And in_mx<776 And in_my>=base And in_my<base+36 Then
      gui_tt(in_mx,in_my,label$+" - "+loc_resolve("[[loc:qol.find_recipe]]"))
      If in_mrelease(1) Then navigate=part\typ:relation=1
     EndIf
    EndIf
    base=base+44:lines=lines+1
   EndIf
  EndIf
 Next
 If recipe\condition$<>"" Then bmpf_txt_rect(456,base+8,314,100,recipe\condition$,Cbmpf_tiny)
 If recipe\method$<>"" Then bmpf_txt_rect(456,base+116,314,160,recipe\method$,Cbmpf_tiny)
 Viewport 0,0,set_scrx,set_scry
 qol_detail_scroll=qol_min(qol_detail_scroll,qol_max(0,lines*44+280-342))
 If navigate Then qol_recipe_navigate(navigate,relation):Return
 If recipe\combi Then
  Local cancraft=qol_recipe_materials(recipe) And (qol_recipe_locked(recipe)=0)
  If in_focus>99 And in_focus<>Cstate_pc_build Then cancraft=False
  If qol_button(456,526,190,"[[loc:qol.craft_one]]",cancraft) Then qol_craft(recipe)
 Else
  bmpf_txt(456,533,"[[loc:qol.use_at_location]]",Cbmpf_tiny)
 EndIf
 If recipe\icon Then
  If qol_button(656,526,120,"[[loc:qol.used_in]]") Then qol_recipe_navigate(recipe\icon,2)
 EndIf
 bmpf_txt(456,562,"[[loc:qol.detail_scroll]]",Cbmpf_tiny)
End Function
