package com.shanhai.core.logic

/**
 * 排位赛段位天梯（GDD 二十·竞技与荣誉）。
 *
 * 5 段位：炼气 → 筑基 → 金丹 → 元婴 → 化神（炼气最低、化神最高）。
 * 每升一段需在本段攒满 [POINTS_TO_PROMOTE] 积分；胜则加点、负则扣点，
 * 满点升段、不足 0 降段。化神封顶（积分不溢出）、炼气保底（积分不低于 0）。
 *
 * 注：ban/pick + meta 平衡依赖真实对手匹配（平台侧 PvP），
 * 本引擎只负责段位积分数学，胜负结果由平台在对局结算后回传。
 */
object RankEngine {
    /** 段位名（索引 0=炼气 … 4=化神）。 */
    val TIERS: List<String> = listOf("炼气", "筑基", "金丹", "元婴", "化神")

    const val MIN_TIER_INDEX = 0            // 炼气
    const val MAX_TIER_INDEX = 4            // 化神
    const val POINTS_TO_PROMOTE = 100       // 每段攒满 100 点升段
    const val WIN_POINTS = 20               // 胜利 +20
    const val LOSS_POINTS = 15              // 失败 -15

    /** 段位名 → 索引（未知返回 0）。 */
    fun tierIndex(name: String): Int = TIERS.indexOf(name).let { if (it < 0) MIN_TIER_INDEX else it }

    /** 索引 → 段位名（越界夹取）。 */
    fun tierName(index: Int): String = TIERS.getOrElse(index.coerceIn(MIN_TIER_INDEX, MAX_TIER_INDEX)) { TIERS[MIN_TIER_INDEX] }

    /**
     * 应用一场对局结果，返回新段位/积分与升降段标记。
     * - 胜利：point += 20；满 100 则升段并扣 100（可连跳），化神封顶。
     * - 失败：point -= 15；不足 0 则降段并补 100（可连降，保留“负债”），炼气保底 0。
     */
    fun applyResult(tierIndex: Int, points: Int, won: Boolean): RankResult {
        var t = tierIndex.coerceIn(MIN_TIER_INDEX, MAX_TIER_INDEX)
        var p = points.coerceIn(0, POINTS_TO_PROMOTE - 1)   // 段内积分合法区间，抵御坏档/越界输入
        var promoted = false
        var demoted = false
        if (won) {
            p += WIN_POINTS
            while (p >= POINTS_TO_PROMOTE && t < MAX_TIER_INDEX) {
                p -= POINTS_TO_PROMOTE
                t += 1
                promoted = true
            }
            if (t == MAX_TIER_INDEX) p = p.coerceAtMost(POINTS_TO_PROMOTE - 1) // 化神封顶
        } else {
            p -= LOSS_POINTS
            while (p < 0 && t > MIN_TIER_INDEX) {
                p += POINTS_TO_PROMOTE
                t -= 1
                demoted = true
            }
            if (t == MIN_TIER_INDEX) p = p.coerceAtLeast(0) // 炼气保底
        }
        return RankResult(t, p, promoted, demoted)
    }

    /**
     * 升段荣誉奖励（按新段位发少量货币，展示型荣誉为主）。
     * 炼气(0)无、筑基(1)→香火30、金丹(2)→灵玉30、元婴(3)→香火60+灵玉30、化神(4)→灵玉80+妖丹10。
     */
    fun rankReward(tierIndex: Int): RankReward {
        return when (tierIndex.coerceIn(MIN_TIER_INDEX, MAX_TIER_INDEX)) {
            1 -> RankReward(incense = 30, lingyu = 0, yaodan = 0)
            2 -> RankReward(incense = 0, lingyu = 30, yaodan = 0)
            3 -> RankReward(incense = 60, lingyu = 30, yaodan = 0)
            4 -> RankReward(incense = 0, lingyu = 80, yaodan = 10)
            else -> RankReward(0, 0, 0)
        }
    }
}

/** 段位积分变更结果。 */
data class RankResult(
    val tierIndex: Int,
    val points: Int,
    val promoted: Boolean,
    val demoted: Boolean,
)

/** 升段荣誉奖励。 */
data class RankReward(val incense: Int = 0, val lingyu: Int = 0, val yaodan: Int = 0)
