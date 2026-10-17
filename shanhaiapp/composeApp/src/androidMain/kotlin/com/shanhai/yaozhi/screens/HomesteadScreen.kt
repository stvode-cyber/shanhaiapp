package com.shanhai.yaozhi.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shanhai.core.logic.HomesteadEngine

@Composable
fun HomesteadScreen(modifier: Modifier = Modifier) {
    var fengshui by remember { mutableIntStateOf(0) }
    var offlineHours by remember { mutableIntStateOf(0) }

    val incRate = HomesteadEngine.incenseRate(fengshui)
    val pillRate = HomesteadEngine.pillRate(fengshui)
    val atkBonus = HomesteadEngine.fengshuiAttackBonus(fengshui)
    val offline = HomesteadEngine.compute(
        fengshui,
        nowMs = offlineHours * HomesteadEngine.HOUR_MS,
        lastMs = 0L
    )

    Column(modifier = modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("家园洞府", fontSize = 22.sp, fontWeight = FontWeight.Bold)

        Surface(shape = RoundedCornerShape(12.dp), tonalElevation = 2.dp,
            modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("阵法风水 · Lv.$fengshui", fontSize = 16.sp, fontWeight = FontWeight.Medium)
                Text("香火时产：${"%.1f".format(incRate)} / 小时", fontSize = 14.sp)
                Text("妖丹时产：${"%.2f".format(pillRate)} / 小时", fontSize = 14.sp)
                Text("全局攻击增益：${"%.0f".format(atkBonus * 100)}%", fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.primary)
                Text("升级花费：${HomesteadEngine.upgradeCost(fengshui)} 妖丹", fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { fengshui++ }, modifier = Modifier.weight(1f)) { Text("升级阵法") }
            OutlinedButton(onClick = { fengshui = 0 }, modifier = Modifier.weight(1f)) { Text("重置") }
        }

        HorizontalDivider()
        Text("离线产出模拟", fontSize = 16.sp, fontWeight = FontWeight.Medium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(1, 4, 8, 12).forEach { h ->
                FilterChip(selected = offlineHours == h,
                    onClick = { offlineHours = h },
                    label = { Text("${h}h", fontSize = 12.sp) })
            }
        }
        Text("离线香火：${offline.incense} · 妖丹：${offline.pills}", fontSize = 14.sp)
        Text("计费小时：${"%.1f".format(offline.hours)}${if (offline.capped) "（触顶 ${HomesteadEngine.MAX_OFFLINE_HOURS}h）" else ""}",
            fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
