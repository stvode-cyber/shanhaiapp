package com.shanhai.yaozhi.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shanhai.core.data.MonsterPool
import com.shanhai.core.logic.TalentEngine
import com.shanhai.core.model.MonsterInstance
import com.shanhai.core.model.TalentBranch
import com.shanhai.yaozhi.LocalGameState
import com.shanhai.yaozhi.LocalGsVersion
import com.shanhai.yaozhi.mutate
import com.shanhai.yaozhi.components.AnimatedStatCard
import com.shanhai.yaozhi.components.GradientHeader
import com.shanhai.yaozhi.theme.*
import kotlinx.coroutines.launch

/**
 * 天赋分配 · V3（视觉润色）
 * v3: GradientHeader + 分支彩色进度条 + 按钮缩放
 */
@Composable
fun TalentScreen(modifier: Modifier = Modifier) {
    val gsState = LocalGameState.current
    val versionState = LocalGsVersion.current
    val _v = versionState.value  // 订阅 → 天赋点后自动刷新
    val gs = gsState.value

    val inst = gs.owned.values.firstOrNull()
        ?: MonsterInstance(instanceId = "auto_talent", defId = MonsterPool.all[0].id, level = 10, exp = 0, breakthrough = 0)

    val cap = TalentEngine.talentCap(inst)
    val alloc = TalentEngine.allocated(inst)

    Column(modifier = modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {

        // v3: GradientHeader
        GradientHeader(
            title = "天赋分配",
            subtitle = "已分配 $alloc / 上限 $cap"
        )

        // v3: 数值卡片
        val instDef = MonsterPool.byId(inst.defId)
        AnimatedStatCard(
            label = instDef?.name ?: "当前妖",
            value = inst.level,
            icon = "⭐",
            accentColor = JinHuang
        )

        // v3: 三分支彩色卡片
        val branches = TalentBranch.values()
        branches.forEach { branch ->
            val layers = TalentEngine.layersOf(inst, branch)
            val canAdd = TalentEngine.canAssign(inst, branch)
            val label = when (branch) {
                TalentBranch.ATTACK -> Pair("攻击分支", ZhuSha)
                TalentBranch.DEFENSE -> Pair("防御分支", QingDai)
                TalentBranch.SUPPORT -> Pair("辅助分支", JinHuang)
            }
            val scope = rememberCoroutineScope()
            val scale = remember { Animatable(1f) }
            val branchColor = label.second

            Surface(shape = RoundedCornerShape(14.dp), tonalElevation = 1.dp,
                modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.padding(14.dp).fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(label.first, fontSize = 16.sp, fontWeight = FontWeight.Bold,
                            color = branchColor)
                        Spacer(Modifier.height(6.dp))
                        // v3: 彩色进度条
                        Row(verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Box(modifier = Modifier.weight(1f).height(8.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(XuanZhi.copy(alpha = 0.3f))) {
                                Box(modifier = Modifier.fillMaxHeight()
                                    .fillMaxWidth((layers.toFloat() / 5f).coerceIn(0f, 1f))
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(branchColor))
                            }
                            Text("$layers/5", fontSize = 12.sp, color = YanHui)
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    Button(
                        onClick = {
                            scope.launch {
                                scale.animateTo(0.9f, tween(80))
                                scale.animateTo(1f, tween(150))
                            }
                            gsState.mutate(versionState) { it.assignTalent(inst.instanceId, branch) }
                        },
                        enabled = canAdd,
                        modifier = Modifier.graphicsLayer { scaleX = scale.value; scaleY = scale.value },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (canAdd) branchColor else YanHui.copy(alpha = 0.4f))
                    ) { Text("+", fontSize = 16.sp) }
                }
            }
        }

        if (branches.isEmpty()) {
            Surface(shape = RoundedCornerShape(12.dp), color = XuanZhi.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("✨", fontSize = 32.sp)
                    Text("暂无天赋分支", fontSize = 13.sp, color = YanHui)
                }
            }
        }
    }
}
