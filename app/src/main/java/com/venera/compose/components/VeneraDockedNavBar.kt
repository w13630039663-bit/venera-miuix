package com.venera.compose.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 贴边式画框底栏（Docked Frame Bottom Bar）。
 *
 * 设计特征：
 * 1. 紧凑贴底：高度 54dp，横向贯通贴边延伸至屏幕两侧，消除浮空割裂感，释放 22dp+ 纵向可用视野；
 * 2. 画框式细节（Morph Frame Pill）：未选中项隐藏文字保持极简，选中项平滑呈现文字并被精致圆角画框药丸包裹；
 * 3. 弹性果冻拉伸（Stretch & Rebound）：切换 Tab 时指示器在水平方向前缘突进拉伸、后缘追赶、到达后微超调回弹锁定，底板沉稳不动。
 */
@Composable
fun VeneraDockedNavBar(
    currentTab: VeneraNavTab,
    onTabSelected: (VeneraNavTab) -> Unit,
    modifier: Modifier = Modifier,
    tabs: List<VeneraNavTab> = VeneraNavTab.entries,
) {
    val isDark = isSystemInDarkTheme()
    val ux = LocalUxPlatformServices.current

    // 100% 不透明实底（surface 材质），严禁半透明浮空
    val surfaceColor = MiuixTheme.colorScheme.surface
    val topBorderColor = if (isDark) {
        Color.White.copy(alpha = 0.08f)
    } else {
        Color.Black.copy(alpha = 0.06f)
    }

    val selectedIndex = tabs.indexOf(currentTab).coerceAtLeast(0)
    val tabCount = tabs.size

    // 贴合屏幕底部外壳：全宽、实底（包含系统 navigationBars 区域的背景延伸）
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(surfaceColor)
            .border(
                width = 0.5.dp,
                color = topBorderColor,
                shape = RoundedCornerShape(topStart = 0.dp, topEnd = 0.dp)
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp),
            contentAlignment = Alignment.Center
        ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp)
        ) {
            val totalWidth = maxWidth
            val cellWidth = totalWidth / tabCount
            val basePillWidth = 78.dp

            // 监听目标位置变化，计算水平拉伸量
            var previousIndex by remember { mutableIntStateOf(selectedIndex) }
            val stretchExtra = remember { Animatable(0f) }

            LaunchedEffect(selectedIndex) {
                if (selectedIndex != previousIndex) {
                    val direction = if (selectedIndex > previousIndex) 1f else -1f
                    previousIndex = selectedIndex
                    // 前半程高速拉伸增加宽度
                    stretchExtra.animateTo(
                        targetValue = 26f,
                        animationSpec = spring(stiffness = Spring.StiffnessHigh, dampingRatio = Spring.DampingRatioNoBouncy)
                    )
                    // 后半程平滑减速回弹
                    stretchExtra.animateTo(
                        targetValue = 0f,
                        animationSpec = spring(stiffness = Spring.StiffnessMediumLow, dampingRatio = 0.72f)
                    )
                }
            }

            // 指示器中心横向位移（高阻尼弹簧，平滑跟进与超调）
            val targetCenterX = cellWidth * selectedIndex + (cellWidth / 2)
            val animatedCenterX by animateDpAsState(
                targetValue = targetCenterX,
                animationSpec = spring(
                    dampingRatio = 0.76f,
                    stiffness = Spring.StiffnessMediumLow
                ),
                label = "DockedPillCenterX"
            )

            val currentPillWidth = basePillWidth + stretchExtra.value.dp
            val pillLeft = animatedCenterX - (currentPillWidth / 2)

            // 精致画框药丸指示器
            Box(
                modifier = Modifier
                    .offset(x = pillLeft, y = 8.dp)
                    .width(currentPillWidth)
                    .height(38.dp)
                    .shadow(
                        elevation = 3.dp,
                        shape = RoundedCornerShape(19.dp),
                        spotColor = Color.Black.copy(alpha = if (isDark) 0.35f else 0.08f),
                        ambientColor = Color.Black.copy(alpha = 0.04f)
                    )
                    .clip(RoundedCornerShape(19.dp))
                    .background(
                        MiuixTheme.colorScheme.primaryContainer.copy(
                            alpha = if (isDark) 0.45f else 0.65f
                        )
                    )
                    .border(
                        width = 0.8.dp,
                        color = if (isDark) Color.White.copy(alpha = 0.22f) else MiuixTheme.colorScheme.primary.copy(alpha = 0.25f),
                        shape = RoundedCornerShape(19.dp)
                    )
            )

            // 各 Tab 项
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
                            MiuixTheme.colorScheme.onSurface.copy(alpha = if (isDark) 0.55f else 0.45f)
                        },
                        animationSpec = spring(stiffness = Spring.StiffnessMedium),
                        label = "DockedNavColor"
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
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            AnimatedNavIcon(
                                tab = tab,
                                selected = isSelected,
                                tint = contentColor,
                                baseSize = if (isSelected) 22.dp else 21.dp
                            )
                            AnimatedVisibility(
                                visible = isSelected,
                                enter = fadeIn(spring(stiffness = Spring.StiffnessMedium)),
                                exit = fadeOut(spring(stiffness = Spring.StiffnessHigh)),
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = tab.title,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = contentColor,
                                        maxLines = 1,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        }
        // 避让底部的系统手势导航栏，并保证实底背景色贯通延伸到屏幕物理边缘
        Spacer(modifier = Modifier.navigationBarsPadding())
    }
}
