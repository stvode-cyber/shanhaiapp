package com.shanhai.core.quest

import com.shanhai.core.persistence.QuestSave

/**
 * 任务进度日志：持有全部任务的运行时进度，接收 [GameEvent] 推进目标，
 * 支持领取奖励与每日重置。纯内存实现，便于各平台接入持久化。
 */
class QuestLog {

    private val quests: MutableList<QuestProgress> = QuestLibrary.all.map { def ->
        QuestProgress(def, MutableList(def.objectives.size) { 0 }, QuestState.ACTIVE)
    }.toMutableList()

    /**
     * 记录一个游戏事件，推进匹配的目标进度。
     * 返回本次「新完成」的任务标题（用于 UI 弹窗），已 COMPLETED/CLAIMED 的不再触发。
     */
    fun record(event: GameEvent): List<String> {
        val newlyCompleted = mutableListOf<String>()
        for (q in quests) {
            if (q.state != QuestState.ACTIVE) continue
            for (i in q.def.objectives.indices) {
                val o = q.def.objectives[i]
                when (o.type) {
                    ObjectiveType.CATCH_TOTAL ->
                        if (event is GameEvent.MonsterCaught)
                            q.current[i] = (q.current[i] + 1).coerceAtMost(o.target)
                    ObjectiveType.CATCH_ELEMENT ->
                        if (event is GameEvent.MonsterCaught && event.element.name == o.ref)
                            q.current[i] = o.target
                    ObjectiveType.CATCH_RARITY ->
                        if (event is GameEvent.MonsterCaught && event.rarity.name == o.ref)
                            q.current[i] = o.target
                    ObjectiveType.WIN_BATTLE ->
                        if (event is GameEvent.BattleWon)
                            q.current[i] = (q.current[i] + 1).coerceAtMost(o.target)
                    ObjectiveType.WIN_WITH_ELEMENT ->
                        if (event is GameEvent.BattleWon && event.attackerElement?.name == o.ref)
                            q.current[i] = o.target
                    ObjectiveType.ENTER_SECRET ->
                        if (event is GameEvent.SecretRealmEntered)
                            q.current[i] = (q.current[i] + 1).coerceAtMost(o.target)
                    ObjectiveType.CODEX_RATIO ->
                        if (event is GameEvent.CodexUpdated)
                            q.current[i] = maxOf(q.current[i], event.ratioPercent).coerceAtMost(o.target)
                    ObjectiveType.TRAIN_LEVEL ->
                        if (event is GameEvent.MonsterTrained)
                            q.current[i] = maxOf(q.current[i], event.newLevel).coerceAtMost(o.target)
                    ObjectiveType.BREAKTHROUGH_TOTAL ->
                        if (event is GameEvent.MonsterBrokenThrough)
                            q.current[i] = (q.current[i] + 1).coerceAtMost(o.target)
                    ObjectiveType.EVOLVE_TOTAL ->
                        if (event is GameEvent.MonsterEvolved)
                            q.current[i] = (q.current[i] + 1).coerceAtMost(o.target)
                    ObjectiveType.EQUIP_TOTAL ->
                        if (event is GameEvent.MonsterEquipped)
                            q.current[i] = (q.current[i] + 1).coerceAtMost(o.target)
                    ObjectiveType.FORGE_TOTAL ->
                        if (event is GameEvent.MonsterForged)
                            q.current[i] = (q.current[i] + 1).coerceAtMost(o.target)
                    ObjectiveType.FUSE_TOTAL ->
                        if (event is GameEvent.MonsterFused)
                            q.current[i] = (q.current[i] + 1).coerceAtMost(o.target)
                    ObjectiveType.TRIBULATE_TOTAL ->
                        if (event is GameEvent.MonsterTribulated)
                            q.current[i] = (q.current[i] + 1).coerceAtMost(o.target)
                    ObjectiveType.TALENT_TOTAL ->
                        if (event is GameEvent.MonsterTalentAssigned)
                            q.current[i] = (q.current[i] + 1).coerceAtMost(o.target)
                    ObjectiveType.GACHA_TOTAL ->
                        if (event is GameEvent.MonsterGachaPulled)
                            q.current[i] = (q.current[i] + 1).coerceAtMost(o.target)
                    ObjectiveType.DUNGEON_TOTAL ->
                        if (event is GameEvent.DungeonEntered)
                            q.current[i] = (q.current[i] + 1).coerceAtMost(o.target)
                }
            }
            if (q.isComplete() && q.state == QuestState.ACTIVE) {
                q.state = QuestState.COMPLETED
                newlyCompleted.add(q.def.title)
            }
        }
        return newlyCompleted
    }

    /**
     * 领取任务奖励：仅 COMPLETED 可领，领后变 CLAIMED。
     * 返回奖励定义（由调用方入账货币），不可领返回 null。
     */
    fun claim(id: String): QuestReward? {
        val q = quests.firstOrNull { it.def.id == id } ?: return null
        if (q.state != QuestState.COMPLETED) return null
        q.state = QuestState.CLAIMED
        return q.def.reward
    }

    fun all(): List<QuestProgress> = quests.toList()
    fun byCategory(cat: QuestCategory): List<QuestProgress> = quests.filter { it.def.category == cat }
    fun activeCount(): Int = quests.count { it.state == QuestState.ACTIVE }
    fun completableCount(): Int = quests.count { it.state == QuestState.COMPLETED }

    /** 每日重置：把「今日目标」类任务清零并回到 ACTIVE（其余不动） */
    fun resetDaily() {
        for (q in quests) {
            if (q.def.daily && q.state != QuestState.LOCKED) {
                for (i in q.current.indices) q.current[i] = 0
                q.state = QuestState.ACTIVE
            }
        }
    }

    /** 存档快照：每个任务存 id + 当前进度 + 状态 */
    fun snapshot(): List<QuestSave> =
        quests.map { QuestSave(it.def.id, it.current.toList(), it.state) }

    /**
     * 从存档恢复进度。按 id 匹配；长度不对齐时只恢复前 size 个，避免任务库更新越界。
     * 存档中不存在的新任务保持默认（ACTIVE / 0）。
     */
    fun applySnapshot(list: List<QuestSave>) {
        for (s in list) {
            val q = quests.firstOrNull { it.def.id == s.id } ?: continue
            val capped = s.current.take(q.current.size)
            for (i in q.current.indices) {
                q.current[i] = capped.getOrElse(i) { q.current[i] }
            }
            q.state = s.state
        }
    }
}
