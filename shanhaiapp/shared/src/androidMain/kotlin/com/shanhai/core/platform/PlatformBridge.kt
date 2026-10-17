package com.shanhai.core.platform

/**
 * Android 平台 PlatformBridge actual 实现。
 *
 * 地图 SDK：osmdroid 6.1.20（Apache 2.0，无 key 要求，全球覆盖）
 *   - 依赖：org.osmdroid:osmdroid-android:6.1.20
 *   - 不需要 API key，Manifest 只需要 INTERNET + ACCESS_NETWORK_STATE
 *   - 比高德 V11 更适合原型开发 + 国际市场
 */

import android.content.Context
import org.osmdroid.config.Configuration
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView

// ===== 全局初始化（由 composeApp MainActivity.onCreate 调）=====
object OsmBootstrap {
    private var initialized = false

    /** 在 Activity.onCreate 早期调用。重复调用安全。 */
    fun init(context: Context) {
        if (initialized) return
        try {
            // osmdroid 6.0+ 必须在初始化前设置 user-agent（合规要求）
            Configuration.getInstance().userAgentValue = "com.shanhai.yaozhi"
            initialized = true
        } catch (e: Exception) {
            initialized = false
        }
    }

    fun isReady() = initialized
}

// ===== FileExport =====
actual class FileExport {
    actual fun exportJson(filename: String, json: String): Pair<Boolean, String?> {
        return try {
            val f = java.io.File(System.getProperty("user.home"), filename)
            f.writeText(json)
            true to f.absolutePath
        } catch (e: Exception) { false to e.message }
    }
    actual fun importJson(filename: String): String? {
        val f = java.io.File(System.getProperty("user.home"), filename)
        return if (f.exists()) f.readText() else null
    }
}

// ===== MapSdk =====
actual class MapSdk {
    // 当前选中的地图请求（composeApp 侧的 MapScreen 观察这个来渲染）
    @Volatile private var pending: MapRequest? = null

    actual fun isAvailable(): Boolean = OsmBootstrap.isReady()

    actual fun showMap(lat: Double, lng: Double, title: String) {
        if (!OsmBootstrap.isReady()) return
        pending = MapRequest(lat, lng, title)
    }

    fun consumePending(): MapRequest? {
        val r = pending; pending = null; return r
    }

    data class MapRequest(val lat: Double, val lng: Double, val title: String)

    /** 创建一个配置好的 osmdroid MapView（供 Compose AndroidView 使用） */
    fun createMapView(context: Context, req: MapRequest? = null): MapView {
        val mv = MapView(context).apply {
            setTileSource(org.osmdroid.tileprovider.tilesource.TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            controller.setZoom(14.0)
        }
        req?.let {
            mv.controller.setCenter(GeoPoint(it.lat, it.lng))
        }
        return mv
    }
}

// ===== ArBridge =====
actual class ArBridge {
    actual fun isSupported(): Boolean = false
    actual fun isPermissionGranted(): Boolean = false
}
