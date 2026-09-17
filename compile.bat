@echo off
echo === Compiling P2P Chat ===
cd /d "%~dp0"

if not exist out mkdir out

javac -encoding UTF-8 -d out -sourcepath src ^
  src\server\UserStore.java ^
  src\server\Server.java ^
  src\client\P2PConnection.java ^
  src\client\P2PServer.java ^
  src\client\Client.java ^
  src\client\gui\ChatPanel.java ^
  src\client\gui\MainFrame.java ^
  src\client\gui\LoginFrame.java ^
  src\client\gui\App.java

if %ERRORLEVEL% NEQ 0 (
    echo.
    echo [FAIL] Compile that bai!
    pause
    exit /b 1
)
echo [OK] Compile thanh cong!
echo.
