package com.venera.compose.components

import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
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
     * 返回时收藏页那棵子树是从零重组的（导航条目重建，真机实测连 `mode` 都被打回默认值），
     * 落地那张卡会晚一到数帧才存在；槽位若在最后一刻才从「无」跳成「122×169」，
     * 观感就是「封面闪一下才归位」。AnimatedSize 让占位随飞行一起收敛，跳变被吸收掉。
     */
    @OptIn(ExperimentalSharedTransitionApi::class)
    val CoverPlaceholderSize: SharedTransitionScope.PlaceholderSize =
        SharedTransitionScope.PlaceholderSize.AnimatedSize
}
