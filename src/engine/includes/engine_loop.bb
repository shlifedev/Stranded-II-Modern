;############################################ RUNTIME

menu_start()

While Not m_performquit

	loopspeedlimit=MilliSecs()


	;Stuff
	Select m_section

		Case Csection_menu

			menu()

		Case Csection_game_sp

			game()

		Case Csection_game_mp

			game_mp()

		Case Csection_editor

			editor()

	End Select


	;Everywhere
	gframe=gframe+1
	console()
	;While (MilliSecs()-loopspeedlimit)<19
	;	Delay 1
	;Wend
	modern_present()
Wend



;############################################ SHUTDOWN

e_clear()
EndGraphics()
End
