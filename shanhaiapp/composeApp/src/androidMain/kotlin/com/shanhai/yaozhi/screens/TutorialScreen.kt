package com.shanhai.yaozhi.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.shanhai.core.logic.TutorialEngine
import com.shanhai.core.logic.TutorialStep
import com.shanhai.yaozhi.LocalGameState
import com.shanhai.yaozhi.LocalGsVersion
import com.shanhai.yaozhi.components.GradientHeader
import com.shanhai.yaozhi.theme.*
import kotlinx.coroutines.launch

/**
 * 新手引导 · V3（视觉润色）
 * v3: GradientHeader + 完成渐变卡 + 进度条 + 步骤彩色状态
 */
@Composable
fun TutorialScreen(modifier: Modifier = Modifier) {
    val gsState = LocalGameState.current
    val _v = LocalGsVersion.current.value
    val gs = gsState.value
    var completedOrder by remember { mutableIntStateOf(0) }
    var skipped by remember { mutableStateOf(false) }

    val allSteps = remember { TutorialStep.BY_ORDER }
    val current = remember(completedOrder) { TutorialEngine.currentStep(completedOrder) }
    val done = remember(completedOrder, skipped) { TutorialEngine.allDone(completedOrder, skipped) }
    val totalSteps = TutorialStep.LAST_ORDER

    Column(modifier = modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)) {

        GradientHeader(title = "新手引导",
            subtitle = "已完成 $completedOrder / $totalSteps")

        // v3: 进度条
        val progressAnimated = remember { Animatable(0f) }
        LaunchedEffect(completedOrder, totalSteps) {
            if (totalSteps > 0) progressAnimated.animateTo(
                completedOrder.toFloat() / totalSteps, tween(600))
        }
        Box(modifier = Modifier.fillMaxWidth().height(8.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(XuanZhi.copy(alpha = 0.3f))) {
            Box(modifier = Modifier.fillMaxHeight()
                .fillMaxWidth(progressAnimated.value)
                .clip(RoundedCornerShape(4.dp))
                .background(brush = Brush.horizontalGradient(listOf(QingDai, JinHuang))))
        }

        if (done) {
            Surface(shape = RoundedCornerShape(14.dp),
                color = QingDai.copy(alpha = 0.12f),
                modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("🎉", fontSize = 40.sp)
                    Text("引导完成！", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = QingDai)
                    Text("解锁所有功能入口", fontSize = 14.sp, color = YanHui)
                }
            }
        } else {
            current?.let { step ->
                val btnScope = rememberCoroutineScope()
                val doneScale = remember { Animatable(1f) }
                Surface(shape = RoundedCornerShape(16.dp), tonalElevation = 3.dp,
                    modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("当前步骤（${step.order}/$totalSteps）",
                            fontSize = 12.sp, color = YanHui)
                        Text(step.title, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = MoHei)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = {
                                btnScope.launch { doneScale.animateTo(0.94f, tween(80)); doneScale.animateTo(1f, tween(150)) }
                                completedOrder = step.order
                            }, modifier = Modifier.weight(1f).height(44.dp)
                                .graphicsLayer { scaleX = doneScale.value; scaleY = doneScale.value },
                                colors = ButtonDefaults.buttonColors(containerColor = QingDai)) {
                                Text("完成此步", fontSize = 14.sp)
                            }
                            OutlinedButton(onClick = { skipped = true },
                                modifier = Modifier.weight(1f).height(44.dp)) {
                                Text("跳过全部", fontSize = 14.sp)
                            }
                        }
                    }
                }
            }
        }

        HorizontalDivider()
        Text("引导进度", fontSize = 16.sp, fontWeight = FontWeight.Medium)

        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(allSteps) { step ->
                val completed = step.order <= completedOrder
                Surface(shape = RoundedCornerShape(10.dp),
                    color = if (completed) QingDai.copy(alpha = 0.12f)
                           else XuanZhi.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()) {
                    Row(modifier = Modifier.padding(12.dp).fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Box(modifier = Modifier.size(24.dp).clip(androidx.compose.foundation.shape.CircleShape)
                                .background(if (completed) QingDai.copy(alpha = 0.25f) else YanHui.copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center) {
                                Text(if (completed) "✓" else "○", fontSize = 12.sp,
                                    color = if (completed) QingDai else YanHui)
                            }
                            Text(step.title, fontSize = 13.sp,
                                color = if (completed) MoHei else YanHui,
                                fontWeight = if (completed) FontWeight.Medium else FontWeight.Normal)
                        }
                        if (step.forceable) {
                            Surface(shape = RoundedCornerShape(6.dp),
                                color = ZhuSha.copy(alpha = 0.15f)) {
                                Text("强制", fontSize = 10.sp, color = ZhuSha,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                            }
                        }
                    }
                }
            }
        }

        OutlinedButton(onClick = { completedOrder = 0; skipped = false },
            modifier = Modifier.fillMaxWidth()) { Text("重置引导") }
    }
}
