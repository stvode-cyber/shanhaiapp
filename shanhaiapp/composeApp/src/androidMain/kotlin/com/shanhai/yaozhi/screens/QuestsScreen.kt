package com.shanhai.yaozhi.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shanhai.core.quest.QuestLibrary

@Composable
fun QuestsScreen(modifier: Modifier = Modifier) {
    val quests = QuestLibrary.all
    Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
        Text("敕令任务（${quests.size} 条）", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        quests.forEach { q ->
            Surface(shape = RoundedCornerShape(10.dp), tonalElevation = 1.dp,
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
