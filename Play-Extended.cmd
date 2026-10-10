@echo off
cd /d "%~dp0build\extended\runtime" || exit /b 1
if not exist "StrandedII.exe" (
    echo Run setup and build with --edition extended first. See docs/development.md.
    pause
    exit /b 1
)
start "" "StrandedII.exe" %*
