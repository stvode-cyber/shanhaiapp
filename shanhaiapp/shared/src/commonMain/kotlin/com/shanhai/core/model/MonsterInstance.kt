package com.shanhai.core.model

import kotlinx.serialization.Serializable

/**
 * 玩家「拥有的一只妖」的运行时实例（区别于静态的 [Monster] 定义）。
 * 同一妖种可拥有多只，各自独立养成。仅存可变养成数据，妖种定义仍取自 [MonsterPool]。
 *
 * - level：当前等级（受等级上限约束）
 * - exp：当前等级内已累积的经验（升到 level+1 需 [com.shanhai.core.logic.CultivationEngine.expForLevel]）
 * - breakthrough：突破次数（每次突破提升等级上限与属性，见 CultivationEngine）
 * - tribulations：渡劫次数（养成线终章飞升，稀有度升阶 + 永久属性倍率，见 CultivationEngine）
 * - talents：天赋树分配（分支→已点层数，见 [com.shanhai.core.logic.TalentEngine]），默认空向后兼容
 *
 * 该数据类保持「纯数据 + 可序列化」，不反向依赖 logic 包，避免循环依赖；
 * 等级上限 / 属性加成 / 渡劫 / 天赋等计算统一放在 logic 包各引擎。
 */
@Serializable
data class MonsterInstance(
    val instanceId: String,
    val defId: String,
    val level: Int,
    val exp: Int,
    val breakthrough: Int,
    val tribulations: Int = 0,
    val talents: Map<TalentBranch, Int> = emptyMap(),
    val moralChoice: MoralChoice? = null,   // 道德选择标记（镇压/超度，GDD 十七）；旧存档缺省 null，向后兼容
)
