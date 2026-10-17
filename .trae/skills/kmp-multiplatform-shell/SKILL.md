---
name: "kmp-multiplatform-shell"
description: "Kotlin Multiplatform 多 target 原生壳开发与依赖修复套路：纯 Android app 链 KMP library 的 variant 匹配、Compose/Kotlin 版本兼容、AGP 版本对齐、expect/actual 平台桥接写法。Invoke when 搭 KMP 原生壳、Android/iOS 桥接、或 Gradle 依赖变体报错。"
---

# KMP 多 Target 原生壳开发与依赖修复

## 何时调用

- 新项目搭 KMP（Kotlin Multiplatform）+ Android/iOS 原生壳
- 纯 Android application 链 KMP shared library 报 variant 不匹配
- Compose Multiplatform 壳与 Kotlin 版本不兼容
- AGP / Kotlin 版本对齐问题（比如 AGP 8.3 超出 Kotlin 1.9.23 测试范围）
- 写 expect/actual 平台桥接接口（FileExport / MapSdk / ArBridge 等）

## 核心原则

**KMP 平台依赖必须对等** — application 端和 library 端必须用同一套 KMP 插件体系，variant 才对得上。

## 坑 1：纯 Android application 链 KMP library → 报 variant 不匹配

### 症状

```
Could not resolve project :shared.
Required project :composeApp:debugRuntimeClasspath
Consumer attributes: ..., AndroidBuildType='debug'
But the selected variant does not match these attributes.
```

### 根因

纯 `com.android.application` 插件的模块，没有 KMP 体系的 variant 命名规则（debugAndroid / releaseAndroid），无法匹配 `kotlin-multiplatform` library 产出的 Android 变体。

### 解法

**application 端也加 `kotlin-multiplatform` 插件**，变成 KMP application：

```kotlin
// composeApp/build.gradle.kts
plugins {
    alias(libs.plugins.kotlinMultiplatform)   // ← 关键！
    alias(libs.plugins.androidApplication)
}
android {
    namespace = "com.example.app"
    compileSdk = 34
    // ...
}
kotlin {
    androidTarget()
    sourceSets {
        val androidMain by getting {
            dependencies {
                implementation(project(":shared"))  // 现在能正确链接
            }
        }
    }
}
```

### 配套目录结构

KMP application 的源码要从 `src/main/` 移到 `src/androidMain/`：

```
composeApp/src/
└── androidMain/
    ├── AndroidManifest.xml
    ├── kotlin/com/example/app/MainActivity.kt
    └── res/
```

## 坑 2：Compose Multiplatform 壳需 Kotlin 2.0+

### 症状

```
e: Compose Compiler version 1.6.3 (or 1.7.x) requires Kotlin 2.0+
当前项目 Kotlin = 1.9.23
```

### 根因

Compose Compiler 1.6+ 开始只兼容 Kotlin 2.0 的 K2 编译器，Kotlin 1.9.x 无法使用。

### 解法（两条路）

| 方案 | 适用场景 | 操作 |
|------|----------|------|
| **A. 升 Kotlin 2.0** | 项目可接受迁移成本 | Kotlin 1.9.23 → 2.0.20；AGP 8.2 → 8.4+；Compose Compiler 随 BOM |
| **B. 暂用原生 View** | 快速验证功能，不急 UI 现代化 | 去掉 Compose 依赖，用 `TextView`/`LinearLayout` 写壳入口 |

> 选型建议：先 B 跑通 APK，再 A 升级。AGP/Kotlin 双升容易出连锁问题。

## 坑 3：AGP 版本超出 Kotlin 测试范围

### 症状

```
[Kotlinc classpath issue] ... AGP 8.3.x not tested with Kotlin 1.9.23
```

### 根因

AGP 大版本每年两次发布（Feb/July），Kotlin 每个 minor 只对特定 AGP 版本做兼容性测试。

### 解法

查兼容性矩阵：https://developer.android.com/build/releases/gradle-plugin

| AGP | Kotlin | Gradle Wrapper |
|-----|--------|----------------|
| 8.2.x | 1.9.23 ✅ / 2.0+ ✅ | 8.5+ |
| 8.3.x | **1.9.23 ❌** / 2.0+ ✅ | 8.6+ |
| 8.4.x | 2.0+ ✅ | 8.7+ |

**简单规则**：AGP 8.X 配 Kotlin 2.(X-2)，AGP 8.2 配 Kotlin 2.0 或 1.9.23。

## 坑 4：expect/actual 平台桥接写法

### 推荐模式

```kotlin
// shared/src/commonMain/kotlin/.../platform/PlatformBridge.kt
package com.shanhai.core.platform

// expect 声明（commonMain 有声明但无 body）
expect object FileExport {
    fun exportJson(filename: String, json: String): Boolean
    fun importJson(filename: String): String?
}

expect object MapSdk {
    val isAvailable: Boolean
    fun showMap(lat: Double, lng: Double, title: String)
}

expect object ArBridge {
    val isSupported: Boolean
    val isPermissionGranted: Boolean
}
```

```kotlin
// shared/src/androidMain/kotlin/.../platform/PlatformBridge.kt
// androidMain actual — 实现 Android 端逻辑
actual object FileExport {
    actual fun exportJson(filename: String, json: String): Boolean {
        // Android: 用 Context.getExternalFilesDir + FileWriter
        return true
    }
    actual fun importJson(filename: String): String? { ... }
}
```

```kotlin
// shared/src/jvmMain/kotlin/.../platform/PlatformBridge.kt
// jvmMain actual — 实现 JVM 端逻辑（用于 jvmTest 测试）
actual object FileExport {
    actual fun exportJson(filename: String, json: String): Boolean {
        // JVM: 用 java.io.File
        return true
    }
    actual fun importJson(filename: String): String? { ... }
}
```

> **注意**：shared 同时有 jvmTarget + androidTarget 时，两个 target 都需要 actual。任何一个缺 → 编译不过。

## 坑 5：iOS target 需要 macOS

### 症状

```
Could not find target iosX64 (or iosArm64, iosSimulatorArm64)
Windows 上无法编译 iOS target
```

### 根因

Kotlin/Native 的 iOS target 硬依赖 macOS SDK 工具链（xcode-select、xcrun、ibtool 等），Windows/Linux 无法交叉编译。

### 解法（三阶段）

| 阶段 | 操作 | 平台 |
|------|------|------|
| 1. 先不接 iOS | shared 只配 jvmTarget + androidTarget；iosApp 用独立 Swift 实现 | 任何 |
| 2. macOS 上补 framework | shared 加 `iosX64() + iosArm64()` + cocoapods 插件 → `.framework` | macOS |
| 3. Swift 端接 | `pod install` 后 `import shared_KMP_Module`；或用 SKIE 优化互操作 | macOS + iOS 设备 |

> SKIE 文档：https://github.com/skaia-io/skie — 自动生成 Swift 友好的 Kotlin 接口。

## 脚手架速查

```gradle.kts
// shared/build.gradle.kts 模板
kotlin {
    jvmTarget()
    androidTarget()
    // iosX64(); iosArm64()    // 等 macOS 打开
    sourceSets {
        val commonMain by getting { dependencies { /* kotlinx.serialization, coroutines... */ } }
        val jvmMain by getting { /* java.io.File 相关 */ }
        val androidMain by getting { /* AndroidX, Context 相关 */ }
        val commonTest by getting { /* kotlin.test */ }
    }
}
android {
    namespace = "com.example.shared"
    compileSdk = 34
    defaultConfig { minSdk = 26 }
}
```

## 完整验证清单

搭完原生壳后**必须跑**这三条：

```bash
# 1. shared JVM 测试（commonMain + jvmMain 的逻辑）
./gradlew :shared:jvmTest

# 2. shared Android 编译（androidMain actual 是否有编译错）
./gradlew :shared:compileDebugKotlinAndroid

# 3. Android 壳 APK 打包（application → library 链接是否正常）
./gradlew :composeApp:assembleDebug
```

三条全绿 = KMP 多 target 壳搭完。

---

## 实战：Compose 壳 v0.4.0 架构模式（山海妖志）

> 项目 `shanhaiapp/composeApp` 已验证的最佳实践，避免底部导航超过 5 tab 的 UX 灾难。

### 架构选型：5 核心 tab + Hub 网格

| 层级 | 数量 | 方案 |
|------|------|------|
| 底部导航 | 5 | Scaffold → NavigationBar |
| 次级入口 | 13 | HubScreen 网格（4 列 LazyVerticalGrid） |
| 页面总数 | 18 | 5 核心 + 13 次级 |

**为什么这样做**：Material 3 NavigationBar 官方建议 ≤ 5 item，超过后触控目标变小、用户找不到。用 HubScreen 做"全部功能"入口，既保证 UX，又能塞下任意多的 Screen。

### MainActivity 骨架

```kotlin
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        OsmBootstrap.init(this)                    // ← osmdroid 必须先 init
        val codex = Codex(MonsterPool.all)
        setContent {
            ShanhaiTheme { Surface { GameRoot(codex) } }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GameRoot(codex: Codex) {
    var currentTab by remember { mutableStateOf(CoreTab.MAP) }
    var hubNav by remember { mutableStateOf<HubItem?>(null) }  // Hub 跳转目标

    Scaffold(
        bottomBar = {
            NavigationBar {
                CoreTab.values().forEach { tab ->
                    NavigationBarItem(
                        selected = currentTab == tab && hubNav == null,
                        onClick = { currentTab = tab; hubNav = null },  // ← 清空 Hub
                        icon = { Icon(tab.icon, null) },
                        label = { Text(tab.label) }
                    )
                }
            }
        }
    ) { padding ->
        AnimatedContent(
            targetState = hubNav ?: currentTab.toHubItem(),
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "screen_transition"
        ) { item ->
            when (item) {
                HubItem.MAP -> MapScreen(modifier = Modifier.padding(padding))
                // ... 全部 HubItem 分支
                null -> HubScreen(codex, onNavigate = { hubNav = it },
                    modifier = Modifier.padding(padding))
            }
        }
    }
}
```

### 关键要点

| 点 | 说明 |
|----|------|
| **Scaffold 必须用 padding** | `Modifier.padding(padding)` 包裹内容才不会被 NavigationBar 遮挡 |
| **AnimatedContent 转场** | `fadeIn() togetherWith fadeOut()` 是最轻量的页面切换，不增加性能开销 |
| **HubItem 枚举统一路由** | 5 核心 tab 的枚举值也在 HubItem 里，MainActivity 的 `when` 只需一套 |
| **hubNav 覆盖 currentTab** | 用户点 Hub 网格某入口 → hubNav 设值 → AnimatedContent 自动切换页面 |
| **Hub tab onClick 清空 hubNav** | 回底部导航时 hubNav = null，路由回到 currentTab 控制 |

### Theme 三档结构

```kotlin
private val ShanhaiTypography = Typography(
    headlineLarge = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.Bold),
    titleLarge   = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.SemiBold),
    bodyMedium   = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Normal),
)
private val ShanhaiShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small      = RoundedCornerShape(8.dp),
    medium     = RoundedCornerShape(12.dp),
    large      = RoundedCornerShape(16.dp),
)
```

### Windows 上的 Gradle 硬坑

```powershell
# ★ 任何 gradle 前必须 robocopy 绕过中文路径+emoji
robocopy "d:\项目\🐍shanhaiapp" "C:\temp\shanhaiapp_build" /E /XD "build" ".gradle" /PURGE /NFL /NDL /NJH /NJS /nc /ns /np
cd C:\temp\shanhaiapp_build
$env:JAVA_HOME='C:\Program Files\Java\jdk-17'
.\gradlew.bat :composeApp:assembleDebug --no-daemon --console=plain
```

### 版本快照（2026-10-17）

- Kotlin 2.0.21 + AGP 8.4.2 + Compose Compiler Gradle plugin
- osmdroid 6.1.20（零 KEY）
- JDK Temurin 17
