package com.venera.compose.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.venera.compose.components.venera.LocalGlassBackdrop
import com.venera.compose.components.venera.blurBackdropSource
import com.venera.compose.feature.LocalVeneraDarkTheme
import com.venera.compose.ui.tokens.veneraGlassEnabled
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 氛围光背景绘制核心。
 *
 * 刻意抽成 [DrawScope] 扩展供两处共用：
 *  1. [VeneraAmbientBackground] 的页面背景 Canvas；
 *  2. 顶栏毛玻璃的采样源（rememberTopBarBackdrop）。
 *
 * 两处必须画出**完全一致**的内容：毛玻璃模糊的是采样层而非屏幕像素，
 * 若采样层只铺纯 surface 而页面实际带 primary/secondary 光斑，
 * 玻璃就会比页面背景偏白，与 MD3 动态取色脱节（用户报告的「模糊玻璃变白」根因）。
 */
fun DrawScope.drawVeneraAmbient(
    primary: Color,
    secondary: Color,
    surface: Color,
    isDark: Boolean,
) {
    val primaryAlpha = if (isDark) 0.28f else 0.40f
    val secondaryAlpha = if (isDark) 0.15f else 0.22f
    val width = size.width
    val glowRadius = width * 0.95f

    // 1. 底色
    drawRect(color = surface)

    // 2. 顶部主色大光斑 (居中稍偏上)
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                primary.copy(alpha = primaryAlpha),
                primary.copy(alpha = primaryAlpha * 0.65f),
                primary.copy(alpha = primaryAlpha * 0.25f),
                Color.Transparent
            ),
            center = Offset(width * 0.5f, -glowRadius * 0.2f),
            radius = glowRadius
        ),
        radius = glowRadius,
        center = Offset(width * 0.5f, -glowRadius * 0.2f)
    )

    // 3. 顶部偏右侧副色光斑
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                secondary.copy(alpha = secondaryAlpha),
                secondary.copy(alpha = secondaryAlpha * 0.5f),
                Color.Transparent
            ),
            center = Offset(width * 0.9f, glowRadius * 0.1f),
            radius = glowRadius * 0.8f
        ),
        radius = glowRadius * 0.8f,
        center = Offset(width * 0.9f, glowRadius * 0.1f)
    )

    // 4. 浅色模式微弱暗化，增强顶栏内容可读性
    if (!isDark) {
        drawRect(color = Color.Black.copy(alpha = 0.04f))
    }
}

/**
 * Apple Music 风格主题色沉浸式氛围光背景
 * 1:1 对齐原版 Flutter [AppBackground._buildAmbient] 效果：
 * - 底部铺满系统 surface 底色
 * - 顶部居中 1.6 倍屏幕宽度的 primary 主题色超大径向渐变光斑
 * - 顶部偏右 secondary 补充色渐变光斑
 * - 零离屏纹理与零高斯卷积开销，纯 GPU 顶点渐变极速渲染
 */
@Composable
fun VeneraAmbientBackground(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val isDark = LocalVeneraDarkTheme.current
    val primaryColor = MiuixTheme.colorScheme.primary
    val surfaceColor = MiuixTheme.colorScheme.surface
    val secondaryColor = MiuixTheme.colorScheme.tertiaryContainer.let {
        if (it != Color.Unspecified) it else Color(0xFF6750A4)
    }

    // 全站玻璃的**唯一采样源**（2026-09-29 拍板：采静态氛围层，不采页面内容层）。
    //
    // 为什么是这一层：下面那个 Canvas 与 content 是**兄弟节点**（Canvas 在底），所以卡片采它
    // 不会自引用 —— 反过来采"页面内容层"就是 `feature/Navigation.kt:504` 注释里记过的那个形状
    // （玻璃挂在录制层的后代里 ⇒ RenderNode 无限递归）。而氛围光不随滚动变 ⇒ 录制层不失效
    // ⇒ **滚动时不重捕**，这正是全站贴玻璃还不忘减帧的前提。
    //
    // 代价如实记着：本文件头注自称的"零离屏纹理"性质**只在 LIQUID_GLASS 档失去**，
    // 所以录制器必须条件安装 —— 实色档一份都不建。
    val glass = veneraGlassEnabled()
    val ambientBackdrop: LayerBackdrop? = if (glass) rememberLayerBackdrop { drawContent() } else null

    Box(modifier = modifier.fillMaxSize()) {
        Canvas(
            Modifier
                .blurBackdropSource(ambientBackdrop)
                .fillMaxSize()
        ) {
            drawVeneraAmbient(
                primary = primaryColor,
                secondary = secondaryColor,
                surface = surfaceColor,
                isDark = isDark,
            )
        }

        // 页面实际内容铺在氛围光之上
        CompositionLocalProvider(LocalGlassBackdrop provides ambientBackdrop) {
            content()
        }
    }
}