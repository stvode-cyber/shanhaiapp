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
 * 出率（常规池）：灵 40% / 仙 45% / 神 15%。
 * 保底：十连保底 ≥ 灵；软保底第 50 抽起升神率；硬保底 90 抽必神。
 * 红线：付费只卖「机会/外观」，不卖成品战力。
 */
object GachaEngine {

    const val POOL_LING = 0.40
    const val POOL_XIAN = 0.45
    const val POOL_SHEN = 0.15

    const val SOFT_PITY_START = 49      // 连抽未出神满 49 次后（即第 50 抽起），神率线性提升
    const val HARD_PITY = 90            // 连抽未出神满 89 次后（即第 90 抽）硬保底必神
    const val SOFT_PITY_STEP = 0.02     // 每抽额外神率

    const val SINGLE_LINGYU = 60        // 单抽灵玉
    const val TEN_LINGYU = 540          // 十连（9 折）
    const val DUPLICATE_YAODAN = 10     // 重复妖转化妖丹

    /** 抽卡状态：距上次出神已抽次数（硬保底靠它归零） */
    data class GachaState(var sinceShen: Int = 0)

    /** 签文等级（解签） */
    enum class SignTier { ZHONG, SHANG, SHANG_SHANG } // 中签 / 上签 / 上上签

    /** 单次抽卡结果 */
    data class GachaResult(
        val monsterId: String,
        val rarity: Rarity,
        val sign: SignTier,
    )

    /** 签文映射稀有度（上上=神 / 上=仙 / 中=灵） */
    fun signFor(rarity: Rarity): SignTier = when (rarity) {
        Rarity.SHEN -> SignTier.SHANG_SHANG
        Rarity.XIAN -> SignTier.SHANG
        else -> SignTier.ZHONG
    }

    /**
     * 依据保底状态判定本次稀有度。
     * - 距上次出神 ≥ [HARD_PITY]：必神；
     * - 进入软保底区间：神率 = 基础 + (sinceShen-START)×STEP，封顶 1。
     * 注：抽卡池不含凡级，故基础区间只有 灵/仙/神。
     */
    fun rollRarity(state: GachaState, rng: () -> Double = ::defaultRandom): Rarity {
        val pity = state.sinceShen   // 距上次出神已抽次数（本抽之前）
        val shenRate = if (pity >= HARD_PITY - 1) {
            1.0
        } else if (pity >= SOFT_PITY_START) {
            (POOL_SHEN + (pity - SOFT_PITY_START + 1) * SOFT_PITY_STEP).coerceAtMost(1.0)
        } else {
            POOL_SHEN
        }
        val r = rng()
        return when {
            r < shenRate -> Rarity.SHEN
            r < shenRate + POOL_XIAN -> Rarity.XIAN
            else -> Rarity.LING
        }
    }

    /** 从指定稀有度池随机取一只妖（池为空则回退随机遇妖池） */
    private fun pickFrom(rarity: Rarity, rng: () -> Double): String {
        val pool = MonsterPool.byRarity(rarity)
        return if (pool.isEmpty()) MonsterPool.randomEncounter(rng).id
        else pool[(rng() * pool.size).toInt().coerceAtMost(pool.size - 1)].id
    }

    /** 单抽：更新保底计数并返回结果 */
    fun pull(state: GachaState, rng: () -> Double = ::defaultRandom): GachaResult {
        val rarity = rollRarity(state, rng)
        if (rarity == Rarity.SHEN) state.sinceShen = 0 else state.sinceShen++
        val id = pickFrom(rarity, rng)
        return GachaResult(id, rarity, signFor(rarity))
    }

    /**
     * 十连：先抽 10 次，再保证「至少一只 ≥ 灵」（基础池本已无凡级，此为防御性兜底）。
     * 若 10 次全低于灵（理论上不可能，但保代码健壮性），把最后一只升级为随机灵级妖。
     */
    fun tenPull(state: GachaState, rng: () -> Double = ::defaultRandom): List<GachaResult> {
        val results = (0 until 10).map { pull(state, rng) }
        if (results.none { it.rarity >= Rarity.LING }) {
            val lastIdx = results.lastIndex
            val upgraded = results[lastIdx].copy(
                monsterId = pickFrom(Rarity.LING, rng),
                rarity = Rarity.LING,
                sign = SignTier.ZHONG,
            )
            return results.toMutableList().also { it[lastIdx] = upgraded }
        }
        return results
    }
}
