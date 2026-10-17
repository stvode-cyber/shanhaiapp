package com.shanhai.yaozhi.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shanhai.core.logic.TutorialEngine
import com.shanhai.core.logic.TutorialStep
import com.shanhai.yaozhi.LocalGameState
import com.shanhai.yaozhi.LocalGsVersion

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

    Column(modifier = modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("新手引导", fontSize = 22.sp, fontWeight = FontWeight.Bold)

        if (done) {
            Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("🎉 引导完成！", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text("解锁所有功能入口", fontSize = 14.sp)
                }
            }
        } else {
            current?.let { step ->
                Surface(shape = RoundedCornerShape(12.dp), tonalElevation = 3.dp,
                    modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("当前步骤（${step.order}/${TutorialStep.LAST_ORDER}）",
                            fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(step.title, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { completedOrder = step.order },
                                modifier = Modifier.weight(1f)) { Text("完成此步") }
                            OutlinedButton(onClick = { skipped = true },
                                modifier = Modifier.weight(1f)) { Text("跳过全部") }
                        }
                    }
                }
            }
        }

        HorizontalDivider()
        Text("引导进度", fontSize = 16.sp, fontWeight = FontWeight.Medium)

        LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            items(allSteps) { step ->
                Surface(shape = RoundedCornerShape(8.dp),
                    color = if (step.order <= completedOrder)
                        MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth()) {
                    Row(modifier = Modifier.padding(10.dp).fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(if (step.order <= completedOrder) "✅ ${step.title}" else "○ ${step.title}",
                            fontSize = 13.sp)
                        if (step.forceable) {
                            Text("强制", fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }

        OutlinedButton(onClick = { completedOrder = 0; skipped = false },
            modifier = Modifier.fillMaxWidth()) { Text("重置引导") }
    }
}
