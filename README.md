# 山海妖志 · 捉宠游戏

> KMP 原生双端 · 零 KEY 闭环 · 三重验证链全绿 · 原型 21 tab 全引擎覆盖

## 项目一句话

LBS 山海经收妖 + 图鉴 + 对战 + 养成的移动端游戏。Kotlin Multiplatform（shared 共享核 + Android Compose + iOS MapKit），JS 原型可独立运行。

## 快速开始

```powershell
# 原型（最快出结果）
cd shanhaiapp\prototype
node smoke_dom.js      # 75/75 零异常
node test_game.js      # 706/706 零漂移

# 全链验证
powershell -ExecutionPolicy Bypass -File shanhaiapp\verify_all.ps1

# Gradle（注意：中文路径需 robocopy 到 C:\temp）
robocopy "d:\1号\项目部\🎮游戏\捉宠游戏\shanhaiapp" "C:\temp\shanhaiapp_build" /E /XD "build" ".gradle" /PURGE
cd C:\temp\shanhaiapp_build
$env:JAVA_HOME='C:\Program Files\Java\jdk-17'
.\gradlew.bat :shared:jvmTest :composeApp:assembleDebug :composeApp:assembleRelease
```

## 技术栈

| 层 | 技术 |
|----|------|
| shared | Kotlin 2.0.21，K2 编译器 |
| Android | Compose Multiplatform + osmdroid 6.1.20 |
| iOS | 原生 MapKit（iosMain actual 占位） |
| 原型 | 单文件 index.html + game.js（零依赖） |
| 构建 | Gradle 8.7 + AGP 8.4.2 + Compose Compiler Gradle plugin |

## 零 KEY 终态

全项目高德/AMAP/LBS_KEY 残留：**零**。osmdroid（Apache 2.0，Release 3.4MB）+ OSM Web（原型 iframe）+ MapKit（iOS 待 macOS 桥接）。

## 三重验证链

| 层 | 结果 |
|----|------|
| JS 引擎断言 | 706/706 零漂移 |
| DOM 冒烟 | 75/75 零异常 |
| Kotlin jvmTest | 30/30 BUILD SUCCESSFUL |
| Android Debug | BUILD SUCCESSFUL（9.3 MB） |
| Android Release | BUILD SUCCESSFUL（3.4 MB） |

## 原型 21 Tab

| # | Tab | 覆盖引擎 |
|---|-----|---------|
| 1 | map | Location + Capture + Fatigue |
| 2 | codex | MonsterPool |
| 3 | gacha | Gacha |
| 4 | battle | Battle + RankBanPick + Weather |
| 5 | cultivate | Cultivation |
| 6 | quests | QuestLibrary |
| 7 | sect | SectTech + Social |
| 8 | equip | Equipment |
| 9 | dungeon | Dungeon |
| 10 | secret | SecretRealm |
| 11 | homestead | Homestead |
| 12 | store | Shop |
| 13 | season | Season |
| 14 | honor | Honor + Rank + Reputation |
| 15 | tutorial | Tutorial |
| 16 | platform | MockPlatform + DailyReset |
| 17 | lbs | Location（OSM iframe） |
| 18 | fuse | Fusion |
| 19 | formation | Formation + Combo |
| 20 | talent | Talent |
| 21 | activity | Activity |

## 重要文件索引

| 我想知道... | 去哪看 |
|------------|--------|
| 所有凭证/路径/版本 | `.workbuddy/credentials.md` |
| 项目健康度/验证数字 | `工作台帐.md ①仪表盘` |
| 活跃阻断 | `工作台帐.md ①活跃问题` |
| 当前任务进度 | `工作台帐.md ⑦任务卡` |
| 规则（自主循环） | `AGENTS.md` |
| 凭证速查 Skill | `.trae/skills/project-credentials/SKILL.md` |

## 仓库信息

- GitHub：https://github.com/stvode-cyber/shanhaiapp
- 分支：master（默认）
- 敏感文件已 gitignore（keystore、local.properties、__pycache__）

## 活跃阻断

| # | 为什么阻断 | 状态 |
|---|-----------|------|
| P17 | gradle 中文路径+emoji → JNI error | 绕过：robocopy 到 C:\temp |
| P29 | iOS KMP framework 需 macOS | 占位：iosMain actual 已写好，等 macOS podInstall |
