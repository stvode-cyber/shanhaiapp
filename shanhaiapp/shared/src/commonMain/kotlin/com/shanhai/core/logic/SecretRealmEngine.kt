package com.shanhai.core.logic

import com.shanhai.core.data.MonsterPool
import com.shanhai.core.data.defaultRandom
import com.shanhai.core.model.Element
import com.shanhai.core.model.Monster
import com.shanhai.core.model.Rarity

/**
 * 在家秘境刷妖引擎（GDD 三·探索双模式·在家秘境）。
 *
 * 玩家点「入秘境」进入随机主题小地图（山林/水域/古墓/东海），主题决定五行偏向，
 * 按秘境稀有度权重（凡/灵主导）加权随机刷妖，用同一套点按收妖。
 * 秘境收的妖不计入地域图鉴（由 Codex.fromSecretRealm 处理），每日限次或耗香火（由 GameState 管理）。
 *
 * 本引擎只做**可验证的派生计算**（主题→五行偏向 + 加权抽样），平台把随机源喂入即可。
 */
object SecretRealmEngine {
    /** 每次入秘境生成的刷妖数量 */
    const val SPAWN_COUNT = 6

    /** 每日免费次数耗尽后，再入秘境需消耗的香火 */
    const val INCENSE_COST = 20

    /**
     * 秘境稀有度权重：凡/灵主导（对比 LBS 凡45/灵35/仙15/神5，秘境更压低高阶，
     * 体现「秘境多见凡/灵级」的平衡杠杆）。
     */
    private val RARITY_WEIGHT: Map<Rarity, Double> = mapOf(
        Rarity.FAN to 60.0,
        Rarity.LING to 30.0,
        Rarity.XIAN to 8.0,
        Rarity.SHEN to 2.0,
    )

    /** 主题五行偏向加成（秘境比 LBS 的 1.8 更强调主题一致性） */
    const val ELEMENT_BIAS = 3.0

    /** 主题 → 五行偏向（复用 LBS 地域主题映射） */
    fun themeElementBias(theme: RegionTheme): Element = LocationEngine.regionElementBias(theme)

    /** 随机选一个主题（均匀分布） */
    fun pickTheme(rng: () -> Double = ::defaultRandom): RegionTheme {
        val themes = RegionTheme.values()
        val idx = (rng() * themes.size).toInt() % themes.size
        return themes[idx]
    }

    private fun weightOf(m: Monster, theme: RegionTheme): Double {
        val base = RARITY_WEIGHT[m.rarity] ?: 1.0
        return if (m.element == themeElementBias(theme)) base * ELEMENT_BIAS else base
    }

    /**
     * 依据主题加权抽样 [count] 只妖的 id（可重复，模拟连刷）。
     * 主题五行偏向 ×3.0，稀有度权重凡/灵主导。返回妖 id 列表。
     */
    fun generateSpawns(theme: RegionTheme, count: Int, rng: () -> Double = ::defaultRandom): List<String> {
        val pool = MonsterPool.all
        val weights = pool.map { weightOf(it, theme) }
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
}
