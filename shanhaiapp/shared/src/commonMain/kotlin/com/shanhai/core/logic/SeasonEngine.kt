package com.shanhai.core.logic

/**
 * 赛季通行证（GDD 十九·长线留存：90 天双轨）。
 *
 * - 一个赛季固定 [SEASON_LENGTH_DAYS] 天；玩家通过日常活动（收妖/对战/副本/签到）累计赛季经验 [seasonXp]。
 * - 每 [XP_PER_TIER] 点经验解锁一档（1..[TIERS]），档位同时提供「免费轨道」与「付费轨道」奖励。
 * - 付费轨道需先购买通行证（[GameState.buySeasonPass]，耗灵玉）；购买后可补领已解锁的付费档。
 * - 赛季结束后滚动到下一赛季（序号 +1、经验/已领清零、通行证失效），由平台在启动/进入前台时调用 [GameState.tickSeason]。
 *
 * 全程纯函数，可 Python 等价验证；所有时间以毫秒 epoch 传入，便于确定性测试。
 */
object SeasonEngine {
    const val SEASON_LENGTH_DAYS = 90
    const val XP_PER_TIER = 100
    const val TIERS = 60
    const val DAY_MS = 86_400_000L

    // 各活动单次提供的赛季经验（供 GameState 接入真实玩法）
    const val XP_CATCH = 5
    const val XP_BATTLE = 10
    const val XP_DUNGEON = 15
    const val XP_SIGNIN = 8

    /** 赛季内第几天：未开始返回 0；赛季内进行中 1..[SEASON_LENGTH_DAYS]；已结束返回 > 长度。 */
    fun dayIndex(startMs: Long, nowMs: Long): Int {
        if (startMs <= 0 || nowMs < startMs) return 0
        return ((nowMs - startMs) / DAY_MS).toInt() + 1
    }

    /** 当前是否处于赛季进行中。 */
    fun inSeason(startMs: Long, nowMs: Long): Boolean {
        val d = dayIndex(startMs, nowMs)
        return d in 1..SEASON_LENGTH_DAYS
    }

    /** 赛季剩余天数（未开始返回满长；已结束返回 0）。 */
    fun daysRemaining(startMs: Long, nowMs: Long): Int {
        val d = dayIndex(startMs, nowMs)
        if (d <= 0) return SEASON_LENGTH_DAYS
        if (d > SEASON_LENGTH_DAYS) return 0
        return SEASON_LENGTH_DAYS - d + 1
    }

    /** 累计经验 → 已解锁档位（封顶 [TIERS]）。 */
    fun tierFromXp(xp: Int): Int = (xp / XP_PER_TIER).coerceAtMost(TIERS)

    /** 达到 [tier] 档所需累计经验阈值。 */
    fun xpForTier(tier: Int): Int = tier * XP_PER_TIER

    /** 免费轨道某档奖励（每 10 档为里程碑，奖励更厚）。 */
    fun freeReward(tier: Int): SeasonReward =
        if (tier % 10 == 0) SeasonReward(incense = 200, lingyu = 0, yaodan = 15)
        else SeasonReward(incense = 30, lingyu = 0, yaodan = 3)

    /** 付费轨道某档奖励（每 10 档为里程碑，奖励更厚）。 */
    fun paidReward(tier: Int): SeasonReward =
        if (tier % 10 == 0) SeasonReward(incense = 0, lingyu = 60, yaodan = 20)
        else SeasonReward(incense = 0, lingyu = 10, yaodan = 5)
}

/** 单档奖励内容（纯数据）。 */
data class SeasonReward(
    val incense: Int = 0,   // 香火
    val lingyu: Int = 0,    // 灵玉（付费货币）
    val yaodan: Int = 0,    // 妖丹
)

/** 领取某档位的结果（标记免费/付费是否实际发放，便于 UI 反馈）。 */
data class SeasonClaimResult(
    val tier: Int,
    val claimedFree: Boolean,
    val claimedPaid: Boolean,
    val incense: Int,
    val lingyu: Int,
    val yaodan: Int,
)
