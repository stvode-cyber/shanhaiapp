package com.shanhai.yaozhi.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shanhai.core.data.MonsterPool
import com.shanhai.core.logic.TalentEngine
import com.shanhai.core.model.MonsterInstance
import com.shanhai.core.model.TalentBranch
import com.shanhai.yaozhi.LocalGameState
import com.shanhai.yaozhi.LocalGsVersion
import com.shanhai.yaozhi.mutate
import com.shanhai.yaozhi.components.EmptyState

/**
 * 天赋分配 · V2
 * 从 gs.owned 取真实妖 + gs.assignTalent  mutation
 */
@Composable
fun TalentScreen(modifier: Modifier = Modifier) {
    val gsState = LocalGameState.current
    val versionState = LocalGsVersion.current
    val _v = versionState.value  // 订阅 → 天赋点后自动刷新
    val gs = gsState.value

    val inst = gs.owned.values.firstOrNull()
        ?: MonsterInstance(instanceId = "auto_talent", defId = MonsterPool.all[0].id, level = 10, exp = 0, breakthrough = 0)

    val cap = TalentEngine.talentCap(inst)
    val alloc = TalentEngine.allocated(inst)

    Column(modifier = modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("天赋分配", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text("已分配：$alloc / 上限：$cap", fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant)

        val branches = TalentBranch.values()
        branches.forEach { branch ->
            val layers = TalentEngine.layersOf(inst, branch)
            val canAdd = TalentEngine.canAssign(inst, branch)
            val label = when (branch) {
                TalentBranch.ATTACK -> "攻击分支"
                TalentBranch.DEFENSE -> "防御分支"
                TalentBranch.SUPPORT -> "辅助分支"
            }
            Surface(shape = MaterialTheme.shapes.medium, tonalElevation = 1.dp,
                modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.padding(12.dp).fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(label, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                        Text("已点 $layers 层", fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Button(
                        onClick = {
                            gsState.mutate(versionState) { it.assignTalent(inst.instanceId, branch) }
                        },
                        enabled = canAdd
                    ) { Text("+") }
                }
            }
        }

        if (branches.isEmpty()) {
            EmptyState("✨", "暂无天赋分支")
        }
    }
}
