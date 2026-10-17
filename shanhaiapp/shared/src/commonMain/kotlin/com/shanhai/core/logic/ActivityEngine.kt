package com.shanhai.core.logic

/**
 * 活动系统（GDD「活动·节气复玩 / 年节限时」+ 运营活动）。
 *
 * 纯函数引擎：以「日期区间 [start,end]」(YYYY-MM-DD，定宽字符串) 触发限时活动，
 * 叠加收妖 / 经验 / 收益三类全局乘子。活动为运营配置、不进存档，由 [GameState]
 * 按当日 lastDailyReset 查询当前生效活动并缝接各玩法接缝（capture/exp/income）。
 *
 * 注：刷怪稀有度分布仍由 [LocationEngine] 的节气（SolarTerm）驱动；本引擎负责
 * 运营期的全局增益层，二者正交、可叠加。
 */
enum class ActivityKind {
    /** 节日（中元 / 春节等限时） */
    FESTIVAL,
    /** 收妖活动 */
    CAPTURE_RALLY,
    /** 对战活动 */
    BATTLE_RALLY,
    /** 登录有礼 */
    LOGIN,
}

/** 单个限时活动定义。 */
data class Activity(
    val id: String,
    val name: String,
    val desc: String,
    val kind: ActivityKind,
    /** 起始日 YYYY-MM-DD（含） */
    val start: String,
    /** 结束日 YYYY-MM-DD（含） */
    val end: String,
    /** 收妖成功率乘子（默认 1.0） */
    val captureMul: Double = 1.0,
    /** 经验获取乘子（默认 1.0） */
    val expMul: Double = 1.0,
    /** 收益（香火/妖丹）乘子（默认 1.0） */
    val incomeMul: Double = 1.0,
)

/** 当前生效活动的聚合乘子。 */
data class ActivityBonuses(
    val captureMul: Double,
    val expMul: Double,
    val incomeMul: Double,
)

object ActivityEngine {
    /** 日期比较：a <= b（定宽 YYYY-MM-DD 字符串字典序即日期序）。 */
    fun dateLE(a: String, b: String): Boolean = a <= b

    /** 活动在 today 是否生效（含起止日）。 */
    fun isActive(a: Activity, today: String): Boolean = today >= a.start && today <= a.end

    /** 返回 today 当日所有生效活动。 */
    fun active(today: String): List<Activity> = ACTIVITIES.filter { isActive(it, today) }

    /** 把多个生效活动乘性聚合为单一乘子（无活动则全为 1.0）。 */
    fun aggregate(list: List<Activity>): ActivityBonuses {
        var c = 1.0
        var e = 1.0
        var inc = 1.0
        for (a in list) {
            c *= a.captureMul
            e *= a.expMul
            inc *= a.incomeMul
        }
        return ActivityBonuses(c, e, inc)
    }

    /** 活动库（运营配置；日期区间固定为示例窗口，上线由后端/配置下发覆盖）。 */
    val ACTIVITIES: List<Activity> = listOf(
        Activity(
            "act_zhongyuan", "中元鬼节", "鬼门开，百鬼夜行，收妖成功率大涨",
            ActivityKind.FESTIVAL, "2026-08-28", "2026-09-01", captureMul = 1.2,
        ),
        Activity(
            "act_chunjie", "新春嘉年华", "年兽限时现身，收妖成功率大涨",
            ActivityKind.FESTIVAL, "2026-02-16", "2026-02-22", captureMul = 1.3,
        ),
        Activity(
            "act_capture_rally", "收妖大作战", "七日收妖狂欢，收妖成功率提升",
            ActivityKind.CAPTURE_RALLY, "2026-08-15", "2026-08-21", captureMul = 1.15,
        ),
        Activity(
            "act_battle_rally", "论道大会", "七日论道，对战经验与收益提升",
            ActivityKind.BATTLE_RALLY, "2026-08-15", "2026-08-21",
            expMul = 1.25, incomeMul = 1.2,
        ),
        Activity(
            "act_login", "勤修有礼", "活动期间签到香火额外加成",
            ActivityKind.LOGIN, "2026-08-15", "2026-08-21", incomeMul = 1.1,
        ),
        Activity(
            "act_zhongqiu", "中秋月圆", "皓月当空，收妖成功率提升",
            ActivityKind.FESTIVAL, "2026-09-25", "2026-09-27", captureMul = 1.25,
        ),
        Activity(
            "act_guoqing", "国庆盛典", "普天同庆，对战经验与收益提升",
            ActivityKind.BATTLE_RALLY, "2026-10-01", "2026-10-07",
            expMul = 1.2, incomeMul = 1.15,
        ),
    )
}
