package com.shanhai.yaozhi.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shanhai.core.logic.Codex
import com.shanhai.yaozhi.LocalGameState
import com.shanhai.yaozhi.LocalGsVersion

/**
 * HubScreen —— "我的"页面，次级 13 个功能入口全挂这里
 * V2：从 LocalGameState.current.codex 取数据
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

        // 顶部统计卡片
        Surface(shape = RoundedCornerShape(16.dp), tonalElevation = 2.dp,
            modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("山海妖志", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text("灵玉 ${gsState.value.lingyu} ｜ 妖丹 ${gsState.value.yaodan} ｜ 香火 ${gsState.value.incense}",
                    fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("图鉴 ${codex.regionCaught()} / ${codex.regionTotal()} 妖",
                    fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { codex.regionCaught().toFloat() / codex.regionTotal() },
                    modifier = Modifier.fillMaxWidth().height(6.dp),
                )
            }
        }

        Text("全部功能", fontSize = 16.sp, fontWeight = FontWeight.Medium)

        LazyVerticalGrid(
            columns = GridCells.Fixed(4),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(items) { item ->
                HubEntry(item = item, onClick = { onNavigate(item) })
            }
        }
    }
}

@Composable
private fun HubEntry(item: HubItem, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth().height(80.dp).clickable(onClick = onClick)
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center) {
            Icon(item.icon, contentDescription = item.label,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp))
            Spacer(Modifier.height(4.dp))
            Text(item.label, fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

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
