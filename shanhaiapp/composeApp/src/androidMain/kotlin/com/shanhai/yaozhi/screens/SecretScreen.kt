package com.shanhai.yaozhi.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
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
import com.shanhai.core.data.MonsterPool
import com.shanhai.core.logic.RegionTheme
import com.shanhai.core.logic.SecretRealmEngine
import com.shanhai.yaozhi.LocalGameState
import com.shanhai.yaozhi.LocalGsVersion
import com.shanhai.yaozhi.mutate
import com.shanhai.yaozhi.components.AnimatedStatCard
import com.shanhai.yaozhi.components.ChipSize
import com.shanhai.yaozhi.components.GradientHeader
import com.shanhai.yaozhi.components.RarityChip
import com.shanhai.yaozhi.theme.*
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.launch

/**
 * 在家秘境 · V3（视觉润色）
 * v3: GradientHeader + 主题彩色卡 + 稀有度结果标签
 */
@Composable
fun SecretScreen(modifier: Modifier = Modifier) {
    val gsState = LocalGameState.current
    val versionState = LocalGsVersion.current
    val _v = versionState.value
    val gs = gsState.value

    val themes = remember { RegionTheme.values().toList() }
    var lastSpawn by remember { mutableStateOf<List<String>>(emptyList()) }

    Column(modifier = modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)) {

        GradientHeader(title = "在家秘境",
            subtitle = "每次消耗 ${SecretRealmEngine.INCENSE_COST} 香火")

        AnimatedStatCard("香火", gs.incense, "🔥", QingDai)

        themes.forEach { theme ->
            val themeColor = when (theme) {
                RegionTheme.SHANLIN -> QingDai
                RegionTheme.SHUIYU -> Color(0xFF3498DB)
                RegionTheme.GUMU -> MoHei
                RegionTheme.DONGHAI -> JinHuang
            }
            val btnScope = rememberCoroutineScope()
            val btnScale = remember { Animatable(1f) }
            Surface(shape = RoundedCornerShape(14.dp), tonalElevation = 2.dp,
                modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.padding(14.dp).fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.weight(1f)) {
                        Box(modifier = Modifier.size(44.dp).clip(CircleShape)
                            .background(themeColor.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center) {
                            Text("🏞", fontSize = 22.sp)
                        }
                        Column {
                            Text(theme.label, fontSize = 16.sp, fontWeight = FontWeight.Bold,
                                color = themeColor)
                            val bias = SecretRealmEngine.themeElementBias(theme)
                            Text("偏向 ${bias.label} · 刷 ${SecretRealmEngine.SPAWN_COUNT} 只",
                                fontSize = 12.sp, color = YanHui)
                        }
                    }
                    Button(
                        onClick = {
                            btnScope.launch { btnScale.animateTo(0.94f, tween(80)); btnScale.animateTo(1f, tween(150)) }
                            val result = gsState.mutate(versionState) { it.enterSecretRealm() }
                            lastSpawn = result?.spawns ?: emptyList()
                        },
                        enabled = gs.incense >= SecretRealmEngine.INCENSE_COST,
                        modifier = Modifier.graphicsLayer { scaleX = btnScale.value; scaleY = btnScale.value },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (gs.incense >= SecretRealmEngine.INCENSE_COST) themeColor else YanHui.copy(alpha = 0.4f))
                    ) { Text("入秘境", fontSize = 13.sp) }
                }
            }
        }

        if (lastSpawn.isNotEmpty()) {
            HorizontalDivider()
            Text("本次刷妖（${lastSpawn.size} 只）", fontSize = 16.sp,
                fontWeight = FontWeight.Medium)
            lastSpawn.forEach { mid ->
                MonsterPool.byId(mid)?.let { m ->
                    Surface(shape = RoundedCornerShape(8.dp),
                        color = colorForRarity(m.rarity).copy(alpha = 0.08f),
                        modifier = Modifier.fillMaxWidth()) {
                        Row(modifier = Modifier.padding(10.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Text("· ${m.name}", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                            RarityChip(m.rarity, size = ChipSize.SMALL)
                        }
                    }
                }
            }
        }
    }
}

private val RegionTheme.label: String get() = when (this) {
    RegionTheme.SHANLIN -> "山林"; RegionTheme.SHUIYU -> "水域"
    RegionTheme.GUMU -> "古墓"; RegionTheme.DONGHAI -> "东海"
}
private val com.shanhai.core.model.Element.label: String get() = when (this) {
    com.shanhai.core.model.Element.METAL -> "金"; com.shanhai.core.model.Element.WOOD -> "木"
    com.shanhai.core.model.Element.EARTH -> "土"; com.shanhai.core.model.Element.WATER -> "水"
    com.shanhai.core.model.Element.FIRE -> "火"; com.shanhai.core.model.Element.WIND -> "风"
    com.shanhai.core.model.Element.THUNDER -> "雷"
}
