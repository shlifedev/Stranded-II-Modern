; Generated from the original UI drawing recipes.
Function modern_create_borders()
gfx_border(3)=CreateImage(set_scrx,16)
SetBuffer ImageBuffer(gfx_border(3))
TileBlock gfx_border(1)
MaskImage gfx_border(3),255,0,255
gfx_border(4)=CreateImage(16,set_scry)
SetBuffer ImageBuffer(gfx_border(4))
TileBlock gfx_border(2)
MaskImage gfx_border(4),255,0,255
SetBuffer BackBuffer()
End Function
Function modern_create_windows()
gfx_sidebar=CreateImage(210,set_scry)
SetBuffer ImageBuffer(gfx_sidebar)
TileBlock gfx_bg(1)
Color 255,0,255:Rect 202,0,8,set_scry,1
DrawImage gfx_border(4),194,0
MaskImage gfx_sidebar,255,0,255

gfx_win=CreateImage(580,595)
SetBuffer ImageBuffer(gfx_win)
TileBlock gfx_bg(1)
Color 255,0,255:Rect 0,0,8,595,1
Rect 572,0,8,595,1:Rect 0,587,580,8,1
DrawImage gfx_border(3),0,579
DrawImage gfx_border(4),0,0
DrawImage gfx_border(4),564,0
DrawImage gfx_border(0),0,579
DrawImage gfx_border(0),564,579
MaskImage gfx_win,255,0,255

gfx_winbar=CreateImage(580,16)
SetBuffer ImageBuffer(gfx_winbar)
DrawBlock gfx_border(3),0,0
DrawImage gfx_border(0),0,0
DrawImage gfx_border(0),564,0
MaskImage gfx_winbar,255,0,255
SetBuffer BackBuffer()
End Function
Function modern_rebuild_ui()
 SetBuffer BackBuffer()
 If gfx_sidebar<>0 Then FreeImage gfx_sidebar
 If gfx_win<>0 Then FreeImage gfx_win
 If gfx_winbar<>0 Then FreeImage gfx_winbar
 If gfx_border(3)<>0 Then FreeImage gfx_border(3)
 If gfx_border(4)<>0 Then FreeImage gfx_border(4)
 modern_create_borders()
 modern_create_windows()
 Color 255,255,255
End Function
