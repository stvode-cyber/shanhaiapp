package com.shanhai.yaozhi

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
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
 * 山海妖志 · Android Compose 壳（v0.1.0）
 * V1 核心循环：巡妖图 → 收妖 → 妖典 → 试炼
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        OsmBootstrap.init(this)

        val state = GameState()
        val codex = Codex(MonsterPool.all)

        setContent {
            ShanhaiTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    GameRoot(codex = codex, state = state)
                }
            }
        }
    }
}

private enum class Tab(val label: String) {
    MAP("巡妖图"), CAPTURE("收妖"), CODEX("妖典"), BATTLE("试炼"), ABOUT("关于")
}

@Composable
private fun GameRoot(codex: Codex, state: GameState) {
    var currentTab by remember { mutableStateOf(Tab.MAP) }
    var captureNode by remember { mutableStateOf<SpiritNode?>(null) }

    // 如果在 CAPTURE tab 但没有选中节点 → 显示空状态
    Box(modifier = Modifier.fillMaxSize()) {
        when {
            currentTab == Tab.MAP -> MapScreen(
                onEnterCapture = { node ->
                    captureNode = node
                    currentTab = Tab.CAPTURE
                },
                modifier = Modifier.fillMaxSize()
            )
            currentTab == Tab.CAPTURE -> if (captureNode != null) {
                CaptureScreen(
                    nodeName = captureNode!!.name,
                    onBack = { currentTab = Tab.MAP; captureNode = null },
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                CaptureEmpty(onPickFromMap = { currentTab = Tab.MAP })
            }
            currentTab == Tab.CODEX -> CodexScreen(codex, modifier = Modifier.fillMaxSize())
            currentTab == Tab.BATTLE -> BattleScreen(modifier = Modifier.fillMaxSize())
            currentTab == Tab.ABOUT -> AboutScreen(codex, modifier = Modifier.fillMaxSize())
        }

        // 底部导航
        NavigationBar(
            modifier = Modifier.align(androidx.compose.ui.Alignment.BottomCenter)
        ) {
            Tab.values().forEach { tab ->
                NavigationBarItem(
                    selected = currentTab == tab,
                    onClick = { currentTab = tab },
                    icon = { TabIcon(tab) },
                    label = { Text(tab.label, fontSize = 11.sp) }
                )
            }
        }
    }
}

@Composable
private fun TabIcon(tab: Tab) {
    val icon = when (tab) {
        Tab.MAP -> Icons.Filled.Place
        Tab.CAPTURE -> Icons.Filled.MyLocation
        Tab.CODEX -> Icons.Filled.MenuBook
        Tab.BATTLE -> Icons.Filled.SportsEsports
        Tab.ABOUT -> Icons.Filled.Info
    }
    Icon(icon, contentDescription = tab.label)
}

@Composable
private fun CaptureEmpty(onPickFromMap: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = androidx.compose.ui.Alignment.Center
    ) {
        Column(horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
            Text("请先从巡妖图选一个灵脉节点", fontSize = 16.sp)
            Spacer(Modifier.height(16.dp))
            Button(onClick = onPickFromMap) { Text("去巡妖图") }
        }
    }
}

@Composable
private fun AboutScreen(codex: Codex, modifier: Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("山海妖志 · v0.1.0", fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Text("Kotlin 2.0.21 + Compose BOM 2024.09.03", fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        HorizontalDivider()
        Text("引擎健康度", fontSize = 18.sp, fontWeight = FontWeight.Medium)
        AboutRow("MonsterPool", "${MonsterPool.all.size} 妖")
        AboutRow("Codex 总妖数", "${codex.regionTotal()}")
        AboutRow("已收服", "${codex.regionCaught()}")
        AboutRow("验证链", "706/706 + 75/75 + 30/30")
        HorizontalDivider()
        Text("3D 状态", fontSize = 18.sp, fontWeight = FontWeight.Medium)
        AboutRow("SceneView 路线", "❌ compileSdk 34 不兼容（需 37）")
        AboutRow("osmdroid 2D 地图", "✅ 可用")
        HorizontalDivider()
        Text("V1 已交付", fontSize = 16.sp, fontWeight = FontWeight.Medium)
        AboutRow("① 巡妖图", "✅ osmdroid + 灵脉节点快选")
        AboutRow("② 收妖", "✅ 光环收缩 + CaptureEngine")
        AboutRow("③ 妖典", "✅ 列表 + 稀有/类别筛选 + 详情")
        AboutRow("④ 试炼", "✅ 回合制 + BattleEngine 五行克制")
    }
}

@Composable private fun AboutRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, fontSize = 14.sp)
        Text(value, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
}
