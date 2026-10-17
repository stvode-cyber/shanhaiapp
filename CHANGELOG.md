# CHANGELOG · 变更日志索引

> 本文件只做「索引」与「状态管理」，不承载详细变更正文。每次重要变更在此留一条链接式记录，正文写在对应模块子文件或 `93_讨论与处理/` 专题中。
> 格式：`YYYY-MM-DD · 范围 · 一句话结论（→ 指向详处）`

---

## 2026-10

- **2026-10-17 · 妖池分布对齐 GDD（精30/怪24/妖25/灵兽21）** · MonsterPool.kt 25 只 category 重分类 + verify_pool.py 4 断言同步，三重验证链全绿。GitHub 9f17af8（→ `90_文档规约/GDD对齐审计.md`）。
- **2026-10-17 · GDD 设定审计 + 五行矩阵 bug 修复** · Element.kt 五行矩阵 3 处对齐 GDD 附录，三重验证链全绿。GitHub cfa6e5f（→ `90_文档规约/GDD对齐审计.md`）。
- **2026-10-17 · 文档体系升级** · 17 模块总纲全量同步 v0.4.0（引擎→Screen 绑定表 + 状态修正）、00_工程总纲去过时、STATUS 瘦身为纯导航、每日日志补档。GitHub b54f349 + 66bc3c8（→ 各模块 00_总纲）。
- **2026-10-17 · AGENTS §11 瘦身** · 345→283 行，删模板示例，实时数据统一走工作台帐①⑦（→ AGENTS.md）。
- **2026-10-17 · v1.0.0-compose Release** · tag + push GitHub，三重验证链全绿（→ GitHub Releases）。
- **2026-10-17 · v0.4.0 视觉润色** · ShanhaiTheme 升级 Typography+Shapes+Dark、MainActivity 重构 Scaffold+5 核心 tab+Hub 网格+AnimatedContent 淡入淡出、HubScreen 18 功能入口+图鉴进度条。GitHub ba2912a（→ composeApp/）。
- **2026-10-17 · V4 收尾 3 Screen** · HonorScreen（荣誉勋章称号）+ TutorialScreen（新手引导步骤）+ ActivityScreen（限时活动聚合）。GitHub 0abe48a（→ composeApp/screens/）。
- **2026-10-17 · V3 副本社交 5 Screen** · DungeonScreen（四类副本）+ SecretScreen（4 主题秘境）+ HomesteadScreen（离线产出）+ SectScreen（宗门科技树）+ SeasonScreen（赛季通行证）。GitHub 272eaf9（→ composeApp/screens/）。
- **2026-10-17 · V2 养成经济 6 Screen** · CultivateScreen（升级/突破/进化）+ EquipScreen（装备列表）+ FuseScreen（融合预览）+ TalentScreen（三分支加点）+ StoreScreen（焚香购买）+ QuestsScreen（敕令列表）。GitHub f30cd36（→ composeApp/screens/）。
- **2026-10-17 · V1 核心循环 4 Screen** · MapScreen（osmdroid 巡妖图）+ CaptureScreen（光环收服）+ CodexScreen（妖典双筛选）+ BattleScreen（回合制对战）。GitHub d919e9d（→ composeApp/screens/）。
- **2026-10-17 · GitHub 推送** · 项目首次 init + 推送到 `https://github.com/stvode-cyber/shanhaiapp`，4 个 commit，敏感文件全 gitignore（→ 仓库首页）。
- **2026-10-17 · 凭证集中化** · 新建 `.workbuddy/credentials.md` + Skill `project-credentials`（→ 凭证文件）。
- **2026-10-17 · 台账体系瘦身** · RCC 简化 + 任务卡+进度条 + 模块总纲归档（→ 工作台帐）。
- **2026-10-17 · 零 KEY 终态闭环** · osmdroid 替换高德，Release APK 70MB→3.4MB（→ 根目录 README）。
- **2026-10-17 · 原型 21 tab 全引擎** · 活动日历+信誉分层+FusionEngine 6 配方（→ prototype/）。
- **2026-10-01 · Kotlin 2.0 + Compose 恢复** · Kotlin 2.0.21 + AGP 8.4.2 + Compose Compiler Gradle plugin（→ shared/）。

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
