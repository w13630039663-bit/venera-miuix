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
 * blur-behind 实时糊），而这里要的**本来就是起点那一帧**，冻结才是对的。
 * 2026-09-29 第四轮起它多带一个 [origin]：那一帧不再只是"垫着不空"，而是**从卡片原位
 * 飞到大图位置**的那个飞行体 —— 跨窗口做不了真共享元素，那就把它的两样输入
 * （起点那一帧 + 起点矩形）自己递过去，落地后交叉淡入真图，观感同一条。
 *
 * 只截卡片那一块矩形（画布平移后整屏只写进这张小位图），不占整屏内存。
 */
object GalleryFlyIn {

    var payload by mutableStateOf<ImageBitmap?>(null)
        private set

    /**
     * 起点那张卡在**窗口坐标**里的矩形 —— 共享元素那一头要的"从哪儿飞过来"。
     *
     * 两个窗口都是 edge-to-edge 全屏（`VeneraSubActivityBase` 与 MainActivity 同一套），
     * 所以这一串坐标在对面那扇窗口里可以直接用。真机上若发现整体偏了一档状态栏高度，
     * 问题就在这一条假设上，改这里而不是在对面加偏移。
     */
    var origin by mutableStateOf<Rect?>(null)
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
            origin = cardBounds
        }
    }

    /** 大图那一档到位就交还，别把这张位图一直攥在手里。 */
    fun consume() {
        payload = null
        origin = null
    }
}
