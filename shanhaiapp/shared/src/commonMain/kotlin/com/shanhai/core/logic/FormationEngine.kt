package com.shanhai.core.logic

import com.shanhai.core.model.*

/**
 * 阵容羁绊（GDD 七·技能系统：队伍组合技 / 阵容）。
 *
 * 给定出战妖列表，判定哪些「组合技」条件满足，并给出全队攻击增伤倍率（用于 [BattleEngine.damage]）。
 * 纯函数、无随机、无平台依赖，可在本机做等价验证（见 verify/ 脚本）。
 *
 * GDD 五条组合技：
 *  - 阴阳调和：水≥2 且 火≥2
 *  - 百妖夜行：同类别≥3
 *  - 三才阵：类别覆盖≥3 种
 *      ⚠️ 近似说明：GDD 原文为「三派混编」，但玩家仅持单派系（茅山/龙虎山/阁皂山），
 *      单队无法表达三派；此处以「三类别齐聚」近似「三才阵」，团战等后端多派场景可另行扩展。
 *  - 相生阵：五行齐全（金木水火土 五元素都在）
 *  - 青丘幻月：狐族（clan == "fox"）≥3
 */
enum class SynergyId { YINYANG, BAIYAO, SANCAI, XIANGSHENG, QINGQIU }

/** 一个已激活的阵容羁绊。 */
data class Synergy(
    val id: SynergyId,
    val name: String,
    /** 该羁绊提供的全队攻击增伤（叠加，封顶见 [FormationEngine.teamAttackMultiplier]）。 */
    val atkMul: Double,
    /** 触发的大招次数（1~2）。 */
    val ultimateCharges: Int,
    val desc: String,
)

object FormationEngine {
    /** 每个激活羁绊提供的攻击增伤。 */
    private const val PER_SYNERGY_MUL = 0.12

    /** 全队攻击增伤上限（+60% = ×1.6）。 */
    const val MAX_MUL = 0.60

    /** 判定的五行集合（金木水火土）。 */
    private val FIVE_ELEMENTS = setOf(Element.METAL, Element.WOOD, Element.WATER, Element.FIRE, Element.EARTH)

    /**
     * 评估一支队伍激活的羁绊（可能 0~5 条同时激活）。
     * [team] 为出战妖的 [Monster] 定义列表（不含重复实例的区隔，按定义判定）。
     */
    fun evaluate(team: List<Monster>): List<Synergy> {
        if (team.isEmpty()) return emptyList()
        val syn = mutableListOf<Synergy>()

        val elements = team.map { it.element }
        val water = elements.count { it == Element.WATER }
        val fire = elements.count { it == Element.FIRE }
        // 阴阳调和：水≥2 且 火≥2
        if (water >= 2 && fire >= 2)
            syn += Synergy(SynergyId.YINYANG, "阴阳调和", PER_SYNERGY_MUL, 1,
                "水火既济，攻守相济（水≥2 且 火≥2）")

        // 百妖夜行：同类别≥3
        val catCount = team.groupingBy { it.category }.eachCount()
        val topCat = catCount.maxByOrNull { it.value }
        if (topCat != null && topCat.value >= 3)
            syn += Synergy(SynergyId.BAIYAO, "百妖夜行", PER_SYNERGY_MUL, 1,
                "同类成群，势如破竹（${topCat.key.label}≥3）")

        // 三才阵：类别覆盖≥3 种（近似「三派混编」）
        if (catCount.size >= 3)
            syn += Synergy(SynergyId.SANCAI, "三才阵", PER_SYNERGY_MUL, 1,
                "三才并济，阵法自成（≥3 类别）")

        // 相生阵：五行齐全
        if (elements.toSet().containsAll(FIVE_ELEMENTS))
            syn += Synergy(SynergyId.XIANGSHENG, "相生阵", PER_SYNERGY_MUL, 2,
                "五行相生，生生不息（金木水火土齐全）")

        // 青丘幻月：狐族≥3
        val fox = team.count { it.clan == "fox" }
        if (fox >= 3)
            syn += Synergy(SynergyId.QINGQIU, "青丘幻月", PER_SYNERGY_MUL, 2,
                "青丘幻月，魅惑九霄（狐族≥3）")

        return syn
    }

    /**
     * 全队攻击增伤倍率（叠加上限 [MAX_MUL]）。
     * 例：激活 2 条 → ×1.24；激活 5 条（叠满 0.60）→ ×1.6。
     */
    fun teamAttackMultiplier(synergies: List<Synergy>): Double {
        val total = synergies.sumOf { it.atkMul }.coerceAtMost(MAX_MUL)
        return 1.0 + total
    }
}
