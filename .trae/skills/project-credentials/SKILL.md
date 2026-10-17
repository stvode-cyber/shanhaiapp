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

# 一键全链验证（6 步：node×2 + robocopy + gradlew×3）
powershell -ExecutionPolicy Bypass -File "d:\1号\项目部\🎮游戏\捉宠游戏\shanhaiapp\verify_all.ps1"

# Git 操作（仓库已存在，remote = origin）
cd "d:\1号\项目部\🎮游戏\捉宠游戏"
git push                         # 推到 GitHub
git tag -a vX.Y.Z -m "tag 说明" # 打版本标签
git push origin vX.Y.Z          # 推标签
gh release create vX.Y.Z ...    # 建 GitHub release
```

## 三、版本快照（v1.2.0-release · 2026-10-18）

| 组件 | 版本 |
|------|------|
| Kotlin | 2.0.21 |
| AGP | 8.4.2 |
| Compose BOM | 2024.09.03 |
| osmdroid | 6.1.20 |
| Gradle Wrapper | 8.7 |
| **GitHub Release** | **v1.0.0 + v1.0.0-compose**（15 commits · 最新 20fc076）|
| **最新验证** | **verify_all [ALL PASS]**：706/706 + 75/75 + 37/37 + Debug + Release 3.59MB 正式签名 |
| **Release keystore** | RSA 2048 / PKCS12 / 25 年 / SHA-256: 1387ee48d3fe3b4442f6027dc1aec74debf19fe68c680696a68c3708cc243423（密码存 credentials.md）|

## 四、零 KEY 确认

全项目高德/AMAP/LBS_KEY 残留：**零**。osmdroid（Android）+ OSM Web（原型）+ MapKit（iOS 待接）。

## 五、GitHub 仓库

- URL：`https://github.com/stvode-cyber/shanhaiapp`
- default branch：master
- remote name：origin
- gh CLI 已登录（account: stvode-cyber，scope: repo）
- 敏感文件 gitignore：keystore / local.properties / __pycache__ / build/ / .gradle/

## 六、完整信息去哪找

- 凭证细节 + 变更日志 → `.workbuddy/credentials.md`
- 活跃阻断/可验证基线 → `工作台帐.md ①仪表盘`
- 维护 hack（独有坑）→ `工作台帐.md ⚠️维护 hack`
- 规则（自主循环/验证纪律）→ `AGENTS.md`
