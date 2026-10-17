package com.shanhai.core.logic

/**
 * 疲劳值 / 单日收益封顶（GDD 十六·公平与稳健·防脚本）。
 *
 * - 玩家通过日常活动（收妖 / 对战 / 副本 / 签到）累积 [fatigue]（0..[MAX_FATIGUE]）。
 * - 疲劳越高，可获取的收益倍率越低：低于 [SOFT_CAP] 全额（×1.0）；达到 [SOFT_CAP] 起减半（×0.5）；
 *   达到 [MAX_FATIGUE] 后收益封顶（×0.0），防止脚本长时间挂机刷资源。
 * - 疲劳每日跨天重置（[GameState.resetFatigueDaily]，由 maybeResetDaily 触发）。
 *
 * 全程纯函数，可 Python 等价验证；倍率仅作用于「收益」，不影响玩法进度。
 */
object FatigueEngine {
    const val MAX_FATIGUE = 100
    const val SOFT_CAP = 60

    // 各活动单次累积的疲劳（供 GameState 接入真实玩法）
    const val FATIGUE_CATCH = 2
    const val FATIGUE_BATTLE = 4
    const val FATIGUE_DUNGEON = 8
    const val FATIGUE_SIGNIN = 3

    /**
     * 当前疲劳对应的收益倍率：
     * - [MAX_FATIGUE]：封顶（×0.0）
     * - [SOFT_CAP]..<[MAX_FATIGUE]：疲劳（×0.5）
     * - < [SOFT_CAP]：充沛（×1.0）
     */
    fun incomeMul(fatigue: Int): Double = when {
        fatigue >= MAX_FATIGUE -> 0.0
        fatigue >= SOFT_CAP -> 0.5
        else -> 1.0
    }

    /** 累积疲劳并夹取到 0..[MAX_FATIGUE]。 */
    fun addFatigue(current: Int, delta: Int): Int =
        (current + delta).coerceIn(0, MAX_FATIGUE)

    /** 跨天重置为 0。 */
    fun resetDaily(): Int = 0

    /** 疲劳状态文案（供 UI 展示）。 */
    fun label(fatigue: Int): String = when {
        fatigue >= MAX_FATIGUE -> "透支·收益封顶"
        fatigue >= SOFT_CAP -> "疲劳·收益减半"
        else -> "充沛"
    }
}
