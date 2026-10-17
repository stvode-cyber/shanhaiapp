package com.shanhai.core.logic

/**
 * 经济与变现引擎（GDD 十四·经济与变现）。
 *
 * 货币：灵玉（付费）/ 香火（体力，每日回满）/ 妖丹（重复妖转化）。
 * 红线：付费**只卖「机会/外观」，不卖成品战力**。
 *
 * 本切片实现「引妖香」商店项（香火回补，用灵玉购，日购上限防通胀）。
 * 首充 / 月卡 / 战令 / 单抽价等定价待定（行业区间），此处仅落地可验证的兑换与上限逻辑。
 */
object ShopEngine {

    /** 引妖香：1 份花费灵玉 / 回补香火 */
    const val INCENSE_REFILL_COST_LINGYU = 30
    const val INCENSE_REFILL_AMOUNT = 60
    /** 日购上限（防通胀，GDD：限购日上限） */
    const val INCENSE_DAILY_BUY_CAP = 3

    /** 商店本地购买状态（每日重置由 GameState 处理） */
    data class ShopState(
        var incenseBoughtToday: Int = 0,
        var lastBuyDay: String = "",
    )

    /** 是否可购引妖香：灵玉足够 + 当日未达上限 */
    fun canBuyIncense(state: ShopState, today: String, lingyu: Int): Boolean {
        if (lingyu < INCENSE_REFILL_COST_LINGYU) return false
        val bought = if (state.lastBuyDay == today) state.incenseBoughtToday else 0
        return bought < INCENSE_DAILY_BUY_CAP
    }

    /**
     * 购买引妖香：成功返回 (香火获得量, 灵玉花费)，否则 null。
     * 调用方负责把返回的香火/灵玉并入 GameState 并写回 lastBuyDay。
     */
    fun buyIncense(state: ShopState, today: String, lingyu: Int): Pair<Int, Int>? {
        if (!canBuyIncense(state, today, lingyu)) return null
        if (state.lastBuyDay != today) {
            state.lastBuyDay = today
            state.incenseBoughtToday = 0
        }
        state.incenseBoughtToday += 1
        return INCENSE_REFILL_AMOUNT to INCENSE_REFILL_COST_LINGYU
    }
}
