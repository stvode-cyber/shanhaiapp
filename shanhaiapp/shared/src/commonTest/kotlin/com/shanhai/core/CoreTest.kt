package com.shanhai.core

import com.shanhai.core.data.MonsterPool
import com.shanhai.core.data.SkillLibrary
import com.shanhai.core.logic.BattleEngine
import com.shanhai.core.logic.CaptureEngine
import com.shanhai.core.logic.Combatant
import com.shanhai.core.logic.CultivationEngine
import com.shanhai.core.logic.EquipmentEngine
import com.shanhai.core.logic.GachaEngine
import com.shanhai.core.logic.FormationEngine
import com.shanhai.core.logic.FormationTemplates
import com.shanhai.core.logic.FusionEngine
import com.shanhai.core.logic.GameState
import com.shanhai.core.logic.FormationTemplate
import com.shanhai.core.logic.SynergyId
import com.shanhai.core.model.*
import com.shanhai.core.data.EquipmentLibrary
import com.shanhai.core.quest.*

import com.shanhai.core.logic.Codex
import com.shanhai.core.logic.toCombatant
import com.shanhai.core.persistence.SaveCodec
import kotlin.math.abs
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** 固定随机源，保证伤害/收妖测试可断言精确值 */
private class FixedRandom(private val value: Double) : Random() {
    override fun nextDouble(): Double = value
    override fun nextBits(bitCount: Int): Int = 0
}

class CoreTest {

    @Test
    fun `五行相克矩阵正确`() {
        assertEquals(1.6, Element.multiplier(Element.METAL, Element.WOOD))
        assertEquals(0.6, Element.multiplier(Element.WOOD, Element.METAL))
        assertEquals(1.0, Element.multiplier(Element.METAL, Element.METAL))
        assertEquals(1.6, Element.multiplier(Element.WATER, Element.FIRE))
        assertEquals(1.6, Element.multiplier(Element.THUNDER, Element.WATER))
    }

    @Test
    fun `类别克制环正确`() {
        assertEquals(1.3, MonsterCategory.multiplier(MonsterCategory.YAO, MonsterCategory.JING))
        assertEquals(1.0 / 1.3, MonsterCategory.multiplier(MonsterCategory.JING, MonsterCategory.YAO))
        assertEquals(1.3, MonsterCategory.multiplier(MonsterCategory.JING, MonsterCategory.GUAI))
    }

    @Test
    fun `成长率随稀有度放大属性`() {
        val fan = MonsterPool.byId("jing_saozhou")!!   // 凡
        val shen = MonsterPool.byId("guai_nianshou")!! // 神
        assertTrue(shen.statsAtLevel(1).hp > fan.statsAtLevel(1).hp)
        assertEquals(1.5, Rarity.SHEN.growth)
    }

    @Test
    fun test_57() {
        val attacker = MonsterPool.byId("yao_she")!!.toCombatant(1, SkillLibrary.pick(listOf("s_jingtao")))
        val defender = MonsterPool.byId("jing_denglong")!!.toCombatant(1, emptyList())
        val skill = SkillLibrary.get("s_jingtao") // 水行 75 威力
        val res = BattleEngine.damage(attacker, defender, skill, FixedRandom(0.95))
        // 46/42 * 75 * 1.0 * 1.6 * 1.3 * 1.0 * 0.995 ≈ 170
        assertEquals(155, res.damage)
        assertEquals(1.6, res.elementMult)
        assertEquals(false, res.crit)
    }

    @Test
    fun `自动选最优克制技`() {
        val attacker = MonsterPool.byId("yao_long")!!.toCombatant(10, SkillLibrary.pick(listOf("s_hantan", "s_jingtao")))
        val defender = MonsterPool.byId("jing_denglong")!!.toCombatant(10, emptyList())
        val best = BattleEngine.bestCounterSkill(attacker, defender)
        // 水行对火均 ×1.6，寒潭玄冰威力更高 → 应选寒潭玄冰
        assertEquals("寒潭玄冰", best?.name)
    }

    @Test
    fun `收妖概率：精度越高越高，神且无助最低仍有保底`() {
        val best = CaptureEngine.successProbability(1.0, Rarity.FAN, true)
        assertEquals(0.98, best, 0.0001)
        val worst = CaptureEngine.successProbability(0.0, Rarity.SHEN, false)
        assertEquals(0.06, worst, 0.0001)
        assertTrue(best > worst)
    }

    @Test
    fun test_87() {
        val alwaysWin = CaptureEngine.resolve(1.0, Rarity.FAN, true, rng = { 0.0 })
        assertTrue(alwaysWin.success)
        val alwaysLose = CaptureEngine.resolve(1.0, Rarity.FAN, true, rng = { 1.0 })
        assertEquals(false, alwaysLose.success)
    }

    @Test
    fun `图鉴完成度随收妖增长`() {
        val codex = Codex(MonsterPool.all)
        assertEquals(0.0, codex.regionCompletion())
        codex.catch("jing_saozhou")
        assertEquals(1.0 / MonsterPool.all.size, codex.regionCompletion())
        assertTrue(codex.hasInRegion("jing_saozhou"))
    }

    @Test
    fun `任务进度随收妖事件递增并触发完成`() {
        val gs = GameState()
        assertEquals(0, gs.quests.byCategory(QuestCategory.ACHIEVEMENT)
            .first { it.def.id == "a_catch10" }.current[0])
        repeat(10) { gs.catchMonster("jing_saozhou") }
        val q = gs.quests.byCategory(QuestCategory.ACHIEVEMENT).first { it.def.id == "a_catch10" }
        assertEquals(10, q.current[0])
        assertEquals(QuestState.COMPLETED, q.state)
    }

    @Test
    fun `用指定行获胜完成任务（五行初识·火）`() {
        val gs = GameState()
        val before = gs.quests.byCategory(QuestCategory.STORY).first { it.def.id == "s_fire_win" }
        assertEquals(QuestState.ACTIVE, before.state)
        gs.onBattleWon(Element.FIRE)
        val after = gs.quests.byCategory(QuestCategory.STORY).first { it.def.id == "s_fire_win" }
        assertEquals(QuestState.COMPLETED, after.state)
        // 用错行不应完成
        val gs2 = GameState()
        gs2.onBattleWon(Element.WATER)
        assertEquals(QuestState.ACTIVE, gs2.quests.byCategory(QuestCategory.STORY)
            .first { it.def.id == "s_fire_win" }.state)
    }

    @Test
    fun `领取任务奖励入账货币`() {
        val gs = GameState()
        repeat(3) { gs.catchMonster("jing_saozhou") } // 完成 今日·收妖（香火+30）
        val daily = gs.quests.byCategory(QuestCategory.DAILY).first { it.def.id == "d_catch3" }
        assertEquals(QuestState.COMPLETED, daily.state)
        val incenseBefore = gs.incense
        val text = gs.claimQuest("d_catch3")
        assertEquals("香火+30", text)
        assertEquals(incenseBefore + 30, gs.incense)
        assertEquals(QuestState.CLAIMED, gs.quests.byCategory(QuestCategory.DAILY)
            .first { it.def.id == "d_catch3" }.state)
        // 重复领取无效
        assertEquals(null, gs.claimQuest("d_catch3"))
    }

    @Test
    fun `每日目标重置归零`() {
        val gs = GameState()
        repeat(3) { gs.catchMonster("jing_saozhou") }
        gs.quests.resetDaily()
        val daily = gs.quests.byCategory(QuestCategory.DAILY).first { it.def.id == "d_catch3" }
        assertEquals(0, daily.current[0])
        assertEquals(QuestState.ACTIVE, daily.state)
        // 成就类不受影响
        val ach = gs.quests.byCategory(QuestCategory.ACHIEVEMENT).first { it.def.id == "a_catch10" }
        assertEquals(3, ach.current[0])
    }

    @Test
    fun `图鉴完成度目标随收妖推进`() {
        val gs = GameState()
        // 收满全部妖，图鉴完成度达到 100%，但目标按阈值封顶（a_codex30 目标 30%）
        MonsterPool.all.forEach { gs.catchMonster(it.id) }
        val q = gs.quests.byCategory(QuestCategory.ACHIEVEMENT).first { it.def.id == "a_codex30" }
        assertEquals(30, q.current[0])
        assertEquals(QuestState.COMPLETED, q.state)
    }

    @Test
    fun `存档往返：进度不丢、秘境与地域分离`() {
        val a = GameState()
        repeat(3) { a.catchMonster("jing_saozhou") }          // 地域
        a.catchMonster("guai_nianshou", fromSecretRealm = true) // 秘境
        a.onBattleWon(Element.FIRE)
        assertEquals(120, a.incense) // 香火初始 120（任务未领取，不变）
        a.maybeResetDaily("2026-08-13")

        val json = SaveCodec.encode(a.toSaveData())
        assertTrue(json.contains("jing_saozhou"))

        // 全新状态，从存档恢复
        val b = GameState()
        val decoded = SaveCodec.decode(json)
        assertTrue(decoded != null)
        b.applySaveData(decoded!!)

        assertEquals(a.codex.regionCaught(), b.codex.regionCaught())
        assertEquals(a.codex.snapshotSecret().toSet(), b.codex.snapshotSecret().toSet())
        assertEquals(a.team, b.team)
        assertEquals(a.incense, b.incense)
        assertEquals(a.lingyu, b.lingyu)
        assertEquals(a.yaodan, b.yaodan)
        assertEquals(a.secretRealmLeft, b.secretRealmLeft)
        // 任务进度逐一一致
        val qa = a.quests.byCategory(QuestCategory.STORY).first { it.def.id == "s_fire_win" }
        val qb = b.quests.byCategory(QuestCategory.STORY).first { it.def.id == "s_fire_win" }
        assertEquals(qa.current, qb.current)
        assertEquals(qa.state, qb.state)
    }

    @Test
    fun `跨天自动重置每日任务并回满秘境次数`() {
        val gs = GameState()
        repeat(3) { gs.catchMonster("jing_saozhou") } // 完成今日·收妖
        gs.secretRealmLeft = 0
        gs.maybeResetDaily("2026-08-13")
        assertEquals("2026-08-13", gs.lastDailyReset)
        assertEquals(3, gs.secretRealmLeft) // 秘境回满
        // 切到第二天 → 每日任务清零
        gs.maybeResetDaily("2026-08-14")
        val daily = gs.quests.byCategory(QuestCategory.DAILY).first { it.def.id == "d_catch3" }
        assertEquals(0, daily.current[0])
        assertEquals(QuestState.ACTIVE, daily.state)
        // 成就类不受影响
        val ach = gs.quests.byCategory(QuestCategory.ACHIEVEMENT).first { it.def.id == "a_catch10" }
        assertEquals(3, ach.current[0])
    }

    // ── 养成系统（等级 / 经验 / 突破 / 进化） ──

    private fun expTo(level: Int): Int = (1 until level).sumOf { CultivationEngine.expForLevel(it) }

    @Test
    fun `养成-经验升级与满级封顶`() {
        val gs = GameState()
        val inst = gs.catchMonster("yao_she").let { gs.owned.values.first() }
        gs.gainExp(inst.instanceId, expTo(15))
        assertEquals(15, gs.owned[inst.instanceId]!!.level)
        // 继续灌注远超上限的经验，应封顶在基础等级上限 30，且经验不溢出
        gs.gainExp(inst.instanceId, expTo(30) + 99999)
        assertEquals(30, gs.owned[inst.instanceId]!!.level)
        assertTrue(gs.owned[inst.instanceId]!!.exp <= CultivationEngine.expForLevel(29))
    }

    @Test
    fun `养成-突破消耗妖丹并提升等级上限`() {
        val gs = GameState()
        gs.yaodan = 100
        val inst = gs.catchMonster("yao_she").let { gs.owned.values.first() }
        gs.gainExp(inst.instanceId, expTo(20)) // 达首次突破条件（Lv.20）
        assertEquals(20, gs.owned[inst.instanceId]!!.level)
        assertTrue(gs.breakthroughMonster(inst.instanceId))
        assertEquals(1, gs.owned[inst.instanceId]!!.breakthrough)
        assertEquals(80, gs.yaodan) // 100 - 20（首次突破成本）
        assertEquals(35, CultivationEngine.levelCap(gs.owned[inst.instanceId]!!)) // 30 + 5
        // 未达再次突破等级（Lv.20→需 Lv.25），应失败且不扣资源
        assertEquals(false, gs.breakthroughMonster(inst.instanceId))
        assertEquals(80, gs.yaodan)
    }

    @Test
    fun `养成-进化保留等级与突破`() {
        val gs = GameState()
        val inst = gs.catchMonster("yao_she").let { gs.owned.values.first() }
        gs.gainExp(inst.instanceId, expTo(25)) // 达进化等级门槛
        assertEquals(25, gs.owned[inst.instanceId]!!.level)
        assertTrue(gs.evolveMonster(inst.instanceId))
        val after = gs.owned[inst.instanceId]!!
        assertEquals("yao_jiao", after.defId)
        assertEquals(25, after.level)   // 进化保留等级
        assertEquals(0, after.breakthrough) // 进化保留突破
    }

    @Test
    fun `存档含拥有实例并往返一致`() {
        val a = GameState()
        a.yaodan = 500
        val i1 = a.catchMonster("yao_she").let { a.owned.values.first() }
        a.gainExp(i1.instanceId, expTo(20))
        a.breakthroughMonster(i1.instanceId)
        val json = SaveCodec.encode(a.toSaveData())
        assertTrue(json.contains("\"owned\""))
        val b = GameState()
        b.applySaveData(SaveCodec.decode(json)!!)
        assertEquals(a.owned.size, b.owned.size)
        val bi = b.owned[i1.instanceId]!!
        assertEquals(20, bi.level)
        assertEquals(1, bi.breakthrough)
        assertEquals(480, b.yaodan) // 500 - 20
    }

    // ── 装备祭炼 / 炼妖融合 ──

    @Test
    fun `装备-等级加成与元素契合`() {
        val def = EquipmentLibrary.byId("eq_armor")!! // 水甲
        val noAff = EquipmentEngine.bonusAtLevel(def, 1, false)
        assertEquals(16, noAff.def)
        val aff = EquipmentEngine.bonusAtLevel(def, 1, true) // 给水妖→契合 ×1.2
        assertEquals(19, aff.def)  // 16*1.2=19.2→19
        assertEquals(36, aff.hp)   // 30*1.2=36
        assertTrue(EquipmentEngine.canForge(OwnedEquip("eq_sword", "x", 1)))
        assertTrue(!EquipmentEngine.canForge(OwnedEquip("eq_sword", "x", 10))) // 已达上限
    }

    @Test
    fun `装备-有效属性含突破与装备加成`() {
        val gs = GameState()
        gs.yaodan = 100
        val inst = gs.catchMonster("gui_guijia").let { gs.owned.values.first() } // 水妖
        val sword = gs.equipment.first { it.equipId == "eq_sword" }   // 金，不契合
        val armor = gs.equipment.first { it.equipId == "eq_armor" }   // 水，契合
        assertTrue(gs.equipItem(inst.instanceId, sword.instanceId))
        assertTrue(gs.equipItem(inst.instanceId, armor.instanceId))
        val eff = gs.effectiveStatsOf(inst)
        val base = CultivationEngine.effectiveStats(inst)!!
        assertEquals(base.atk + 18, eff.atk)   // 青锋剑不契合
        assertEquals(base.def + 19, eff.def)   // 玄龟甲水契合 ×1.2
        assertEquals(base.hp + 36, eff.hp)
        assertEquals(base.spd + 4, eff.spd)
    }

    @Test
    fun `装备-祭炼消耗妖丹并升级`() {
        val gs = GameState()
        gs.yaodan = 50
        gs.catchMonster("yao_she")
        val sword = gs.equipment.first { it.equipId == "eq_sword" }
        assertTrue(gs.forgeEquip(sword.instanceId))
        assertEquals(2, gs.equipment.first { it.instanceId == sword.instanceId }.level)
        assertEquals(40, gs.yaodan) // 50 - 10
    }

    @Test
    fun `炼妖-融合产出与消耗`() {
        val gs = GameState()
        gs.yaodan = 100
        gs.catchMonster("yao_jiao")
        gs.catchMonster("gui_linggui")
        val a = gs.owned.values.first { it.defId == "yao_jiao" }
        val b = gs.owned.values.first { it.defId == "gui_linggui" }
        val canFuse = FusionEngine.canFuse(a, b, gs.yaodan)
        val r = gs.fuse(a.instanceId, b.instanceId)
        assertTrue(r != null)
        assertEquals("gui_xuanwu", r!!.newDefId) // 配方：蛟+灵龟→玄武
        assertEquals(70, gs.yaodan) // 100 - 30
        assertEquals(1, gs.owned.size)           // 两原实例移除，新增 1
        assertEquals("gui_xuanwu", gs.owned.values.first().defId)
    }

    @Test
    fun `炼妖-6个配方全覆盖`() {
        val gs = GameState()
        gs.yaodan = 999
        for ((pair, expectedDefId) in FusionEngine.RECIPES) {
            val ids = pair.toList()
            gs.catchMonster(ids[0])
            gs.catchMonster(ids[1])
            val a = gs.owned.values.first { it.defId == ids[0] }
            val b = gs.owned.values.first { it.defId == ids[1] }
            val r = gs.fuse(a.instanceId, b.instanceId)
            assertTrue(r != null, "recipe $pair -> $expectedDefId fuse failed")
            assertEquals(expectedDefId, r!!.newDefId, "recipe $pair wrong output")
            // cleanup: consume the new instance so next iteration starts clean
            val newInst = gs.owned.values.firstOrNull { it.defId == expectedDefId }
            if (newInst != null) gs.owned.remove(newInst.instanceId)
        }
    }

    @Test
    fun `存档含装备并往返一致`() {
        val a = GameState()
        a.yaodan = 60
        val inst = a.catchMonster("yao_she").let { a.owned.values.first() }
        val sword = a.equipment.first { it.equipId == "eq_sword" }
        assertTrue(a.equipItem(inst.instanceId, sword.instanceId))
        a.forgeEquip(sword.instanceId)
        val json = SaveCodec.encode(a.toSaveData())
        val b = GameState()
        b.applySaveData(SaveCodec.decode(json)!!)
        assertEquals(a.equipment.size, b.equipment.size)
        assertEquals(a.equipped, b.equipped)
        assertEquals(2, b.equipment.first { it.equipId == "eq_sword" }.level)
        assertEquals(50, b.yaodan) // 60 - 10（祭炼）
    }

    // ── 渡劫天劫（养成线终章，保证成功 / 必成） ──

    /** 构造满级满突破的实例，便于直接测试渡劫前置（绕开逐级培养） */
    private fun maxedInst(defId: String, trib: Int = 0): MonsterInstance {
        // 等级上限 = 30 + 5×5 = 55；突破 5 次
        return MonsterInstance("i_${defId}_t", defId, 55, 0, 5, tribulations = trib)
    }

    @Test
    fun `渡劫-前置条件：满级满突破可渡劫，否则失败`() {
        // 未满级（50<55）不可渡劫
        val gsLow = GameState()
        gsLow.owned["i"] = MonsterInstance("i", "yao_she", 50, 0, 5)
        assertEquals(false, gsLow.tribulateMonster("i"))
        // 未满突破（0<5）不可渡劫
        val gsBt = GameState()
        gsBt.owned["i"] = MonsterInstance("i", "yao_she", 55, 0, 0)
        assertEquals(false, gsBt.tribulateMonster("i"))
        // 满级满突破可渡劫
        val gsOk = GameState()
        gsOk.yaodan = 1000
        gsOk.owned["i"] = maxedInst("yao_she")
        assertEquals(true, gsOk.tribulateMonster("i"))
    }

    @Test
    fun `渡劫-消耗妖丹并升阶升属性与任务推进`() {
        val gs = GameState()
        gs.yaodan = 1000
        val inst = maxedInst("yao_she") // 基础 凡
        gs.owned["i_she"] = inst
        val before = CultivationEngine.effectiveStats(inst)!!
        assertTrue(gs.tribulateMonster("i_she"))
        val after = gs.owned["i_she"]!!
        assertEquals(1, after.tribulations)                 // 渡劫次数 +1
        assertEquals(800, gs.yaodan)                         // 1000 - 200（首次渡劫成本）
        assertEquals(Rarity.LING, CultivationEngine.currentRarity(after)) // 凡 → 灵
        // 渡劫后有效属性 ≈ 渡劫前 ×1.25（整数截断允许 ±1 误差）
        val afterEff = CultivationEngine.effectiveStats(after)!!
        for ((v0, v1) in listOf(before.atk to afterEff.atk, before.def to afterEff.def,
                                 before.hp to afterEff.hp, before.spd to afterEff.spd)) {
            assertTrue(abs(v1 - v0 * 1.25) <= 1, "渡劫后属性 ≈×1.25（期望≈${v0 * 1.25}，实际 $v1）")
        }
        // 任务 TRIBULATE_TOTAL 推进并完成任务
        val q = gs.quests.byCategory(QuestCategory.TRIAL).first { it.def.id == "t_tribulate" }
        assertEquals(1, q.current[0])
        assertEquals(QuestState.COMPLETED, q.state)
    }

    @Test
    fun `渡劫-消耗逐级递增`() {
        assertEquals(200, CultivationEngine.tribulateCost(MonsterInstance("a", "yao_she", 55, 0, 5, 0)))
        assertEquals(400, CultivationEngine.tribulateCost(MonsterInstance("a", "yao_she", 55, 0, 5, 1)))
        assertEquals(600, CultivationEngine.tribulateCost(MonsterInstance("a", "yao_she", 55, 0, 5, 2)))
    }

    @Test
    fun `渡劫-神阶不可再渡（保值）`() {
        // yao_long 基础即为 神，maxTribulations=0
        assertEquals(0, CultivationEngine.maxTribulations(Rarity.SHEN))
        val gs = GameState()
        gs.yaodan = 1000
        gs.owned["i_long"] = maxedInst("yao_long") // 神阶满级满突破
        assertEquals(false, gs.tribulateMonster("i_long"))
        // 凡妖满渡劫（3 次）后亦不可再渡
        val gsFan = GameState()
        gsFan.yaodan = 9999
        gsFan.owned["i_she"] = maxedInst("yao_she", trib = 3)
        assertEquals(false, gsFan.tribulateMonster("i_she"))
    }

    @Test
    fun `存档含渡劫次数并往返一致`() {
        val a = GameState()
        a.yaodan = 500
        a.owned["i_x"] = MonsterInstance("i_x", "yao_she", 30, 0, 2, tribulations = 2)
        val json = SaveCodec.encode(a.toSaveData())
        assertTrue(json.contains("\"tribulations\""))
        val b = GameState()
        b.applySaveData(SaveCodec.decode(json)!!)
        assertEquals(2, b.owned["i_x"]!!.tribulations)
    }

    // ── 限定神池 + 通用池保底（GDD 十四·经济） ──

    @Test
    fun `限定池硬保底 85 抽必神`() {
        val state = GachaEngine.GachaState()
        // 用 1.0（必非神 rng）连抽 84 次 → 第 85 次硬保底必神
        repeat(84) { GachaEngine.pull(state, GachaEngine.PoolType.LIMITED) { 1.0 } }
        assertEquals(84, state.limitedSinceShen)
        val last = GachaEngine.pull(state, GachaEngine.PoolType.LIMITED) { 1.0 }
        assertEquals(Rarity.SHEN, last.rarity)
        assertEquals(0, state.limitedSinceShen) // 出神后归零
    }

    @Test
    fun `通用池硬保底 90 抽必神`() {
        val state = GachaEngine.GachaState()
        repeat(89) { GachaEngine.pull(state, GachaEngine.PoolType.NORMAL) { 1.0 } }
        assertEquals(89, state.normalSinceShen)
        val last = GachaEngine.pull(state, GachaEngine.PoolType.NORMAL) { 1.0 }
        assertEquals(Rarity.SHEN, last.rarity)
        assertEquals(0, state.normalSinceShen)
    }

    @Test
    fun `两池保底计数独立互不干扰`() {
        val state = GachaEngine.GachaState()
        // LIMITED 出神 → 只清零 limitedSinceShen，normalSinceShen 应保留
        repeat(5) { GachaEngine.pull(state, GachaEngine.PoolType.NORMAL) { 1.0 } }
        assertEquals(5, state.normalSinceShen)
        GachaEngine.pull(state, GachaEngine.PoolType.LIMITED) { 0.0 } // 必神
        assertEquals(0, state.limitedSinceShen)
        assertEquals(5, state.normalSinceShen) // 通用池计数不受限影响
    }

    // ── 流派模板一键套用（GDD 七·技能系统） ──

    @Test
    fun `攻流派 优先输出技能`() {
        val monster = MonsterPool.byId("yao_she")!!
        val picked = FormationTemplates.pickFourSkills(monster, FormationTemplate.OFFENSIVE)
        assertEquals(4, picked.size)
        // 第一轮应优先 ATTACK / ELEMENT / PASSIVE（至少 1 个）
        val primaryTypes = setOf(SkillType.ATTACK, SkillType.ELEMENT, SkillType.PASSIVE)
        val primaryCount = picked.count { it.type in primaryTypes }
        assertTrue(primaryCount >= 1, "攻流派应至少挑到 1 个输出型技能，实际全非输出")
    }

    @Test
    fun `防流派 优先护盾恢复技能`() {
        val monster = MonsterPool.byId("gui_guijia")!!
        val picked = FormationTemplates.pickFourSkills(monster, FormationTemplate.DEFENSIVE)
        assertEquals(4, picked.size)
        val primaryTypes = setOf(SkillType.DEFENSE, SkillType.SUPPORT, SkillType.PASSIVE)
        val primaryCount = picked.count { it.type in primaryTypes }
        assertTrue(primaryCount >= 1, "防流派应至少挑到 1 个防御型技能")
    }

    @Test
    fun `一键套用 队伍每只妖都返回 4 个技能`() {
        val team = listOfNotNull(
            MonsterPool.byId("yao_she"),
            MonsterPool.byId("gui_guijia"),
            MonsterPool.byId("jing_denglong"),
        )
        for (tpl in FormationTemplate.values()) {
            val result = FormationTemplates.apply(team, tpl)
            assertEquals(team.size, result.size)
            for ((mid, skills) in result) {
                assertTrue(skills.size == 4, "$tpl 对 $mid 未返回 4 技能")
            }
        }
    }

    @Test
    fun `羁绊评估 五行齐全激活相生阵`() {
        // METAL=jing_tongjing, WOOD=jing_saozhou, WATER=yao_she, FIRE=jing_denglong, EARTH=guai_nianshou
        val team = listOfNotNull(
            MonsterPool.byId("jing_tongjing"),   // 金
            MonsterPool.byId("jing_saozhou"),    // 木
            MonsterPool.byId("yao_she"),         // 水
            MonsterPool.byId("jing_denglong"),   // 火
            MonsterPool.byId("guai_nianshou"),   // 土
            MonsterPool.byId("yao_hu"),          // 火（凑 6 只）
        )
        val elements = team.map { it.element }.toSet()
        assertTrue(elements.containsAll(listOf(Element.METAL, Element.WOOD, Element.WATER, Element.FIRE, Element.EARTH)),
            "队伍应包含五行，实际：$elements")
        val syn = FormationEngine.evaluate(team)
        assertTrue(syn.any { it.id == SynergyId.XIANGSHENG }, "五行齐全应激活相生阵")
        assertEquals(2, syn.first { it.id == SynergyId.XIANGSHENG }.ultimateCharges)
    }

}
