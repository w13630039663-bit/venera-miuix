package com.venera.compose.gallery.domain

/**
 * 「键盘收起那一下，搜索卡该不该收成一条」的判据。
 *
 * 抽出来的理由是一条真机缺陷（2026-09-29 第四轮）：滚到深处点顶栏🔍 → 点「排行」→
 * 菜单**闪一下就没了**，选期弹层同样。链条是这样的：
 * `DropdownMenu` / `AlertDialog` 是**可聚焦的弹层**，它一开就把焦点从输入框抢走 →
 * 输入法落下 → 页面那道"键盘收起就收成一条"的 effect 点亮 → `mode` 变 RESULTS →
 * 而顶栏此刻折平进度还在 1，`searchCollapsed` 当场成立 → 整块搜索区被移出组合 →
 * **菜单的锚点跟着没了**。顺带卡片收缩动画与网格避让动画抢帧，就是用户说的"一闪一闪"。
 *
 * 所以键盘下落要分两种人：**用户自己收的**（接着看图，该收条）与
 * **被自家弹层挤下去的**（用户还在那一页挑档，不该动）。
 */
object GallerySearchCollapse {

    /**
     * @param imeWasVisible 本轮**确实见过**键盘起来（展开那一帧键盘还没弹，不设这道闸会当场把刚展开的区收掉）。
     * @param popupOpen 排行菜单或选期弹层正开着 —— 这一次下落是它抢焦点抢出来的。
     * @param reverseOpen 「以图搜图」那一层开着：它没有"收成一条"的形态（本来就只有两行），
     *   而从系统相册挑图回来必然伴随一次键盘下落，那一刻收条是错的。
     */
    fun shouldCollapseOnImeHidden(
        imeWasVisible: Boolean,
        popupOpen: Boolean,
        reverseOpen: Boolean,
    ): Boolean = when {
        !imeWasVisible -> false
        popupOpen -> false
        reverseOpen -> false
        else -> true
    }
}
