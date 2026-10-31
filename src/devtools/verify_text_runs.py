"""Compare cached bitmap-font text runs with the uncached glyph walk.

Compiles the actual localization, UTF-8 and bmpf layout functions with synthetic
glyph widths and a recording ko_glyph. Every layout is also produced by the
previous per-byte ko_localize/ko_code walk, kept below as the reference.
No window, font pages or GPU drawing are involved.
"""
from pathlib import Path
import argparse
import json
import subprocess

import blitz3d_toolchain as engine
from verify_timing import function


LAYOUT = ('bmpf_txt', 'bmpf_txt_c', 'bmpf_txt_r', 'bmpf_len', 'bmpf_trim', 'bmpf_txt_scroll')

HEADER = r'''
Global set_rootdir$=SystemProperty("APPDIR"),test_failures,test_trace$
Global report=WriteFile(set_rootdir$+"text-runs.txt")
Const bmpf_count=6
Dim bmpf_framesw(65535,bmpf_count)
Dim test_samples$(15)
'''

GLYPH = r'''Function ko_glyph(c,id,x,y)
 If c<32 Then Return
 test_trace$=test_trace$+c+":"+id+"@"+x+","+y+";"
End Function'''

# The walk the cache replaced: localize and decode the whole string on each call.
REFERENCE = r'''
Function ref_txt(x,y,txt$,id=0)
	txt$=ko_localize(txt$)
	Local strlen=Len(txt$)
	Local i,sign
	For i=1 To strlen
		sign=ko_code(txt$,i)
		If sign<>32 Then
			ko_glyph(sign,id,x,y)
			x=x+bmpf_framesw(sign,id)
		Else
			x=x+bmpf_framesw(32,id)
		EndIf
	Next
End Function

Function ref_txt_c(x,y,txt$,id=0)
	txt$=ko_localize(txt$)
	Local strlen=Len(txt$)
	Local i,sign,offset
	For i=1 To strlen
		sign=ko_code(txt$,i)
		If sign<>32 Then
			offset=offset+bmpf_framesw(sign,id)
		Else
			offset=offset+bmpf_framesw(32,id)
		EndIf
	Next
	x=x-(offset/2)
	For i=1 To strlen
		sign=ko_code(txt$,i)
		If sign<>32 Then
			ko_glyph(sign,id,x,y)
			x=x+bmpf_framesw(sign,id)
		Else
			x=x+bmpf_framesw(32,id)
		EndIf
	Next
End Function

Function ref_txt_r(x,y,txt$,id=0)
	txt$=ko_localize(txt$)
	Local strlen=Len(txt$)
	Local i,sign
	For i=strlen To 1 Step -1
		sign=ko_code(txt$,i)
		If sign<>32 Then
			x=x-bmpf_framesw(sign,id)
			ko_glyph(sign,id,x,y)
		Else
			x=x-bmpf_framesw(32,id)
		EndIf
	Next
End Function

Function ref_len(txt$,id=0)
	txt$=ko_localize(txt$)
	Local strlen=Len(txt$)
	Local x,i,sign
	For i=1 To strlen
		sign=ko_code(txt$,i)
		If sign<>32 Then
			x=x+bmpf_framesw(sign,id)
		Else
			x=x+bmpf_framesw(32,id)
		EndIf
	Next
	Return x
End Function

Function ref_trim$(txt$,id,maxlen)
 txt$=ko_localize(txt$)
 While ref_len(txt$,id)>maxlen
  If Len(txt$)=0 Then Return ""
  txt$=Left(txt$,ko_prev(txt$,Len(txt$)))
 Wend
 Return txt$
End Function

Function ref_txt_scroll(x#,y,txt$,id=0,ml,scroll#)
	txt$=ko_localize(txt$)
	Local sx#=x#
	Local strlen=Len(txt$)
	Local i,sign
	x#=x#+scroll#
	For i=1 To strlen
		sign=ko_code(txt$,i)
		If sign<>32 Then
			If x#>=sx# And x#+bmpf_framesw(sign,id)<=ml Then
				ko_glyph(sign,id,x,y)
			EndIf
			x#=x#+bmpf_framesw(sign,id)
		Else
			x#=x#+bmpf_framesw(32,id)
		EndIf
	Next
End Function
'''

FIXTURE = r'''
; Only loc_bind reads script variables; these layouts carry no bindings to capture.
Function var_get$(key$)
 Return ""
End Function

Function check(ok,label$)
 WriteLine report,"CHECK "+label$
 If Not ok Then test_failures=test_failures+1:WriteLine report,"FAIL "+label$
End Function

Function ref_layout$(kind,txt$,id)
 test_trace$=""
 Select kind
  Case 0:ref_txt(10,20,txt$,id)
  Case 1:ref_txt_c(200,20,txt$,id)
  Case 2:ref_txt_r(400,20,txt$,id)
  Case 3:ref_txt_scroll(10,20,txt$,id,90,-15)
  Case 4:Return ref_len(txt$,id)
  Case 5:Return ref_trim(txt$,id,40)
 End Select
 Return test_trace$
End Function

Function run_layout$(kind,txt$,id)
 test_trace$=""
 Select kind
  Case 0:bmpf_txt(10,20,txt$,id)
  Case 1:bmpf_txt_c(200,20,txt$,id)
  Case 2:bmpf_txt_r(400,20,txt$,id)
  Case 3:bmpf_txt_scroll(10,20,txt$,id,90,-15)
  Case 4:Return bmpf_len(txt$,id)
  Case 5:Return bmpf_trim(txt$,id,40)
 End Select
 Return test_trace$
End Function

Function same_layout(txt$,label$)
 Local kind,id,pass,expected$
 For id=0 To 5 Step 5
  For kind=0 To 5
   expected$=ref_layout(kind,txt$,id)
   ; The second pass is served from the cached run.
   For pass=1 To 2
    check(run_layout(kind,txt$,id)=expected$,label$+" kind="+kind+" font="+id+" pass="+pass)
   Next
  Next
 Next
End Function

Function run_count()
 Local run.Tkorun,n
 For run=Each Tkorun:n=n+1:Next
 Return n
End Function

For c=32 To 65535
 For id=0 To bmpf_count
  bmpf_framesw(c,id)=3+((c+id) Mod 9)
 Next
Next
loc_put("test.label","라벨 Label")
loc_put("test.bound","n=$n")
Local alias.Tkoalias=New Tkoalias
alias\original$="Alias source":alias\translated$="[[loc:test.label]] alias"
Local h=ko_hash(alias\original$)
alias\link=ko_heads(h):ko_heads(h)=Handle(alias)

test_samples$(0)=""
test_samples$(1)="A A"
test_samples$(2)="한 A"
test_samples$(3)="[[loc:test.label]]"
test_samples$(4)="Count: [[loc:test.bound|n=3132]] x"
test_samples$(5)="Alias source"
test_samples$(6)="[[loc:missing.key]]"
test_samples$(7)=Chr(200)+"x"+Chr(128)+Chr(233)+Chr(128)+"y"+Chr(240)+Chr(159)+Chr(152)+Chr(128)+"z"
test_samples$(8)=String("가나다 abc ",12)
; Same length and tail: one hash bucket holds both runs.
test_samples$(9)="xx12345678"
test_samples$(10)="yy12345678"
test_samples$(11)=" leading and trailing  "
test_samples$(12)="123"
test_samples$(13)="12"+"3"
test_samples$(14)="[[loc:test.label]]"+Chr(31)+"line"

For editing=0 To 1
 ko_editing=editing
 For i=0 To 14
  same_layout(test_samples$(i),"sample "+i+" editing="+editing)
 Next
Next
ko_editing=0

; Catalog changes after a run was cached must reach the next draw.
width_before=bmpf_len("[[loc:test.label]]",0)
loc_put("test.label","a considerably longer replacement label")
check(bmpf_len("[[loc:test.label]]",0)=ref_len("[[loc:test.label]]",0),"catalog change re-resolves cached run")
check(bmpf_len("[[loc:test.label]]",0)<>width_before,"catalog change alters cached width")
same_layout("Alias source","alias after catalog change")

; Unique per-frame texts are bounded by the cache limit.
For i=1 To Cko_run_limit+904
 bmpf_len("unique "+i,0)
 If ko_run_entries>Cko_run_limit Then Exit
Next
check(ko_run_entries<=Cko_run_limit,"cache stays within its limit")
check(run_count()=ko_run_entries,"cache entry count matches stored runs")
For i=0 To 14
 same_layout(test_samples$(i),"sample "+i+" after flush")
Next

WriteLine report,"FAILURES "+test_failures
CloseFile report
End
'''


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    output = args.output.resolve()
    output.mkdir(parents=True, exist_ok=False)
    # Missing catalog keys are logged under core/, as in the game runtime.
    (output / 'core').mkdir()
    includes = engine.ROOT / 'src/engine/includes'
    unicode = (includes / 'ko_unicode.bb').read_text('utf8')
    # Font pages need a graphics context; widths are synthetic and glyphs recorded.
    unicode = unicode.replace(function(unicode, 'ko_font_load'), '')
    unicode = unicode.replace(function(unicode, 'ko_glyph'), GLYPH)
    fonts = (includes / 'load_bmpf.bb').read_text('utf8')
    body = HEADER + (includes / 'localization.bb').read_text('utf8') + '\n' + unicode + '\n'
    body += '\n'.join(function(fonts, name) for name in LAYOUT) + '\n' + REFERENCE + FIXTURE
    fixture, exe = output / 'text-runs.bb', output / 'text-runs.exe'
    fixture.write_text(body, encoding='utf8')
    engine.compile_test(fixture, exe, graphics=False, cwd=output)
    subprocess.run([str(exe)], cwd=output, check=True, timeout=60,
                   creationflags=subprocess.CREATE_NO_WINDOW)
    result = (output / 'text-runs.txt').read_text('utf8')
    assert 'FAILURES 0' in result and '\nFAIL ' not in '\n' + result, result
    checks = result.count('CHECK ')
    report = {'layout_checks': checks, 'cached_runs_match_uncached_walk': 'pass',
              'rendering': 'not tested; recorded ko_glyph calls with synthetic widths'}
    (output / 'report.json').write_text(json.dumps(report, indent=2) + '\n', encoding='utf8')
    print('Text run cache checks passed:', json.dumps(report))


if __name__ == '__main__':
    main()
