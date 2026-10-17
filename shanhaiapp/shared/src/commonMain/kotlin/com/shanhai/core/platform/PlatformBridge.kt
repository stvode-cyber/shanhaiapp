package com.shanhai.core.platform

/**
 * 平台桥接接口 — 选型：
 *   Android → osmdroid（Apache 2.0，零 KEY）/ iOS → 待接 MapKit（系统 SDK，零 KEY）
 *   AR UNSUPPORTED / 本地 JSON 导出 / 前台调度 / 社交先不做。
 * expect 声明在 commonMain；actual 由各 target 提供（jvmMain / androidMain / iosMain）。
 */

/** 本地文件系统（导出/导入存档 JSON） */
expect class FileExport() {
    fun exportJson(filename: String, json: String): Pair<Boolean, String?>
    fun importJson(filename: String): String?
}

/** 地图 SDK（选型 D21：Android osmdroid 已落地，iOS 暂留接口） */
expect class MapSdk() {
    fun isAvailable(): Boolean
    fun showMap(lat: Double, lng: Double, title: String)
}

/** AR 能力（选型 D04：维持 UNSUPPORTED） */
expect class ArBridge() {
    fun isSupported(): Boolean
    fun isPermissionGranted(): Boolean
}

/** 平台门面（注入用） */
object Platform {
    val file by lazy { FileExport() }
    val map by lazy { MapSdk() }
    val ar by lazy { ArBridge() }
}
