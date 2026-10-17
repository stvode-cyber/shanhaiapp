package com.shanhai.core.logic

import com.shanhai.core.model.*

/**
 * 战斗自检测引擎（GDD 十五·数值基础 + 七行平衡）。
 *
 * 纯函数：给定七行元素，构造等属性「基准妖」互为对手，按固定随机种子跑完整战斗，
 * 产出 **七行相克胜率矩阵** 与 **平衡报告**，用于上线前校验五行克制是否形成有效循环
 * （克制方稳定占优、被克方稳定劣势、无元素退化统治、所有对局必然终结）。
 *
 * 不依赖存档 / 网络 / 平台；与 [BattleSession]、[BattleEngine] 共用同一套伤害公式，
 * 因此本引擎的结果即代表真实战斗的平衡性。
 */
object BattleIntegrityEngine {
    /** 基准等级（与常规出战等级一致，确保属性成长参与计算）。 */
    const val REFERENCE_LEVEL = 50

    /** 为某元素构造等属性基准妖（单一本属攻击技，便于隔离五行效果）。 */
    fun referenceMonster(element: Element): Monster {
        val skill = Skill(
            id = "ref_${element.name.lowercase()}",
            name = "${element.label}击",
            type = SkillType.ELEMENT,
            element = element,
            power = 100,
            isDamage = true,
            critBonus = 0.0,
        )
        return Monster(
            id = "ref_${element.name}",
            name = "${element.label}基准妖",
            category = MonsterCategory.YAO,
            element = element,
            rarity = Rarity.LING,
            baseAtk = 100, baseDef = 100, baseHp = 100, baseSpd = 100,
            skills = listOf(skill),
        )
    }

    /** 构造单妖基准队伍：player 用元素 a，enemy 用元素 b（属性完全对称，仅五行不同）。 */
    fun buildMatchup(a: Element, b: Element): Pair<List<Combatant>, List<Combatant>> {
        val ma = referenceMonster(a)
        val mb = referenceMonster(b)
        val ca = ma.toCombatant(REFERENCE_LEVEL, ma.skills, 0, ma.statsAtLevel(REFERENCE_LEVEL))
        val cb = mb.toCombatant(REFERENCE_LEVEL, mb.skills, 0, mb.statsAtLevel(REFERENCE_LEVEL))
        return listOf(ca) to listOf(cb)
    }

    /** 跑一场确定性战斗（固定种子），返回终局。 */
    fun runMatchup(a: Element, b: Element, seed: Long = 1L): BattleOutcome {
        val (p, e) = buildMatchup(a, b)
        val session = BattleSession(p, e, random = kotlin.random.Random(seed))
        session.simulateToEnd()
        return session.outcome
    }

    /** 七行相克胜率矩阵：matrix[a][b] = A 作为玩家对 B 的胜率（1.0 胜 / 0.0 负；自对弈记 0.5）。 */
    fun elementMatchupMatrix(seed: Long = 1L): Map<Element, Map<Element, Double>> {
        val elems = Element.values()
        val outer = linkedMapOf<Element, Map<Element, Double>>()
        for (a in elems) {
            val inner = linkedMapOf<Element, Double>()
            for (b in elems) {
                val d = if (b == a) 0.5 else if (runMatchup(a, b, seed) == BattleOutcome.PLAYER_WIN) 1.0 else 0.0
                inner[b] = d
            }
            outer[a] = inner
        }
        return outer
    }

    /** 单元素平衡指标。 */
    data class ElementBalance(
        val element: Element,
        val avgWinRate: Double,       // 对所有其他元素的平均胜率
        val beatsWinRate: Double,     // 对「自己克制」对手的平均胜率
        val beatenWinRate: Double,    // 对「克制自己」对手的平均胜率
        val matchCount: Int,
    )

    /** 全局平衡报告（上线前自检用）。 */
    data class BalanceReport(
        val matrix: Map<Element, Map<Element, Double>>,
        val perElement: List<ElementBalance>,
        val allTerminated: Boolean,        // 所有对局均分出胜负（无 ONGOING）
        val noDegenerateDominance: Boolean, // 无元素全胜 / 全败（退化统治）
        val beatsAdvantage: Boolean,       // 克制方胜率 >= 0.8（克制有效）
        val beatenDisadvantage: Boolean,   // 被克方胜率 <= 0.2（被克有效）
    )

    fun balanceReport(seed: Long = 1L): BalanceReport {
        val m = elementMatchupMatrix(seed)
        val elems = Element.values()
        val per = elems.map { a ->
            val opp = elems.filter { it != a }
            val beats = opp.filter { Element.beats(a, it) }
            val beaten = opp.filter { Element.beatenBy(a, it) }
            val avg = opp.map { m[a]!![it]!! }.average()
            val beatsWR = if (beats.isEmpty()) 1.0 else beats.map { m[a]!![it]!! }.average()
            val beatenWR = if (beaten.isEmpty()) 0.0 else beaten.map { m[a]!![it]!! }.average()
            ElementBalance(a, avg, beatsWR, beatenWR, opp.size)
        }
        val allTerm = elems.all { a -> elems.all { b -> b == a || m[a]!![b]!! in setOf(0.0, 1.0) } }
        val noDom = per.all { it.avgWinRate < 1.0 && it.avgWinRate > 0.0 }
        val beatsAdv = per.all { it.beatsWinRate >= 0.8 }
        val beatenDis = per.all { it.beatenWinRate <= 0.2 }
        return BalanceReport(m, per, allTerm, noDom, beatsAdv, beatenDis)
    }
}
