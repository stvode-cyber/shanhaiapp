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
import com.shanhai.core.data.defaultRandom
import com.shanhai.core.logic.RegionTheme
import com.shanhai.core.logic.SecretRealmEngine

@Composable
fun SecretScreen(modifier: Modifier = Modifier) {
    val themes = remember { RegionTheme.values().toList() }
    var freeEntries by remember { mutableIntStateOf(2) }
    var lastSpawn by remember { mutableStateOf<List<String>>(emptyList()) }

    Column(modifier = modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("在家秘境", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text("每日免费 $freeEntries 次 · 耗尽后 ${SecretRealmEngine.INCENSE_COST} 香火/次",
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
                            lastSpawn = SecretRealmEngine.generateSpawns(theme, SecretRealmEngine.SPAWN_COUNT)
                            if (freeEntries > 0) freeEntries--
                        },
                        enabled = freeEntries > 0
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
