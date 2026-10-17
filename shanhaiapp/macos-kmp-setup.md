# macOS 上接 KMP iOS framework — 精确命令链

## 前置条件
- macOS 机器 + Xcode 15+
- Ruby + CocoaPods（`sudo gem install cocoapods`）
- Java 17（`brew install openjdk@17`）
- 命令行工具：`xcode-select --install`

## Step 1：克隆项目 + 准备 Gradle

```bash
cd ~/projects/shanhaiapp   # 项目根
./gradlew :shared --version     # 确认 Kotlin 2.0.21 / Gradle 8.7
```

## Step 2：生成 iOS framework（X64 Simulator）

```bash
# 在 shanhaiapp 根
./gradlew :shared:podInstallIosX64
# 预期输出：BUILD SUCCESSFUL
# 产物位置：shared/build/cocoapods/framework/ShanHaiKmp.framework
```

## Step 3：iosApp 端 pod install

```bash
cd iosApp
pod install
# 预期：Podfile.lock 生成 + Pods/ 目录 + iosApp.xcworkspace 生成
```

## Step 4：Xcode 打开 + 编译

```bash
open iosApp/iosApp.xcworkspace
```

在 Xcode 里：
1. 选择 target = iosApp，模拟器 = iPhone 15
2. Cmd+B 编译（首次编译慢，KMP framework 头文件生成）
3. Cmd+R 运行 —— 应该看到 PlatformBridge.swift 里的占位数据

## Step 5：接 KMP 接口（改 PlatformBridge.swift）

```swift
import ShanHaiKmp   // 去掉 TODO 注释

// FileExport.actual 的 iOS 实现已经在 shared 里（iosMain actual）
// MapSdk 的占位 iOS 实现已在 PlatformBridge.swift
// 但高德地图需要手动集成 AMap3DMap.framework
```

## 常见问题

| 报错 | 解法 |
|------|------|
| `pod: command not found` | `sudo gem install cocoapods` |
| `No Xcode or tools available` | `xcode-select --install` + 第一次打开 Xcode 让它自动安装 SDK |
| `Could not find 'libcurl'` | Xcode 15+ 需要 iOS 15+，`podspec` 里要写 `ios.deployment_target = '15.0'` |
| 模拟器编译过但真机报错 | 把 `iosArm64()` 打开，或者在 iOS Build Settings 里加 arm64 |
| `pod install` 卡住 | `pod install --repo-update` |

## 框架产物位置

```
shared/build/cocoapods/
├── framework/
│   └── ShanHaiKmp.framework/   ← 静态 framework（所有 iOS 架构合在一起）
├── ShanHaiKmp.podspec          ← pod install 时用的 spec
└── ios/
    ├── ShanHaiKmp.framework (x86_64 模拟器)
    └── ShanHaiKmp.framework (arm64 真机/模拟器)
```

## 验证清单

- [ ] `./gradlew :shared:podInstallIosX64` BUILD SUCCESSFUL
- [ ] `cd iosApp && pod install` 成功，有 iosApp.xcworkspace
- [ ] Xcode Cmd+B 编译通过
- [ ] PlatformBridge.swift 里 `import ShanHaiKmp` 后能看到 GameState/Codex 类
- [ ] 模拟器上跑起来（哪怕只有占位 UI）