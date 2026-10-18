package com.shanhai.yaozhi.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Place
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.shanhai.core.data.defaultRandom
import com.shanhai.yaozhi.LocalGameState
import com.shanhai.yaozhi.LocalGsVersion
import com.shanhai.yaozhi.theme.*
import kotlinx.coroutines.delay
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker

/**
 * 巡妖图 · V2（UI 润色）
 * v2 新增：弹窗 AnimatedVisibility + 信息栏渐变底 + 节点快选 pulse 反馈
 */
data class SpiritNode(
    val name: String,
    val geo: GeoPoint,
    val color: Color,
    val desc: String
)

private val NODES = listOf(
    SpiritNode("城隍桥", GeoPoint(39.9042, 116.4074), ZhuSha, "京城，子时常有鬼修出没"),
    SpiritNode("古庙", GeoPoint(30.2741, 120.1551), JinHuang, "江南，山中古刹藏妖"),
    SpiritNode("老槐树", GeoPoint(34.2658, 108.9541), QingDai, "长安，千年成精之地"),
    SpiritNode("东海之滨", GeoPoint(24.4798, 118.0894), QingDaiLight, "厦门，海妖登陆点"),
)

@Composable
fun MapScreen(
    onEnterCapture: (SpiritNode) -> Unit,
    modifier: Modifier = Modifier
) {
    val gsState = LocalGameState.current
    val _v = LocalGsVersion.current.value
    val gs = gsState.value
    var selectedNode by remember { mutableStateOf<SpiritNode?>(null) }

    // 节点点击 pulse 状态
    var pulsingNode by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(pulsingNode) {
        if (pulsingNode != null) {
            delay(200)
            pulsingNode = null
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                MapView(ctx).apply {
                    setMultiTouchControls(true)
                    controller.setZoom(11.0)
                    controller.setCenter(NODES[0].geo)
                    NODES.forEach { node ->
                        val marker = Marker(this).apply {
                            position = node.geo
                            title = node.name
                            snippet = node.desc
                            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                            icon = android.graphics.drawable.GradientDrawable().apply {
                                setColor(node.color.value.toInt())
                                cornerRadius = 24f
                                setSize(48, 48)
                            }
                            setOnMarkerClickListener { _, _ ->
                                selectedNode = node
                                true
                            }
                        }
                        overlays.add(marker)
                    }
                }
            },
            update = { mapView -> mapView.invalidate() }
        )

        // 顶部标题栏（带渐变底 + border）
        Surface(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(12.dp),
            shape = RoundedCornerShape(16.dp),
            color = XuanZhi.copy(alpha = 0.95f),
            tonalElevation = 4.dp,
            shadowElevation = 8.dp
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(Icons.Filled.Place, null, tint = ZhuSha, modifier = Modifier.size(22.dp))
                Text("巡妖图 · 踏破铁鞋", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = MoHei)
                Surface(shape = RoundedCornerShape(8.dp),
                    color = QingDaiLight.copy(alpha = 0.15f)) {
                    Text("灵玉${gs.lingyu} 香${gs.incense}", fontSize = 11.sp,
                        color = QingDai,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
                }
            }
        }

        // 节点快选 chip 栏（居中，下方）
        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 100.dp, start = 12.dp, end = 12.dp),
            shape = RoundedCornerShape(28.dp),
            color = XuanZhi.copy(alpha = 0.92f),
            tonalElevation = 2.dp
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                NODES.forEach { n ->
                    val isPulsing = pulsingNode == n.name
                    val scale by animateFloatAsState(
                        targetValue = if (isPulsing) 1.1f else 1f,
                        animationSpec = QuickAnim,
                        label = "chipScale"
                    )
                    AssistChip(
                        onClick = {
                            pulsingNode = n.name
                            selectedNode = n
                        },
                        label = { Text(n.name, fontSize = 12.sp,
                            fontWeight = if (selectedNode?.name == n.name) FontWeight.Bold else FontWeight.Normal) },
                        leadingIcon = {
                            Icon(Icons.Filled.Place, null, tint = n.color,
                                modifier = Modifier.size(14.dp))
                        },
                        modifier = Modifier.graphicsLayer { scaleX = scale; scaleY = scale }
                    )
                }
            }
        }

        // 节点详情弹窗（AnimatedVisibility fadeIn + slideInUp）
        AnimatedVisibility(
            visible = selectedNode != null,
            modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp).fillMaxWidth(),
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(tween(300)),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(tween(300))
        ) {
            selectedNode?.let { node ->
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = XuanZhi,
                    tonalElevation = 6.dp,
                    shadowElevation = 10.dp
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(12.dp).clip(RoundedCornerShape(6.dp)).background(node.color))
                            Spacer(Modifier.width(8.dp))
                            Text(node.name, fontSize = 22.sp, fontWeight = FontWeight.Bold,
                                color = node.color)
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(node.desc, fontSize = 14.sp, color = MoHeiSoft,
                            lineHeight = 20.sp)
                        Spacer(Modifier.height(16.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            OutlinedButton(
                                onClick = { selectedNode = null },
                                modifier = Modifier.weight(1f)
                            ) { Text("关闭") }
                            Button(
                                onClick = { onEnterCapture(node); selectedNode = null },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = ZhuSha)
                            ) { Text("前往收妖", color = XueBai, fontWeight = FontWeight.Bold) }
                        }
                    }
                }
            }
        }
    }
}

