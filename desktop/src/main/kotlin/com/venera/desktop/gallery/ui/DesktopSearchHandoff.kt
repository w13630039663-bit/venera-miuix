package com.venera.desktop.gallery.ui

import androidx.compose.runtime.compositionLocalOf
import com.venera.compose.gallery.data.GallerySite
import com.venera.compose.gallery.domain.GallerySearchSource

/**
 * 「把这串词交给搜索那一屏」的载荷（桌面第十二批 批2 · ⑤）。
 *
 * ## 为什么桌面也要有这么一颗槽
 *
 * Android 侧那条主路径是 `GalleryInfoSheet` 点标签 → `GallerySearchHandoff.postTag` →
 * `GalleryScreen` consume → `GallerySearchViewModel.acceptHandoff`。
 * 桌面没有跨 Activity 这个问题（整个应用就一扇窗、状态就在应用根那一颗 `remember` 里），
 * 所以这颗槽的形状是**根上的一份状态 + 一处 provide**，而不是一张全局 object ——
 * 全局 object 在桌面没有任何必要，而它会成为第二份"没人知道有谁在写"的状态
 * （判据 ⑥ 数声明处就是在数这件事）。
 *
 * ## 来源轴跟着站走，而「都打」不算猜
 *
 * Android 那句原文是「两站词表不通，站点不能猜」。桌面这一侧的来源轴第一档就是「全部」，
 * 于是跨站并条的那个名字（画师整页那种）有了第三种诚实的落法：**打全部**。
 * 都打是真做了的一件事；把它写成某一站才是猜。
 */
internal data class DesktopSearchSeed(val query: String, val source: GallerySearchSource, val token: Int)

/**
 * 一枚标签 + 它所属的那些站 → 那颗载荷。
 *
 * 串**原样交**：不改写、不加前缀。排除项那枚减号是站方语法（`GalleryTagFilter.of` 认它），
 * 在这里"顺手去掉减号"会让下一次 parse 变成另一枚标签。
 *
 * [token] 不是装饰：种子是数据类，同一枚标签第二次交接时 `query` 与 `source` 都没变 ⇒
 * 消费方的 `LaunchedEffect(seed)` 不会重启 ⇒ 用户改了输入框之后再点同一枚标签，屏上不动 ——
 * 那是"看起来能点而其实只在第一次管用"。
 */
internal fun desktopSearchSeedFor(sites: List<GallerySite>, tag: String, token: Int): DesktopSearchSeed {
    val distinct = GallerySite.entries.filter { it in sites }
    val source = if (distinct.size == 1) GallerySearchSource(distinct) else GallerySearchSource.ALL
    return DesktopSearchSeed(tag, source, token)
}

/**
 * 交接槽的**句柄**。默认值是 `error` 而不是空操作：
 * 漏 provide 的表现必须是"当场说清没人接"，而不是"按下去没反应"（那是一枚假开关，本仓最忌的形状）。
 *
 * 形状与 [LocalDesktopWallPick] 一致 —— 同一族跨切动作不该有一种给参数、一种给局部。
 */
internal val LocalDesktopSearchHandoff = compositionLocalOf<(List<GallerySite>, String) -> Unit> {
    { _, _ -> error("详情浮层/画师那一屏在没有交接槽的组合里被渲染：点标签会当场炸，而不是安静地什么都不做") }
}
