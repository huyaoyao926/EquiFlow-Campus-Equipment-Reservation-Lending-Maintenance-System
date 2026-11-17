@echo off
setlocal
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0..\scripts\Start.ps1" -OpenBrowser %*
set "START_STATUS=%ERRORLEVEL%"
pause
exit /b %START_STATUS%
