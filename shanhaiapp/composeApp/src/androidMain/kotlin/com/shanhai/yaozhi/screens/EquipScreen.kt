package com.shanhai.yaozhi.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shanhai.core.data.EquipmentLibrary

@Composable
fun EquipScreen(modifier: Modifier = Modifier) {
    val equips = EquipmentLibrary.all
    Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
        Text("装备库（${equips.size} 件）", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        equips.forEach { eq ->
            Surface(shape = RoundedCornerShape(10.dp), tonalElevation = 1.dp,
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Row(modifier = Modifier.padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween) {
                    Column {
                        Text(eq.name, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Text("${eq.slot.label} · ${eq.element?.label ?: "通用"} · ${eq.rarity.label}",
                            fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text("ATK+${eq.baseAtk} DEF+${eq.baseDef}", fontSize = 12.sp)
                }
            }
        }
    }
}

private val com.shanhai.core.model.EquipSlot.label: String get() = when (this) {
    com.shanhai.core.model.EquipSlot.WEAPON -> "武器"
    com.shanhai.core.model.EquipSlot.ARMOR -> "防具"
    com.shanhai.core.model.EquipSlot.ACCESSORY -> "饰品"
}
private val com.shanhai.core.model.Element.label: String get() = when (this) {
    com.shanhai.core.model.Element.METAL -> "金"
    com.shanhai.core.model.Element.WOOD -> "木"
    com.shanhai.core.model.Element.EARTH -> "土"
    com.shanhai.core.model.Element.WATER -> "水"
    com.shanhai.core.model.Element.FIRE -> "火"
    com.shanhai.core.model.Element.WIND -> "风"
    com.shanhai.core.model.Element.THUNDER -> "雷"
}
private val com.shanhai.core.model.Rarity.label: String get() = when (this) {
    com.shanhai.core.model.Rarity.FAN -> "凡"
    com.shanhai.core.model.Rarity.LING -> "灵"
    com.shanhai.core.model.Rarity.XIAN -> "仙"
    com.shanhai.core.model.Rarity.SHEN -> "神"
}
