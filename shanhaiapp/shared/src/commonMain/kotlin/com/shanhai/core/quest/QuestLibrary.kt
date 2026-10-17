package com.shanhai.core.quest

/**
 * 任务库（代表性切片，覆盖全部 ObjectiveType 与五类分类）。
 * GDD 全量任务（含章节剧情/赛季通行证/宗门科技）后续可在此扩充。
 */
object QuestLibrary {
    val all: List<QuestDef> = listOf(
        // ── 今日目标（每日重置） ──
        QuestDef(
            "d_catch3", "今日·收妖", "今日收服 3 只妖", QuestCategory.DAILY,
            listOf(ObjectiveDef(ObjectiveType.CATCH_TOTAL, 3, label = "收服任意妖")),
            QuestReward(incense = 30, text = "香火+30"), daily = true,
        ),
        QuestDef(
            "d_win2", "今日·试炼", "今日赢得 2 场对战", QuestCategory.DAILY,
            listOf(ObjectiveDef(ObjectiveType.WIN_BATTLE, 2, label = "赢得对战")),
            QuestReward(lingyu = 5, text = "灵玉+5"), daily = true,
        ),
        QuestDef(
            "d_secret1", "今日·秘境", "今日进入秘境 1 次", QuestCategory.DAILY,
            listOf(ObjectiveDef(ObjectiveType.ENTER_SECRET, 1, label = "进入秘境")),
            QuestReward(yaodan = 20, text = "妖丹+20"), daily = true,
        ),

        // ── 山海异闻（剧情） ──
        QuestDef(
            "s_explore5", "初探山海", "收服 5 只妖，踏遍山海", QuestCategory.STORY,
            listOf(ObjectiveDef(ObjectiveType.CATCH_TOTAL, 5, label = "累计收服")),
            QuestReward(text = "十连祈妖券"),
        ),
        QuestDef(
            "s_fire_win", "五行初识", "用火行妖赢下一场对战", QuestCategory.STORY,
            listOf(ObjectiveDef(ObjectiveType.WIN_WITH_ELEMENT, 1, ref = "FIRE", label = "用火行获胜")),
            QuestReward(text = "星辉砂×10"),
        ),
        QuestDef(
            "s_evolve", "蜕凡成圣", "让一只妖进化一次（需达 25 级）", QuestCategory.STORY,
            listOf(ObjectiveDef(ObjectiveType.EVOLVE_TOTAL, 1, label = "进化次数")),
            QuestReward(lingyu = 30, text = "灵玉+30"),
        ),

        // ── 灵脉试炼（日常资源） ──
        QuestDef(
            "t_ling", "灵脉修行", "收服 1 只灵级（及以上）妖", QuestCategory.TRIAL,
            listOf(ObjectiveDef(ObjectiveType.CATCH_RARITY, 1, ref = "LING", label = "收服灵级妖")),
            QuestReward(yaodan = 30, text = "妖丹+30"),
        ),
        QuestDef(
            "t_secret3", "秘境采集", "累计进入秘境 3 次", QuestCategory.TRIAL,
            listOf(ObjectiveDef(ObjectiveType.ENTER_SECRET, 3, label = "进入秘境")),
            QuestReward(text = "渡劫材料×5"),
        ),
        QuestDef(
            "t_train", "勤修苦练", "将一只妖培养到 15 级", QuestCategory.TRIAL,
            listOf(ObjectiveDef(ObjectiveType.TRAIN_LEVEL, 15, label = "培养到 Lv.15")),
            QuestReward(incense = 60, yaodan = 40, text = "香火+60 妖丹+40"),
        ),
        QuestDef(
            "t_break", "破而后立", "完成 1 次突破（消耗妖丹）", QuestCategory.TRIAL,
            listOf(ObjectiveDef(ObjectiveType.BREAKTHROUGH_TOTAL, 1, label = "突破次数")),
            QuestReward(lingyu = 20, text = "灵玉+20"),
        ),
        QuestDef(
            "t_equip", "披甲上阵", "为妖装备 1 件装备", QuestCategory.TRIAL,
            listOf(ObjectiveDef(ObjectiveType.EQUIP_TOTAL, 1, label = "装备次数")),
            QuestReward(yaodan = 20, text = "妖丹+20"),
        ),
        QuestDef(
            "t_forge", "祭炼之道", "祭炼（升级）装备 1 次", QuestCategory.TRIAL,
            listOf(ObjectiveDef(ObjectiveType.FORGE_TOTAL, 1, label = "祭炼次数")),
            QuestReward(incense = 40, text = "香火+40"),
        ),
        QuestDef(
            "t_fuse", "炼妖初成", "炼妖融合 1 次（消耗妖丹）", QuestCategory.TRIAL,
            listOf(ObjectiveDef(ObjectiveType.FUSE_TOTAL, 1, label = "融合次数")),
            QuestReward(lingyu = 30, text = "灵玉+30"),
        ),
        QuestDef(
            "t_tribulate", "渡劫飞升", "渡劫飞升 1 次（满级满突破 + 耗妖丹）", QuestCategory.TRIAL,
            listOf(ObjectiveDef(ObjectiveType.TRIBULATE_TOTAL, 1, label = "渡劫次数")),
            QuestReward(lingyu = 50, yaodan = 50, text = "灵玉+50 妖丹+50"),
        ),
        QuestDef(
            "t_talent", "天赋初开", "为妖点天赋 3 次（攻/防/辅 build）", QuestCategory.TRIAL,
            listOf(ObjectiveDef(ObjectiveType.TALENT_TOTAL, 3, label = "点天赋次数")),
            QuestReward(lingyu = 30, yaodan = 30, text = "灵玉+30 妖丹+30"),
        ),
        QuestDef(
            "t_gacha", "抽签祈妖", "祈妖抽卡 10 次（签文映稀有度）", QuestCategory.TRIAL,
            listOf(ObjectiveDef(ObjectiveType.GACHA_TOTAL, 10, label = "祈妖次数")),
            QuestReward(lingyu = 60, yaodan = 40, text = "灵玉+60 妖丹+40"),
        ),
        QuestDef(
            "t_dungeon", "初探妖境", "通关副本 3 次（掉落妖丹/材料）", QuestCategory.TRIAL,
            listOf(ObjectiveDef(ObjectiveType.DUNGEON_TOTAL, 3, label = "通关副本")),
            QuestReward(lingyu = 50, yaodan = 50, text = "灵玉+50 妖丹+50"),
        ),

        // ── 成就（长线收集，复用现有 ObjectiveType / GameEvent 自动追踪） ──
        QuestDef(
            "a_codex30", "图鉴新秀", "图鉴完成度达到 30%", QuestCategory.ACHIEVEMENT,
            listOf(ObjectiveDef(ObjectiveType.CODEX_RATIO, 30, label = "图鉴完成度")),
            QuestReward(text = "称号「巡妖使」"),
        ),
        QuestDef(
            "a_catch10", "收妖大师", "累计收服 10 只妖", QuestCategory.ACHIEVEMENT,
            listOf(ObjectiveDef(ObjectiveType.CATCH_TOTAL, 10, label = "累计收服")),
            QuestReward(text = "自选灵妖"),
        ),
        QuestDef(
            "a_thunder", "元素调和", "用雷行妖赢下一场对战", QuestCategory.ACHIEVEMENT,
            listOf(ObjectiveDef(ObjectiveType.WIN_WITH_ELEMENT, 1, ref = "THUNDER", label = "用雷行获胜")),
            QuestReward(lingyu = 50, text = "灵玉+50"),
        ),
        QuestDef(
            "a_codex100", "山海通志", "图鉴完成度达到 100%", QuestCategory.ACHIEVEMENT,
            listOf(ObjectiveDef(ObjectiveType.CODEX_RATIO, 100, label = "图鉴完成度")),
            QuestReward(text = "称号「山海通志」"),
        ),
        QuestDef(
            "a_catch100", "收妖宗师", "累计收服 100 只妖", QuestCategory.ACHIEVEMENT,
            listOf(ObjectiveDef(ObjectiveType.CATCH_TOTAL, 100, label = "累计收服")),
            QuestReward(lingyu = 120, yaodan = 120, text = "灵玉+120 妖丹+120"),
        ),
        QuestDef(
            "a_catch_shen", "神妖降世", "收服 1 只神级妖", QuestCategory.ACHIEVEMENT,
            listOf(ObjectiveDef(ObjectiveType.CATCH_RARITY, 1, ref = "SHEN", label = "收服神级妖")),
            QuestReward(text = "神妖专属外观"),
        ),
        QuestDef(
            "a_win50", "百战之师", "累计赢得 50 场对战", QuestCategory.ACHIEVEMENT,
            listOf(ObjectiveDef(ObjectiveType.WIN_BATTLE, 50, label = "累计胜场")),
            QuestReward(lingyu = 100, text = "灵玉+100"),
        ),
        QuestDef(
            "a_trib3", "九转飞升", "累计渡劫飞升 3 次", QuestCategory.ACHIEVEMENT,
            listOf(ObjectiveDef(ObjectiveType.TRIBULATE_TOTAL, 3, label = "累计渡劫")),
            QuestReward(lingyu = 80, yaodan = 80, text = "灵玉+80 妖丹+80"),
        ),
        QuestDef(
            "a_dungeon10", "妖境征服", "累计通关副本 10 次", QuestCategory.ACHIEVEMENT,
            listOf(ObjectiveDef(ObjectiveType.DUNGEON_TOTAL, 10, label = "累计通关副本")),
            QuestReward(lingyu = 90, yaodan = 90, text = "灵玉+90 妖丹+90"),
        ),
        QuestDef(
            "a_gacha50", "祈妖有缘", "累计祈妖抽卡 50 次", QuestCategory.ACHIEVEMENT,
            listOf(ObjectiveDef(ObjectiveType.GACHA_TOTAL, 50, label = "累计祈妖")),
            QuestReward(lingyu = 60, text = "灵玉+60"),
        ),
        QuestDef(
            "a_secret20", "秘境行者", "累计进入秘境 20 次", QuestCategory.ACHIEVEMENT,
            listOf(ObjectiveDef(ObjectiveType.ENTER_SECRET, 20, label = "累计进入秘境")),
            QuestReward(yaodan = 100, text = "妖丹+100"),
        ),
        QuestDef(
            "a_talent9", "天赋纵横", "累计为妖点天赋 9 次", QuestCategory.ACHIEVEMENT,
            listOf(ObjectiveDef(ObjectiveType.TALENT_TOTAL, 9, label = "累计点天赋")),
            QuestReward(lingyu = 50, yaodan = 50, text = "灵玉+50 妖丹+50"),
        ),
        QuestDef(
            "a_evolve5", "进化之道", "累计让妖进化 5 次", QuestCategory.ACHIEVEMENT,
            listOf(ObjectiveDef(ObjectiveType.EVOLVE_TOTAL, 5, label = "累计进化")),
            QuestReward(lingyu = 60, yaodan = 60, text = "灵玉+60 妖丹+60"),
        ),
        QuestDef(
            "a_train30", "登峰造极", "培养任一妖到 30 级", QuestCategory.ACHIEVEMENT,
            listOf(ObjectiveDef(ObjectiveType.TRAIN_LEVEL, 30, label = "培养到 Lv.30")),
            QuestReward(lingyu = 40, yaodan = 40, text = "灵玉+40 妖丹+40"),
        ),
        QuestDef(
            "a_break5", "破茧成蝶", "累计突破 5 次", QuestCategory.ACHIEVEMENT,
            listOf(ObjectiveDef(ObjectiveType.BREAKTHROUGH_TOTAL, 5, label = "累计突破")),
            QuestReward(lingyu = 50, text = "灵玉+50"),
        ),
        QuestDef(
            "a_equip5", "披甲三千", "累计为妖装备 5 次", QuestCategory.ACHIEVEMENT,
            listOf(ObjectiveDef(ObjectiveType.EQUIP_TOTAL, 5, label = "累计装备")),
            QuestReward(yaodan = 40, text = "妖丹+40"),
        ),
        QuestDef(
            "a_forge10", "神工匠心", "累计祭炼装备 10 次", QuestCategory.ACHIEVEMENT,
            listOf(ObjectiveDef(ObjectiveType.FORGE_TOTAL, 10, label = "累计祭炼")),
            QuestReward(incense = 80, text = "香火+80"),
        ),
        QuestDef(
            "a_fuse5", "炼妖宗师", "累计炼妖融合 5 次", QuestCategory.ACHIEVEMENT,
            listOf(ObjectiveDef(ObjectiveType.FUSE_TOTAL, 5, label = "累计融合")),
            QuestReward(lingyu = 40, text = "灵玉+40"),
        ),
        QuestDef(
            "a_win200", "千胜之师", "累计赢得 200 场对战", QuestCategory.ACHIEVEMENT,
            listOf(ObjectiveDef(ObjectiveType.WIN_BATTLE, 200, label = "累计胜场")),
            QuestReward(lingyu = 150, text = "灵玉+150"),
        ),
        QuestDef(
            "a_secret50", "秘境寻踪", "累计进入秘境 50 次", QuestCategory.ACHIEVEMENT,
            listOf(ObjectiveDef(ObjectiveType.ENTER_SECRET, 50, label = "累计进入秘境")),
            QuestReward(yaodan = 150, text = "妖丹+150"),
        ),
        QuestDef(
            "a_gacha200", "祈妖狂人", "累计祈妖抽卡 200 次", QuestCategory.ACHIEVEMENT,
            listOf(ObjectiveDef(ObjectiveType.GACHA_TOTAL, 200, label = "累计祈妖")),
            QuestReward(lingyu = 80, text = "灵玉+80"),
        ),
        QuestDef(
            "a_dungeon30", "妖境霸主", "累计通关副本 30 次", QuestCategory.ACHIEVEMENT,
            listOf(ObjectiveDef(ObjectiveType.DUNGEON_TOTAL, 30, label = "累计通关副本")),
            QuestReward(lingyu = 120, yaodan = 120, text = "灵玉+120 妖丹+120"),
        ),

        // ── 年节限时（活动） ──
        QuestDef(
            "e_lantern", "上元灯会", "收服 1 只火行妖", QuestCategory.EVENT,
            listOf(ObjectiveDef(ObjectiveType.CATCH_ELEMENT, 1, ref = "FIRE", label = "收服火行妖")),
            QuestReward(text = "节日外观·灯神"),
        ),
    )

    fun byId(id: String): QuestDef? = all.firstOrNull { it.id == id }
}
