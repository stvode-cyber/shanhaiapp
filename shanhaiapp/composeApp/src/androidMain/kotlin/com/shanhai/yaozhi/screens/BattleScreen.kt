package com.shanhai.yaozhi.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shanhai.core.data.MonsterPool
import com.shanhai.core.logic.BattleEngine
import com.shanhai.core.logic.Combatant
import com.shanhai.core.logic.DamageResult
import com.shanhai.core.logic.FormationTemplate
import com.shanhai.core.logic.FormationTemplates
import com.shanhai.core.model.Stats
import com.shanhai.yaozhi.LocalGameState
import com.shanhai.yaozhi.LocalGsVersion
import com.shanhai.yaozhi.mutate
import com.shanhai.yaozhi.theme.*
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.random.Random

/**
 * 试炼对战 · V2（UI 润色）
 * v2 新增：HP 条渐变+临界脉动 + 伤害飘字 + 出招缩放 + 日志高亮
 * 引擎逻辑不变，只改视觉层
 */

// ===== 飘字数据类（顶层，避免类内部引用问题）=====
private data class FloatDamage(
    val id: Int,
    val text: String,
    val color: Color
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BattleScreen(modifier: Modifier = Modifier) {
    val gsState = LocalGameState.current
    val versionState = LocalGsVersion.current
    val _v = versionState.value
    val gs = gsState.value
    val pool = remember { MonsterPool.all }
    var myCombatant by remember { mutableStateOf(buildCombatant(pool[0], level = 5)) }
    var enemyCombatant by remember { mutableStateOf(buildCombatant(pool[3], level = 5)) }
    var battleLog by remember { mutableStateOf(listOf("战斗开始！")) }
    var isPlayerTurn by remember { mutableStateOf(true) }
    var gameOver by remember { mutableStateOf(false) }
    var showTemplateDialog by remember { mutableStateOf(false) }

    // 飘字列表
    var myFloats by remember { mutableStateOf<List<FloatDamage>>(emptyList()) }
    var enemyFloats by remember { mutableStateOf<List<FloatDamage>>(emptyList()) }
    var floatCounter by remember { mutableIntStateOf(0) }

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
        Box(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                CombatantCard(myCombatant, "我方", QingDai)
                Text("VS", fontSize = 24.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.CenterVertically),
                    color = JinHuang)
                CombatantCard(enemyCombatant, "敌方", ZhuSha)
            }
            enemyFloats.forEach { ft ->
                FloatDamageText(ft, modifier = Modifier.align(Alignment.TopEnd)) {
                    enemyFloats = enemyFloats.filter { it.id != ft.id }
                }
            }
            myFloats.forEach { ft ->
                FloatDamageText(ft, modifier = Modifier.align(Alignment.TopStart)) {
                    myFloats = myFloats.filter { it.id != ft.id }
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        HpBarV2(myCombatant.hp, myCombatant.maxHp, "我方", QingDai)
        Spacer(Modifier.height(6.dp))
        HpBarV2(enemyCombatant.hp, enemyCombatant.maxHp, "敌方", ZhuSha)

        Spacer(Modifier.height(16.dp))

        Surface(
            modifier = Modifier.fillMaxWidth().weight(1f),
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceVariant
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text("战斗日志", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                battleLog.takeLast(8).forEach { LogLineHighlight(it) }
            }
        }

        Spacer(Modifier.height(12.dp))

        if (!gameOver) {
            val btnScale = remember { Animatable(1f) }
            val btnScope = rememberCoroutineScope()
            Button(
                onClick = {
                    if (!isPlayerTurn) return@Button
                    btnScope.launch {
                        btnScale.animateTo(0.92f, tween(80))
                        delay(30)
                        btnScale.animateTo(1f, tween(200))
                    }
                    val mySkill = BattleEngine.bestCounterSkill(myCombatant, enemyCombatant)
                        ?: myCombatant.equippedSkills.firstOrNull() ?: return@Button
                    val dmg = BattleEngine.damage(myCombatant, enemyCombatant, mySkill)
                    addFloat(enemyFloats, floatCounter, dmg) { f, c -> enemyFloats = f; floatCounter = c }
                    enemyCombatant = enemyCombatant.copy(
                        hp = (enemyCombatant.hp - dmg.damage).coerceAtLeast(0))
                    battleLog = battleLog + "我方 ${mySkill.name} 造成 ${dmg.damage} 伤害！${dmg.label}${if (dmg.crit) " 暴击！" else ""}"
                    if (enemyCombatant.hp <= 0) {
                        battleLog = battleLog + "🎉 敌方妖被收服！"; gameOver = true
                    } else { isPlayerTurn = false }
                },
                modifier = Modifier.fillMaxWidth().height(56.dp)
                    .graphicsLayer { scaleX = btnScale.value; scaleY = btnScale.value },
                enabled = isPlayerTurn,
                colors = ButtonDefaults.buttonColors(containerColor = if (isPlayerTurn) ZhuSha else ZhuSha.copy(alpha = 0.5f))
            ) {
                Text(if (isPlayerTurn) "出招" else "敌方回合...",
                    fontSize = 18.sp, fontWeight = FontWeight.Bold, color = XueBai)
            }
            Spacer(Modifier.height(8.dp))
            LaunchedEffect(isPlayerTurn) {
                if (!isPlayerTurn && !gameOver) {
                    delay(800)
                    val enemySkill = BattleEngine.bestCounterSkill(enemyCombatant, myCombatant)
                        ?: enemyCombatant.equippedSkills.firstOrNull()
                    if (enemySkill != null) {
                        val dmg = BattleEngine.damage(enemyCombatant, myCombatant, enemySkill)
                        addFloat(myFloats, floatCounter, dmg) { f, c -> myFloats = f; floatCounter = c }
                        myCombatant = myCombatant.copy(
                            hp = (myCombatant.hp - dmg.damage).coerceAtLeast(0))
                        battleLog = battleLog + "敌方 ${enemySkill.name} 造成 ${dmg.damage} 伤害！${dmg.label}${if (dmg.crit) " 暴击！" else ""}"
                        if (myCombatant.hp <= 0) {
                            battleLog = battleLog + "💀 我方妖倒下了..."; gameOver = true
                        } else { isPlayerTurn = true }
                    }
                }
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = {
                        myCombatant = buildCombatant(pool[Random.nextInt(pool.size)], level = 5)
                        enemyCombatant = buildCombatant(pool[Random.nextInt(pool.size)], level = 5)
                        myCombatant.ensureHp(); enemyCombatant.ensureHp()
                        battleLog = listOf("战斗开始！")
                        isPlayerTurn = true; gameOver = false
                        myFloats = emptyList(); enemyFloats = emptyList()
                    },
                    modifier = Modifier.weight(1f)
                ) { Text("再来一局") }
            }
        }
    }

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
                                    gsState.mutate(versionState) { it.currentFormation = tpl }
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
            dismissButton = { TextButton(onClick = { showTemplateDialog = false }) { Text("取消") } }
        )
    }
}

// ===== 辅助函数 =====

private fun DamageResult.isResisted(): Boolean = elementMult < 1.0 || categoryMult < 1.0

private fun addFloat(
    current: List<FloatDamage>,
    counter: Int,
    dmg: DamageResult,
    setter: (List<FloatDamage>, Int) -> Unit
) {
    val (color, label) = when {
        dmg.crit -> CritFloatText to "暴击!-${dmg.damage}"
        dmg.isResisted() -> ResistFloatText to "被克-${dmg.damage}"
        else -> DamageCritical to "-${dmg.damage}"
    }
    val newId = counter + 1
    setter(current + FloatDamage(newId, label, color), newId)
}

// ===== V2 组件 =====

@Composable
private fun HpBarV2(hp: Int, max: Int, label: String, baseColor: Color) {
    val pct = if (max > 0) (hp.toFloat() / max) else 0f
    val hpColor by animateColorAsState(
        targetValue = when {
            pct > 0.5f -> baseColor
            pct > 0.3f -> ZhuShaSoft
            else -> ZhuSha
        },
        animationSpec = tween(300),
        label = "hpColor"
    )
    val hpWidth by animateFloatAsState(
        targetValue = pct,
        animationSpec = tween(400, easing = FastOutSlowInEasing),
        label = "hpWidth"
    )
    Column {
        Text("$label  HP $hp/$max", fontSize = 12.sp, fontWeight = FontWeight.Medium)
        Row(modifier = Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)) {
            Box(
                modifier = Modifier.fillMaxHeight().fillMaxWidth(hpWidth)
                    .background(brush = Brush.horizontalGradient(
                        colors = listOf(hpColor, hpColor.copy(alpha = 0.85f))),
                        shape = RoundedCornerShape(5.dp))
            )
        }
    }
}

@Composable
private fun FloatDamageText(
    ft: FloatDamage,
    modifier: Modifier = Modifier,
    onDone: () -> Unit
) {
    val offsetY = remember { Animatable(0f) }
    val alpha = remember { Animatable(1f) }
    LaunchedEffect(ft.id) {
        coroutineScope {
            launch { offsetY.animateTo(-60f, tween(800)) }
            launch { alpha.animateTo(0f, tween(800)) }
        }
        onDone()
    }
    Text(text = ft.text, color = ft.color, fontSize = 20.sp,
        fontWeight = FontWeight.Bold,
        modifier = modifier
            .graphicsLayer { translationY = offsetY.value; this.alpha = alpha.value })
}

@Composable
private fun LogLineHighlight(log: String) {
    val color = when {
        log.contains("暴击") -> CritFloatText
        log.contains("被克") -> ResistFloatText
        log.contains("收服") || log.contains("成功") -> HealingGreen
        log.contains("倒下") || log.contains("逃走") -> ZhuSha
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Text(log, fontSize = 12.sp, color = color, lineHeight = 16.sp)
}

@Composable
private fun CombatantCard(c: Combatant, label: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        val rarityGlow by animateColorAsState(
            targetValue = colorForRarity(c.monster.rarity).copy(alpha = 0.25f),
            animationSpec = tween(300),
            label = "rarityGlow"
        )
        Box(modifier = Modifier.size(88.dp).clip(CircleShape).background(rarityGlow),
            contentAlignment = Alignment.Center) {
            Box(modifier = Modifier.size(72.dp).clip(CircleShape).background(color),
                contentAlignment = Alignment.Center) {
                Text(c.monster.name.take(1), color = Color.White,
                    fontSize = 32.sp, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(c.monster.name, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        Text("Lv.${c.level} · ${c.monster.rarity.label}", fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun buildCombatant(m: com.shanhai.core.model.Monster, level: Int): Combatant {
    val stats = Stats(hp = m.baseHp + level * 10, atk = m.baseAtk + level * 2,
        def = m.baseDef + level, spd = m.baseSpd + level)
    return Combatant(monster = m, level = level, equippedSkills = m.skills.take(4),
        effective = stats, hp = stats.hp).also { it.ensureHp() }
}
