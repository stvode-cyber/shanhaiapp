package com.shanhai.yaozhi.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shanhai.core.logic.SeasonEngine
import com.shanhai.yaozhi.LocalGameState
import com.shanhai.yaozhi.LocalGsVersion
import com.shanhai.yaozhi.components.AnimatedStatCard
import com.shanhai.yaozhi.components.GradientHeader
import com.shanhai.yaozhi.theme.*
import kotlinx.coroutines.launch

/**
 * 赛季通行证 · V3（视觉润色）
 * v3: GradientHeader + 档位渐变卡 + 进度条 + 按钮缩放
 */
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

        GradientHeader(
            title = "赛季通行证",
            subtitle = "S1 · ${SeasonEngine.SEASON_LENGTH_DAYS} 天双轨制 · ${SeasonEngine.TIERS} 档"
        )

        AnimatedStatCard("当前档位", curTier, "🏆", JinHuang)

        // v3: 经验渐变卡 + 进度条
        Surface(shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
            Box(modifier = Modifier.fillMaxWidth().background(
                brush = Brush.linearGradient(
                    listOf(JinHuang.copy(alpha = 0.15f), XuanZhi)))
                .padding(16.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()) {
                        Text("赛季经验", fontSize = 14.sp, color = YanHui)
                        Text("$xp XP", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = JinHuang)
                    }
                    val progressAnimated = remember { Animatable(0f) }
                    val progressTarget = if (curTier < SeasonEngine.TIERS)
                        (xp.toFloat() / nextTierXp).coerceIn(0f, 1f) else 1f
                    LaunchedEffect(xp) { progressAnimated.animateTo(progressTarget, tween(600)) }
                    Box(modifier = Modifier.fillMaxWidth().height(10.dp)
                        .clip(RoundedCornerShape(5.dp))
                        .background(XuanZhi.copy(alpha = 0.3f))) {
                        Box(modifier = Modifier.fillMaxHeight()
                            .fillMaxWidth(progressAnimated.value)
                            .clip(RoundedCornerShape(5.dp))
                            .background(brush = Brush.horizontalGradient(
                                listOf(ZhuSha, JinHuang))))
                    }
                    Text(
                        if (curTier < SeasonEngine.TIERS)
                            "→ T${curTier + 1} 需 $nextTierXp XP（还差 ${nextTierXp - xp}）"
                        else "已满级！",
                        fontSize = 12.sp, color = YanHui)
                }
            }
        }

        // 奖励卡
        val freeR = SeasonEngine.freeReward(curTier)
        val paidR = SeasonEngine.paidReward(curTier)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Surface(shape = RoundedCornerShape(10.dp), color = QingDai.copy(alpha = 0.1f),
                modifier = Modifier.weight(1f)) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Text("🎁 免费档 T$curTier", fontSize = 12.sp, color = QingDai, fontWeight = FontWeight.Medium)
                    Text("香火 ${freeR.incense} · 妖丹 ${freeR.yaodan}", fontSize = 12.sp)
                }
            }
            Surface(shape = RoundedCornerShape(10.dp), color = ZhuSha.copy(alpha = 0.1f),
                modifier = Modifier.weight(1f)) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Text("💎 付费档 T$curTier", fontSize = 12.sp, color = ZhuSha, fontWeight = FontWeight.Medium)
                    Text("灵玉 ${paidR.lingyu} · 妖丹 ${paidR.yaodan}", fontSize = 12.sp)
                }
            }
        }

        // 加经验按钮
        val xpScope = rememberCoroutineScope()
        val btnScale = remember { Animatable(1f) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                xpScope.launch { btnScale.animateTo(0.95f, tween(80)); btnScale.animateTo(1f, tween(150)) }
                xp += SeasonEngine.XP_CATCH
            }, modifier = Modifier.weight(1f)
                .graphicsLayer { scaleX = btnScale.value; scaleY = btnScale.value },
                colors = ButtonDefaults.buttonColors(containerColor = QingDai)) {
                Text("收妖 +${SeasonEngine.XP_CATCH}")
            }
            Button(onClick = { xp += SeasonEngine.XP_BATTLE }, modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = ZhuSha)) {
                Text("对战 +${SeasonEngine.XP_BATTLE}")
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { xp += SeasonEngine.XP_DUNGEON }, modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = JinHuang)) {
                Text("副本 +${SeasonEngine.XP_DUNGEON}")
            }
            Button(onClick = { xp += SeasonEngine.XP_SIGNIN }, modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = ZhuShaSoft)) {
                Text("签到 +${SeasonEngine.XP_SIGNIN}")
            }
        }
    }
}
