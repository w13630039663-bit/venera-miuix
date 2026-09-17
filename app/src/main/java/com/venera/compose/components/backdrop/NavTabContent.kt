// SPDX-License-Identifier: Apache-2.0
//
// 官方 LiquidBottomTabs 的 content 插槽实现（对应官方 catalog 里每个 tab 的图标 + 标签）。
//
// 关键点：官方把同一份 [content] 渲染了三次（外壳层、隐形着色层、指示器层都各自 render 一遍），
// 所以这里必须是纯函数式、无副作用、无自身状态的 Composable —— 用当前选中态决定图标与颜色。
package com.venera.compose.components.backdrop

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.venera.compose.components.VeneraNavTab
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 单个导航项。图标、颜色与字重共同表达选中态；
 * 按压放大比例读 [LocalLiquidBottomTabScale]（由官方组件在外层提供）。
 */
@Composable
fun RowScope.VeneraLiquidNavTab(
    tab: VeneraNavTab,
    selected: Boolean,
    isDark: Boolean,
    onTabSelected: (VeneraNavTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val contentColor by animateColorAsState(
        targetValue = if (selected) MiuixTheme.colorScheme.primary
        else MiuixTheme.colorScheme.onSurface.copy(alpha = if (isDark) 0.7f else 0.6f),
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "NavTabColor"
    )
    val tabScale = LocalLiquidBottomTabScale.current()

    Column(
        modifier = modifier
            .weight(1f)
            .selectable(
                selected = selected,
                role = Role.Tab,
                interactionSource = interaction,
                indication = null,
                onClick = { if (!selected) onTabSelected(tab) },
            )
            .graphicsLayer {
                scaleX = tabScale
                scaleY = tabScale
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = if (selected) tab.filledIcon else tab.outlinedIcon,
            contentDescription = tab.title,
            tint = contentColor,
            modifier = Modifier.size(if (selected) 23.dp else 21.dp)
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = tab.title,
            fontSize = 10.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = contentColor,
        )
    }
}

/** 官方组件的 content 插槽入口：一次性铺出所有 tab。 */
@Composable
fun RowScope.VeneraLiquidNavTabs(
    tabs: List<VeneraNavTab>,
    currentTab: VeneraNavTab,
    isDark: Boolean,
    onTabSelected: (VeneraNavTab) -> Unit,
) {
    tabs.forEach { tab ->
        VeneraLiquidNavTab(
            tab = tab,
            selected = tab == currentTab,
            isDark = isDark,
            onTabSelected = onTabSelected,
        )
    }
}
