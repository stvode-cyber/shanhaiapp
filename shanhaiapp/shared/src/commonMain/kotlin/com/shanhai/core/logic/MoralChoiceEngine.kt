package com.shanhai.core.logic

import com.shanhai.core.model.MoralChoice

/**
 * 道德选择 → 有效属性倍率（GDD 十七·自由度）。纯函数，便于双端镜像与等价断言。
 *
 * - 镇压 SUPPRESS：强攻，攻击 ×1.10，防御/生命 ×1.00。
 * - 超度 TRANSCEND：净化，防御/生命 ×1.08，攻击 ×1.00。
 * - 未选择（null）：中性，全部 ×1.00。
 *
 * 倍率仅作用于「有效属性」派生阶段（见 [com.shanhai.core.logic.CultivationEngine.effectiveStats] 的接入点），
 * 不改变妖种基础属性，亦不影响收妖成功率——只影响「养成后的标记妖」的最终战力取向。
 */
data class StatMods(val atk: Double, val def: Double, val hp: Double)

object MoralChoiceEngine {
    fun modifiers(choice: MoralChoice?): StatMods = when (choice) {
        MoralChoice.SUPPRESS -> StatMods(1.10, 1.00, 1.00)
        MoralChoice.TRANSCEND -> StatMods(1.00, 1.08, 1.08)
        null -> StatMods(1.00, 1.00, 1.00)
    }
}
