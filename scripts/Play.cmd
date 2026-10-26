@echo off
cd /d "%~dp0..\build\runtime" || exit /b 1
if not exist "StrandedII.exe" (
    echo Run setup and build first. See README.md.
    pause
    exit /b 1
)
start "" "StrandedII.exe" %*
