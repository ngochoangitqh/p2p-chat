@echo off
chcp 65001 >nul
title Day code len GitHub - ngochoangitqh/p2p-chat

set PATH=C:\Users\ACER\.gemini\antigravity\scratch\mingit\cmd;C:\Users\ACER\.gemini\antigravity\scratch\mingit\mingw64\bin;%PATH%
set REPO_URL=https://github.com/ngochoangitqh/p2p-chat.git

echo ========================================================
echo        ĐANG ĐẨY MÃ NGUỒN P2P CHAT LÊN GITHUB
echo ========================================================
echo.
echo [1/3] Cấu hình remote: %REPO_URL%
git remote remove origin >nul 2>&1
git remote add origin %REPO_URL%

echo [2/3] Kiểm tra nhánh main...
git branch -M main

echo [3/3] Đang đẩy lên GitHub...
echo (Git sẽ mở trình duyệt Edge của bạn để xác thực tài khoản ngochoangitqh)
echo (Bạn chỉ cần bấm nút xanh "Authorize" hoặc "Sign in with your browser")
echo.
git push -u origin main

echo.
if %ERRORLEVEL% equ 0 (
    echo ========================================================
    echo  [OK] ĐÃ ĐẨY MÃ NGUỒN LÊN GITHUB THÀNH CÔNG!
    echo  Link repo: https://github.com/ngochoangitqh/p2p-chat
    echo ========================================================
) else (
    echo [!] Chưa hoàn tất việc đẩy code.
)
echo.
pause
