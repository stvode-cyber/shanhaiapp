package com.shanhai.core.logic

import com.shanhai.core.data.MonsterPool
import com.shanhai.core.data.defaultRandom
import com.shanhai.core.model.Rarity

/**
 * 祈妖抽卡引擎（GDD 十四·经济与变现 · 抽签祈妖）。
 *
 * 国风抽卡：摇签筒 → 掉签 → 解签 → 亮妖；签文映射稀有度
 * （上上签=神 / 上签=仙 / 中签=灵，无下下签坑）。重复妖转妖丹。
 *
 * 多池支持：
 *  - NORMAL（通用池）：灵 40% / 仙 45% / 神 15%，软保底 50 抽 / 硬保底 90 抽
 *  - LIMITED（限定神池）：神 1.8%（GDD 说的 1.5-2%），软保底 40 抽 / 硬保底 85 抽
 *    仅仙/神（无凡/无灵），适合短期活动引流
 *
 * 红线：付费只卖「机会/外观」，不卖成品战力。
 */
object GachaEngine {

    /** 抽卡池类型（GDD 十四·经济：常规池 + 限定神独立池）。 */
    enum class PoolType { NORMAL, LIMITED }

    // ── 通用池参数 ──
    const val NORMAL_LING = 0.40
    const val NORMAL_XIAN = 0.45
    const val NORMAL_SHEN = 0.15
    const val NORMAL_SOFT_PITY_START = 49
    const val NORMAL_HARD_PITY = 90
    const val NORMAL_SOFT_PITY_STEP = 0.02

    // ── 限定神池参数 ──
    /** 限定神池神率：1.8%（GDD 说 1.5-2%，取中间值） */
    const val LIMITED_SHEN = 0.018
    /** 限定神池仙率：98.2%（无凡无灵） */
    const val LIMITED_XIAN = 0.982
    /** 限定池软保底起始：40 抽（比通用池紧，诱导氪金） */
    const val LIMITED_SOFT_PITY_START = 39
    /** 限定池硬保底：85 抽 */
    const val LIMITED_HARD_PITY = 85
    const val LIMITED_SOFT_PITY_STEP = 0.025

    // ── 通用参数 ──
    const val SINGLE_LINGYU = 60        // 单抽灵玉
    const val TEN_LINGYU = 540          // 十连（9 折）
    const val DUPLICATE_YAODAN = 10     // 重复妖转化妖丹

    /** 按池类型取抽卡参数。 */
    private fun params(pool: PoolType) = when (pool) {
        PoolType.NORMAL -> Triple(NORMAL_SHEN to NORMAL_XIAN, NORMAL_SOFT_PITY_START, NORMAL_HARD_PITY)
        PoolType.LIMITED -> Triple(LIMITED_SHEN to LIMITED_XIAN, LIMITED_SOFT_PITY_START, LIMITED_HARD_PITY)
    }

    /** 抽卡状态：距上次出神已抽次数（硬保底靠它归零），按池分别维护。 */
    data class GachaState(
        var normalSinceShen: Int = 0,
        var limitedSinceShen: Int = 0,
    ) {
        fun sinceShenOf(pool: PoolType): Int = when (pool) {
            PoolType.NORMAL -> normalSinceShen
            PoolType.LIMITED -> limitedSinceShen
        }
        fun markShen(pool: PoolType) {
            when (pool) {
                PoolType.NORMAL -> normalSinceShen = 0
                PoolType.LIMITED -> limitedSinceShen = 0
            }
        }
        fun inc(pool: PoolType) {
            when (pool) {
                PoolType.NORMAL -> normalSinceShen++
                PoolType.LIMITED -> limitedSinceShen++
            }
        }
    }

    /** 签文等级（解签） */
    enum class SignTier { ZHONG, SHANG, SHANG_SHANG } // 中签 / 上签 / 上上签

    /** 单次抽卡结果 */
    data class GachaResult(
        val monsterId: String,
        val rarity: Rarity,
        val sign: SignTier,
        val pool: PoolType = PoolType.NORMAL,
    )

    /** 签文映射稀有度（上上=神 / 上=仙 / 中=灵） */
    fun signFor(rarity: Rarity): SignTier = when (rarity) {
        Rarity.SHEN -> SignTier.SHANG_SHANG
        Rarity.XIAN -> SignTier.SHANG
        else -> SignTier.ZHONG
    }

    /**
     * 依据保底状态 + 池类型判定本次稀有度。
     * - 距上次出神 ≥ 硬保底：必神；
     * - 进入软保底区间：神率 = 基础 + (sinceShen-START)×STEP，封顶 1。
     */
    fun rollRarity(state: GachaState, pool: PoolType, rng: () -> Double = ::defaultRandom): Rarity {
        val (baseRates, softStart, hardLimit) = params(pool)
        val (baseShen, baseXian) = baseRates
        val pity = state.sinceShenOf(pool)
        val step = if (pool == PoolType.LIMITED) LIMITED_SOFT_PITY_STEP else NORMAL_SOFT_PITY_STEP
        // 硬保底：先直接判必神（绕开 shenRate=1.0 + r<1.0 的边界 bug）
        if (pity >= hardLimit - 1) return Rarity.SHEN
        val shenRate = if (pity >= softStart) {
            (baseShen + (pity - softStart + 1) * step).coerceAtMost(1.0)
        } else {
            baseShen
        }
        val r = rng()
        return when {
            r < shenRate -> Rarity.SHEN
            r < shenRate + baseXian -> Rarity.XIAN
            else -> Rarity.LING
        }
    }

    /** 从指定稀有度池随机取一只妖（池为空则回退随机遇妖池）。限定池仅能出仙/神。 */
    private fun pickFrom(rarity: Rarity, pool: PoolType, rng: () -> Double): String {
        // 限定池不出灵/凡——防御性边界：rollRarity 保证不会走到 LING，但万一改逻辑留空兜底
        if (pool == PoolType.LIMITED && rarity <= Rarity.LING) {
            val shenPool = MonsterPool.byRarity(Rarity.SHEN)
            val xianPool = MonsterPool.byRarity(Rarity.XIAN)
            val all = shenPool + xianPool
            if (all.isNotEmpty()) return all[(rng() * all.size).toInt().coerceAtMost(all.size - 1)].id
        }
        val rp = MonsterPool.byRarity(rarity)
        return if (rp.isEmpty()) MonsterPool.randomEncounter(rng).id
        else rp[(rng() * rp.size).toInt().coerceAtMost(rp.size - 1)].id
    }

    /** 单抽：更新对应池保底计数并返回结果 */
    fun pull(state: GachaState, pool: PoolType = PoolType.NORMAL, rng: () -> Double = ::defaultRandom): GachaResult {
        val rarity = rollRarity(state, pool, rng)
        if (rarity == Rarity.SHEN) state.markShen(pool) else state.inc(pool)
        val id = pickFrom(rarity, pool, rng)
        return GachaResult(id, rarity, signFor(rarity), pool)
    }

    /** 十连：先抽 10 次，再保证「至少一只 ≥ 灵」。 */
    fun tenPull(state: GachaState, pool: PoolType = PoolType.NORMAL, rng: () -> Double = ::defaultRandom): List<GachaResult> {
        val results = (0 until 10).map { pull(state, pool, rng) }
        if (results.none { it.rarity >= Rarity.LING }) {
            val lastIdx = results.lastIndex
            val upgraded = results[lastIdx].copy(
                monsterId = pickFrom(Rarity.LING, pool, rng),
                rarity = Rarity.LING,
                sign = SignTier.ZHONG,
            )
            return results.toMutableList().also { it[lastIdx] = upgraded }
        }
        return results
    }
}
