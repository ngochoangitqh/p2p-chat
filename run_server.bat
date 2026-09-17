@echo off
chcp 65001 > nul
echo === Starting P2P Chat Server ===
cd /d "%~dp0"
call compile.bat
if %ERRORLEVEL% NEQ 0 exit /b 1
java -Dfile.encoding=UTF-8 -cp out server.Server
pause
