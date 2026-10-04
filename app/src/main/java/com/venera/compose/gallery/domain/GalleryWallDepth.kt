package com.venera.compose.gallery.domain

/**
 * 一面墙「离开时看到第几张」的落点判据（2026-10-04，用户报「切走再切回画廊会回到顶部」）。
 *
 * 为什么需要"锚"而不能只存那一个数：
 * 深度记的是**序号**，而序号只对特定的那份内容有意义 —— 换一批之后第 40 张是完全不同的图，
 * 把用户送到那里既不是他离开时的位置，也会让续页判据以为已经到底。
 * 所以落点必须绑在"这块内容还是不是刚才那块"上，判据只认内容头一张的 id。
 *
 * 为什么这份数要活在 `rememberSaveable` 而不是 ViewModel 里：
 * 切主 Tab 走的是 `gotoTab` 的 `popUpTo(startDestination){saveState=true}`，
 * 那条目的地是被 **pop** 掉的 —— `NavBackStackEntry` 类头明写 "ViewModels will be cleared"，
 * `saveState/restoreState` 只救 `rememberSaveable` 那一族。所以「提进 VM 就好了」这一型
 * 治不了切 Tab（既有结论，与日榜 `GalleryFeedCache`、猜你喜欢 `GalleryForYouCache`
 * 之所以存在是同一条理由）。
 */
internal data class SavedWallDepth(
    /** 记下这个深度时，墙上第一张图的 id。 */
    val anchorId: Long,
    /** 当时 `firstVisibleItemIndex`（含墙里的 chrome 行与节头，口径与 `scrollToItem` 一致）。 */
    val index: Int,
)

/**
 * 现在该把这面墙放到第几张；返回 null = **没人要求落点**，调用方按自己的默认走
 * （新一轮内容就该是 0，见 [GallerySearchContext] 那侧的 `pendingScrollRestore`）。
 *
 * 内容还没落地（`currentAnchorId == null`）时一律返回 null —— 那时连"是不是同一块内容"
 * 都无从判断，硬滚一次只会被 Lazy 的夹取按回 0，反而把真实落点抹掉。
 */
internal fun depthToApply(saved: SavedWallDepth?, currentAnchorId: Long?): Int? {
    if (saved == null || currentAnchorId == null) return null
    return saved.index.takeIf { saved.anchorId == currentAnchorId }
}
