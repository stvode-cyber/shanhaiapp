package com.shanhai.yaozhi.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shanhai.core.logic.Codex
import com.shanhai.yaozhi.LocalGameState
import com.shanhai.yaozhi.LocalGsVersion
import com.shanhai.yaozhi.theme.*
import com.shanhai.yaozhi.components.GradientHeader
import com.shanhai.yaozhi.components.AnimatedStatCard
import kotlinx.coroutines.launch

/**
 * HubScreen V2 —— "我的"页面，次级 18 个功能入口全挂这里
 * v2 升级：GradientHeader 标题栏 + AnimatedStatCard 数值动画 + HubEntry 彩色图标底
 */
@Composable
fun HubScreen(
    onNavigate: (HubItem) -> Unit,
    modifier: Modifier = Modifier
) {
    val gsState = LocalGameState.current
    val _v = LocalGsVersion.current.value  // 订阅 → codex 变化自动刷新
    val codex = gsState.value.codex
    val items = remember { HubItem.all }

    Column(modifier = modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {

        // v2: GradientHeader 标题栏
        GradientHeader(
            title = "山海妖志",
            subtitle = "图鉴 ${codex.regionCaught()}/${codex.regionTotal()}"
        )

        // v2: 三卡片数值动画
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AnimatedStatCard("灵玉", gsState.value.lingyu, "💎", JinHuang, Modifier.weight(1f))
            AnimatedStatCard("妖丹", gsState.value.yaodan, "🧪", ZhuSha, Modifier.weight(1f))
            AnimatedStatCard("香火", gsState.value.incense, "🔥", QingDai, Modifier.weight(1f))
        }

        // 图鉴进度条
        Surface(shape = RoundedCornerShape(12.dp), tonalElevation = 1.dp,
            modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp)) {
                val progressAnimated = remember { Animatable(0f) }
                val total = codex.regionTotal()
                val caught = codex.regionCaught()
                LaunchedEffect(caught, total) {
                    if (total > 0) progressAnimated.animateTo(
                        caught.toFloat() / total, tween(600))
                }
                Row(modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("图鉴进度", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    Text("$caught / $total", fontSize = 13.sp, color = QingDai,
                        fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(6.dp))
                Box(modifier = Modifier.fillMaxWidth().height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(XuanZhi.copy(alpha = 0.3f))) {
                    Box(modifier = Modifier.fillMaxHeight()
                        .fillMaxWidth(progressAnimated.value)
                        .clip(RoundedCornerShape(4.dp))
                        .background(
                            brush = androidx.compose.ui.graphics.Brush.horizontalGradient(
                                listOf(ZhuSha, JinHuang))))
                }
            }
        }

        Text("全部功能", fontSize = 15.sp, fontWeight = FontWeight.Medium, color = MoHei)

        LazyVerticalGrid(
            columns = GridCells.Fixed(4),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(items) { item ->
                HubEntryV2(item = item, onClick = { onNavigate(item) })
            }
        }
    }
}

/** v2: HubEntry 彩色图标底 + 点击缩放 */
@Composable
private fun HubEntryV2(item: HubItem, onClick: () -> Unit) {
    val color = HubItemColorMap[item] ?: QingDai
    val scope = rememberCoroutineScope()
    val scale = remember { Animatable(1f) }
    Surface(
        shape = RoundedCornerShape(12.dp),
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth().height(80.dp)
            .graphicsLayer { scaleX = scale.value; scaleY = scale.value }
            .clickable {
                scope.launch {
                    scale.animateTo(0.92f, tween(80))
                    scale.animateTo(1f, tween(150))
                }
                onClick()
            }
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center) {
            Box(
                modifier = Modifier.size(36.dp)
                    .clip(androidx.compose.foundation.shape.CircleShape)
                    .background(color.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(item.icon, contentDescription = item.label,
                    tint = color, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.height(4.dp))
            Text(item.label, fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private val HubItemColorMap = mapOf(
    HubItem.MAP to QingDai,
    HubItem.CODEX to JinHuang,
    HubItem.BATTLE to ZhuSha,
    HubItem.DUNGEON to MoHei,
    HubItem.CULTIVATE to XianZi,
    HubItem.EQUIP to LingDian,
    HubItem.FUSE to ZhuShaDark,
    HubItem.TALENT to JinHuang,
    HubItem.STORE to ShengLv,
    HubItem.QUESTS to QingDaiLight,
    HubItem.SECRET to XianZi,
    HubItem.HOMESTEAD to QingDai,
    HubItem.SECT to MoHei,
    HubItem.SEASON to ZhuSha,
    HubItem.HONOR to JinHuang,
    HubItem.TUTORIAL to ShengLv,
    HubItem.ACTIVITY to ZhuShaSoft,
    HubItem.ABOUT to YanHui,
)

/** 次级入口枚举，与 MainActivity 的 Tab 一一对应 */
enum class HubItem(val label: String, val icon: ImageVector) {
    MAP("巡妖", Icons.Filled.Place),
    CODEX("妖典", Icons.AutoMirrored.Filled.MenuBook),
    BATTLE("试炼", Icons.Filled.SportsEsports),
    DUNGEON("副本", Icons.Filled.Castle),
    CULTIVATE("养成", Icons.Filled.AutoAwesome),
    EQUIP("装备", Icons.Filled.Shield),
    FUSE("融合", Icons.Filled.Merge),
    TALENT("天赋", Icons.Filled.Star),
    STORE("商店", Icons.Filled.ShoppingCart),
    QUESTS("敕令", Icons.AutoMirrored.Filled.Assignment),
    SECRET("秘境", Icons.Filled.Nature),
    HOMESTEAD("家园", Icons.Filled.Home),
    SECT("宗门", Icons.Filled.Groups),
    SEASON("赛季", Icons.Filled.Schedule),
    HONOR("荣誉", Icons.Filled.WorkspacePremium),
    TUTORIAL("新手", Icons.Filled.RocketLaunch),
    ACTIVITY("活动", Icons.Filled.Event),
    ABOUT("关于", Icons.Filled.Info),
    ;

    companion object { val all = entries }
}
