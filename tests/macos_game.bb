; Appended to the shared full-engine fixture in an isolated native runtime.
; Exercise real frame functions after the synthetic intro/transition checks.
menu_start()
For nativeFrame=1 To 100
 compat_clock=compat_clock+20
 menu()
 modern_present()
Next
compat_check(SaveBuffer(BackBuffer(),set_rootdir$+"native-menu.bmp"),"native menu screenshot")

m_section=Csection_game_sp:m_menu=0:g_unpaused=1
compat_check(load_map("maps\adventure\map02.s2","")=1,"native playable island load")
parse_all()
For nativeFrame=1 To 180
 compat_clock=compat_clock+20
 game()
 modern_present()
Next
compat_check(SaveBuffer(BackBuffer(),set_rootdir$+"native-island.bmp"),"native island screenshot")
compat_check(save_map("saves\NATIVE-CHECK.sav","sav","")=1,"native checked save replacement")
compat_check(modern_save_valid("saves\NATIVE-CHECK.sav"),"native saved file preflight")
Local nativeX#=EntityX(g_cplayer\h),nativeY#=EntityY(g_cplayer\h),nativeZ#=EntityZ(g_cplayer\h)
; The shared saver deliberately writes the collision-resolved pose.
If col_player_valid And col_player_managed(g_cplayer) Then
 nativeX#=col_player_x#:nativeY#=col_player_y#:nativeZ#=col_player_z#
EndIf
compat_check(save_map("saves\NATIVE-CHECK.sav","sav","")=1,"native save backup replacement")
compat_check(modern_save_valid("saves\NATIVE-CHECK.sav.bak"),"native save backup preflight")
compat_check(load_map("saves\NATIVE-CHECK.sav","")=1,"native saved game reload")
WriteLine compat_report,"SAVED POSITION "+nativeX#+" "+nativeY#+" "+nativeZ#
compat_position("native-reloaded-player",g_cplayer\h)
compat_check(Abs(EntityX(g_cplayer\h)-nativeX#)<0.01 And Abs(EntityY(g_cplayer\h)-nativeY#)<0.01 And Abs(EntityZ(g_cplayer\h)-nativeZ#)<0.01,"native saved player position")
mac_service("search-open","한글")
mac_service("search-set","나무 돌")
compat_check(mac_service("search-text")="나무 돌","native Cocoa Korean text roundtrip")
mac_service("search-close")
