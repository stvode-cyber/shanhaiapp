package com.shanhai.yaozhi.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shanhai.core.data.MonsterPool
import com.shanhai.core.logic.CultivationEngine
import com.shanhai.core.model.MonsterInstance
import com.shanhai.yaozhi.LocalGameState
import com.shanhai.yaozhi.LocalGsVersion
import com.shanhai.yaozhi.mutate
import com.shanhai.yaozhi.components.AnimatedStatCard
import com.shanhai.yaozhi.components.GradientHeader
import com.shanhai.yaozhi.components.RarityChip
import com.shanhai.yaozhi.theme.*
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.launch

/**
 * 养成 · V3（视觉润色）
 * v3: GradientHeader + 妖彩色底卡 + EXP 进度条 + 按钮缩放
 */
@Composable
fun CultivateScreen(modifier: Modifier = Modifier) {
    val gsState = LocalGameState.current
    val versionState = LocalGsVersion.current
    val _v = versionState.value  // 订阅 → mutate 后必重组
    val gs = gsState.value

    val firstInst = gs.owned.values.firstOrNull() ?: MonsterInstance(
        instanceId = "inst_auto", defId = MonsterPool.all[0].id,
        level = 5, exp = 0, breakthrough = 0
    )

    val def = MonsterPool.all.firstOrNull { it.id == firstInst.defId } ?: MonsterPool.all[0]
    val inst = gs.owned[firstInst.instanceId] ?: firstInst

    val levelCap = CultivationEngine.levelCap(inst)
    val btLevelReq = CultivationEngine.breakthroughLevelReq(inst.breakthrough)
    val btCost = CultivationEngine.breakthroughCost(inst.breakthrough)
    val canBT = CultivationEngine.canBreakthrough(inst)
    val canEvo = CultivationEngine.canEvolve(inst)
    val expNeed = CultivationEngine.expForLevel(inst.level + 1)

    Column(modifier = modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {

        // v3: GradientHeader + 妖彩色卡
        GradientHeader(
            title = "养成 · ${def.name}",
            subtitle = "Lv.${inst.level} · 突破 ${inst.breakthrough} 次"
        )

        // v3: 妖形象 + 稀有度光晕
        val rarityColor = colorForRarity(def.rarity)
        Surface(shape = RoundedCornerShape(14.dp), tonalElevation = 2.dp,
            modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.padding(14.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(modifier = Modifier.size(64.dp).clip(CircleShape)
                    .background(rarityColor.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center) {
                    Box(modifier = Modifier.size(52.dp).clip(CircleShape).background(rarityColor),
                        contentAlignment = Alignment.Center) {
                        Text(def.name.take(1), color = XueBai, fontSize = 24.sp,
                            fontWeight = FontWeight.Bold)
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(def.name, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        RarityChip(def.rarity, size = com.shanhai.yaozhi.components.ChipSize.SMALL)
                    }
                    Text("等级上限 $levelCap · 当前 EXP ${inst.exp}", fontSize = 12.sp, color = YanHui)
                    Spacer(Modifier.height(6.dp))
                    val expAnimated = remember { Animatable(0f) }
                    LaunchedEffect(inst.exp, expNeed) {
                        if (expNeed > 0) expAnimated.animateTo(
                            (inst.exp.toFloat() / expNeed).coerceIn(0f, 1f), tween(600))
                    }
                    Box(modifier = Modifier.fillMaxWidth().height(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(XuanZhi.copy(alpha = 0.3f))) {
                        Box(modifier = Modifier.fillMaxHeight()
                            .fillMaxWidth(expAnimated.value)
                            .clip(RoundedCornerShape(4.dp))
                            .background(brush = Brush.horizontalGradient(
                                listOf(QingDai, QingDaiLight))))
                    }
                    Text("→ Lv.${inst.level + 1} 需 $expNeed EXP", fontSize = 11.sp, color = YanHui)
                }
            }
        }

        // v3: 三操作按钮（彩色 + 缩放）
        val expScope = rememberCoroutineScope()
        val btScope = rememberCoroutineScope()
        val evoScope = rememberCoroutineScope()
        val expScale = remember { Animatable(1f) }
        val btScale = remember { Animatable(1f) }
        val evoScale = remember { Animatable(1f) }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                expScope.launch { expScale.animateTo(0.95f, tween(80)); expScale.animateTo(1f, tween(150)) }
                gsState.mutate(versionState) { it.gainExp(inst.instanceId, 500) }
            }, modifier = Modifier.weight(1f).graphicsLayer { scaleX = expScale.value; scaleY = expScale.value },
                colors = ButtonDefaults.buttonColors(containerColor = QingDai)) {
                Text("+500 EXP", fontSize = 13.sp)
            }
            Button(onClick = {
                expScope.launch { expScale.animateTo(0.95f, tween(80)); expScale.animateTo(1f, tween(150)) }
                gsState.mutate(versionState) { it.gainExp(inst.instanceId, 2000) }
            }, modifier = Modifier.weight(1f).graphicsLayer { scaleX = expScale.value; scaleY = expScale.value },
                colors = ButtonDefaults.buttonColors(containerColor = JinHuang)) {
                Text("+2000 EXP", fontSize = 13.sp)
            }
        }

        Divider()
        Text("突破（需 Lv.$btLevelReq，花费 $btCost 灵石）", fontSize = 12.sp, color = YanHui)
        Button(
            onClick = {
                btScope.launch { btScale.animateTo(0.94f, tween(80)); btScale.animateTo(1f, tween(150)) }
                gsState.mutate(versionState) { it.breakthroughMonster(inst.instanceId) }
            },
            enabled = canBT,
            modifier = Modifier.fillMaxWidth().height(48.dp)
                .graphicsLayer { scaleX = btScale.value; scaleY = btScale.value },
            colors = ButtonDefaults.buttonColors(
                containerColor = if (canBT) ZhuSha else YanHui.copy(alpha = 0.4f))
        ) { Text(if (canBT) "突破！" else "需 Lv.$btLevelReq", fontSize = 15.sp, fontWeight = FontWeight.Bold) }

        Divider()
        Text("进化", fontSize = 12.sp, color = YanHui)
        Button(
            onClick = {
                evoScope.launch { evoScale.animateTo(0.94f, tween(80)); evoScale.animateTo(1f, tween(150)) }
                gsState.mutate(versionState) { it.evolveMonster(inst.instanceId) }
            },
            enabled = canEvo,
            modifier = Modifier.fillMaxWidth().height(48.dp)
                .graphicsLayer { scaleX = evoScale.value; scaleY = evoScale.value },
            colors = ButtonDefaults.buttonColors(
                containerColor = if (canEvo) XianZi else YanHui.copy(alpha = 0.4f))
        ) { Text(if (canEvo) "进化！" else "无进化链或等级不足", fontSize = 15.sp) }

        Spacer(Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AnimatedStatCard("灵玉", gs.lingyu, "💎", JinHuang, Modifier.weight(1f))
            AnimatedStatCard("妖丹", gs.yaodan, "🧪", ZhuSha, Modifier.weight(1f))
        }
    }
}
