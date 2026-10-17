package com.shanhai.core.platform

import java.io.File

actual class FileExport {
    actual fun exportJson(filename: String, json: String): Pair<Boolean, String?> =
        try { val f = File(System.getProperty("user.home"), filename); f.writeText(json); true to f.absolutePath }
        catch (e: Exception) { false to e.message }
    actual fun importJson(filename: String): String? {
        val f = File(System.getProperty("user.home"), filename)
        return if (f.exists()) f.readText() else null
    }
}

actual class MapSdk {
    actual fun isAvailable(): Boolean = false
    actual fun showMap(lat: Double, lng: Double, title: String) { /* no-op */ }
}

actual class ArBridge {
    actual fun isSupported(): Boolean = false
    actual fun isPermissionGranted(): Boolean = false
}
