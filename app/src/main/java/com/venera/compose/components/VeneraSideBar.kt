package com.venera.compose.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuOpen
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.venera.compose.ui.tokens.VeneraTokens
import top.yukonga.miuix.kmp.basic.Text

/**
 * 大屏侧边导航栏（Navigation Rail - MD3 Expressive）。
 *
 * 设计特征：
 * 1. 顶部避让与展开控制：顶部避开状态栏安全区（statusBarsPadding + 顶部间距），配备折叠/展开按钮（Menu / MenuOpen）；
 * 2. 药丸形状防变形：折叠态下指示器为标准药丸尺寸（56x32dp），水平垂直居中包住图标，禁止横向过度拉伸；
 * 3. 标签与垂直居中：图标下方配备清晰文字标签，所有项垂直居中对称排布；
 * 4. 展开模式（Expanded Rail）：展开后呈现“左图标 + 右文字”的横向大条目，选中态背景为精致大圆角矩形；
 * 5. 内容避让间距：侧栏与右侧内容区（画廊搜索框、瀑布流等）之间保持舒适的 24dp 边距。
 */
@Composable
fun VeneraSideBar(
    currentTab: VeneraNavTab?,
    onTabSelected: (VeneraNavTab) -> Unit,
    mode: WideScreenLayoutMode,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val tokens = VeneraTokens
    val ux = LocalUxPlatformServices.current

    if (mode == WideScreenLayoutMode.Compact) {
        Box(modifier = modifier.fillMaxSize()) { content() }
        return
    }

    // 用户支持手动展开/折叠（超大窗默认展开，宽窗支持按钮切换）
    var userExpanded by rememberSaveable { mutableStateOf(mode == WideScreenLayoutMode.Expanded) }
    val isExpanded = userExpanded

    // 侧栏宽度平滑过渡：折叠 80dp，展开 200dp
    val targetRailWidth = if (isExpanded) 200.dp else 80.dp
    val animatedRailWidth by animateDpAsState(
        targetValue = targetRailWidth,
        animationSpec = spring(stiffness = 500f),
        label = "RailWidthAnimation",
    )

    Row(modifier = modifier.fillMaxSize()) {
        // ── 侧栏本体 ──
        Column(
            modifier = Modifier
                .width(animatedRailWidth)
                .fillMaxHeight()
                .background(tokens.color.surfaceContainerHigh)
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // 顶部折叠/展开按钮（顶部安全区内）
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                contentAlignment = if (isExpanded) Alignment.CenterStart else Alignment.Center,
            ) {
                IconButton(
                    onClick = {
                        ux.hapticTap()
                        userExpanded = !userExpanded
                    },
                    modifier = Modifier.size(40.dp),
                ) {
                    Icon(
                        imageVector = if (isExpanded) Icons.AutoMirrored.Filled.MenuOpen else Icons.Default.Menu,
                        contentDescription = if (isExpanded) "折叠导航栏" else "展开导航栏",
                        tint = tokens.color.textSecondary,
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 导航条目列表：整体居中排列
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
            ) {
                VeneraNavTab.entries.forEach { tab ->
                    val selected = tab == currentTab
                    val contentColor = if (selected) tokens.color.primary else tokens.color.textSecondary

                    if (isExpanded) {
                        // ── 展开模式：左图标、右文字的横向列表项，选中为大圆角矩形 ──
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                ) {
                                    if (!selected) {
                                        ux.hapticTap()
                                        onTabSelected(tab)
                                    }
                                }
                                .background(
                                    if (selected) {
                                        tokens.color.primary.copy(alpha = tokens.current.selectedSurfaceAlpha)
                                    } else {
                                        Color.Transparent
                                    }
                                )
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            AnimatedNavIcon(
                                tab = tab,
                                selected = selected,
                                tint = contentColor,
                                baseSize = 24.dp,
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Text(
                                text = tab.title,
                                fontSize = 14.sp,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                color = contentColor,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    } else {
                        // ── 折叠模式：标准 56x32dp 药丸胶囊包住图标，下方带居中文字 ──
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                ) {
                                    if (!selected) {
                                        ux.hapticTap()
                                        onTabSelected(tab)
                                    }
                                },
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            // 56x32dp 标准药丸背景，居中包住图标，不拉伸
                            Box(
                                modifier = Modifier
                                    .size(width = 56.dp, height = 32.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(
                                        if (selected) {
                                            tokens.color.primary.copy(alpha = tokens.current.selectedSurfaceAlpha)
                                        } else {
                                            Color.Transparent
                                        }
                                    ),
                                contentAlignment = Alignment.Center,
                            ) {
                                AnimatedNavIcon(
                                    tab = tab,
                                    selected = selected,
                                    tint = contentColor,
                                    baseSize = 22.dp,
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = tab.title,
                                fontSize = 11.sp,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                color = contentColor,
                                maxLines = 1,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }
            }
        }

        // ── 内容层：与左侧导航栏保持 24dp 边距 ──
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 24.dp),
        ) {
            content()
        }
    }
}
