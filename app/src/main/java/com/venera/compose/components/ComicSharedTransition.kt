package com.venera.compose.components

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect

/**
 * 列表卡片 ↔ 详情页封面这对共享元素的**唯一**口径来源。
 *
 * key 必须两端逐字相同，否则共享元素根本不触发（只会各自淡入淡出）。真机探针实测：
 * 同一张网络收藏卡片上同时能拿到 `jm` 与 `禁漫天堂` 两个源名口径 —— 交给详情页的是
 * sourceKey，而卡片 badge 显示的是显示名 —— 所以这里规定死：**一律用 sourceKey**。
 */
object ComicSharedTransition {

    fun coverKey(sourceKey: String, comicId: String) = "cover-$sourceKey-$comicId"

    /**
     * M-B 曲线：快出慢进，400ms。
     *
     * compose animation 1.12 的 [BoundsTransform] 签名是 `(initialBounds, targetBounds) -> AnimationSpec<Rect>`
     * —— **位置与尺寸共用一条 spec**，1.7 时代那种 position/resize 分轴控制在这里写不出来，
     * 所以只能靠单条曲线形状定夺。400ms 是「眼睛能确认是同一张图」的下限，再短就退化成跳变。
     */
    val CoverBounds = BoundsTransform { _, _ ->
        tween<Rect>(
            durationMillis = 400,
            easing = CubicBezierEasing(0.2f, 0f, 0f, 1f),
        )
    }

    /**
     * 落地槽位用**跟随飞行动画的尺寸**，而不是等到卡片真出现才定尺寸。
     *
     * 返回时列表页那棵子树是从零重组的（导航条目重建），落地那张卡会晚一到数帧才存在；
     * 槽位若在最后一刻才从「无」跳成目标尺寸，观感就是「封面闪一下才归位」。
     * AnimatedSize 让占位随飞行一起收敛，跳变被吸收掉。
     */
    @OptIn(ExperimentalSharedTransitionApi::class)
    val CoverPlaceholderSize: SharedTransitionScope.PlaceholderSize =
        SharedTransitionScope.PlaceholderSize.AnimatedSize
}

/** 列表页 → 详情页封面飞行所需的两个作用域。 */
@Immutable
data class CoverTransitionScopes(
    val shared: SharedTransitionScope,
    val visibility: AnimatedVisibilityScope,
)

/**
 * 由页面根处 provide 一次，深层卡片直接读。
 *
 * 为什么走 CompositionLocal：作用域只有 NavHost 那个条目里有，而卡片埋在
 * 「页面 → 分组 → 行 → 卡」第四层（搜索页就是这样），逐层灌参数会把每一层的签名
 * 都污染一遍。读不到时返回 null —— 那时封面就是不参与飞行的普通封面，页面照常工作，
 * 转场是增强，不该成为渲染前提。
 */
val LocalCoverTransitionScopes =
    staticCompositionLocalOf<CoverTransitionScopes?> { null }

/**
 * 给封面容器挂上「列表 → 详情」共享元素。
 *
 * @param allowFly 打码命中的封面必须传 false：飞行内容会被渲染进
 *   SharedTransitionLayout 的 overlay，等于绕开页面级裁剪，遮罩不能有机会被揭开。
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Modifier.coverSharedElement(key: String, allowFly: Boolean = true): Modifier {
    val scopes = LocalCoverTransitionScopes.current
    if (scopes == null || !allowFly) return this
    return with(scopes.shared) {
        this@coverSharedElement.sharedElement(
            sharedContentState = rememberSharedContentState(key = key),
            animatedVisibilityScope = scopes.visibility,
            boundsTransform = ComicSharedTransition.CoverBounds,
            placeholderSize = ComicSharedTransition.CoverPlaceholderSize,
        )
    }
}
