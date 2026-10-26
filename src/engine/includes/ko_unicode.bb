; UTF-8 byte boundaries, per-language bitmap glyphs and display aliases.
; Original Stranded II copyright remains with Unreal Software.
Dim ko_pages(6,63)
Dim ko_index(65535)
Dim ko_offset(65535,6),ko_inkwidth(65535,6)
Global ko_cellw,ko_cellh
Dim ko_heads(4095)
Global ko_editing=0
Type Tkoalias
 Field original$,translated$,link
End Type

Function ko_cont(b)
 Return b>=128 And b<192
End Function

; Positions used by the editor are zero-based byte boundaries.
Function ko_floor(t$,p)
 If p<0 Then Return 0
 If p>Len(t$) Then p=Len(t$)
 While p>0 And p<Len(t$)
  If Not ko_cont(Asc(Mid(t$,p+1,1))) Then Exit
  p=p-1
 Wend
 Return p
End Function

Function ko_prev(t$,p)
 Return ko_floor(t$,p-1)
End Function

Function ko_next(t$,p)
 If p>=Len(t$) Then Return Len(t$)
 p=p+1
 While p<Len(t$)
  If Not ko_cont(Asc(Mid(t$,p+1,1))) Then Exit
  p=p+1
 Wend
 Return p
End Function

Function ko_code(t$,i)
 Local b=Asc(Mid(t$,i,1)),b2,b3
 If b<128 Then Return b
 If b<192 Then Return 0
 b2=Asc(Mid(t$,i+1,1))
 If b>=194 And b<224 And ko_cont(b2) Then Return (b-192)*64+b2-128
 b3=Asc(Mid(t$,i+2,1))
 If b>=224 And b<240 And ko_cont(b2) And ko_cont(b3) Then Return (b-224)*4096+(b2-128)*64+b3-128
 Return 63
End Function

Function ko_hash(t$)
 Local h=0,i
 For i=1 To Len(t$)
  h=(h*33+Asc(Mid(t$,i,1))) And 4095
 Next
 Return h
End Function

Function ko_alias_load()
 Local f=ReadFile("sys\ko_aliases.dat"),i,n,h,a.Tkoalias
 If f=0 Then Return
 n=ReadInt(f)
 For i=1 To n
  a=New Tkoalias
  a\original$=ReadString(f)
  a\translated$=ReadString(f)
  h=ko_hash(a\original$)
  a\link=ko_heads(h)
  ko_heads(h)=Handle(a)
 Next
 CloseFile f
End Function

Function ko_localize$(t$)
 If ko_editing Then Return t$
 Local a.Tkoalias=Object.Tkoalias(ko_heads(ko_hash(t$)))
 While a<>Null
  If a\original$=t$ Then Return loc_resolve(a\translated$)
  a=Object.Tkoalias(a\link)
 Wend
 Return loc_resolve(t$)
End Function

Function ko_font_load(id)
 Local path$=set_rootdir$+"core\locales\"+loc_language$+"\"
 Local f=ReadFile(path$+"glyphs.dat"),n,i,c,nw,tw,nx,tx,ni,ti,pages,p
 If f=0 Then RuntimeError "Language font missing. Run src/devtools/dev.py localize."
 If ReadInt(f)<>$32544E46 Then RuntimeError "Invalid language font format"
 n=ReadInt(f)
 ko_cellw=ReadInt(f):ko_cellh=ReadInt(f)
 If n<1 Or n>65536 Or ko_cellw<1 Or ko_cellw>64 Or ko_cellh<>19 Then RuntimeError "Invalid language font dimensions"
 If FileSize(path$+"glyphs.dat")<>16+n*28 Then RuntimeError "Invalid language font metadata"
 For i=0 To n-1
  c=ReadInt(f):nw=ReadInt(f):tw=ReadInt(f)
  nx=ReadInt(f):tx=ReadInt(f):ni=ReadInt(f):ti=ReadInt(f)
  If c<32 Or c>65535 Or nw<1 Or tw<1 Or ni<1 Or ni>ko_cellw Or ti<1 Or ti>ko_cellw Then RuntimeError "Invalid language glyph"
  ko_index(c)=i+1
  If id=5 Then
   bmpf_framesw(c,id)=tw:ko_offset(c,id)=tx:ko_inkwidth(c,id)=ti
  Else
   bmpf_framesw(c,id)=nw:ko_offset(c,id)=nx:ko_inkwidth(c,id)=ni
  EndIf
 Next
 CloseFile f
 If ko_index(63)=0 Or ko_index(32)=0 Then RuntimeError "Language font lacks question mark/space"
 For c=32 To 65535
  If ko_index(c)=0 Then bmpf_framesw(c,id)=bmpf_framesw(63,id)
 Next
 pages=(n+1023)/1024
 For p=0 To pages-1
  ko_pages(id,p)=LoadImage(path$+id+"_"+p+".bmp")
  If ko_pages(id,p)=0 Then RuntimeError "Language glyph page missing"
  MaskImage ko_pages(id,p),255,0,255
 Next
 For c=0 To 31:bmpf_framesw(c,id)=0:Next
End Function

Function ko_glyph(c,id,x,y)
 If c<32 Then Return
 If ko_index(c)=0 Then c=63
 Local idx=ko_index(c)-1
 DrawImageRect ko_pages(id,idx/1024),x+ko_offset(c,id),y,(idx Mod 32)*ko_cellw,((idx Mod 1024)/32)*ko_cellh,ko_inkwidth(c,id),ko_cellh
End Function
