@echo off
chcp 65001 >nul
echo ========================================================
echo        ĐẨY MÃ NGUỒN P2P CHAT LÊN GITHUB
echo ========================================================
echo.
set GIT_EXE="C:\Users\ACER\.gemini\antigravity\scratch\mingit\cmd\git.exe"

set /p REPO_URL="Nhập URL repository GitHub của bạn (mặc định: https://github.com/ngochoangitqh/p2p-chat.git): "
if "%REPO_URL%"=="" (
    set REPO_URL=https://github.com/ngochoangitqh/p2p-chat.git
)

echo.
echo [1/3] Cấu hình remote origin: %REPO_URL%
%GIT_EXE% remote remove origin >nul 2>&1
%GIT_EXE% remote add origin %REPO_URL%

echo [2/3] Đóng gói các thay đổi mới nhất...
%GIT_EXE% branch -M main
%GIT_EXE% add .
%GIT_EXE% commit -m "feat: P2P Chat application with hybrid architecture and HashMap message storage" >nul 2>&1

echo [3/3] Đang đẩy (push) lên GitHub...
%GIT_EXE% push -u origin main

echo.
if %ERRORLEVEL% equ 0 (
    echo [OK] Đã đẩy code lên GitHub thành công!
) else (
    echo [!] Quá trình push chưa hoàn tất. Vui lòng kiểm tra quyền truy cập hoặc Personal Access Token.
)
echo.
pause
