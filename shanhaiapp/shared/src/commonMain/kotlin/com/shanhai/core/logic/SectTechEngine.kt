package com.shanhai.core.logic

/**
 * 宗门科技树（GDD 二十·待细化项：宗门科技树 = 公会建设 + 科技加成）。
 *
 * 自然扩展已实现的「三派（茅山 / 龙虎山 / 阁皂山）」社交系统：
 * - 玩家加入派系后，通过【据点战占领】积累「宗门贡献」；
 * - 用贡献研习科技节点，节点按类型提供全局加成：
 *     攻击 / 防御 / 体力 / 御妖（收妖率）/ 经修（经验）/ 资粮（收益）/ 据点（据点经营增益）；
 * - 三派各有一条「专属进阶」科技（需对应派系且前置基础科技）。
 *
 * 纯函数引擎：所有判定不读写 GameState，便于双端共用与 Python 等价验证。
 */
object SectTechEngine {

    /** 科技加成类型 */
    enum class BonusType { ATTACK, DEFENSE, HP, CAPTURE, EXP, INCOME, STRONGHOLD }

    /** 单个科技节点定义 */
    data class Node(
        val id: String,
        val name: String,
        val desc: String,
        val type: BonusType,
        val maxLevel: Int,
        val perLevel: Double,                 // 每级提供的加成比例（加性小数，如 +0.04 = +4%）
        val costBase: Int,                     // 1 级研究消耗贡献
        val costGrowth: Int,                   // 每提升一级额外消耗
        val prereq: List<String> = emptyList(),// 前置节点（须 ≥1 级）
        val faction: Faction? = null,          // 非空表示仅该派系可研习
    )

    /** 占领据点获得的宗门贡献（受「据点经营」科技加成后是再乘 (1+bonus)） */
    const val CONTRIBUTION_PER_CAPTURE = 50

    /** 全部科技节点（基础 7 + 三派专属 3） */
    val NODES: List<Node> = listOf(
        Node("tech_gongfa",     "攻伐之术", "提升全队攻击伤害",            BonusType.ATTACK,    5, 0.04, 20, 10),
        Node("tech_shouyu",     "守御心经", "提升妖怪防御",               BonusType.DEFENSE,   5, 0.04, 20, 10),
        Node("tech_tipu",       "体魄淬炼", "提升妖怪体力上限",           BonusType.HP,        5, 0.04, 20, 10),
        Node("tech_yuyao",      "御妖真诀", "提升收妖成功率",             BonusType.CAPTURE,   4, 0.05, 30, 12),
        Node("tech_jingxiu",    "经修玄法", "提升修炼获得的经验",         BonusType.EXP,       5, 0.05, 25, 12),
        Node("tech_ziliao",     "资粮广积", "提升副本/签到收益",          BonusType.INCOME,    5, 0.04, 25, 12),
        Node("tech_judi",       "据点经营", "占领据点获得额外宗门贡献",   BonusType.STRONGHOLD,3, 0.10, 40, 20, prereq = listOf("tech_gongfa")),
        // ── 三派专属进阶（需对应派系 + 前置基础科技） ──
        Node("tech_maoshan_fu", "茅山符阵", "茅山专属：额外攻击加成",     BonusType.ATTACK,    3, 0.06, 60, 30, prereq = listOf("tech_gongfa"),    faction = Faction.MAOSHAN),
        Node("tech_longhu_dan", "龙虎丹道", "龙虎山专属：额外体力加成",   BonusType.HP,        3, 0.06, 60, 30, prereq = listOf("tech_tipu"),     faction = Faction.LONGHUSHAN),
        Node("tech_gezao_qi",   "阁皂奇门", "阁皂山专属：额外收妖加成",   BonusType.CAPTURE,   3, 0.06, 60, 30, prereq = listOf("tech_yuyao"),    faction = Faction.GEZAO),
    )

    private val byId = NODES.associateBy { it.id }

    fun node(id: String): Node? = byId[id]

    /** 某节点 id 列表（供 UI 遍历） */
    fun nodeIds(): List<String> = NODES.map { it.id }

    /** 研究下一级所需贡献（当前 level → level+1）；已满级返回 Int.MAX_VALUE */
    fun researchCost(level: Int, node: Node): Int =
        if (level >= node.maxLevel) Int.MAX_VALUE else node.costBase + node.costGrowth * level

    /** 前置是否全部满足（每个前置 ≥1 级） */
    fun prereqMet(node: Node, levels: Map<String, Int>): Boolean =
        node.prereq.all { (levels[it] ?: 0) >= 1 }

    /** 是否可研习（存在 / 未满级 / 前置满足 / 派系匹配 / 贡献足够） */
    fun canResearch(id: String, levels: Map<String, Int>, faction: Faction?, contribution: Int): Boolean {
        val node = byId[id] ?: return false
        val lv = levels[id] ?: 0
        if (lv >= node.maxLevel) return false
        if (!prereqMet(node, levels)) return false
        if (node.faction != null && node.faction != faction) return false
        return contribution >= researchCost(lv, node)
    }

    /**
     * 执行研习（纯函数）：成功返回 (新等级表, 剩余贡献)，否则 null。
     * 调用方负责把结果写回状态。
     */
    fun research(id: String, levels: Map<String, Int>, faction: Faction?, contribution: Int): Pair<Map<String, Int>, Int>? {
        if (!canResearch(id, levels, faction, contribution)) return null
        val node = byId[id]!!
        val lv = levels[id] ?: 0
        val cost = researchCost(lv, node)
        val next = levels.toMutableMap()
        next[id] = lv + 1
        return next to (contribution - cost)
    }

    /** 汇总全部已研习节点的加成（加性小数）。派系专属节点仅当 [faction] 匹配时计入。 */
    fun bonuses(levels: Map<String, Int>, faction: Faction?): SectBonuses {
        var attack = 0.0; var defense = 0.0; var hp = 0.0
        var capture = 0.0; var exp = 0.0; var income = 0.0; var stronghold = 0.0
        for (node in NODES) {
            if (node.faction != null && node.faction != faction) continue
            val lv = levels[node.id] ?: 0
            if (lv <= 0) continue
            val v = node.perLevel * lv
            when (node.type) {
                BonusType.ATTACK -> attack += v
                BonusType.DEFENSE -> defense += v
                BonusType.HP -> hp += v
                BonusType.CAPTURE -> capture += v
                BonusType.EXP -> exp += v
                BonusType.INCOME -> income += v
                BonusType.STRONGHOLD -> stronghold += v
            }
        }
        return SectBonuses(attack, defense, hp, capture, exp, income, stronghold)
    }
}

/** 宗门科技汇总加成（加性小数，如 attack=0.20 表示全队攻击 +20%）。 */
data class SectBonuses(
    val attack: Double = 0.0,
    val defense: Double = 0.0,
    val hp: Double = 0.0,
    val capture: Double = 0.0,
    val exp: Double = 0.0,
    val income: Double = 0.0,
    val stronghold: Double = 0.0,
)
