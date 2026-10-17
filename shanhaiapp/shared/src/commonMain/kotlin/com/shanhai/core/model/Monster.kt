package com.shanhai.core.model

/** 战斗属性（已含等级与成长率的最终属性） */
data class Stats(
    val atk: Int,
    val def: Int,
    val hp: Int,
    val spd: Int,
)

/**
 * 妖怪定义。skills 为 12+ 技能池，实际出战从池中自由选 4 槽（equipped 由战斗时指定）。
 * 进化链通过 evolveFrom / evolveTo 表达（如 蛇妖→蛟→龙）。
 */
data class Monster(
    val id: String,
    val name: String,
    val category: MonsterCategory,
    val element: Element,
    val rarity: Rarity,
    val baseAtk: Int,
    val baseDef: Int,
    val baseHp: Int,
    val baseSpd: Int,
    val skills: List<Skill>,
    val evolveFrom: String? = null,
    val evolveTo: String? = null,
    val isFestival: Boolean = false, // 节日限定（如年兽），无进化
    val desc: String = "",
    val clan: String? = null,         // 族群（如 "fox" 狐族），用于阵容羁绊判定；定义数据不入库
) {
    /** 等级系数：1 + (level-1)*0.02 */
    private fun levelCoef(level: Int): Double = 1.0 + (level - 1) * 0.02

    /** 指定等级、含成长率的最终属性 */
    fun statsAtLevel(level: Int): Stats {
        val g = rarity.growth
        val lc = levelCoef(level)
        return Stats(
            atk = (baseAtk * g * lc).toInt().coerceAtLeast(1),
            def = (baseDef * g * lc).toInt().coerceAtLeast(1),
            hp = (baseHp * g * lc).toInt().coerceAtLeast(1),
            spd = (baseSpd * g).toInt().coerceAtLeast(1),
        )
    }
}
