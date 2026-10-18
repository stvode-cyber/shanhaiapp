package com.shanhai.yaozhi.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shanhai.core.data.MonsterPool
import com.shanhai.core.logic.HonorEngine
import com.shanhai.yaozhi.LocalGameState
import com.shanhai.yaozhi.LocalGsVersion
import com.shanhai.yaozhi.components.AnimatedStatCard
import com.shanhai.yaozhi.components.GradientHeader
import com.shanhai.yaozhi.theme.*

/**
 * 荣誉殿堂 · V3（视觉润色）
 * v3: GradientHeader + 段位渐变卡 + 勋章彩色解锁态 + 称号 Chip
 */
@Composable
fun HonorScreen(modifier: Modifier = Modifier) {
    val gsState = LocalGameState.current
    val _v = LocalGsVersion.current.value  // 订阅 → 成就变化自动刷新
    val gs = gsState.value

    val profile = remember(gs) {
        val total = MonsterPool.all.size
        val caught = gs.codex.snapshotRegion().size
        val codexRatio = if (total > 0) (caught * 100 / total) else 0
        val rankTierIndex = when {
            gs.tribulationTotal >= 5 -> 4
            gs.tribulationTotal >= 2 -> 3
            codexRatio >= 80 -> 2
            codexRatio >= 40 -> 1
            else -> 0
        }
        HonorEngine.HonorProfile(
            achievementCount = gs.achievementCount(),
            achievementTotal = gs.achievementTotal(),
            rankTierIndex = rankTierIndex,
            codexRatio = codexRatio,
            tribulationTotal = gs.tribulationTotal
        )
    }
    val TIERS = listOf("炼气", "筑基", "金丹", "元婴", "化神")
    val tierColors = listOf(QingDai, QingDaiLight, JinHuang, ZhuShaSoft, ZhuSha)

    val unlockedMedals = HonorEngine.unlockedMedals(profile)
    val allMedals = HonorEngine.MEDALS
    val unlockedTitles = HonorEngine.unlockedTitles(profile)
    val currentTierColor = tierColors[profile.rankTierIndex]

    Column(modifier = modifier.fillMaxSize().padding(16.dp)) {

        // v3: GradientHeader
        GradientHeader(
            title = "荣誉殿堂",
            subtitle = "称号 ${unlockedTitles.size}/${HonorEngine.TITLES.size}"
        )

        Spacer(Modifier.height(8.dp))

        // v3: 段位渐变卡
        Surface(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
            Box(modifier = Modifier.fillMaxWidth().background(
                brush = Brush.linearGradient(
                    listOf(
                        currentTierColor.copy(alpha = 0.15f),
                        XuanZhi
                    ))).padding(18.dp)) {
                Row(modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically) {
                    Column {
                        Text("当前段位", fontSize = 13.sp, color = YanHui)
                        Row(verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Box(modifier = Modifier.size(48.dp).clip(CircleShape)
                                .background(currentTierColor),
                                contentAlignment = Alignment.Center) {
                                Text(TIERS[profile.rankTierIndex].take(1),
                                    color = XueBai, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                            }
                            Column {
                                Text(TIERS[profile.rankTierIndex],
                                    fontSize = 24.sp, fontWeight = FontWeight.Bold,
                                    color = currentTierColor)
                                Text("→ ${if (profile.rankTierIndex < TIERS.size - 1) TIERS[profile.rankTierIndex + 1] else "已满级"}",
                                    fontSize = 11.sp, color = YanHui)
                            }
                        }
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            AnimatedStatCard("成就", profile.achievementCount, "🏆", JinHuang, Modifier.width(90.dp))
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            AnimatedStatCard("图鉴", profile.codexRatio, "📖", QingDai, Modifier.width(90.dp))
                        }
                        AnimatedStatCard("渡劫", profile.tribulationTotal, "⚡", ZhuSha, Modifier.width(90.dp))
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        Text("勋章（${unlockedMedals.size}/${allMedals.size}）",
            fontSize = 16.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(6.dp))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(allMedals) { m ->
                val unlocked = unlockedMedals.contains(m)
                Surface(shape = RoundedCornerShape(10.dp),
                    color = if (unlocked) JinHuang.copy(alpha = 0.12f)
                           else XuanZhi.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()) {
                    Row(modifier = Modifier.padding(12.dp).fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically) {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(36.dp).clip(CircleShape)
                                .background(if (unlocked) JinHuang.copy(alpha = 0.25f) else YanHui.copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center) {
                                Text(if (unlocked) "🏅" else "🔒", fontSize = 16.sp)
                            }
                            Column {
                                Text(m.name, fontSize = 14.sp, fontWeight = FontWeight.Medium,
                                    color = if (unlocked) MoHei else YanHui)
                                Text(m.desc, fontSize = 12.sp, color = YanHui)
                            }
                        }
                        Text("×${m.req}", fontSize = 12.sp,
                            color = if (unlocked) JinHuang else YanHui,
                            fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        Text("称号", fontSize = 16.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            HonorEngine.TITLES.forEach { t ->
                val unlocked = unlockedTitles.contains(t)
                FilterChip(selected = unlocked,
                    onClick = {},
                    label = { Text(t.name, fontSize = 12.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = JinHuang.copy(alpha = 0.2f),
                        selectedLabelColor = JinHuang))
            }
        }
    }
}
