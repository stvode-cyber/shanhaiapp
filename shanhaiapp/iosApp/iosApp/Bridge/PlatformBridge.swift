// PlatformBridge.swift
//
// iOS 平台桥接层。三阶段演化：
//
// 阶段 1（当前，Windows/任意）：
//   - 占位实现，iosApp 独立运行
//   - 所有 KMP 相关接口返回默认值
//   - 高德地图 SDK 未集成
//
// 阶段 2（macOS 上 pod install 后）：
//   - import ShanHaiKmp（KMP shared 模块 framework）
//   - FileExport / MapSdk / ArBridge 的 iOS actual 实现
//   - 直接委托给 KMP shared 的 expect/actual 接口
//
// 阶段 3（正式上线）：
//   - 接入高德 iOS SDK（AMap3DMap 9.8.0）
//   - ArBridge 返回 true（如果决定做 AR）
//   - 完整的 iOS UI（SwiftUI 或 UIKit）
//
// macOS 上接 KMP 的步骤（见 macos-kmp-setup.md）：
//   1. 在 Mac 上跑 ./gradlew :shared:podInstallIosX64（Intel）
//      或 ./gradlew :shared:podInstallIosArm64（Apple Silicon）
//   2. 在 iosApp 目录跑 pod install
//   3. 用 Xcode 打开 iosApp.xcworkspace
//   4. 编译运行 —— shared 模块的 GameState/Codex/Engine 全可用
//
// TODO(macOS): import ShanHaiKmp   // 阶段 2 打开注释
import Foundation

// MARK: - KMP Shared Bridge Declaration (占位接口)
//
// 山海妖志平台选型 D04：
//  - 高德地图 SDK：iOS 集成 AMap3DMap.framework，占位接口 MapSdkBridge
//  - AR：UNSUPPORTED，占位接口 ArBridge 返回 false
//  - 本地 JSON 导出：iOS 走 FileManager，通过 shared 的 FileExport actual 实现调用
//  - 社交/云同步：先不做
//
// iOS 接 shared 的路线（在 macOS 上执行）：
//  1. shared 加 iosX64() + iosArm64() target
//  2. build.gradle.kts 加 cocoapods 插件生成 framework
//  3. pod install 后 Swift 端 import shared_KMP_module
//  4. 所有 shared 的 expect/actual 接口自动暴露给 Swift
//  5. 用 SKIE 优化 Swift/Kotlin 互操作（可选）
//
// 当前文件：先声明平台桥接接口的 Swift 端签名，
// 等 macOS 上 KMP iOS target 编好后用 SKIE 或 cinterop 自动对接。

/// 文件导出（对应 shared FileExport expect）
protocol FileExportProtocol {
    func exportJson(filename: String, json: String) -> (Bool, String?)
    func importJson(filename: String) -> String?
}

/// 地图（对应 shared MapSdk expect）
protocol MapSdkProtocol {
    var isAvailable: Bool { get }
    func showMap(lat: Double, lng: Double, title: String)
}

/// AR（选型：UNSUPPORTED，接口预留）
protocol ArBridgeProtocol {
    var isSupported: Bool { get }
    var isPermissionGranted: Bool { get }
}

/// 平台门面（iOS concrete implementation — 等 KMP framework 接入后改委托给 shared actual）
class PlatformBridge {
    static let shared = PlatformBridge()
    
    let file: FileExportProtocol = IOSFileExport()
    let map: MapSdkProtocol = IOSMapSdk()
    let ar: ArBridgeProtocol = IOSArBridge()
}

// MARK: - 占位实现（等 KMP framework 接入后替换）

private class IOSFileExport: FileExportProtocol {
    func exportJson(filename: String, json: String) -> (Bool, String?) {
        let urls = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)
        guard let dir = urls.first else { return (false, nil) }
        let url = dir.appendingPathComponent(filename)
        do {
            try json.write(to: url, atomically: true, encoding: .utf8)
            return (true, url.path)
        } catch { return (false, error.localizedDescription) }
    }
    func importJson(filename: String) -> String? {
        let urls = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)
        guard let dir = urls.first else { return nil }
        let url = dir.appendingPathComponent(filename)
        return try? String(contentsOf: url, encoding: .utf8)
    }
}

private class IOSMapSdk: MapSdkProtocol {
    let isAvailable: Bool = false  // 未集成高德 iOS SDK
    func showMap(lat: Double, lng: Double, title: String) { /* no-op */ }
}

private class IOSArBridge: ArBridgeProtocol {
    let isSupported: Bool = false  // 选型：UNSUPPORTED
    let isPermissionGranted: Bool = false
}
