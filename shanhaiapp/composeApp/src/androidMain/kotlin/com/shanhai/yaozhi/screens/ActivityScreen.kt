package com.shanhai.yaozhi.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shanhai.core.logic.ActivityEngine
import com.shanhai.yaozhi.LocalGameState
import com.shanhai.yaozhi.LocalGsVersion
import com.shanhai.yaozhi.components.GradientHeader
import com.shanhai.yaozhi.theme.*

/**
 * 限时活动 · V3（视觉润色）
 * v3: GradientHeader + 活动聚合渐变底 + 进行中高亮
 */
@Composable
fun ActivityScreen(modifier: Modifier = Modifier) {
    val gsState = LocalGameState.current
    val _v = LocalGsVersion.current.value
    val gs = gsState.value
    val activities = remember { ActivityEngine.ACTIVITIES }
    var today by remember { mutableStateOf("2026-10-18") }
    val active = remember(today) { ActivityEngine.active(today) }
    val bonuses = remember(active) { ActivityEngine.aggregate(active) }

    Column(modifier = modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)) {

        GradientHeader(title = "限时活动", subtitle = "今日 $today · 进行中 ${active.size}")

        // v3: 活动聚合渐变底
        Surface(shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
            Box(modifier = Modifier.fillMaxWidth().background(
                brush = androidx.compose.ui.graphics.Brush.linearGradient(
                    listOf(JinHuang.copy(alpha = 0.12f), XuanZhi)))
                .padding(16.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("乘性聚合加成", fontSize = 13.sp, color = YanHui)
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Surface(shape = RoundedCornerShape(8.dp),
                            color = QingDai.copy(alpha = 0.15f)) {
                            Text("🎯 收妖 ×${"%.2f".format(bonuses.captureMul)}",
                                fontSize = 12.sp, color = QingDai,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp))
                        }
                        Surface(shape = RoundedCornerShape(8.dp),
                            color = ZhuSha.copy(alpha = 0.15f)) {
                            Text("⚔️ 经验 ×${"%.2f".format(bonuses.expMul)}",
                                fontSize = 12.sp, color = ZhuSha,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp))
                        }
                        Surface(shape = RoundedCornerShape(8.dp),
                            color = JinHuang.copy(alpha = 0.15f)) {
                            Text("💰 收益 ×${"%.2f".format(bonuses.incomeMul)}",
                                fontSize = 12.sp, color = JinHuang,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp))
                        }
                    }
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("2026-10-15", "2026-10-18", "2026-10-25", "2026-02-10").forEach { d ->
                FilterChip(selected = today == d,
                    onClick = { today = d },
                    label = { Text(d.takeLast(5), fontSize = 12.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = ZhuSha.copy(alpha = 0.15f),
                        selectedLabelColor = ZhuSha))
            }
        }

        Text("全部活动（${activities.size}）", fontSize = 16.sp, fontWeight = FontWeight.Medium)

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(activities) { a ->
                val isActive = ActivityEngine.isActive(a, today)
                val activeColor = when (a.kind) {
                    com.shanhai.core.logic.ActivityKind.FESTIVAL -> ZhuShaSoft
                    com.shanhai.core.logic.ActivityKind.CAPTURE_RALLY -> QingDai
                    com.shanhai.core.logic.ActivityKind.BATTLE_RALLY -> ZhuSha
                    com.shanhai.core.logic.ActivityKind.LOGIN -> JinHuang
                }
                Surface(shape = RoundedCornerShape(12.dp),
                    color = if (isActive) activeColor.copy(alpha = 0.1f)
                           else XuanZhi.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Surface(shape = RoundedCornerShape(6.dp),
                                color = activeColor.copy(alpha = 0.2f)) {
                                Text(a.kind.label, fontSize = 11.sp, color = activeColor,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
                            }
                            if (isActive) Surface(shape = RoundedCornerShape(6.dp),
                                color = ZhuSha.copy(alpha = 0.15f)) {
                                Text("✨ 进行中", fontSize = 11.sp, color = ZhuSha,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
                            }
                        }
                        Text(a.name, fontSize = 16.sp, fontWeight = FontWeight.Bold,
                            color = if (isActive) MoHei else YanHui)
                        Text(a.desc, fontSize = 12.sp, color = YanHui)
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            if (a.captureMul != 1.0) Text("🎯×${a.captureMul}", fontSize = 11.sp, color = QingDai)
                            if (a.expMul != 1.0) Text("⚔️×${a.expMul}", fontSize = 11.sp, color = ZhuSha)
                            if (a.incomeMul != 1.0) Text("💰×${a.incomeMul}", fontSize = 11.sp, color = JinHuang)
                        }
                        Text("${a.start} → ${a.end}", fontSize = 11.sp, color = YanHui)
                    }
                }
            }
        }
    }
}

private val com.shanhai.core.logic.ActivityKind.label: String get() = when (this) {
    com.shanhai.core.logic.ActivityKind.FESTIVAL -> "节庆"
    com.shanhai.core.logic.ActivityKind.CAPTURE_RALLY -> "收妖活动"
    com.shanhai.core.logic.ActivityKind.BATTLE_RALLY -> "对战活动"
    com.shanhai.core.logic.ActivityKind.LOGIN -> "登录有礼"
}
