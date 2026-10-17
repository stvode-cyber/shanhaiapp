package com.shanhai.yaozhi.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shanhai.core.quest.QuestLibrary
import com.shanhai.yaozhi.LocalGameState
import com.shanhai.yaozhi.LocalGsVersion
import com.shanhai.yaozhi.components.EmptyState

/** 敕令任务（只读展示）。 */
@Composable
fun QuestsScreen(modifier: Modifier = Modifier) {
    val gsState = LocalGameState.current
    val _v = LocalGsVersion.current.value  // 订阅 → 资源变化自动刷新
    val gs = gsState.value
    val quests = QuestLibrary.all

    Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
        Text("敕令任务（${quests.size} 条）", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text("灵玉 ${gs.lingyu} ｜ 妖丹 ${gs.yaodan}", fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(12.dp))
        if (quests.isEmpty()) {
            EmptyState("📜", "暂无可接任务")
        }
        quests.forEach { q ->
            Surface(shape = MaterialTheme.shapes.medium, tonalElevation = 1.dp,
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(q.title, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Text(q.desc, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("奖励：${q.reward}", fontSize = 12.sp)
                }
            }
        }
    }
}
