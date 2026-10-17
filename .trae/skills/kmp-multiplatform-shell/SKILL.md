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

## 实战：Windows 桌面壳 pywebview + PyInstaller 套路

> 把 prototype/index.html 包成原生窗口 EXE。20MB，双击就能跑。

### 环境

| 组件 | 版本 |
|------|------|
| Python | 3.12.10 |
| pywebview | 6.2.1（WebView2 后端）|
| PyInstaller | 6.22.2 |

### app.py 核心套路（路径双兼容）

```python
def get_base_path():
    """支持两种布局：
    - 开发：windows/app.py + ../prototype/index.html（兄弟目录）
    - 打包：windows/app.py + prototype/index.html（同目录子文件夹）
    """
    base = os.path.dirname(os.path.abspath(__file__))
    same_dir = os.path.join(base, 'prototype', 'index.html')
    if os.path.exists(same_dir):
        return same_dir
    sibling = os.path.join(base, '..', 'prototype', 'index.html')
    return sibling

window = webview.create_window(
    title='山海妖志 v1.2.0-release',
    url=index_path,
    width=1280, height=800,
    confirm_close=True,   # 防误关
)
webview.start(debug=False)
```

### pywebview 6.2.1 API 注意

| API | 状态 | 说明 |
|-----|------|------|
| `window.events.keys['F11'] = fn` | ❌ 不存在 | AttributeError: 'EventContainer' object has no attribute 'keys' |
| `window.toggle_fullscreen()` | ✅ 可用 | 但只能在 JS 侧拦截 keydown 后调用 |
| ESC 默认关闭 | ✅ 自带 | 无需写代码 |
| `confirm_close=True` | ✅ 可用 | 关闭前弹窗确认 |

### PyInstaller 打包流程

```powershell
# ★ 任何 PyInstaller 前必须 robocopy 绕过中文路径+emoji
robocopy "d:\项目\🎮游戏\shanhaiapp\windows" "C:\temp\shanhai_windows" /E /XD "build" "dist" /PURGE /NFL /NDL /NJH /NJS
robocopy "d:\项目\🎮游戏\shanhaiapp\prototype" "C:\temp\shanhai_windows\prototype" /E /NFL /NDL /NJH /NJS

# spec 文件 datas 必须指向同目录（PyInstaller 解压到 app.py 同级）
# datas=[('prototype', 'prototype')]   ← 正确
# datas=[('../prototype', 'prototype')] ← 错误！打包后 ../ 不存在

cd C:\temp\shanhai_windows
python -m PyInstaller "山海妖志.spec" --clean --noconfirm

# 打包完拷回项目
robocopy "C:\temp\shanhai_windows\dist" "d:\项目\🎮游戏\shanhaiapp\windows\dist" "山海妖志.exe" /NFL /NDL /NJH /NJS
```

### .gitignore 必须排除

```gitignore
# Windows pywebview build artifacts
/windows/dist/
/windows/build/
```

20MB+ 的二进制 EXE 绝对不能进 git——仓库会膨胀到几百 MB，clone 变慢。

### 验证清单

```powershell
# 1. 启动 EXE（或 python app.py）
# 2. 看窗口标题是否正确（项目版本号）
Get-Process | Where-Object { $_.MainWindowTitle -like "*山海*" } | Select Id, ProcessName, MainWindowTitle, Responding
# 3. Responding=True 说明没崩溃
# 4. 手动点进窗口确认内容加载（原型 Activity tab + 信誉分层 + 6 配方）
```

---

## 实战：模块总纲引擎→Screen 绑定模式（山海妖志 v0.4.0）

> 当项目核心引擎 + Compose Screen 全量交付后，17 个模块总纲的**标准化升级套路**。
> 解决的问题：模块总纲停留在"计划子文件(待建)"状态，过时信息误导新 AI。

### 总纲模板（50-80 行/个）

```markdown
# XX · 模块名 · 模块总纲

> 模块编号：XX ｜ 最后更新：YYYY-MM-DD ｜ 状态：✅ 已实现+已验证（Screen名 + 可选备注）

## 一、模块目的
一句话说清模块干啥 + 对应 GDD 章节。

## 二、对应引擎与 UI（核心表）
| 层 | 文件 | 说明 |
|----|------|------|
| Kotlin 引擎 | `shared/.../logic/XXXEngine.kt` | 引擎职责 |
| Kotlin 引擎 | `shared/.../data/XXLibrary.kt` | 数据真源 |
| Compose Screen | `composeApp/.../screens/XxxScreen.kt` | UI 职责 |

## 三、当前状态
- ✅ 引擎验证点：具体做了啥、断言数
- ✅ Screen 验证点：构建绿 / UI 要点
- ℹ️ 待接入项（如有）：纯可选、不影响核心

## 四、与其他模块关系
- 上游依赖：哪些模块喂数据给本模块
- 下游影响：本模块喂数据给哪些模块

## 五、导航
- 返回：[00_工程总纲](../../00_工程总纲.md) ｜ 规约：[90_文档规约](../../90_文档规约/00_总纲.md)
```

### 升级 Checklist

1. **删"计划子文件(待建)"** — 项目完整交付后不需要细碎文档，核心引擎+Screen 足够
2. **加"对应引擎与 UI"绑定表** — 每个模块精确绑定到哪个 Kotlin 文件 + 哪个 Screen
3. **更新日期到交付日** — 8-19 旧版全部同步到 10-17 v0.4.0
4. **修正过时状态** — "真实地图 SDK 待接入" → osmdroid+MapScreen ✅；"无 JDK 未编译" → 三重验证链全绿 ✅
5. **50-80 行控制** — 不堆 GDD 正文，只做导航+状态+绑定

### 17 模块升级速查（v0.4.0 版已完成）

| 编号 | 模块 | 核心引擎 | Compose Screen |
|------|------|----------|----------------|
| 10 | 探索与地图 | LocationEngine + SecretRealmEngine | MapScreen + SecretScreen |
| 20 | 收妖与AR | CaptureEngine + MoralChoiceEngine | CaptureScreen |
| 30 | 图鉴 | Codex | CodexScreen |
| 40 | 战斗 | BattleEngine + BattleSession + FormationEngine | BattleScreen |
| 50 | 技能 | SkillLibrary + ComboEngine | 嵌入 BattleScreen |
| 60 | 养成 | CultivationEngine + TalentEngine + FusionEngine | Cultivate + Talent + Fuse |
| 70 | 装备 | EquipmentLibrary + EquipmentEngine | EquipScreen |
| 80 | 妖池 | MonsterPool | 无（数据真源） |
| 100 | 副本妖境 | DungeonEngine | DungeonScreen |
| 110 | 社交与据点 | SocialEngine + SectTechEngine | SectScreen |
| 120 | 经济与祈妖 | ShopEngine + GachaEngine | StoreScreen |
| 130 | 公平与防作弊 | ReputationEngine + FatigueEngine + SaveIntegrityEngine | 嵌入 HonorScreen |
| 140 | 排位赛 | RankEngine + RankBanPickEngine | 待建（平台匹配） |
| 150 | 家园洞府 | HomesteadEngine | HomesteadScreen |
| 160 | 引导留存与运营 | TutorialEngine + SeasonEngine + HonorEngine + ActivityEngine | Tutorial + Season + Honor + Activity + Quests |
| 170 | 技术架构与工程 | PlatformBridge(commonMain) + GameState | MainActivity + HubScreen + ShanhaiTheme |
