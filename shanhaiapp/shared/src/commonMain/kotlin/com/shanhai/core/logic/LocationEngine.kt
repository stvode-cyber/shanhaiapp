package com.shanhai.core.logic

import com.shanhai.core.data.MonsterPool
import com.shanhai.core.data.defaultRandom
import com.shanhai.core.model.Element
import com.shanhai.core.model.Rarity

/**
 * LBS 灵脉引擎（GDD 十六·探索 / 户外灵脉）。
 *
 * 真实地图被「灵脉」网络覆盖（= POI）。灵脉节点渗出妖气吸引妖怪；
 * 名山/古战场 = 妖巢/据点；昼夜 + 节气改变出没。
 *
 * 本引擎只做**可验证的派生计算**（坐标→地域主题→加权刷怪），
 * 真实 GPS / 地图 SDK 由各平台接入后把 [SpawnContext] 喂进来即可。
 */
enum class SolarTerm {
    /** 平常日 */
    NONE,
    /** 中元（鬼门开：怪类权重翻倍、相柳现身概率提升） */
    ZHONGYUAN,
    /** 春节（年兽限时） */
    CHUNJIE,
}

/** 地域主题（决定五行偏向）。 */
enum class RegionTheme {
    SHANLIN,   // 山林 → 木
    SHUIYU,    // 水域 → 水
    GUMU,      // 古墓 → 土
    DONGHAI,   // 东海 → 水
}

/** 刷怪上下文：由平台从 GPS / 系统时间 / 节气日历填入。 */
data class SpawnContext(
    val lat: Double,
    val lng: Double,
    val hourOfDay: Int,            // 0..23
    val solarTerm: SolarTerm = SolarTerm.NONE,
    val ts: Long = 0L,             // 当前定位 epoch 秒（GPS 防作弊用）
    val prevLat: Double? = null,   // 上一次定位纬度（GPS 防作弊用）
    val prevLng: Double? = null,   // 上一次定位经度
    val prevTs: Long = 0L,         // 上一次定位 epoch 秒
)

/** GPS 防作弊判定档位（GDD 十六·探索 / 户外灵脉）。 */
enum class IntegrityTier {
    /** 正常：可正常收妖，稀有度权重不变。 */
    NORMAL,
    /** 降品质：速度过快（> [LocationEngine.SOFT_SPEED_KMH]）但不构成瞬移；仍可收妖，但神不出、仙权重×0.2（不阻断、不扣分）。 */
    DEGRADED,
    /** 拒收：检测到瞬移（短时超大位移）→ 收妖无效，按 [com.shanhai.core.logic.CheatKind.TELEPORT] 扣信誉分。 */
    REJECTED,
}

/** LBS 防作弊判定结果。 */
data class IntegrityResult(
    /** tier != [IntegrityTier.REJECTED] 即可收妖（DEGRADED 仍可收但降品质）。 */
    val ok: Boolean,
    val reason: String,
    val speedKmh: Double = 0.0,
    /** 判定档位（NORMAL / DEGRADED / REJECTED）。 */
    val tier: IntegrityTier = IntegrityTier.NORMAL,
    /** 降权标记（DEGRADED 或 REJECTED 均为 true，提示平台降权/拒绝）。 */
    val degraded: Boolean = false,
)

object LocationEngine {
    /** 软档速度阈值（km/h）：超过即判定降品质（神不出、仙×0.2），但不阻断收妖、不扣分（GDD 的「>30km/h 降品质」）。 */
    const val SOFT_SPEED_KMH = 30.0
    /** 短时位移阈值（米）：在窗口内移动超过此距离视为瞬移 */
    const val TELEPORT_METERS = 5000.0
    /** 瞬移判定时间窗口（秒）：GDD 的「瞬移 <3 分钟」取上限 180s（原 10s 漏检 10s~180s 之间的瞬移）。 */
    const val TELEPORT_WINDOW_SEC = 180L

    private const val EARTH_RADIUS_M = 6_371_000.0

    /** 区域主题 → 五行偏向（该主题刷怪偏向此行） */
    fun regionElementBias(theme: RegionTheme): Element = when (theme) {
        RegionTheme.SHANLIN -> Element.WOOD
        RegionTheme.SHUIYU -> Element.WATER
        RegionTheme.GUMU -> Element.EARTH
        RegionTheme.DONGHAI -> Element.WATER
    }

    /**
     * 由坐标**确定性**解析地域主题（同一坐标永远得到同一主题）。
     * 切片实现：对经纬度做整数哈希取模，避免引入浮点地图依赖。
     */
    fun resolveRegion(ctx: SpawnContext): RegionTheme {
        val a = ((ctx.lat * 1000).toInt()) xor ((ctx.lng * 1000).toInt())
        val idx = kotlin.math.abs(a) % RegionTheme.values().size
        return RegionTheme.values()[idx]
    }

    /** 子时（23 点 / 0 点）判定：鬼类（怪）出没加成 */
    fun isNight(ctx: SpawnContext): Boolean = ctx.hourOfDay == 23 || ctx.hourOfDay == 0

    /** 稀有度基础权重（与 MonsterPool.randomEncounter 一致：凡>灵>仙>神） */
    private fun rarityWeight(r: Rarity): Double = when (r) {
        Rarity.FAN -> 45.0
        Rarity.LING -> 35.0
        Rarity.XIAN -> 15.0
        Rarity.SHEN -> 5.0
    }

    /**
     * 计算单只妖在给定上下文下的刷怪权重：
     * - 区域五行偏向 ×1.8
     * - 夜间（子时）怪类（GUAI）×1.6
     * - 中元：怪类 ×2；春节：isFestival ×3（年兽等限时妖）
     */
    private fun weightOf(m: com.shanhai.core.model.Monster, ctx: SpawnContext, degraded: Boolean = false): Double {
        var w = rarityWeight(m.rarity)
        if (degraded) {
            when (m.rarity) {
                // 降品质：神不出（权重归零）、仙权重×0.2，凡/灵不变（GDD 十六·软档惩罚）
                Rarity.SHEN -> return 0.0
                Rarity.XIAN -> w *= 0.2
                else -> {}
            }
        }
        if (m.element == regionElementBias(resolveRegion(ctx))) w *= 1.8
        if (ctx.solarTerm == SolarTerm.ZHONGYUAN && m.category == com.shanhai.core.model.MonsterCategory.GUAI) w *= 2.0
        if (ctx.solarTerm == SolarTerm.CHUNJIE && m.isFestival) w *= 3.0
        if (isNight(ctx) && m.category == com.shanhai.core.model.MonsterCategory.GUAI) w *= 1.6
        return w
    }

    /**
     * 依据上下文加权抽样 [count] 只妖的 id（可重复，模拟连刷）。
     * [degraded]=true 时按降品质规则调整权重（神不出、仙×0.2）。
     * 返回妖 id 列表；[rng] 默认使用平台随机源，便于测试注入。
     */
    fun nearbySpawns(ctx: SpawnContext, count: Int, rng: () -> Double = ::defaultRandom, degraded: Boolean = false): List<String> {
        val pool = MonsterPool.all
        val weights = pool.map { weightOf(it, ctx, degraded) }
        val total = weights.sum()
        val out = mutableListOf<String>()
        repeat(count) {
            var r = rng() * total
            var chosen = pool.last()
            for (i in pool.indices) {
                r -= weights[i]
                if (r <= 0.0) { chosen = pool[i]; break }
            }
            out.add(chosen.id)
        }
        return out
    }

    /**
     * 给定坐标是否靠近某灵脉节点（切片：固定若干 POI，按经纬度格点判定）。
     * 真实实现应换成与地图 SDK 的距离查询。
     */
    fun nearbyNode(ctx: SpawnContext): String? {
        // 以 (lat,lng) 落在某 0.01° 格内模拟「靠近节点」
        val key = "${"%.2f".format(ctx.lat)}:${"%.2f".format(ctx.lng)}"
        return NODE_KEYS[key]
    }

    private val NODE_KEYS = mapOf(
        "31.23:121.47" to "城隍桥",
        "30.25:120.16" to "古庙",
        "25.03:102.71" to "老槐树",
        "30.62:122.08" to "东海之滨",
    )

    /** 灵脉节点坐标真源（结构化），供地图 SDK 适配复用，避免重复硬编码 POI；派生自 [NODE_KEYS]，单一真源。 */
    fun nodeCoordinates(): List<MapNode> = NODE_KEYS.map { (k, name) ->
        val (la, ln) = k.split(":").let { it[0].toDouble() to it[1].toDouble() }
        MapNode(la, ln, name)
    }

    /** 全部合法灵脉节点名（据点战占领目标白名单；派生自 [NODE_KEYS]，单一真源） */
    val STRONGHOLD_NODES: Set<String> = NODE_KEYS.values.toSet()

    /** Haversine 球面距离（米）。 */
    fun haversine(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLng = Math.toRadians(lng2 - lng1)
        val a = kotlin.math.sin(dLat / 2) * kotlin.math.sin(dLat / 2) +
                kotlin.math.cos(Math.toRadians(lat1)) * kotlin.math.cos(Math.toRadians(lat2)) *
                kotlin.math.sin(dLng / 2) * kotlin.math.sin(dLng / 2)
        return EARTH_RADIUS_M * 2.0 * kotlin.math.asin(kotlin.math.sqrt(a))
    }

    /**
     * GPS 防作弊：相邻两次定位间的速度 / 瞬移检测（三档，GDD 十六·探索 / 户外灵脉）。
     * - 位移 ≥ [TELEPORT_METERS] 且间隔 ≤ [TELEPORT_WINDOW_SEC] → 瞬移（伪造坐标）→ [IntegrityTier.REJECTED]（收妖无效 + 扣信誉）
     * - 速度 > [SOFT_SPEED_KMH]（>30km/h，远超正常步行/骑行）→ [IntegrityTier.DEGRADED]（降品质，不阻断、不扣分）
     * - 其余 → [IntegrityTier.NORMAL]（正常）
     * 无前次定位（首次定位）或时间无效 → 视为正常（NORMAL），交由平台后续持续校验。
     */
    fun spawnIntegrity(ctx: SpawnContext): IntegrityResult {
        val prevLat = ctx.prevLat
        val prevLng = ctx.prevLng
        if (prevLat == null || prevLng == null) return IntegrityResult(true, "首次定位", tier = IntegrityTier.NORMAL)
        val dt = ctx.ts - ctx.prevTs
        if (dt <= 0L) return IntegrityResult(true, "时间无效-跳过", tier = IntegrityTier.NORMAL)
        val dist = haversine(prevLat, prevLng, ctx.lat, ctx.lng)
        val speed = dist / dt.toDouble() * 3.6
        if (dist >= TELEPORT_METERS && dt <= TELEPORT_WINDOW_SEC) {
            return IntegrityResult(false, "检测到瞬移：%.0f 米 / %d 秒".format(dist, dt), speed, IntegrityTier.REJECTED, true)
        }
        if (speed > SOFT_SPEED_KMH) {
            return IntegrityResult(true, "速度过快(降品质)：%.0f km/h".format(speed), speed, IntegrityTier.DEGRADED, true)
        }
        return IntegrityResult(true, "正常", speed, IntegrityTier.NORMAL, false)
    }

    /** 受信任的刷怪抽样：REJECTED 时返回空（隐藏灵脉）；DEGRADED 时按降品质权重抽样；NORMAL 时正常抽样。 */
    fun safeSpawns(ctx: SpawnContext, count: Int, rng: () -> Double = ::defaultRandom): List<String> {
        val res = spawnIntegrity(ctx)
        return when (res.tier) {
            IntegrityTier.REJECTED -> emptyList()
            else -> nearbySpawns(ctx, count, rng, degraded = res.degraded)
        }
    }
}
