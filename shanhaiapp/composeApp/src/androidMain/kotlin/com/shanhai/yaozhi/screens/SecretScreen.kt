package com.shanhai.yaozhi.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shanhai.core.data.MonsterPool
import com.shanhai.core.logic.RegionTheme
import com.shanhai.core.logic.SecretRealmEngine
import com.shanhai.yaozhi.LocalGameState
import com.shanhai.yaozhi.LocalGsVersion
import com.shanhai.yaozhi.mutate

/**
 * 在家秘境 · V2
 * 本地 freeEntries → gs.incense 真实值
 * 按钮调 gs.enterSecretRealm()（内部扣香火 + 记事件）
 */
@Composable
fun SecretScreen(modifier: Modifier = Modifier) {
    val gsState = LocalGameState.current
    val versionState = LocalGsVersion.current
    val _v = versionState.value  // 订阅 → 进入秘境后香火刷新
    val gs = gsState.value

    val themes = remember { RegionTheme.values().toList() }
    var lastSpawn by remember { mutableStateOf<List<String>>(emptyList()) }

    Column(modifier = modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("在家秘境", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text("香火：${gs.incense} ｜ 每次消耗 ${SecretRealmEngine.INCENSE_COST} 香火",
            fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

        themes.forEach { theme ->
            Surface(shape = RoundedCornerShape(10.dp), tonalElevation = 2.dp,
                modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.padding(16.dp).fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                ) {
                    Column {
                        Text("🏞 ${theme.label}", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        val bias = SecretRealmEngine.themeElementBias(theme)
                        Text("五行偏向：${bias.label} · 刷 ${SecretRealmEngine.SPAWN_COUNT} 只",
                            fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Button(
                        onClick = {
                            val result = gsState.mutate(versionState) { it.enterSecretRealm() }
                            lastSpawn = result?.spawns ?: emptyList()
                        },
                        enabled = gs.incense >= SecretRealmEngine.INCENSE_COST
                    ) { Text("入秘境") }
                }
            }
        }

        if (lastSpawn.isNotEmpty()) {
            HorizontalDivider()
            Text("本次刷妖（${lastSpawn.size} 只）", fontSize = 16.sp, fontWeight = FontWeight.Medium)
            lastSpawn.forEach { mid ->
                MonsterPool.byId(mid)?.let { m ->
                    Text("  · ${m.name}（${m.rarity.label}）", fontSize = 14.sp)
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
private val com.shanhai.core.model.Rarity.label: String get() = when (this) {
    com.shanhai.core.model.Rarity.FAN -> "凡"; com.shanhai.core.model.Rarity.LING -> "灵"
    com.shanhai.core.model.Rarity.XIAN -> "仙"; com.shanhai.core.model.Rarity.SHEN -> "神"
}
