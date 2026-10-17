package com.shanhai.core.data

import com.shanhai.core.model.Element
import com.shanhai.core.model.Rarity
import com.shanhai.core.logic.ComboEngine
import com.shanhai.core.logic.FusionEngine
import com.shanhai.core.quest.ObjectiveType
import com.shanhai.core.quest.QuestLibrary

/**
 * 静态游戏定义完整性校验（GDD·稳健性收口，定义层）。
 *
 * 与 [com.shanhai.core.persistence.SaveIntegrityEngine]（玩家存档层）互补：本引擎校验
 * 「游戏定义自身」的相互引用一致性——妖池↔技能池、进化链、连携技能、任务目标 ref。
 * 任意一项不一致都会导致：
 *  - 启动即崩溃（Kotlin 端 [SkillLibrary.get] 对未知 id 抛 error）；
 *  - 或静默失效（Swift 端 [SkillLibrary] 用 compactMap 丢弃未知 id → 妖少技能；
 *    进化链断裂 / 连携永不触发 / 任务永不完成）。
 *
 * 双端同跑 [validate]，把「数据 bug 在两端的失败模式分歧」变成确定性失败。
 * 设计为启动期（debug）一次性断言；纯函数、无副作用、不依赖平台。
 */
object DataIntegrityEngine {

    /** 单条违规（稳定 code 便于程序判定，message 供展示）。 */
    data class Violation(val code: String, val message: String)

    private val skillIds: Set<String> = SkillLibrary.allSkills.map { it.id }.toSet()
    private val monsterIds: Set<String> = MonsterPool.all.map { it.id }.toSet()
    private val elementNames: Set<String> = Element.values().map { it.name }.toSet()
    private val rarityNames: Set<String> = Rarity.values().map { it.name }.toSet()

    // 任务 ref 的语义域：哪些 ObjectiveType 引用 Element / Rarity
    private val elementRefTypes = setOf(ObjectiveType.CATCH_ELEMENT, ObjectiveType.WIN_WITH_ELEMENT)
    private val rarityRefTypes = setOf(ObjectiveType.CATCH_RARITY)

    /**
     * 校验全部静态定义，返回所有违规（可能为空）。不修改任何定义。
     */
    fun validate(): List<Violation> {
        val v = mutableListOf<Violation>()
        val add = { code: String, msg: String -> v.add(Violation(code, msg)) }

        // 1) 妖池技能 ⊆ 技能池
        for (m in MonsterPool.all) {
            for (s in m.skills) {
                if (s.id !in skillIds) add("MON_SKILL", "妖 ${m.id} 技能 ${s.id} 不存在于技能池")
            }
        }

        // 2) 进化链 ⊆ 妖池
        for (m in MonsterPool.all) {
            m.evolveTo?.let { if (it !in monsterIds) add("EVO_TO", "妖 ${m.id} evolveTo 不存在: $it") }
            m.evolveFrom?.let { if (it !in monsterIds) add("EVO_FROM", "妖 ${m.id} evolveFrom 不存在: $it") }
        }

        // 3) 连携技能 ⊆ 技能池
        for (c in ComboEngine.COMBOS) {
            for (sid in c.required) {
                if (sid !in skillIds) add("COMBO_SKILL", "连携 ${c.id} 技能 $sid 不存在于技能池")
            }
        }

        // 4) 任务 ref ∈ 合法枚举名
        for (q in QuestLibrary.all) {
            for (o in q.objectives) {
                if (o.ref.isEmpty()) continue
                if (o.type in elementRefTypes && o.ref !in elementNames)
                    add("QUEST_REF_ELEM", "任务 ${q.id} 目标 ${o.type} ref 非法元素: ${o.ref}")
                if (o.type in rarityRefTypes && o.ref !in rarityNames)
                    add("QUEST_REF_RAR", "任务 ${q.id} 目标 ${o.type} ref 非法稀有度: ${o.ref}")
            }
        }

        // 5) 炼妖融合配方产物 ⊆ 妖池（防止「玄武」类配方指向不存在的妖 → 融合静默失败）
        for (out in FusionEngine.recipeOutputs) {
            if (out !in monsterIds) add("RECIPE_OUT", "融合配方产物 $out 不存在于妖池")
        }

        return v
    }

    /** 是否无违规。 */
    fun isClean(): Boolean = validate().isEmpty()

    /**
     * 启动期断言：debug 下数据不一致直接失败，避免带病上线。
     * 平台层在 Application/AppDelegate 启动处调用（shared 不依赖平台启动生命周期）。
     */
    fun assertClean() {
        val vs = validate()
        if (vs.isNotEmpty()) {
            error("静态数据完整性校验失败: ${vs.joinToString { "${it.code}:${it.message}" }}")
        }
    }
}
