"""
山海妖志 · Windows 桌面壳
用 pywebview 把 prototype/index.html 包成原生窗口

运行：python app.py
"""
import webview
import os
import sys

def get_base_path():
    """定位 prototype/index.html 的绝对路径"""
    # app.py 在 shanhaiapp/windows/ 下，prototype 在 shanhaiapp/prototype/ 下
    base = os.path.dirname(os.path.abspath(__file__))
    return os.path.join(base, '..', 'prototype', 'index.html')

def main():
    index_path = get_base_path()
    if not os.path.exists(index_path):
        print(f"ERROR: index.html not found at {index_path}")
        sys.exit(1)

    window = webview.create_window(
        title='山海妖志 v1.0',
        url=index_path,
        width=1280,
        height=800,
        min_size=(1024, 700),
        resizable=True,
        text_select=True,      # 允许文本选择
        easy_drag=False,       # 防止拖拽误触
    )

    # 启用开发者工具（F12 或 Ctrl+Shift+I）
    webview.start(debug=False)

if __name__ == '__main__':
    main()
