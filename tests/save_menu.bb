; Shared by native macOS and both Windows edition smoke checks.
; Requires a loaded scene and an isolated runtime with no existing saves.
Local preview_count=0,preview_image=map_image,preview_cwd$
load_savegames(0)
For preview_entry.Tsg=Each Tsg
 preview_count=preview_count+1
Next
preview_check(preview_count=0,"save menu starts with an empty test directory")
preview_check(map_image=preview_image And ImageWidth(map_image)=96,"save enumeration without preview retains the current image")
load_savegames()
preview_check(map_image=0,"empty save menu releases the previous preview")
load_savegames()
preview_check(map_image=0,"empty save menu can be reopened")

preview_check(save_map("saves\PREVIEW-ONE.sav","sav",""),"create save menu fixture")
CopyFile "saves\PREVIEW-ONE.sav","saves\PREVIEW-TWO.sav"
For preview_cycle=1 To 2
 load_savegames()
 preview_count=0
 For preview_entry.Tsg=Each Tsg
  preview_count=preview_count+1
 Next
 preview_check(preview_count=2 And in_opt(0)=1,"save menu selects the first of two saves")
 preview_check(ImageWidth(map_image)=96 And ImageHeight(map_image)=72,"save menu creates a live preview on each visit")
 DrawBlock map_image,0,0
 Flip False
Next
load_map_header("saves\PREVIEW-TWO.sav")
preview_check(ImageWidth(map_image)=96 And ImageHeight(map_image)=72,"selecting another save replaces its preview")
DeleteFile "saves\PREVIEW-ONE.sav"
load_savegames()
preview_count=0
For preview_entry.Tsg=Each Tsg
 preview_count=preview_count+1
Next
preview_check(preview_count=1 And ImageWidth(map_image)=96,"save menu refreshes after deleting one save")
DeleteFile "saves\PREVIEW-TWO.sav"
load_savegames()
preview_count=0
For preview_entry.Tsg=Each Tsg
 preview_count=preview_count+1
Next
preview_check(preview_count=0 And map_image=0,"deleting the final save clears the preview")
load_savegames()
preview_check(map_image=0,"save menu reopens after deleting the final save")

For preview_cycle=1 To 2
 load_maps()
 preview_count=0
 For preview_entry.Tsg=Each Tsg
  preview_count=preview_count+1
 Next
 preview_check(preview_count>0 And ImageWidth(map_image)=96 And ImageHeight(map_image)=72,"map menu creates a live preview on each visit")
 DrawBlock map_image,0,0
 Flip False
Next
preview_image=map_image
load_maps(0)
preview_check(map_image=preview_image And ImageWidth(map_image)=96,"map enumeration without preview retains the current image")
preview_cwd$=CurrentDir()
CreateDir "preview-empty"
CreateDir "preview-empty\maps"
ChangeDir "preview-empty"
load_maps()
preview_check(map_image=0,"empty map menu releases the previous preview")
load_maps()
preview_check(map_image=0,"empty map menu can be reopened")
ChangeDir preview_cwd$
DeleteDir "preview-empty\maps"
DeleteDir "preview-empty"
