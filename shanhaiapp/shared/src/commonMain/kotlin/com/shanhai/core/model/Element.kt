package com.shanhai.core.model

/**
 * 七行元素（金木水火土风雷）。
 * 相克矩阵来自 GDD 附录：攻方克受方 → ×1.6；被克 → ×0.6；其余 ×1.0。
 */
enum class Element(val label: String) {
    METAL("金"), WOOD("木"), EARTH("土"), WATER("水"), FIRE("火"), WIND("风"), THUNDER("雷");

    companion object {
        /**
         * 攻方克制受方的集合（行=攻方，列=受方）。
         * GDD 附录矩阵（攻方克受方 ×1.6）：
         *   金→木风  木→土雷  土→水风  水→火雷  火→金风  风→土雷  雷→木水
         */
        private val BEATS: Map<Element, Set<Element>> = mapOf(
            METAL to setOf(WOOD, WIND),       // 金克木、风
            WOOD to setOf(EARTH, THUNDER),   // 木克土、雷
            EARTH to setOf(WATER, WIND),     // 土克水、风
            WATER to setOf(FIRE, THUNDER),   // 水克火、雷
            FIRE to setOf(METAL, WIND),      // 火克金、风
            WIND to setOf(EARTH, THUNDER),   // 风克土、雷
            THUNDER to setOf(WOOD, WATER),   // 雷克木、水
        )

        /** 攻方是否克制受方 */
        fun beats(attacker: Element, defender: Element): Boolean =
            BEATS[attacker]?.contains(defender) == true

        /** 攻方是否被受方克制 */
        fun beatenBy(attacker: Element, defender: Element): Boolean =
            BEATS[defender]?.contains(attacker) == true

        /** 攻方对受方的五行倍率 */
        fun multiplier(attacker: Element, defender: Element): Double =
            when {
                beats(attacker, defender) -> 1.6
                beatenBy(attacker, defender) -> 0.6
                else -> 1.0
            }

        /** 倍率文字（如 "水克火 ×1.6"） */
        fun multiplierLabel(attacker: Element, defender: Element): String {
            val m = multiplier(attacker, defender)
            return when {
                m > 1.0 -> "${attacker.label}克${defender.label} ×$m"
                m < 1.0 -> "${defender.label}克${attacker.label} ×$m"
                else -> "无克制 ×1.0"
            }
        }
    }
}
