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
import com.shanhai.core.logic.HomesteadEngine
import com.shanhai.yaozhi.LocalGameState
import com.shanhai.yaozhi.LocalGsVersion
import com.shanhai.yaozhi.mutate
import com.shanhai.yaozhi.components.AnimatedStatCard
import com.shanhai.yaozhi.components.GradientHeader
import com.shanhai.yaozhi.theme.*
import kotlinx.coroutines.launch

/**
 * 家园洞府 · V3（视觉润色）
 * v3: GradientHeader + 风水渐变卡 + 增益彩色标签 + 按钮缩放
 */
@Composable
fun HomesteadScreen(modifier: Modifier = Modifier) {
    val gsState = LocalGameState.current
    val versionState = LocalGsVersion.current
    val _v = versionState.value
    val gs = gsState.value

    val fengshui = gs.homesteadLevel
    val incRate = HomesteadEngine.incenseRate(fengshui)
    val pillRate = HomesteadEngine.pillRate(fengshui)
    val atkBonus = HomesteadEngine.fengshuiAttackBonus(fengshui)
    val canUpgrade = gs.canUpgradeHomestead()

    Column(modifier = modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {

        GradientHeader(title = "家园洞府", subtitle = "阵法风水 Lv.$fengshui")

        AnimatedStatCard("妖丹", gs.yaodan, "🧪", ZhuSha)

        // v3: 风水渐变卡
        Surface(shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
            Box(modifier = Modifier.fillMaxWidth().background(
                brush = Brush.linearGradient(
                    listOf(QingDai.copy(alpha = 0.12f), XuanZhi)))
                .padding(16.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("阵法风水 · Lv.$fengshui", fontSize = 16.sp,
                        fontWeight = FontWeight.Bold, color = QingDai)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Surface(shape = RoundedCornerShape(8.dp),
                            color = QingDai.copy(alpha = 0.15f)) {
                            Text("🔥 香火 +${"%.1f".format(incRate)}/h",
                                fontSize = 12.sp, color = QingDai,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp))
                        }
                        Surface(shape = RoundedCornerShape(8.dp),
                            color = ZhuSha.copy(alpha = 0.15f)) {
                            Text("🧪 妖丹 +${"%.2f".format(pillRate)}/h",
                                fontSize = 12.sp, color = ZhuSha,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp))
                        }
                        Surface(shape = RoundedCornerShape(8.dp),
                            color = JinHuang.copy(alpha = 0.15f)) {
                            Text("⚔️ 攻 +${"%.0f".format(atkBonus * 100)}%",
                                fontSize = 12.sp, color = JinHuang,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp))
                        }
                    }
                    Text("升级花费：${HomesteadEngine.upgradeCost(fengshui)} 妖丹",
                        fontSize = 12.sp, color = YanHui)
                }
            }
        }

        val btnScope = rememberCoroutineScope()
        val btnScale = remember { Animatable(1f) }
        Button(onClick = {
            btnScope.launch { btnScale.animateTo(0.95f, tween(80)); btnScale.animateTo(1f, tween(150)) }
            gsState.mutate(versionState) { it.upgradeHomestead() }
        }, enabled = canUpgrade, modifier = Modifier.fillMaxWidth().height(48.dp)
            .graphicsLayer { scaleX = btnScale.value; scaleY = btnScale.value },
            colors = ButtonDefaults.buttonColors(
                containerColor = if (canUpgrade) QingDai else YanHui.copy(alpha = 0.4f))) {
            Text(if (canUpgrade) "升级阵法" else "妖丹不足",
                fontSize = 15.sp, fontWeight = FontWeight.Bold)
        }
    }
}
