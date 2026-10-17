package com.shanhai.core.logic

/**
 * 家园洞府（GDD 十九·长线留存）。
 *
 * 放置产出：洞府随时间产出香火与妖丹，产出速率随「阵法风水」等级提升；
 * 离线时长累计产出，但封顶 [MAX_OFFLINE_HOURS] 小时（防止长期离线滚雪球）。
 * 阵法风水另提供全局战力增益（[fengshuiAttackBonus]，作为报告值供战斗/养成叠加）。
 *
 * 全程纯函数，可 Python 等价验证；所有时间以毫秒 epoch 传入，便于确定性测试。
 */
object HomesteadEngine {
    const val INCENSE_PER_HOUR_BASE = 20.0   // 阵法 Lv0 香火时产
    const val PILL_PER_HOUR_BASE = 1.0       // 阵法 Lv0 妖丹时产
    const val MAX_OFFLINE_HOURS = 12.0       // 离线产出封顶时长（小时）
    const val HOUR_MS = 3_600_000L

    const val FENGSHUI_INCENSE_MUL = 0.10    // 每级 +10% 香火时产
    const val FENGSHUI_PILL_PER_LEVEL = 0.1  // 每级 +0.1 妖丹时产
    const val FENGSHUI_ATTACK_PER_LEVEL = 0.02 // 每级 +2% 全局攻击增益（报告值）

    /** 阵法等级 → 香火时产（/小时）。 */
    fun incenseRate(level: Int): Double =
        INCENSE_PER_HOUR_BASE * (1.0 + FENGSHUI_INCENSE_MUL * level)

    /** 阵法等级 → 妖丹时产（/小时）。 */
    fun pillRate(level: Int): Double =
        PILL_PER_HOUR_BASE + FENGSHUI_PILL_PER_LEVEL * level

    /** 阵法等级 → 全局攻击增益（小数，如 0.06 = +6%）。 */
    fun fengshuiAttackBonus(level: Int): Double =
        FENGSHUI_ATTACK_PER_LEVEL * level

    /** 升级到下一级所需妖丹（当前等级 level → level+1）。 */
    fun upgradeCost(level: Int): Int = 100 * (level + 1)

    /**
     * 计算 [lastMs] 到 [nowMs] 之间的挂起产出。
     * - [lastMs] <= 0 视为「从未收取」，返回 0（首收不发历史产出，避免新号白嫖）。
     * - 真实时长按小时计，封顶 [MAX_OFFLINE_HOURS]。
     * 返回 (香火整数, 妖丹整数, 实际计费小时数, 是否触顶)。
     */
    fun compute(level: Int, nowMs: Long, lastMs: Long): HomesteadResult {
        if (lastMs <= 0 || nowMs <= lastMs) {
            return HomesteadResult(0, 0, 0.0, capped = false)
        }
        val elapsedHours = (nowMs - lastMs).toDouble() / HOUR_MS
        val cappedHours = if (elapsedHours > MAX_OFFLINE_HOURS) MAX_OFFLINE_HOURS else elapsedHours
        val incense = (incenseRate(level) * cappedHours).toInt()
        val pills = (pillRate(level) * cappedHours).toInt()
        return HomesteadResult(incense, pills, cappedHours, capped = elapsedHours > MAX_OFFLINE_HOURS)
    }
}

/** 家园洞府一次收取的产出结果（纯数据）。 */
data class HomesteadResult(
    val incense: Int,     // 本次获得香火
    val pills: Int,       // 本次获得妖丹
    val hours: Double,    // 实际计费小时数
    val capped: Boolean,  // 是否触顶 12h
)
