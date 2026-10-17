package com.shanhai.yaozhi.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shanhai.core.logic.SectTechEngine
import com.shanhai.yaozhi.components.EmptyState

@Composable
fun SectScreen(modifier: Modifier = Modifier) {
    val nodes = remember { SectTechEngine.NODES }
    var levels by remember { mutableStateOf(mutableMapOf<String, Int>()) }

    val bonuses = SectTechEngine.bonuses(levels, faction = null)

    Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
        Text("宗门科技树", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text("总加成：ATK +${"%.0f".format(bonuses.attack * 100)}% · DEF +${"%.0f".format(bonuses.defense * 100)}% · CAPTURE +${"%.0f".format(bonuses.capture * 100)}%",
            fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(8.dp))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (nodes.isEmpty()) {
                item { EmptyState("🏯", "宗门科技树暂无节点") }
            }
            items(nodes) { n ->
                val lv = levels[n.id] ?: 0
                val canResearch = SectTechEngine.canResearch(n.id, levels, null, contribution = Int.MAX_VALUE)
                Surface(shape = MaterialTheme.shapes.medium, tonalElevation = 1.dp,
                    modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.padding(12.dp).fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(n.name, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                            Text("${n.type.label} · Lv.$lv/${n.maxLevel} · +${"%.0f".format(n.perLevel * 100)}%/级",
                                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(n.desc, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Button(
                            onClick = {
                                val res = SectTechEngine.research(n.id, levels, null, Int.MAX_VALUE)
                                res?.let { levels = it.first.toMutableMap() }
                            },
                            enabled = canResearch,
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp)
                        ) {
                            Text(if (lv < n.maxLevel) "+${SectTechEngine.researchCost(lv, n)}" else "MAX", fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}

private val SectTechEngine.BonusType.label: String get() = when (this) {
    SectTechEngine.BonusType.ATTACK -> "攻击"; SectTechEngine.BonusType.DEFENSE -> "防御"
    SectTechEngine.BonusType.HP -> "生命"; SectTechEngine.BonusType.CAPTURE -> "御妖"
    SectTechEngine.BonusType.EXP -> "经验"; SectTechEngine.BonusType.INCOME -> "收益"
    SectTechEngine.BonusType.STRONGHOLD -> "据点"
}
