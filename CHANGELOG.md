# CHANGELOG · 变更日志索引

> 本文件只做「索引」与「状态管理」，不承载详细变更正文。每次重要变更在此留一条链接式记录，正文写在对应模块子文件或 `93_讨论与处理/` 专题中。
> 格式：`YYYY-MM-DD · 范围 · 一句话结论（→ 指向详处）`

---

## 2026-10

- **2026-10-17 · GitHub 推送** · 项目首次 init + 推送到 `https://github.com/stvode-cyber/shanhaiapp`，4 个 commit，敏感文件（keystore/local.properties）全 gitignore（→ 仓库首页）。
- **2026-10-17 · 凭证集中化** · 新建 `.workbuddy/credentials.md`（独立凭证/路径/版本/命令速查）+ 项目级 Skill `.trae/skills/project-credentials/SKILL.md`，AGENTS §11 升级为四件套交接（→ 凭证文件）。
- **2026-10-17 · 台账体系瘦身** · RCC-05/06/07 简化为一行一坑、维护 hack 去重指 credentials.md、新增任务卡+进度条结构、shanhaiapp 下 6 个历史 md 归档、STATUS.md 瘦身为纯导航（→ 工作台帐）。
- **2026-10-17 · 零 KEY 终态闭环** · 高德 AAR 依赖清零、PlatformBridge 全 osmdroid、Release APK 70MB→3.4MB、三重验证链全绿 706/706 + 75/75 + 30/30 + Debug + Release（→ 根目录 README）。
- **2026-10-17 · 原型 21 tab 全引擎** · 活动日历 tab + 信誉分层仪表 + FusionEngine 6 配方 + 21 tab 全部引擎深接，GameState→原型完全镜像（→ shanhaiapp/prototype/）。
- **2026-10-01 · Kotlin 2.0 + Compose 恢复** · 升 Kotlin 2.0.21 + AGP 8.4.2 + Compose Compiler Gradle plugin，K2 零兼容问题；composeApp 改为 KMP application；iosMain actual 用 platform.Foundation 占位。

---

## 2026-08

- **2026-08-19 · 文档架构** · 确立「总纲先行 + 模块化管理 + 讨论与正式分离」规约，建立 `00_工程总纲`/`STATUS`/`CHANGELOG`/各模块总纲/`90_文档规约`/`93_讨论与处理` 骨架（→ 本目录新结构）。
- **2026-08-18 · 原型** · 零依赖 JS 原型交付，`test_game.js` 591/591 + `smoke_dom.js` 54/54 全过（→ `shanhaiapp/prototype/`）。
- **2026-08-17 · 验证收口** · 纯客户端可验证断言完整收口 1502/1502，双端镜像 0/0/0（→ `shanhaiapp/验证收口总览.md`）。
- **2026-08-16 · 契约占位** · 六类平台契约（#180–#184 云同步/每日重置/社交GPS/AR/地图SDK）双端闭环 + Python 契约断言（→ `shanhaiapp/GDD覆盖审计.md`）。
- **2026-08-13 · 基线** · GDD 整合版与核心切片起步（→ 《山海妖志》游戏设计文档（GDD·整合版）.md）。

---

## 历史归档

- 逐日详细进展与计数同步：`.workbuddy/memory/2026-08-13.md` … `2026-08-19.md`
- 累计演进计数（967→1109→1425→1487→1501→1502）与踩坑沉淀：`shanhaiapp/验证收口总览.md`
- 模块级交付说明（legacy，待拆入模块子文件）：`shanhaiapp/信誉分交付说明.md`、`shanhaiapp/阵容羁绊交付说明.md`
