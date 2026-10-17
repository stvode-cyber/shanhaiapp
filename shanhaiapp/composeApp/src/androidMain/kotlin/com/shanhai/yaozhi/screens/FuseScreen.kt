package com.shanhai.yaozhi.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shanhai.core.data.MonsterPool
import com.shanhai.core.logic.FusionEngine
import com.shanhai.core.model.MonsterInstance
import com.shanhai.yaozhi.components.EmptyState

@Composable
fun FuseScreen(modifier: Modifier = Modifier) {
    val aDefId = MonsterPool.all[0].id
    val bDefId = MonsterPool.all[1].id
    val instA = remember(aDefId) { MonsterInstance(instanceId = "fuse_a", defId = aDefId, level = 5, exp = 0, breakthrough = 0) }
    val instB = remember(bDefId) { MonsterInstance(instanceId = "fuse_b", defId = bDefId, level = 5, exp = 0, breakthrough = 0) }
    var yaodan by remember { mutableIntStateOf(3) }

    val plan = FusionEngine.plan(instA, instB)

    Column(modifier = modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("妖融合", fontSize = 22.sp, fontWeight = FontWeight.Bold)

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(Modifier.weight(1f)) {
                val aDef = MonsterPool.all.first { it.id == instA.defId }
                Surface(shape = MaterialTheme.shapes.medium, tonalElevation = 2.dp,
                    modifier = Modifier.fillMaxWidth()) {
                    Text("A: ${aDef.name}", modifier = Modifier.padding(12.dp), fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }
            Text("+", fontSize = 24.sp, modifier = Modifier.align(androidx.compose.ui.Alignment.CenterVertically))
            Column(Modifier.weight(1f)) {
                val bDef = MonsterPool.all.first { it.id == instB.defId }
                Surface(shape = MaterialTheme.shapes.medium, tonalElevation = 2.dp,
                    modifier = Modifier.fillMaxWidth()) {
                    Text("B: ${bDef.name}", modifier = Modifier.padding(12.dp), fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        Text("消耗妖丹：$yaodan")

        plan?.let { p ->
            val newDef = MonsterPool.byId(p.newDefId)
            Text("融合预览", fontSize = 16.sp, fontWeight = FontWeight.Medium)
            Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("产出：${newDef?.name ?: "未知"}", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Text("等级：${p.newLevel} · 突破：${p.newBreakthrough}", fontSize = 14.sp)
                }
            }
        } ?: Text("无可用配方", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
