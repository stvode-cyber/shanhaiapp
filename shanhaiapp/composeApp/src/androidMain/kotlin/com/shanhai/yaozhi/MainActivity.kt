package com.shanhai.yaozhi

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.*
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
import com.shanhai.core.data.MonsterPool
import com.shanhai.core.logic.Codex
import com.shanhai.core.logic.GameState
import com.shanhai.core.platform.OsmBootstrap
import com.shanhai.yaozhi.screens.*
import com.shanhai.yaozhi.theme.ShanhaiTheme

/**
 * 山海妖志 · Android Compose 壳 v0.4.0
 * 5 核心 tab 底部导航 + Hub 挂次级 13 入口
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        OsmBootstrap.init(this)
        val codex = Codex(MonsterPool.all)
        val gs = loadInitialGameState(this)  // 从 SharedPreferences 读档；没有则空 GameState()
        setContent {
            ShanhaiTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    GameStateProvider(gs) {
                        GameRoot(codex = codex)
                    }
                }
            }
        }
    }
}

/** 底部 5 核心 tab */
private enum class CoreTab(val label: String, val icon: ImageVector) {
    MAP("巡妖", Icons.Filled.Place),
    CODEX("妖典", Icons.AutoMirrored.Filled.MenuBook),
    BATTLE("试炼", Icons.Filled.SportsEsports),
    DUNGEON("副本", Icons.Filled.Castle),
    HUB("我的", Icons.Filled.Person),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GameRoot(codex: Codex) {
    var currentTab by remember { mutableStateOf(CoreTab.MAP) }
    var hubNav by remember { mutableStateOf<HubItem?>(null) }

    // 当前页面：如果有 hubNav 则显示 Hub 目标页面
    val currentScreen = hubNav ?: currentTab.toHubItem()

    Scaffold(
        bottomBar = {
            NavigationBar {
                CoreTab.values().forEach { tab ->
                    NavigationBarItem(
                        selected = currentTab == tab && hubNav == null,
                        onClick = {
                            currentTab = tab
                            hubNav = null  // 清空 Hub 跳转
                        },
                        icon = { Icon(tab.icon, contentDescription = tab.label) },
                        label = { Text(tab.label, fontSize = 11.sp) }
                    )
                }
            }
        }
    ) { padding ->
        AnimatedContent(
            targetState = currentScreen,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "screen_transition"
        ) { screen ->
            when (screen) {
                HubItem.MAP -> MapScreen(onEnterCapture = {},
                    modifier = Modifier.padding(padding).fillMaxSize())
                HubItem.CODEX -> CodexScreen(
                    modifier = Modifier.padding(padding).fillMaxSize())
                HubItem.BATTLE -> BattleScreen(
                    modifier = Modifier.padding(padding).fillMaxSize())
                HubItem.DUNGEON -> DungeonScreen(
                    modifier = Modifier.padding(padding).fillMaxSize())

                HubItem.CULTIVATE -> CultivateScreen(
                    modifier = Modifier.padding(padding).fillMaxSize())
                HubItem.EQUIP -> EquipScreen(
                    modifier = Modifier.padding(padding).fillMaxSize())
                HubItem.FUSE -> FuseScreen(
                    modifier = Modifier.padding(padding).fillMaxSize())
                HubItem.TALENT -> TalentScreen(
                    modifier = Modifier.padding(padding).fillMaxSize())
                HubItem.STORE -> StoreScreen(
                    modifier = Modifier.padding(padding).fillMaxSize())
                HubItem.QUESTS -> QuestsScreen(
                    modifier = Modifier.padding(padding).fillMaxSize())
                HubItem.SECRET -> SecretScreen(
                    modifier = Modifier.padding(padding).fillMaxSize())
                HubItem.HOMESTEAD -> HomesteadScreen(
                    modifier = Modifier.padding(padding).fillMaxSize())
                HubItem.SECT -> SectScreen(
                    modifier = Modifier.padding(padding).fillMaxSize())
                HubItem.SEASON -> SeasonScreen(
                    modifier = Modifier.padding(padding).fillMaxSize())
                HubItem.HONOR -> HonorScreen(
                    modifier = Modifier.padding(padding).fillMaxSize())
                HubItem.TUTORIAL -> TutorialScreen(
                    modifier = Modifier.padding(padding).fillMaxSize())
                HubItem.ACTIVITY -> ActivityScreen(
                    modifier = Modifier.padding(padding).fillMaxSize())
                HubItem.ABOUT -> AboutScreen(
                    modifier = Modifier.padding(padding).fillMaxSize())

                // HUB tab 本身
                null -> HubScreen(
                    onNavigate = { hubNav = it },
                    modifier = Modifier.padding(padding).fillMaxSize())
            }
        }
    }
}

private fun CoreTab.toHubItem(): HubItem? = when (this) {
    CoreTab.MAP -> HubItem.MAP
    CoreTab.CODEX -> HubItem.CODEX
    CoreTab.BATTLE -> HubItem.BATTLE
    CoreTab.DUNGEON -> HubItem.DUNGEON
    CoreTab.HUB -> null  // null = 显示 HubScreen
}

/** AboutScreen · 版本信息 + 存档管理 */
@Composable
private fun AboutScreen(modifier: Modifier) {
    val gsState = LocalGameState.current
    val versionState = LocalGsVersion.current
    val _v = versionState.value  // 订阅 → 存档操作后刷新时间戳
    val gs = gsState.value
    val context = androidx.compose.ui.platform.LocalContext.current
    val mgr = remember(context) { SaveManager(context.applicationContext) }

    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("山海妖志 · v0.4.0", fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Text("19/19 Screen 已接 GameState ✅", fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant)

        HorizontalDivider()

        // 📦 存档管理
        Text("📦 存档管理", fontSize = 18.sp, fontWeight = FontWeight.Medium)
        val lastTs = mgr.lastSaveTs()
        val lastSaveText = if (lastTs > 0)
            java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.CHINA)
                .format(java.util.Date(lastTs))
        else "从未存档"
        Text("最后存档：$lastSaveText", fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant)

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = {
                    val ts = mgr.save(gs.toSaveData())
                    // 触发一下重组刷新时间戳（读时间戳是 remember 缓存的，必须 bump version）
                    gsState.mutate(versionState) { it }
                },
                modifier = Modifier.weight(1f)
            ) { Text("立即存档") }

            OutlinedButton(
                onClick = {
                    val loaded = mgr.load()
                    if (loaded != null) {
                        gsState.mutate(versionState) { it.applySaveData(loaded) }
                    }
                },
                modifier = Modifier.weight(1f)
            ) { Text("读档") }
        }

        OutlinedButton(
            onClick = {
                mgr.reset()
                gsState.mutate(versionState) { GameState().apply { } }
            },
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = MaterialTheme.colorScheme.error
            ),
            modifier = Modifier.fillMaxWidth()
        ) { Text("⚠️ 清空存档（不可恢复）") }

        HorizontalDivider()

        // 引擎健康度
        Text("引擎健康度", fontSize = 18.sp, fontWeight = FontWeight.Medium)
        AboutRow("MonsterPool", "${MonsterPool.all.size} 妖")
        AboutRow("Codex", "${gs.codex.regionTotal()} 妖 · 已获 ${gs.codex.regionCaught()}")
        AboutRow("验证链", "706/706 + 75/75 + 37/37")
        HorizontalDivider()
        Text("资源", fontSize = 16.sp, fontWeight = FontWeight.Medium)
        AboutRow("灵玉", "${gs.lingyu}")
        AboutRow("妖丹", "${gs.yaodan}")
        AboutRow("香火", "${gs.incense}")
        HorizontalDivider()
        Text("引擎层", fontSize = 16.sp, fontWeight = FontWeight.Medium)
        AboutRow("21 引擎", "shared KMP 全量")
        AboutRow("Android 壳", "Jetpack Compose + osmdroid")
        AboutRow("存档", "SharedPreferences + JSON 序列化")
    }
}

@Composable private fun AboutRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, fontSize = 14.sp)
        Text(value, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
}
