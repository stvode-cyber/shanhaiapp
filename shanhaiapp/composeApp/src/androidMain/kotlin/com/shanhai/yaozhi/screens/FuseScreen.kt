package com.shanhai.yaozhi.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shanhai.core.data.MonsterPool
import com.shanhai.core.logic.FusionEngine
import com.shanhai.core.model.MonsterInstance
import com.shanhai.yaozhi.LocalGameState
import com.shanhai.yaozhi.LocalGsVersion
import com.shanhai.yaozhi.mutate
import com.shanhai.yaozhi.components.AnimatedStatCard
import com.shanhai.yaozhi.components.GradientHeader
import com.shanhai.yaozhi.components.RarityChip
import com.shanhai.yaozhi.theme.*
import kotlinx.coroutines.launch

/**
 * 妖融合 · V3（视觉润色）
 * v3: GradientHeader + 双妖彩色底卡片 + 预览渐变面板 + 按钮缩放
 */
@Composable
fun FuseScreen(modifier: Modifier = Modifier) {
    val gsState = LocalGameState.current
    val versionState = LocalGsVersion.current
    val _v = versionState.value  // 订阅 → 融合后自动刷新
    val gs = gsState.value

    val owned = gs.owned.values.toList()
    val instA = owned.getOrElse(0) {
        MonsterInstance(instanceId = "auto_a", defId = MonsterPool.all[0].id, level = 5, exp = 0, breakthrough = 0)
    }
    val instB = owned.getOrElse(1) {
        MonsterInstance(instanceId = "auto_b", defId = MonsterPool.all[1].id, level = 5, exp = 0, breakthrough = 0)
    }

    val plan = FusionEngine.plan(instA, instB)
    val canFuse = FusionEngine.canFuse(instA, instB, gs.yaodan)
    val scope = rememberCoroutineScope()
    val btnScale = remember { Animatable(1f) }

    Column(modifier = modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {

        // v3: GradientHeader + 妖丹统计
        GradientHeader(
            title = "妖融合",
            subtitle = "妖丹 ${gs.yaodan}"
        )

        // 融合消耗
        AnimatedStatCard("消耗妖丹", FusionEngine.FUSE_COST_YAODAN, "🧪", ZhuSha)

        // 双妖卡片（彩色底 + 稀有度色）
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val aDef = MonsterPool.byId(instA.defId)
            val aColor = if (aDef != null) colorForRarity(aDef.rarity) else YanHui
            MonsterInstanceCardV3("A", aDef?.name ?: "未知", instA.level, aColor, Modifier.weight(1f))

            Box(modifier = Modifier.size(36.dp).clip(CircleShape)
                .background(QingDai.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center) {
                Text("+", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = QingDai)
            }

            val bDef = MonsterPool.byId(instB.defId)
            val bColor = if (bDef != null) colorForRarity(bDef.rarity) else YanHui
            MonsterInstanceCardV3("B", bDef?.name ?: "未知", instB.level, bColor, Modifier.weight(1f))
        }

        // v3: 融合预览渐变面板
        plan?.let { p ->
            val newDef = MonsterPool.byId(p.newDefId)
            val newColor = if (newDef != null) colorForRarity(newDef.rarity) else YanHui
            Surface(shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()) {
                Box(modifier = Modifier.fillMaxWidth()
                    .background(
                        brush = androidx.compose.ui.graphics.Brush.linearGradient(
                            listOf(
                                JinHuang.copy(alpha = 0.12f),
                                newColor.copy(alpha = 0.1f)
                            ),
                            androidx.compose.ui.geometry.Offset(0f, 0f),
                            androidx.compose.ui.geometry.Offset(1f, 1f)
                        ))
                    .padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(
                            modifier = Modifier.size(50.dp).clip(CircleShape)
                                .background(newColor),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(newDef?.name?.take(1) ?: "?",
                                color = XueBai, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text("融合预览", fontSize = 12.sp, color = YanHui)
                            Text(newDef?.name ?: "未知",
                                fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MoHei)
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("Lv.${p.newLevel}", fontSize = 12.sp, color = MoHei)
                                Text("·", fontSize = 12.sp)
                                Text("突破 ${p.newBreakthrough}", fontSize = 12.sp, color = ZhuSha)
                            }
                        }
                        if (newDef != null) {
                            RarityChip(newDef.rarity)
                        }
                    }
                }
            }
        } ?: Surface(shape = RoundedCornerShape(12.dp), color = XuanZhi.copy(alpha = 0.5f),
            modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally) {
                Text("⚗️", fontSize = 32.sp)
                Text("当前组合无可用配方", fontSize = 13.sp, color = YanHui)
            }
        }

        // v3: 按钮缩放
        Button(
            onClick = {
                scope.launch {
                    btnScale.animateTo(0.94f, tween(80))
                    btnScale.animateTo(1f, tween(150))
                }
                gsState.mutate(versionState) { it.fuse(instA.instanceId, instB.instanceId) }
            },
            enabled = canFuse && owned.size >= 2,
            modifier = Modifier.fillMaxWidth().height(52.dp)
                .graphicsLayer { scaleX = btnScale.value; scaleY = btnScale.value },
            colors = ButtonDefaults.buttonColors(containerColor = if (canFuse && owned.size >= 2) ZhuSha else YanHui.copy(alpha = 0.5f))
        ) { Text("开始融合", fontSize = 16.sp, fontWeight = FontWeight.Bold) }
    }
}

/** v3: 妖实例彩色卡片 */
@Composable
private fun MonsterInstanceCardV3(
    slot: String,
    name: String,
    level: Int,
    color: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier
) {
    Surface(shape = RoundedCornerShape(14.dp), tonalElevation = 2.dp, modifier = modifier) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier.size(44.dp).clip(CircleShape)
                    .background(color.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Text(slot, color = color, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
            Column {
                Text(name, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Text("Lv.$level", fontSize = 11.sp, color = YanHui)
            }
        }
    }
}
