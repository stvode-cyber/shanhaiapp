package com.shanhai.core.logic

/**
 * 灵脉节点坐标（派生自 [LocationEngine.NODE_KEYS] 单一真源）。
 * 由地图 SDK 适配层复用，避免重复硬编码 POI。
 */
data class MapNode(
    val lat: Double,
    val lng: Double,
    val name: String,
)

/** POI 类别：灵脉节点 / 据点（据点战占领目标）。 */
enum class PoiKind { LINGMAI, STRONGHOLD }

/**
 * 地图叠加 POI（灵脉 / 据点）。
 * [id] 与 [name] 对齐 [LocationEngine.STRONGHOLD_NODES] 节点名，保证据点战与地图同值。
 */
data class MapPoi(
    val id: String,
    val name: String,
    val lat: Double,
    val lng: Double,
    val kind: PoiKind,
)

/**
 * 真实地图 SDK 适配契约（GDD·LBS 探索双模式）。
 *
 * 当前 LBS 节点为固定 POI 真源（[LocationEngine.nodeCoordinates] / [LocationEngine.STRONGHOLD_NODES]），
 * 真实实现替换为地图 SDK 的 POI 叠加 / 玩家定位图层 / 区域缩放。
 * 运行时由平台层接入（ARKit/MapKit/高德/腾讯地图等），shared 仅定义契约 + 复用真源 POI，双端不漂移。
 */
interface MapSdkAdapter {
    /** 地图 SDK 是否可用（无地图依赖时返回 false，游戏以固定坐标降级）。 */
    fun isAvailable(): Boolean

    /** 叠加灵脉 / 据点 POI 图层；返回是否成功。 */
    fun overlayPois(pois: List<MapPoi>): Boolean

    /** 设置玩家实时定位图层。 */
    fun setPlayerLayer(lat: Double, lng: Double): Boolean

    /** 聚焦到指定区域并缩放到 [zoom] 级别。 */
    fun focusRegion(lat: Double, lng: Double, zoom: Int): Boolean

    /**
     * 从 [LocationEngine] 真源构建 POI 列表（灵脉全量 + 据点白名单标记）。
     * 复用单一真源，不重复实现坐标硬编码。
     */
    fun buildPois(): List<MapPoi> {
        return LocationEngine.nodeCoordinates().map { node ->
            MapPoi(
                id = node.name,
                name = node.name,
                lat = node.lat,
                lng = node.lng,
                kind = if (LocationEngine.STRONGHOLD_NODES.contains(node.name)) PoiKind.STRONGHOLD else PoiKind.LINGMAI,
            )
        }
    }
}
