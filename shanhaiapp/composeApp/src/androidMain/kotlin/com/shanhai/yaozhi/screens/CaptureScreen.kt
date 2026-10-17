package com.shanhai.yaozhi.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.shanhai.core.logic.CaptureEngine
import com.shanhai.core.logic.CaptureResult
import com.shanhai.core.model.Monster
import com.shanhai.core.model.Rarity
import com.shanhai.yaozhi.theme.QingDai
import com.shanhai.yaozhi.theme.ZhuSha
import kotlinx.coroutines.delay
import kotlin.random.Random

/**
 * 收妖界面 · V1
 * 光环从大收缩到小，玩家点按瞬间入葫芦。
 * 调用 CaptureEngine.resolve() 判定成功率。
 */
private val MAX_RADIUS = 120f
private val MIN_RADIUS = 20f

@Composable
fun CaptureScreen(
    nodeName: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    // 随机选一只妖
    val monster = remember(nodeName) { MonsterPool.all.random(Random(nodeName.hashCode())) }
    var ringRadius by remember { mutableFloatStateOf(MAX_RADIUS) }
    var isCapturing by remember { mutableStateOf(false) }
    var captureResult by remember { mutableStateOf<CaptureResult?>(null) }
    var assistMode by remember { mutableStateOf(true) }
    var showResult by remember { mutableStateOf(false) }

    // 光环收缩动画
    LaunchedEffect(isCapturing, showResult) {
        if (!isCapturing || showResult) return@LaunchedEffect
        val cycle = CaptureEngine.ringCycleMs(monster.rarity, assistMode)
        val duration = cycle.toInt()
        animate(
            initialValue = MAX_RADIUS,
            targetValue = MIN_RADIUS,
            animationSpec = tween(durationMillis = duration, easing = LinearEasing)
        ) { value, _ ->
            ringRadius = value
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
    ) {
        // 顶部栏
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("收妖 · $nodeName", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            AssistChip(
                onClick = { assistMode = !assistMode },
                label = { Text(if (assistMode) "辅助 开" else "辅助 关", fontSize = 12.sp) }
            )
        }

        Spacer(Modifier.height(8.dp))
        MonsterInfoCard(monster)

        Spacer(Modifier.height(24.dp))

        // 收妖舞台
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentAlignment = Alignment.Center
        ) {
            if (showResult) {
                CaptureResultPanel(captureResult, monster) {
                    showResult = false
                    isCapturing = false
                    ringRadius = MAX_RADIUS
                }
            } else {
                // 妖本体（彩色圆）
                Box(
                    modifier = Modifier
                        .size(60.dp)
                        .clip(CircleShape)
                        .background(colorForRarity(monster.rarity)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        monster.name.take(1),
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
                // 光环
                Box(
                    modifier = Modifier
                        .size(ringRadius.dp * 2)
                        .clip(CircleShape)
                        .border(
                            width = 4.dp,
                            color = ZhuSha,
                            shape = CircleShape
                        )
                )
                // 最佳点指示
                Box(
                    modifier = Modifier
                        .size((MIN_RADIUS + 8).dp * 2)
                        .clip(CircleShape)
                        .border(
                            width = 2.dp,
                            color = QingDai.copy(alpha = 0.4f),
                            shape = CircleShape
                        )
                )
            }
        }

        // 收妖按钮
        if (!showResult) {
            Button(
                onClick = {
                    if (!isCapturing) {
                        isCapturing = true
                    } else {
                        // 点按 → 判定
                        val acc = CaptureEngine.accuracy(
                            tapRadius = ringRadius.toDouble(),
                            minRadius = MIN_RADIUS.toDouble(),
                            maxRadius = MAX_RADIUS.toDouble(),
                            assist = assistMode
                        )
                        captureResult = CaptureEngine.resolve(
                            accuracy = acc,
                            rarity = monster.rarity,
                            assist = assistMode
                        )
                        showResult = true
                    }
                },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = ZhuSha)
            ) {
                Text(if (!isCapturing) "引妖入瓮" else "点按收妖！", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("返回地图") }
    }
}

@Composable
private fun MonsterInfoCard(monster: Monster) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth()
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
                Text(monster.name.take(1), color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            }
            Column {
                Text(monster.name, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Text("${monster.rarity.label} · ${monster.element.label} · ${monster.category.label}", fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun CaptureResultPanel(result: CaptureResult?, monster: Monster, onRetry: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        result?.let { r ->
            Text(
                if (r.success) "收服成功！" else "妖逃走了…",
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = if (r.success) QingDai else ZhuSha
            )
            Spacer(Modifier.height(8.dp))
            Text("命中精度：${"%.0f".format(r.accuracy * 100)}%", fontSize = 14.sp)
            Text("成功率：${"%.0f".format(r.probability * 100)}%", fontSize = 14.sp)
            Text("实际判定：${if (r.struggled) "妖挣扎了" else "一击入瓮"}", fontSize = 14.sp)
            Spacer(Modifier.height(24.dp))
            Button(onClick = onRetry) { Text("再来一次") }
        }
    }
}

private fun colorForRarity(r: Rarity): Color = when (r) {
    Rarity.FAN -> Color(0xFF7F8C8D)
    Rarity.LING -> Color(0xFF3498DB)
    Rarity.XIAN -> Color(0xFF9B59B6)
    Rarity.SHEN -> Color(0xFFF39C12)
}
