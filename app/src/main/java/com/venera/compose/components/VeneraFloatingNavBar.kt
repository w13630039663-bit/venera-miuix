package com.venera.compose.components

import android.view.HapticFeedbackConstants
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.theme.MiuixTheme

enum class VeneraNavTab(
    val title: String,
    val outlinedIcon: ImageVector,
    val filledIcon: ImageVector
) {
    HOME("首页", Icons.Outlined.Home, Icons.Filled.Home),
    // 2026-09-23 信息架构改判（用户点名，评审记录见 FREEZE-STATEMENT.md 同名条目）：
    // 历史从主 Tab 降回二级页。撤这一项**不是删功能**，入口换成三处 ——
    // 首页「历史记录」分区头、设置首页一条、阅读器顶栏胶囊一条。
    // ⚠️ 枚举顺序就是底栏顺序，也是左右横滑翻页顺序（Navigation.kt 的 tabSwipePager
    // 与 VeneraNavTab.entries 单一真相）。腾出来的那个位置给了画廊（下面 GALLERY）——
    // 09-23 我按「收藏右侧第 3 位」预留，09-24 用户改口「放搜索右侧第四个」，以这里为准。
    FAVORITES("收藏", Icons.Outlined.BookmarkBorder, Icons.Filled.Bookmark),
    SEARCH("搜索", Icons.Outlined.Search, Icons.Filled.Search),
    // 画廊：yande.re 图站，与漫画侧完全隔离的独立模块（gallery/ 包，
    // 边界与实测端点见 gallery-module-isolation-plan-2026-09.md）。
    GALLERY("画廊", Icons.Outlined.Image, Icons.Filled.Image),
    // 「分类索引」与「全站探索」已合并为统一的「探索」页（见 UnifiedExploreScreen）。
    // 合并的是页面入口，不是各源的分类体系 —— 每个源仍保留自己的分类 / Tag / 排序。
    EXPLORE("探索", Icons.Outlined.Explore, Icons.Filled.Explore)
}

/**
 * 1:1 复刻原版 Flutter [NaviPane] 与 [LiquidGlassLens] 悬浮胶囊底栏
 * - 居中独立悬浮在屏幕底部
 * - 具备半透明毛玻璃底色、微光外框、软阴影
 * - 内部搭载物理回弹滑动的指示器药丸（Pill Highlight）
 * - 选中态图标由 Outlined 平滑切换为 Filled，颜色由 60% 灰度过渡至主题色
 */
@Composable
fun VeneraFloatingNavBar(
    currentTab: VeneraNavTab,
    onTabSelected: (VeneraNavTab) -> Unit,
    modifier: Modifier = Modifier,
    tabs: List<VeneraNavTab> = VeneraNavTab.entries
) {
    val view = LocalView.current
    val isDark = isSystemInDarkTheme()
    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp.dp

    // 宽屏档走共享口径（WideScreenPolicy）；手机档保持 screenWidth - 28dp 的现定稿观感。
    val barWidth = wideScreenChromeMaxWidth(screenWidth) ?: (screenWidth - 28.dp)
    val tabCount = tabs.size
    val selectedIndex = tabs.indexOf(currentTab).coerceAtLeast(0)

    val surfaceGlassColor = if (isDark) {
        MiuixTheme.colorScheme.surface.copy(alpha = 0.85f)
    } else {
        Color(0xFFFCFCFD).copy(alpha = 0.90f)
    }

    val borderColor = if (isDark) {
        Color.White.copy(alpha = 0.12f)
    } else {
        Color.Black.copy(alpha = 0.08f)
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp),
        contentAlignment = Alignment.BottomCenter
    ) {
        // 外层悬浮胶囊外壳
        Box(
            modifier = Modifier
                .width(barWidth)
                .height(64.dp)
                .shadow(
                    elevation = 16.dp,
                    shape = RoundedCornerShape(32.dp),
                    spotColor = Color.Black.copy(alpha = if (isDark) 0.5f else 0.15f),
                    ambientColor = Color.Black.copy(alpha = 0.08f)
                )
                .clip(RoundedCornerShape(32.dp))
                .background(surfaceGlassColor)
                .border(width = 1.dp, color = borderColor, shape = RoundedCornerShape(32.dp))
        ) {
            // 滑动指示高亮药丸 (Morph Pill)
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val cellWidth = maxWidth / tabCount
                val pillLeftOffset by animateDpAsState(
                    targetValue = cellWidth * selectedIndex + 4.dp,
                    animationSpec = spring(
                        dampingRatio = 0.78f,
                        stiffness = Spring.StiffnessMediumLow
                    ),
                    label = "PillOffset"
                )

                // 物理高亮滑动药丸
                Box(
                    modifier = Modifier
                        .offset(x = pillLeftOffset, y = 6.dp)
                        .width(cellWidth - 8.dp)
                        .height(52.dp)
                        .clip(RoundedCornerShape(26.dp))
                        .background(
                            MiuixTheme.colorScheme.primaryContainer.copy(
                                alpha = if (isDark) 0.35f else 0.45f
                            )
                        )
                )

                // 图标与文字交互列
                Row(
                    modifier = Modifier.fillMaxSize(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    tabs.forEachIndexed { index, tab ->
                        val isSelected = tab == currentTab
                        val contentColor by animateColorAsState(
                            targetValue = if (isSelected) {
                                MiuixTheme.colorScheme.primary
                            } else {
                                MiuixTheme.colorScheme.onSurface.copy(alpha = 0.60f)
                            },
                            animationSpec = spring(stiffness = Spring.StiffnessMedium),
                            label = "NavColor"
                        )

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) {
                                    if (tab != currentTab) {
                                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                        onTabSelected(tab)
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = if (isSelected) tab.filledIcon else tab.outlinedIcon,
                                    contentDescription = tab.title,
                                    tint = contentColor,
                                    modifier = Modifier.size(if (isSelected) 23.dp else 21.dp)
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = tab.title,
                                    fontSize = 10.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = contentColor,
                                    lineHeight = 12.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}