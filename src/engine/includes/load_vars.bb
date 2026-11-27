Global ms,lastms
Global gt
Global fps,fpsms,fpsf,fo1#,fo2#,f#
Global fps_visible=0
Global tmod#=1.
Global gframe

Dim splits$(0)

Type Tcache
	Field id
End Type

Global tmp_x#,tmp_y#,tmp_z#
Global tmp_rx#,tmp_ry#,tmp_rz#
Global tmp_h
Global tmp_class,tmp_id
Global tmp_ty#
Global tmp_iselx,tmp_isely,tmp_iseltyp
Global tmp_kill
Global tmp_killclass
Global tmp_killid
Global tmp_killmodel
Global tmp_killspawntimer
Global tmp_ph
Global tmp_lines
Global tmp_linesvisible
Global tmp_spawnid
Global tmp_dumpvars
Global tmp_skipevent
Global tmp_loading
Global tmp_setcam
Global tmp_loadmapcmdmap$
Global tmp_tarclass
Global tmp_tarid
Global tmp_tarx#,tmp_tary#,tmp_tarz#
Global tmp_gclass,tmp_gid
Global tmp_dvclass,tmp_dvtyp
Global tmp_constv
Global tmp_slideend

Global blackframe#



Global set_debug
Global set_debug_menu
Global set_debug_wireframe=0
Global set_debug_testmap=0
Global set_debug_testmapfile$
Global set_debug_rt
Global set_debug_rt_objects$
Global set_debug_rt_units$
Global set_debug_rt_items$
Global set_debug_rt_infos$
Global set_debug_rt_states$
Global set_debug_rt_particles$
Global set_debug_rt_cull$
Global set_debug_rt_updateworld$
Global set_debug_rt_poststuff$
Global set_debug_rt_render
Global set_debug_rt_update
Global set_debug_seq
Global set_debug_load=0
Global set_drawinterface=1

Global set_scrx,set_scrx_c
Global set_scry,set_scry_c
Global set_scrbit,set_scrbit_c
Global set_scrwin
Global set_reboot

Global set_msens#
Global set_minvert
Global set_msmooth

Global set_viewrange,set_viewfac#
Global set_terrain
Global set_water
Global set_sky
Global set_effects
Global set_grass

Global set_water_texsize
Global set_water_updaterate
Global set_water_updatet
Global set_water_updatestep

Global set_2dfx
Global set_lightfx
Global set_windsway
Global set_fog
Global set_hwmultitex

Global set_mb
Global set_mb_alpha#
Global set_mb_alpha_override#

Global set_musicvolume#
Global set_fxvolume#
Global set_name$
Global set_serverport

Global set_cmd$
Global set_rootdir$
Global set_moddir$
Global set_inbb

Global set_buildplaceid
Global set_buildplacewaterid

Global set_xinvert

Global set_gore

Global set_flarefx

Global set_sv_name$
Global set_sv_port
Global set_sv_ip
Global set_sv_map$
Global set_sv_pw$
Global set_sv_playerlimit


Global cam
Global camxz


Global listener


Global m_section
Global m_menu,in_prevmenu
Global m_performquit


Dim in_mhit(5)
Dim in_mdown(5)
Dim in_mrelease(5)
Global in_mx,in_my
Global in_mxo,in_myo,in_mpt,in_mpts
Global in_mxs#,in_mys#,in_mzs#
Global in_key
Global in_escape
Global in_ttx,in_tty,in_ttt$
Dim in_check(10)
Dim in_opt(10)
Dim in_input$(30)
Global in_inputfocus
Dim in_slider(10)
Global in_slidefocus
Global in_cursor,in_cursorms,in_cursorf
Global in_px#,in_py#,in_pz#,in_pe
Global in_ca#
Global in_wa#

Global in_t50,in_t50go
Global in_t100,in_t100go
Global in_t500,in_t500go
Global in_t1000,in_t1000go
Global in_t2000,in_t2000go
Global in_t3000,in_t3000go
Global in_t5000,in_t5000go
Global in_t10000,in_t10000go

Global in_tgametime

Global in_gt20,in_gt20go
Global in_gt50,in_gt50go
Global in_gt100,in_gt100go
Global in_gt500,in_gt500go
Global in_gt1000,in_gt1000go
Global in_gt5000,in_gt5000go

Global in_gtmod#=1

Global in_turnh
Global in_object_scr
Global in_object_sel
Global in_unit_scr
Global in_unit_sel
Global in_item_scr
Global in_item_sel
Global in_info_scr
Global in_info_sel
Global in_state_scr
Global in_state_sel
Global in_scr_scr
Global in_scr_scr2
Global in_scr_scr3
Global in_scr_descr
Global in_scr_sel=-1
Global in_scr_sel2=-1

Global in_ex_sel
Global in_ex_class
Global in_ex_id
Global in_ex_allowstore
Global in_ex_only$

Global in_dnd
Global in_dnd_pivotx
Global in_dnd_pivoty
Global in_dnd_src
Global in_dnd_typ
Global in_dnd_x
Global in_dnd_y
Global in_dnd_show

Global in_accelerate#

Global in_getkey=-1
Dim in_keys(21)
Dim in_keyname$(262)
Dim in_keyhit(41)
Dim in_keydown(41)
Dim in_keyrelease(41)

Dim game_scriptkey(20)

Dim in_col(2)
Dim in_tercol(2,2)
Dim in_scol(2)

Global in_edbrush
Global in_edtool
Global in_edgrassmode
Global in_edzoom=1
Global in_edmap$
Global in_edrandrot
Global in_edrot#
Global in_edtertool
Global in_edbrushangle#
Global in_edoverinfo$
Global in_edterbtool
Global in_edselgroup

Global in_fraph
Global in_frapdown

Global in_tb_scrx,in_tb_scry
Global in_tb_row
Global in_tb_col

Global in_input_scr
Global in_input_col

Global in_mark
Global in_ms_col,in_ms_row
Global in_me_col,in_me_row

Global in_editor_dummy
Global in_editor_buffer
Global in_editor_bufferx
Global in_editor_buffery
Global in_editor_bufferz
Global in_editor_sidebar

Global in_buildsetup_dummy
Global in_buildsetup_typ
Global in_buildsetup_watersurface
Global in_buildsetup_space
Global in_buildsetup_id

Global in_combi_gid

Global in_console
Global in_conin$
Global in_conlastloadmsg$
Global in_conscroll

Global in_mxsmooth#,in_mysmooth#

Dim in_quickslot(9)

Global in_focus
Global in_fo_x,in_fo_y
Global in_fo_txt$
Global in_fo_perc
Global in_fo_id
Global in_fo_timer

Global in_msgtitle$
Global in_msgtext$

Dim in_win_color(2)
Global in_win_path$
Global in_win_file$
Global in_win_map$

Global in_usgn_version$
Global in_usgn_msg$
Global in_usgn_msgscr#

Global in_chat$
Global in_lastchat$

Global in_sh
Global in_sh_x,in_sh_y
Global in_sh_txt$
Global in_sh_cmd$
Global in_sh_update

Global in_lastjump

Dim in_sb(9)
Dim in_sb_icon(9)
Dim in_sb_txt$(9)
Dim in_sb_scr$(9)
Dim in_sb_handle(9)

Dim in_sitxt(19)
Dim in_sitxt_txt$(19)
Dim in_sitxt_font(19)
Dim in_sitxt_align(19)
Dim in_sitxt_x(19)
Dim in_sitxt_y(19)

Dim in_siimg(39)
Dim in_siimg_mask(39)
Dim in_siimg_x(39)
Dim in_siimg_y(39)

Global in_tpforce#
Dim in_tp#(2)

Global in_sticktoslide=0
Global in_sticktoslidex
Global in_sticktoslidey

Global in_quicksave
Type Tin_quicksave
	Field mode
	Field map$
	Field skills
	Field items
	Field vars
	Field diary
	Field states
	Field locks
End Type

Global in_pc_custom$
Global in_pc_customt
Global in_pc_event$
Global in_pc_menu
Global in_pc_menu_x
Global in_pc_menu_y

Global in_dlg$
Global in_dlg_page$

Global in_scriptblur#

Global in_tut
Global in_tut_y#
Global in_tut_txt$

Global in_wpnrdy
Global in_wpnrdyg#

Global in_blockclose
Global in_jumprehit

Global in_waverate

Global in_compass

Dim in_skyoverride(4)

Global in_clickscreenimage
Global in_clickscreenx

Global in_buildg$

Global in_statefxf
Global in_statefxt

Global in_buildcamh


Global pc_typ
Global pc_value
Global pc_child
Global pc_txt$
Global pc_gt


Global v_speed#

Global v_topspeed#
Global v_turnspeed#
Global v_acceleration#
Global v_friction#
Global v_steering
Global v_maxdepth
Global v_flyspeed#

Global v_steerpos



Global p_l$
Global p_len
Global p_lines
Global p_pl
Global p_p
Global p_gotparam
Global p_com$
Global p_bracelevel
Global p_bracemode
Global p_bracetype$
Global p_bracepar$
Global p_elseif
Global p_varname$

Global p_loopmode
Global p_looprow
Global p_loopcol
Global p_looptype$
Global p_loopargs$
Global p_looplevel
Global p_loopi
Global p_loop_object.Tobject
Global p_loop_unit.Tunit
Global p_loop_item.Titem
Global p_loop_info.Tinfo
Global p_loop_state.Tstate

Global p_parseinprogress

Global p_env_class
Global p_env_id
Global p_env_event$
Global p_env_info$

Global p_skipevent

Global p_m_ol,p_m_or
Global p_m_base$,p_m_value$,p_m_basec
Global p_m_o$

Global p_error
Dim p_erd$(10)
Global p_noerror

Global pv_x#,pv_y#,pv_z#
Global pv_buffer$
Global pv_attack_damage#
Global pv_attack_weapon
Global pv_attack_ammo

Global pv_impact_class
Global pv_impact_id
Global pv_impact_kill
Global pv_impact_num
Global pv_impact_amount
Global pv_impact_x#
Global pv_impact_y#
Global pv_impact_z#
Global pv_impact_ground

Global pv_ai_eater

Global pv_state

Global p_return$

Global p_internal
Global p_internaltxt$

Global pv_lastbuilding

Global pv_loadmaploaded

Global pc_sp_txt$
Global pc_sp_height#
Global pc_sp_x#,pc_sp_y#,pc_sp_z#
Global pc_sp_class
Global pc_sp_id

Global pc_use_x#,pc_use_y#,pc_use_z#

Global pl_cl_title$
Global pc_cl_mode
Global pc_cl_code$
Global pc_cl_class
Global pc_cl_id
Global pc_cl_pos


Global seq_blend
Global seq_blendp#

Global seq_start

Global seq_skipable

Dim seq_msg$(2,1)

Global seq_cstart
Global seq_cend
Global seq_cstartt
Global seq_cendt

Global seq_cpoint
Global seq_cpoint_class
Global seq_cpoint_id
Global seq_cpoint_p

Global seq_tmod
Global seq_tabs
Global seq_tlast

Global seq_cls
Global seq_cls_r,seq_cls_g,seq_cls_b

Global seq_img
Global seq_img_masked

Global seq_hideplayer

Global seq_flw_class
Global seq_flw_id
Global seq_flw_x#
Global seq_flw_y#
Global seq_flw_z#


Global mfx_chan
Global mfx_file
Global mfx_filename$
Global mfx_peakvol#
Global mfx_vol#
Global mfx_fade
Global mfx_fadestart
Global mfx_fadeend


Global ter
Global ter_size
Global ter_tex_color
Global ter_groundy#


Global env_sea,env_sea2,env_seascr#
Global env_seasinus#
Global env_sky
Global env_ground
Global env_light
Global env_weathery#
Global env_cweather
Global env_weatherchan
Global env_wbox
Global env_wa#

Global env_watertex
Global env_watercam

Dim env_col(2)
Dim env_wcol(2)

Global env_watersin#

Dim env_wavematrix(0,0)
Dim env_wavematrixy#(0,0)

Dim env_skytex(5)

Global env_itemlight
Global env_itemchan

Global env_lenspivot
Global env_lenssun

Global env_moon




Global grass_c=19
Global grass_dist=15
Global grass_mapsize
Global grass_f#
Global grass_x,grass_y
Global grass_wind#
Dim grass_rgb(0,0,0)
Dim grass_h(grass_c,grass_c)


Global mb_sprite
Global mb_tex


Global g_player
Global g_player_mst
Global g_player_dead
Global g_cplayer.Tunit

Global g_airtimer
Global g_dive
Global g_swim

Global g_drive

Dim g_objectlist.Tobject(0)
Global g_objectlistc
Global g_objecttimer

Dim g_unitlist.Tunit(0)
Global g_unitlistc
Global g_unittimer

Dim g_itemlist.Titem(0)
Global g_itemlistc
Global g_itemtimer

Global g_unpaused

Global g_sleep


Global map_name$
Global map_path$
Global map_day
Global map_hour
Global map_minute
Global map_freezetime
Global map_skybox$,map_skyboxh
Global map_multiplayer
Global map_climate
Global map_music$,map_musich$
Global map_briefing$
Global map_briefing_key$
Global map_password$
Global map_mode$
Global map_image
Global map_timeupdate
Global map_lday
Global map_lhour
Global map_lminute
Dim map_fog(3)

Global map_id$

Global map_rainratio
Global map_snowratio

Global map_mapimage

Global sg_date$
Global sg_time$
Global sg_multiplayer

Global amb_file
Global amb_chan
Global amb_mode


Global mp_joinserver

Global mp_sendpostimer
Global mp_sendposrate=50
Global mp_sendpingtimer
Global mp_lastping

Global mp_name$
Global mp_id
Global mp_ip



Global object_serial
Global TCobject.Tobject

Type Tobject
	Field id
	Field typ
	Field h
	Field ch
	Field health#,health_max#
	Field windsway#
	Field daytimer
	Field alpha#
	Field view_cull
	Field shadow_restore,shadow_alpha#
	Field chan
End Type


Global unit_serial
Global TCunit.Tunit

Type Tunit
	Field id
	Field typ
	Field h,mh
	Field vh
	Field h_pivot
	Field health#,health_max#
	Field hunger#
	Field thirst#
	Field exhaustion#
	
	Field player_name$
	Field player_ip
	Field player_port
	Field player_ping
	Field player_weapon
	Field player_ammo
	Field player_lastattack
	Field player_score
	Field player_deads
	
	Field phy_jump#
	Field phy_ft
	Field phy_fall
	Field phy_fally#
	
	Field ani
	
	Field states
	
	Field ai_mode
	Field ai_mode2
	Field ai_target_class
	Field ai_target_id
	Field ai_timer
	Field ai_duration
	Field ai_cx#,ai_cz#,ai_ch
	
	Field chan
	
	Field freeze
	
	Field lastspot
End Type


Global item_serial
Global TCitem.Titem

Type Titem
	Field id
	Field typ
	Field h
	Field health#
	Field count
	
	Field phy_pause
	Field phy_fall
	
	Field parent_class
	Field parent_mode
	Field parent_id
	
	Field if_sel
End Type


Global info_serial
Global TCinfo.Tinfo

Type Tinfo
	Field id
	Field typ
	Field h
	
	Field timer
	
	Field vars$
	Field ints[2]
	Field floats#[2]
	Field strings$[2]
End Type


Global TCstate.Tstate

Type Tstate
	Field typ
	
	Field parent_class
	Field parent_id
	
	Field x#,y#,z#
	Field fx#,fy#,fz#
	Field value
	Field value_f#
	Field value_s$
	
	Field r,g,b
	
	Field h
	
	Field light
	
	Field chan
End Type


Global TCx.Tx

Type Tx
	Field typ
	Field parent_class
	Field parent_id
	
	Field mode
	Field key$
	Field value$
	Field stuff$
End Type

Type Txc
	Field typ
	Field parent_class
	Field parent_id
	Field mode
	Field key$
	Field value$
	Field stuff$
End Type


Global TCp.Tp

Type Tp
	Field h
	Field typ
	
	Field x#,y#
	Field fx#,fy#,fz#
	Field r#,g#,b#
	Field a#
	Field size#
	Field rot#
	Field fadein#
	
	Field age
	Field timer
	Field frame
	
	Field parentid
	Field parentx#
	Field parentz#
End Type


Global TCpro.Tpro

Type Tpro
	Field mh
	Field h
	Field typ
	Field spawner
	Field weapon
	
	Field fx#,fy#,fz#
	Field age
	Field water
	Field wc
	Field tail
	
	Field tcol[2]
	Field tfx
	Field tset#[1]
	
	Field speed#
	Field damage#
	Field drag#
	
	Field sx#
	Field sy#
	Field spitch#
End Type


Type Tres
	Field file$
	Field h
	Field typ
End Type


Type Tsoundset
	Field name$
	
	Field spot
	Field attack
	Field idle1
	Field idle2
	Field idle3
	Field die
	Field flee
	Field move
End Type


Global com_serial
Type Tcom
	Field id
	Field mode
	Field typ
	Field count
	Field del
	Field script$
	Field genname$
	Field gid
	Field aid$
End Type

Global coms_serial


Global bui_serial
Type Tbui
	Field id
	Field mode
	Field typ
	Field count
	Field script$
	Field group$
End Type


Type Tfind
	Field class
	Field parent
	Field typ
	Field ratio
	Field max
	Field min
	Field reqtyp
End Type


Type Tmsg
	Field msg$
	Field col
	Field age
	Field scroll#
End Type


Type Ttbt
	Field txt$
End Type


Type Tcon
	Field txt$
End Type


Dim Dpc$(0)
Type Tpc
	Field txt$
End Type

Type Tpj
	Field l
	Field pos
	Field level
	Field typ$
	Field name$
End Type

Type Tpm
	Field typ
	Field pos
	Field txt$
	Field value$
End Type

Type Tpt
	Field class
	Field id
	Field event$
	Field info$
	Field script$
End Type

Type Tplj
	Field l
	Field pos
	Field level
	Field typ$
	Field start
End Type

Type Tpkill
	Field class
	Field id
	Field h
	Field spawntimer
End Type


Type Tsg
	Field name$
End Type

Type Tseq
	Field t
	Field event$
	Field in[5]
	Field fl#[5]
	Field txt$[5]
End Type


Type Tsfx
	Field file
	Field chan
End Type


Type Tfile
	Field name$
	Field path$
	Field typ
	Field icon
End Type


Type Twave
	Field x,z
	Field dir
	Field h
End Type

Type Tsc
	Field cmd$
	Field groups$
	Field params$
End Type

Type Tsgr
	Field group$
End Type


Type Tserver
	Field ip
	Field port
	
	Field name$
	Field map$
	Field players
	Field maxplayers
	Field password
	
	Field ping
	Field pingstamp
	
	Field timer
End Type

Type Tpos
	Field id
	Field time
	Field x#,y#,z#
	Field yaw#
End Type

Type Tat
	Field path$
End Type


Type Ttimer
	Field typ
	Field parent_class
	Field parent_id
	Field duration
	Field start
	Field mode
	Field scr$
End Type


Type Tfrap
	Field parent_class
	Field parent_id
	Field y#
	Field pitch#
	Field roll#
End Type


Global TCup.Tup

Type Tup
	Field unit
	Field info
	Field mode
End Type


Type Tw
	Field h
	Field id
End Type


Global speech_chan

Type Tspeech
	Field file$
	Field limit
	Field lastplayed
End Type


Type Tran
	Field r1#
	Field r2#
	Field a
	Field class
	Field id
End Type

Global Crandom_c=50
Dim Drprofile$(Crandom_c)
Global random_c=0

Dim Drpool(0)

Global random_script$


Type Trecent
	Field file$
End Type


Type Tdefparam
	Field class
	Field typ
	Field key$
	Field value$
End Type

Type Tdefvar
	Field class
	Field typ
	Field key$
	Field descr$
	Field defaultval$
	Field isglobal
End Type


Type Tsani
	Field typ
	Field h
	Field f1
	Field f2
End Type


Type Tlensf
	Field h
	Field offset#
	Field ra#
	Field ca#
End Type


Type t3d
	Field class
	Field id
	Field txt$
	Field typ
	Field offset
	Field range
End Type


Type tconst
	Field name$
	Field value
End Type


Type Tcscr
	Field typ
	Field x
	Field y
	Field txt$
	Field col
	Field image
	Field event$
	Field align
	Field tooltip$
End Type


Dim ed_groupg$(4)
Dim ed_groupn$(4)
Dim ed_groupc(4)

Global ed_setscript$

Global ed_current_class=-5
Global ed_current_group$
Global ed_current_c

Type Tgroup
	Field class
	Field group$
	Field name$
	Field count
End Type

Type Tgroupl
	Field id
End Type


Type Tcopy
	Field typ
	Field id
	Field key$
	Field value
	Field value_f#
	Field value_s$
	Field x#,y#,z#
	Field fx#,fy#,fz#
	Field r,g,b
End Type

Type Ttrade
	Field id
	Field mode
	Field typ
	Field count
End Type
