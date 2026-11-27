modern_version_load()
modern_config()

If set_scrx<800 Or set_scry<600 Then
	set_scrx=800
	set_scry=600
EndIf

Select set_scrbit
	Case 16,32
	Default set_scrbit=16
End Select

If set_scrwin>1 Then
	If Windowed3D()=0 Then
		con_add("ERROR: Unable to run Stranded II in windowed mode",Cbmpf_red)
		set_scrwin=0
	EndIf
EndIf

If set_scrwin>1 Or GfxMode3DExists(set_scrx,set_scry,set_scrbit) Then
	Graphics3D set_scrx,set_scry,set_scrbit,set_scrwin
Else
	If GfxMode3DExists(800,600,16) Then
		Graphics3D 800,600,16,set_scrwin
		set_scrx=800
		set_scry=600
		set_scrbit=16
	ElseIf GfxMode3DExists(800,600,32) Then
		Graphics3D 800,600,32,set_scrwin
		set_scrx=800
		set_scry=600
		set_scrbit=32
	ElseIf GfxMode3DExists(1024,768,16) Then
		Graphics3D 1024,768,16,set_scrwin
		set_scrx=1024
		set_scry=768
		set_scrbit=16
	ElseIf GfxMode3DExists(1024,768,32) Then
		Graphics3D 1024,768,32,set_scrwin
		set_scrx=1024
		set_scry=768
		set_scrbit=32
	Else
		RuntimeError("Unable to initiale Graphics"+Chr(13)+"Please check DirectX and GFX Drivers"+Chr(13)+Chr(13)+"Fehler beim Initialisieren der Grafik"+Chr(13)+"Bitte überprüfe DirectX und Grafiktreiber")
	EndIf
EndIf

modern_window()
AntiAlias remaster_enabled(Cremaster_aa)

If set_moddir$="Stranded II" Then
	AppTitle(Cedition_title$)
Else
	AppTitle(set_moddir$+" - "+Cedition_title$+" Mod")
EndIf
HidePointer()

TextureFilter("",8)
TextureFilter("_a",2)
TextureFilter("_m",4)
TextureFilter("_c",16+32)
TextureFilter("_u",16)
TextureFilter("_v",32)
TextureFilter("_h",256)
TextureFilter("_hc",512)
TextureFilter("_sm",64)

cam=CreateCamera()
CameraRange cam,1,3000
listener=CreateListener(cam,0.02,1,1)
camxz=CreatePivot()

env_light=CreateLight(1)
LightColor env_light,50,50,50

SeedRnd(MilliSecs())

SetBuffer BackBuffer()

mb_setup()

If FileType("saves")<>2 Then CreateDir("saves")
If FileType("maps")<>2 Then CreateDir("maps")
