; Shared real-engine checks; the caller supplies cheat_check and a loaded player.
dev_world_reset()
Local cheatHealth#=g_cplayer\health,cheatDrive=g_drive,cheatFreeze=map_freezetime
Local cheatDay=map_day,cheatHour=map_hour,cheatMinute=map_minute
Local cheatX#=EntityX(g_cplayer\h),cheatY#=EntityY(g_cplayer\h),cheatZ#=EntityZ(g_cplayer\h)
cheat_check(dev_player(),"cheats have a single-player target")
g_cplayer\hunger=10:g_cplayer\thirst=20:g_cplayer\exhaustion=30
con_unit(g_player):game_exhaust(1,2,3)
cheat_check(g_cplayer\hunger=11 And g_cplayer\thirst=22 And g_cplayer\exhaustion=33,"disabled modifiers preserve survival costs")
dev_lock_hunger=1:dev_lock_exhaustion=1
game_exhaust(500,2,500)
cheat_check(g_cplayer\hunger=0 And g_cplayer\thirst=24 And g_cplayer\exhaustion=0,"independent need locks suppress only selected costs")
dev_lock_thirst=1:dev_survival()
cheat_check(g_cplayer\thirst=0,"thirst lock applies while overlay is closed")
g_cplayer\health=1:dev_queue(Cdev_heal):dev_actions()
cheat_check(g_cplayer\health=g_cplayer\health_max And g_cplayer\hunger=0,"queued recovery restores health and needs")
; Repair only direct inventory, preserving world items and stack quantities.
Local cheatCarried=set_item(-1,1,cheatX,cheatY,cheatZ,3)
store_item(cheatCarried,Cclass_unit,g_player)
con_item(cheatCarried):TCitem\health=1
Local cheatLoose=set_item(-1,1,cheatX,cheatY,cheatZ,2)
con_item(cheatLoose):TCitem\health=1
dev_queue(Cdev_repair):dev_actions():con_item(cheatCarried)
cheat_check(TCitem\health=Ditem_health(1) And TCitem\count=3,"repair preserves inventory stack count")
con_item(cheatLoose):cheat_check(TCitem\health=1,"repair leaves world items unchanged")
free_item(cheatCarried):free_item(cheatLoose)
g_drive=0
dev_queue(Cdev_bookmark):dev_actions()
cheat_check(dev_bookmark,"remember position")
g_cplayer\phy_jump=gt+500:in_tpforce=10
cheat_check(cheat_teleport(cheatX+10,cheatY+20,cheatZ),"teleport accepted on foot")
cheat_check(g_cplayer\phy_jump=0 And in_tpforce=0 And col_player_valid=0,"teleport clears old jump and collision state")
dev_queue(Cdev_return):dev_actions()
cheat_check(Abs(EntityX(g_cplayer\h)-cheatX)<0.01 And Abs(EntityY(g_cplayer\h)-cheatY)<0.01,"return restores remembered position")
; Compare actual movement from the same pose with the same frame factor.
Local cheatFactor#=f,cheatProcess=pc_typ,cheatConsole=in_console,cheatFrozen=g_cplayer\freeze
f=1:pc_typ=0:in_console=0:g_cplayer\freeze=0:dev_clear_input()
Dim cheatMove#(1)
For cheatTrial=0 To 1
 dev_speed=1.+Float(cheatTrial)
 cheat_teleport(cheatX,1000,cheatZ)
 in_keydown(Ckey_forward)=1:game_input_normal()
 cheatMove(cheatTrial)=Sqr((EntityX(g_cplayer\h)-cheatX)^2+(EntityZ(g_cplayer\h)-cheatZ)^2)
Next
cheat_check(cheatMove(0)>0 And Abs(cheatMove(1)-2.*cheatMove(0))<0.001,"movement multiplier doubles walking distance")
f=cheatFactor:pc_typ=cheatProcess:in_console=cheatConsole:g_cplayer\freeze=cheatFrozen:dev_clear_input()
dev_queue(Cdev_surface):dev_actions()
cheat_check(Abs(EntityY(g_cplayer\h)-(TerrainY(ter,EntityX(g_cplayer\h),0,EntityZ(g_cplayer\h))+Dunit_colyr(g_cplayer\typ)+5.))<0.01,"surface escape places player above terrain")
cheat_teleport(cheatX,cheatY,cheatZ)
g_drive=1
cheat_check(cheat_teleport(0,0,0)=0 And Abs(EntityX(g_cplayer\h)-cheatX)<0.01,"mounted teleport rejected without moving player")
g_drive=0
cheat_check(cheat_time(cheatDay,12,0),"set noon")
cheat_check(map_timeupdate=0 And map_lhour=12 And in_tgametime=gt,"time change resets pending time events")
cheat_check(cheat_time(cheatDay,24,0)=0 And map_hour=12,"invalid time leaves clock unchanged")
map_freezetime=0:dev_freeze_time=1:in_tgametime=gt-Cworld_minute_ms
e_environment_update()
cheat_check(map_minute=0 And map_freezetime=0,"clock modifier does not alter map freeze flag")
dev_freeze_time=0:in_tgametime=gt-Cworld_minute_ms
e_environment_update()
cheat_check(map_minute=1,"clock resumes when modifier is disabled")
cheat_time(cheatDay,cheatHour,cheatMinute):map_freezetime=cheatFreeze
; New map epochs discard queued actions, bookmarks and all session modifiers.
dev_speed=3:dev_freeze_time=1:dev_queue(Cdev_heal)
dev_world_reset():g_cplayer\health=1:dev_actions()
cheat_check(g_cplayer\health=1 And First TdevAction=Null,"world reset discards stale actions")
cheat_check(dev_speed=1 And dev_freeze_time=0 And dev_bookmark=0 And dev_lock_hunger=0 And dev_lock_thirst=0 And dev_lock_exhaustion=0,"world reset clears session modifiers and bookmark")
; The same helpers must refuse menu/editor/multiplayer contexts.
m_section=Csection_menu
dev_queue(Cdev_heal):dev_actions()
cheat_check(cheat_heal()=0 And cheat_time(1,12,0)=0 And cheat_repair()=0 And g_cplayer\health=1,"cheats disabled outside single-player")
m_section=Csection_game_sp:g_drive=cheatDrive:g_cplayer\health=cheatHealth
dev_world_reset()
