@echo off
chcp 65001 >nul
cd /d "%~dp0"
echo ============================================
echo   山海妖志 · Windows 桌面壳
echo ============================================
echo.
python app.py
pause
