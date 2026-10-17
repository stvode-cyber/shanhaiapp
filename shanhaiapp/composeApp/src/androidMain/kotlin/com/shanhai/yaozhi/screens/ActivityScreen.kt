package com.shanhai.yaozhi.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shanhai.core.logic.ActivityEngine

@Composable
fun ActivityScreen(modifier: Modifier = Modifier) {
    val activities = remember { ActivityEngine.ACTIVITIES }
    var today by remember { mutableStateOf("2026-10-17") }
    val active = remember(today) { ActivityEngine.active(today) }
    val bonuses = remember(active) { ActivityEngine.aggregate(active) }

    Column(modifier = modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("限时活动", fontSize = 22.sp, fontWeight = FontWeight.Bold)

        Surface(shape = RoundedCornerShape(12.dp), tonalElevation = 2.dp,
            modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("今日：$today", fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("进行中：${active.size} 个活动", fontSize = 16.sp,
                    fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                Text("乘性聚合：收妖 ×${"%.2f".format(bonuses.captureMul)} · 经验 ×${"%.2f".format(bonuses.expMul)} · 收益 ×${"%.2f".format(bonuses.incomeMul)}",
                    fontSize = 12.sp)
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("2026-10-15", "2026-10-17", "2026-10-25", "2026-02-10").forEach { d ->
                FilterChip(selected = today == d,
                    onClick = { today = d },
                    label = { Text(d.takeLast(5), fontSize = 12.sp) })
            }
        }

        Text("全部活动（${activities.size}）", fontSize = 16.sp, fontWeight = FontWeight.Medium)

        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(activities) { a ->
                val isActive = ActivityEngine.isActive(a, today)
                Surface(shape = RoundedCornerShape(10.dp),
                    color = if (isActive) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("${a.kind.label} ${if (isActive) "✨ 进行中" else "· 未开启"}",
                            fontSize = 12.sp,
                            color = if (isActive) MaterialTheme.colorScheme.primary
                                   else MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(a.name, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Text(a.desc, fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (a.captureMul != 1.0) Text("收妖 ×${a.captureMul}", fontSize = 11.sp)
                            if (a.expMul != 1.0) Text("经验 ×${a.expMul}", fontSize = 11.sp)
                            if (a.incomeMul != 1.0) Text("收益 ×${a.incomeMul}", fontSize = 11.sp)
                        }
                        Text("${a.start} → ${a.end}", fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
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
