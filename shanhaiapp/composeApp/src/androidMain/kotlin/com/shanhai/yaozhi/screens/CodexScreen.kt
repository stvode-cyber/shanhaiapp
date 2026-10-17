package com.shanhai.yaozhi.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shanhai.core.data.MonsterPool
import com.shanhai.core.logic.Codex
import com.shanhai.core.model.Monster
import com.shanhai.core.model.MonsterCategory
import com.shanhai.core.model.Rarity
import com.shanhai.yaozhi.LocalGameState
import com.shanhai.yaozhi.LocalGsVersion
import com.shanhai.yaozhi.components.EmptyState

/**
 * 妖典图鉴 · V2
 * 从 GameState.codex 取已捕获数据 + MonsterPool 全量定义
 */
@Composable
fun CodexScreen(modifier: Modifier = Modifier) {
    val gsState = LocalGameState.current
    val _v = LocalGsVersion.current.value  // 订阅 → 捕获后自动刷新
    val codex = gsState.value.codex
    val allMonsters = remember { MonsterPool.all }
    var rarityFilter by remember { mutableStateOf<Rarity?>(null) }
    var categoryFilter by remember { mutableStateOf<MonsterCategory?>(null) }
    var selectedMonster by remember { mutableStateOf<Monster?>(null) }

    val filtered = remember(allMonsters, rarityFilter, categoryFilter) {
        allMonsters.filter { m ->
            (rarityFilter == null || m.rarity == rarityFilter) &&
            (categoryFilter == null || m.category == categoryFilter)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // 头部
        Surface(
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("妖典图鉴", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text(
                    "${codex.regionCaught()}/${codex.regionTotal()}",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        // 筛选行
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = rarityFilter == null,
                onClick = { rarityFilter = null },
                label = { Text("全部稀有", fontSize = 12.sp) }
            )
            Rarity.values().forEach { r ->
                FilterChip(
                    selected = rarityFilter == r,
                    onClick = { rarityFilter = r },
                    label = { Text(r.label, fontSize = 12.sp) }
                )
            }
        }
        Row(
            modifier = Modifier.padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = categoryFilter == null,
                onClick = { categoryFilter = null },
                label = { Text("全部类", fontSize = 12.sp) }
            )
            MonsterCategory.values().forEach { c ->
                FilterChip(
                    selected = categoryFilter == c,
                    onClick = { categoryFilter = c },
                    label = { Text(c.label, fontSize = 12.sp) }
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        // 列表
        if (filtered.isEmpty()) {
            EmptyState("🔍", "没有符合筛选条件的妖")
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filtered, key = { it.id }) { monster ->
                    MonsterCard(monster, codex.snapshotRegion().contains(monster.id)) {
                        selectedMonster = monster
                    }
                }
            }
        }
    }

    // 详情弹窗
    selectedMonster?.let { m ->
        MonsterDetailDialog(m, codex) { selectedMonster = null }
    }
}

@Composable
private fun MonsterCard(monster: Monster, caught: Boolean, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = if (caught) MaterialTheme.colorScheme.surface
        else MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(colorForRarity(monster.rarity)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    if (caught) monster.name.take(1) else "?",
                    color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    if (caught) monster.name else "???",
                    fontSize = 16.sp, fontWeight = FontWeight.Bold
                )
                Text(
                    "${monster.rarity.label} · ${monster.element.label} · ${monster.category.label}",
                    fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (caught) {
                Icon(Icons.Filled.CheckCircle, null, tint = Color(0xFF27AE60), modifier = Modifier.size(20.dp))
            }
        }
    }
}

@Composable
private fun MonsterDetailDialog(monster: Monster, codex: Codex, onDismiss: () -> Unit) {
    val caught = codex.snapshotRegion().contains(monster.id)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(monster.name) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("稀有度：${monster.rarity.label}")
                Text("五行：${monster.element.label}")
                Text("类别：${monster.category.label}")
                HorizontalDivider()
                Text("HP ${monster.baseHp} · ATK ${monster.baseAtk} · DEF ${monster.baseDef} · SPD ${monster.baseSpd}")
                HorizontalDivider()
                Text(monster.desc.ifBlank { "暂无描述" }, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                HorizontalDivider()
                Text("已装备技能：", fontWeight = FontWeight.Medium)
                monster.skills.forEach { sk ->
                    Text("  · ${sk.name}（${sk.element.label} · 威力${sk.power}）", fontSize = 13.sp)
                }
            }
        },
        confirmButton = { Button(onClick = onDismiss) { Text("关闭") } }
    )
}

private fun colorForRarity(r: Rarity): Color = when (r) {
    Rarity.FAN -> Color(0xFF7F8C8D)
    Rarity.LING -> Color(0xFF3498DB)
    Rarity.XIAN -> Color(0xFF9B59B6)
    Rarity.SHEN -> Color(0xFFF39C12)
}

private val Rarity.label: String get() = when (this) {
    Rarity.FAN -> "凡"
    Rarity.LING -> "灵"
    Rarity.XIAN -> "仙"
    Rarity.SHEN -> "神"
}
private val MonsterCategory.label: String get() = when (this) {
    MonsterCategory.JING -> "精"
    MonsterCategory.GUAI -> "怪"
    MonsterCategory.YAO -> "妖"
    MonsterCategory.LINGSHOU -> "灵兽"
}
private val com.shanhai.core.model.Element.label: String get() = when (this) {
    com.shanhai.core.model.Element.METAL -> "金"
    com.shanhai.core.model.Element.WOOD -> "木"
    com.shanhai.core.model.Element.EARTH -> "土"
    com.shanhai.core.model.Element.WATER -> "水"
    com.shanhai.core.model.Element.FIRE -> "火"
    com.shanhai.core.model.Element.WIND -> "风"
    com.shanhai.core.model.Element.THUNDER -> "雷"
}
