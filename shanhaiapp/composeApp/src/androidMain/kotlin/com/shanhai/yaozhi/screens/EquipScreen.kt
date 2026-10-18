package com.shanhai.yaozhi.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shanhai.core.data.EquipmentLibrary
import com.shanhai.core.model.Rarity
import com.shanhai.yaozhi.LocalGameState
import com.shanhai.yaozhi.LocalGsVersion
import com.shanhai.yaozhi.components.AnimatedStatCard
import com.shanhai.yaozhi.components.ChipSize
import com.shanhai.yaozhi.components.EmptyState
import com.shanhai.yaozhi.components.GradientHeader
import com.shanhai.yaozhi.components.RarityChip
import com.shanhai.yaozhi.theme.*

/**
 * 装备库 · V3（视觉润色）
 * v3: GradientHeader + 装备卡稀有度光晕 + 属性高亮
 */
@Composable
fun EquipScreen(modifier: Modifier = Modifier) {
    val gsState = LocalGameState.current
    val _v = LocalGsVersion.current.value
    val gs = gsState.value
    val equips = EquipmentLibrary.all

    Column(modifier = modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {

        GradientHeader(title = "装备库", subtitle = "${equips.size} 件可锻造")

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AnimatedStatCard("灵玉", gs.lingyu, "💎", JinHuang, Modifier.weight(1f))
            AnimatedStatCard("妖丹", gs.yaodan, "🧪", ZhuSha, Modifier.weight(1f))
        }

        Spacer(Modifier.height(4.dp))

        if (equips.isEmpty()) {
            EmptyState("🛡️", "暂无装备")
        } else {
            equips.forEach { e ->
                val rarityColor = colorForRarity(e.rarity)
                Surface(shape = RoundedCornerShape(14.dp), tonalElevation = 2.dp,
                    modifier = Modifier.fillMaxWidth()) {
                    Row(modifier = Modifier.padding(14.dp).fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(52.dp).clip(CircleShape)
                            .background(rarityColor.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center) {
                            Box(modifier = Modifier.size(42.dp).clip(CircleShape).background(rarityColor),
                                contentAlignment = Alignment.Center) {
                                Text(e.name.take(1), color = XueBai, fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold)
                            }
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(e.name, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                                RarityChip(e.rarity, size = ChipSize.SMALL)
                            }
                            Text("${e.slot.name} · ${e.element?.name ?: "-"}", fontSize = 11.sp, color = YanHui)
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                if (e.baseAtk > 0) Text("⚔️${e.baseAtk}", fontSize = 12.sp, color = ZhuSha)
                                if (e.baseDef > 0) Text("🛡️${e.baseDef}", fontSize = 12.sp, color = QingDai)
                                if (e.baseSpd > 0) Text("💨${e.baseSpd}", fontSize = 12.sp, color = JinHuang)
                            }
                        }
                    }
                }
            }
        }
    }
}
