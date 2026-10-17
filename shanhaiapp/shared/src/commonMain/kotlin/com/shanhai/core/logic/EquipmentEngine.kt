package com.shanhai.core.logic

import com.shanhai.core.model.Stats
import com.shanhai.core.data.EquipmentLibrary
import com.shanhai.core.model.*

/**
 * 装备/祭炼引擎（GDD 十六·养成线：装备祭炼 + 装备五路径）。
 *
 * 设计要点：
 * - [EquipDef] 只存静态定义（含 1 级基准与每级成长），本引擎集中全部数值规则，便于双端一致与单测。
 * - 单件装备有效加成 = 主属性(随等级/突破/星级/亲和) + 淬炼副属性，四来源叠加：
 *   主属性倍率 = (1 + (L-1) × 实际每级成长) × 星级倍率 × (元素一致 ? 契合倍率 : 1)
 *   - 突破阶 [tier] 提升等级上限([TIER_LEVEL_BONUS]/阶) 与每级成长([TIER_PERLEVEL_BONUS]/阶)
 *   - 星级 [stars] 每星 +[STAR_BONUS] 基础倍率
 * - 祭炼（升级）：消耗妖丹，等级 +1，至 [maxLevelOf] 上限止（上限随突破提升）。
 * - 升星/突破/淬炼消耗灵玉；继承消耗妖丹。多件装备加成按槽位叠加（同妖实例三槽各一件）。
 */
object EquipmentEngine {

    /** 元素契合时的额外加成倍数 */
    const val AFFINITY_MULT: Double = 1.2

    /** 每次祭炼消耗妖丹 */
    const val FORGE_COST_YAODAN: Int = 10

    // ── 五路径常量 ──
    const val MAX_STAR: Int = 5                 // 星级上限
    const val STAR_BONUS: Double = 0.10         // 每星 +10% 基础
    const val TIER_LEVEL_BONUS: Int = 10        // 每突破阶 +10 等级上限
    const val TIER_PERLEVEL_BONUS: Double = 0.03 // 每突破阶 +0.03 每级成长
    const val MAX_SUBSTATS: Int = 4             // 副属性最多 4 条
    const val SUB_CAP: Int = 15                 // 单条副属性上限
    const val TEMPER_STEP: Int = 3              // 每次淬炼 +值
    const val STAR_COST_BASE: Int = 50          // 升星基础消耗（×当前星）：1→2:50 ... 4→5:200
    const val TIER_COST_BASE: Int = 80          // 突破基础消耗（×当前阶）：1→2:80 ... 3→4:240
    const val TEMPER_COST_BASE: Int = 30        // 淬炼基础消耗（×已淬炼次数）
    const val INHERIT_COST_YAODAN: Int = 20     // 继承消耗妖丹

    /** 副属性开启/强化顺序（确定性，便于验证与双端一致） */
    private val SUB_ORDER = listOf(EquipStat.ATK, EquipStat.DEF, EquipStat.HP, EquipStat.SPD)

    // ── 突破阶上限（随稀有度）：凡1 / 灵2 / 仙3 / 神4 ──
    fun maxTier(rarity: Rarity): Int = rarity.ordinal + 1

    /** 装备实际等级上限（受突破提升） */
    fun maxLevelOf(def: EquipDef, oe: OwnedEquip): Int =
        def.maxLevel + (oe.tier - 1) * TIER_LEVEL_BONUS

    /** 装备实际每级成长率（受突破提升） */
    fun effectivePerLevel(def: EquipDef, oe: OwnedEquip): Double =
        def.bonusPerLevel + (oe.tier - 1) * TIER_PERLEVEL_BONUS

    /** 星级倍率 */
    fun starMult(oe: OwnedEquip): Double = 1.0 + (oe.stars - 1) * STAR_BONUS

    /** 单件装备主属性有效加成（含 level/tier/stars/affinity），不含副属性 */
    private fun mainBonus(oe: OwnedEquip, def: EquipDef, elementMatches: Boolean): Stats {
        val mult = (1.0 + (oe.level - 1) * effectivePerLevel(def, oe)) * starMult(oe) * (if (elementMatches) AFFINITY_MULT else 1.0)
        return Stats(
            (def.baseAtk * mult).toInt().coerceAtLeast(0),
            (def.baseDef * mult).toInt().coerceAtLeast(0),
            (def.baseHp * mult).toInt().coerceAtLeast(0),
            (def.baseSpd * mult).toInt().coerceAtLeast(0),
        )
    }

    /** 淬炼副属性加成合计 */
    fun subStatsBonus(oe: OwnedEquip): Stats {
        var atk = 0; var dfn = 0; var hp = 0; var spd = 0
        for (s in oe.substats) {
            when (s.stat) {
                EquipStat.ATK -> atk += s.value
                EquipStat.DEF -> dfn += s.value
                EquipStat.HP -> hp += s.value
                EquipStat.SPD -> spd += s.value
            }
        }
        return Stats(atk, dfn, hp, spd)
    }

    /** 单件装备完整有效加成（主属性 + 副属性） */
    fun bonus(oe: OwnedEquip, def: EquipDef, elementMatches: Boolean): Stats =
        addStats(mainBonus(oe, def, elementMatches), subStatsBonus(oe))

    /** 兼容旧调用（仅 level 的加成，不含五路径） */
    fun bonusAtLevel(def: EquipDef, level: Int, elementMatches: Boolean): Stats =
        bonus(OwnedEquip(def.id, "x", level), def, elementMatches)

    fun addStats(a: Stats, b: Stats): Stats =
        Stats(a.atk + b.atk, a.def + b.def, a.hp + b.hp, a.spd + b.spd)

    /** 某妖实例当前已装备的多件装备之加成合计（含副属性） */
    fun totalBonus(monster: Monster, equips: List<OwnedEquip>): Stats {
        var total = Stats(0, 0, 0, 0)
        for (o in equips) {
            val d = EquipmentLibrary.byId(o.equipId) ?: continue
            total = addStats(total, bonus(o, d, d.element == monster.element))
        }
        return total
    }

    // ── 升星 ──
    fun canStarUp(oe: OwnedEquip): Boolean = oe.stars < MAX_STAR
    fun starCost(oe: OwnedEquip): Int = STAR_COST_BASE * oe.stars
    fun starUp(oe: OwnedEquip): OwnedEquip? =
        if (!canStarUp(oe)) null else oe.copy(stars = oe.stars + 1)

    // ── 突破 ──
    fun canBreakthrough(oe: OwnedEquip, def: EquipDef): Boolean =
        oe.tier < maxTier(def.rarity)
    fun tierCost(oe: OwnedEquip): Int = TIER_COST_BASE * oe.tier
    fun breakthrough(oe: OwnedEquip, def: EquipDef): OwnedEquip? =
        if (!canBreakthrough(oe, def)) null else oe.copy(tier = oe.tier + 1)

    // ── 淬炼 ──
    fun canTemper(oe: OwnedEquip): Boolean {
        if (oe.substats.size >= MAX_SUBSTATS) {
            return oe.substats.any { it.value < SUB_CAP } // 满条数但仍有未满条目
        }
        return true
    }

    /** 已耗淬炼次数（各条 value 向上取整到 STEP 之和），用于成本递增 */
    fun temperUsed(oe: OwnedEquip): Int =
        oe.substats.sumOf { (it.value + TEMPER_STEP - 1) / TEMPER_STEP }
    fun temperCost(oe: OwnedEquip): Int = TEMPER_COST_BASE * (temperUsed(oe) + 1)

    fun temper(oe: OwnedEquip): OwnedEquip? {
        if (!canTemper(oe)) return null
        val existing = oe.substats.map { it.stat }.toSet()
        val nextNew = SUB_ORDER.firstOrNull { it !in existing }
        val newList = if (nextNew != null && oe.substats.size < MAX_SUBSTATS) {
            oe.substats + SubStat(nextNew, TEMPER_STEP)
        } else {
            val idx = oe.substats.indexOfFirst { it.value < SUB_CAP } // 强化现有第一条未满的
            if (idx < 0) return null
            oe.substats.mapIndexed { i, s ->
                if (i == idx) SubStat(s.stat, (s.value + TEMPER_STEP).coerceAtMost(SUB_CAP)) else s
            }
        }
        return oe.copy(substats = newList)
    }

    // ── 继承（合并较优，保留双方养成投入） ──
    fun canInherit(src: OwnedEquip, dst: OwnedEquip): Boolean = src.instanceId != dst.instanceId
    fun inherit(src: OwnedEquip, dst: OwnedEquip): OwnedEquip? {
        if (!canInherit(src, dst)) return null
        val map = dst.substats.associate { it.stat to it.value }.toMutableMap()
        for (s in src.substats) map[s.stat] = maxOf(map[s.stat] ?: 0, s.value)
        val merged = mutableListOf<SubStat>()
        for (st in SUB_ORDER) {                 // 按固定顺序输出，最多 MAX_SUBSTATS 条
            val v = map[st] ?: continue
            merged.add(SubStat(st, v))
            if (merged.size >= MAX_SUBSTATS) break
        }
        return dst.copy(
            level = maxOf(dst.level, src.level),
            stars = maxOf(dst.stars, src.stars),
            tier = maxOf(dst.tier, src.tier),
            substats = merged,
        )
    }

    /** 是否可继续祭炼（未达等级上限，上限随突破提升） */
    fun canForge(oe: OwnedEquip): Boolean {
        val def = EquipmentLibrary.byId(oe.equipId) ?: return false
        return oe.level < maxLevelOf(def, oe)
    }
}
