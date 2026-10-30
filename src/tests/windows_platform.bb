; Actual Unicode file names and typed 64-bit Windows probe_handle marshalling.
CreateDir "한글 경로"
If FileType("한글 경로")<>2 Then RuntimeError "Unicode probe_directory creation"
probe_output=WriteFile("한글 경로\원본.txt")
If probe_output=0 Then RuntimeError "Unicode WriteFile"
WriteString probe_output,"UTF-8 저장 내용"
CloseFile probe_output
CopyFile "한글 경로\원본.txt","한글 경로\복사.txt"
If FileType("한글 경로\복사.txt")<>1 Then RuntimeError "Unicode CopyFile"
If modern_file_move("한글 경로\복사.txt","한글 경로\이동.txt",1)=0 Then RuntimeError "Unicode atomic move"
probe_input=ReadFile("한글 경로\이동.txt")
If ReadString(probe_input)<>"UTF-8 저장 내용" Then RuntimeError "Unicode readback"
CloseFile probe_input
probe_data=CreateBank(320)
probe_handle=modern_file_find("한글 경로\이동.txt",probe_data)
If probe_handle=-1 Or probe_handle=0 Then RuntimeError "64-bit find probe_handle"
If modern_find_close(probe_handle)=0 Then RuntimeError "64-bit find close"
probe_handle=modern_file_open("한글 경로\이동.txt",$40000000,3,0,3,0,0)
If probe_handle=-1 Or probe_handle=0 Then RuntimeError "64-bit file probe_handle"
If modern_file_flush_handle(probe_handle)=0 Then RuntimeError "File flush"
If modern_handle_close(probe_handle)=0 Then RuntimeError "64-bit probe_handle close"
If modern_file_open("한글 경로\없는 파일",$80000000,3,0,3,0,0)<>-1 Then RuntimeError "Invalid probe_handle sentinel"
If modern_last_error()<>2 Then RuntimeError "Native error preserved"
probe_directory=ReadDir("한글 경로")
found=0
Repeat
 name$=NextFile(probe_directory)
 If name$="이동.txt" Then found=1
Until name$=""
CloseDir probe_directory
If Not found Then RuntimeError "Unicode probe_directory enumeration"
; Legacy text input can leave a partial UTF-8 sequence in a typed save name.
; Every Windows path call must map it to the same file instead of failing.
probe_name$="한글 경로\저장"+Chr(195)
If FileType(probe_name$+".tmp")<>0 Then RuntimeError "Partial UTF-8 missing FileType"
probe_output=WriteFile(probe_name$+".tmp")
If probe_output=0 Then RuntimeError "Partial UTF-8 WriteFile"
WriteString probe_output,"partial"
CloseFile probe_output
If FileType(probe_name$+".tmp")<>1 Then RuntimeError "Partial UTF-8 FileType"
probe_handle=modern_file_open(probe_name$+".tmp",$40000000,3,0,3,0,0)
If probe_handle=-1 Or probe_handle=0 Then RuntimeError "Partial UTF-8 flush handle"
If modern_handle_close(probe_handle)=0 Then RuntimeError "Partial UTF-8 handle close"
If modern_file_move(probe_name$+".tmp",probe_name$+".sav",9)=0 Then RuntimeError "Partial UTF-8 commit"
probe_input=ReadFile(probe_name$+".sav")
If probe_input=0 Then RuntimeError "Partial UTF-8 ReadFile"
If ReadString(probe_input)<>"partial" Then RuntimeError "Partial UTF-8 readback"
CloseFile probe_input
DeleteFile probe_name$+".sav"
If FileType(probe_name$+".sav")<>0 Then RuntimeError "Partial UTF-8 DeleteFile"
If modern_counter_frequency(probe_data)=0 Then RuntimeError "Counter frequency"
If PeekInt(probe_data,0)=0 And PeekInt(probe_data,4)=0 Then RuntimeError "Counter frequency bank"
If modern_counter(probe_data)=0 Then RuntimeError "Counter bank"
modern_interrupt_time(probe_data)
FreeBank probe_data
End
