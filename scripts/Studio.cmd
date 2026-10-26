@echo off
setlocal
cd /d "%~dp0.." || exit /b 1

if defined S2_STUDIO_PYTHON (
    "%S2_STUDIO_PYTHON%" -X utf8 src\devtools\studio_launch.py %*
) else if exist ".venv\Scripts\python.exe" (
    ".venv\Scripts\python.exe" -X utf8 src\devtools\studio_launch.py %*
) else (
    where py >nul 2>nul
    if errorlevel 1 (
        python -X utf8 src\devtools\studio_launch.py %*
    ) else (
        py -3 -X utf8 src\devtools\studio_launch.py %*
    )
)
set "studio_exit=%errorlevel%"
if not "%studio_exit%"=="0" pause
exit /b %studio_exit%
