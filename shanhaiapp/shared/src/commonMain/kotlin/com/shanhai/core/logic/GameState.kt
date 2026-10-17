package com.shanhai.core.logic

import com.shanhai.core.logic.SectTechEngine
import com.shanhai.core.data.EquipmentLibrary
import com.shanhai.core.data.MonsterPool
import com.shanhai.core.data.defaultRandom
import com.shanhai.core.logic.ARCaptureController
import com.shanhai.core.logic.DungeonEngine
import com.shanhai.core.logic.GachaEngine
import com.shanhai.core.logic.LocationEngine
import com.shanhai.core.logic.ShopEngine
import com.shanhai.core.logic.SocialEngine
import com.shanhai.core.model.Element
import com.shanhai.core.model.EquipSlot
import com.shanhai.core.model.MonsterInstance
import com.shanhai.core.model.OwnedEquip
import com.shanhai.core.model.Stats
import com.shanhai.core.model.TalentBranch
import com.shanhai.core.model.MoralChoice
import com.shanhai.core.persistence.SaveData
import com.shanhai.core.quest.GameEvent
import com.shanhai.core.quest.QuestCategory
import com.shanhai.core.quest.QuestLog
import com.shanhai.core.quest.QuestReward
import com.shanhai.core.quest.QuestState

/**
 * 收妖结果：里程碑奖励 + 本次新完成的任务标题（供 UI 弹窗）。
 * [success] 默认 true；当传入的妖种 id 在 MonsterPool 中不存在（平台误传 / 过期数据 /
 * 外挂注入）时为 false 且本次收妖零变更（不污染图鉴、不产生悬空实例）。
 */
data class CatchOutcome(val milestone: String?, val questsCompleted: List<String>, val success: Boolean = true)

/** 进入秘境的结果：主题、五行偏向、本次刷妖列表 */
data class SecretRealmResult(val theme: RegionTheme, val element: Element, val spawns: List<String>)

/**
 * 全局游戏状态（切片版）：图鉴 + 出战队伍（≤6）+ 三类货币 + 每日秘境次数 + 任务进度。
 * 持久化由各平台仓储调用 toSaveData/applySaveData 接入（见 persistence/SaveData）。
 */
class GameState(val codex: Codex = Codex(MonsterPool.all)) {
    val quests = QuestLog()
    val team: MutableList<String> = mutableListOf()      // 出战队伍（≤6，存「妖实例 id」）
    val owned: MutableMap<String, MonsterInstance> = mutableMapOf() // 拥有的妖实例（按实例 id 索引）
    var nextInstSeq: Int = 1                             // 实例 id 自增序号
    // ── 装备 / 炼妖 ──
    val equipment: MutableList<OwnedEquip> = mutableListOf()        // 拥有的装备实例
    var nextEquipSeq: Int = 1                                      // 装备实例 id 自增序号
    val equipped: MutableMap<String, MutableMap<EquipSlot, String>> = mutableMapOf() // 妖实例id → 槽位 → 装备实例id
    var incense: Int = 120       // 香火（体力，每日回满）
    var lingyu: Int = 0          // 灵玉（付费货币）
    var yaodan: Int = 0          // 妖丹（重复妖转化，专用于突破/炼宝）
    var secretRealmLeft: Int = 3 // 每日秘境次数
    var lastDailyReset: String = "" // 上次每日重置日期 yyyy-MM-dd，用于跨天自动重置
    // ── ② LBS·AR / ③ 祈妖·副本·社交·经济 ──
    var gachaNormalSinceShen: Int = 0                              // 通用池：距上次出神抽数（硬保底状态）
    var gachaLimitedSinceShen: Int = 0                             // 限定神池：距上次出神抽数（硬保底状态）
    var currentFormation: FormationTemplate? = null                 // 当前选的流派模板（null=未选）
    val dungeonEnteredToday: MutableMap<String, Int> = mutableMapOf() // 副本 id → 当日次数
    var faction: String? = null                              // 所属派系
    var strongholdNode: String? = null                       // 据点战占领节点
    var strongholdDay: String = ""                           // 据点战占领日期（每日限一次）
    var shopIncenseBought: Int = 0                           // 当日引妖香已购份数
    var shopDay: String = ""                                 // 商店购买日期（每日重置）
    var reputation: Int = ReputationEngine.DEFAULT_SCORE      // 信誉分（GDD 十六·公平与稳健）
    // ── 二十·宗门科技树（GDD 二十·公会建设 + 科技加成） ──
    val sectTech: MutableMap<String, Int> = mutableMapOf()    // 科技节点 id → 等级
    var sectContribution: Int = 0                             // 宗门贡献（占领据点积累，用于研习科技）
    // ── 十八·新手引导 / 7 日留存 ──
    var tutorialStep: Int = 0                 // 已完成引导最高步序（0=未开始）
    var tutorialSkipped: Boolean = false      // 是否跳过引导
    var signInDay: Int = 0                    // 累计签到天数（驱动 7 日留存解锁）
    var lastSignIn: String = ""               // 最近签到日期 yyyy-MM-dd
    // ── 十九·长线留存：家园洞府（放置产香火/妖丹 + 阵法风水加成） ──
    var homesteadLevel: Int = 0               // 阵法风水等级（0 = 未建，但可产基础产出）
    var lastHomesteadCollect: Long = 0        // 上次收取时间戳（ms epoch；0 = 从未收取）

    // ── 十九·长线留存：赛季通行证（90 天双轨，GDD 十九） ──
    var seasonStartMs: Long = 0               // 当前赛季起点（ms epoch；0 = 未开始）
    var seasonXp: Int = 0                     // 当前赛季累计经验
    var hasSeasonPass: Boolean = false        // 是否已购通行证（解锁付费轨道）
    var seasonClaimedFree: MutableSet<Int> = mutableSetOf()  // 已领免费档
    var seasonClaimedPaid: MutableSet<Int> = mutableSetOf()   // 已领付费档
    var seasonIndex: Int = 1                  // 赛季序号（滚动 +1）

    // ── 十六·公平与稳健：疲劳值 / 单日收益封顶（GDD 十六·防脚本） ──
    var fatigue: Int = 0                      // 疲劳值 0..100，每日跨天重置

    // ── 二十·竞技与荣誉：排位赛段位天梯（GDD 二十·段位 炼气→化神） ──
    var rankTierIndex: Int = RankEngine.MIN_TIER_INDEX   // 当前段位索引 0=炼气 … 4=化神
    var rankPoints: Int = 0                                // 当前段位内积分 0..99

    // ── 二十·竞技与荣誉：成就 / 勋章 / 称号（GDD 二十·展示型荣誉） ──
    var tribulationTotal: Int = 0        // 累计渡劫飞升次数（用于称号解锁统计）
    var equippedTitleId: String = HonorEngine.NO_TITLE   // 玩家装备的展示称号 id（""=无）

    // ── 天气地形战斗（GDD 十九）：运行时环境变量，不进存档，由平台提供真实天气/地形 ──
    var currentWeather: Weather = Weather.CLEAR      // 当前天气（晴/雨/雪/风）
    var currentTerrain: RegionTheme? = null         // 当前地形（山林/水域/古墓/东海），null = 无地形

    /**
     * 收妖：生成一只「妖实例」（等级 1、经验 0、未突破）并入队，推进图鉴与任务进度。
     * 同一妖种可多次收服，得到各自独立的实例（instanceId 唯一）。
     */
    fun catchMonster(id: String, fromSecretRealm: Boolean = false, choice: MoralChoice? = null): CatchOutcome {
        // 防御性边界：未知妖种 id（平台误传 / 过期数据 / 外挂注入）直接拒绝，
        // 避免污染图鉴并产生 defId 悬空、属性回退首妖的孤儿实例。零变更返回。
        if (MonsterPool.byId(id) == null) return CatchOutcome(null, emptyList(), success = false)
        val prev = codex.regionCaught()
        codex.catch(id, fromSecretRealm)

        val inst = MonsterInstance("i_${id}_${nextInstSeq++}", id, 1, 0, 0, moralChoice = choice)
        owned[inst.instanceId] = inst
        if (team.size < 6) team.add(inst.instanceId)
        ensureStarterKit()   // 首只妖时发放新手装备，保证「炼妖」页可用

        val monster = MonsterPool.byId(id)!!
        val completed = mutableListOf<String>()
        completed += quests.record(GameEvent.MonsterCaught(monster.element, monster.rarity, fromSecretRealm))
        completed += quests.record(GameEvent.CodexUpdated((codex.regionCompletion() * 100).toInt()))

        val milestone = codex.newlyUnlocked(prev)?.second
        completeTutorialStep(1)   // 引导步 ①：首次收服
        addSeasonXp(SeasonEngine.XP_CATCH)   // 收妖也给赛季经验
        addFatigue(FatigueEngine.FATIGUE_CATCH)   // 收妖累积疲劳
        return CatchOutcome(milestone, completed)
    }

    /** 取某拥有实例的镇压/超度有效属性倍率；实例不存在返回 null（GDD 十七·自由度） */
    fun moralModsFor(instanceId: String): StatMods? {
        val inst = owned[instanceId] ?: return null
        return MoralChoiceEngine.modifiers(inst.moralChoice)
    }

    // ── 养成 API（等级 / 经验 / 突破 / 进化） ──

    /** 给指定实例加经验并自动升级，推进「培养」类任务；经验受宗门科技「经修」倍率加成 */
    fun gainExp(instanceId: String, amount: Int): List<String> {
        val inst = owned[instanceId] ?: return emptyList()
        val after = CultivationEngine.gainExp(inst, (amount * sectExpMul() * activityExpMul()).toInt())
        owned[instanceId] = after
        return quests.record(GameEvent.MonsterTrained(after.level))
    }

    /** 突破：消耗妖丹、提升等级上限与属性。条件/妖丹不足返回 false */
    fun breakthroughMonster(instanceId: String): Boolean {
        val inst = owned[instanceId] ?: return false
        if (!CultivationEngine.canBreakthrough(inst)) return false
        val cost = CultivationEngine.breakthroughCost(inst.breakthrough)
        if (yaodan < cost) return false
        yaodan -= cost
        owned[instanceId] = CultivationEngine.breakthrough(inst) ?: return false
        quests.record(GameEvent.MonsterBrokenThrough)
        return true
    }

    /** 进化：达级且存在下一形态则进化，保留等级/经验/突破。不可进化返回 false */
    fun evolveMonster(instanceId: String): Boolean {
        val inst = owned[instanceId] ?: return false
        val after = CultivationEngine.evolve(inst) ?: return false
        owned[instanceId] = after
        quests.record(GameEvent.MonsterEvolved)
        return true
    }

    /** 渡劫飞升：满级满突破后消耗妖丹，稀有度升阶 + 永久属性倍率（保证成功 / 必成）。条件/妖丹不足返回 false */
    fun tribulateMonster(instanceId: String): Boolean {
        val inst = owned[instanceId] ?: return false
        if (!CultivationEngine.canTribulate(inst)) return false
        val cost = CultivationEngine.tribulateCost(inst)
        if (yaodan < cost) return false
        yaodan -= cost
        owned[instanceId] = CultivationEngine.tribulate(inst) ?: return false
        quests.record(GameEvent.MonsterTribulated)
        tribulationTotal += 1   // 累计渡劫次数（称号解锁统计）
        return true
    }

    // ── 天赋 / 命格（每只妖点 3 层天赋树 攻/防/辅） ──

    /**
     * 给指定妖实例在 [branch] 分支点一层天赋（顺序解锁，受天赋点容量约束）。
     * 容量不足或分支已满返回 false；成功后推进「天赋」类任务。
     */
    fun assignTalent(instanceId: String, branch: TalentBranch): Boolean {
        val inst = owned[instanceId] ?: return false
        if (!TalentEngine.canAssign(inst, branch)) return false
        owned[instanceId] = TalentEngine.assign(inst, branch) ?: return false
        quests.record(GameEvent.MonsterTalentAssigned(branch))
        return true
    }

    // ── 装备 / 祭炼 / 炼妖融合 API ──

    /**
     * 把一件装备发到背包（用于奖励/演示），返回新实例；[equipId] 不在 [EquipmentLibrary]
     * 中（平台误传 / 过期数据 / 外挂注入）返回 null 且不入包，避免产生 defId 悬空、
     * 装备页显示异常的死装备。
     */
    fun grantEquip(equipId: String): OwnedEquip? {
        if (EquipmentLibrary.byId(equipId) == null) return null
        val oe = OwnedEquip(equipId, "e_${nextEquipSeq++}", 1)
        equipment.add(oe)
        return oe
    }

    /** 首只妖时发放新手装备（保证「炼妖」页立即可用），仅当背包为空时触发一次 */
    private fun ensureStarterKit() {
        if (equipment.isEmpty()) {
            grantEquip("eq_sword")
            grantEquip("eq_armor")
        }
    }

    /** 按装备实例 id 查找背包中的装备 */
    fun findEquip(equipEid: String): OwnedEquip? = equipment.firstOrNull { it.instanceId == equipEid }

    /** 该妖实例当前已装备的多件装备（按映射取出） */
    fun equippedItems(instanceId: String): List<OwnedEquip> {
        val slots = equipped[instanceId] ?: return emptyList()
        return slots.values.mapNotNull { findEquip(it) }
    }

    /** 给妖实例装备一件装备（同槽已有则替换，原装备回到背包）。装备被别的妖占用或妖/装备不存在返回 false */
    fun equipItem(instanceId: String, equipEid: String): Boolean {
        val inst = owned[instanceId] ?: return false
        val oe = findEquip(equipEid) ?: return false
        val def = MonsterPool.byId(inst.defId) ?: return false
        for ((mid, slots) in equipped) {
            if (mid != instanceId && slots.containsValue(equipEid)) return false
        }
        val ed = EquipmentLibrary.byId(oe.equipId) ?: return false
        equipped.getOrPut(instanceId) { mutableMapOf() }[ed.slot] = equipEid
        quests.record(GameEvent.MonsterEquipped)
        return true
    }

    /** 卸下某妖实例某槽位的装备（回到背包） */
    fun unequipItem(instanceId: String, slot: EquipSlot): Boolean =
        equipped[instanceId]?.remove(slot) != null

    /** 祭炼（升级）一件装备：消耗妖丹，等级 +1，至 maxLevel 止 */
    fun forgeEquip(equipEid: String): Boolean {
        val idx = equipment.indexOfFirst { it.instanceId == equipEid }
        if (idx < 0) return false
        val oe = equipment[idx]
        val def = EquipmentLibrary.byId(oe.equipId) ?: return false
        if (oe.level >= EquipmentEngine.maxLevelOf(def, oe)) return false
        if (yaodan < EquipmentEngine.FORGE_COST_YAODAN) return false
        yaodan -= EquipmentEngine.FORGE_COST_YAODAN
        equipment[idx] = oe.copy(level = oe.level + 1)
        quests.record(GameEvent.MonsterForged)
        return true
    }

    // ── 装备五路径（升星 / 突破 / 淬炼 / 继承） ──

    /** 升星：消耗灵玉，星级 +1（至 [EquipmentEngine.MAX_STAR] 止） */
    fun starUpEquip(equipEid: String): Boolean {
        val idx = equipment.indexOfFirst { it.instanceId == equipEid }
        if (idx < 0) return false
        val oe = equipment[idx]
        if (!EquipmentEngine.canStarUp(oe)) return false
        val cost = EquipmentEngine.starCost(oe)
        if (lingyu < cost) return false
        lingyu -= cost
        equipment[idx] = EquipmentEngine.starUp(oe) ?: return false
        quests.record(GameEvent.MonsterForged)
        return true
    }

    /** 突破：消耗灵玉，突破阶 +1（上限随稀有度），提升等级上限与每级成长 */
    fun breakthroughEquip(equipEid: String): Boolean {
        val idx = equipment.indexOfFirst { it.instanceId == equipEid }
        if (idx < 0) return false
        val oe = equipment[idx]
        val def = EquipmentLibrary.byId(oe.equipId) ?: return false
        if (!EquipmentEngine.canBreakthrough(oe, def)) return false
        val cost = EquipmentEngine.tierCost(oe)
        if (lingyu < cost) return false
        lingyu -= cost
        equipment[idx] = EquipmentEngine.breakthrough(oe, def) ?: return false
        quests.record(GameEvent.MonsterForged)
        return true
    }

    /** 淬炼：消耗灵玉，开启/强化一条副属性（确定性顺序，最多 [EquipmentEngine.MAX_SUBSTATS] 条） */
    fun temperEquip(equipEid: String): Boolean {
        val idx = equipment.indexOfFirst { it.instanceId == equipEid }
        if (idx < 0) return false
        val oe = equipment[idx]
        if (!EquipmentEngine.canTemper(oe)) return false
        val cost = EquipmentEngine.temperCost(oe)
        if (lingyu < cost) return false
        lingyu -= cost
        equipment[idx] = EquipmentEngine.temper(oe) ?: return false
        quests.record(GameEvent.MonsterForged)
        return true
    }

    /** 继承：把源装备的养成（等级/星级/突破/副属性，取较优）转移到目标，源被消耗，消耗妖丹 */
    fun inheritEquip(srcEid: String, dstEid: String): Boolean {
        if (srcEid == dstEid) return false
        val si = equipment.indexOfFirst { it.instanceId == srcEid }
        if (si < 0) return false
        val di = equipment.indexOfFirst { it.instanceId == dstEid }
        if (di < 0) return false
        val src = equipment[si]
        val dst = equipment[di]
        if (yaodan < EquipmentEngine.INHERIT_COST_YAODAN) return false
        // 源若正被装备，先卸下（避免悬空引用）
        for ((_, slots) in equipped) {
            slots.entries.removeIf { it.value == srcEid }
        }
        yaodan -= EquipmentEngine.INHERIT_COST_YAODAN
        equipment[di] = EquipmentEngine.inherit(src, dst) ?: return false
        equipment.removeIf { it.instanceId == srcEid }
        quests.record(GameEvent.MonsterForged)
        return true
    }

    /** 计算妖实例的有效战斗属性（突破加成 + 已装备道具加成 + 宗门科技 防御/体力 倍率） */
    fun effectiveStatsOf(inst: MonsterInstance): Stats {
        val base = CultivationEngine.effectiveStats(inst) ?: Stats(1, 1, 1, 1)
        val m = MonsterPool.byId(inst.defId)
        val bonus = if (m != null) EquipmentEngine.totalBonus(m, equippedItems(inst.instanceId)) else Stats(0, 0, 0, 0)
        return Stats(
            atk = base.atk + bonus.atk,
            def = ((base.def + bonus.def) * sectDefenseMul()).toInt(),
            hp = ((base.hp + bonus.hp) * sectHpMul()).toInt(),
            spd = base.spd + bonus.spd,
        )
    }

    /** 由妖实例构建对战单位（含突破与装备的有效属性） */
    fun buildCombatant(inst: MonsterInstance): Combatant {
        val m = MonsterPool.byId(inst.defId) ?: MonsterPool.all.first()
        val skills = m.skills.take(4)
        return Combatant(m, inst.level, skills, inst.breakthrough, effectiveStatsOf(inst))
    }

    /** 炼妖融合：消耗两实例与妖丹，产出新实例并入队；不可融合返回 null */
    fun fuse(aId: String, bId: String): FusionEngine.FusionResult? {
        val a = owned[aId] ?: return null
        val b = owned[bId] ?: return null
        if (!FusionEngine.canFuse(a, b, yaodan)) return null
        val plan = FusionEngine.plan(a, b) ?: return null
        yaodan -= FusionEngine.FUSE_COST_YAODAN
        unequipAll(aId)
        unequipAll(bId)
        owned.remove(aId)
        owned.remove(bId)
        team.remove(aId)
        team.remove(bId)
        val newInst = MonsterInstance("i_${plan.newDefId}_${nextInstSeq++}", plan.newDefId, plan.newLevel, 0, plan.newBreakthrough)
        owned[newInst.instanceId] = newInst
        if (team.size < 6) team.add(newInst.instanceId)
        quests.record(GameEvent.MonsterFused)
        return plan
    }

    private fun unequipAll(instanceId: String) { equipped.remove(instanceId) }

    /** 对战胜利（attackerElement 为获胜方行，用于「用某行获胜」类目标） */
    fun onBattleWon(attackerElement: Element?): List<String> {
        addSeasonXp(SeasonEngine.XP_BATTLE)   // 对战胜利给赛季经验
        addFatigue(FatigueEngine.FATIGUE_BATTLE)   // 对战累积疲劳
        return quests.record(GameEvent.BattleWon(attackerElement))
    }

    /**
     * 进入秘境（GDD 三·在家秘境）：每日 [secretRealmLeft] 次优先（免费），耗尽则耗
     * [SecretRealmEngine.INCENSE_COST] 香火；随机主题 → 生成刷妖 → 记 SecretRealmEntered 事件。
     * 无可用次数且香火不足返回 null。
     */
    fun enterSecretRealm(rng: () -> Double = ::defaultRandom): SecretRealmResult? {
        val usedIncense = secretRealmLeft <= 0
        if (usedIncense) {
            if (incense < SecretRealmEngine.INCENSE_COST) return null
            incense -= SecretRealmEngine.INCENSE_COST
        } else {
            secretRealmLeft -= 1
        }
        val theme = SecretRealmEngine.pickTheme(rng)
        val spawns = SecretRealmEngine.generateSpawns(theme, SecretRealmEngine.SPAWN_COUNT, rng)
        quests.record(GameEvent.SecretRealmEntered)
        completeTutorialStep(2)   // 引导步 ②：入秘境
        return SecretRealmResult(theme, SecretRealmEngine.themeElementBias(theme), spawns)
    }

    // ── 十八·新手引导 / 7 日留存 ──

    /** 引导是否仍在进行（未全完成且未跳过）。 */
    fun tutorialActive(): Boolean =
        !TutorialEngine.allDone(tutorialStep, tutorialSkipped)

    /** 当前应进行的引导步（无则 null，表示已完成/已跳过）。 */
    fun currentTutorialStep(): TutorialStep? =
        TutorialEngine.currentStep(tutorialStep)

    /** 标记某步完成（仅当步序更高时推进）。 */
    fun completeTutorialStep(order: Int) {
        if (order > tutorialStep) tutorialStep = order
    }

    /** 跳过整段引导。 */
    fun skipTutorial() { tutorialSkipped = true }

    /**
     * 已解锁的功能入口：引导进行中全部开放（引导即教学，包含秘境），
     * 引导完成后按累计签到天逐日解锁。
     */
    fun unlockedFeatures(): Set<RealmFeature> =
        if (tutorialActive()) RealmFeature.entries.toSet()
        else TutorialEngine.unlockedFeatures(signInDay)

    /** 今日是否已签到。 */
    fun canSignIn(today: String): Boolean = lastSignIn != today

    /**
     * 签到：每日首次领取递增奖励（香火），推进 7 日留存并标记引导步 ⑤。
     * 已签过则返回 0。
     */
    fun claimSignIn(today: String): Int {
        if (!canSignIn(today)) return 0
        signInDay += 1
        lastSignIn = today
        val reward = TutorialEngine.signInReward(signInDay)
        val gained = (reward * incomeMul() * sectIncomeMul() * activityIncomeMul()).toInt()   // 签到收益受疲劳封顶 + 宗门科技「资粮」+ 活动加成
        incense += gained
        completeTutorialStep(5)   // 引导步 ⑤：签到
        addSeasonXp(SeasonEngine.XP_SIGNIN)   // 签到给赛季经验
        addFatigue(FatigueEngine.FATIGUE_SIGNIN)   // 签到累积疲劳
        return gained
    }

    // ── 十九·长线留存：家园洞府（放置产香火/妖丹 + 阵法风水加成） ──

    /** 当前香火时产（/小时，随阵法等级提升）。 */
    fun homesteadIncenseRate(): Double = HomesteadEngine.incenseRate(homesteadLevel)

    /** 当前妖丹时产（/小时，随阵法等级提升）。 */
    fun homesteadPillRate(): Double = HomesteadEngine.pillRate(homesteadLevel)

    /** 阵法风水全局攻击增益（小数，如 0.06 = +6%）。 */
    fun homesteadAttackBonus(): Double = HomesteadEngine.fengshuiAttackBonus(homesteadLevel)

    /** 预览当前挂起产出（不修改状态）。 */
    fun homesteadPending(nowMs: Long): HomesteadResult =
        HomesteadEngine.compute(homesteadLevel, nowMs, lastHomesteadCollect)

    /**
     * 收取洞府产出：把 [nowMs] 时刻的挂起产出入账（香火/妖丹），并把收取时间戳推进到 [nowMs]。
     * 返回本次实际获得；[nowMs] <= [lastHomesteadCollect]（或从未收取 lastMs<=0）时返回 0 不入账。
     */
    fun collectHomestead(nowMs: Long): HomesteadResult {
        if (lastHomesteadCollect <= 0) {
            // 首次进入：仅启动计时（推进时间戳），不补发历史产出，避免新号白嫖
            lastHomesteadCollect = nowMs
            return HomesteadResult(0, 0, 0.0, capped = false)
        }
        val r = HomesteadEngine.compute(homesteadLevel, nowMs, lastHomesteadCollect)
        if (r.incense <= 0 && r.pills <= 0) return r
        incense += r.incense
        yaodan += r.pills
        lastHomesteadCollect = nowMs
        return r
    }

    /** 是否可升阵法（妖丹够）。 */
    fun canUpgradeHomestead(): Boolean = yaodan >= HomesteadEngine.upgradeCost(homesteadLevel)

    /** 升一级阵法风水，耗妖丹；成功返回 true。 */
    fun upgradeHomestead(): Boolean {
        val cost = HomesteadEngine.upgradeCost(homesteadLevel)
        if (yaodan < cost) return false
        yaodan -= cost
        homesteadLevel += 1
        return true
    }

    // ── 十九·长线留存：赛季通行证（90 天双轨，GDD 十九） ──

    /** 确保赛季已开始（首次调用以 [nowMs] 为赛季起点）。 */
    private fun ensureSeasonStart(nowMs: Long) {
        if (seasonStartMs <= 0) seasonStartMs = nowMs
    }

    /**
     * 赛季推进：若已超出 [SeasonEngine.SEASON_LENGTH_DAYS] 天则滚动到下一赛季
     * （序号 +1、经验/已领清零、通行证失效）。返回是否发生滚动。
     * 平台应在启动 / 进入前台时调用 [nowMs]。
     */
    fun tickSeason(nowMs: Long): Boolean {
        ensureSeasonStart(nowMs)
        if (nowMs - seasonStartMs >= SeasonEngine.SEASON_LENGTH_DAYS * SeasonEngine.DAY_MS) {
            seasonIndex += 1
            seasonStartMs += SeasonEngine.SEASON_LENGTH_DAYS * SeasonEngine.DAY_MS
            seasonXp = 0
            seasonClaimedFree.clear()
            seasonClaimedPaid.clear()
            hasSeasonPass = false
            return true
        }
        return false
    }

    /** 当前赛季第几天（1..90；未开始 0；已结束 >90）。 */
    fun seasonDayNow(nowMs: Long): Int = SeasonEngine.dayIndex(seasonStartMs, nowMs)

    /** 当前赛季剩余天数（0 表示已结束）。 */
    fun seasonDaysRemaining(nowMs: Long): Int = SeasonEngine.daysRemaining(seasonStartMs, nowMs)

    /** 是否在赛季进行中。 */
    fun isSeasonInProgress(nowMs: Long): Boolean = SeasonEngine.inSeason(seasonStartMs, nowMs)

    /** 当前已解锁档位（0..[SeasonEngine.TIERS]）。 */
    fun currentSeasonTier(): Int = SeasonEngine.tierFromXp(seasonXp)

    /** 累加赛季经验（平台负责周期性 [tickSeason] 保证赛季有效）。 */
    fun addSeasonXp(amount: Int) { if (amount > 0) seasonXp += amount }

    // ── 十六·公平与稳健：疲劳值 / 单日收益封顶 ──

    /** 当前疲劳对应的收益倍率（×1.0 / ×0.5 / ×0.0）。 */
    fun incomeMul(): Double = FatigueEngine.incomeMul(fatigue)

    /** 累积疲劳（夹取 0..[FatigueEngine.MAX_FATIGUE]）。 */
    fun addFatigue(delta: Int) { fatigue = FatigueEngine.addFatigue(fatigue, delta) }

    /** 跨天重置疲劳为 0（由 [maybeResetDaily] 调用）。 */
    fun resetFatigueDaily() { fatigue = FatigueEngine.resetDaily() }

    // ── 活动（运营限时·日期区间触发·不进存档） ──

    /** 当日（lastDailyReset）所有生效活动。 */
    fun currentActivities(): List<Activity> = ActivityEngine.active(lastDailyReset)

    /** 收妖成功率乘子（生效活动乘性叠加）。 */
    fun activityCaptureMul(): Double = ActivityEngine.aggregate(currentActivities()).captureMul

    /** 经验获取乘子（生效活动乘性叠加）。 */
    fun activityExpMul(): Double = ActivityEngine.aggregate(currentActivities()).expMul

    /** 收益（香火/妖丹）乘子（生效活动乘性叠加）。 */
    fun activityIncomeMul(): Double = ActivityEngine.aggregate(currentActivities()).incomeMul

    // ── 二十·竞技与荣誉：排位赛段位天梯 ──

    /** 当前段位名（炼气/筑基/金丹/元婴/化神）。 */
    fun rankTierName(): String = RankEngine.tierName(rankTierIndex)

    /** 当前段位内积分进度比例 0.0..1.0（用于 UI 进度条）。 */
    fun rankProgress(): Double = (rankPoints.toDouble() / RankEngine.POINTS_TO_PROMOTE).coerceIn(0.0, 1.0)

    /**
     * 记录一场排位对局结果（胜负由平台侧 PvP 结算后回传）。
     * 更新段位/积分，若升段则按新段位发放荣誉奖励（仅升段一次发奖，
     * 连跳多段只按最终段位发奖，避免重复）。返回变更结果。
     */
    fun rankedBattle(won: Boolean): RankResult {
        val res = RankEngine.applyResult(rankTierIndex, rankPoints, won)
        rankTierIndex = res.tierIndex
        rankPoints = res.points
        if (res.promoted) {
            val reward = RankEngine.rankReward(res.tierIndex)
            incense += reward.incense
            lingyu += reward.lingyu
            yaodan += reward.yaodan
        }
        return res
    }

    /** 疲劳状态文案（供 UI 展示）。 */
    fun fatigueLabel(): String = FatigueEngine.label(fatigue)

    // ── 二十·竞技与荣誉：成就 / 勋章 / 称号（GDD 二十·展示型荣誉） ──

    /** 成就进度：来自任务系统 ACHIEVEMENT 分类（COMPLETED/CLAIMED 算达成）。 */
    fun achievementCount(): Int =
        quests.byCategory(QuestCategory.ACHIEVEMENT).count { it.state == QuestState.COMPLETED || it.state == QuestState.CLAIMED }
    fun achievementTotal(): Int = quests.byCategory(QuestCategory.ACHIEVEMENT).size

    /** 装配荣誉画像，供 [HonorEngine] 计算勋章/称号。 */
    fun honorProfile(): HonorEngine.HonorProfile = HonorEngine.HonorProfile(
        achievementCount = achievementCount(),
        achievementTotal = achievementTotal(),
        rankTierIndex = rankTierIndex,
        codexRatio = (codex.regionCompletion() * 100).toInt(),
        tribulationTotal = tribulationTotal,
    )

    /** 已解锁勋章。 */
    fun medals(): List<HonorEngine.Medal> = HonorEngine.unlockedMedals(honorProfile())

    /** 已解锁称号。 */
    fun titles(): List<HonorEngine.Title> = HonorEngine.unlockedTitles(honorProfile())

    /** 当前已解锁称号 id 集合。 */
    fun unlockedTitleIds(): Set<String> = titles().map { it.id }.toSet()

    /** 装备称号：仅当已解锁才成功，返回是否装备成功。 */
    fun equipTitle(id: String): Boolean {
        if (id == HonorEngine.NO_TITLE) { equippedTitleId = HonorEngine.NO_TITLE; return true }
        if (!HonorEngine.isUnlocked(id, honorProfile())) return false
        equippedTitleId = id
        return true
    }

    /** 当前展示称号名（未装备返回空串）。 */
    fun displayTitle(): String {
        if (equippedTitleId == HonorEngine.NO_TITLE) return HonorEngine.NO_TITLE
        return HonorEngine.TITLES.firstOrNull { it.id == equippedTitleId }?.name ?: HonorEngine.NO_TITLE
    }

    /** 购买赛季通行证（耗 [cost] 灵玉），成功返回 true；购买后已解锁付费档可补领。 */
    fun buySeasonPass(cost: Int = 300): Boolean {
        if (lingyu < cost) return false
        lingyu -= cost
        hasSeasonPass = true
        return true
    }

    /**
     * 领取某档位奖励：免费档始终可领；付费档需已购通行证。
     * 已领取的档位不再发放。返回本次实际发放（免费 / 付费分别标记）。
     */
    fun claimSeasonTier(tier: Int): SeasonClaimResult {
        val cur = currentSeasonTier()
        var claimedFree = false
        var claimedPaid = false
        var inc = 0
        var ly = 0
        var yd = 0
        if (tier in 1..cur && !seasonClaimedFree.contains(tier)) {
            val r = SeasonEngine.freeReward(tier)
            inc += r.incense
            yd += r.yaodan
            incense += r.incense
            yaodan += r.yaodan
            seasonClaimedFree.add(tier)
            claimedFree = true
        }
        if (tier in 1..cur && hasSeasonPass && !seasonClaimedPaid.contains(tier)) {
            val r = SeasonEngine.paidReward(tier)
            ly += r.lingyu
            yd += r.yaodan
            lingyu += r.lingyu
            yaodan += r.yaodan
            seasonClaimedPaid.add(tier)
            claimedPaid = true
        }
        return SeasonClaimResult(tier, claimedFree, claimedPaid, inc, ly, yd)
    }

    /** 一键领取所有已解锁且未领的档位（免费 + 付费），返回逐档结果。 */
    fun claimAllSeason(): List<SeasonClaimResult> {
        val cur = currentSeasonTier()
        return (1..cur).map { claimSeasonTier(it) }
    }

    // ── ② LBS / AR 收妖 ──

    /** AR 收妖当前可用状态（平台层应覆盖 ARCaptureController.status） */
    fun arStatus(): ARCaptureController.ARStatus = ARCaptureController.status()

    /** LBS 防作弊判定（平台在刷新灵脉 / 收妖前应先用此检查，异常时隐藏灵脉） */
    fun locationIntegrity(ctx: SpawnContext): IntegrityResult = LocationEngine.spawnIntegrity(ctx)

    /** 当前定位是否允许户外收妖：REJECTED（瞬移）禁止，其余允许（DEGRADED 仍允许但平台应降品质）。 */
    fun canCapture(ctx: SpawnContext): Boolean = locationIntegrity(ctx).tier != IntegrityTier.REJECTED

    // ── 信誉分（GDD 十六·公平与稳健）：与 GPS 防作弊闭环 ──

    /** 当前信誉分层。 */
    fun reputationTier(): ReputationTier = ReputationEngine.tier(reputation)

    /** 当前信誉分层中文标签。 */
    fun reputationLabel(): String = ReputationEngine.tierLabel(reputation)

    /** 罕见（仙/神）妖刷怪倍率（观察层 −30%）。 */
    fun lbsRareSpawnMul(): Double = ReputationEngine.rareSpawnMultiplier(reputation)

    /** 是否可进行户外 LBS 探索（受限层禁止，仅可入秘境）。 */
    fun canExploreLbs(): Boolean = ReputationEngine.canExploreLbs(reputation)

    /**
     * 上报一次 LBS 防作弊结果：异常则按类型扣信誉分。
     * 返回是否实际扣分（仅异常且未触底时扣分）。平台应在检测到异常时调用，
     * 注意去重（避免每帧重复扣分）——本切片由调用方控制调用频率。
     */
    fun reportIntegrity(result: IntegrityResult): Boolean {
        // 仅 REJECTED（瞬移）扣分；DEGRADED（降品质）按 GDD 不阻断、不扣分。
        if (result.tier != IntegrityTier.REJECTED) return false
        val after = ReputationEngine.applyPenalty(reputation, CheatKind.TELEPORT)
        val changed = after != reputation
        reputation = after
        return changed
    }

    // ── ③ 祈妖抽卡 ──

    /**
     * 祈妖抽卡：[ten]=true 走十连（540 灵玉，9 折），否则单抽（60 灵玉）。
     * 灵玉不足返回 null。重复妖转妖丹，新妖入队并推进图鉴；记录 GACHA 任务。
     * 返回抽卡结果列表（含稀有度与签文）。
     */
    fun pullGacha(ten: Boolean, pool: GachaEngine.PoolType = GachaEngine.PoolType.NORMAL): List<GachaEngine.GachaResult>? {
        val cost = if (ten) GachaEngine.TEN_LINGYU else GachaEngine.SINGLE_LINGYU
        if (lingyu < cost) return null
        lingyu -= cost
        val gstate = GachaEngine.GachaState(gachaNormalSinceShen, gachaLimitedSinceShen)
        val results = if (ten) GachaEngine.tenPull(gstate, pool, ::defaultRandom)
        else listOf(GachaEngine.pull(gstate, pool, ::defaultRandom))
        gachaNormalSinceShen = gstate.normalSinceShen
        gachaLimitedSinceShen = gstate.limitedSinceShen
        for (r in results) {
            val monster = MonsterPool.byId(r.monsterId)
            val isNew = !codex.hasAny(r.monsterId)
            if (isNew) {
                codex.catch(r.monsterId, false)
                val inst = MonsterInstance("i_${r.monsterId}_${nextInstSeq++}", r.monsterId, 1, 0, 0)
                owned[inst.instanceId] = inst
                if (team.size < 6) team.add(inst.instanceId)
            } else {
                yaodan += GachaEngine.DUPLICATE_YAODAN
            }
            quests.record(GameEvent.MonsterGachaPulled)
        }
        return results
    }

    // ── ③ 副本妖境 ──

    /** 通关副本：扣香火、记当日次数、发放奖励；条件不足返回 null */
    fun enterDungeon(defId: String, today: String): DungeonEngine.DungeonReward? {
        val def = DungeonLibrary.byId(defId) ?: return null
        if (!DungeonEngine.canEnter(def, DungeonEngine.DungeonRunState(dungeonEnteredToday), incense, today)) return null
        incense -= def.costIncense
        val reward = DungeonEngine.enter(def, DungeonEngine.DungeonRunState(dungeonEnteredToday), ::defaultRandom)
        dungeonEnteredToday[defId] = (dungeonEnteredToday[defId] ?: 0) + 1
        yaodan += (reward.yaodan * incomeMul() * sectIncomeMul() * activityIncomeMul()).toInt()   // 副本收益受疲劳封顶 + 宗门科技「资粮」+ 活动加成
        if (reward.bossCaptured && reward.bossMonsterId != null && !codex.hasAny(reward.bossMonsterId)) {
            codex.catch(reward.bossMonsterId, false)
            val inst = MonsterInstance("i_${reward.bossMonsterId}_${nextInstSeq++}", reward.bossMonsterId, 1, 0, 0)
            owned[inst.instanceId] = inst
            if (team.size < 6) team.add(inst.instanceId)
        }
        quests.record(GameEvent.DungeonEntered)
        addSeasonXp(SeasonEngine.XP_DUNGEON)   // 副本通关给赛季经验
        addFatigue(FatigueEngine.FATIGUE_DUNGEON)   // 副本累积疲劳
        return reward
    }

    // ── ③ 社交（三派 / 据点战） ──

    /** 加入派系（可后期转）：返回是否成功（首次或改换） */
    fun joinFaction(f: Faction): Boolean {
        faction = f.name
        completeTutorialStep(4)   // 引导步 ④：三派择门
        return true
    }

    /** 占领据点节点（每日限一次），成功则按「据点经营」科技积累宗门贡献 */
    fun captureNode(node: String, today: String): Boolean {
        // 防御性边界：未知节点（平台误传 / 过期数据 / 外挂注入）直接拒绝，
        // 避免写入悬空据点引用 + 仍触发虚假 +10% 攻击加成。零变更返回。
        if (node !in LocationEngine.STRONGHOLD_NODES) return false
        val st = SocialEngine.StrongholdState(strongholdNode, strongholdDay)
        val ok = SocialEngine.capture(st, node, today)
        strongholdNode = st.ownedNode
        strongholdDay = st.lastCaptureDay
        if (ok) {
            val gain = (SectTechEngine.CONTRIBUTION_PER_CAPTURE * (1.0 + sectStrongholdBonus())).toInt()
            addSectContribution(gain)
        }
        return ok
    }

    /** 当前据点攻击加成（占领 → 1.10） */
    fun strongholdAttackBonus(): Double =
        SocialEngine.attackBonus(strongholdNode != null)

    // ── 二十·宗门科技树（GDD 二十·公会建设 + 科技加成） ──

    /** 玩家所属派系（Faction 枚举），未择门返回 null */
    fun currentFaction(): Faction? =
        faction?.let { runCatching { Faction.valueOf(it) }.getOrNull() }

    /** 当前科技汇总加成（按已研习等级 + 派系汇总） */
    fun sectBonuses(): SectBonuses =
        SectTechEngine.bonuses(sectTech, currentFaction())

    /** 全队攻击增伤倍率（伤害公式乘子） */
    fun sectAttackMul(): Double = 1.0 + sectBonuses().attack
    /** 防御倍率（effectiveStatsOf 的 def） */
    fun sectDefenseMul(): Double = 1.0 + sectBonuses().defense
    /** 体力倍率（effectiveStatsOf 的 hp） */
    fun sectHpMul(): Double = 1.0 + sectBonuses().hp
    /** 收妖成功率倍率（CaptureEngine） */
    fun sectCaptureMul(): Double = 1.0 + sectBonuses().capture
    /** 经验倍率（gainExp） */
    fun sectExpMul(): Double = 1.0 + sectBonuses().exp
    /** 收益倍率（enterDungeon / claimSignIn） */
    fun sectIncomeMul(): Double = 1.0 + sectBonuses().income
    /** 据点经营增益（占领据点额外宗门贡献） */
    fun sectStrongholdBonus(): Double = sectBonuses().stronghold

    /** 某科技当前等级 */
    fun sectTechLevel(id: String): Int = sectTech[id] ?: 0

    /** 某科技是否可研习（供 UI 展示按钮可用态） */
    fun canResearchSectTech(id: String): Boolean =
        SectTechEngine.canResearch(id, sectTech, currentFaction(), sectContribution)

    /** 研习某科技：扣贡献、写等级；成功返回 true，条件不足返回 false */
    fun researchSectTech(id: String): Boolean {
        val res = SectTechEngine.research(id, sectTech, currentFaction(), sectContribution) ?: return false
        val (levels, contrib) = res
        sectTech.clear()
        sectTech.putAll(levels)
        sectContribution = contrib
        return true
    }

    /** 增加宗门贡献（平台在据点战 / 宗门活动时调用） */
    fun addSectContribution(amount: Int) { if (amount > 0) sectContribution += amount }

    // ── ③ 经济（引妖香商店） ──

    /** 购买引妖香：成功返回 (香火获得, 灵玉花费)，否则 null（灵玉不足/达日上限） */
    fun buyIncense(today: String): Pair<Int, Int>? {
        val st = ShopEngine.ShopState(shopIncenseBought, shopDay)
        val res = ShopEngine.buyIncense(st, today, lingyu) ?: return null
        shopIncenseBought = st.incenseBoughtToday
        shopDay = st.lastBuyDay
        incense += res.first
        lingyu -= res.second
        return res
    }

    /** 领取任务奖励并入账；返回奖励文案，无可领返回 null */
    fun claimQuest(id: String): String? {
        val reward: QuestReward = quests.claim(id) ?: return null
        incense += reward.incense
        lingyu += reward.lingyu
        yaodan += reward.yaodan
        return reward.text
    }

    fun teamMonsters(): List<MonsterInstance> =
        team.mapNotNull { owned[it] }

    /** 当前出战队伍激活的阵容羁绊（GDD 七·队伍组合技）。 */
    fun teamSynergies(): List<Synergy> {
        val monsters = teamMonsters().mapNotNull { MonsterPool.byId(it.defId) }
        return FormationEngine.evaluate(monsters)
    }

    /** 当前出战队伍的全队攻击增伤倍率（用于 [BattleSession]/[BattleEngine.damage]）。 */
    fun teamAttackMultiplier(): Double = FormationEngine.teamAttackMultiplier(teamSynergies())

    /** 当前地形五行偏向（复用 [LocationEngine.regionElementBias]），无地形返回 null。 */
    fun currentTerrainBias(): Element? = currentTerrain?.let { LocationEngine.regionElementBias(it) }

    /**
     * 进入战斗时传给 [BattleSession] 的玩家全队攻击增伤倍率：
     * 阵容羁绊 × 阵法风水增益（缝合 #85 阵法风水攻击增益，此前仅作报告值）。
     */
    fun battleTeamMul(): Double = teamAttackMultiplier() * (1.0 + homesteadAttackBonus())

    /** 设置天气（平台层接入真实天气后调用，本地也可调试指定）。 */
    fun setWeather(w: Weather) { currentWeather = w }

    /** 设置地形（平台层接入地图 SDK 后调用，本地也可调试指定）。 */
    fun setTerrain(t: RegionTheme?) { currentTerrain = t }

    /** 某妖（按 defId）当前出战 4 技能可触发的连携组合技（GDD 七·连携）。 */
    fun monsterCombos(defId: String): List<Combo> {
        val m = MonsterPool.byId(defId) ?: return emptyList()
        return ComboEngine.availableCombos(m.skills.take(4).map { it.id })
    }

    // ---- 持久化：快照 / 恢复 / 每日重置 ----

    /** 导出当前进度为可序列化存档 */
    fun toSaveData(): SaveData = SaveData(
        caughtRegion = codex.snapshotRegion().toList(),
        caughtSecret = codex.snapshotSecret().toList(),
        team = team.toList(),
        incense = incense,
        lingyu = lingyu,
        yaodan = yaodan,
        secretRealmLeft = secretRealmLeft,
        lastDailyReset = lastDailyReset,
        quests = quests.snapshot(),
        owned = owned.values.toList(),
        nextInstSeq = nextInstSeq,
        equipment = equipment.toList(),
        nextEquipSeq = nextEquipSeq,
        equipped = equipped.mapValues { it.value.toMap() },
        gachaNormalSinceShen = gachaNormalSinceShen,
        gachaLimitedSinceShen = gachaLimitedSinceShen,
        dungeonEntered = dungeonEnteredToday.toMap(),
        faction = faction,
        strongholdNode = strongholdNode,
        strongholdDay = strongholdDay,
        shopIncenseBought = shopIncenseBought,
        shopDay = shopDay,
        reputation = reputation,
        sectTech = sectTech.toMap(),
        sectContribution = sectContribution,
        tutorialStep = tutorialStep,
        tutorialSkipped = tutorialSkipped,
        signInDay = signInDay,
        lastSignIn = lastSignIn,
        homesteadLevel = homesteadLevel,
        lastHomesteadCollect = lastHomesteadCollect,
        seasonStartMs = seasonStartMs,
        seasonXp = seasonXp,
        hasSeasonPass = hasSeasonPass,
        seasonClaimedFree = seasonClaimedFree.toList(),
        seasonClaimedPaid = seasonClaimedPaid.toList(),
        seasonIndex = seasonIndex,
        fatigue = fatigue,
        rankTierIndex = rankTierIndex,
        rankPoints = rankPoints,
        tribulationTotal = tribulationTotal,
        equippedTitleId = equippedTitleId,
    )

    /** 从存档恢复进度（妖怪定义、任务定义仍取自各自的 Library，仅恢复可变进度） */
    fun applySaveData(save: SaveData) {
        save.caughtRegion.forEach { codex.catch(it, false) }
        save.caughtSecret.forEach { codex.catch(it, true) }
        owned.clear()
        save.owned.forEach { owned[it.instanceId] = it }
        team.clear()
        team.addAll(save.team.filter { owned.containsKey(it) })
        incense = save.incense
        lingyu = save.lingyu
        yaodan = save.yaodan
        secretRealmLeft = save.secretRealmLeft
        lastDailyReset = save.lastDailyReset
        nextInstSeq = save.nextInstSeq
        equipment.clear()
        equipment.addAll(save.equipment)
        nextEquipSeq = save.nextEquipSeq
        equipped.clear()
        for ((mid, slots) in save.equipped) equipped[mid] = slots.toMutableMap()
        gachaNormalSinceShen = save.gachaNormalSinceShen
        gachaLimitedSinceShen = save.gachaLimitedSinceShen
        dungeonEnteredToday.clear()
        dungeonEnteredToday.putAll(save.dungeonEntered)
        faction = save.faction
        strongholdNode = save.strongholdNode
        strongholdDay = save.strongholdDay
        shopIncenseBought = save.shopIncenseBought
        shopDay = save.shopDay
        reputation = save.reputation
        sectTech.clear()
        sectTech.putAll(save.sectTech)
        sectContribution = save.sectContribution
        tutorialStep = save.tutorialStep
        tutorialSkipped = save.tutorialSkipped
        signInDay = save.signInDay
        lastSignIn = save.lastSignIn
        homesteadLevel = save.homesteadLevel
        lastHomesteadCollect = save.lastHomesteadCollect
        seasonStartMs = save.seasonStartMs
        seasonXp = save.seasonXp
        hasSeasonPass = save.hasSeasonPass
        seasonClaimedFree = save.seasonClaimedFree.toMutableSet()
        seasonClaimedPaid = save.seasonClaimedPaid.toMutableSet()
        seasonIndex = save.seasonIndex
        fatigue = save.fatigue
        rankTierIndex = save.rankTierIndex
        rankPoints = save.rankPoints
        tribulationTotal = save.tribulationTotal
        equippedTitleId = save.equippedTitleId
        quests.applySnapshot(save.quests)
    }

    /**
     * 跨天检测：若 today 与上次重置日不同，则重置每日任务并把秘境次数回满。
     * 首启（lastDailyReset 为空）也会触发一次，使初始状态规整。
     */
    fun maybeResetDaily(today: String) {
        if (DailyResetEngine.isCrossDay(lastDailyReset, today)) {
            quests.resetDaily()
            secretRealmLeft = 3
            dungeonEnteredToday.clear()
            if (shopDay != today) { shopIncenseBought = 0; shopDay = today }
            if (strongholdDay != today) { strongholdNode = null; strongholdDay = today }
            reputation = ReputationEngine.dailyRecover(reputation)   // 每日缓慢恢复 +1
            fatigue = FatigueEngine.resetDaily()   // 疲劳每日重置
            lastDailyReset = today
        }
    }
}
