package com.shanhai.yaozhi.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shanhai.core.data.MonsterPool
import com.shanhai.core.logic.GachaEngine
import com.shanhai.core.model.Rarity
import com.shanhai.yaozhi.LocalGameState

/**
 * 商店 + 祈妖抽卡（GDD 十四·经济与变现）。
 * Tab 1：引妖香购买
 * Tab 2：祈妖 · 通用池（保底 50/90）
 * Tab 3：祈妖 · 限定神池（保底 40/85，神率 1.8%）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StoreScreen(modifier: Modifier = Modifier) {
    val gs = LocalGameState.current
    var selectedTab by remember { mutableIntStateOf(0) }

    Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
        Text("商店 · 祈妖", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text("灵玉：${gs.lingyu} ｜ 妖丹：${gs.yaodan}", fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant)

        Spacer(Modifier.height(8.dp))

        TabRow(selectedTabIndex = selectedTab) {
            Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 },
                text = { Text("引妖香", fontSize = 12.sp) })
            Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 },
                text = { Text("通用祈妖", fontSize = 12.sp) })
            Tab(selected = selectedTab == 2, onClick = { selectedTab = 2 },
                text = { Text("限定神池", fontSize = 12.sp) })
        }

        Spacer(Modifier.height(12.dp))

        when (selectedTab) {
            0 -> IncenseTab(gs)
            1 -> GachaTab(gs, GachaEngine.PoolType.NORMAL)
            2 -> GachaTab(gs, GachaEngine.PoolType.LIMITED)
        }
    }
}

// ── Tab 0：引妖香 ──

@Composable
private fun IncenseTab(gs: com.shanhai.core.logic.GameState) {
    val cost = com.shanhai.core.logic.ShopEngine.INCENSE_REFILL_COST_LINGYU
    val amount = com.shanhai.core.logic.ShopEngine.INCENSE_REFILL_AMOUNT
    val cap = com.shanhai.core.logic.ShopEngine.INCENSE_DAILY_BUY_CAP
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("今日已购：${gs.shopIncenseBought} / $cap", fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Surface(shape = RoundedCornerShape(10.dp), tonalElevation = 2.dp,
            modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("引妖香·香火回补", fontSize = 16.sp, fontWeight = FontWeight.Medium)
                Text("花费 $cost 灵玉 → 补 $amount 香火（日限 $cap）", fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Button(
                    onClick = {
                        val today = java.time.LocalDate.now().toString()
                        gs.buyIncense(today)
                    },
                    enabled = gs.lingyu >= cost && gs.shopIncenseBought < cap,
                    modifier = Modifier.fillMaxWidth()
                ) { Text("购买（-$cost 灵玉）") }
            }
        }
    }
}

// ── Tab 1/2：祈妖抽卡（通用 / 限定） ──

@Composable
private fun GachaTab(gs: com.shanhai.core.logic.GameState, pool: GachaEngine.PoolType) {
    val isLimited = pool == GachaEngine.PoolType.LIMITED
    val hardLimit = if (isLimited) GachaEngine.LIMITED_HARD_PITY else GachaEngine.NORMAL_HARD_PITY
    val softStart = if (isLimited) GachaEngine.LIMITED_SOFT_PITY_START else GachaEngine.NORMAL_SOFT_PITY_START
    val sinceShen = if (isLimited) gs.gachaLimitedSinceShen else gs.gachaNormalSinceShen
    val shenRate = if (isLimited) GachaEngine.LIMITED_SHEN else GachaEngine.NORMAL_SHEN

    var lastResult by remember { mutableStateOf<List<GachaEngine.GachaResult>?>(null) }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.verticalScroll(rememberScrollState())) {

        // 池子信息卡
        Surface(shape = RoundedCornerShape(10.dp), tonalElevation = if (isLimited) 4.dp else 2.dp,
            modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(if (isLimited) "🎯 限定神池（限时）" else "🎰 通用祈妖池",
                    fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Text(if (isLimited) "神率 ${(shenRate * 1000).toInt() / 10.0}% ｜ 软保底 ${softStart + 1}抽起 ｜ 硬保底 $hardLimit 抽必神"
                    else "灵40% 仙45% 神15% ｜ 软保底 ${softStart + 1}抽起 ｜ 硬保底 $hardLimit 抽必神",
                    fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(4.dp))
                Text("距上次出神：$sinceShen 抽", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                LinearProgressIndicator(
                    progress = { (sinceShen.toFloat() / hardLimit).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().height(6.dp),
                )
            }
        }

        // 抽卡按钮
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth()) {
            Button(onClick = {
                lastResult = gs.pullGacha(ten = false, pool = pool)
            }, enabled = gs.lingyu >= GachaEngine.SINGLE_LINGYU,
                modifier = Modifier.weight(1f).height(48.dp)) {
                Text("单抽 -${GachaEngine.SINGLE_LINGYU}")
            }
            Button(onClick = {
                lastResult = gs.pullGacha(ten = true, pool = pool)
            }, enabled = gs.lingyu >= GachaEngine.TEN_LINGYU,
                modifier = Modifier.weight(1f).height(48.dp)) {
                Text("十连 -${GachaEngine.TEN_LINGYU}")
            }
        }

        // 抽卡结果展示
        lastResult?.let { results ->
            Spacer(Modifier.height(8.dp))
            Text("本次结果：", fontSize = 14.sp, fontWeight = FontWeight.Medium)
            results.forEach { r ->
                val def = MonsterPool.byId(r.monsterId)
                val rarityColor = when (r.rarity) {
                    Rarity.SHEN -> if (isLimited) 0xFFFFD700.toInt() else 0xFFE74C3C.toInt()
                    Rarity.XIAN -> 0xFF9B59B6.toInt()
                    Rarity.LING -> 0xFF3498DB.toInt()
                    else -> 0xFF95A5A6.toInt()
                }
                Surface(shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth()) {
                    Row(modifier = Modifier.padding(10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(def?.name ?: r.monsterId, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        Text("${r.rarity.name}  签:${r.sign.name}", fontSize = 12.sp,
                            color = androidx.compose.ui.graphics.Color(rarityColor))
                    }
                }
            }
        }
    }
}
