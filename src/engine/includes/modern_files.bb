; Same-directory staging and checked replacement. Never truncate a live save.
Global modern_file_error

Function modern_flush_file(path$)
 Local descriptor=modern_file_open(path$,$40000000,3,0,3,0,0)
 If descriptor=-1 Then modern_file_error=modern_last_error():Return False
 Local ok=modern_file_flush_handle(descriptor)
 If ok=0 Then modern_file_error=modern_last_error()
 modern_handle_close(descriptor)
 Return ok
End Function

Function modern_commit_file(path$,staged$,backup=1)
 modern_file_error=0
 If FileType(staged$)<>1 Then Return False
 If modern_flush_file(staged$)=0 Then Return False
 If backup And FileType(path$)=1 Then
  If modern_file_copy(path$,path$+".bak.tmp",0)=0 Then modern_file_error=modern_last_error():Return False
  If modern_flush_file(path$+".bak.tmp")=0 Then Return False
  If modern_file_move(path$+".bak.tmp",path$+".bak",9)=0 Then modern_file_error=modern_last_error():Return False
 EndIf
 ; MOVEFILE_REPLACE_EXISTING | MOVEFILE_WRITE_THROUGH, on the same volume.
 If modern_file_move(staged$,path$,9)=0 Then modern_file_error=modern_last_error():Return False
 Return True
End Function

Function modern_stamp(path$,infoBank)
 Local descriptor=modern_file_find(path$,infoBank)
 If descriptor=-1 Then Return False
 modern_find_close(descriptor)
 Return True
End Function

Function modern_stamp_newer(low,high,otherlow,otherhigh)
 ; FILETIME consists of two unsigned DWORDs. Compare without float rounding.
 If high<>otherhigh Then Return (high Xor $80000000)>(otherhigh Xor $80000000)
 Return (low Xor $80000000)>(otherlow Xor $80000000)
End Function
