@echo off
setlocal
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0..\scripts\Stop-Cluster.ps1"
set "STOP_STATUS=%ERRORLEVEL%"
pause
exit /b %STOP_STATUS%
