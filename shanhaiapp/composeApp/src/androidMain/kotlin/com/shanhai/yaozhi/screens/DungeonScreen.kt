package com.shanhai.yaozhi.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shanhai.core.data.defaultRandom
import com.shanhai.core.logic.DungeonDef
import com.shanhai.core.logic.DungeonEngine
import com.shanhai.core.logic.DungeonLibrary
import com.shanhai.core.logic.DungeonType
import com.shanhai.yaozhi.components.EmptyState

@Composable
fun DungeonScreen(modifier: Modifier = Modifier) {
    val all = remember { DungeonLibrary.all }
    val grouped = remember(all) { all.groupBy { d: DungeonDef -> d.type } }
    var selected by remember { mutableStateOf<DungeonDef?>(null) }
    val runState = remember { mutableStateOf(DungeonEngine.DungeonRunState()) }
    var lastReward by remember { mutableStateOf<DungeonEngine.DungeonReward?>(null) }

    Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
        Text("副本妖境", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))

        val listEmpty = grouped.values.all { it.isEmpty() }
        if (listEmpty) {
            EmptyState("📜", "当前没有可用副本")
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            grouped.forEach { (type: DungeonType, list: List<DungeonDef>) ->
                if (list.isEmpty()) return@forEach
                item { Text(type.label, fontSize = 14.sp, fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.primary) }
                items(list) { d ->
                    val usedToday = runState.value.enteredToday[d.id] ?: 0
                    val remaining = d.dailyLimit - usedToday
                    Surface(shape = MaterialTheme.shapes.medium, tonalElevation = 1.dp,
                        modifier = Modifier.fillMaxWidth()) {
                        Row(modifier = Modifier.padding(12.dp).fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(d.name, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                Text(d.desc, fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("消耗 ${d.costIncense} 香火 · 今日剩余 $remaining/${d.dailyLimit}", fontSize = 12.sp)
                            }
                            Button(onClick = { selected = d },
                                enabled = remaining > 0,
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp)) {
                                Text(if (remaining > 0) "进入" else "已用完", fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }
    }

    selected?.let { d ->
        AlertDialog(
            onDismissRequest = { selected = null },
            title = { Text("进入 ${d.name}") },
            text = {
                Column {
                    Text(d.desc)
                    Spacer(Modifier.height(8.dp))
                    Text("消耗：${d.costIncense} 香火 · 日限：${d.dailyLimit}")
                    Text("妖丹：${d.yaodanRange} · 材料：${d.materialRange} · 符纸概率：${"%.0f".format(d.talismanChance * 100)}%")
                    if (lastReward != null) {
                        Spacer(Modifier.height(8.dp))
                        val r = lastReward!!
                        Text("上次通关：妖丹 ${r.yaodan} · 材料 ${r.material} ${if (r.talisman) "· 符纸" else ""}${if (r.skin) "· 外观" else ""}")
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    lastReward = DungeonEngine.enter(d, runState.value) { defaultRandom() }
                    runState.value = runState.value.copy()  // 强制重组：MutableMap 内部变化不触发 Compose
                    selected = null
                }) { Text("确认进入") }
            },
            dismissButton = { OutlinedButton(onClick = { selected = null }) { Text("取消") } }
        )
    }
}

private val DungeonType.label: String get() = when (this) {
    DungeonType.YIWEN -> "🗡️ 山海异闻（剧情）"
    DungeonType.SHILIAN -> "⚔️ 灵脉试炼（日常）"
    DungeonType.DUOJIE -> "🔥 渡劫秘境（成长）"
    DungeonType.NIANJIE -> "🎊 年节限时（活动）"
}
