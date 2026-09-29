package com.venera.compose.gallery.domain

/**
 * 大图页动图（GIF / 动图 WebP）要不要解成动画。
 *
 * 只管**大图页**：墙上的卡片一律静帧（实现见 `GalleryAnimationGate` —— 墙上那批请求
 * 从不表态，默认就是静）。这条边界是 2026-09-29 拍板的，理由不是观感而是内存：
 * 一屏动图同时解动画，就是当天那条 OOM 读数的形状。
 *
 * 默认档是 `WIFI_ONLY` 而不是 `ALWAYS`：这一档花的是**原档的流量**，而 2026-09-29 那条同屏闪退
 * 读数（256 MB 堆只剩 52 MB，见 `gallery-round4-and-settings-2026-09.md` §2）里
 * 动图正是最吃那条路的类型。移动网络下要不要为它花这份流量，不该由一个默认值替用户决定。
 */
enum class GalleryAnimatedMode { WIFI_ONLY, ALWAYS, NEVER }

object GalleryMotion {

    /**
     * 这一屏该不该解动画。
     *
     * [unmetered] 由 `GalleryConnectivity` 去问系统（不计费网络 ≈ Wi-Fi/以太网）；
     * 拿不到读数（无活动网络、没权限）一律按**计费**处理 —— 判据宁可少动，不可偷跑流量。
     */
    fun animates(mode: GalleryAnimatedMode, unmetered: Boolean): Boolean = when (mode) {
        GalleryAnimatedMode.ALWAYS -> true
        GalleryAnimatedMode.NEVER -> false
        GalleryAnimatedMode.WIFI_ONLY -> unmetered
    }
}
