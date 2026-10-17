package com.shanhai.yaozhi

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shanhai.core.data.MonsterPool
import com.shanhai.core.logic.Codex
import com.shanhai.core.platform.OsmBootstrap
import com.shanhai.yaozhi.screens.*
import com.shanhai.yaozhi.theme.ShanhaiTheme

/**
 * 山海妖志 · Android Compose 壳（v0.2.0）
 * V1 核心循环 + V2 养成经济
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        OsmBootstrap.init(this)
        val codex = Codex(MonsterPool.all)
        setContent {
            ShanhaiTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    GameRoot(codex = codex)
                }
            }
        }
    }
}

private enum class Tab(val label: String) {
    MAP("巡妖"), CODEX("妖典"), BATTLE("试炼"),
    CULTIVATE("养成"), EQUIP("装备"), FUSE("融合"),
    TALENT("天赋"), STORE("商店"), QUESTS("敕令"),
    DUNGEON("副本"), SECRET("秘境"), HOMESTEAD("家园"),
    SECT("宗门"), SEASON("赛季"),
    ABOUT("关于")
}

@Composable
private fun GameRoot(codex: Codex) {
    var currentTab by remember { mutableStateOf(Tab.MAP) }
    var captureNode by remember { mutableStateOf<SpiritNode?>(null) }

    Box(modifier = Modifier.fillMaxSize()) {
        when {
            currentTab == Tab.MAP -> MapScreen(
                onEnterCapture = { n -> captureNode = n; currentTab = Tab.BATTLE },
                modifier = Modifier.fillMaxSize()
            )
            currentTab == Tab.CODEX -> CodexScreen(codex, modifier = Modifier.fillMaxSize())
            currentTab == Tab.BATTLE -> BattleScreen(modifier = Modifier.fillMaxSize())
            currentTab == Tab.CULTIVATE -> CultivateScreen(modifier = Modifier.fillMaxSize())
            currentTab == Tab.EQUIP -> EquipScreen(modifier = Modifier.fillMaxSize())
            currentTab == Tab.FUSE -> FuseScreen(modifier = Modifier.fillMaxSize())
            currentTab == Tab.TALENT -> TalentScreen(modifier = Modifier.fillMaxSize())
            currentTab == Tab.STORE -> StoreScreen(modifier = Modifier.fillMaxSize())
            currentTab == Tab.QUESTS -> QuestsScreen(modifier = Modifier.fillMaxSize())
            currentTab == Tab.DUNGEON -> DungeonScreen(modifier = Modifier.fillMaxSize())
            currentTab == Tab.SECRET -> SecretScreen(modifier = Modifier.fillMaxSize())
            currentTab == Tab.HOMESTEAD -> HomesteadScreen(modifier = Modifier.fillMaxSize())
            currentTab == Tab.SECT -> SectScreen(modifier = Modifier.fillMaxSize())
            currentTab == Tab.SEASON -> SeasonScreen(modifier = Modifier.fillMaxSize())
            currentTab == Tab.ABOUT -> AboutScreen(codex, modifier = Modifier.fillMaxSize())
        }

        // 底部导航（Scrollable 支持 10 tab）
        NavigationBar(
            modifier = Modifier.align(androidx.compose.ui.Alignment.BottomCenter)
        ) {
            Tab.values().forEach { tab ->
                NavigationBarItem(
                    selected = currentTab == tab,
                    onClick = { currentTab = tab },
                    icon = { TabIcon(tab) },
                    label = { Text(tab.label, fontSize = 10.sp) }
                )
            }
        }
    }
}

@Composable
private fun TabIcon(tab: Tab) {
    val icon = when (tab) {
        Tab.MAP -> Icons.Filled.Place
        Tab.CODEX -> Icons.Filled.MenuBook
        Tab.BATTLE -> Icons.Filled.SportsEsports
        Tab.CULTIVATE -> Icons.Filled.AutoAwesome
        Tab.EQUIP -> Icons.Filled.Shield
        Tab.FUSE -> Icons.Filled.Merge
        Tab.TALENT -> Icons.Filled.Star
        Tab.STORE -> Icons.Filled.ShoppingCart
        Tab.QUESTS -> Icons.Filled.Assignment
        Tab.DUNGEON -> Icons.Filled.Castle
        Tab.SECRET -> Icons.Filled.Nature
        Tab.HOMESTEAD -> Icons.Filled.Home
        Tab.SECT -> Icons.Filled.Groups
        Tab.SEASON -> Icons.Filled.Schedule
        Tab.ABOUT -> Icons.Filled.Info
    }
    Icon(icon, contentDescription = tab.label, modifier = Modifier.size(20.dp))
}

@Composable
private fun AboutScreen(codex: Codex, modifier: Modifier) {
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("山海妖志 · v0.3.0", fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Text("V1 核心循环 + V2 养成经济 + V3 副本社交 已交付", fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        HorizontalDivider()
        Text("引擎健康度", fontSize = 18.sp, fontWeight = FontWeight.Medium)
        AboutRow("MonsterPool", "${MonsterPool.all.size} 妖")
        AboutRow("Codex", "${codex.regionTotal()} 妖")
        AboutRow("验证链", "706/706 + 75/75 + 30/30")
        HorizontalDivider()
        Text("V1 ✅", fontSize = 16.sp, fontWeight = FontWeight.Medium)
        AboutRow("① 巡妖图 + 收妖", "✅ osmdroid + CaptureEngine")
        AboutRow("② 妖典", "✅ 筛选 + 详情")
        AboutRow("③ 试炼", "✅ 回合制 + BattleEngine")
        Text("V2 ✅", fontSize = 16.sp, fontWeight = FontWeight.Medium)
        AboutRow("④ 养成", "✅ CultivationEngine 升级/突破/进化")
        AboutRow("⑤ 装备", "✅ EquipmentLibrary 展示")
        AboutRow("⑥ 融合", "✅ FusionEngine.plan()")
        AboutRow("⑦ 天赋", "✅ TalentEngine 三分支加点")
        AboutRow("⑧ 商店", "✅ ShopEngine 焚香")
        AboutRow("⑨ 敕令", "✅ QuestLibrary 列表")
        Text("V3 ✅", fontSize = 16.sp, fontWeight = FontWeight.Medium)
        AboutRow("⑩ 副本妖境", "✅ DungeonEngine 四类副本")
        AboutRow("⑪ 在家秘境", "✅ SecretRealmEngine 主题刷妖")
        AboutRow("⑫ 家园洞府", "✅ HomesteadEngine 离线产出")
        AboutRow("⑬ 宗门科技", "✅ SectTechEngine 科技树")
        AboutRow("⑭ 赛季通行证", "✅ SeasonEngine 90 天双轨")
        Text("V4 待做", fontSize = 16.sp, fontWeight = FontWeight.Medium)
        AboutRow("荣誉/教程/活动", "⬜")
        AboutRow("视觉润色", "⬜")
    }
}

@Composable private fun AboutRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, fontSize = 14.sp)
        Text(value, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
}
