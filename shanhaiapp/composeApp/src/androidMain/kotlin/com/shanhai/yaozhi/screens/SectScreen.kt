package com.shanhai.yaozhi.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.shanhai.core.logic.SectTechEngine
import com.shanhai.yaozhi.LocalGameState
import com.shanhai.yaozhi.LocalGsVersion
import com.shanhai.yaozhi.components.AnimatedStatCard
import com.shanhai.yaozhi.components.EmptyState
import com.shanhai.yaozhi.components.GradientHeader
import com.shanhai.yaozhi.theme.*
import kotlinx.coroutines.launch

/**
 * 宗门科技树 · V3（视觉润色）
 * v3: GradientHeader + 加成彩色卡片 + 节点彩色进度条 + 按钮缩放
 */
@Composable
fun SectScreen(modifier: Modifier = Modifier) {
    val gsState = LocalGameState.current
    val _v = LocalGsVersion.current.value
    val gs = gsState.value
    val nodes = remember { SectTechEngine.NODES }
    var levels by remember { mutableStateOf(mutableMapOf<String, Int>()) }

    val bonuses = SectTechEngine.bonuses(levels, faction = null)

    Column(modifier = modifier.fillMaxSize().padding(16.dp)) {

        // v3: GradientHeader
        GradientHeader(
            title = "宗门科技树",
            subtitle = "${nodes.size} 个可研究节点"
        )

        Spacer(Modifier.height(8.dp))

        // v3: 总加成彩色卡片
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AnimatedStatCard("攻击", (bonuses.attack * 100).toInt(), "⚔️", ZhuSha, Modifier.weight(1f))
            AnimatedStatCard("防御", (bonuses.defense * 100).toInt(), "🛡️", QingDai, Modifier.weight(1f))
            AnimatedStatCard("御妖", (bonuses.capture * 100).toInt(), "🎯", JinHuang, Modifier.weight(1f))
        }

        Spacer(Modifier.height(12.dp))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (nodes.isEmpty()) {
                item { EmptyState("🏯", "宗门科技树暂无节点") }
            }
            items(nodes) { n ->
                val lv = levels[n.id] ?: 0
                val canResearch = SectTechEngine.canResearch(n.id, levels, null, contribution = Int.MAX_VALUE)
                val typeColor = when (n.type) {
                    SectTechEngine.BonusType.ATTACK -> ZhuSha
                    SectTechEngine.BonusType.DEFENSE -> QingDai
                    SectTechEngine.BonusType.CAPTURE -> JinHuang
                    SectTechEngine.BonusType.HP -> ZhuShaSoft
                    SectTechEngine.BonusType.EXP -> QingDaiLight
                    SectTechEngine.BonusType.INCOME -> JinHuang
                    SectTechEngine.BonusType.STRONGHOLD -> MoHei
                }
                val btnScope = rememberCoroutineScope()
                val scale = remember { Animatable(1f) }

                Surface(shape = RoundedCornerShape(14.dp), tonalElevation = 1.dp,
                    modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.padding(12.dp).fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(n.name, fontSize = 15.sp, fontWeight = FontWeight.Bold,
                                    color = typeColor)
                                Surface(shape = RoundedCornerShape(6.dp),
                                    color = typeColor.copy(alpha = 0.15f)) {
                                    Text(n.type.label, fontSize = 10.sp, color = typeColor,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                                }
                            }
                            Text("Lv.$lv/${n.maxLevel} · +${"%.0f".format(n.perLevel * 100)}%/级",
                                fontSize = 12.sp, color = YanHui)
                            Text(n.desc, fontSize = 11.sp, color = YanHui)
                            // v3: 彩色进度条
                            Box(modifier = Modifier.fillMaxWidth().height(4.dp).padding(top = 4.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(XuanZhi.copy(alpha = 0.3f))) {
                                Box(modifier = Modifier.fillMaxHeight()
                                    .fillMaxWidth((lv.toFloat() / n.maxLevel).coerceIn(0f, 1f))
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(typeColor))
                            }
                        }
                        Button(
                            onClick = {
                                btnScope.launch { scale.animateTo(0.92f, tween(80)); scale.animateTo(1f, tween(150)) }
                                val res = SectTechEngine.research(n.id, levels, null, Int.MAX_VALUE)
                                res?.let { levels = it.first.toMutableMap() }
                            },
                            enabled = canResearch,
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                            modifier = Modifier.graphicsLayer { scaleX = scale.value; scaleY = scale.value },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (canResearch) typeColor else YanHui.copy(alpha = 0.4f))
                        ) {
                            Text(if (lv < n.maxLevel) "+${SectTechEngine.researchCost(lv, n)}" else "MAX", fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}

private val SectTechEngine.BonusType.label: String get() = when (this) {
    SectTechEngine.BonusType.ATTACK -> "攻击"; SectTechEngine.BonusType.DEFENSE -> "防御"
    SectTechEngine.BonusType.HP -> "生命"; SectTechEngine.BonusType.CAPTURE -> "御妖"
    SectTechEngine.BonusType.EXP -> "经验"; SectTechEngine.BonusType.INCOME -> "收益"
    SectTechEngine.BonusType.STRONGHOLD -> "据点"
}
