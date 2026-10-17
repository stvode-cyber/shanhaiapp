package com.shanhai.core.quest

import com.shanhai.core.model.Element
import com.shanhai.core.model.Rarity
import com.shanhai.core.model.TalentBranch
import kotlinx.serialization.Serializable

/**
 * 任务分类（GDD 十二·副本 + 十八·新手引导「今日目标」+ 二十·成就勋章）。
 * 今日目标每日重置；其余为长线收集/剧情/活动。
 */
enum class QuestCategory(val label: String) {
    DAILY("今日目标"),
    STORY("山海异闻"),
    TRIAL("灵脉试炼"),
    ACHIEVEMENT("成就"),
    EVENT("年节限时"),
}

/**
 * 目标类型——决定进度如何被游戏事件驱动。
 * - CATCH_TOTAL      累计收服任意妖 N 只
 * - CATCH_ELEMENT    收服指定行（ref=Element.name）妖 1 只
 * - CATCH_RARITY     收服指定稀有度（ref=Rarity.name）妖 1 只
 * - WIN_BATTLE       累计对战获胜 N 场
 * - WIN_WITH_ELEMENT 用指定行（ref=Element.name）妖赢 1 场
 * - ENTER_SECRET     累计进入秘境 N 次
 * - CODEX_RATIO      图鉴完成度达到 ref 百分比（取最大值，不回退）
 * - TRAIN_LEVEL      培养出任一妖达到 ref 级（取最大值，不回退）
 * - BREAKTHROUGH_TOTAL 累计突破 N 次（累加，封顶）
 * - EVOLVE_TOTAL     累计进化 N 次（累加，封顶）
 * - EQUIP_TOTAL      累计装备（穿戴）妖 N 次（累加，封顶）
 * - FORGE_TOTAL      累计祭炼装备 N 次（累加，封顶）
 * - FUSE_TOTAL       累计炼妖融合 N 次（累加，封顶）
 * - TRIBULATE_TOTAL  累计渡劫飞升 N 次（累加，封顶）
 * - TALENT_TOTAL     累计为妖点天赋 N 次（累加，封顶）
 * - GACHA_TOTAL      累计祈妖抽卡 N 次（累加，封顶）
 * - DUNGEON_TOTAL    累计通关副本 N 次（累加，封顶）
 */
enum class ObjectiveType {
    CATCH_TOTAL,
    CATCH_ELEMENT,
    CATCH_RARITY,
    WIN_BATTLE,
    WIN_WITH_ELEMENT,
    ENTER_SECRET,
    CODEX_RATIO,
    TRAIN_LEVEL,
    BREAKTHROUGH_TOTAL,
    EVOLVE_TOTAL,
    EQUIP_TOTAL,
    FORGE_TOTAL,
    FUSE_TOTAL,
    TRIBULATE_TOTAL,
    TALENT_TOTAL,
    GACHA_TOTAL,
    DUNGEON_TOTAL,
}

/** 奖励（结构化，便于领奖时直接入账货币） */
data class QuestReward(
    val incense: Int = 0,
    val lingyu: Int = 0,
    val yaodan: Int = 0,
    val text: String,
)

/** 目标定义（不可变） */
data class ObjectiveDef(
    val type: ObjectiveType,
    val target: Int,
    val ref: String = "",
    val label: String,
)

@Serializable
enum class QuestState { LOCKED, ACTIVE, COMPLETED, CLAIMED }

/** 任务定义（不可变，来自 QuestLibrary） */
data class QuestDef(
    val id: String,
    val title: String,
    val desc: String,
    val category: QuestCategory,
    val objectives: List<ObjectiveDef>,
    val reward: QuestReward,
    val daily: Boolean = false,
)

/**
 * 任务运行时进度（可变的 current 与 state）。
 * current 与 def.objectives 一一对应。
 */
data class QuestProgress(
    val def: QuestDef,
    val current: MutableList<Int>,
    var state: QuestState,
) {
    init {
        require(current.size == def.objectives.size) { "进度长度需与目标一致：${def.id}" }
    }

    fun isComplete(): Boolean =
        def.objectives.indices.all { current[it] >= def.objectives[it].target }

    fun objectiveRatio(i: Int): Float {
        val t = def.objectives[i].target
        return if (t <= 0) 1f else current[i].coerceAtMost(t).toFloat() / t
    }

    fun objectiveText(i: Int): String {
        val o = def.objectives[i]
        return when (o.type) {
            ObjectiveType.CODEX_RATIO -> "图鉴完成度 ${current[i].coerceAtMost(o.target)}% / ${o.target}%"
            ObjectiveType.TRAIN_LEVEL -> "${o.label}  Lv.${current[i].coerceAtMost(o.target)}/${o.target}"
            else -> "${o.label}  ${current[i].coerceAtMost(o.target)}/${o.target}"
        }
    }
}

/** 驱动任务进度的游戏事件（由各玩法产生，喂给 QuestLog.record） */
sealed class GameEvent {
    data class MonsterCaught(val element: Element, val rarity: Rarity, val fromSecretRealm: Boolean) : GameEvent()
    data class BattleWon(val attackerElement: Element?) : GameEvent()
    object SecretRealmEntered : GameEvent()
    data class CodexUpdated(val ratioPercent: Int) : GameEvent()
    // ── 养成线事件 ──
    data class MonsterTrained(val newLevel: Int) : GameEvent()   // 任一妖升级到 newLevel
    object MonsterBrokenThrough : GameEvent()                    // 完成一次突破
    object MonsterEvolved : GameEvent()                          // 完成一次进化
    // ── 装备/炼妖线事件 ──
    object MonsterEquipped : GameEvent()                         // 完成一次装备穿戴
    object MonsterForged : GameEvent()                           // 完成一次装备祭炼
    object MonsterFused : GameEvent()                            // 完成一次炼妖融合
    object MonsterTribulated : GameEvent()
    data class MonsterTalentAssigned(val branch: TalentBranch) : GameEvent() // 完成一次天赋分配（攻/防/辅）
    object MonsterGachaPulled : GameEvent()                                 // 完成一次祈妖抽卡
    object DungeonEntered : GameEvent()                                     // 完成一次副本通关
}
