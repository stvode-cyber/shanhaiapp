package com.shanhai.yaozhi.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shanhai.core.data.EquipmentLibrary
import com.shanhai.yaozhi.LocalGameState
import com.shanhai.yaozhi.LocalGsVersion
import com.shanhai.yaozhi.components.EmptyState

/** 装备库（只读展示）。 */
@Composable
fun EquipScreen(modifier: Modifier = Modifier) {
    val gsState = LocalGameState.current
    val _v = LocalGsVersion.current.value  // 订阅 → 资源变化自动刷新
    val gs = gsState.value
    val equips = EquipmentLibrary.all

    Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
        Text("装备库（${equips.size} 件）", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text("灵玉 ${gs.lingyu} ｜ 妖丹 ${gs.yaodan}", fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(12.dp))
        if (equips.isEmpty()) {
            EmptyState("🛡️", "暂无装备")
        } else {
            equips.forEach { e ->
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    tonalElevation = 2.dp,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("${e.name}（${e.rarity.label}）", fontSize = 16.sp, fontWeight = FontWeight.Medium)
                        Text("槽位 ${e.slot.name} · 五行 ${e.element?.name ?: "-"} · 攻${e.baseAtk} 防${e.baseDef} 速${e.baseSpd}",
                            fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}
