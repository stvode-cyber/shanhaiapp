package com.shanhai.core.logic

import com.shanhai.core.data.MonsterPool

/**
 * 排位赛 Ban/Pick 选将（GDD 二十·竞技与荣誉，补全 [RankEngine] 注释中“ban/pick 依赖平台侧 PvP”的缺口）。
 *
 * 纯逻辑、无随机、无平台依赖，可在本机做等价验证（见 verify/ 脚本）。
 * 对局前双方各 ban 若干妖（移出共享可用池，双方均不可再选），再各 pick 一支出战队伍。
 * 真实匹配 / 对局结算仍由平台侧 PvP 负责；本引擎只管选将合法性与可用池推导。
 *
 * 全部操作用 [DraftResult] 枚举表达成功 / 各类非法态，避免用异常控制正常流程，
 * 便于纯客户端等价断言，也天然具备“坏档 / 外部输入”的入站防护。
 */
object RankBanPickEngine {

    /** 每方 ban 数。 */
    const val BAN_PER_SIDE = 2

    /** 每方 pick 数（出战队伍规模）。 */
    const val PICK_PER_SIDE = 3

    /** 对局两侧。 */
    enum class Side { A, B }

    /** 选将操作结果（确定、可断言）。 */
    enum class DraftResult {
        OK,              // 操作成功
        INVALID_MONSTER, // 妖 id 不存在于妖池
        ALREADY_BANNED,  // 已被任一方 ban（含自己）
        BAN_LIMIT,       // 该方 ban 已达上限
        PICK_BANNED,     // 试图 pick 一个已被 ban 的妖
        ALREADY_PICKED,  // 该方已 pick 过该妖
        PICK_LIMIT,      // 该方 pick 已达上限
    }

    /** 选将态（不可变，每次操作返回新实例）。 */
    data class DraftState(
        val bannedA: Set<String> = emptySet(),
        val bannedB: Set<String> = emptySet(),
        val pickedA: List<String> = emptyList(),
        val pickedB: List<String> = emptyList(),
    ) {
        /** 双方 ban 的并集（双方均不可选）。 */
        val denied: Set<String> get() = bannedA + bannedB
    }

    /** 当前可用池 = 妖池 − 双方 ban 并集。 */
    fun availablePool(state: DraftState): Set<String> =
        MonsterPool.ALL_IDS - state.denied

    private fun mineBan(state: DraftState, side: Side): Set<String> =
        if (side == Side.A) state.bannedA else state.bannedB

    private fun minePick(state: DraftState, side: Side): List<String> =
        if (side == Side.A) state.pickedA else state.pickedB

    /** 任一方 ban 一只妖；成功返回 (新 state, OK)，非法返回 (原 state, 原因)。 */
    fun ban(state: DraftState, side: Side, id: String): Pair<DraftState, DraftResult> {
        if (id !in MonsterPool.ALL_IDS) return state to DraftResult.INVALID_MONSTER
        if (id in state.denied) return state to DraftResult.ALREADY_BANNED
        val mine = mineBan(state, side)
        if (mine.size >= BAN_PER_SIDE) return state to DraftResult.BAN_LIMIT
        val ns = if (side == Side.A) state.copy(bannedA = mine + id)
                 else state.copy(bannedB = mine + id)
        return ns to DraftResult.OK
    }

    /** 任一方 pick 一只妖（必须从可用池、无重复、未超上限）。 */
    fun pick(state: DraftState, side: Side, id: String): Pair<DraftState, DraftResult> {
        if (id !in MonsterPool.ALL_IDS) return state to DraftResult.INVALID_MONSTER
        if (id in state.denied) return state to DraftResult.PICK_BANNED
        val mine = minePick(state, side)
        if (id in mine) return state to DraftResult.ALREADY_PICKED
        if (mine.size >= PICK_PER_SIDE) return state to DraftResult.PICK_LIMIT
        val ns = if (side == Side.A) state.copy(pickedA = mine + id)
                 else state.copy(pickedB = mine + id)
        return ns to DraftResult.OK
    }

    /** 选将是否完成：双方都 ban 满且 pick 满。 */
    fun isComplete(state: DraftState): Boolean =
        state.bannedA.size == BAN_PER_SIDE && state.bannedB.size == BAN_PER_SIDE &&
        state.pickedA.size == PICK_PER_SIDE && state.pickedB.size == PICK_PER_SIDE

    /**
     * 入站队伍合法性防护（坏档 / 外部输入）：
     * 不含被 ban 妖、无重复、规模不超过 PICK_PER_SIDE。用于校验平台回传的队伍，不修改 state。
     */
    fun isTeamValid(state: DraftState, side: Side, team: List<String>): Boolean {
        if (team.any { it in state.denied }) return false
        if (team.size != team.toSet().size) return false
        if (team.size > PICK_PER_SIDE) return false
        return true
    }
}
