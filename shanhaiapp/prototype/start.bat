@echo off
chcp 65001 >nul
echo ============================================
echo   山海妖志 · 原型一键启动
echo ============================================
echo.
echo [1/2] 跑测试（test_game + smoke_dom）...
node test_game.js
echo.
node smoke_dom.js
echo.
echo [2/2] 启动本地 HTTP 服务（端口 8765）...
echo       浏览器打开: http://localhost:8765/index.html
echo       Ctrl+C 停止服务
echo.
python -m http.server 8765
