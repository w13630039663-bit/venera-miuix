package com.venera.compose.components.venera

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.venera.compose.ui.tokens.VeneraTokens
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.basic.TopAppBarState
import top.yukonga.miuix.kmp.basic.rememberTopAppBarState
import top.yukonga.miuix.kmp.blur.Backdrop
import top.yukonga.miuix.kmp.blur.BlendColorEntry
import top.yukonga.miuix.kmp.blur.BlurDefaults
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.ProgressiveBlur
import top.yukonga.miuix.kmp.blur.isRuntimeShaderSupported
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.progressiveTextureBlur
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * Venera 统一顶栏（对齐 pixez-miuix 规范）：
 * - 顶置时大标题靠左底衬全通透；
 * - 下滑时大标题自动折叠居中，并淡入 HyperOS 渐变式高斯模糊（从状态栏向内容区平滑衰减，无硬切边缘）；
 * - 优雅降级：不支持 RuntimeShader 时平滑降级为 surface 半透明。
 *
 * @param title 折叠后居中的小标题。
 * @param largeTitle 顶置时靠左的大标题；默认与 [title] 相同。
 * @param subtitle 大标题下方副标题（可选）。
 * @param scrollBehavior 滚动行为控制器，需挂载到列表的 nestedScroll 上。
 * @param backdrop 采样源，由 [rememberTopBarBackdrop] 提供并挂载到列表上。
 * @param navigationIcon 左侧导航图标。
 * @param actions 右侧操作图标集合。
 * @param bottomContent 顶栏下方常驻内容（如分类 Tab 等）。
 */
@Composable
fun VeneraTopAppBar(
    title: String,
    modifier: Modifier = Modifier,
    largeTitle: String = title,
    subtitle: String = "",
    scrollBehavior: ScrollBehavior? = null,
    backdrop: Backdrop? = null,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    bottomContent: @Composable () -> Unit = {},
) {
    val tokens = VeneraTokens
    val density = LocalDensity.current
    val surfaceColor = MiuixTheme.colorScheme.surface

    // 动态淡入进度：仅根据 contentOffset（下滑 48dp 线性淡入），对齐 pixez-miuix 规范。
    // 不使用 collapsedFraction——它在大标题折叠动画中会快速跳至 1.0，导致模糊背板过早 100% 不透明（白色覆盖）。
    val scrollProgress = remember(scrollBehavior) {
        derivedStateOf {
            val state = scrollBehavior?.state ?: return@derivedStateOf 0f
            val thresholdPx = with(density) { 48.dp.toPx() }
            if (thresholdPx > 0f) (-state.contentOffset / thresholdPx).coerceIn(0f, 1f) else 0f
        }
    }

    Box(modifier = modifier.fillMaxWidth()) {
        // ── 真实渐变高斯模糊背板（对齐 pixez-miuix 规范）──
        if (backdrop != null && isRuntimeShaderSupported()) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .graphicsLayer {
                        alpha = scrollProgress.value
                    }
                    .progressiveTextureBlur(
                        backdrop = backdrop,
                        shape = RectangleShape,
                        // 从状态栏开始 100% 强度模糊，底部平滑衰减至 0，curve = 2.2f 杜绝硬切边缘
                        gradient = ProgressiveBlur.Top.copy(curve = 2.2f),
                        blurRadius = 10f,
                        colors = BlurDefaults.blurColors(
                            blendColors = listOf(
                                BlendColorEntry(color = surfaceColor.copy(alpha = 0.3f)),
                            ),
                        ),
                    ),
            )
        } else {
            // 降级兜底：不支持 RuntimeShader 时显示半透明底色
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .graphicsLayer {
                        alpha = scrollProgress.value
                    }
                    .background(surfaceColor.copy(alpha = 0.85f)),
            )
        }

        // ── 底部分割线：alpha = 0.08 * fraction（折叠越深越明显，顶置时完全透明不可见）──
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(0.5.dp)
                .graphicsLayer { alpha = 0.08f * scrollProgress.value }
                .background(tokens.color.textPrimary)
                .align(Alignment.BottomStart),
        )

        // ── 顶栏本体：miuix TopAppBar，底色透明，由背板负责视觉背景 ──
        TopAppBar(
            title = title,
            largeTitle = largeTitle,
            subtitle = subtitle,
            color = Color.Transparent,
            titleColor = tokens.color.textPrimary,
            largeTitleColor = tokens.color.textPrimary,
            subtitleColor = tokens.color.textSecondary,
            navigationIcon = navigationIcon,
            actions = actions,
            scrollBehavior = scrollBehavior,
            bottomContent = bottomContent,
        )
    }
}

/**
 * 带有底色保护与平台着色器能力检测的 Backdrop 采样源。
 *
 * 1. 运行时熔断：若硬件/平台不支持 RuntimeShader (Android < API 33) 或主动禁用模糊，返回 null；
 * 2. 底色注入：在 onDraw 最底层绘制 surfaceColor，确保采样源在透明区域有不透明背景支撑，杜绝 Skia 高斯模糊边缘采样透明导致的暗黑伪影与噪点。
 */
@Composable
fun rememberTopBarBackdrop(enableBlur: Boolean = true): LayerBackdrop? {
    if (!enableBlur || !isRuntimeShaderSupported()) return null
    val surfaceColor = MiuixTheme.colorScheme.surface
    return rememberLayerBackdrop {
        drawRect(surfaceColor)
        drawContent()
    }
}

/**
 * 方便内容列表快捷挂载 Backdrop 采样源。
 */
fun Modifier.blurBackdropSource(backdrop: LayerBackdrop?): Modifier =
    if (backdrop != null) this.layerBackdrop(backdrop) else this

/**
 * 创建与 VeneraTopAppBar 配套的滚动行为。
 */
@Composable
fun rememberVeneraTopAppBarBehavior(
    state: TopAppBarState = rememberTopAppBarState(),
    canScroll: () -> Boolean = { true },
): ScrollBehavior = MiuixScrollBehavior(state = state, canScroll = canScroll)
