# MEMORY · 长期记忆导航入口

> 本文件是长期记忆的**导航入口**（只指路，不堆详细日志）。详细记忆与每日日志统一维护在 `.workbuddy/memory/`。

## 一、记忆位置

- **长期笔记（跨会话有效）**：[`.workbuddy/memory/MEMORY.md`](./.workbuddy/memory/MEMORY.md) — 项目长期约定、技术路线、当前状态快照。
- **每日日志**：[`.workbuddy/memory/2026-08-19.md`](./.workbuddy/memory/2026-08-19.md) 等（按日期 Append-Only）。
- **自动化/任务**：见 WorkBuddy 自动化与任务列表。

## 二、当前状态快照（2026-10-18 v1.2.0-release）

- 三重验证链全绿：JS 706/706 + DOM 75/75 + jvmTest **37/37** + Debug + Release（3.59MB 正式签名 RSA 2048/25年）。
- 19 Compose Screen 全接 LocalGameState，无 mock 壳。存档持久化已上线（SaveManager + SharedPreferences + 自动 ON_PAUSE）。
- 源码：80 Kotlin 文件（57 shared + 22 composeApp + SaveManager.kt）。零 KEY 闭环（osmdroid + OSM Web）。
- 活跃阻断：P17 gradle 中文路径（已绕过 C:\temp）、P29 iOS 需 macOS（硬限）。
- 待接入：iOS framework、AR 摄像头运行时、服务端权威（后端职责）。

## 三、导航

- 返回：[`00_工程总纲`](./00_工程总纲.md) ｜ 状态：[`STATUS.md`](./STATUS.md) ｜ 变更：[`CHANGELOG.md`](./CHANGELOG.md)
