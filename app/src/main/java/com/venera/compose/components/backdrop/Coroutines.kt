// SPDX-License-Identifier: Apache-2.0
//
// Ported from Kyant0/AndroidLiquidGlass — https://github.com/Kyant0/AndroidLiquidGlass
// app/src/androidMain/kotlin/com/kyant/backdrop/catalog/utils/Coroutines.android.kt
package com.venera.compose.components.backdrop

import android.view.Choreographer
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/** 等待下一帧。官方 DampedDragAnimation.release() 用它让回弹动画慢一拍启动。 */
suspend fun awaitFrame() {
    suspendCancellableCoroutine { cont ->
        Choreographer.getInstance().postFrameCallback {
            if (cont.isActive) cont.resume(Unit)
        }
    }
}
