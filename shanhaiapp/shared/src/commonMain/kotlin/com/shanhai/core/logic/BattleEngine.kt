package com.shanhai.core.logic

import com.shanhai.core.model.*
import kotlin.random.Random

/**
 * 出战单位（含可变血量，供战斗运行时使用）。
 * equippedSkills 为从妖技能池中自由选的 4 槽；
 * [effective] 为已计入「突破加成 + 装备加成」的最终有效属性（由 [com.shanhai.core.logic.GameState.buildCombatant] 计算），
 * 战斗伤害直接取用，避免运行时重复叠加。
 */
data class Combatant(
    val monster: Monster,
    val level: Int,
    val equippedSkills: List<Skill>,
    val breakthrough: Int = 0,
    val effective: Stats,
    var hp: Int = -1,
) {
    val maxHp: Int get() = effective.hp
    fun ensureHp() { if (hp < 0) hp = maxHp }
    val isAlive: Boolean get() = hp > 0
}

data class DamageResult(
    val damage: Int,
    val elementMult: Double,
    val categoryMult: Double,
    val envMul: Double = 1.0,
    val crit: Boolean,
    val skill: Skill,
    val label: String,
)

/**
 * 对战引擎：实现 GDD 伤害公式（十五·数值基础）。
 * floor((ATK/DEF) × 威力 × 等级系数 × 五行 × 类别 × 暴击 × 随机)
 *  - 等级系数 = 1 + (level-1)*0.02
 *  - 五行倍率：克制 ×1.6 / 被克 ×0.6 / 其余 ×1.0
 *  - 类别倍率（环）：克制 ×1.3 / 被克 ×1/1.3
 *  - 暴击 ×1.5（基础 5% + 装备 + 技能）
 *  - 随机 0.9~1.0
 */
object BattleEngine {
    fun levelCoef(level: Int): Double = 1.0 + (level - 1) * 0.02

    /** 自动选最优克制技（五行×类别×威力加权），用于「高亮克制技」 */
    fun bestCounterSkill(attacker: Combatant, defender: Combatant): Skill? {
        if (attacker.equippedSkills.isEmpty()) return null
        return attacker.equippedSkills.maxByOrNull { sk ->
            Element.multiplier(sk.element, defender.monster.element) *
                MonsterCategory.multiplier(attacker.monster.category, defender.monster.category) *
                (if (sk.isDamage) sk.power else 1)
        }
    }

    fun damage(
        attacker: Combatant,
        defender: Combatant,
        skill: Skill,
        random: Random = Random.Default,
        /** 出战方阵容羁绊带来的全队攻击增伤倍率（默认 1.0，见 [FormationEngine]）。 */
        teamAttackMul: Double = 1.0,
        /** 天气/地形环境偏置乘子（默认 1.0，见 [WeatherEngine]，由 [BattleSession] 按攻击者元素实时计算）。 */
        envMul: Double = 1.0,
        /** 宗门科技树带来的全队攻击增伤倍率（默认 1.0，见 [SectTechEngine]）。 */
        sectAttackMul: Double = 1.0,
    ): DamageResult {
        // 直接采用已计入突破+装备的有效属性，避免重复叠加
        val atk = attacker.effective.atk.toDouble()
        val def = defender.effective.def.coerceAtLeast(1).toDouble()
        val elem = Element.multiplier(skill.element, defender.monster.element)
        val cat = MonsterCategory.multiplier(attacker.monster.category, defender.monster.category)
        val critChance = (0.05 + skill.critBonus).coerceIn(0.0, 1.0)
        val rnd = random.nextDouble(0.9, 1.0)
        val crit = random.nextDouble(0.0, 1.0) < critChance
        val critMult = if (crit) 1.5 else 1.0
        val raw = (atk / def) * skill.power * levelCoef(attacker.level) * elem * cat * critMult * rnd * teamAttackMul * envMul * sectAttackMul
        val dmg = kotlin.math.floor(raw).toInt().coerceAtLeast(1)
        return DamageResult(dmg, elem, cat, envMul, crit, skill, Element.multiplierLabel(skill.element, defender.monster.element))
    }
}
