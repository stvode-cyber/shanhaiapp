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
import com.shanhai.core.data.defaultRandom
import com.shanhai.core.logic.DungeonDef
import com.shanhai.core.logic.DungeonEngine
import com.shanhai.core.logic.DungeonLibrary
import com.shanhai.core.logic.DungeonType
import com.shanhai.yaozhi.LocalGameState
import com.shanhai.yaozhi.LocalGsVersion
import com.shanhai.yaozhi.mutate
import com.shanhai.yaozhi.components.AnimatedStatCard
import com.shanhai.yaozhi.components.EmptyState
import com.shanhai.yaozhi.components.GradientHeader
import com.shanhai.yaozhi.theme.*
import kotlinx.coroutines.launch

/**
 * 副本妖境 · V3（视觉润色）
 * v3: GradientHeader + 副本分组彩色 + 剩余次数进度条 + 弹窗渐变底
 */
@Composable
fun DungeonScreen(modifier: Modifier = Modifier) {
    val gsState = LocalGameState.current
    val versionState = LocalGsVersion.current
    val _v = versionState.value
    val gs = gsState.value
    val all = remember { DungeonLibrary.all }
    val grouped = remember(all) { all.groupBy { d: DungeonDef -> d.type } }
    var selected by remember { mutableStateOf<DungeonDef?>(null) }
    val runState = remember { mutableStateOf(DungeonEngine.DungeonRunState()) }
    var lastReward by remember { mutableStateOf<DungeonEngine.DungeonReward?>(null) }

    Column(modifier = modifier.fillMaxSize().padding(16.dp)) {

        GradientHeader(
            title = "副本妖境",
            subtitle = "香火 ${gs.incense} · 妖丹 ${gs.yaodan}"
        )

        Spacer(Modifier.height(8.dp))

        val listEmpty = grouped.values.all { it.isEmpty() }
        if (listEmpty) {
            EmptyState("📜", "当前没有可用副本")
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            grouped.forEach { (type: DungeonType, list: List<DungeonDef>) ->
                if (list.isEmpty()) return@forEach
                val typeColor = when (type) {
                    DungeonType.YIWEN -> QingDai
                    DungeonType.SHILIAN -> ZhuSha
                    DungeonType.DUOJIE -> JinHuang
                    DungeonType.NIANJIE -> ZhuShaSoft
                }
                item {
                    Surface(shape = RoundedCornerShape(8.dp),
                        color = typeColor.copy(alpha = 0.1f)) {
                        Text(type.label, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                            color = typeColor, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
                    }
                }
                items(list) { d ->
                    val usedToday = runState.value.enteredToday[d.id] ?: 0
                    val remaining = d.dailyLimit - usedToday
                    val btnScope = rememberCoroutineScope()
                    val btnScale = remember { Animatable(1f) }
                    Surface(shape = RoundedCornerShape(14.dp), tonalElevation = 1.dp,
                        modifier = Modifier.fillMaxWidth()) {
                        Row(modifier = Modifier.padding(14.dp).fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(d.name, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MoHei)
                                Text(d.desc, fontSize = 12.sp, color = YanHui)
                                Spacer(Modifier.height(6.dp))
                                Row(verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Surface(shape = RoundedCornerShape(6.dp),
                                        color = QingDai.copy(alpha = 0.12f)) {
                                        Text("🔥 ${d.costIncense}", fontSize = 11.sp, color = QingDai,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                                    }
                                    // 剩余次数进度条
                                    Box(modifier = Modifier.weight(1f).height(6.dp)
                                        .clip(RoundedCornerShape(3.dp))
                                        .background(XuanZhi.copy(alpha = 0.3f))) {
                                        Box(modifier = Modifier.fillMaxHeight()
                                            .fillMaxWidth((remaining.toFloat() / d.dailyLimit).coerceIn(0f, 1f))
                                            .clip(RoundedCornerShape(3.dp))
                                            .background(typeColor))
                                    }
                                    Text("$remaining/${d.dailyLimit}", fontSize = 11.sp,
                                        color = if (remaining > 0) typeColor else YanHui,
                                        fontWeight = FontWeight.Bold)
                                }
                            }
                            Button(onClick = {
                                btnScope.launch { btnScale.animateTo(0.94f, tween(80)); btnScale.animateTo(1f, tween(150)) }
                                selected = d
                            }, enabled = remaining > 0,
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
                                modifier = Modifier.graphicsLayer { scaleX = btnScale.value; scaleY = btnScale.value },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (remaining > 0) typeColor else YanHui.copy(alpha = 0.4f))) {
                                Text(if (remaining > 0) "进入" else "已用完", fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }
    }

    selected?.let { d ->
        val typeColor = when (d.type) {
            DungeonType.YIWEN -> QingDai
            DungeonType.SHILIAN -> ZhuSha
            DungeonType.DUOJIE -> JinHuang
            DungeonType.NIANJIE -> ZhuShaSoft
        }
        AlertDialog(
            onDismissRequest = { selected = null },
            title = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Text(d.name, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Surface(shape = RoundedCornerShape(6.dp), color = typeColor.copy(alpha = 0.15f)) {
                        Text(d.type.label.take(2), fontSize = 10.sp, color = typeColor,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                    }
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Surface(shape = RoundedCornerShape(12.dp),
                        color = typeColor.copy(alpha = 0.08f),
                        modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(d.desc, fontSize = 13.sp, color = MoHei)
                            Text("消耗：${d.costIncense} 香火 · 日限：${d.dailyLimit}", fontSize = 12.sp, color = YanHui)
                            Text("妖丹：${d.yaodanRange} · 材料：${d.materialRange} · 符纸 ${"%.0f".format(d.talismanChance * 100)}%",
                                fontSize = 12.sp, color = YanHui)
                        }
                    }
                    if (lastReward != null) {
                        Surface(shape = RoundedCornerShape(10.dp),
                            color = JinHuang.copy(alpha = 0.1f),
                            modifier = Modifier.fillMaxWidth()) {
                            Text("上次通关：妖丹 ${lastReward!!.yaodan} · 材料 ${lastReward!!.material} ${if (lastReward!!.talisman) "· 符纸" else ""}${if (lastReward!!.skin) "· 外观" else ""}",
                                fontSize = 12.sp, color = JinHuang,
                                modifier = Modifier.padding(10.dp))
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    val def = d!!
                    gsState.mutate(versionState) { gs ->
                        if (gs.incense >= def.costIncense) gs.incense -= def.costIncense
                    }
                    val reward = DungeonEngine.enter(def, runState.value) { defaultRandom() }
                    runState.value = runState.value.copy()
                    gsState.mutate(versionState) { gs ->
                        gs.yaodan += reward.yaodan
                    }
                    lastReward = reward
                    selected = null
                }, enabled = gs.incense >= d.costIncense) { Text("确认进入（-${d.costIncense} 香火）") }
            },
            dismissButton = { OutlinedButton(onClick = { selected = null }) { Text("取消") } }
        )
    }
}

private val DungeonType.label: String get() = when (this) {
    DungeonType.YIWEN -> "山海异闻"; DungeonType.SHILIAN -> "灵脉试炼"
    DungeonType.DUOJIE -> "渡劫秘境"; DungeonType.NIANJIE -> "年节限时"
}
