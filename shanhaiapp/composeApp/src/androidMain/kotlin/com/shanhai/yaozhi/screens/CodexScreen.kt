package com.shanhai.yaozhi.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
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
import com.shanhai.yaozhi.components.ChipSize
import com.shanhai.yaozhi.components.EmptyState
import com.shanhai.yaozhi.components.GradientHeader
import com.shanhai.yaozhi.components.RarityChip
import com.shanhai.yaozhi.theme.*
import kotlinx.coroutines.launch

/**
 * 妖典图鉴 · V3（视觉润色）
 * v3: GradientHeader 标题栏 + RarityChip 统一标签 + MonsterCard 稀有度光晕圈 + 详情渐变底
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
        // v3: GradientHeader
        GradientHeader(
            title = "妖典图鉴",
            subtitle = "${codex.regionCaught()}/${codex.regionTotal()}"
        )

        Spacer(Modifier.height(8.dp))

        // 筛选行（加间距优化）
        Row(
            modifier = Modifier.padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            FilterChip(
                selected = rarityFilter == null,
                onClick = { rarityFilter = null },
                label = { Text("全部稀有", fontSize = 12.sp) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = QingDai.copy(alpha = 0.15f),
                    selectedLabelColor = QingDai
                )
            )
            Rarity.values().forEach { r ->
                val active = rarityFilter == r
                FilterChip(
                    selected = active,
                    onClick = { rarityFilter = r },
                    label = { Text(r.label, fontSize = 12.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = colorForRarity(r).copy(alpha = 0.15f),
                        selectedLabelColor = colorForRarity(r)
                    )
                )
            }
        }
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
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

        Spacer(Modifier.height(4.dp))

        // 列表
        if (filtered.isEmpty()) {
            EmptyState("🔍", "没有符合筛选条件的妖")
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(filtered, key = { it.id }) { monster ->
                    MonsterCardV3(monster, codex.snapshotRegion().contains(monster.id)) {
                        selectedMonster = monster
                    }
                }
            }
        }
    }

    // 详情弹窗（v3: 渐变底 + 稀有度标签）
    selectedMonster?.let { m ->
        MonsterDetailDialogV3(m, codex) { selectedMonster = null }
    }
}

/** v3: MonsterCard 稀有度光晕圈 + 渐变底 */
@Composable
private fun MonsterCardV3(monster: Monster, caught: Boolean, onClick: () -> Unit) {
    val rarityGlow by animateColorAsState(
        targetValue = colorForRarity(monster.rarity).copy(alpha = 0.2f),
        animationSpec = tween(300),
        label = "rarityGlow"
    )
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
            // v3: 光晕圈 + 彩色底
            Box(
                modifier = Modifier.size(54.dp).clip(CircleShape).background(rarityGlow),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier.size(44.dp).clip(CircleShape)
                        .background(colorForRarity(monster.rarity)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        if (caught) monster.name.take(1) else "?",
                        color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold
                    )
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    if (caught) monster.name else "???",
                    fontSize = 16.sp, fontWeight = FontWeight.Bold
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    RarityChip(monster.rarity, size = ChipSize.SMALL)
                    Text("${monster.element.label}·${monster.category.label}",
                        fontSize = 11.sp, color = YanHui)
                }
            }
            if (caught) {
                Icon(Icons.Filled.CheckCircle, null, tint = HealingGreen, modifier = Modifier.size(22.dp))
            }
        }
    }
}

/** v3: 详情弹窗渐变底 */
@Composable
private fun MonsterDetailDialogV3(monster: Monster, codex: Codex, onDismiss: () -> Unit) {
    val caught = codex.snapshotRegion().contains(monster.id)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Text(monster.name, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                RarityChip(monster.rarity, size = ChipSize.MEDIUM)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Surface(shape = RoundedCornerShape(12.dp),
                    color = colorForRarity(monster.rarity).copy(alpha = 0.08f),
                    modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("五行 ${monster.element.label} · 类别 ${monster.category.label}",
                            fontSize = 13.sp, color = YanHui)
                        Text("HP ${monster.baseHp} · ATK ${monster.baseAtk} · DEF ${monster.baseDef} · SPD ${monster.baseSpd}",
                            fontSize = 13.sp, color = MoHei)
                    }
                }
                Text(monster.desc.ifBlank { "暂无描述" }, fontSize = 13.sp, color = YanHui)
                Text("已装备技能：", fontWeight = FontWeight.Medium, fontSize = 13.sp)
                monster.skills.forEach { sk ->
                    Text("  · ${sk.name}（${sk.element.label} · 威力${sk.power}）", fontSize = 13.sp,
                        color = MoHei)
                }
            }
        },
        confirmButton = { Button(onClick = onDismiss) { Text("关闭") } }
    )
}

// ===== 标签扩展（Theme 已统一 colorForRarity，这里只保留 label）=====
private val Rarity.label: String get() = when (this) {
    Rarity.FAN -> "凡"; Rarity.LING -> "灵"
    Rarity.XIAN -> "仙"; Rarity.SHEN -> "神"
}
private val MonsterCategory.label: String get() = when (this) {
    MonsterCategory.JING -> "精"; MonsterCategory.GUAI -> "怪"
    MonsterCategory.YAO -> "妖"; MonsterCategory.LINGSHOU -> "灵兽"
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
