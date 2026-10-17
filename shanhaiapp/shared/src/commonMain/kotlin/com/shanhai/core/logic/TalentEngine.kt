package com.shanhai.core.logic

import com.shanhai.core.model.MonsterInstance
import com.shanhai.core.model.TalentBranch

/**
 * 天赋引擎（GDD 十六·养成扩展：每只妖点 3 层天赋树 攻/防/辅，build 再深一层）。
 *
 * 设计要点：
 * - 每只妖实例在 [MonsterInstance.talents] 上分配天赋点（分支→已点层数）；分支内**顺序解锁**（层 N 需先有 N-1，由 layersOf<MAX_LAYER 保证）。
 * - 天赋点**容量随突破提升**（`talentCap = BASE_TALENT + 突破次数×TALENT_PER_BREAK`），形成攻/防/辅 的 build 取舍：点满一个分支会挤占其他分支。
 * - 分支→主属性加成（每只实例独立，与等级/突破/渡劫倍率相乘）：
 *   - ATTACK   攻：ATK%   （每层 [ATK_PCT_PER_LAYER]）
 *   - DEFENSE  防：DEF%   （每层 [DEF_PCT_PER_LAYER]）+ HP%（每层 [HP_PCT_PER_LAYER]）
 *   - SUPPORT  辅：SPD%   （每层 [SPD_PCT_PER_LAYER]）
 * - 全部数值集中于此，双端一致、可单测；不消耗货币（点天赋是 build 选择，资源约束来自有限的点数）。
 */
object TalentEngine {

    /** 每个分支最多层数 */
    const val MAX_LAYER: Int = 3

    /** 初始可分配天赋点（恰好每个分支点 1 层） */
    const val BASE_TALENT: Int = 3

    /** 每次突破额外获得的天赋点 */
    const val TALENT_PER_BREAK: Int = 1

    // ── 每层百分比加成（分支→主属性） ──
    const val ATK_PCT_PER_LAYER: Double = 0.06   // 攻：ATK%
    const val DEF_PCT_PER_LAYER: Double = 0.06   // 防：DEF%
    const val HP_PCT_PER_LAYER: Double = 0.04    // 防：HP%
    const val SPD_PCT_PER_LAYER: Double = 0.05   // 辅：SPD%

    /** 当前实例可分配天赋点上限 */
    fun talentCap(inst: MonsterInstance): Int =
        BASE_TALENT + inst.breakthrough * TALENT_PER_BREAK

    /** 某分支已分配层数（缺省 0） */
    fun layersOf(inst: MonsterInstance, branch: TalentBranch): Int =
        inst.talents[branch] ?: 0

    /** 已分配总点数 */
    fun allocated(inst: MonsterInstance): Int =
        inst.talents.values.sum()

    /** 是否还能在该分支再点一层（分支未满 且 总点未达容量上限） */
    fun canAssign(inst: MonsterInstance, branch: TalentBranch): Boolean =
        layersOf(inst, branch) < MAX_LAYER && allocated(inst) < talentCap(inst)

    /**
     * 在该分支分配一层（顺序解锁由 layersOf<MAX_LAYER 保证，无需显式校验前置层）。
     * 不可分配（分支已满或容量不足）返回 null，不修改原实例。
     */
    fun assign(inst: MonsterInstance, branch: TalentBranch): MonsterInstance? {
        if (!canAssign(inst, branch)) return null
        val next = inst.talents.toMutableMap()
        next[branch] = layersOf(inst, branch) + 1
        return inst.copy(talents = next)
    }

    // ── 各属性天赋倍率（与突破/渡劫倍率相乘） ──
    fun atkMult(inst: MonsterInstance): Double =
        1.0 + ATK_PCT_PER_LAYER * layersOf(inst, TalentBranch.ATTACK)

    fun defMult(inst: MonsterInstance): Double =
        1.0 + DEF_PCT_PER_LAYER * layersOf(inst, TalentBranch.DEFENSE)

    fun hpMult(inst: MonsterInstance): Double =
        1.0 + HP_PCT_PER_LAYER * layersOf(inst, TalentBranch.DEFENSE)

    fun spdMult(inst: MonsterInstance): Double =
        1.0 + SPD_PCT_PER_LAYER * layersOf(inst, TalentBranch.SUPPORT)
}
