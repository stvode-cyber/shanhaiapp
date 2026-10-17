package com.shanhai.yaozhi.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shanhai.core.data.MonsterPool
import com.shanhai.core.logic.CultivationEngine
import com.shanhai.core.model.MonsterInstance
import com.shanhai.yaozhi.LocalGameState
import com.shanhai.yaozhi.LocalGsVersion
import com.shanhai.yaozhi.mutate

/**
 * 养成 · 升级/突破/进化（GDD 五·养成）。
 * 操作 GameState.owned 里的真实妖实例。
 */
@Composable
fun CultivateScreen(modifier: Modifier = Modifier) {
    val gsState = LocalGameState.current
    val versionState = LocalGsVersion.current
    val _v = versionState.value  // 订阅 → mutate 后必重组
    val gs = gsState.value

    // 玩家第一只妖（无则自动创建一只占位妖）
    val firstInst = gs.owned.values.firstOrNull() ?: MonsterInstance(
        instanceId = "inst_auto", defId = MonsterPool.all[0].id,
        level = 5, exp = 0, breakthrough = 0
    )

    val def = MonsterPool.all.firstOrNull { it.id == firstInst.defId } ?: MonsterPool.all[0]
    val inst = gs.owned[firstInst.instanceId] ?: firstInst

    val levelCap = CultivationEngine.levelCap(inst)
    val btLevelReq = CultivationEngine.breakthroughLevelReq(inst.breakthrough)
    val btCost = CultivationEngine.breakthroughCost(inst.breakthrough)
    val canBT = CultivationEngine.canBreakthrough(inst)
    val canEvo = CultivationEngine.canEvolve(inst)
    val expNeed = CultivationEngine.expForLevel(inst.level + 1)

    Column(modifier = modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("养成 · ${def.name}", fontSize = 22.sp, fontWeight = FontWeight.Bold)

        Surface(shape = RoundedCornerShape(12.dp), tonalElevation = 2.dp,
            modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("等级", fontSize = 14.sp); Text("Lv.${inst.level}", fontWeight = FontWeight.Bold)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("突破", fontSize = 14.sp); Text("${inst.breakthrough} 次", fontWeight = FontWeight.Bold)
                }
                Text("等级上限：$levelCap", fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        Text("升级", fontSize = 16.sp, fontWeight = FontWeight.Medium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                gsState.mutate(versionState) { it.gainExp(inst.instanceId, 500) }
            }, modifier = Modifier.weight(1f)) { Text("+500 EXP") }
            Button(onClick = {
                gsState.mutate(versionState) { it.gainExp(inst.instanceId, 2000) }
            }, modifier = Modifier.weight(1f)) { Text("+2000 EXP") }
        }
        Text("升到 Lv.${inst.level + 1} 需 $expNeed EXP", fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant)

        Text("突破", fontSize = 16.sp, fontWeight = FontWeight.Medium)
        Button(
            onClick = {
                gsState.mutate(versionState) { it.breakthroughMonster(inst.instanceId) }
            },
            enabled = canBT,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (canBT) "突破！（Lv.$btLevelReq，花费 $btCost 灵石）"
                 else "需等级 $btLevelReq")
        }

        Text("进化", fontSize = 16.sp, fontWeight = FontWeight.Medium)
        Button(
            onClick = {
                gsState.mutate(versionState) { it.evolveMonster(inst.instanceId) }
            },
            enabled = canEvo,
            modifier = Modifier.fillMaxWidth()
        ) { Text(if (canEvo) "进化！" else "无进化链或等级不足") }

        Text("灵玉：${gs.lingyu} ｜ 妖丹：${gs.yaodan}", fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
