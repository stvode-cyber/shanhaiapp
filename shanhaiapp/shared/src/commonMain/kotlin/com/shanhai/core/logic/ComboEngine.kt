package com.shanhai.core.logic

/**
 * 连携组合技（GDD 七·技能系统「连携组合技（同妖）」）。
 *
 * 同妖装备特定技能对触发额外效果：占槽（取前 4 个出战技能）+ 有 CD（由 [BattleSession] 的 comboCd 控制，每场限次）。
 * 判定纯函数式，不依赖平台，可用 [ComboEngine.availableCombos] 在 Python 等价验证。
 *
 * 连携定义选取池中真实共现的技能对（示例取自 GDD：捆妖索+甘霖→缚妖甘露；裂空斩+万剑→狂战连斩 等），
 * 以保证可在不改动持久化的前提下直接验证「出战 4 技能包含两组件 → 触发对应连携」。
 */
enum class ComboKind { DAMAGE_MUL, HEAL_PERCENT, CONTROL }

data class ComboEffect(val kind: ComboKind, val value: Double)

data class Combo(
    val id: String,
    val name: String,
    val required: List<String>, // 2 个技能 id（需同时出现在出战 4 技能中）
    val effect: ComboEffect,
    val cd: Int,                // 连携冷却（玩家回合数）
)

object ComboEngine {
    /** 连携定义表（技能对真实共现于妖池，可在 MonsterPool 中核对）。 */
    val COMBOS: List<Combo> = listOf(
        Combo("fuyaoganlu", "缚妖甘露", listOf("s_kunyao", "s_ganlin"),
            ComboEffect(ComboKind.HEAL_PERCENT, 0.25), cd = 3),
        Combo("kuangzhanlianzhan", "狂战连斩", listOf("s_liekong", "s_wanjian"),
            ComboEffect(ComboKind.DAMAGE_MUL, 1.3), cd = 2),
        Combo("xuanbinghanbi", "玄冰寒壁", listOf("s_hantan", "s_xuanbing"),
            ComboEffect(ComboKind.DAMAGE_MUL, 1.2), cd = 3),
        Combo("lianyanfentian", "烈焰焚天", listOf("s_fentian", "s_sanmei"),
            ComboEffect(ComboKind.DAMAGE_MUL, 1.35), cd = 2),
    )

    /** 给定出战技能 id 列表，返回其中已满足（两组件都在）的连携。 */
    fun availableCombos(equippedSkillIds: List<String>): List<Combo> {
        val set = equippedSkillIds.toSet()
        return COMBOS.filter { combo -> combo.required.all { set.contains(it) } }
    }

    /** 已激活连携带来的伤害放大倍率（DAMAGE_MUL 类相乘，封顶 1.6）。 */
    fun damageMultiplier(combos: List<Combo>): Double {
        val mul = combos.filter { it.effect.kind == ComboKind.DAMAGE_MUL }
            .fold(1.0) { acc, c -> acc * c.effect.value }
        return minOf(mul, 1.6)
    }

    /** 已激活连携中的最大回血比例（HEAL_PERCENT）。 */
    fun healPercent(combos: List<Combo>): Double =
        combos.filter { it.effect.kind == ComboKind.HEAL_PERCENT }
            .maxOfOrNull { it.effect.value } ?: 0.0
}
