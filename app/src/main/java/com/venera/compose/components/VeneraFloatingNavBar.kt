package com.venera.compose.components

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
    SEARCH("搜索", Icons.Outlined.Search, Icons.Filled.Search),
    GALLERY("图廊", Icons.Outlined.Image, Icons.Filled.Image),
    FAVORITES("收藏", Icons.Outlined.BookmarkBorder, Icons.Filled.Bookmark),
    EXPLORE("探索", Icons.Outlined.Explore, Icons.Filled.Explore)
}

/**
 * 照原版 Flutter 那套悬浮胶囊底栏（`NaviPane` + `LiquidGlassLens`）做的实现
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
    val isDark = isSystemInDarkTheme()
    // 取宽走跨端口（LocalWindowWidth）：Android 侧供给 screenWidthDp（值逐值不变），
    // 桌面侧读窗口容器宽 —— 大屏底栏因此两端口径同源。替换前这两行是
    // `LocalConfiguration.current.screenWidthDp.dp`，那是 AndroidX 的件，桌面没有。
    val screenWidth = LocalWindowWidthDp
    // 触觉反馈从平台服务来（2026-10-20，写法 A）：`android.view.HapticFeedbackConstants`
    // 是 Android SDK 的件，桌面没有。默认实现静默无操作，Android 侧由入口挂
    // `AndroidUxPlatformServices`，震动手感逐字不变（原 `KEYBOARD_TAP`）。
    val ux = LocalUxPlatformServices.current

    // 宽屏档走共享口径（WideScreenPolicy）；手机档保持 screenWidth - 28dp 的现定稿观感。
    val barWidth = wideScreenChromeMaxWidth(screenWidth) ?: (screenWidth - 28.dp)
    val tabCount = tabs.size
    val selectedIndex = tabs.indexOf(currentTab).coerceAtLeast(0)

    // 两档都吃色板那枚 `surface`：浅色档从前写死 `Color(0xFFFCFCFD)`，换一张深色壁纸
    // 或换一套种子色它仍然是那一点近白 —— 底栏与整屏脱节，而它是屏上最大的一块常驻面。
    // 透明度按档给（浅色档 surface 与页面底色只差一档，要多压一点才读得出"这是一块"）。
    val surfaceGlassColor = MiuixTheme.colorScheme.surface.copy(
        alpha = if (isDark) 0.85f else 0.90f,
    )

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
                                        ux.hapticTap()
                                        onTabSelected(tab)
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                AnimatedNavIcon(
                                    tab = tab,
                                    selected = isSelected,
                                    tint = contentColor,
                                    baseSize = if (isSelected) 23.dp else 21.dp,
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
