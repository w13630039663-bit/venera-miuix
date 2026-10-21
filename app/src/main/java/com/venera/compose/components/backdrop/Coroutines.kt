// SPDX-License-Identifier: Apache-2.0
//
// Ported from Kyant0/AndroidLiquidGlass — https://github.com/Kyant0/AndroidLiquidGlass
// app/src/androidMain/kotlin/com/kyant/backdrop/catalog/utils/Coroutines.android.kt
package com.venera.compose.components.backdrop

import com.venera.compose.components.UxPlatformServices

/**
 * 等待下一帧 —— 官方 `DampedDragAnimation.release()` 用它让回弹动画慢一拍启动。
 *
 * ## 2026-10-20 改判：从 `Choreographer` 换成平台服务
 *
 * 原实现（保留下来的原文，供后人核对语义）：
 * ```
 * suspend fun awaitFrame() {
 *     suspendCancellableCoroutine { cont ->
 *         Choreographer.getInstance().postFrameCallback {
 *             if (cont.isActive) cont.resume(Unit)
 *         }
 *     }
 * }
 * ```
 * 那是 `android.view` 的件 —— `components/backdrop/` 要上桌面，这一颗就成了挡路的。
 *
 * 现在帧钟由 [UxPlatformServices.awaitNextFrame] 提供：
 *  - Android 侧仍是 `Choreographer.getInstance().postFrameCallback`（逐字未改，
 *    见 `AndroidUxPlatformServices.awaitNextFrame`）；
 *  - 桌面侧走 Compose 的帧钟（那本来就是跨平台的）。
 *
 * 语义是否等价，说清楚：两者共同的承诺都是「至少过了一帧再回来」，
 * 而调用点要的只是这个先后关系（慢一拍启动），不是「必须由系统 VSYNC 回调驱动」。
 *
 * ⚠️ 写成**扩展函数**而不是新增一个全局 `awaitFrame()`：让调用点显式表达
 * 「我要用这台平台的帧钟」，而不是又一次静默依赖全局状态。
 */
suspend fun UxPlatformServices.awaitFrame() {
    awaitNextFrame()
}
