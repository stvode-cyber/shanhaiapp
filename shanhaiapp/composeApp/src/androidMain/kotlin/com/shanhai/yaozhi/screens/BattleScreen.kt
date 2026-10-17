package com.shanhai.yaozhi.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shanhai.core.data.MonsterPool
import com.shanhai.core.logic.BattleEngine
import com.shanhai.core.logic.Combatant
import com.shanhai.core.logic.DamageResult
import com.shanhai.core.logic.FormationTemplate
import com.shanhai.core.logic.FormationTemplates
import com.shanhai.core.model.MonsterCategory
import com.shanhai.core.model.Rarity
import com.shanhai.core.model.Stats
import com.shanhai.yaozhi.LocalGameState
import com.shanhai.yaozhi.theme.QingDai
import com.shanhai.yaozhi.theme.ZhuSha
import kotlin.random.Random

/**
 * 试炼对战 · V1
 * 我方妖 vs 敌方妖，回合制，每回合自动选最优克制技。
 * 新增：流派模板一键套用（攻/防/辅三预设）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BattleScreen(modifier: Modifier = Modifier) {
    val gs = LocalGameState.current
    val pool = remember { MonsterPool.all }
    var myCombatant by remember { mutableStateOf(buildCombatant(pool[0], level = 5)) }
    var enemyCombatant by remember { mutableStateOf(buildCombatant(pool[3], level = 5)) }
    var battleLog by remember { mutableStateOf(listOf("战斗开始！")) }
    var isPlayerTurn by remember { mutableStateOf(true) }
    var gameOver by remember { mutableStateOf(false) }
    var showTemplateDialog by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
    ) {
        Row(horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()) {
            Text("试炼对战", fontSize = 22.sp, fontWeight = FontWeight.Bold)
            OutlinedButton(onClick = { showTemplateDialog = true }) {
                Text("流派模板", fontSize = 12.sp)
            }
        }
        Text("当前流派：${gs.currentFormation?.label ?: "未选"}", fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(12.dp))

        // 对战双方
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            CombatantCard(myCombatant, "我方", QingDai)
            Text("VS", fontSize = 24.sp, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.CenterVertically))
            CombatantCard(enemyCombatant, "敌方", ZhuSha)
        }

        Spacer(Modifier.height(20.dp))

        // HP 条
        HpBar(myCombatant.hp, myCombatant.maxHp, "我方")
        Spacer(Modifier.height(4.dp))
        HpBar(enemyCombatant.hp, enemyCombatant.maxHp, "敌方")

        Spacer(Modifier.height(16.dp))

        // 战斗日志
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceVariant
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text("战斗日志", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                battleLog.takeLast(6).forEach { log ->
                    Text(log, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        // 操作按钮
        if (!gameOver) {
            Button(
                onClick = {
                    if (!isPlayerTurn) return@Button
                    val mySkill = BattleEngine.bestCounterSkill(myCombatant, enemyCombatant)
                        ?: myCombatant.equippedSkills.firstOrNull() ?: return@Button
                    val dmg = BattleEngine.damage(myCombatant, enemyCombatant, mySkill)
                    enemyCombatant = enemyCombatant.copy(
                        hp = (enemyCombatant.hp - dmg.damage).coerceAtLeast(0)
                    )
                    battleLog = battleLog + "我方 ${mySkill.name} 造成 ${dmg.damage} 伤害！${dmg.label}${if (dmg.crit) " 暴击！" else ""}"
                    if (enemyCombatant.hp <= 0) {
                        battleLog = battleLog + "🎉 敌方妖被收服！"
                        gameOver = true
                    } else {
                        isPlayerTurn = false
                    }
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                enabled = isPlayerTurn
            ) {
                Text(if (isPlayerTurn) "出招" else "敌方回合...", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }

            Spacer(Modifier.height(8.dp))

            LaunchedEffect(isPlayerTurn) {
                if (!isPlayerTurn && !gameOver) {
                    kotlinx.coroutines.delay(800)
                    val enemySkill = BattleEngine.bestCounterSkill(enemyCombatant, myCombatant)
                        ?: enemyCombatant.equippedSkills.firstOrNull()
                    if (enemySkill != null) {
                        val dmg = BattleEngine.damage(enemyCombatant, myCombatant, enemySkill)
                        myCombatant = myCombatant.copy(
                            hp = (myCombatant.hp - dmg.damage).coerceAtLeast(0)
                        )
                        battleLog = battleLog + "敌方 ${enemySkill.name} 造成 ${dmg.damage} 伤害！${dmg.label}${if (dmg.crit) " 暴击！" else ""}"
                        if (myCombatant.hp <= 0) {
                            battleLog = battleLog + "💀 我方妖倒下了..."
                            gameOver = true
                        } else {
                            isPlayerTurn = true
                        }
                    }
                }
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = {
                        myCombatant = buildCombatant(pool[Random.nextInt(pool.size)], level = 5)
                        enemyCombatant = buildCombatant(pool[Random.nextInt(pool.size)], level = 5)
                        myCombatant.ensureHp()
                        enemyCombatant.ensureHp()
                        battleLog = listOf("战斗开始！")
                        isPlayerTurn = true
                        gameOver = false
                    },
                    modifier = Modifier.weight(1f)
                ) { Text("再来一局") }
            }
        }
    }

    // 流派模板弹窗
    if (showTemplateDialog) {
        AlertDialog(
            onDismissRequest = { showTemplateDialog = false },
            title = { Text("流派模板 · 一键套用") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("选择后，每只妖将按流派自动推荐 4 个出战技能：", fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    for (tpl in FormationTemplate.values()) {
                        Surface(shape = RoundedCornerShape(8.dp),
                            color = if (gs.currentFormation == tpl)
                                MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.fillMaxWidth()) {
                            Row(modifier = Modifier.padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween) {
                                Column {
                                    Text(tpl.label, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                    Text(tpl.desc, fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Button(onClick = {
                                    gs.currentFormation = tpl
                                    showTemplateDialog = false
                                }, modifier = Modifier.align(Alignment.CenterVertically)) {
                                    Text("套用", fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showTemplateDialog = false }) { Text("取消") }
            }
        )
    }
}

@Composable
private fun CombatantCard(c: Combatant, label: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(color),
            contentAlignment = Alignment.Center
        ) {
            Text(c.monster.name.take(1), color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(4.dp))
        Text(c.monster.name, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        Text("Lv.${c.level}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun HpBar(hp: Int, max: Int, label: String) {
    val pct = if (max > 0) (hp.toFloat() / max) else 0f
    Column {
        Text("$label  HP $hp/$max", fontSize = 12.sp)
        LinearProgressIndicator(
            progress = { pct },
            modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
            color = if (pct > 0.3f) QingDai else ZhuSha,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
        )
    }
}

private fun buildCombatant(m: com.shanhai.core.model.Monster, level: Int): Combatant {
    val stats = Stats(
        hp = m.baseHp + level * 10,
        atk = m.baseAtk + level * 2,
        def = m.baseDef + level,
        spd = m.baseSpd + level,
    )
    return Combatant(
        monster = m,
        level = level,
        equippedSkills = m.skills.take(4),
        effective = stats,
        hp = stats.hp,
    ).also { it.ensureHp() }
}
