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
import com.shanhai.yaozhi.LocalGameState
import com.shanhai.yaozhi.LocalGsVersion
import com.shanhai.yaozhi.mutate
import com.shanhai.yaozhi.components.EmptyState

/**
 * 妖融合 · V2
 * 从 gs.owned 取两只妖 + gs.yaodan + gs.fuse(aId, bId)
 */
@Composable
fun FuseScreen(modifier: Modifier = Modifier) {
    val gsState = LocalGameState.current
    val versionState = LocalGsVersion.current
    val _v = versionState.value  // 订阅 → 融合后自动刷新
    val gs = gsState.value

    val owned = gs.owned.values.toList()
    val instA = owned.getOrElse(0) { MonsterInstance(instanceId = "auto_a", defId = MonsterPool.all[0].id, level = 5, exp = 0, breakthrough = 0) }
    val instB = owned.getOrElse(1) { MonsterInstance(instanceId = "auto_b", defId = MonsterPool.all[1].id, level = 5, exp = 0, breakthrough = 0) }

    val plan = FusionEngine.plan(instA, instB)
    val canFuse = FusionEngine.canFuse(instA, instB, gs.yaodan)

    Column(modifier = modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("妖融合", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text("妖丹：${gs.yaodan}", fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant)

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(Modifier.weight(1f)) {
                val aDef = MonsterPool.byId(instA.defId)
                Surface(shape = MaterialTheme.shapes.medium, tonalElevation = 2.dp,
                    modifier = Modifier.fillMaxWidth()) {
                    Text("A: ${aDef?.name ?: "未知"}（Lv.${instA.level}）",
                        modifier = Modifier.padding(12.dp), fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }
            Text("+", fontSize = 24.sp, modifier = Modifier.align(Alignment.CenterVertically))
            Column(Modifier.weight(1f)) {
                val bDef = MonsterPool.byId(instB.defId)
                Surface(shape = MaterialTheme.shapes.medium, tonalElevation = 2.dp,
                    modifier = Modifier.fillMaxWidth()) {
                    Text("B: ${bDef?.name ?: "未知"}（Lv.${instB.level}）",
                        modifier = Modifier.padding(12.dp), fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        Text("消耗妖丹：${FusionEngine.FUSE_COST_YAODAN}（你有 ${gs.yaodan}）")

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
        } ?: EmptyState("⚗️", "当前组合无可用配方")

        Button(
            onClick = {
                gsState.mutate(versionState) { it.fuse(instA.instanceId, instB.instanceId) }
            },
            enabled = canFuse && owned.size >= 2,
            modifier = Modifier.fillMaxWidth()
        ) { Text("开始融合") }
    }
}
