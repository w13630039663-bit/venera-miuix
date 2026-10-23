package com.venera.compose.components

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween

/**
 * MIUIX / HyperOS 动效的**单一真相**（用户 2026-10-20 第 9 条：「加些 miuix 的预测返回动画」）。
 *
 * 三枚常量逐字取自 miuix 那份参考实现（`_probe/pixez-miuix` 的 `MiuixNavAnimation.kt` /
 * `MiuixPredictiveBackAnimation.kt`），不本地加码 —— 手感对不对，只有与原厂同一组数才算数：
 *
 * - [MiuixDecelerateEasing]：强阻尼、快启动的非线性减速贝塞尔。收尾/回弹一律走它，
 *   与仓内既有的 `ComicSharedTransition.kt:44` 同值 —— 那一份是先落地者，等它哪天
 *   迁到这颗来时把内联删掉即可（本批不重排别人的文件）。
 * - [PredictiveFollowSpec]：**跟手**弹簧。不用 `snapTo` 直驱的原因与仓内既有口径一致：
 *   澎湃 / HyperOS 这类机器在拖动期间只派发少量进度点，逐点落位就是整页跳位；
 *   3000f 无回弹那一档读起来是「几乎贴手指、稀疏时平滑追赶」（`GalleryPostScreen.kt`
 *   与 `PixivWorkScreen.kt` 各自内联的同款，收口同上）。
 * - [PredictiveFinishSpec] / [PredictiveCancelSpec]：确认后的收尾与取消后的回弹，
 *   时长与曲线取 miuix 原值（240ms / 200ms）。
 */
val MiuixDecelerateEasing: CubicBezierEasing = CubicBezierEasing(0.2f, 0.0f, 0.0f, 1.0f)

/** 跟手弹簧：高刚度无回弹，逐帧进度事件下几乎实时贴合手指。 */
val PredictiveFollowSpec = spring<Float>(
    dampingRatio = Spring.DampingRatioNoBouncy,
    stiffness = 3000f,
    visibilityThreshold = 0.0005f,
)

/** 手势确认后的收尾：敏捷轻快（120ms），消除松手后的迟滞等待感。 */
val PredictiveFinishSpec = tween<Float>(durationMillis = 120, easing = MiuixDecelerateEasing)

/** 手势取消后的回弹：160ms 平滑滑回原位。 */
val PredictiveCancelSpec = tween<Float>(durationMillis = 160, easing = MiuixDecelerateEasing)
