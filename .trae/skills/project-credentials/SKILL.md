---
name: project-credentials
description: 山海妖志项目凭证、路径、版本、版本控制信息集中查询。Use when the new AI接手、需要查环境路径、凭证文件位置、版本号、或 push GitHub 时. Do not use for代码逻辑或游戏设计问题.
---

# 山海妖志 · 凭证与环境速查

新 AI 接手时先看这个文件，然后读 `.workbuddy/credentials.md` 拿完整细节。

## 一、硬编码路径（别记错）

| 资源 | 路径 |
|------|------|
| **项目根** | `d:\1号\项目部\🎮游戏\捉宠游戏` |
| **JDK** | `C:\Program Files\Java\jdk-17` |
| **Android SDK** | `C:\Users\Administrator\Android\Sdk` |
| **gradle temp** | `C:\temp\shanhaiapp_build` |
| **完整凭证文件** | `.workbuddy/credentials.md` |

## 二、快速操作命令

```powershell
# ★ gradle 前必须 robocopy（中文路径 JNI error）
robocopy "d:\1号\项目部\🎮游戏\捉宠游戏\shanhaiapp" "C:\temp\shanhaiapp_build" /E /XD "build" ".gradle" /PURGE /NFL /NDL /NJH /NJS /nc /ns /np

cd C:\temp\shanhaiapp_build
$env:JAVA_HOME='C:\Program Files\Java\jdk-17'

# 一键全链验证
powershell -ExecutionPolicy Bypass -File "d:\1号\项目部\🎮游戏\捉宠游戏\shanhaiapp\verify_all.ps1"

# Git push 到 GitHub（首次）
cd "d:\1号\项目部\🎮游戏\捉宠游戏"
git remote add origin <你的GitHub仓库URL>
git branch -M main
git push -u origin main
```

## 三、版本快照（2026-10-17）

| 组件 | 版本 |
|------|------|
| Kotlin | 2.0.21 |
| AGP | 8.4.2 |
| Compose BOM | 2024.09.03 |
| osmdroid | 6.1.20 |
| Gradle Wrapper | 8.7 |

## 四、零 KEY 确认

全项目高德/AMAP/LBS_KEY 残留：**零**。osmdroid（Android）+ OSM Web（原型）+ MapKit（iOS 待接）。

## 五、完整信息去哪找

- 凭证细节 + 变更日志 → `.workbuddy/credentials.md`
- 活跃阻断/可验证基线 → `工作台帐.md ①仪表盘`
- 维护 hack（独有坑）→ `工作台帐.md ⚠️维护 hack`
- 规则（自主循环/验证纪律）→ `AGENTS.md`
