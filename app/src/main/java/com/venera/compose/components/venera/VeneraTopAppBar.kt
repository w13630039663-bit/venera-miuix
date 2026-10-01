package com.venera.compose.components.venera

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.venera.compose.components.drawVeneraAmbient
import com.venera.compose.feature.LocalVeneraDarkTheme
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
 * 顶栏这一侧的采样层，供 [VeneraTopBarPill] 直接取用。
 *
 * 为什么要走 CompositionLocal 而不是逐屏传参：`navigationIcon` / `actions` / `bottomContent`
 * 都是 [VeneraTopAppBar] **内部求值**的 composable lambda，而它自己已经收了 `backdrop` 参数——
 * 在这里 provide 一次，13 个调用点一行都不用改。
 * 用 `compositionLocalOf` 不用 static：这个值会随能力检测与 `enableBlur` 在 null↔非 null 之间切，
 * static 会让切换不重组（正是 `VeneraTokens.kt` 那条"切档留旧值"的病的形状）。
 */
val LocalTopBarBackdrop = compositionLocalOf<Backdrop?> { null }

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
 * @param onDoubleTap 双击顶栏空白处（2026-09-30 新增，加性可选）。
 *   画廊用它"双击回到顶部"。**默认 null ⇒ 其余 12 个调用点零改动、零行为差异**。
 *
 *   ⚠️ 手势与顶栏内的动作钮是**并存**的：`detectTapGestures` 挂在顶栏这层 Box 上，
 *   而 `actions` 里那些 `VeneraTopBarPill` 各有自己的 clickable，子组件先消费点击事件，
 *   所以"点胶囊仍然生效、双击空白处才回顶"两条预期同时成立。
 *   这一条**尚未真机验证**（本轮设备未连），真机上要先确认这两件事都成立。
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
    onDoubleTap: (() -> Unit)? = null,
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

    Box(
        modifier = modifier
            .fillMaxWidth()
            // 双击回顶部（只在调用方给了 onDoubleTap 时挂）。
            //
            // ⚠️ 用 `pointerInput` 而不是 `combinedClickable`：后者会给整条顶栏加上按压涟漪与
            // 可点击语义（顶栏不是按钮），也会把点击从子组件手里抢过来。`detectTapGestures`
            // 只认双击那一下、单点不消费，所以 `actions` 里的胶囊照常先拿到自己的点击。
            .then(
                if (onDoubleTap != null) {
                    Modifier.pointerInput(onDoubleTap) {
                        detectTapGestures(onDoubleTap = { onDoubleTap() })
                    }
                } else {
                    Modifier
                },
            ),
    ) {
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
                                // 低强度补底：只负责提升文字对比，不再把玻璃推向白色。
                                // 泛白的主因曾是采样层缺氛围光（见 rememberTopBarBackdrop），
                                // 修掉后这里只需要很轻的一层。
                                BlendColorEntry(color = surfaceColor.copy(alpha = 0.16f)),
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
        // 顶栏里的图标按钮（VeneraTopBarPill）要磨砂就得拿到**同一份**采样层，
        // 否则它会采到别层内容、颜色与背后那条对不上（收藏页那颗当初就是这么对齐的）。
        CompositionLocalProvider(LocalTopBarBackdrop provides backdrop) {
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
}

/**
 * 带有底色保护与平台着色器能力检测的 Backdrop 采样源。
 *
 * 1. 运行时熔断：若硬件/平台不支持 RuntimeShader (Android < API 33) 或主动禁用模糊，返回 null；
 * 2. 氛围光注入：采样层必须复刻 [com.venera.compose.components.drawVeneraAmbient] 的
 *    页面背景（surface 底色 + primary/secondary 取色光斑 + 浅色压暗）。
 *    毛玻璃模糊的是**采样层**而非屏幕像素——若采样层只铺纯 surface，玻璃会比
 *    实际页面背景偏白，与 MD3 动态取色脱节（「模糊玻璃变白」的用户报告根因）。
 */
@Composable
fun rememberTopBarBackdrop(enableBlur: Boolean = true): LayerBackdrop? {
    if (!enableBlur || !isRuntimeShaderSupported()) return null
    val isDark = LocalVeneraDarkTheme.current
    val primary = MiuixTheme.colorScheme.primary
    val secondary = MiuixTheme.colorScheme.tertiaryContainer.let {
        if (it != Color.Unspecified) it else Color(0xFF6750A4)
    }
    val surfaceColor = MiuixTheme.colorScheme.surface
    return rememberLayerBackdrop {
        drawVeneraAmbient(
            primary = primary,
            secondary = secondary,
            surface = surfaceColor,
            isDark = isDark,
        )
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
