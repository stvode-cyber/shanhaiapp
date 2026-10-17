package com.shanhai.core.logic

import com.shanhai.core.model.Monster
import com.shanhai.core.model.MonsterCategory

data class CodexEntry(val monster: Monster, val caught: Boolean)

data class Milestone(val threshold: Int, val reward: String, val reached: Boolean)

/**
 * 图鉴：记录已收妖怪。区分「地域图鉴」（计入完成度）与「秘境收的」（不计地域图鉴，GDD 三·探索）。
 * 完成度按地域图鉴 / 妖池总数计算；含里程碑奖励。
 */
class Codex(private val allMonsters: List<Monster>) {
    private val caughtRegion = mutableSetOf<String>()
    private val caughtSecret = mutableSetOf<String>()

    fun catch(id: String, fromSecretRealm: Boolean = false) {
        if (fromSecretRealm) caughtSecret.add(id) else caughtRegion.add(id)
    }

    fun hasInRegion(id: String): Boolean = caughtRegion.contains(id)
    fun hasAny(id: String): Boolean = caughtRegion.contains(id) || caughtSecret.contains(id)

    /** 存档快照：地域已收 / 秘境已收（两套集合独立，便于恢复） */
    fun snapshotRegion(): Set<String> = caughtRegion.toSet()
    fun snapshotSecret(): Set<String> = caughtSecret.toSet()

    fun regionCaught(): Int = caughtRegion.size
    fun regionTotal(): Int = allMonsters.size
    fun regionCompletion(): Double = if (allMonsters.isEmpty()) 0.0 else caughtRegion.size.toDouble() / allMonsters.size

    fun byCategory(category: MonsterCategory): List<CodexEntry> =
        allMonsters.filter { it.category == category }.map { CodexEntry(it, hasInRegion(it.id)) }

    fun milestones(): List<Milestone> =
        MILESTONES.map { (threshold, reward) -> Milestone(threshold, reward, caughtRegion.size >= threshold) }

    /** 本次收妖后是否新解锁里程碑（用于弹奖励） */
    fun newlyUnlocked(prevCount: Int): Pair<Int, String>? =
        MILESTONES.firstOrNull { it.first > prevCount && caughtRegion.size >= it.first }

    companion object {
        private val MILESTONES = listOf(
            1 to "初入巡妖使（香火+20）",
            5 to "小有所成（妖丹×50）",
            10 to "妖王克星（十连祈妖券）",
            20 to "图鉴近全（妖丹×100）",
        )
    }
}
