"""
山海妖志 · Windows 桌面壳 v1.2.0-release
用 pywebview 把 prototype/index.html 包成原生窗口

快捷键：ESC 退出 · F11 全屏切换 · F12 开发者工具

运行：
  python app.py                # 开发模式
  双击 启动.bat                # 批处理启动
  dist/山海妖志.exe            # 打包后双击
"""
import webview
import os
import sys

VERSION = 'v1.2.0-release'

def get_base_path():
    """定位 prototype/index.html 的绝对路径

    支持两种布局：
    - 开发：windows/app.py + ../prototype/index.html（兄弟目录）
    - 打包：windows/app.py + prototype/index.html（同目录子文件夹）
    """
    base = os.path.dirname(os.path.abspath(__file__))
    # 优先找同目录 prototype（PyInstaller 打包后 datas 解压到此）
    same_dir = os.path.join(base, 'prototype', 'index.html')
    if os.path.exists(same_dir):
        return same_dir
    # 回退：找兄弟目录 prototype（开发布局）
    sibling = os.path.join(base, '..', 'prototype', 'index.html')
    return sibling

def main():
    index_path = get_base_path()
    if not os.path.exists(index_path):
        print(f"ERROR: index.html not found at {index_path}")
        print(f"Expected path: {index_path}")
        sys.exit(1)

    window = webview.create_window(
        title=f'山海妖志 {VERSION}',
        url=index_path,
        width=1280,
        height=800,
        min_size=(1024, 700),
        resizable=True,
        text_select=True,       # 允许文本选择
        easy_drag=False,         # 防止拖拽误触
        confirm_close=True,      # 关闭时确认（防止误关）
    )

    # ESC 默认关闭窗口（pywebview 自带）
    # 后续可加 F11 全屏等 — 用 JS 侧拦截 keydown 事件

    # debug=True 启动 F12 开发者工具；release 关掉
    webview.start(debug=False)

if __name__ == '__main__':
    main()
