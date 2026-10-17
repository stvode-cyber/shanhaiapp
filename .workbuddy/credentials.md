# Project Credentials & Paths · 山海妖志

> **独立集中记录所有凭证、路径、版本信息。新 AI 接手先读这个文件。**
> 最后更新：2026-10-17。**零 KEY 闭环**——本项目不使用任何 API secret。

---

## 一、环境路径（硬编码，不能错）

| 资源 | 路径 | 备注 |
|------|------|------|
| **项目根** | `d:\1号\项目部\🎮游戏\捉宠游戏` | 含 emoji 目录名 |
| **JDK (Temurin 17)** | `C:\Program Files\Java\jdk-17` | JAVA_HOME 必须指向这里 |
| **Android SDK** | `C:\Users\Administrator\Android\Sdk` | — |
| **Gradle Wrapper** | `shanhaiapp\gradlew.bat` | 版本 8.7 |
| **Kotlin** | 2.0.21（K2 编译器） | AGP 8.4.2，Compose Compiler Gradle plugin |

## 二、凭证文件位置

| 文件 | 路径 | 密码/说明 |
|------|------|----------|
| **debug keystore** | `shanhaiapp\keystores\debug.keystore` | 默认: `android` / `android` |
| **release keystore** | ⚠️ **未创建** | 需在 **C:\temp 干净路径**下用 keytool 生成，禁止在中文路径操作 |
| **local.properties** | `shanhaiapp\local.properties` | 需手动填 `sdk.dir=C\:\\Users\\Administrator\\Android\\Sdk`，已被 .gitignore 排除 |

## 三、零 KEY 终态确认

| 检查项 | 状态 |
|--------|------|
| AndroidManifest（3 份）高德 key | ✅ 无 |
| build.gradle.kts（shared + composeApp）高德 aar | ✅ 无 |
| proguard-rules.pro 高德规则 | ✅ 无 |
| PlatformBridge androidMain import | ✅ org.osmdroid |
| PlatformBridge iosMain 注释 | ✅ MapKit（零 KEY） |
| **全项目高德/AMAP/LBS_KEY 残留** | **✅ 零** |
| osmdroid User-Agent | ✅ OsmBootstrap.init() 已设 |

## 四、版本信息

| 组件 | 版本 | 位置 |
|------|------|------|
| Kotlin | 2.0.21 | gradle/libs.versions.toml |
| AGP | 8.4.2 | gradle/libs.versions.toml |
| Compose BOM | 2024.09.03 | gradle/libs.versions.toml |
| Compose Compiler | Gradle plugin（与 Kotlin 2.0.21 自动匹配） | composeApp/build.gradle.kts |
| osmdroid | 6.1.20 | shared + composeApp 各一处 build.gradle.kts |
| Gradle Wrapper | 8.7 | gradle/wrapper/gradle-wrapper.properties |

## 五、重要操作命令速查

```powershell
# ★ 硬坑：任何 gradle 前必须 robocopy 绕过中文路径
robocopy "d:\1号\项目部\🎮游戏\捉宠游戏\shanhaiapp" "C:\temp\shanhaiapp_build" /E /XD "build" ".gradle" /PURGE /NFL /NDL /NJH /NJS /nc /ns /np

cd C:\temp\shanhaiapp_build
$env:JAVA_HOME='C:\Program Files\Java\jdk-17'

# jvmTest
.\gradlew.bat :shared:jvmTest --no-daemon --console=plain

# Debug APK
.\gradlew.bat :composeApp:assembleDebug --no-daemon --console=plain

# Release APK
.\gradlew.bat :composeApp:assembleRelease --no-daemon --console=plain

# 一键全链验证
powershell -ExecutionPolicy Bypass -File "d:\1号\项目部\🎮游戏\捉宠游戏\shanhaiapp\verify_all.ps1"

# 原型验证
cd "d:\1号\项目部\🎮游戏\捉宠游戏\shanhaiapp\prototype"
node smoke_dom.js
node test_game.js
```

## 六、已知外部资源

| 资源 | 类型 | 是否需要 KEY | 说明 |
|------|------|-------------|------|
| OpenStreetMap | iframe（原型） | 否 | LBS tab 地图显示，本地浏览器加载可能被 CORS 拦 |
| osmdroid | Android SDK | 否 | Apache 2.0，Release 3.4MB |
| MapKit | iOS SDK | 否 | Apple 原生，iosMain actual 待 macOS 桥接 |

## 七、项目状态快照（2026-10-17）

- **DoD §2 五条**：全部满足
- **三重验证链**：706/706 + 75/75 + jvmTest 30/30 + Debug + Release 全绿
- **原型 tab**：21 全引擎覆盖
- **活跃阻断**：P17 gradle 中文路径、P29 iOS 需 macOS（均为硬限）
- **Windows 可推进空间**：基本为零

---

## 八、变更日志

| 日期 | 版本 | 变更 |
|------|------|------|
| 2026-10-17 | v1.0 | 初始创建，集中所有凭证+路径+版本信息 |
