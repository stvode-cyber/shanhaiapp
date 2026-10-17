package com.shanhai.core.model

/**
 * 妖怪四大类：精 / 怪 / 妖 / 灵兽。
 * 类别克制环（×1.3）：妖→精→怪→灵兽→妖。
 */
enum class MonsterCategory(val label: String) {
    JING("精"),     // 物精 / 草木精
    GUAI("怪"),     // 异兽凶兽
    YAO("妖"),      // 动物成妖
    LINGSHOU("灵兽"); // 祥瑞

    companion object {
        private val RING: Map<MonsterCategory, MonsterCategory> = mapOf(
            YAO to JING,
            JING to GUAI,
            GUAI to LINGSHOU,
            LINGSHOU to YAO,
        )

        /** 攻方对受方的类别倍率（克制 ×1.3，被克 ×1/1.3，其余 ×1.0） */
        fun multiplier(attacker: MonsterCategory, defender: MonsterCategory): Double =
            when {
                RING[attacker] == defender -> 1.3
                RING[defender] == attacker -> 1.0 / 1.3
                else -> 1.0
            }
    }
}
