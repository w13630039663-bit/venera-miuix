package com.venera.compose.gallery.domain

import kotlin.math.abs

/**
 * 大图页三条行为档位（批次 C1，2026-09-29）的判据。
 *
 * 三条放一个文件是因为它们是**同一屏上的三个行为**（音量键、连播、预加载），而不是一堆无关的小工具：
 * 读这一个文件就能答"这一屏会不会自己动、动之前问谁"。设置页只负责摆档位，一律不自己算。
 */

/** 音量键翻页的目标页；`null` = 这一笔按键我们不吃（交回系统 = 还是音量条）。 */
object GalleryVolumeKeys {
    fun targetPage(
        enabled: Boolean,
        isVolumeDown: Boolean,
        currentPage: Int,
        pageCount: Int,
    ): Int? {
        if (!enabled || pageCount <= 0) return null
        val next = if (isVolumeDown) currentPage + 1 else currentPage - 1
        // 端点不循环、不弹回：阅读器到章末可以跨章，画廊没有"下一章"，到底就是到底
        // （与 GalleryAutoPlay 同一条"到底停"口径，两处不一致就会被读成其中一处坏了）。
        return if (next in 0 until pageCount) next else null
    }
}

/** 大图页自动连播：该不该走下一张。 */
object GalleryAutoPlay {
    fun shouldAdvance(
        seconds: Int,
        infoOpen: Boolean,
        isVideo: Boolean,
        zoomed: Boolean,
        currentPage: Int,
        pageCount: Int,
    ): Boolean {
        if (seconds <= 0) return false
        // 弹层开着 / 正在缩放 = 人正在操作这一张，自动翻走就是抢操作。
        if (infoOpen || zoomed) return false
        // 视频不吃连播（用户 2026-09-29 拍板）：连播不该替人决定何时起播视频、何时出声。
        if (isVideo) return false
        return currentPage < pageCount - 1
    }
}

/**
 * 智能预加载的三档。
 *
 * `OFF` 的真实含义要说清：它关的是**我们主动预取的那批 + 邻居预组合**，
 * 不等于"没有网络流量" —— 当前页自己的 fast/large/file 三档照旧按现状发。
 */
enum class GalleryPreloadMode { OFF, NEXT, BOTH_TWO }

object GalleryPreload {

    /** `HorizontalPager.beyondViewportPageCount` 该给几。这个参数是**对称**的（没有"只向前"那一说）。 */
    fun beyondPages(mode: GalleryPreloadMode): Int = when (mode) {
        GalleryPreloadMode.OFF -> 0
        GalleryPreloadMode.NEXT -> 1
        GalleryPreloadMode.BOTH_TWO -> 2
    }

    /** 主动预取哪几页：不含当前页、夹在列表范围内、近的先来（同距离时向前，因为人多半往后翻）。 */
    fun plan(mode: GalleryPreloadMode, currentPage: Int, pageCount: Int): List<Int> {
        val span = when (mode) {
            GalleryPreloadMode.OFF -> return emptyList()
            GalleryPreloadMode.NEXT -> 1
            GalleryPreloadMode.BOTH_TWO -> 2
        }
        val from = if (mode == GalleryPreloadMode.NEXT) currentPage + 1 else currentPage - span
        val to = currentPage + span
        return (from..to)
            .filter { it != currentPage && it in 0 until pageCount }
            .sortedWith(compareBy({ abs(it - currentPage) }, { if (it > currentPage) 0 else 1 }))
    }
}
