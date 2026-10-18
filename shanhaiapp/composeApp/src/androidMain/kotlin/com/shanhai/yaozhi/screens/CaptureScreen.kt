package com.shanhai.yaozhi.screens

import androidx.compose.animation.*
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shanhai.core.data.MonsterPool
import com.shanhai.core.logic.CaptureEngine
import com.shanhai.core.logic.CaptureResult
import com.shanhai.core.model.Monster
import com.shanhai.core.model.Rarity
import com.shanhai.yaozhi.LocalGameState
import com.shanhai.yaozhi.LocalGsVersion
import com.shanhai.yaozhi.mutate
import com.shanhai.yaozhi.theme.*
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.random.Random

/**
 * 收妖界面 · V2（UI 润色）
 * v2 新增：妖本体稀有度光晕 + 结果面板弹跳/震动 + 按钮缩放 + 宣纸背景
 */
private val MAX_RADIUS = 120f
private val MIN_RADIUS = 20f

@Composable
fun CaptureScreen(
    nodeName: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val gsState = LocalGameState.current
    val versionState = LocalGsVersion.current
    val _v = versionState.value
    val gs = gsState.value
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
        MonsterInfoCardV2(monster)

        Spacer(Modifier.height(24.dp))

        // 收妖舞台
        Box(
            modifier = Modifier.fillMaxWidth().weight(1f),
            contentAlignment = Alignment.Center
        ) {
            if (showResult) {
                CaptureResultPanelV2(captureResult, monster) {
                    showResult = false
                    isCapturing = false
                    ringRadius = MAX_RADIUS
                }
            } else {
                // 妖本体（稀有度光晕圈 + 彩色底）
                val rarityGlow by animateColorAsState(
                    targetValue = colorForRarity(monster.rarity).copy(alpha = 0.2f),
                    animationSpec = tween(300),
                    label = "rarityGlow"
                )
                Box(
                    modifier = Modifier.size(100.dp).clip(CircleShape).background(rarityGlow),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier.size(60.dp).clip(CircleShape)
                            .background(colorForRarity(monster.rarity)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(monster.name.take(1), fontSize = 28.sp,
                            fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
                // 光环
                Box(
                    modifier = Modifier.size(ringRadius.dp * 2).clip(CircleShape)
                        .border(width = 4.dp, color = ZhuSha, shape = CircleShape)
                )
                // 最佳点指示
                Box(
                    modifier = Modifier.size((MIN_RADIUS + 8).dp * 2).clip(CircleShape)
                        .border(width = 2.dp, color = QingDai.copy(alpha = 0.5f), shape = CircleShape)
                )
            }
        }

        // 收妖按钮（缩放反馈）
        if (!showResult) {
            val scale = remember { Animatable(1f) }
            val scope = rememberCoroutineScope()
            Button(
                onClick = {
                    if (!isCapturing) {
                        isCapturing = true
                    } else {
                        scope.launch {
                            scale.animateTo(0.92f, tween(80))
                            delay(30)
                            scale.animateTo(1f, tween(200))
                        }
                        val acc = CaptureEngine.accuracy(
                            tapRadius = ringRadius.toDouble(),
                            minRadius = MIN_RADIUS.toDouble(),
                            maxRadius = MAX_RADIUS.toDouble(),
                            assist = assistMode
                        )
                        captureResult = CaptureEngine.resolve(acc, monster.rarity, assistMode)
                        if (captureResult!!.success) {
                            gsState.mutate(versionState) { it.owned["cap_${System.currentTimeMillis()}"] =
                                com.shanhai.core.model.MonsterInstance(
                                    instanceId = "cap_${System.currentTimeMillis()}",
                                    defId = monster.id, level = 1, exp = 0, breakthrough = 0
                                )
                            }
                        }
                        showResult = true
                    }
                },
                modifier = Modifier.fillMaxWidth().height(56.dp)
                    .graphicsLayer { scaleX = scale.value; scaleY = scale.value },
                colors = ButtonDefaults.buttonColors(containerColor = ZhuSha)
            ) {
                Text(if (!isCapturing) "引妖入瓮" else "点按收妖！",
                    fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }
        }

        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("返回地图") }
    }
}

// ===== V2 组件 =====

@Composable
private fun MonsterInfoCardV2(monster: Monster) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(52.dp).clip(CircleShape)
                    .background(colorForRarity(monster.rarity)),
                contentAlignment = Alignment.Center
            ) {
                Text(monster.name.take(1), color = Color.White,
                    fontSize = 24.sp, fontWeight = FontWeight.Bold)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(monster.name, fontSize = 17.sp, fontWeight = FontWeight.Bold,
                    color = MoHei)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Surface(shape = RoundedCornerShape(4.dp),
                        color = colorForRarity(monster.rarity).copy(alpha = 0.15f)) {
                        Text(monster.rarity.label, fontSize = 11.sp,
                            color = colorForRarity(monster.rarity),
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp))
                    }
                    Surface(shape = RoundedCornerShape(4.dp),
                        color = QingDai.copy(alpha = 0.15f)) {
                        Text(monster.element.label, fontSize = 11.sp,
                            color = QingDai,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp))
                    }
                    Surface(shape = RoundedCornerShape(4.dp),
                        color = JinHuang.copy(alpha = 0.15f)) {
                        Text(monster.category.label, fontSize = 11.sp,
                            color = JinHuang,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun CaptureResultPanelV2(result: CaptureResult?, monster: Monster, onRetry: () -> Unit) {
    val success = result?.success ?: false
    // 成功弹跳 / 失败震动
    val offsetX = remember { Animatable(0f) }
    val scale = remember { Animatable(0.8f) }

    LaunchedEffect(success) {
        kotlinx.coroutines.coroutineScope {
            if (success) {
                launch { scale.animateTo(1.0f, tween(400, easing = FastOutSlowInEasing)) }
            } else {
                // 震动
                val shake = listOf(-10f, 10f, -8f, 8f, -5f, 5f, 0f)
                for (s in shake) {
                    offsetX.animateTo(s, tween(60))
                }
            }
        }
    }

    AnimatedVisibility(
        visible = true,
        enter = fadeIn(tween(300))
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.graphicsLayer {
                translationX = offsetX.value
                scaleX = scale.value
                scaleY = scale.value
            }) {
            // 妖本体（结果反馈版）
            val monsterGlow = if (success) HealingGreen else ZhuShaSoft
            Box(modifier = Modifier.size(120.dp).clip(CircleShape)
                .background(monsterGlow.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center) {
                Box(modifier = Modifier.size(72.dp).clip(CircleShape)
                    .background(colorForRarity(monster.rarity)),
                    contentAlignment = Alignment.Center) {
                    Text(monster.name.take(1), color = Color.White,
                        fontSize = 34.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(Modifier.height(12.dp))

            Text(
                if (success) "🎉 收服成功！" else "💨 妖逃走了...",
                fontSize = 32.sp, fontWeight = FontWeight.Bold,
                color = if (success) HealingGreen else ZhuSha
            )
            Spacer(Modifier.height(6.dp))

            result?.let { r ->
                Surface(shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("命中精度：${"%.0f".format(r.accuracy * 100)}%",
                            fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        Text("成功率：${"%.0f".format(r.probability * 100)}%", fontSize = 14.sp)
                        Text("实际判定：${if (r.struggled) "妖挣扎了一下才入瓮" else "一击入瓮！"}",
                            fontSize = 14.sp,
                            color = if (success) HealingGreen else ZhuSha)
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            Button(onClick = onRetry, colors = ButtonDefaults.buttonColors(
                containerColor = if (success) HealingGreen else ZhuSha)) {
                Text(if (success) "继续收妖" else "再来一次", color = Color.White)
            }
        }
    }
}
