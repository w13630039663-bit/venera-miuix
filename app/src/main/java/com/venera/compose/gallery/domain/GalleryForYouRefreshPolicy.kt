package com.venera.compose.gallery.domain

/**
 * 「猜你喜欢那一屏要不要沿用上次取到的」的判据（批次 O）。
 *
 * ## 它治的是什么
 *
 * 切 Tab 时画廊那条目的地是**被 pop 掉的**（`gotoTab` 用
 * `popUpTo(startDestination){saveState=true}`），而 `NavBackStackEntry` 的 KDoc 明写
 * pop 时 "ViewModels will be cleared" —— `saveState` 只救 `rememberSaveable`，救不了
 * `viewModel()`。所以每次回到画廊，那一屏的 VM 都是全新的。
 *
 * 日榜那一屏 2026-09-29 就为这件事配过一份离线快照加同日门
 * （[GalleryFeedRefreshPolicy]），但**猜你喜欢这一屏从来没接上同一套**：它没有缓存，
 * 于是每次进来必发一笔请求，而且 `seed` 是新实例的 `System.currentTimeMillis()`，
 * 连推荐标签都重抽一遍 —— 用户读到的就是"整个页面重新加载了一遍，内容还换了"。
 *
 * 本轮补的是那条既定口径的**另一半**，不是新口径：同一天之内不自动联网，
 * 要新内容靠「换一批」。判据与日榜共用 [GalleryFeedRefreshPolicy.shouldRefetch]，不留第二份。
 */
object GalleryForYouRefreshPolicy {

    /**
     * 快照够不够格"直接铺上屏"（= VM 进"已铺缓存"那一档）。
     *
     * 两个条件必须**成对**成立，各缺一个都会静默出错：
     *
     * - **内容非空**：空快照如果算数，[shouldAutoLoad] 就恒为 false —— 一屏空白而门是关着的，
     *   冷启动之后**永远不再取**。那比"每次都刷新"坏得多（那种至少还有内容）。
     *   落盘那头同样只存拿到内容的那一轮，这里是第二道闸。
     * - **种子有效**（正数）：这一屏是按种子从两站原始池里重排出来的。种子丢了还沿用那一屏，
     *   下一次「换一批」换的就是一个与屏上那份无关的池子 —— 用户看到的是"换了一批，
     *   但上一批里我想要的东西再也找不回来了"。`0` 只可能是"没存上"，不是 1970 年那一轮。
     */
    fun canHydrate(postCount: Int, seed: Long?): Boolean =
        postCount > 0 && seed != null && seed > 0L

    /**
     * 这一轮要不要联网取。
     *
     * 与日榜那头同一条：没铺上缓存就取；铺上了，则只有跨天才补拉。
     *
     * ⚠️ `hydrated` 排在日期判断**前面**是有原因的：用户点「换一批」时 VM 会把两个标记
     * 一起清掉（照 `GalleryViewModel.refresh()` 那一对），此时如果还去看日期，
     * "今天存过"就会把「换一批」拦成假按钮。
     */
    fun shouldAutoLoad(
        hydrated: Boolean,
        cachedOnEpochDay: Long?,
        todayEpochDay: Long,
    ): Boolean = !hydrated || GalleryFeedRefreshPolicy.shouldRefetch(cachedOnEpochDay, todayEpochDay)
}
