;©by Unreal Software 2004-2008


Const Cversion$="1.0.0.1"

Include "includes\load_const.bb"
Include "includes\load_vars.bb"
Include "includes\modern_files.bb"
Include "includes\modern_saves.bb"
Include "includes\modern_runtime.bb"
Include "includes\remaster.bb"
Include "includes\modern_version.bb"
Include "includes\load_commandline.bb"
Include "includes\load_functions.bb"
Include "includes\load_settings.bb"
Include "includes\localization.bb"
Include "includes\modern_settings.bb"
loc_boot()
Include "includes\load_strings.bb"
Include "includes\load_setup.bb"
Include "includes\load_bmpf.bb"
bmpf_loadscreen(s$(1),6)
Include "includes\load_media.bb"
Include "includes\modern_ui.bb"
bmpf_loadscreen("[[loc:engine.f8bf61fb6a53]]",18)
Include "includes\load_materials.bb"
bmpf_loadscreen(s$(2),19)
Include "includes\load_keynames.bb"
Include "includes\load_states.bb"
Include "includes\load_lightcycle.bb"
Include "includes\load_game.bb"
Include "includes\load_groups.bb"

load_progress_get()
bmpf_loadscreen(s$(3),20)
Include "includes\load_objects.bb"
bmpf_loadscreen(s$(4),60)
Include "includes\load_units.bb"
bmpf_loadscreen(s$(5),80)
Include "includes\load_items.bb"
bmpf_loadscreen(s$(6),98)
Include "includes\load_infos.bb"
load_progress_set()

Include "includes\load_combinations.bb"
Include "includes\load_buildings.bb"

Include "includes\load_fps.bb"

Include "includes\handle_objects.bb"
Include "includes\handle_units.bb"
Include "includes\handle_items.bb"
Include "includes\handle_infos.bb"
Include "includes\handle_states.bb"
Include "includes\handle_all.bb"

Include "includes\e_clear.bb"
Include "includes\e_environment.bb"
Include "includes\e_save_map.bb"
Include "includes\e_load_map.bb"

Include "includes\gui.bb"
Include "includes\gui_tb.bb"
Include "includes\input.bb"
Include "includes\debug.bb"
Include "includes\functions.bb"
Include "includes\ressources.bb"
Include "includes\parentchild.bb"
Include "includes\grasspread.bb"
Include "includes\motionblur.bb"

Include "includes\menu.bb"

Include "includes\parser.bb"
Include "includes\parser_pre.bb"
Include "includes\parser_functions.bb"
Include "includes\parser_brace.bb"
Include "includes\parser_commands.bb"
Include "includes\parser_vars.bb"
Include "includes\parser_math.bb"
Include "includes\parser_dialogue.bb"
Include "includes\parser_loop.bb"

Include "includes\editor.bb"
Include "includes\editor_menus.bb"
Include "includes\editor_functions.bb"

Include "includes\game.bb"
Include "includes\game_functions.bb"
Include "includes\game_input_vehicles.bb"
Include "includes\game_focus.bb"
Include "includes\game_weapons.bb"
Include "includes\game_sequences.bb"
Include "includes\game_changeday.bb"

Include "includes\console.bb"
Include "includes\skills.bb"

Include "includes\particles.bb"
Include "includes\projectiles.bb"
Include "includes\timer.bb"

Include "includes\physics.bb"

Include "includes\collisions.bb"

Include "includes\sfx.bb"

Include "includes\unrealsoftware.bb"

Include "includes\cull.bb"

Include "includes\ai_units.bb"
Include "includes\ai_signals.bb"
Include "includes\unitpath.bb"

Include "includes\tcpcommands.bb"

Include "includes\udp.bb"

Include "includes\mp_usgn.bb"
Include "includes\mp_game.bb"
Include "includes\mp_game_functions.bb"

Include "includes\mods.bb"

Include "includes\randommap.bb"

Include "includes\speech.bb"

Include "includes\prepare.bb"


prepare_cache_viewranges()

Global br#=1.0
If 1=0

For i=0 To Citem_count
	br=1
	If Ditem_icon$(i)<>"" Then brightness(Ditem_icon$(i),0,0,0,br)
Next

For i=0 To Cobject_count
	br=1
	If Dobject_icon$(i)<>"" Then brightness(Dobject_icon$(i),0,0,0,br)
Next
For i=0 To Cunit_count
	br=1
	If Dunit_icon$(i)<>"" Then brightness(Dunit_icon$(i),0,0,0,br)
Next
EndIf



con_welcome()
unrealsoftware()
