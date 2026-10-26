; The actual app bootstrap installs resources into an isolated writable profile.
root$=SystemProperty("AppDir")
If FileType(root$+"mods\Stranded II")<>2 Then RuntimeError "Bundle resources missing"
If FileType(root$+"core\modern.ini")<>1 Then RuntimeError "Bundle settings missing"
If FileType(root$+"mods\Stranded II\sys\ko_aliases.dat")<>1 Then RuntimeError "Bundle aliases missing"
If FileType(root$+"mods\Stranded II\saves")<>2 Then RuntimeError "Bundle save directory missing"
output=WriteFile(root$+"bundle-check.txt")
WriteLine output,root$
CloseFile output
End
