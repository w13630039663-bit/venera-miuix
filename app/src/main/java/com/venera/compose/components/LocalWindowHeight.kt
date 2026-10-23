package com.venera.compose.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Dp

/**
 * 跨端的**窗口高**读口 —— 与 [LocalWindowWidthDp] 同一路数、同一颗 `LocalWindowInfo`。
 *
 * ## 为什么不是 `LocalConfiguration.current.screenHeightDp`
 *
 * 理由与宽度那颗逐条相同（见 `LocalWindowWidth.kt` 头注 ②）：`LocalConfiguration` 是 AndroidX 的件，
 * 桌面侧的 Compose Multiplatform 里没有 —— 谁就地读它，那一颗文件就出不了 `:desktop` 的编译面。
 * 而这里唯一的需求只是"这一帧的窗口有多高"，`LocalWindowInfo.containerSize` 两端都有，
 * 语义还更准（分屏 / 自由窗口下容器高 ≠ 物理屏高）。
 *
 * 与宽度那颗的**唯一差别**：宽度有一层 `ProvideLocalWindowWidth`（Android 侧显式供 `screenWidthDp`，
 * 为的是"手机档与平板档一个像素都不动"）。高度没有这一层，因为这里没有要保住的旧读数 ——
 * 上一处读它的地方（作品页横屏那档的高度上限）读的正是 `screenHeightDp`，
 * 而 `containerSize.height` 在整屏窗口下与它同值。
 */
val LocalWindowHeightDp: Dp
    @Composable
    @ReadOnlyComposable
    get() = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.height.toDp() }
