menu_start()

While Not m_performquit

	loopspeedlimit=MilliSecs()


	Select m_section

		Case Csection_menu

			menu()

		Case Csection_game_sp

			game()

		Case Csection_game_mp

			game_mp()

		Case Csection_benchmark
			bench_frame()

		Case Csection_editor

			editor()

	End Select


	gframe=gframe+1
	console()
	modern_present()
	qol_present()
Wend



qol_shutdown()
e_clear()
EndGraphics()
End
