package com.venera.compose.components.venera

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import top.yukonga.miuix.kmp.blur.Backdrop
import top.yukonga.miuix.kmp.blur.BlendColorEntry
import top.yukonga.miuix.kmp.blur.BlurDefaults
import top.yukonga.miuix.kmp.blur.ProgressiveBlur
import top.yukonga.miuix.kmp.blur.isRuntimeShaderSupported
import top.yukonga.miuix.kmp.blur.progressiveTextureBlur
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * **页内自持顶栏**那一行的栏级模糊背板（不用 `VeneraTopAppBar` 的那几屏：漫画详情页、
 * pixiv 作品页 —— 它们的顶栏是悬浮在内容之上的，栏本身没有背板就会读成"字直接压在画上"）。
 *
 * 出处只有这一颗：从前它是 `ComicDetailScreen` 里的一枚 `private`（叫 `DetailTopBarBackdrop`），
 * 第二屏要用就得再抄一份 —— 而这一颗的每一档都是"观感数字"（渐变曲线、模糊半径、补底 alpha），
 * 两份迟早长得不一样。配方逐字沿用那一版，只改一处：
 * 默认**恒显**（`visible` 不传就一直在）：作品页那一屏内容铺满整屏、图会穿到状态栏之下，
 * 背板不是一直在就会在某一帧让文字压在锐利画上；详情页那一屏仍按"折叠了才淡入"传自己的判据。
 *
 * 从前下沿还画着一条 0.08 alpha 的分隔线，与 `VeneraTopAppBar` 里那条同配方 —— 2026-10-19 用户
 * 「全部顶栏那种线去掉，影响观感」，两处一起撤（`VeneraTopAppBar` 那侧同理，别再单独加回来）。
 *
 * ⚠️ 两个不能：
 * 1. **不许画在被采样的那一层里面** —— 那颗 `backdrop` 录的就是内容那一堵，背板放进它里面
 *    等于"把模糊再录一遍"，逐帧回灌；
 * 2. 平台不支持 RuntimeShader 或没有采样层时必须走**可见的**回落底（下面那支 `background`），
 *    什么都不画的症状是"顶栏那行字直接压在画上"，与 `VeneraTopBarPill` 同一条判据。
 */
@Composable
fun VeneraTopBarBackdrop(
    backdrop: Backdrop?,
    modifier: Modifier = Modifier,
    visible: () -> Boolean = { true },
    /**
     * **滚动驱动的连续进度**（0..1）。非 null 时不再走 [visible] 那支 200ms 的一刀切淡入，
     * 而是每帧按这个读数定透明度：顶栏模糊跟着手指走，随滚随出、回弹随收（用户 2026-10-19
     * 「在顶部不要模糊，往上滑的时候再出现，这个过程要流畅过渡」）。
     *
     * 交的是取数 lambda 而不是 Float：这个值每帧都在变，当参数传会把整棵顶栏每帧拖进重组；
     * 交给 `graphicsLayer` 在**绘制期**读，只失效图层。
     *
     * 调用方那一侧仍应做"进度贴 0 就不组合这一层"的裁剪（见 `PixivWorkScreen`）：
     * 模糊本身是 RuntimeShader，alpha=0 时也照样跑一遍 —— 停在顶部那段时间不该付这笔钱。
     */
    progress: (() -> Float)? = null,
) {
    val surfaceColor = MiuixTheme.colorScheme.surface
    if (progress != null) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .graphicsLayer { alpha = progress().coerceIn(0f, 1f) }
                .then(glassBlurModifier(backdrop, surfaceColor)),
        )
        return
    }
    val fraction by animateFloatAsState(
        targetValue = if (visible()) 1f else 0f,
        animationSpec = tween(200),
        label = "VeneraTopBarBackdrop",
    )
    if (fraction <= 0f) return
    Box(
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer { alpha = fraction }
            .then(glassBlurModifier(backdrop, surfaceColor)),
    )
}

/**
 * 背板那层"玻璃"本体：两档（真模糊 / 可见回落底）共用同一份配方。
 *
 * 抽成一颗只为让"常显淡入"与"滚动驱动"两条路吃到完全相同的半径、曲线与补底色 ——
 * 两条路各写一遍，同一块顶栏就会随滚动与否长得不一样。
 */
@Composable
private fun glassBlurModifier(backdrop: Backdrop?, surfaceColor: Color): Modifier =
    if (backdrop != null && isRuntimeShaderSupported()) {
        Modifier.progressiveTextureBlur(
            backdrop = backdrop,
            shape = RectangleShape,
            gradient = ProgressiveBlur.Top.copy(curve = 2.2f),
            blurRadius = 10f,
            colors = BlurDefaults.blurColors(
                blendColors = listOf(
                    // 与 `VeneraTopAppBar` 同步：轻补底只保文字对比，玻璃色交给采样层氛围光。
                    BlendColorEntry(color = surfaceColor.copy(alpha = GlassTintAlpha)),
                ),
            ),
        )
    } else {
        Modifier.background(surfaceColor.copy(alpha = 0.85f))
    }
