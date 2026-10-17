package com.shanhai.core.logic

import com.shanhai.core.logic.SeasonEngine.DAY_MS
import com.shanhai.core.logic.SeasonEngine.SEASON_LENGTH_DAYS

/**
 * 每日静默重置——平台调度契约层（收口 GDD 十六/十九/二十 中「每日」「每赛季」语义）。
 *
 * 所有判定收敛在 shared 纯函数，平台层只负责「何时触发」：
 *  - 跨天判据复用 [DailyResetEngine.isCrossDay]（唯一权威，不重复实现）；
 *  - 重置系统清单复用 [DailyResetEngine.TARGETS]（7 项权威）；
 *  - 赛季周期复用 [SeasonEngine.SEASON_LENGTH_DAYS] / [SeasonEngine.DAY_MS]。
 *
 * 平台实现（iOS BGTask / Android WorkManager / 0 点推送）应直接复用 [resolvePending]
 * 与 [DailyResetSchedulerContracts.seasonsElapsed]，把判定逻辑留在 shared，便于 Python 等价验证。
 */
interface DailyResetScheduler {

    /** 平台是否已具备后台定时 / 0 点推送能力（如 iOS BGTask / Android WorkManager）。 */
    fun isAvailable(): Boolean

    /** 注册每日 0 点静默重置任务（幂等，重复调用安全）。返回是否注册成功。 */
    fun register(): Boolean

    /** 注销调度（用户注销 / 卸载前清理）。幂等。 */
    fun unregister()

    /** 当前调度状态（供 UI / 健康度检查）。 */
    fun status(): SchedulerStatus

    /**
     * 由平台在「怀疑跨天」时调用（0 点推送 / 应用回到前台 / 设备重启后）。
     * 默认实现复用 [DailyResetEngine.isCrossDay]：若跨天返回 [DailyResetEngine.TARGETS]，
     * 否则返回空列表（调用方不应改动任何字段）。
     */
    fun resolvePending(lastResetDay: String, today: String): List<DailyResetEngine.Target> {
        return if (DailyResetEngine.isCrossDay(lastResetDay, today)) DailyResetEngine.TARGETS
        else emptyList()
    }
}

/** 调度状态枚举（与平台后台能力对应）。 */
enum class SchedulerStatus(val id: String) {
    IDLE("idle"),
    REGISTERED("registered"),
    SUSPENDED("suspended"),   // 低电量 / 后台受限
    UNAVAILABLE("unavailable")
}

/**
 * 每日静默重置——赛季推进纯函数（与 [DailyResetScheduler] 配套，便于 Python 等价验证）。
 * 给定赛季开始日(ms)与今日(ms)，返回已跨过的赛季数（0 表示仍在同一赛季）。
 * 不修改任何状态——仅给出「应推进几个赛季」。
 */
object DailyResetSchedulerContracts {
    fun seasonsElapsed(seasonStartMs: Long, nowMs: Long): Int {
        if (nowMs < seasonStartMs) return 0
        val elapsedDays = (nowMs - seasonStartMs) / DAY_MS
        return (elapsedDays / SEASON_LENGTH_DAYS).toInt()
    }
}
