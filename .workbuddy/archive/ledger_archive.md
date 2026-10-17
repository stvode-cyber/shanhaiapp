# Ledger Archive · 归档区

> **已归零的根因簇 + 旧动作流水（>20条）+ 历史交付说明。**
> 活跃内容 → 工作台帐.md。这里只存"已关档"的东西。

---

## 一、已归零根因簇（RCC-01 ~ RCC-04 详细历史）

### RCC-01：引用/包名/类名错 · 11 次 ✅ 已归零

| # | 问题 | 解法 |
|---|------|------|
| 1 | SaveIntegrityEngine 包名错 | 修正 package 声明 |
| 2 | SectTechEngine 包名错 | 修正 package 声明 |
| 3 | CloudSyncBackend CloudEnvelope 内部类跨文件 | 改为 public class |
| 4 | GameState Faction 引用多余 | 删除未用 import |
| 5 | LocationEngine pow 未导入 | 加 kotlin.math.pow import |
| 6 | BattleSession Stats import 缺失 | 补 import |
| 7 | EquipmentEngine Stats import 缺失 | 补 import |
| 8 | CoreTest Codex import 缺失 | 补 import |
| 9 | 扩展函数 toCombatant 显式 import | 加 import |
| 10 | ARCaptureController interface 跨作用域 | 移到同文件 |
| 11 | 同上另一处 | 合并 |

### RCC-02：类型签名漂移 · 6 次 ✅ 已归零

| # | 问题 | 解法 |
|---|------|------|
| 1 | Codex newlyUnlocked 返回类型 | 对齐 JS 原型 |
| 2 | CloudSyncEngine encodeToString 歧义 | 显式指定 UTF-8 |
| 3 | SaveExportEngine encodeToString 同上 | 同上 |
| 4 | CaptureEngine Float→Double | 统一 Double |
| 5 | CoreTest 伤害期望值漂移 | 对齐公式 |
| 6 | CoreTest 克制环期望值漂移 | 对齐公式 |

### RCC-03：续行/语法坑 · 2 次 ✅ 已归零

| # | 问题 | 解法 |
|---|------|------|
| 1 | EquipmentEngine 多行运算符续行 | 合并为单行 |
| 2 | BattleIntegrityEngine 嵌套 associate | 改为 for 循环 |

### RCC-04：测试数据/逻辑 Bug · 5 次 ✅ 已归零

| # | 问题 | 解法 |
|---|------|------|
| 1 | CoreTest 非法函数名（×和<） | 替换为合法字符 |
| 2 | 炼妖融合 first()→filter | 修正筛选逻辑 |
| 3 | 期望值漂移 1 | 对齐 JS 原型 |
| 4 | 期望值漂移 2 | 对齐 JS 原型 |
| 5 | 期望值漂移 3 | 对齐 JS 原型 |

---

## 二、旧动作流水（#010 ~ #022，已归档）

```
#022 | 2026-10-01 20:10 | T17 高德地图 SDK Android 集成（后被 D21 废弃，换 osmdroid）
#021 | 2026-10-01 19:45 | iosMain actual 实现 + P29 状态漂移修正
#020 | 2026-10-01 19:15 | iOS target 配置完成（shared 加 iosX64/iosArm64 + cocoapods）
#019 | 2026-09-30 18:30 | Kotlin 2.0 + Compose Multiplatform 壳恢复（K2 零兼容问题）
#018 | 2026-10-01 18:00 | Skill 创建（ledger-growth + kmp-multiplatform-shell）+ AGENTS §11 交接台帐
#017 | 2026-10-01 16:00 | 台帐升级 v2.0（根因簇 RCC + 仪表盘 + 任务池）
#016 | 2026-10-01 17:00 | 原生壳开发完成（Android Compose + iOS 桥接层）
#015 | 2026-10-01 12:00 | CoreTest.kt 26→29/29 全绿
#014 | 2026-10-01 11:45 | compileTestKotlinJvm BUILD SUCCESSFUL → 暴露 3 个期望值漂移
#013 | 2026-10-01 11:00 | Kotlin 主代码 Bug 全面修复
#012 | (更早) | 原型搭建
#011 | (更早) | 引擎核心架构
#010 | (更早) | 项目初始化
```

---

## 三、已归档交付说明（原 shanhaiapp/ 下）

| 原路径 | 归档原因 |
|--------|----------|
| shanhaiapp/优化升级总汇.md | 历史过程记录，已并入工作台帐 |
| shanhaiapp/开发进度.md | 被工作台帐 ①仪表盘 + ⑥本轮总汇 取代 |
| shanhaiapp/验证收口总览.md | 历史验证记录，当前验证以 verify_all.ps1 为准 |
| shanhaiapp/编译验证清单.md | 被工作台帐 ①可验证基线 取代 |
| shanhaiapp/信誉分交付说明.md | 一次性交付文档，ReputationEngine 已并入原型 honor tab |
| shanhaiapp/阵容羁绊交付说明.md | 一次性交付文档，FormationEngine/ComboEngine 已并入原型 |

---

## 四、已归档动作流水（>10 条的旧记录）

> 2026-10-18 从工作台帐④裁剪归档。完整记录保留，供历史追溯。

```
#056 | 2026-10-17 21:40 | 限定神独立池 + 流派模板一键套用（引擎层+jvmTest）| commit 468dff2
#055 | 2026-10-17 21:00 | 妖池分布对齐 GDD — MonsterPool category 重分类 | verify_pool 1388/1388 ✅
#054 | 2026-10-17 20:00 | 设定审计 + 五行矩阵 bug 修复 | Element.kt 3 处修正
#053 | 2026-10-17 17:30 | 补完剩余：git推送 + AGENTS瘦身 + pywebview + CHANGELOG
#052 | 2026-10-17 | 文档体系升级 — 17 模块总纲 v0.4.0 同步 + 台账纠偏
#049 | 2026-10-17 | v0.4.0 视觉润色 — ShanhaiTheme + HubScreen + AnimatedContent
#048 | 2026-10-17 | V4 收尾 3 Screen — Honor/Tutorial/Activity
#047 | 2026-10-17 | V3 副本社交 5 Screen — Dungeon/Secret/Homestead/Sect/Season
#046 | 2026-10-17 | V2 养成经济 6 Screen — Cultivate/Equip/Fuse/Talent/Store/Quests
#045 | 2026-10-17 | V1 核心循环 4 Screen — Map/Capture/Codex/Battle
[原型同步深夜段 编号 023-027]
#027 | 2026-10-18 00:00 | 纠偏：DailyResetEngine + Q5 融合配方
#026 | 2026-10-17 23:55 | Q5 FusionEngine RECIPES 同步（1→6 条）
#025 | 2026-10-17 23:45 | Q4 ReputationEngine 分层仪表
#024 | 2026-10-17 23:15 | Q3 ActivityEngine 活动日历 tab
#023 | 2026-10-17 22:00 | Q1 verify_all.ps1 + Q2 FusionEngine 6 配方 + CoreTest 全绿
#022 | 2026-10-01 20:10 | 高德地图 SDK Android 集成（后 D21 废弃换 osmdroid）
#021 | 2026-10-01 19:45 | iosMain actual 实现 + P29 状态漂移修正
#020 | 2026-10-01 19:15 | iOS target 配置完成
#019 | 2026-09-30 18:30 | Kotlin 2.0 + Compose 壳恢复
#018 | 2026-10-01 18:00 | Skill 创建 + AGENTS §11 交接台帐
#017 | 2026-10-01 16:00 | 台帐升级 v2.0
#016 | 2026-10-01 17:00 | 原生壳开发完成
#015 | 2026-10-01 12:00 | CoreTest.kt 26→29/29
#014 | 2026-10-01 11:45 | compileTestKotlinJvm 暴露 3 期望值漂移
#013 | 2026-10-01 11:00 | Kotlin 主代码 Bug 全面修复
```

