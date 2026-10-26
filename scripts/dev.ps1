param(
    [Parameter(Position=0, Mandatory=$true)]
    [ValidateSet('setup','localize','build','run','check')][string]$Action,
    [string]$Python = 'python',
    [Parameter(ValueFromRemainingArguments=$true)][string[]]$Arguments
)
$ErrorActionPreference = 'Stop'
& $Python -X utf8 (Join-Path $PSScriptRoot '../src/devtools/dev.py') $Action @Arguments
exit $LASTEXITCODE
