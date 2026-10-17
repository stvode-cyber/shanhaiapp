package com.shanhai.core.logic

/**
 * 信誉分引擎（GDD 十六·公平与稳健）。
 *
 * 与 #76 的 GPS 防作弊（[LocationEngine.spawnIntegrity]）配合形成闭环：
 * 检测异常 → 扣信誉分 → 分层 → 软限制（罕见怪 −30% / 仅可入秘境）。
 *
 * 信誉分为**纯客户端侧评分**，用于提示与软限制；权威裁决仍由服务器
 * （`SocialService` 后端）做最终判定（本切片未实现）。
 */
enum class ReputationTier { EXCELLENT, OBSERVED, RESTRICTED }

/** 作弊类型（由 GPS 防作弊结果映射而来）。 */
enum class CheatKind { TELEPORT, SPEED, UNKNOWN }

object ReputationEngine {
    const val DEFAULT_SCORE = 100
    const val MAX_SCORE = 100
    const val MIN_SCORE = 0
    /** 信誉分低于此值 → 受限（仅可入秘境） */
    const val RESTRICTED_BELOW = 60
    /** 信誉分低于此值 → 观察（罕见怪 −30%）；≥ 此值 → 优秀 */
    const val OBSERVED_BELOW = 80

    /** 各作弊类型的扣分 */
    private val PENALTY = mapOf(
        CheatKind.TELEPORT to 18,
        CheatKind.SPEED to 12,
        CheatKind.UNKNOWN to 10,
    )

    /** 信誉分层。 */
    fun tier(score: Int): ReputationTier {
        val s = score.coerceIn(MIN_SCORE, MAX_SCORE)
        return when {
            s < RESTRICTED_BELOW -> ReputationTier.RESTRICTED
            s < OBSERVED_BELOW -> ReputationTier.OBSERVED
            else -> ReputationTier.EXCELLENT
        }
    }

    /** 分层中文标签（UI 展示用）。 */
    fun tierLabel(score: Int): String = when (tier(score)) {
        ReputationTier.EXCELLENT -> "优秀"
        ReputationTier.OBSERVED -> "观察"
        ReputationTier.RESTRICTED -> "受限"
    }

    /** 罕见（仙/神）妖刷怪倍率：观察层 −30%，其余正常。 */
    fun rareSpawnMultiplier(score: Int): Double =
        if (tier(score) == ReputationTier.OBSERVED) 0.7 else 1.0

    /** 是否可进行户外 LBS 探索：受限层禁止（仅可入秘境）。 */
    fun canExploreLbs(score: Int): Boolean = tier(score) != ReputationTier.RESTRICTED

    /** 施加作弊扣分（钳制在 [0,100]）。 */
    fun applyPenalty(score: Int, kind: CheatKind): Int =
        (score - (PENALTY[kind] ?: 10)).coerceIn(MIN_SCORE, MAX_SCORE)

    /** 每日缓慢恢复 +1（钳制在 [0,100]，与 tier/applyPenalty 一致）。 */
    fun dailyRecover(score: Int): Int = (score + 1).coerceIn(MIN_SCORE, MAX_SCORE)
}
