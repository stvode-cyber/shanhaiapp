#!/bin/bash
# 山海妖志 · 原型一键启动（类 Unix）
echo "=== 山海妖志 原型启动 ==="
echo ""
echo "[1/2] 跑测试..."
node test_game.js
echo ""
node smoke_dom.js
echo ""
echo "[2/2] 启动 HTTP 服务 端口 8765 ..."
echo "      浏览器打开: http://localhost:8765/index.html"
echo "      Ctrl+C 停止"
python3 -m http.server 8765
