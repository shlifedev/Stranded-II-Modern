; Included by isolated full-engine fixtures. Short trials check semantics, not performance.
compat_check(bench_enter()=0,"benchmark rejects entry from gameplay")
menu_start():m_menu=Cmenu_main
Local test_water=set_water,test_grass=set_grass,test_view=set_viewrange,test_wind=set_windsway,test_fps=modern_fps
Local test_recording=DebugUiProfileRecording(),test_shadows=remaster_enabled(Cremaster_shadows)
Local test_settings_before$=benchmark_read(set_rootdir$+"core\modern.ini")
compat_check(bench_enter(),"benchmark enters only from main menu")
While bench_job:bench_jobs():Wend
compat_check(bench_actual=768 And bench_requested=768 And bench_failed=0,"benchmark default tree and rock preset")
compat_check(bench_ai=0 And bench_animation=0 And First Tpt=Null,"benchmark default simulation off and no create scripts")
bench_remove=0:bench_job=2:While bench_job:bench_jobs():Wend
For group.TbenchGroup=Each TbenchGroup:group\count=0:Next
Local test_object_group=bench_add(Cclass_object,1,4)
Local test_unit_group=bench_add(Cclass_unit,2,2)
Local test_item_group=bench_add(Cclass_item,1,3)
bench_targets(1.):bench_job=1:While bench_job:bench_jobs():Wend
compat_check(bench_actual=9 And bench_failed=0,"benchmark mixed objects units and ground items")
Local test_items=0
For item.Titem=Each Titem
 If item\parent_class=0 And item\count=1 Then test_items=test_items+1
Next
compat_check(test_items=3,"benchmark items are separate models, not stacks or inventory")
Local test_first.TbenchSpawn=First TbenchSpawn,test_x#=test_first\x,test_z#=test_first\z
For group.TbenchGroup=Each TbenchGroup
 If group\serial=test_object_group Then group\count=7
Next
bench_targets(1.):bench_job=1:While bench_job:bench_jobs():Wend
compat_check(bench_actual=12 And test_first\x=test_x And test_first\z=test_z,"benchmark append preserves existing placement")
For group.TbenchGroup=Each TbenchGroup
 If group\serial=test_object_group Then group\count=10000
Next
bench_targets(1.)
compat_check(bench_requested=10005 And bench_rejected=5,"benchmark enforces total model cap before allocation")
bench_job=1:bench_jobs()
compat_check(bench_job=1 And bench_actual>12 And bench_actual<=44,"benchmark creates bounded batches per frame")
bench_action=7:bench_input()
compat_check(bench_job=0,"benchmark generation cancellation stops further allocation")
For group.TbenchGroup=Each TbenchGroup
 If group\serial=test_object_group Then group\count=7
Next
bench_targets(1.):bench_remove=0:bench_job=3:While bench_job:bench_jobs():Wend
bench_selected=test_item_group:bench_action=4:bench_input():While bench_job:bench_jobs():Wend
compat_check(bench_actual=9 And First Titem=Null,"benchmark selected group deletion keeps other models")
bench_targets(1.):bench_job=1:While bench_job:bench_jobs():Wend
compat_check(bench_actual=12,"benchmark deleted group can be regenerated")
bench_camera():bench_ai=0:bench_animation=1
For test_frame=1 To 4
 compat_clock=compat_clock+20:bench_frame():modern_present()
Next
Local test_animated=False
For unit.Tunit=Each Tunit
 If unit\id<>g_player And unit\freeze=1 And Animating(unit\mh) Then test_animated=True
Next
compat_check(test_animated,"benchmark animation runs independently with AI disabled")
bench_ai=1:bench_animation=0
compat_clock=compat_clock+20:bench_frame():modern_present()
Local test_ai=True
For unit.Tunit=Each Tunit
 If unit\id<>g_player And (unit\freeze<>0 Or Animating(unit\mh)) Then test_ai=False
Next
compat_check(test_ai,"benchmark AI runs independently with animation disabled")
bench_ai=0:bench_animation=0:bench_reflection=0:remaster_pending(Cremaster_shadows)=0:bench_refresh()
bench_warm_ms=20:bench_measure_ms=40
bench_start(0)
For test_frame=1 To 600
 compat_clock=compat_clock+20:bench_frame():modern_present()
 If bench_run=0 Then Exit
Next
compat_check(bench_run=0 And bench_actual=12,"benchmark manual measurement keeps current scene")
Local test_results$=DebugUiBenchmarkReport()
compat_check(Instr(test_results,""+Chr(34)+"completed_fps"+Chr(34)+":")>0 And Instr(test_results,""+Chr(34)+"valid"+Chr(34)+":true")>0,"benchmark asynchronous GPU completion and export data")
compat_check(DebugUiBenchmarkExport(set_rootdir$+"core\benchmarks",1),"benchmark JSON export")
compat_check(DebugUiBenchmarkExport(set_rootdir$+"core\benchmarks",0),"benchmark CSV export")
compat_check(SaveBuffer(BackBuffer(),set_rootdir$+"native-benchmark.bmp"),"benchmark actual render screenshot")
For test_effect=0 To 10:bench_compare(test_effect)=0:Next
bench_compare(8)=1
bench_start(1)
compat_check(bench_trial_total=18,"benchmark count comparisons and both alternate reflection paths three times")
For test_frame=1 To 2000
 compat_clock=compat_clock+20:bench_frame():modern_present()
 If bench_run=0 And bench_job=0 Then Exit
Next
compat_check(bench_run=0 And bench_actual=12,"benchmark automatic comparison restores baseline layout")
test_first=First TbenchSpawn
compat_check(Abs(test_first\x-test_x)<0.01 And Abs(test_first\z-test_z)<0.01,"benchmark regenerates the same baseline positions")
test_results=DebugUiBenchmarkReport()
compat_check(Instr(test_results,""+Chr(34)+"condition"+Chr(34)+":102")>0 And Instr(test_results,""+Chr(34)+"condition"+Chr(34)+":101")>0,"benchmark separate cube and planar result conditions")
bench_start(1)
For test_frame=1 To 600
 compat_clock=compat_clock+20:bench_frame():modern_present()
 If bench_trial_index=2 And bench_run=3 Then Exit
Next
bench_focus_lost()
compat_check(bench_run=0,"benchmark focus loss cancels current trial")
compat_check(Instr(DebugUiBenchmarkReport(),""+Chr(34)+"valid"+Chr(34)+":true")>0,"benchmark cancel retains previous completed results")
bench_warm_ms=3000:bench_measure_ms=8000
bench_exit()
compat_check(bench_active=0 And m_section=Csection_menu,"benchmark exits to main menu")
compat_check(set_water=test_water And set_grass=test_grass And set_viewrange=test_view And set_windsway=test_wind And modern_fps=test_fps,"benchmark restores graphics and FPS limit")
compat_check(DebugUiProfileRecording()=test_recording And remaster_enabled(Cremaster_shadows)=test_shadows,"benchmark restores profiler and shadow settings")
compat_check(benchmark_read(set_rootdir$+"core\modern.ini")=test_settings_before,"benchmark does not persist test preferences")

Function benchmark_read$(path$)
 Local stream=ReadFile(path$),body$=""
 If stream Then
  While Not Eof(stream):body=body+ReadLine(stream)+Chr(10):Wend
  CloseFile stream
 EndIf
 Return body
End Function
