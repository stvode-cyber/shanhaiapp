package com.shanhai.yaozhi.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shanhai.core.quest.QuestLibrary
import com.shanhai.yaozhi.LocalGameState
import com.shanhai.yaozhi.LocalGsVersion
import com.shanhai.yaozhi.components.AnimatedStatCard
import com.shanhai.yaozhi.components.EmptyState
import com.shanhai.yaozhi.components.GradientHeader
import com.shanhai.yaozhi.theme.*

/**
 * 敕令任务 · V3（视觉润色）
 * v3: GradientHeader + 任务卡片彩色类型标签
 */
@Composable
fun QuestsScreen(modifier: Modifier = Modifier) {
    val gsState = LocalGameState.current
    val _v = LocalGsVersion.current.value
    val gs = gsState.value
    val quests = QuestLibrary.all

    Column(modifier = modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {

        GradientHeader(title = "敕令任务", subtitle = "${quests.size} 条可接")

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AnimatedStatCard("灵玉", gs.lingyu, "💎", JinHuang, Modifier.weight(1f))
            AnimatedStatCard("妖丹", gs.yaodan, "🧪", ZhuSha, Modifier.weight(1f))
        }

        Spacer(Modifier.height(4.dp))

        if (quests.isEmpty()) {
            EmptyState("📜", "暂无可接任务")
        }
        quests.forEachIndexed { i, q ->
            val cardColor = when (i % 4) {
                0 -> QingDai; 1 -> ZhuSha; 2 -> JinHuang; else -> ZhuShaSoft
            }
            Surface(shape = RoundedCornerShape(12.dp), tonalElevation = 1.dp,
                modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Surface(shape = RoundedCornerShape(6.dp),
                            color = cardColor.copy(alpha = 0.15f)) {
                            Text("任务", fontSize = 10.sp, color = cardColor,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                        }
                        Text(q.title, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MoHei)
                    }
                    Text(q.desc, fontSize = 12.sp, color = YanHui)
                    Surface(shape = RoundedCornerShape(8.dp),
                        color = JinHuang.copy(alpha = 0.1f)) {
                        Text("🎁 奖励：${q.reward}", fontSize = 12.sp, color = JinHuang,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                    }
                }
            }
        }
    }
}
