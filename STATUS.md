# STATUS · 整体状态看板

> **只做导航，不承载实时数据**。所有数字、状态、阻断 → 看工作台帐。
> 最后更新：2026-10-17。

---

## 去哪看？

| 我想知道... | 去哪看 |
|------------|--------|
| 项目健康度（测试数/构建/tab数） | [`工作台帐.md ① 仪表盘`](./工作台帐.md) |
| 当前任务进度（目标/动作%） | [`工作台帐.md ⑦ 任务卡`](./工作台帐.md) |
| 活跃阻断/P 问题 | [`工作台帐.md ① 活跃问题`](./工作台帐.md) |
| 可验证基线（命令怎么跑） | [`工作台帐.md ⑦ 任务卡`](./工作台帐.md) + [credentials.md](./.workbuddy/credentials.md) |
| 模块索引 | [`00_工程总纲.md 五`](./00_工程总纲.md#五模块索引导航) |
| 最高指令（规则） | [`AGENTS.md`](./AGENTS.md) |
| 变更历史 | [`CHANGELOG.md`](./CHANGELOG.md) |
| 模块引擎→Screen 绑定 | 各模块 `XX_模块/00_总纲.md` 的「对应引擎与 UI」表 |
| 新 AI 接手三件套 | `credentials.md` + `工作台帐①仪表盘` + `工作台帐⑦任务卡` |

---

## 一句话状态

**v1.2.0-release 交付**：三重验证链全绿（706+75+37 + Debug/Release 3.59MB 正式签名），19 Compose Screen 全接 LocalGameState，存档持久化上线（SaveManager + SharedPreferences），零 KEY 闭环。Windows 上核心功能已全部完成。

---

## 已知待接入（均非核心阻断）

| 项 | 原因 | 平台限制 |
|----|------|---------|
| iOS framework 生成 | 需 macOS + pod install | ✅ 硬限 |
| AR 摄像头运行时 | 纯可选，CaptureEngine 契约已钉死 | 平台选型 |
| 服务端权威（GPS/云同步/社交） | 后端职责 | 后端待选型 |
| pywebview Windows 桌面壳 | 非核心，有 Python 脚本可跑 | 可选 |
