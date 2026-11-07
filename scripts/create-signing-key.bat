@echo off
chcp 65001 >nul
rem MorningCaller: create release signing key (double-click to run)
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0create-signing-key.ps1" %*
echo.
pause
