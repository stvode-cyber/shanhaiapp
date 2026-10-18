package com.shanhai.yaozhi.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shanhai.core.data.MonsterPool
import com.shanhai.core.logic.GachaEngine
import com.shanhai.core.model.Rarity
import com.shanhai.yaozhi.LocalGameState
import com.shanhai.yaozhi.LocalGsVersion
import com.shanhai.yaozhi.mutate
import com.shanhai.yaozhi.components.ChipSize
import com.shanhai.yaozhi.components.GradientHeader
import com.shanhai.yaozhi.components.RarityChip
import com.shanhai.yaozhi.theme.*
import kotlinx.coroutines.launch

/**
 * 商店 + 祈妖 · V3（视觉润色）
 * v3: GradientHeader + TabRow 彩色指示 + 祈妖池渐变底 + 结果 RarityChip
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StoreScreen(modifier: Modifier = Modifier) {
    val gsState = LocalGameState.current
    val versionState = LocalGsVersion.current
    val _v = versionState.value  // 订阅 → mutate 后必重组
    val gs = gsState.value
    var selectedTab by remember { mutableIntStateOf(0) }

    Column(modifier = modifier.fillMaxSize().padding(16.dp)) {

        // v3: GradientHeader + 灵玉/妖丹
        GradientHeader(
            title = "商店 · 祈妖",
            subtitle = "灵玉 ${gs.lingyu} · 妖丹 ${gs.yaodan}"
        )

        Spacer(Modifier.height(8.dp))

        // v3: TabRow 彩色指示
        TabRow(selectedTabIndex = selectedTab,
            containerColor = XuanZhi.copy(alpha = 0.3f),
            contentColor = MoHei) {
            Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 },
                text = { Text("引妖香", fontSize = 13.sp) },
                selectedContentColor = ZhuSha)
            Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 },
                text = { Text("通用祈妖", fontSize = 13.sp) },
                selectedContentColor = QingDai)
            Tab(selected = selectedTab == 2, onClick = { selectedTab = 2 },
                text = { Text("限定神池", fontSize = 13.sp) },
                selectedContentColor = JinHuang)
        }

        Spacer(Modifier.height(12.dp))

        when (selectedTab) {
            0 -> IncenseTabV3(gsState, versionState)
            1 -> GachaTabV3(gsState, versionState, GachaEngine.PoolType.NORMAL)
            2 -> GachaTabV3(gsState, versionState, GachaEngine.PoolType.LIMITED)
        }
    }
}

@Composable
private fun IncenseTabV3(
    gsState: androidx.compose.runtime.MutableState<com.shanhai.core.logic.GameState>,
    versionState: androidx.compose.runtime.MutableState<Long>,
) {
    val gs = gsState.value
    val cost = com.shanhai.core.logic.ShopEngine.INCENSE_REFILL_COST_LINGYU
    val amount = com.shanhai.core.logic.ShopEngine.INCENSE_REFILL_AMOUNT
    val cap = com.shanhai.core.logic.ShopEngine.INCENSE_DAILY_BUY_CAP
    val btnScope = rememberCoroutineScope()
    val btnScale = remember { Animatable(1f) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Text("今日已购", fontSize = 12.sp, color = YanHui)
            Text("${gs.shopIncenseBought} / $cap", fontSize = 12.sp,
                color = if (gs.shopIncenseBought >= cap) ZhuSha else QingDai,
                fontWeight = FontWeight.Bold)
        }
        Surface(shape = RoundedCornerShape(14.dp), tonalElevation = 2.dp,
            modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(modifier = Modifier.size(44.dp).clip(androidx.compose.foundation.shape.CircleShape)
                        .background(ZhuShaSoft.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center) {
                        Text("🔥", fontSize = 22.sp)
                    }
                    Column {
                        Text("引妖香·香火回补", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Text("花费 $cost 灵玉 → 补 $amount 香火", fontSize = 12.sp, color = YanHui)
                    }
                }
                Button(
                    onClick = {
                        btnScope.launch { btnScale.animateTo(0.95f, tween(80)); btnScale.animateTo(1f, tween(150)) }
                        val today = java.time.LocalDate.now().toString()
                        gsState.mutate(versionState) { it.buyIncense(today) }
                    },
                    enabled = gs.lingyu >= cost && gs.shopIncenseBought < cap,
                    modifier = Modifier.fillMaxWidth().height(44.dp)
                        .graphicsLayer { scaleX = btnScale.value; scaleY = btnScale.value },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (gs.lingyu >= cost && gs.shopIncenseBought < cap) ZhuSha else YanHui.copy(alpha = 0.4f))
                ) { Text("购买（-$cost 灵玉）", fontSize = 14.sp) }
            }
        }
    }
}

@Composable
private fun GachaTabV3(
    gsState: androidx.compose.runtime.MutableState<com.shanhai.core.logic.GameState>,
    versionState: androidx.compose.runtime.MutableState<Long>,
    pool: GachaEngine.PoolType,
) {
    val gs = gsState.value
    val isLimited = pool == GachaEngine.PoolType.LIMITED
    val hardLimit = if (isLimited) GachaEngine.LIMITED_HARD_PITY else GachaEngine.NORMAL_HARD_PITY
    val softStart = if (isLimited) GachaEngine.LIMITED_SOFT_PITY_START else GachaEngine.NORMAL_SOFT_PITY_START
    val sinceShen = if (isLimited) gs.gachaLimitedSinceShen else gs.gachaNormalSinceShen
    val shenRate = if (isLimited) GachaEngine.LIMITED_SHEN else GachaEngine.NORMAL_SHEN

    var lastResult by remember { mutableStateOf<List<GachaEngine.GachaResult>?>(null) }
    val btnScope = rememberCoroutineScope()
    val singleScale = remember { Animatable(1f) }
    val tenScale = remember { Animatable(1f) }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.verticalScroll(rememberScrollState())) {

        // v3: 池子信息渐变底
        Surface(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
            Box(modifier = Modifier.fillMaxWidth().background(
                brush = Brush.linearGradient(
                    listOf(
                        if (isLimited) JinHuang.copy(alpha = 0.15f) else QingDai.copy(alpha = 0.1f),
                        XuanZhi
                    ))).padding(16.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(if (isLimited) "🎯 限定神池（限时）" else "🎰 通用祈妖池",
                        fontSize = 16.sp, fontWeight = FontWeight.Bold,
                        color = if (isLimited) JinHuang else QingDai)
                    Text(
                        if (isLimited) "神率 ${(shenRate * 1000).toInt() / 10.0}% · 软保底 ${softStart + 1}抽起 · 硬保底 $hardLimit 抽必神"
                        else "灵40% 仙45% 神15% · 软保底 ${softStart + 1}抽起 · 硬保底 $hardLimit 抽必神",
                        fontSize = 11.sp, color = YanHui)
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("距上次出神", fontSize = 12.sp, color = YanHui)
                        Text("$sinceShen / $hardLimit", fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (sinceShen >= softStart) ZhuSha else MoHei)
                    }
                    val pityAnimated = remember { Animatable(0f) }
                    LaunchedEffect(sinceShen, hardLimit) {
                        pityAnimated.animateTo((sinceShen.toFloat() / hardLimit).coerceIn(0f, 1f), tween(600))
                    }
                    Box(modifier = Modifier.fillMaxWidth().height(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(XuanZhi.copy(alpha = 0.3f))) {
                        Box(modifier = Modifier.fillMaxHeight()
                            .fillMaxWidth(pityAnimated.value)
                            .clip(RoundedCornerShape(4.dp))
                            .background(brush = Brush.horizontalGradient(
                                listOf(if (isLimited) JinHuang else QingDai, ZhuSha))))
                    }
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth()) {
            Button(onClick = {
                btnScope.launch { singleScale.animateTo(0.94f, tween(80)); singleScale.animateTo(1f, tween(150)) }
                lastResult = gsState.mutate(versionState) { it.pullGacha(ten = false, pool = pool) }
            }, enabled = gs.lingyu >= GachaEngine.SINGLE_LINGYU,
                modifier = Modifier.weight(1f).height(48.dp)
                    .graphicsLayer { scaleX = singleScale.value; scaleY = singleScale.value },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isLimited) JinHuang else QingDai)) {
                Text("单抽 -${GachaEngine.SINGLE_LINGYU}", fontSize = 13.sp)
            }
            Button(onClick = {
                btnScope.launch { tenScale.animateTo(0.94f, tween(80)); tenScale.animateTo(1f, tween(150)) }
                lastResult = gsState.mutate(versionState) { it.pullGacha(ten = true, pool = pool) }
            }, enabled = gs.lingyu >= GachaEngine.TEN_LINGYU,
                modifier = Modifier.weight(1f).height(48.dp)
                    .graphicsLayer { scaleX = tenScale.value; scaleY = tenScale.value },
                colors = ButtonDefaults.buttonColors(containerColor = ZhuSha)) {
                Text("十连 -${GachaEngine.TEN_LINGYU}", fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        }

        lastResult?.let { results ->
            Spacer(Modifier.height(4.dp))
            Text("本次结果：", fontSize = 14.sp, fontWeight = FontWeight.Medium)
            results.forEach { r ->
                val def = MonsterPool.byId(r.monsterId)
                Surface(shape = RoundedCornerShape(10.dp),
                    color = colorForRarity(r.rarity).copy(alpha = 0.08f),
                    modifier = Modifier.fillMaxWidth()) {
                    Row(modifier = Modifier.padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically) {
                        Row(verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Box(modifier = Modifier.size(36.dp).clip(androidx.compose.foundation.shape.CircleShape)
                                .background(colorForRarity(r.rarity)),
                                contentAlignment = Alignment.Center) {
                                Text(def?.name?.take(1) ?: "?", color = XueBai,
                                    fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            }
                            Text(def?.name ?: r.monsterId, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            RarityChip(r.rarity, size = ChipSize.SMALL)
                            Text(r.sign.name, fontSize = 11.sp, color = YanHui)
                        }
                    }
                }
            }
        }
    }
}
