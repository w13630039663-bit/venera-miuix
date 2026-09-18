package com.venera.compose.components.venera

import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.venera.compose.ui.tokens.VeneraTokens
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.basic.TopAppBarState
import top.yukonga.miuix.kmp.basic.rememberTopAppBarState

/**
 * Venera 统一顶栏（大标题折叠 + 高斯模糊毛玻璃）。
 *
 * ── 交互契约（对齐 pixez-miuix 的顶栏风格）──
 *  - 顶置态（collapsedFraction == 0）：背景 100% 透明、无模糊，大标题靠左、画面通透；
 *  - 下滑中（0 < fraction < 1）：模糊背板随 fraction 淡入，小标题居中自下而上升起；
 *  - 折叠态（fraction == 1）：全宽高斯模糊 + 底部微弱分割线，小标题居中定格。
 *
 * ── 为什么要自己叠一层模糊背板 ──
 * miuix TopAppBar 的 color 会被直接 background() 铺满整条（含状态栏内边距），
 * 且是不透明铺底、不参与任何透明度插值。想做到「顶置通透 → 下滑磨砂」，
 * 只能把 color 传 Color.Transparent，再由本组件叠一层跟随 collapsedFraction
 * 插值的背板 —— 这样状态栏区域也一起被磨砂覆盖，时间/电量与顶栏融为一体。
 *
 * ── 模糊实现的两条路径 ──
 *  - Android 12+：RenderEffect.createBlurEffect（真实高斯模糊，走 GPU）；
 *  - Android 12 以下：降级为 surface.copy(alpha = 0.88f) 半透明背板
 *    （与 VeneraCover 的降级策略一致：Modifier.blur 在部分老设备/关闭硬件加速时
 *    会静默失效，绝不作为唯一手段）。
 *
 * @param title 折叠后居中的小标题。
 * @param largeTitle 顶置时靠左的大标题；默认与 [title] 相同。
 * @param subtitle 大标题下方副标题（可选）。
 * @param scrollBehavior 由 rememberVeneraTopAppBarBehavior() 创建，需同时挂到内容列表的
 *   Modifier.nestedScroll(...) 上才会随滚动折叠。
 * @param navigationIcon 左侧导航图标（返回箭头等）。
 * @param actions 右侧操作图标。
 * @param bottomContent 顶栏下方常驻内容（如收藏页的分类胶囊），随顶栏一起折叠。
 */
@Composable
fun VeneraTopAppBar(
    title: String,
    modifier: Modifier = Modifier,
    largeTitle: String = title,
    subtitle: String = "",
    scrollBehavior: ScrollBehavior? = null,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    bottomContent: @Composable () -> Unit = {},
) {
    val tokens = VeneraTokens
    val dividerColor = tokens.color.textPrimary.copy(alpha = 0.08f)

    // collapsedFraction 只在布局/绘制期读取，避免每帧重组整条顶栏。
    val collapsedFraction = remember(scrollBehavior) {
        derivedStateOf { scrollBehavior?.state?.collapsedFraction ?: 0f }
    }

    Box(modifier = modifier.fillMaxWidth()) {
        // ── 模糊背板：跟随 collapsedFraction 淡入，覆盖顶栏 + 状态栏 ──
        TopBarBlurBackdrop(
            fraction = { collapsedFraction.value },
            modifier = Modifier.matchParentSize(),
        )

        // ── 底部分割线：alpha = 0.08 * fraction（折叠越深越明显，顶置时不可见）──
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(0.5.dp)
                .graphicsLayer { alpha = 0.08f * collapsedFraction.value }
                .drawBehind { drawDivider(dividerColor) }
                .align(androidx.compose.ui.Alignment.BottomStart),
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

/** 分割线绘制：主文字色 8% 的一条极细分割线。 */
private fun DrawScope.drawDivider(dividerColor: Color) {
    drawRect(color = dividerColor)
}

/**
 * 模糊背板：Android 12+ 走 RenderEffect 真实高斯模糊，低版本降级半透明 surface。
 *
 * @param fraction 折叠进度提供者（在绘制期读取，不触发重组）。
 */
@Composable
private fun TopBarBlurBackdrop(
    fraction: () -> Float,
    modifier: Modifier = Modifier,
) {
    val surfaceColor = VeneraTokens.color.surface
    val density = LocalDensity.current
    val blurRadiusPx = with(density) { 20.dp.toPx() }
    val supportsRenderEffect = remember { Build.VERSION.SDK_INT >= Build.VERSION_CODES.S }

    Box(modifier = modifier.fillMaxWidth()) {
        // 底色层：两种路径共用。RenderEffect 模糊的就是这一层自身，
        // 低版本则直接以 0.88 半透明充当毛玻璃替代品。
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    alpha = if (supportsRenderEffect) fraction() else fraction() * 0.88f
                    if (supportsRenderEffect) {
                        // 真实高斯模糊，走 GPU；CLAMP 避免边缘透明渗色。
                        renderEffect = RenderEffect
                            .createBlurEffect(blurRadiusPx, blurRadiusPx, Shader.TileMode.CLAMP)
                            .asComposeRenderEffect()
                    }
                }
                .drawBehind { drawRect(color = surfaceColor) },
        )
    }
}

/**
 * 创建与 VeneraTopAppBar 配套的滚动行为。
 *
 * 用法：
 *   val behavior = rememberVeneraTopAppBarBehavior()
 *   VeneraTopAppBar(title = "历史", largeTitle = "历史", scrollBehavior = behavior)
 *   LazyColumn(modifier = Modifier.nestedScroll(behavior.nestedScrollConnection)) { ... }
 */
@Composable
fun rememberVeneraTopAppBarBehavior(
    state: TopAppBarState = rememberTopAppBarState(),
    canScroll: () -> Boolean = { true },
): ScrollBehavior = MiuixScrollBehavior(state = state, canScroll = canScroll)
