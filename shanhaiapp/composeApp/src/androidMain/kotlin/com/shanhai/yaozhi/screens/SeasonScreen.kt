package com.shanhai.yaozhi.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shanhai.core.logic.SeasonEngine
import com.shanhai.yaozhi.LocalGameState
import com.shanhai.yaozhi.LocalGsVersion

@Composable
fun SeasonScreen(modifier: Modifier = Modifier) {
    val gsState = LocalGameState.current
    val _v = LocalGsVersion.current.value
    val gs = gsState.value
    var xp by remember { mutableIntStateOf(0) }

    val curTier = SeasonEngine.tierFromXp(xp)
    val nextTierXp = SeasonEngine.xpForTier(curTier + 1)

    Column(modifier = modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("赛季通行证", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text("S1 · ${SeasonEngine.SEASON_LENGTH_DAYS} 天双轨制 · ${SeasonEngine.TIERS} 档",
            fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

        Surface(shape = RoundedCornerShape(12.dp), tonalElevation = 2.dp,
            modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("赛季经验：$xp XP", fontSize = 16.sp, fontWeight = FontWeight.Medium)
                Text("当前档位：T$curTier / T${SeasonEngine.TIERS}", fontSize = 14.sp)
                if (curTier < SeasonEngine.TIERS) {
                    Text("下一档 T${curTier + 1} 需 $nextTierXp XP（还差 ${nextTierXp - xp}）", fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                val freeR = SeasonEngine.freeReward(curTier)
                val paidR = SeasonEngine.paidReward(curTier)
                Spacer(Modifier.height(4.dp))
                Text("T$curTier 免费档：香火 ${freeR.incense} · 妖丹 ${freeR.yaodan}", fontSize = 12.sp)
                Text("T$curTier 付费档：灵玉 ${paidR.lingyu} · 妖丹 ${paidR.yaodan}", fontSize = 12.sp)
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { xp += SeasonEngine.XP_CATCH }, modifier = Modifier.weight(1f)) {
                Text("收妖 +${SeasonEngine.XP_CATCH}") }
            Button(onClick = { xp += SeasonEngine.XP_BATTLE }, modifier = Modifier.weight(1f)) {
                Text("对战 +${SeasonEngine.XP_BATTLE}") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { xp += SeasonEngine.XP_DUNGEON }, modifier = Modifier.weight(1f)) {
                Text("副本 +${SeasonEngine.XP_DUNGEON}") }
            Button(onClick = { xp += SeasonEngine.XP_SIGNIN }, modifier = Modifier.weight(1f)) {
                Text("签到 +${SeasonEngine.XP_SIGNIN}") }
        }
    }
}
