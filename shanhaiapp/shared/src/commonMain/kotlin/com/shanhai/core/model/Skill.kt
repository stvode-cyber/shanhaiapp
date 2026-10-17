package com.shanhai.core.model

/** 技能门类（对应 GDD 七大类技能池） */
enum class SkillType(val label: String) {
    ATTACK("攻伐"), DEFENSE("守御"), SPELL("术法"), SUPPORT("辅助"), ELEMENT("五行专精"), PASSIVE("心法被动")
}

/**
 * 技能定义。power 为威力（非伤害技能为 0）；critBonus 为额外暴击率。
 */
data class Skill(
    val id: String,
    val name: String,
    val type: SkillType,
    val element: Element,
    val power: Int,
    val isDamage: Boolean,
    val critBonus: Double = 0.0,
    val description: String = "",
)
