package com.shanhai.core.logic

import com.shanhai.core.model.Stats
import com.shanhai.core.model.Element
import com.shanhai.core.model.Monster
import kotlin.random.Random

enum class Side { PLAYER, ENEMY }
enum class BattleOutcome { ONGOING, PLAYER_WIN, ENEMY_WIN }

/** 战斗事件（驱动 UI 演出与日志） */
data class BattleEvent(
    val side: Side,
    val attackerName: String,
    val defenderName: String,
    val skillName: String,
    val damage: Int,
    val elementLabel: String, // 如 "水克火 ×1.6"
    val crit: Boolean,
    val defenderHpLeft: Int,
    val comboName: String? = null, // 连携组合技触发时记录名称（GDD 七）
    val envMul: Double = 1.0,      // 天气/地形环境乘子（GDD 十九·天气地形战斗）
)

/**
 * 战斗会话：6 宠队伍、同刻出战 1 只、自动选克制技、阵亡自动换妖。
 * UI 每调用一次 [step] 推进「玩家一出招 + 敌方一反击」，便于做镜头切换演出。
 */
class BattleSession(
    val playerTeam: List<Combatant>,
    val enemyTeam: List<Combatant>,
    /** 玩家阵容羁绊带来的全队攻击增伤倍率（默认 1.0，见 [FormationEngine]）。 */
    val playerTeamMul: Double = 1.0,
    /** 天气（GDD 十九·天气地形战斗），默认晴。 */
    val weather: Weather = Weather.CLEAR,
    /** 地形五行偏向（复用 [LocationEngine.RegionTheme]），默认无地形。 */
    val terrainBias: Element? = null,
    /** 宗门科技树带来的全队攻击增伤倍率（默认 1.0，见 [SectTechEngine]）。 */
    val playerSectAttackMul: Double = 1.0,
    private val random: Random = Random.Default,
) {
    var activePlayer = 0
    var activeEnemy = 0
    val log: MutableList<BattleEvent> = mutableListOf()
    var outcome: BattleOutcome = BattleOutcome.ONGOING
    /** 已进行的回合计数（用于回合上限与平局裁决）。 */
    var turn = 0
    /** 连携组合技冷却：玩家妖索引 → 剩余冷却回合数（GDD 七，每场限次） */
    private val comboCd: MutableMap<Int, Int> = mutableMapOf()

    companion object {
        /** 单场战斗回合上限：超过则按剩余血量比例裁决胜负，保证战斗必然终结。 */
        const val MAX_TURNS = 100
    }

    init {
        playerTeam.forEach { it.ensureHp() }
        enemyTeam.forEach { it.ensureHp() }
    }

    fun currentPlayer(): Combatant = playerTeam[activePlayer]
    fun currentEnemy(): Combatant = enemyTeam[activeEnemy]

    private fun nextAlive(team: List<Combatant>, from: Int): Int {
        for (i in team.indices) if (team[i].isAlive) return i
        return -1
    }

    /** 推进一回合（玩家出招 + 敌方反击），返回本回合玩家事件（可能为 null 表示已结束） */
    fun step(): BattleEvent? {
        if (outcome != BattleOutcome.ONGOING) return null
        turn++

        // 玩家出招
        val atk = currentPlayer()
        val def = currentEnemy()
        val skill = BattleEngine.bestCounterSkill(atk, def) ?: return null
        val pEnv = WeatherEngine.envMultiplier(atk.monster.element, weather, terrainBias)
        val res = BattleEngine.damage(atk, def, skill, random, playerTeamMul, envMul = pEnv, sectAttackMul = playerSectAttackMul)
        // 连携组合技（GDD 七）：每玩家回合递减 CD，就绪则触发首个可用连携
        for (k in comboCd.keys) comboCd[k] = (comboCd[k] ?: 0) - 1
        var comboName: String? = null
        var finalDmg = res.damage
        val cd = comboCd[activePlayer] ?: 0
        if (cd <= 0) {
            val combos = ComboEngine.availableCombos(atk.monster.skills.take(4).map { it.id })
            if (combos.isNotEmpty()) {
                val c = combos.first()
                comboName = c.name
                when (c.effect.kind) {
                    ComboKind.DAMAGE_MUL ->
                        finalDmg = (res.damage * c.effect.value).toInt().coerceAtLeast(1)
                    ComboKind.HEAL_PERCENT -> {
                        val heal = (atk.maxHp * c.effect.value).toInt()
                        atk.hp = (atk.hp + heal).coerceAtMost(atk.maxHp)
                    }
                    ComboKind.CONTROL -> { /* 占位：敌方硬控由战斗演出层处理 */ }
                }
                comboCd[activePlayer] = c.cd
            }
        }
        def.hp -= finalDmg
        val ev = BattleEvent(
            Side.PLAYER, atk.monster.name, def.monster.name, skill.name,
            finalDmg, res.label, res.crit, def.hp, comboName, pEnv,
        )
        log.add(ev)

        if (!def.isAlive) {
            val ni = nextAlive(enemyTeam, activeEnemy)
            if (ni == -1) { outcome = BattleOutcome.PLAYER_WIN; return ev }
            activeEnemy = ni
        }

        // 敌方反击
        val eatk = currentEnemy()
        val edef = currentPlayer()
        val eskill = BattleEngine.bestCounterSkill(eatk, edef) ?: return ev
        val eEnv = WeatherEngine.envMultiplier(eatk.monster.element, weather, terrainBias)
        val eres = BattleEngine.damage(eatk, edef, eskill, random, envMul = eEnv)
        edef.hp -= eres.damage
        log.add(
            BattleEvent(
                Side.ENEMY, eatk.monster.name, edef.monster.name, eskill.name,
                eres.damage, eres.label, eres.crit, edef.hp, envMul = eEnv,
            ),
        )
        if (!edef.isAlive) {
            val ni = nextAlive(playerTeam, activePlayer)
            if (ni == -1) { outcome = BattleOutcome.ENEMY_WIN; return ev }
            activePlayer = ni
        }
        // 回合上限：仍未分胜负则按剩余血量比例裁决（平局判玩家胜，确保必有结果）
        if (turn >= MAX_TURNS && outcome == BattleOutcome.ONGOING) resolveByHp()
        return ev
    }

    /** 按双方剩余血量比例裁决胜负（用于回合上限 / 极端僵局）。 */
    private fun resolveByHp() {
        val pf = playerTeam.sumOf { it.hp.coerceAtLeast(0).toDouble() / it.maxHp }
        val ef = enemyTeam.sumOf { it.hp.coerceAtLeast(0).toDouble() / it.maxHp }
        outcome = if (pf >= ef) BattleOutcome.PLAYER_WIN else BattleOutcome.ENEMY_WIN
    }

    /** 一次性模拟到结束（用于快速结算 / 测试） */
    fun simulateToEnd(maxSteps: Int = MAX_TURNS): List<BattleEvent> {
        var n = 0
        while (outcome == BattleOutcome.ONGOING && n < maxSteps) {
            step() ?: break
            n++
        }
        return log.toList()
    }
}

/**
 * 便捷：用妖 + 出战技能 + 等级 + 突破构建 Combatant。
 * [effective] 为已计入突破+装备的有效属性；不传则回退为 statsAtLevel（用于敌方/无装备场景）。
 */
fun Monster.toCombatant(level: Int, equipped: List<com.shanhai.core.model.Skill>, breakthrough: Int = 0, effective: Stats? = null): Combatant =
    Combatant(this, level, equipped, breakthrough, effective ?: statsAtLevel(level))
