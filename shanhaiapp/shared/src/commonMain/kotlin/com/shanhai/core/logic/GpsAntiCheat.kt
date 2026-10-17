package com.shanhai.core.logic

import com.shanhai.core.logic.LocationEngine.SOFT_SPEED_KMH
import com.shanhai.core.logic.LocationEngine.TELEPORT_METERS
import com.shanhai.core.logic.LocationEngine.TELEPORT_WINDOW_SEC

/**
 * GPS 防作弊——服务端权威校验契约（GDD 十六·探索 / 户外灵脉）。
 *
 * 对应客户端 [LocationEngine.spawnIntegrity]（单步快速检测，仅作前端隐藏灵脉）；
 * 服务端持有完整上报轨迹，做跨样本权威裁决（速度 / 瞬移），客户端检测不可信。
 *
 * 判定常量（SOFT_SPEED_KMH / TELEPORT_METERS / TELEPORT_WINDOW_SEC）与客户端
 * [LocationEngine] 严格一致，由平台层对齐，杜绝双端漂移。
 */

/** 客户端上报的定位轨迹点（服务端不盲信其速度字段，独立重算）。 */
data class GpsSample(
    val lat: Double,
    val lng: Double,
    val ts: Long,
    val speedKmh: Double? = null,
)

/** GPS 防作弊——服务端权威裁决。 */
data class AntiCheatVerdict(
    /** 是否可信：由 [tier] 派生（tier != REJECTED 即可信）。 */
    val trusted: Boolean,
    val reason: String,
    val maxSpeedKmh: Double,        // 轨迹内服务端独立计算的最大速度
    val teleportDetected: Boolean,
    /** 判定档位（NORMAL / DEGRADED / REJECTED），与客户端 [IntegrityTier] 一致。 */
    val tier: IntegrityTier = IntegrityTier.NORMAL,
)

interface GpsAntiCheatVerifier {
    /** 平台是否已具备服务端校验通道（如后端 API）。 */
    fun isAvailable(): Boolean

    /** 对一条上报轨迹做权威裁决；空轨迹视为可信（无异常样本）。 */
    fun verify(samples: List<GpsSample>): AntiCheatVerdict

    /**
     * 单次定位点校验（与客户端 [LocationEngine.spawnIntegrity] 对应的服务端单步判定）。
     * 默认实现复用 [LocationEngine.haversine] 与同套判定常量，确保双端判据一致。
     */
    fun verifyPoint(prev: GpsSample?, cur: GpsSample): IntegrityResult {
        if (prev == null) return IntegrityResult(true, "首次定位", tier = IntegrityTier.NORMAL)
        val dt = cur.ts - prev.ts
        if (dt <= 0L) return IntegrityResult(true, "时间无效-跳过", tier = IntegrityTier.NORMAL)
        val dist = LocationEngine.haversine(prev.lat, prev.lng, cur.lat, cur.lng)
        val speed = dist / dt.toDouble() * 3.6
        return when {
            dist >= TELEPORT_METERS && dt <= TELEPORT_WINDOW_SEC ->
                IntegrityResult(false, "检测到瞬移：%.0f 米 / %d 秒".format(dist, dt), speed, IntegrityTier.REJECTED, true)
            speed > SOFT_SPEED_KMH ->
                IntegrityResult(true, "速度过快(降品质)：%.0f km/h".format(speed), speed, IntegrityTier.DEGRADED, true)
            else -> IntegrityResult(true, "正常", speed, IntegrityTier.NORMAL, false)
        }
    }
}
