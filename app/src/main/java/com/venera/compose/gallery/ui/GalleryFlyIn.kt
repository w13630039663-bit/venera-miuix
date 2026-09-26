package com.venera.compose.gallery.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap

/**
 * 卡片 → 大图页的**占位那一帧**：那张卡此刻的像素。
 *
 * 为什么自己画而不用系统那套：翻了本机 SDK 的 `platforms/android-{33,34,35,36}/android.jar`，
 * `android.window` 里只有启动图那套（`SplashScreen` / `SplashScreenView` /
 * `OnExitAnimationListener`），**没有** `SplashScreenViewProvider`、也没有
 * `overrideNextTransitionSplashScreenStartingPoint` —— 跨 Activity 的"共享图像"能力这里够不着。
 * 剩下的经典 View scene transition 要**真的 View** 带 `transitionName`，
 * 而卡片是 Composable，框架会去搬整个 `ComposeView`（不是一张图）。所以只能自带一层。
 *
 * 与 §九 被否掉的"一次性截图当背景"不是一回事：背景要的是**活的**那一屏（交给窗口
 * blur-behind 实时糊），而这里要的**本来就是起点那一帧**，冻结才是对的 ——
 * 它只负责"整页从屏幕下沿升上来"那半秒里画面不空，大图一到位就换掉。
 *
 * 只截卡片那一块矩形（画布平移后整屏只写进这张小位图），不占整屏内存。
 */
object GalleryFlyIn {

    var payload by mutableStateOf<ImageBitmap?>(null)
        private set

    /**
     * 在点击那一刻调用。[cardBounds] 必须是**窗口坐标**
     * （`Modifier.onGloballyPositioned { it.boundsInWindow() }`），
     * 而 [view] 传 `LocalView.current` —— 两者同一坐标系，裁出来的才是那张卡。
     */
    fun capture(view: View, cardBounds: Rect) {
        val left = cardBounds.left.toInt()
        val top = cardBounds.top.toInt()
        val width = cardBounds.width.toInt().coerceAtLeast(1)
        val height = cardBounds.height.toInt().coerceAtLeast(1)
        if (view.width <= 0 || view.height <= 0) return
        runCatching {
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            // 整屏往左上挪回去，只把这一块画进小位图（画布外的像素被裁掉，不占内存）。
            canvas.translate(-left.toFloat(), -top.toFloat())
            view.draw(canvas)
            payload = bitmap.asImageBitmap()
        }
    }

    /** 大图那一档到位就交还，别把这张位图一直攥在手里。 */
    fun consume() {
        payload = null
    }
}
