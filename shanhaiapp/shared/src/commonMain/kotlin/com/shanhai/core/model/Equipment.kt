package com.shanhai.core.model

import kotlinx.serialization.Serializable

/**
 * 装备槽位（每件妖实例每个槽位最多装配一件）。
 */
@Serializable
enum class EquipSlot {
    WEAPON,     // 武器
    ARMOR,      // 护甲
    ACCESSORY,  // 饰品
}

/**
 * 装备定义（静态，来自 [com.shanhai.core.data.EquipmentLibrary]）。
 * - base* 为 1 级基准加成；实际加成随祭炼等级按 [bonusPerLevel] 线性增长；
 * - element 为元素亲和：若与妖的元素一致，加成额外 ×[com.shanhai.core.logic.EquipmentEngine.AFFINITY_MULT]；
 *   null 表示无亲和，全妖通用。
 */
@Serializable
data class EquipDef(
    val id: String,
    val name: String,
    val slot: EquipSlot,
    val element: Element?,   // null = 无元素亲和（全妖通用）
    val rarity: Rarity,
    val baseAtk: Int,
    val baseDef: Int,
    val baseHp: Int,
    val baseSpd: Int,
    val bonusPerLevel: Double = 0.12,
    val maxLevel: Int = 10,
)

/**
 * 装备副属性维度（淬炼产出）。与主属性（[EquipDef] 的 base*）相加计入有效属性。
 */
@Serializable
enum class EquipStat {
    ATK, DEF, HP, SPD,
}

/**
 * 单条淬炼副属性（确定性产出，便于验证与双端一致）。
 */
@Serializable
data class SubStat(
    val stat: EquipStat,
    val value: Int,
)

/**
 * 玩家拥有的「一件装备」的运行时实例（区别于静态 [EquipDef]）。
 * 仅存可变养成数据（祭炼等级 / 升星 / 突破 / 淬炼副属性），定义仍取自 [EquipmentLibrary]。
 *
 * 五路径（GDD 十六·养成线·装备深化）：
 * - [level]：祭炼等级（1..maxLevel，突破后上限提升）
 * - [stars]：星级（1..[com.shanhai.core.logic.EquipmentEngine.MAX_STAR]），每星 +[STAR_BONUS] 基础倍率
 * - [tier]：突破阶（1..maxTier(rarity)），每阶提升等级上限与每级成长
 * - [substats]：淬炼副属性（最多 [MAX_SUBSTATS] 条，每条有上限）
 *
 * 全部新字段带默认值，旧存档（仅 level）可无缝兼容（kotlinx-serialization 默认值 + ignoreUnknownKeys）。
 */
@Serializable
data class OwnedEquip(
    val equipId: String,     // 对应 EquipDef.id
    val instanceId: String,  // 唯一拥有实例 id
    val level: Int = 1,      // 祭炼等级（1..maxLevel）
    val stars: Int = 1,      // 星级（1..MAX_STAR）
    val tier: Int = 1,       // 突破阶（1..maxTier(rarity)）
    val substats: List<SubStat> = emptyList(),  // 淬炼副属性
)
