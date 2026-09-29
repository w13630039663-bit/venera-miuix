package com.venera.compose.gallery.domain

/**
 * 大图页背景那一层（批次 C2）。
 *
 * 四档，**没有"跟随图片主色调"** —— 用户原话里那句"保留现在的样式（提取图片边缘颜色做渐变背景）"
 * 描述的东西从来不存在：现状是窗口 blur-behind 32dp 糊住底下那面墙 + 一层 0.45 黑压暗；
 * 而"边缘取色"这一版我们 2026-09 真做过一次，被真机否掉（"深色主题下头部变成一块边缘清晰的紫色矩形"），
 * `CoverPalette.kt` 已删、androidx.palette 依赖已回退（见 `detail-thumbnail-and-cover-tint-2026-09.md:188-192`）。
 *
 * [opaque] 是这一层唯一需要给判据的地方：非 GLASS 三档都是**不透明底**，
 * 它们自然盖住窗口模糊，所以**不需要**在运行时去动 `FLAG_BLUR_BEHIND`。
 * 反过来，从"纯黑"改回"现状"时模糊要重进大图页才恢复（窗口 flag 是 Activity 起来时挂的）——
 * 这一条如实写进设置页文案，不留"我改了怎么没反应"的悬案。
 */
enum class GalleryViewerBackdrop(val opaque: Boolean, val argb: Long?) {

    /** 现状：窗口模糊底下那面墙 + 0.45 黑压暗。 */
    GLASS(opaque = false, argb = null),

    BLACK(opaque = true, argb = 0xFF000000),

    /**
     * 深灰取 `0xFF121212` —— 全仓唯一已有的深灰口径（`components/backdrop/VeneraLiquidGlassNavBar.kt:119`
     * 暗色那条），不另造一个数。
     */
    DARK_GRAY(opaque = true, argb = 0xFF121212),

    WHITE(opaque = true, argb = 0xFFFFFFFF),
}
