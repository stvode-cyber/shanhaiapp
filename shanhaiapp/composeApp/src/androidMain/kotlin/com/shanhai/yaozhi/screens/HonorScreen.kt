package com.shanhai.yaozhi.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shanhai.core.logic.HonorEngine

@Composable
fun HonorScreen(modifier: Modifier = Modifier) {
    var profile by remember {
        mutableStateOf(HonorEngine.HonorProfile(
            achievementCount = 12, achievementTotal = 30,
            rankTierIndex = 2, codexRatio = 45, tribulationTotal = 0
        ))
    }
    val TIERS = listOf("炼气", "筑基", "金丹", "元婴", "化神")

    val unlockedMedals = HonorEngine.unlockedMedals(profile)
    val allMedals = HonorEngine.MEDALS
    val unlockedTitles = HonorEngine.unlockedTitles(profile)

    Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
        Text("荣誉殿堂", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))

        // 段位
        Surface(shape = RoundedCornerShape(12.dp), tonalElevation = 2.dp,
            modifier = Modifier.fillMaxWidth()) {
            Row(modifier = Modifier.padding(16.dp).fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Column {
                    Text("当前段位", fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(TIERS[profile.rankTierIndex],
                        fontSize = 24.sp, fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary)
                }
                Column(horizontalAlignment = androidx.compose.ui.Alignment.End) {
                    Text("成就 ${profile.achievementCount}/${profile.achievementTotal}", fontSize = 14.sp)
                    Text("图鉴 ${profile.codexRatio}% · 渡劫 ${profile.tribulationTotal}", fontSize = 12.sp)
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        Text("勋章（${unlockedMedals.size}/${allMedals.size}）", fontSize = 16.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(4.dp))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(allMedals) { m ->
                val unlocked = unlockedMedals.contains(m)
                Surface(shape = RoundedCornerShape(8.dp),
                    color = if (unlocked) MaterialTheme.colorScheme.primaryContainer
                           else MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth()) {
                    Row(modifier = Modifier.padding(10.dp).fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween) {
                        Column {
                            Text(if (unlocked) "🏅 ${m.name}" else "🔒 ${m.name}",
                                fontSize = 14.sp, fontWeight = FontWeight.Medium)
                            Text(m.desc, fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text("×${m.req}", fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        Text("称号（${unlockedTitles.size}/${HonorEngine.TITLES.size}）", fontSize = 16.sp, fontWeight = FontWeight.Medium)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            HonorEngine.TITLES.forEach { t ->
                val unlocked = unlockedTitles.contains(t)
                FilterChip(selected = unlocked,
                    onClick = {},
                    label = { Text(t.name, fontSize = 12.sp) })
            }
        }

        // 快速模拟
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                profile = profile.copy(achievementCount = (profile.achievementCount + 1).coerceAtMost(profile.achievementTotal),
                    codexRatio = (profile.codexRatio + 5).coerceAtMost(100),
                    rankTierIndex = (profile.rankTierIndex + 1).coerceAtMost(4))
            }, modifier = Modifier.weight(1f)) { Text("+ 达成") }
            OutlinedButton(onClick = { profile = profile.copy(achievementCount = 12, rankTierIndex = 2, codexRatio = 45) },
                modifier = Modifier.weight(1f)) { Text("重置") }
        }
    }
}

// 段位直接内联 HonorEngine 没有 TIERS 伴生属性，已在函数开头定义
