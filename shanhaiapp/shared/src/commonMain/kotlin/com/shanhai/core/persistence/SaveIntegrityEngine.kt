package com.shanhai.core.persistence

import com.shanhai.core.data.EquipmentLibrary
import com.shanhai.core.data.MonsterPool
import com.shanhai.core.logic.SectTechEngine
import com.shanhai.core.logic.CultivationEngine
import com.shanhai.core.logic.EquipmentEngine
import com.shanhai.core.logic.FatigueEngine
import com.shanhai.core.logic.RankEngine
import com.shanhai.core.logic.ReputationEngine
import com.shanhai.core.logic.TalentEngine
import com.shanhai.core.model.MonsterInstance
import com.shanhai.core.model.OwnedEquip
import com.shanhai.core.model.SubStat
import com.shanhai.core.model.TalentBranch
import com.shanhai.core.quest.QuestLibrary

/**
 * 存档语义完整性校验 + 自愈（GDD·稳健性收口）。
 *
 * 现状：项目此前**没有任何存档语义校验**——旧版本迁移边界、云同步合并异常、外部篡改，
 * 都可能产生「合法 JSON 但非法游戏状态」：例如负货币、defId 指向不存在的妖、
 * 出战队伍引用已遗失实例、等级/突破越界。这些会在 UI 按 id 查表时抛空崩溃。
 *
 * 本引擎提供纯函数能力：
 *  - [validate]：收集全部不一致项（[Violation]），**不修改**原档；
 *  - [isClean]：是否无违规；
 *  - [sanitize]：在「不动定义、不丢进度的前提下修复安全子集」（钳制标量 / 丢弃悬空引用 /
 *    钳制养成越界 / 去重），使修复后的存档通过 [validate]。
 *
 * 与 [CloudSyncEngine] 搭配：云合并后先 `sanitize` 再落盘，避免坏档污染本机。
 * 无网络 / 账号 / 随机依赖。
 */
object SaveIntegrityEngine {

    /** 出战队伍上限（妖 ≤ 6）。 */
    const val TEAM_CAP = 6

    /** 已知派系（与 SocialEngine / Faction 对齐）。 */
    private val FACTIONS = setOf("maoshan", "longhushan", "gezao")

    /** 单条违规（稳定 code 便于程序判定，message 供展示）。 */
    data class Violation(val code: String, val message: String)

    // ── 构建期一次性取查询表 ──
    private val monsterIds: Set<String> = MonsterPool.all.map { it.id }.toSet()
    private val questIds: Set<String> = QuestLibrary.all.map { it.id }.toSet()
    private val equipmentIds: Set<String> = EquipmentLibrary.all.map { it.id }.toSet()
    private val sectTechMax: Map<String, Int> = SectTechEngine.NODES.associate { it.id to it.maxLevel }

    /**
     * 校验整份存档，返回所有违规（可能为空）。不修改入参。
     * 设计为「全量收集」，便于一次性呈现所有问题而非逐个拦截。
     */
    fun validate(save: SaveData): List<Violation> {
        val v = mutableListOf<Violation>()
        val add = { code: String, msg: String -> v.add(Violation(code, msg)) }

        // 版本（迁移暂未实现，仅提示）
        if (save.version != SaveData.VERSION) add("VERSION", "存档版本 ${save.version} != 当前 ${SaveData.VERSION}")

        // 货币非负
        if (save.incense < 0) add("NEG_INCENSE", "香火为负 (${save.incense})")
        if (save.lingyu < 0) add("NEG_LINGYU", "灵玉为负 (${save.lingyu})")
        if (save.yaodan < 0) add("NEG_YAODAN", "妖丹为负 (${save.yaodan})")

        // 标量边界
        if (save.fatigue !in 0..FatigueEngine.MAX_FATIGUE)
            add("FATIGUE_RANGE", "疲劳 ${save.fatigue} 越界 [0,${FatigueEngine.MAX_FATIGUE}]")
        if (save.reputation !in ReputationEngine.MIN_SCORE..ReputationEngine.MAX_SCORE)
            add("REP_RANGE", "信誉分 ${save.reputation} 越界 [${ReputationEngine.MIN_SCORE},${ReputationEngine.MAX_SCORE}]")
        if (save.rankTierIndex !in RankEngine.MIN_TIER_INDEX..RankEngine.MAX_TIER_INDEX)
            add("RANK_TIER", "段位索引 ${save.rankTierIndex} 越界 [${RankEngine.MIN_TIER_INDEX},${RankEngine.MAX_TIER_INDEX}]")
        if (save.rankPoints !in 0 until RankEngine.POINTS_TO_PROMOTE)
            add("RANK_POINTS", "段位积分 ${save.rankPoints} 越界 [0,${RankEngine.POINTS_TO_PROMOTE - 1}]")
        if (save.tribulationTotal < 0) add("NEG_TRIB", "渡劫次数负 (${save.tribulationTotal})")
        if (save.secretRealmLeft < 0) add("NEG_SECRET", "秘境次数负 (${save.secretRealmLeft})")
        if (save.homesteadLevel < 0) add("NEG_HOME", "洞府等级负 (${save.homesteadLevel})")
        if (save.seasonXp < 0) add("NEG_SEASONXP", "赛季经验负 (${save.seasonXp})")
        if (save.signInDay < 0) add("NEG_SIGNIN", "签到天数负 (${save.signInDay})")
        if (save.tutorialStep < 0) add("NEG_TUT", "引导步序负 (${save.tutorialStep})")
        if (save.nextInstSeq < 1) add("SEQ_INST", "实例序号 ${save.nextInstSeq} < 1")
        if (save.nextEquipSeq < 1) add("SEQ_EQUIP", "装备序号 ${save.nextEquipSeq} < 1")

        // 拥有妖实例
        val ownedIds = mutableSetOf<String>()
        var dupWarned = false
        for (inst in save.owned) {
            if (inst.instanceId.isEmpty()) add("INST_ID_EMPTY", "存在空 instanceId 的妖实例")
            if (inst.instanceId in ownedIds) {
                if (!dupWarned) { add("INST_DUP", "妖实例 instanceId 重复: ${inst.instanceId}"); dupWarned = true }
            } else {
                ownedIds.add(inst.instanceId)
            }
            val defOk = inst.defId in monsterIds
            if (!defOk) add("INST_DEF", "妖实例 ${inst.instanceId} 的 defId 不存在: ${inst.defId}")

            // 突破 / 等级上限（依赖 defId 时取真实稀有度；否则按默认保守钳制）
            val bt = inst.breakthrough.coerceIn(0, CultivationEngine.MAX_BREAKTHROUGH)
            val cap = CultivationEngine.BASE_LEVEL_CAP + bt * CultivationEngine.LEVEL_CAP_PER_BREAK
            if (inst.level < 1) add("INST_LVL", "妖实例 ${inst.instanceId} 等级 ${inst.level} < 1")
            else if (inst.level > cap) add("INST_LVL", "妖实例 ${inst.instanceId} 等级 ${inst.level} 超上限 $cap")
            if (inst.breakthrough < 0 || inst.breakthrough > CultivationEngine.MAX_BREAKTHROUGH)
                add("INST_BREAK", "妖实例 ${inst.instanceId} 突破 ${inst.breakthrough} 越界 [0,${CultivationEngine.MAX_BREAKTHROUGH}]")
            if (inst.exp < 0) add("INST_EXP", "妖实例 ${inst.instanceId} 经验负 (${inst.exp})")

            if (defOk) {
                val rarity = MonsterPool.byId(inst.defId)!!.rarity
                val maxTrib = CultivationEngine.maxTribulations(rarity)
                if (inst.tribulations < 0 || inst.tribulations > maxTrib)
                    add("INST_TRIB", "妖实例 ${inst.instanceId} 渡劫 ${inst.tribulations} 越界 [0,$maxTrib]")
            }

            // 天赋
            var allocated = 0
            for ((branch, layers) in inst.talents) {
                if (branch !in TalentBranch.values())
                    add("INST_TALENT_BRANCH", "妖实例 ${inst.instanceId} 天赋分支非法: $branch")
                if (layers < 0 || layers > TalentEngine.MAX_LAYER)
                    add("INST_TALENT_LAYER", "妖实例 ${inst.instanceId} 天赋层 $layers 越界 [0,${TalentEngine.MAX_LAYER}]")
                allocated += layers.coerceAtLeast(0)
            }
            val talentCap = TalentEngine.BASE_TALENT + bt * TalentEngine.TALENT_PER_BREAK
            if (allocated > talentCap)
                add("INST_TALENT_CAP", "妖实例 ${inst.instanceId} 天赋点 $allocated 超容量 $talentCap")
        }

        // 出战队伍：引用已拥有 + 数量合规
        val teamUnknown = save.team.filter { it !in ownedIds }
        if (teamUnknown.isNotEmpty()) add("TEAM_REF", "出战队伍引用未知实例: $teamUnknown")
        if (save.team.size > TEAM_CAP) add("TEAM_CAP", "出战队伍 ${save.team.size} 超上限 $TEAM_CAP")

        // 装备实例唯一 + 下限
        val equipIds = mutableSetOf<String>()
        var eqDup = false
        for (eq in save.equipment) {
            if (eq.instanceId in equipIds) {
                if (!eqDup) { add("EQUIP_DUP", "装备 instanceId 重复: ${eq.instanceId}"); eqDup = true }
            } else {
                equipIds.add(eq.instanceId)
            }
            if (eq.level < 1) add("EQUIP_LVL", "装备 ${eq.instanceId} 祭炼等级 ${eq.level} < 1")
            if (eq.stars < 1) add("EQUIP_STAR", "装备 ${eq.instanceId} 星级 ${eq.stars} < 1")
            if (eq.tier < 1) add("EQUIP_TIER", "装备 ${eq.instanceId} 突破阶 ${eq.tier} < 1")
            if (eq.equipId !in equipmentIds) add("EQUIP_DEF", "装备 ${eq.instanceId} 的 equipId 不存在: ${eq.equipId}")
            // 上界钳制（与 sanitize 对应）：星级/突破阶/副属性 越界会被坏档/外挂用来过度膨胀战力
            val eqDef = EquipmentLibrary.byId(eq.equipId)
            if (eqDef != null) {
                if (eq.stars > EquipmentEngine.MAX_STAR)
                    add("EQUIP_STAR_HI", "装备 ${eq.instanceId} 星级 ${eq.stars} 超上限 ${EquipmentEngine.MAX_STAR}")
                val maxT = EquipmentEngine.maxTier(eqDef.rarity)
                if (eq.tier > maxT)
                    add("EQUIP_TIER_HI", "装备 ${eq.instanceId} 突破阶 ${eq.tier} 超上限 $maxT")
            }
            if (eq.substats.size > EquipmentEngine.MAX_SUBSTATS)
                add("EQUIP_SUBN", "装备 ${eq.instanceId} 副属性 ${eq.substats.size} 超上限 ${EquipmentEngine.MAX_SUBSTATS}")
            for (s in eq.substats) if (s.value > EquipmentEngine.SUB_CAP)
                add("EQUIP_SUBVAL", "装备 ${eq.instanceId} 副属性 ${s.stat} 值 ${s.value} 超上限 ${EquipmentEngine.SUB_CAP}")
        }

        // 装配映射：妖 / 装备 必须存在
        val badEquipRef = mutableListOf<String>()
        for ((instId, slots) in save.equipped) {
            if (instId !in ownedIds) { badEquipRef.add("妖 $instId 不存在"); continue }
            for ((_, eqId) in slots) if (eqId !in equipIds) badEquipRef.add("装备 $eqId 不存在")
        }
        if (badEquipRef.isNotEmpty()) add("EQUIP_REF", "装配引用悬空: $badEquipRef")

        // 任务：id 必须存在，进度非负
        val badQuest = save.quests.filter { it.id !in questIds }
        if (badQuest.isNotEmpty()) add("QUEST_ID", "任务 id 不存在: ${badQuest.map { it.id }}")
        for (q in save.quests) if (q.current.any { it < 0 }) { add("QUEST_NEG", "任务 ${q.id} 进度含负值"); break }

        // 图鉴：id 必须存在
        val badCodex = (save.caughtRegion + save.caughtSecret).filter { it !in monsterIds }
        if (badCodex.isNotEmpty()) add("CODEX_ID", "图鉴 id 不存在: $badCodex")

        // 派系
        if (save.faction != null && save.faction !in FACTIONS) add("FACTION", "未知派系: ${save.faction}")

        // 宗门科技：节点存在 + 等级 ≥ 0 且 ≤ 节点上限
        val badTech = save.sectTech.filterKeys { it !in sectTechMax }
        if (badTech.isNotEmpty()) add("SECTTECH_ID", "宗门科技节点不存在: ${badTech.keys}")
        for ((id, lv) in save.sectTech) {
            if (lv < 0) { add("SECTTECH_NEG", "宗门科技等级含负值"); break }
            val mx = sectTechMax[id]
            if (mx != null && lv > mx) add("SECTTECH_LVL", "宗门科技 $id 等级 $lv 超节点上限 $mx")
        }

        return v
    }

    /** 是否无违规。 */
    fun isClean(save: SaveData): Boolean = validate(save).isEmpty()

    /**
     * 自愈：修复安全子集，返回新的合法存档（不动定义、尽量保留进度）。
     * 策略：钳制标量 / 丢弃悬空引用 / 钳制养成越界 / 去重；不凭空补数据。
     */
    fun sanitize(save: SaveData): SaveData {
        // 货币非负
        val incense = save.incense.coerceAtLeast(0)
        val lingyu = save.lingyu.coerceAtLeast(0)
        val yaodan = save.yaodan.coerceAtLeast(0)

        // 标量钳制
        val fatigue = save.fatigue.coerceIn(0, FatigueEngine.MAX_FATIGUE)
        val reputation = save.reputation.coerceIn(ReputationEngine.MIN_SCORE, ReputationEngine.MAX_SCORE)
        val rankTierIndex = save.rankTierIndex.coerceIn(RankEngine.MIN_TIER_INDEX, RankEngine.MAX_TIER_INDEX)
        val rankPoints = save.rankPoints.coerceIn(0, RankEngine.POINTS_TO_PROMOTE - 1)
        val tribulationTotal = save.tribulationTotal.coerceAtLeast(0)
        val secretRealmLeft = save.secretRealmLeft.coerceAtLeast(0)
        val homesteadLevel = save.homesteadLevel.coerceAtLeast(0)
        val seasonXp = save.seasonXp.coerceAtLeast(0)
        val signInDay = save.signInDay.coerceAtLeast(0)
        val tutorialStep = save.tutorialStep.coerceAtLeast(0)
        val nextInstSeq = save.nextInstSeq.coerceAtLeast(1)
        val nextEquipSeq = save.nextEquipSeq.coerceAtLeast(1)

        // 拥有妖：丢弃 defId 不存在 / instanceId 空 / 重复；钳制养成越界
        val seenInst = mutableSetOf<String>()
        val owned = mutableListOf<MonsterInstance>()
        for (inst in save.owned) {
            if (inst.instanceId.isEmpty()) continue
            if (inst.defId !in monsterIds) continue
            if (inst.instanceId in seenInst) continue
            seenInst.add(inst.instanceId)
            owned.add(sanitizeInstance(inst))
        }
        val ownedIdSet = owned.map { it.instanceId }.toSet()

        // 装备：去重 + 下限钳制
        val seenEq = mutableSetOf<String>()
        val equipment = mutableListOf<OwnedEquip>()
        for (eq in save.equipment) {
            if (eq.equipId !in equipmentIds) continue   // 丢弃 defId 不存在的死装备（自愈）
            if (eq.instanceId in seenEq) continue
            seenEq.add(eq.instanceId)
            // 上界钳制（与 validate 对应）：星级/突破阶/副属性 越界 → 钳到合法上限，杜绝坏档过度膨胀战力
            val eqDef = EquipmentLibrary.byId(eq.equipId)!!
            val maxT = EquipmentEngine.maxTier(eqDef.rarity)
            val lvlCap = eqDef.maxLevel + (minOf(eq.tier, maxT) - 1) * EquipmentEngine.TIER_LEVEL_BONUS
            val subs = eq.substats
                .map { s -> SubStat(s.stat, s.value.coerceIn(0, EquipmentEngine.SUB_CAP)) }
                .take(EquipmentEngine.MAX_SUBSTATS)
            equipment.add(eq.copy(
                level = eq.level.coerceIn(1, lvlCap),
                stars = eq.stars.coerceIn(1, EquipmentEngine.MAX_STAR),
                tier = eq.tier.coerceIn(1, maxT),
                substats = subs,
            ))
        }
        val equipIdSet = equipment.map { it.instanceId }.toSet()

        // 队伍：仅保留已拥有，去重，截断到上限
        val team = save.team.filter { it in ownedIdSet }.distinct().take(TEAM_CAP)

        // 装配：仅保留合法引用（妖存在 + 装备存在 + 非空）
        val equipped = save.equipped
            .filterKeys { it in ownedIdSet }
            .mapValues { (_, slots) -> slots.filterValues { eqId -> eqId in equipIdSet } }
            .filterValues { it.isNotEmpty() }

        // 任务：丢弃未知 id，进度钳制 ≥ 0
        val quests = save.quests
            .filter { it.id in questIds }
            .map { qs -> qs.copy(current = qs.current.map { c -> c.coerceAtLeast(0) }) }

        // 图鉴：仅保留合法 id
        val caughtRegion = save.caughtRegion.filter { it in monsterIds }
        val caughtSecret = save.caughtSecret.filter { it in monsterIds }

        // 派系：未知 → nil
        val faction = if (save.faction in FACTIONS) save.faction else null

        // 宗门科技：丢弃未知节点，等级钳制到 [0, 节点上限]
        // （此前只钳下界，上界缺失会让越界等级残留并触发 SECTTECH_LVL，破坏自愈全满性）
        val sectTech = save.sectTech
            .filterKeys { it in sectTechMax }
            .mapValues { (id, lv) -> lv.coerceIn(0, sectTechMax[id] ?: 0) }

        // 版本归一为当前版本：迁移尚未实现，对未知/旧版本档统一盖章为当前版本，
        // 再修复其余字段（保证 sanitize 为不动点且对任意输入全满）。
        val version = SaveData.VERSION

        return save.copy(
            version = version,
            incense = incense, lingyu = lingyu, yaodan = yaodan,
            fatigue = fatigue, reputation = reputation,
            rankTierIndex = rankTierIndex, rankPoints = rankPoints,
            tribulationTotal = tribulationTotal, secretRealmLeft = secretRealmLeft,
            homesteadLevel = homesteadLevel, seasonXp = seasonXp,
            signInDay = signInDay, tutorialStep = tutorialStep,
            nextInstSeq = nextInstSeq, nextEquipSeq = nextEquipSeq,
            owned = owned, equipment = equipment, team = team, equipped = equipped,
            quests = quests, caughtRegion = caughtRegion, caughtSecret = caughtSecret,
            faction = faction, sectTech = sectTech,
        )
    }

    /** 单只妖实例养成越界钳制（前置：defId 已在 MonsterPool 中）。 */
    private fun sanitizeInstance(inst: MonsterInstance): MonsterInstance {
        val bt = inst.breakthrough.coerceIn(0, CultivationEngine.MAX_BREAKTHROUGH)
        val cap = CultivationEngine.BASE_LEVEL_CAP + bt * CultivationEngine.LEVEL_CAP_PER_BREAK
        val lvl = inst.level.coerceIn(1, cap)
        val maxExp = if (lvl < cap) CultivationEngine.expForLevel(lvl) - 1
        else CultivationEngine.expForLevel((cap - 1).coerceAtLeast(1))
        val exp = inst.exp.coerceIn(0, maxExp.coerceAtLeast(0))
        val rarity = MonsterPool.byId(inst.defId)!!.rarity
        val trib = inst.tribulations.coerceIn(0, CultivationEngine.maxTribulations(rarity))
        // 天赋：仅保留合法分支、层钳制、容量内（超出从最高层逐层回收）
        val talentCap = TalentEngine.BASE_TALENT + bt * TalentEngine.TALENT_PER_BREAK
        val cleaned = inst.talents
            .filterKeys { it in TalentBranch.values() }
            .mapValues { (_, n) -> n.coerceIn(0, TalentEngine.MAX_LAYER) }
            .toMutableMap()
        var allocated = cleaned.values.sum()
        val order = cleaned.entries.sortedByDescending { it.value }.map { it.key }.toMutableList()
        while (allocated > talentCap && order.isNotEmpty()) {
            val b = order.first()
            val cur = cleaned[b] ?: 0
            if (cur <= 0) { order.removeAt(0); continue }
            cleaned[b] = cur - 1
            allocated -= 1
        }
        return inst.copy(level = lvl, exp = exp, breakthrough = bt, tribulations = trib, talents = cleaned)
    }
}
