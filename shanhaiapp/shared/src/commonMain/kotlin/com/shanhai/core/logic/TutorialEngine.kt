package com.shanhai.core.logic

/**
 * GDD 十八·新手引导 + 7 日留存序列。
 *
 * - 前 5 分钟引导：5 步（撞见收服 → 灵脉/秘境 → 首战 → 三派择门 → 签到+图鉴），前 3 步轻量强制，全程可跳过。
 * - 7 日留存：逐日解锁 秘境/副本/祈妖/社交，签到递增奖励。
 *
 * 纯函数 + 数据，无平台依赖，可直接用 Python 等价镜像验证。
 */
enum class TutorialStep(val order: Int, val forceable: Boolean, val title: String) {
    CATCH(1, true, "撞见凡级妖，点按+光环收服"),
    EXPLORE(2, true, "查看灵脉地图，入秘境"),
    BATTLE(3, true, "首场 2.5D 对战，高亮克制技"),
    FACTION(4, false, "三派择门（茅山/龙虎山/阁皂）"),
    CHECKIN(5, false, "签到点 + 图鉴 + 今日目标"),
    ;

    companion object {
        /** 按步序升序的全部步骤。 */
        val BY_ORDER = entries.sortedBy { it.order }
        /** 最后一步的步序（全部完成阈值）。 */
        val LAST_ORDER = BY_ORDER.last().order
    }
}

/** 7 日留存逐日解锁的功能入口。 */
enum class RealmFeature(val unlockDay: Int) {
    REALM(2),    // 秘境
    DUNGEON(3),  // 副本
    GACHA(4),    // 祈妖
    SOCIAL(5),   // 社交
}

object TutorialEngine {

    /** 下一步（completedOrder 为已完成的最高步序，0 表示未开始）；全完成返回 null。 */
    fun currentStep(completedOrder: Int): TutorialStep? =
        TutorialStep.BY_ORDER.firstOrNull { it.order > completedOrder }

    /** 前 3 步轻量强制。 */
    fun isForced(step: TutorialStep): Boolean = step.order <= 3

    /** 引导是否整体完成（步骤全过或已跳过）。 */
    fun allDone(completedOrder: Int, skipped: Boolean): Boolean =
        completedOrder >= TutorialStep.LAST_ORDER || skipped

    /** 按天解锁的功能集合（day 从 1 计）。 */
    fun unlockedFeatures(day: Int): Set<RealmFeature> =
        RealmFeature.entries.filter { it.unlockDay <= day }.toSet()

    /** 签到递增奖励（香火），day 从 1 计，封顶 70。 */
    fun signInReward(day: Int): Int = (10 * day).coerceAtMost(70)
}
