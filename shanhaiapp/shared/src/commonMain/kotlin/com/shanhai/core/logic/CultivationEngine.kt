package com.shanhai.core.logic

import com.shanhai.core.data.MonsterPool
import com.shanhai.core.model.MonsterInstance
import com.shanhai.core.model.Rarity
import com.shanhai.core.model.Stats

/**
 * 妖怪养成引擎（GDD 十六·养成线：等级 / 突破 / 进化）。
 *
 * 设计要点：
 * - [MonsterInstance] 只存可变养成数据，本引擎集中所有数值规则，便于双端一致与单测。
 * - 等级上限 = [BASE_LEVEL_CAP] + 突破次数 × [LEVEL_CAP_PER_BREAK]。
 * - 突破：消耗妖丹，提升等级上限并永久强化属性（[STAT_MULT_PER_BREAK] 每级 +8%）。
 * - 进化：达到 [EVOLVE_LEVEL_REQ] 级且妖种存在 evolveTo 时，进化为下一形态，保留等级/经验/突破。
 * - 渡劫（养成线终章）：满级满突破后消耗妖丹飞升，稀有度升一阶 + 永久属性倍率（[TRIBULATE_STAT_BONUS]），保证成功（必成）。
 * - 属性 = 妖种 statsAtLevel(level) × 突破属性系数（[statMult]） × 渡劫属性系数（[tribulateMult]）。
 *
 * 所有方法对「等级已满」「不可突破/进化/渡劫」等情况返回安全值或 null，由调用方决定反馈。
 */
object CultivationEngine {

    /** 基础等级上限（未突破时） */
    const val BASE_LEVEL_CAP: Int = 30

    /** 最大突破次数 */
    const val MAX_BREAKTHROUGH: Int = 5

    /** 每次突破提升的等级上限 */
    const val LEVEL_CAP_PER_BREAK: Int = 5

    /** 每次突破提升的属性百分比（线性叠加，最高 +40%） */
    const val STAT_MULT_PER_BREAK: Double = 0.08

    /** 进化所需最低等级 */
    const val EVOLVE_LEVEL_REQ: Int = 25

    /** 渡劫：每次飞升的永久属性加成百分比（线性叠加） */
    const val TRIBULATE_STAT_BONUS: Double = 0.25

    /** 渡劫基础妖丹消耗（实际 = 基础 ×(渡劫次数+1)，逐级递增） */
    const val TRIBULATE_COST_YAODAN: Int = 200

    /** 当前实例的等级上限 */
    fun levelCap(inst: MonsterInstance): Int =
        BASE_LEVEL_CAP + inst.breakthrough * LEVEL_CAP_PER_BREAK

    /** 当前实例的属性系数（含突破加成） */
    fun statMult(inst: MonsterInstance): Double =
        1.0 + inst.breakthrough * STAT_MULT_PER_BREAK

    /**
     * 从 [level] 升到 level+1 所需经验。
     * 公式：100 + (level-1) × 25（前期平缓，后期陡增）。
     * level ≤ 0 视为 1 级处理。
     */
    fun expForLevel(level: Int): Int {
        val lv = if (level < 1) 1 else level
        return 100 + (lv - 1) * 25
    }

    /**
     * 给实例增加经验并自动连续升级，返回新的不可变实例。
     * 升到等级上限后，剩余经验封顶为「上一级所需经验」，使经验条显示满格而不溢出。
     */
    fun gainExp(inst: MonsterInstance, amount: Int): MonsterInstance {
        if (amount <= 0) return inst
        var lvl = inst.level
        var xp = inst.exp + amount
        val cap = levelCap(inst)
        while (lvl < cap && xp >= expForLevel(lvl)) {
            xp -= expForLevel(lvl)
            lvl += 1
        }
        // 满级时经验条封顶
        if (lvl >= cap) {
            val top = expForLevel((cap - 1).coerceAtLeast(1))
            xp = xp.coerceAtMost(top)
        }
        return inst.copy(level = lvl, exp = xp)
    }

    /** 进行第 (breakthrough+1) 次突破所需达到的等级 */
    fun breakthroughLevelReq(currentBreakthrough: Int): Int = 20 + currentBreakthrough * 5

    /** 是否已满足突破条件（未达上限且等级达标） */
    fun canBreakthrough(inst: MonsterInstance): Boolean =
        inst.breakthrough < MAX_BREAKTHROUGH &&
            inst.level >= breakthroughLevelReq(inst.breakthrough)

    /** 第 (currentBreakthrough+1) 次突破消耗的妖丹 */
    fun breakthroughCost(currentBreakthrough: Int): Int = 20 + currentBreakthrough * 15

    /** 执行突破；条件不满足返回 null（不修改原实例） */
    fun breakthrough(inst: MonsterInstance): MonsterInstance? =
        if (canBreakthrough(inst)) inst.copy(breakthrough = inst.breakthrough + 1) else null

    /** 是否可进化（妖种存在下一形态且等级达标） */
    fun canEvolve(inst: MonsterInstance, pool: MonsterPool = MonsterPool): Boolean {
        val def = pool.byId(inst.defId) ?: return false
        return def.evolveTo != null && inst.level >= EVOLVE_LEVEL_REQ
    }

    /**
     * 执行进化：defId 替换为下一形态，等级/经验/突破全部保留。
     * 条件不满足（无下一形态或等级不足）返回 null。
     */
    fun evolve(inst: MonsterInstance, pool: MonsterPool = MonsterPool): MonsterInstance? {
        val def = pool.byId(inst.defId) ?: return null
        val to = def.evolveTo ?: return null
        if (inst.level < EVOLVE_LEVEL_REQ) return null
        // 进化后稀有度可能升高（如 FAN→LING），其 maxTribulations 随之降低；
        // 若不钳制，原 tribulations 会超过新稀有度上限 → 非法存档(INST_TRIB) 且 t
        // ribulateMult 把属性算超（数值越界/超模）。故按新稀有度上限钳制。
        val newDef = pool.byId(to) ?: return null
        val newTrib = inst.tribulations.coerceAtMost(maxTribulations(newDef.rarity))
        return inst.copy(defId = to, tribulations = newTrib)
    }

    // ── 渡劫天劫（养成线终章，保证成功 / 必成） ──

    /** 渡劫后的永久属性系数（每渡劫一次 +25%） */
    fun tribulateMult(inst: MonsterInstance): Double =
        1.0 + inst.tribulations * TRIBULATE_STAT_BONUS

    /** 该稀有度（妖种基础稀有度）还能渡劫的次数；神阶不可再渡（保值） */
    fun maxTribulations(rarity: Rarity): Int =
        (Rarity.SHEN.ordinal - rarity.ordinal).coerceAtLeast(0)

    /** 渡劫后的「当前稀有度」（基础稀有度 + 渡劫次数，封顶神阶） */
    fun currentRarity(inst: MonsterInstance, pool: MonsterPool = MonsterPool): Rarity? {
        val def = pool.byId(inst.defId) ?: return null
        val idx = (def.rarity.ordinal + inst.tribulations).coerceAtMost(Rarity.SHEN.ordinal)
        return Rarity.values()[idx]
    }

    /**
     * 是否可渡劫：未满渡劫次数，且已「满级（达当前等级上限）+ 满突破」。
     * 神阶妖（currentRarity 已是神）不可再渡。
     */
    fun canTribulate(inst: MonsterInstance, pool: MonsterPool = MonsterPool): Boolean {
        val def = pool.byId(inst.defId) ?: return false
        if (inst.tribulations >= maxTribulations(def.rarity)) return false
        return inst.breakthrough >= MAX_BREAKTHROUGH && inst.level >= levelCap(inst)
    }

    /** 第 (当前渡劫次数+1) 次渡劫消耗的妖丹（逐级递增） */
    fun tribulateCost(inst: MonsterInstance): Int =
        TRIBULATE_COST_YAODAN * (inst.tribulations + 1)

    /**
     * 执行渡劫（保证成功 / 必成）：tribulations +1（稀有度随之升阶、属性倍率提升），保留等级/经验/突破。
     * 条件不满足返回 null（不修改原实例）。
     */
    fun tribulate(inst: MonsterInstance, pool: MonsterPool = MonsterPool): MonsterInstance? =
        if (canTribulate(inst, pool)) inst.copy(tribulations = inst.tribulations + 1) else null

    /**
     * 计算实例的有效战斗属性（含突破加成 + 渡劫倍率 + 天赋分支倍率）。
     * 返回 null 表示 defId 在妖池中找不到（理论上不应发生）。
     */
    fun effectiveStats(inst: MonsterInstance, pool: MonsterPool = MonsterPool): Stats? {
        val def = pool.byId(inst.defId) ?: return null
        val base = def.statsAtLevel(inst.level)
        val mult = statMult(inst) * tribulateMult(inst)
        val t = TalentEngine
        return Stats(
            atk = (base.atk * mult * t.atkMult(inst)).toInt().coerceAtLeast(1),
            def = (base.def * mult * t.defMult(inst)).toInt().coerceAtLeast(1),
            hp = (base.hp * mult * t.hpMult(inst)).toInt().coerceAtLeast(1),
            spd = (base.spd * mult * t.spdMult(inst)).toInt().coerceAtLeast(1),
        )
    }

    /** 进化链完整路径（如 蛇妖→蛟→龙），供 UI 展示 */
    fun evolutionChain(startDefId: String, pool: MonsterPool = MonsterPool): List<String> {
        val chain = mutableListOf<String>()
        var cur = pool.byId(startDefId) ?: return chain
        val seen = mutableSetOf<String>()
        while (cur.id !in seen) {
            chain.add(cur.id)
            seen.add(cur.id)
            cur = cur.evolveTo?.let { pool.byId(it) } ?: break
        }
        return chain
    }
}
