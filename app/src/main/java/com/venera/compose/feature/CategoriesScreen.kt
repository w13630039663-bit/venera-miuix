/**
 * S0-3 机械拆分自 MainActivity.kt（代码正文逐行原样搬运，未作改写）。
 */
package com.venera.compose.feature

import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import com.venera.compose.reader.*
import com.venera.compose.data.db.*
import com.venera.compose.data.prefs.*
import com.venera.compose.source.model.*

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AndroidCategoriesScreen() {
    val categories = listOf(
        "热血少年" to listOf("战斗", "冒险", "科幻", "竞技", "超能力"),
        "奇幻探索" to listOf("异世界", "转生", "魔法", "迷宫", "魔王"),
        "恋爱日常" to listOf("校园", "搞笑", "治愈", "百合", "纯爱", "职场"),
        "悬疑暗黑" to listOf("推理", "恐怖", "惊悚", "末日", "心理")
    )
    LazyColumn(
        contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 8.dp, bottom = 80.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        items(categories) { (group, tags) ->
            MiuixSectionHeader(title = group, onTap = { })
            Card(modifier = Modifier.fillMaxWidth()) {
                FlowRow(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    tags.forEach { tag ->
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MiuixTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                        ) {
                            Text(
                                text = "$tag ›",
                                fontSize = 13.sp,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

// ==================== 7. 设置页 (SettingsPage) 1:1 复刻 ====================
