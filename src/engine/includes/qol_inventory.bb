; Presentation snapshots never reorder the engine's Titem list.
Type Tqolitem
 Field itemid,typ,rank,name$
End Type
Global qol_view_class,qol_view_parent,qol_view_sort=-1

Function qol_category(typ)
 Local group$=Ditem_group$(typ)
 If Instr(group$,"tool") Then Return 1
 If Instr(group$,"weapon") Then Return 2
 If Instr(group$,"ammo") Then Return 3
 If Instr(group$,"food") Then Return 4
 If Instr(group$,"potion") Then Return 5
 If Instr(group$,"material") Then Return 6
 Return 7
End Function

Function qol_rank(typ,player)
 If player Then
  For i=1 To 9
   If in_quickslot(i)=typ Then Return i
  Next
 EndIf
 Return 10+qol_category(typ)
End Function

Function qol_item.Titem(id)
 For item.Titem=Each Titem
  If item\id=id Then Return item
 Next
 Return Null
End Function

Function qol_count(typ,class,id)
 Local count
 For item.Titem=Each Titem
  If item\typ=typ And item\parent_class=class And item\parent_id=id Then count=count+item\count
 Next
 Return count
End Function

Function qol_view(class,id)
 Local item.Titem,row.Tqolitem,other.Tqolitem,player=(class=Cclass_unit And id=g_player)
 ; Freeze the view during a drag. Rebuild after its release.
 If in_dnd And qol_view_class=class And qol_view_parent=id Then Return
 For row=Each Tqolitem:Delete row:Next
 qol_view_class=class:qol_view_parent=id
 For item=Each Titem
  If item\parent_class=class And item\parent_id=id Then
   row=New Tqolitem:row\itemid=item\id:row\typ=item\typ
   row\rank=qol_rank(item\typ,player):row\name$=Lower(ko_localize(Ditem_name$(item\typ)))
   If qol_enabled(Cqol_sort) Then
    For other=Each Tqolitem
     If other=row Then Exit
     If row\rank<other\rank Then Insert row Before other:Exit
     If row\rank=other\rank Then
      If row\name$<other\name$ Then Insert row Before other:Exit
      If row\name$=other\name$ Then
       If row\typ<other\typ Or (row\typ=other\typ And row\itemid<other\itemid) Then Insert row Before other:Exit
      EndIf
     EndIf
    Next
   EndIf
  EndIf
 Next
End Function

Function qol_drag(item.Titem,x,y,source)
 If gui_dndwatch(x,y) And (KeyDown(42)=0) And (KeyDown(54)=0) Then
  in_dnd=1:in_dnd_pivotx=in_mx-x:in_dnd_pivoty=in_my-y
  in_dnd_src=source:in_dnd_typ=item\typ:in_dnd_x=in_mx:in_dnd_y=in_my:in_dnd_show=0
 EndIf
End Function

Function qol_inventory_enabled()
 Return qol_enabled(Cqol_sort)
End Function

Function qol_inventory_grid()
 qol_view(Cclass_unit,g_player)
 Local row.Tqolitem,item.Titem,n,total,selected,x,y,i
 For row=Each Tqolitem:total=total+1:Next
 in_scr_scr=qol_max(0,qol_min(in_scr_scr,qol_max(0,((total-1)/11)*11)))
 For x=0 To 10
  For y=0 To 4:DrawBlock gfx_if_itemback,236+x*45,63+y*45:Next
 Next
 For row=Each Tqolitem
  item=qol_item(row\itemid)
  If item<>Null Then
   x=236+((n-in_scr_scr) Mod 11)*45:y=63+((n-in_scr_scr)/11)*45
   If n>=in_scr_scr And n<in_scr_scr+55 Then
    If gui_iconbox(x,y,Ditem_iconh(item\typ),Ditem_name$(item\typ),item\if_sel) Then item\if_sel=1-item\if_sel:FlushKeys()
    qol_drag(item,x,y,0)
    gui_iconboxc(x,y,item\count)
    For i=1 To 9
     If in_quickslot(i)=item\typ Then gui_iconboxqs(x,y,i):Exit
    Next
   EndIf
   If item\if_sel Then
    selected=selected+1:TCitem=item
    tmp_iselx=x:tmp_isely=y:tmp_iseltyp=item\typ
   EndIf
   n=n+1
  EndIf
 Next
 If gui_ibutton(742,63,Cicon_up,sm$(29),in_scr_scr>0) Then in_scr_scr=qol_max(0,in_scr_scr-11)
 If gui_ibutton(742,253,Cicon_down,sm$(30),total>in_scr_scr+55) Then in_scr_scr=in_scr_scr+11
 If in_mzs>0 Then in_scr_scr=qol_max(0,in_scr_scr-11)
 If in_mzs<0 And total>in_scr_scr+55 Then in_scr_scr=in_scr_scr+11
 Return selected
End Function

Function qol_inventory_toolbar()
 If (qol_enabled(Cqol_recipes)=0) Then Return
 If qol_button(566,7,170,"[[loc:qol.recipes]]") Then qol_recipes_open(0)
 Local item.Titem,typ,n
 For item=Each Titem
  If item\parent_class=Cclass_unit And item\parent_id=g_player And item\if_sel Then n=n+1:typ=item\typ
 Next
 If n=1 Then
  If qol_button(620,548,153,"[[loc:qol.related]]") Then qol_recipes_open(typ)
 EndIf
End Function

Function qol_protected(typ)
 If g_cplayer\player_weapon=typ Then Return True
 For i=1 To 9
  If in_quickslot(i)=typ Then Return True
 Next
 Return False
End Function

Function qol_destination_exists()
 Select in_ex_class
  Case Cclass_object:Return con_object(in_ex_id)
  Case Cclass_unit:Return con_unit(in_ex_id)
  Case Cclass_info:Return con_info(in_ex_id)
 End Select
 Return False
End Function

Function qol_move(id,count)
 Local item.Titem=qol_item(id),class,parent,typ,previouscount,result
 If item=Null Or (qol_destination_exists()=0) Then Return 0
 If (if_exchange_allowed(item\typ)=0) Then Return 0
 class=Cclass_unit:parent=g_player
 If item\parent_class=Cclass_unit And item\parent_id=g_player Then
  If Not in_ex_allowstore Then Return 0
  class=in_ex_class:parent=in_ex_id
 ElseIf item\parent_class<>in_ex_class Or item\parent_id<>in_ex_id Then
  Return 0
 EndIf
 typ=item\typ:previouscount=qol_count(typ,class,parent)
 TCitem=item:result=exchange_item(class,parent,count)
 in_ex_sel=0
 Return qol_max(0,qol_count(typ,class,parent)-previouscount)
End Function

; IDs are snapshotted before callbacks can merge/delete items.
Type Tqoltransfer
 Field itemid,count
End Type

Function qol_bulk(deposit)
 Local item.Titem,move.Tqoltransfer,moved,eligible,corpse,class=in_ex_class,parent=in_ex_id
 For move=Each Tqoltransfer:Delete move:Next
 If (qol_destination_exists()=0) Then Return 0
 If in_ex_class=Cclass_unit Then
  If con_unit(in_ex_id) Then corpse=(TCunit\health#<=0)
 EndIf
 For item=Each Titem
  eligible=False
  If deposit Then
   If item\parent_class=Cclass_unit And item\parent_id=g_player And in_ex_allowstore Then
    If (qol_protected(item\typ)=0) And qol_count(item\typ,in_ex_class,in_ex_id)>0 Then eligible=True
   EndIf
  Else
   eligible=(item\parent_class=in_ex_class And item\parent_id=in_ex_id)
  EndIf
  If eligible And if_exchange_allowed(item\typ) Then
   move=New Tqoltransfer:move\itemid=item\id:move\count=item\count
  EndIf
 Next
 For move=Each Tqoltransfer
  ; A script may open a different exchange. Never continue into that target.
  If in_ex_class=class And in_ex_id=parent Then moved=moved+qol_move(move\itemid,move\count)
  Delete move
 Next
 qol_status$=ss$(loc_resolve("[[loc:qol.moved]]"),Str(moved)):qol_status_until=ms+4000
 If (deposit=0) And corpse And qol_enabled(Cqol_close) And in_ex_class=class And in_ex_id=parent And m_menu=Cmenu_if_exchange Then
  If countstored_items(in_ex_class,in_ex_id)=0 Then
   m_menu=0:in_dnd=0:if_unselectitems():flushinput()
  Else
   qol_status$=qol_status$+" "+loc_resolve("[[loc:qol.remaining]]")
  EndIf
 EndIf
 Return moved
End Function

Function qol_storage_grid(class,id,basey,rows,source,offset)
 qol_view(class,id)
 Local total,n,x,y,row.Tqolitem,item.Titem,selected
 For row=Each Tqolitem:total=total+1:Next
 offset=qol_max(0,qol_min(offset,qol_max(0,((total-1)/11)*11)))
 For x=0 To 10
  For y=0 To rows-1:DrawBlock gfx_if_itemback,236+x*45,basey+y*45:Next
 Next
 For row=Each Tqolitem
  If n>=offset And n<offset+rows*11 Then
   item=qol_item(row\itemid)
   If item<>Null Then
    x=236+((n-offset) Mod 11)*45:y=basey+((n-offset)/11)*45
    If gui_iconbox(x,y,Ditem_iconh(item\typ),Ditem_name$(item\typ),item\id=in_ex_sel) Then
     in_ex_sel=item\id
     If qol_enabled(Cqol_storage) And (KeyDown(42) Or KeyDown(54)) Then qol_transfer_id=item\id:qol_transfer_count=item\count
    EndIf
    qol_drag(item,x,y,source):gui_iconboxc(x,y,item\count)
   EndIf
  EndIf
  n=n+1
 Next
 If gui_ibutton(742,basey,Cicon_up,sm$(29),offset>0) Then offset=qol_max(0,offset-11)
 If gui_ibutton(742,basey+(rows-1)*45,Cicon_down,sm$(30),total>offset+rows*11) Then offset=offset+11
 If in_my>=basey And in_my<basey+rows*45 Then
  If in_mzs>0 Then offset=qol_max(0,offset-11)
  If in_mzs<0 And total>offset+rows*11 Then offset=offset+11
 EndIf
 Return offset
End Function

; getinput may already have consumed a key assigned to an engine/script action.
Function qol_shortcut_hit(scan)
 Local hit=KeyHit(scan),i
 For i=0 To 20
  If in_keys(i)=scan And in_keyhit(i) Then hit=True
  If game_scriptkeys>0 And game_scriptkey(i)=scan And in_keyhit(i+21) Then hit=True
 Next
 Return hit
End Function

Function qol_storage_draw()
 If qol_enabled(Cqol_storage)=0 And qol_enabled(Cqol_sort)=0 Then Return False
 If (qol_destination_exists()=0) Then m_menu=Cmenu_if_items:Return True
 Local name$,typ,total,used,action,item.Titem
 Select in_ex_class
  Case Cclass_object:name$=Dobject_name$(TCobject\typ):typ=TCobject\typ:total=Dobject_maxweight(typ)
  Case Cclass_unit:name$=Dunit_name$(TCunit\typ):typ=TCunit\typ:total=Dunit_maxweight(typ)
  Default:name$=sm$(201)
 End Select
 DrawImage gfx_icons,236,5,Cicon_bag
 bmpf_txt(273,10,bmpf_trim(ko_localize(name$),0,320))
 bmpf_txt(236,43,"[[loc:qol.player_items]]",Cbmpf_tiny)
 qol_transfer_id=0
 in_scr_scr=qol_storage_grid(Cclass_unit,g_player,63,5,0,in_scr_scr)
 used=Dunit_maxweight(g_cplayer\typ)-capacity(Cclass_unit,g_player,g_cplayer\typ)
 bmpf_txt(236,286,ss$(sm$(150),numsep$(used),numsep$(Dunit_maxweight(g_cplayer\typ)),numsep$(Dunit_maxweight(g_cplayer\typ)-used)),Cbmpf_tiny)
 If qol_enabled(Cqol_storage) Then
  If qol_button(236,307,158,"[[loc:qol.take_all]]") Then action=1
  If qol_button(400,307,205,"[[loc:qol.deposit_matching]]",in_ex_allowstore) Then action=2
  If qol_button(611,307,80,"[[loc:qol.move_one]]",in_ex_sel>0) Then qol_transfer_id=in_ex_sel:qol_transfer_count=1
  If qol_button(697,307,79,"[[loc:qol.move_stack]]",in_ex_sel>0) Then qol_transfer_id=in_ex_sel:qol_transfer_count=2147483647
 Else
  If gui_ibutton(447,305,Cicon_ex1,sm$(165),in_ex_sel>0) Then qol_transfer_id=in_ex_sel:qol_transfer_count=1
  If gui_ibutton(484,305,Cicon_ex5,sm$(166),in_ex_sel>0) Then qol_transfer_id=in_ex_sel:qol_transfer_count=5
  If gui_ibutton(521,305,Cicon_exall,sm$(167),in_ex_sel>0) Then qol_transfer_id=in_ex_sel:qol_transfer_count=2147483647
 EndIf
 in_scr_scr2=qol_storage_grid(in_ex_class,in_ex_id,347,4,1,in_scr_scr2)
 used=total-capacity(in_ex_class,in_ex_id,typ)
 If total>0 Then bmpf_txt(236,527,ss$(sm$(150),numsep$(used),numsep$(total),numsep$(total-used)),Cbmpf_tiny)
 If qol_button(606,7,125,"[[loc:qol.close]]") Then m_menu=0:flushinput():Return True
 If ms<qol_status_until Then
  bmpf_txt_rect(236,550,535,38,qol_status$,Cbmpf_tiny)
 ElseIf qol_enabled(Cqol_storage) Then
  bmpf_txt_rect(236,550,535,38,"[[loc:qol.transfer_help]]",Cbmpf_tiny)
 EndIf
 If qol_enabled(Cqol_storage) Then
  If qol_shortcut_hit(19) Then
   action=1
   If KeyDown(42) Or KeyDown(54) Then action=2
  ElseIf qol_shortcut_hit(2) And in_ex_sel>0 Then
   qol_transfer_id=in_ex_sel:qol_transfer_count=1
  ElseIf qol_shortcut_hit(33) And in_ex_sel>0 Then
   qol_transfer_id=in_ex_sel:qol_transfer_count=2147483647
  EndIf
 EndIf
 If action>0 Then
  qol_bulk(action=2):flushinput()
 ElseIf qol_transfer_id Then
  If qol_move(qol_transfer_id,qol_transfer_count)=0 Then
   qol_status$=loc_resolve("[[loc:qol.move_blocked]]"):qol_status_until=ms+4000
  EndIf
  flushinput()
 EndIf
 Return True
End Function
