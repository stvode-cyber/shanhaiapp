package com.shanhai.core.logic

import com.shanhai.core.model.*

/**
 * 阵容羁绊 + 流派模板引擎（GDD 七·技能系统 + GDD 七·队伍组合技）。
 *
 * 两层职责：
 *  1. evaluate(team) — 现有逻辑：判羁绊、算全队增伤（不动，纯追加）
 *  2. FormationTemplate — 三预设流派一键套用（攻/防/辅）
 *
 * 纯函数、无随机、无平台依赖。
 */

// ── 流派模板（GDD 七·技能系统：攻/防/辅三预设一键套用） ──

/** 流派类型。 */
enum class FormationTemplate(val label: String, val desc: String) {
    OFFENSIVE("攻", "全输出技能 + 暴击阵（ATTACK + ELEMENT + PASSIVE 输出心法）"),
    DEFENSIVE("防", "全护盾/恢复 + 反制阵（DEFENSE + SUPPORT + PASSIVE 防御心法）"),
    SUPPORT("辅", "全增益/减益 + 速绝阵（SPELL + SUPPORT + PASSIVE 控制心法）"),
}

/**
 * 流派模板：给定一只妖的完整技能池（Monster.skills，≥4），
 * 按模板优先级选 4 个出战技能槽。
 *
 * 策略：每只妖独立筛选，按 [primary → secondary → tertiary] 顺序挑够 4 个槽，
 * 不足时回退到同妖其他技能（不丢技能、不跨妖借技）。
 */
/**
 * 流派模板引擎（纯函数，不进存档）。
 * 给定 Monster 列表 + 流派模板 → 返回每只妖推荐的 4 个出战技能。
 */
object FormationTemplates {

    /** 三流派的技能类型优先级（主 → 次 → 辅）。 */
    private val PRIORITY: Map<FormationTemplate, List<SkillType>> = mapOf(
        FormationTemplate.OFFENSIVE to listOf(SkillType.ATTACK, SkillType.ELEMENT, SkillType.PASSIVE),
        FormationTemplate.DEFENSIVE to listOf(SkillType.DEFENSE, SkillType.SUPPORT, SkillType.PASSIVE),
        FormationTemplate.SUPPORT to listOf(SkillType.SPELL, SkillType.SUPPORT, SkillType.PASSIVE),
    )

    /** 判断技能是否属于目标流派的"主输出/主防御/主辅助"类型。 */
    private fun Skill.isPrimary(template: FormationTemplate): Boolean =
        PRIORITY[template]!!.contains(this.type)

    /** 从一只妖的技能池里按模板选 4 个出战技能。 */
    fun pickFourSkills(monster: Monster, template: FormationTemplate): List<Skill> {
        val prio = PRIORITY[template]!!
        val picked = mutableListOf<Skill>()
        val used = mutableSetOf<Int>()

        // 第一轮：严格按类型优先级挑
        for (type in prio) {
            if (picked.size >= 4) break
            monster.skills.forEachIndexed { idx, sk ->
                if (picked.size < 4 && sk.type == type && idx !in used) {
                    picked += sk; used += idx
                }
            }
        }
        // 第二轮兜底：优先挑同妖中"次优"技能（对攻流派→伤害技能；对防流派→非伤害高血技能）
        // 但不强制，按技能池原始顺序补齐
        if (picked.size < 4) {
            monster.skills.forEachIndexed { idx, sk ->
                if (picked.size < 4 && idx !in used) {
                    picked += sk; used += idx
                }
            }
        }
        return picked.take(4)
    }

    /**
     * 一键套用：给定出战队伍（Monster 定义列表）+ 模板，
     * 返回每只妖的出战技能映射（monsterId → 4 个 Skill）。
     * 纯函数，不修改 Monster 本体，不进存档。
     */
    fun apply(team: List<Monster>, template: FormationTemplate): Map<String, List<Skill>> =
        team.associate { it.id to pickFourSkills(it, template) }
}

// ── 羁绊逻辑（原有 evaluate + teamAttackMultiplier，不动） ──

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
