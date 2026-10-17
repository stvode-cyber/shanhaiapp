package com.shanhai.core.logic

/**
 * 每日 0 点静默重置（收口 GDD 十六/十九/二十 中多次出现的「每日」语义）。
 *
 * 原本跨天判定内联在 [GameState.maybeResetDaily] 中（字符串不等比较 + 首启空串触发）。
 * 这里把两件事抽成纯函数，便于：
 *  1. Python 等价验证（无需编译 Kotlin/Swift 即可断言判定与目录正确）；
 *  2. 平台层（后台定时 / 0 点推送 / 静默重置）复用同一份判定，而不必关心各系统如何重置。
 *
 * 抽出的纯函数：
 *  - [isCrossDay] —— 给定上次重置日与今日，是否跨天（应触发重置）。
 *  - [TARGETS]    —— 每日重置涉及的系统权威清单（供 UI 展示与验证）。
 *
 * 实际各系统的字段改动仍留在 [GameState.maybeResetDaily]（因字段异质），
 * 但由 [isCrossDay] 统一判定是否进入重置分支。
 */
object DailyResetEngine {

    /** 每日重置涉及的系统。顺序即 [GameState.maybeResetDaily] 中处理顺序。 */
    enum class Target(val id: String, val desc: String) {
        QUESTS(       "quests",        "每日任务刷新"),
        SECRET_REALM("secret_realm",  "在家秘境免费次数回满 (3)"),
        DUNGEON(      "dungeon",       "副本当日进入计数清零"),
        SHOP(         "shop",          "引妖香每日购买上限重置"),
        STRONGHOLD(   "stronghold",    "据点战占领节点重置"),
        REPUTATION(   "reputation",    "信誉分每日缓慢恢复 +1"),
        FATIGUE(      "fatigue",       "疲劳值每日重置为 0"),
    }

    /** 每日重置的权威系统清单。 */
    val TARGETS: List<Target> = Target.values().toList()

    /** 每日重置系统数量（权威计数，供断言/UI 使用）。 */
    val TARGET_COUNT: Int = TARGETS.size

    /**
     * 跨天判定（纯函数）。
     * - 首启（上次重置日为空串）视为跨天，使初始状态规整。
     * - 否则以「上次重置日 != 今日」为跨天判据（yyyy-MM-dd 字符串比较）。
     */
    fun isCrossDay(lastResetDay: String, today: String): Boolean {
        return lastResetDay.isEmpty() || lastResetDay != today
    }
}
