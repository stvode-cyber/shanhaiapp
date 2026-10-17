package com.shanhai.yaozhi.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shanhai.core.logic.HomesteadEngine
import com.shanhai.yaozhi.LocalGameState
import com.shanhai.yaozhi.LocalGsVersion
import com.shanhai.yaozhi.mutate

/**
 * 家园洞府 · V2
 * gs.homesteadLevel 真实值 + gs.upgradeHomestead() mutation
 */
@Composable
fun HomesteadScreen(modifier: Modifier = Modifier) {
    val gsState = LocalGameState.current
    val versionState = LocalGsVersion.current
    val _v = versionState.value  // 订阅 → 升级后自动刷新
    val gs = gsState.value

    val fengshui = gs.homesteadLevel
    val incRate = HomesteadEngine.incenseRate(fengshui)
    val pillRate = HomesteadEngine.pillRate(fengshui)
    val atkBonus = HomesteadEngine.fengshuiAttackBonus(fengshui)
    val canUpgrade = gs.canUpgradeHomestead()

    Column(modifier = modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("家园洞府", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text("妖丹：${gs.yaodan}", fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant)

        Surface(shape = RoundedCornerShape(12.dp), tonalElevation = 2.dp,
            modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("阵法风水 · Lv.$fengshui", fontSize = 16.sp, fontWeight = FontWeight.Medium)
                Text("香火时产：${"%.1f".format(incRate)} / 小时", fontSize = 14.sp)
                Text("妖丹时产：${"%.2f".format(pillRate)} / 小时", fontSize = 14.sp)
                Text("全局攻击增益：${"%.0f".format(atkBonus * 100)}%", fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.primary)
                Text("升级花费：${HomesteadEngine.upgradeCost(fengshui)} 妖丹", fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        Button(onClick = {
            gsState.mutate(versionState) { it.upgradeHomestead() }
        }, enabled = canUpgrade, modifier = Modifier.fillMaxWidth()) {
            Text(if (canUpgrade) "升级阵法" else "妖丹不足")
        }
    }
}
