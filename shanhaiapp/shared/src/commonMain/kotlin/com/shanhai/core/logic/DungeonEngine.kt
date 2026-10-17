package com.shanhai.core.logic

import com.shanhai.core.data.defaultRandom

/**
 * 副本妖境引擎（GDD 十二·副本系统）。
 *
 * 四类：山海异闻（剧情）/ 灵脉试炼（日常刷资源）/ 渡劫秘境（成长材料）/ 年节限时（活动）。
 * 进入消耗香火，每日限次；通关掉落：妖丹、渡劫材料、符纸、外观、妖王概率收服（补全图鉴）。
 * 战斗复用 [BattleEngine]（本切片由奖励表直接结算，不进入实时战斗）。
 */
enum class DungeonType {
    YIWEN,    // 山海异闻（剧情）
    SHILIAN,  // 灵脉试炼（日常刷资源）
    DUOJIE,   // 渡劫秘境（成长材料）
    NIANJIE,  // 年节限时（活动）
}

/** 副本定义（不可变，来自 DungeonLibrary） */
data class DungeonDef(
    val id: String,
    val type: DungeonType,
    val name: String,
    val desc: String,
    val costIncense: Int,
    val dailyLimit: Int,
    val yaodanRange: IntRange,          // 妖丹产出区间
    val materialRange: IntRange,        // 渡劫材料产出区间
    val talismanChance: Double,         // 符纸掉落概率
    val skinChance: Double,             // 外观掉落概率
    val bossMonsterId: String? = null,  // 妖王（可收服补全图鉴）
    val bossCaptureChance: Double = 0.0,
)

/** 副本库（切片：每类 1~2 个代表副本） */
object DungeonLibrary {
    val all: List<DungeonDef> = listOf(
        DungeonDef("d_yiwen_taotie", DungeonType.YIWEN, "饕餮之祸", "山海异闻·封印贪食巨兽",
            costIncense = 20, dailyLimit = 3, yaodanRange = 5..10, materialRange = 0..0,
            talismanChance = 0.3, skinChance = 0.1, bossMonsterId = "guai_taotie", bossCaptureChance = 0.15),
        DungeonDef("d_shilian_lingmai", DungeonType.SHILIAN, "灵脉试炼", "日常刷妖丹与符纸",
            costIncense = 20, dailyLimit = 5, yaodanRange = 8..15, materialRange = 0..2,
            talismanChance = 0.5, skinChance = 0.0),
        DungeonDef("d_duojie_lei", DungeonType.DUOJIE, "渡劫秘境·雷", "产出渡劫材料（雷系）",
            costIncense = 30, dailyLimit = 3, yaodanRange = 6..12, materialRange = 2..4,
            talismanChance = 0.4, skinChance = 0.05),
        DungeonDef("d_nianjie_chun", DungeonType.NIANJIE, "年节·除岁", "春节限时活动副本",
            costIncense = 30, dailyLimit = 1, yaodanRange = 10..20, materialRange = 1..3,
            talismanChance = 0.6, skinChance = 0.2, bossMonsterId = "guai_nianshou", bossCaptureChance = 0.25),
    )

    fun byId(id: String): DungeonDef? = all.firstOrNull { it.id == id }
}

object DungeonEngine {

    /** 副本当日进入次数状态 */
    data class DungeonRunState(val enteredToday: MutableMap<String, Int> = mutableMapOf())

    /** 是否可进入：香火足够且未超每日限次 */
    fun canEnter(def: DungeonDef, state: DungeonRunState, incense: Int, today: String): Boolean {
        if (incense < def.costIncense) return false
        val entered = state.enteredToday[def.id] ?: 0
        return entered < def.dailyLimit
    }

    /** 通关奖励 */
    data class DungeonReward(
        val yaodan: Int,
        val material: Int,
        val talisman: Boolean,
        val skin: Boolean,
        val bossCaptured: Boolean,
        val bossMonsterId: String?,
    )

    /**
     * 结算一次副本奖励（确定性依赖 [rng]，便于测试）。
     * 调用方需先确认 [canEnter] 通过，并负责扣香火 / 记当日次数。
     */
    fun enter(def: DungeonDef, state: DungeonRunState, rng: () -> Double = ::defaultRandom): DungeonReward {
        val yaodan = def.yaodanRange.first + ((rng() * (def.yaodanRange.last - def.yaodanRange.first + 1)).toInt())
        val material = if (def.materialRange.last > 0)
            def.materialRange.first + ((rng() * (def.materialRange.last - def.materialRange.first + 1)).toInt())
        else 0
        val talisman = rng() < def.talismanChance
        val skin = rng() < def.skinChance
        val bossCaptured = def.bossMonsterId != null && rng() < def.bossCaptureChance
        return DungeonReward(yaodan, material, talisman, skin, bossCaptured, def.bossMonsterId)
    }
}
