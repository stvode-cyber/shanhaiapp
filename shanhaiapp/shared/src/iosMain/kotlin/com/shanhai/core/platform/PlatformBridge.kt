package com.shanhai.core.platform

/**
 * iOS 平台 actual 实现（Kotlin/Native）。
 *
 * 依赖：platform.Foundation（Kotlin/Native 自动可用，映射 iOS Foundation 框架）
 * 验证环境：macOS 上 gradle :shared:podInstallIosX64 编译时自动启用
 *
 * 选型 D21：地图暂留接口，待接 MapKit（iOS 系统内置 SDK，零 KEY）。
 * Android 端已落地 osmdroid（Apache 2.0，零 KEY），跨端接口契约已闭合。
 */

import platform.Foundation.NSFileManager
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSUserDomainMask
import platform.Foundation.NSString
import platform.Foundation.stringWithContentsOfFile
import platform.Foundation.writeToFile
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSArray

// ===== FileExport =====
actual class FileExport {

    private fun documentsPath(): String {
        // iOS App 沙盒 Documents 目录（标准持久化位置）
        val paths = NSSearchPathForDirectoriesInDomains(
            NSDocumentDirectory,
            NSUserDomainMask,
            true
        )
        return (paths.firstOrNull() as? String) ?: "/tmp"
    }

    actual fun exportJson(filename: String, json: String): Pair<Boolean, String?> {
        return try {
            val fullPath = "${documentsPath()}/$filename"
            (json as NSString).writeToFile(fullPath, atomically = true, encoding = 4)  // NSUTF8StringEncoding = 4
            true to fullPath
        } catch (e: Exception) {
            false to e.message
        }
    }

    actual fun importJson(filename: String): String? {
        val fullPath = "${documentsPath()}/$filename"
        val fileManager = NSFileManager.defaultManager
        if (!fileManager.fileExistsAtPath(fullPath)) return null
        return (NSString.stringWithContentsOfFile(fullPath) as? String)
    }
}

// ===== MapSdk =====
actual class MapSdk {

    // iOS MapKit（系统内置，零 KEY）— 待接，保持接口与 Android osmdroid 一致
    // 正式集成后：
    //   1. 无需额外 pod（MapKit 是 iOS 系统 SDK）
    //   2. Info.plist 加 NSLocationWhenInUseUsageDescription（可选，仅定位时需要）
    //   3. isAvailable() 返回 true；showMap() 用 MKMapView present
    actual fun isAvailable(): Boolean = false

    actual fun showMap(lat: Double, lng: Double, title: String) {
        // TODO(MapKit): macOS 上接 Kotlin/Native → import platform.MapKit.MKMapView
        // val mapView = MKMapView(frame = CGRectMake(0, 0, UIScreen.mainScreen.bounds.size.width, UIScreen.mainScreen.bounds.size.height))
        // mapView.centerCoordinate = CLLocationCoordinate2D(latitude = lat, longitude = lng)
    }
}

// ===== ArBridge =====
actual class ArBridge {

    // 选型 D04：AR 维持 UNSUPPORTED（iOS 上 ARKit 需要 A11+ 芯片，覆盖面有限）
    // 如果要做：pod 'ARCore' 或直接用 ARKit
    actual fun isSupported(): Boolean = false

    actual fun isPermissionGranted(): Boolean = false
}