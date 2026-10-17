package com.shanhai.core.logic

/**
 * 成就 / 勋章 / 称号（GDD 二十·竞技与荣誉·展示型荣誉）。
 *
 * 纯函数引擎，可由 Python 等价验证：输入玩家聚合画像 [HonorProfile]，输出已解锁勋章/称号。
 * - 成就：复用任务系统 ACHIEVEMENT 分类（见 QuestLibrary），此处只做统计与展示，不重复定义。
 * - 勋章：基于「已达成成就数」的阶梯荣誉（青铜→白银→黄金）。
 * - 称号：基于综合进度的解锁条件，玩家可装备其一作为展示称号（[GameState.equippedTitleId]）。
 *
 * 本引擎不直接依赖 QuestLog / GameState，仅消费 [HonorProfile]，保证双端数值一致、可断言。
 */
object HonorEngine {

    /** 玩家聚合画像（由 GameState 在调用前装配，纯数据，便于双端一致）。 */
    data class HonorProfile(
        val achievementCount: Int,   // 已达成成就数（COMPLETED/CLAIMED）
        val achievementTotal: Int,   // 成就任务总数
        val rankTierIndex: Int,      // 段位索引 0..4（炼气→化神）
        val codexRatio: Int,         // 图鉴完成度 0..100
        val tribulationTotal: Int,   // 累计渡劫飞升次数
    )

    /** 勋章定义（req = 解锁所需「已达成成就数」；req = Int.MAX_VALUE 表示需达成全部成就）。 */
    data class Medal(val id: String, val name: String, val desc: String, val req: Int)

    val MEDALS: List<Medal> = listOf(
        Medal("medal_bronze",   "青铜妖士", "达成 1 项成就", 1),
        Medal("medal_silver",   "白银妖师", "达成 4 项成就", 4),
        Medal("medal_gold",     "黄金妖尊", "达成 8 项成就", 8),
        Medal("medal_diamond",  "钻石妖圣", "达成全部成就",   Int.MAX_VALUE),
    )

    /** 已解锁勋章（按成就数阶梯过滤；钻石需达成全部成就）。 */
    fun unlockedMedals(p: HonorProfile): List<Medal> = MEDALS.filter {
        if (it.req == Int.MAX_VALUE) p.achievementTotal > 0 && p.achievementCount >= p.achievementTotal
        else p.achievementCount >= it.req
    }

    /** 称号定义（解锁条件在 [isUnlocked] 中按 id 判定）。 */
    data class Title(val id: String, val name: String, val desc: String)

    val TITLES: List<Title> = listOf(
        Title("title_shouyao",  "收妖大师", "达成任意 1 项成就"),
        Title("title_xunyao",   "巡妖使",   "图鉴完成度 ≥ 30%"),
        Title("title_tuojie",   "渡劫真君", "完成 1 次渡劫飞升"),
        Title("title_huashen",  "化神尊者", "段位达到化神"),
        Title("title_tujian",   "山海通志", "图鉴完成度 100%"),
        Title("title_dacheng",  "妖志大成", "达成全部成就"),
        Title("title_yingxiong","山海英雄", "达成 6 项成就"),
        Title("title_zhuanji",  "妖志宗师", "图鉴完成度 ≥ 80%"),
        Title("title_jiuzhuan", "九转真君", "完成 3 次渡劫飞升"),
        Title("title_jindan",   "金丹道君", "段位达到金丹"),
        Title("title_yuanying", "元婴老祖", "段位达到元婴"),
        Title("title_zhongliu", "中流砥柱", "达成 12 项成就"),
    )

    /** 判定某个称号是否解锁（基于画像）。 */
    fun isUnlocked(id: String, p: HonorProfile): Boolean = when (id) {
        "title_shouyao"  -> p.achievementCount >= 1
        "title_xunyao"   -> p.codexRatio >= 30
        "title_tuojie"   -> p.tribulationTotal >= 1
        "title_huashen"  -> p.rankTierIndex >= 4
        "title_tujian"   -> p.codexRatio >= 100
        "title_dacheng"  -> p.achievementTotal > 0 && p.achievementCount >= p.achievementTotal
        "title_yingxiong"-> p.achievementCount >= 6
        "title_zhuanji"  -> p.codexRatio >= 80
        "title_jiuzhuan" -> p.tribulationTotal >= 3
        "title_jindan"   -> p.rankTierIndex >= 2
        "title_yuanying" -> p.rankTierIndex >= 3
        "title_zhongliu" -> p.achievementCount >= 12
        else -> false
    }

    /** 已解锁称号列表。 */
    fun unlockedTitles(p: HonorProfile): List<Title> = TITLES.filter { isUnlocked(it.id, p) }

    /** 默认无称号（空串）。 */
    const val NO_TITLE = ""
}
