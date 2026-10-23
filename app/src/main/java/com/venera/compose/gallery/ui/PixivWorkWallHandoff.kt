package com.venera.compose.gallery.ui

import com.venera.compose.gallery.data.GalleryPost

/**
 * 「我是从**相关图片**那一排点进来的」这一件事的交棒件。
 *
 * ## 为什么不能继续借用 [GalleryViewerQueue]（用户 2026-10-19 第 8 条）
 *
 * 那一颗是**一整格全局槽**：`set()` 覆盖、`snapshot()` 按"这一条在不在里面"认。
 * 从相关图片点进去时它被写成那一排，于是有两个洞：
 *
 * 1. **回不去**：宿主从前用 `recreate()` 换号，栈深不变 ⇒ 返回直接落回画廊主屏，
 *    而不是上一条作品（用户原话「返回不会回到上一个作品页」）。
 * 2. **点 A 开 B**：`recreate()` 让系统按**它自己存的那份 Intent** 重开，
 *    运行时 `intent.putExtra(...)` 那一笔并不保证带过去。带不过去时，屏上
 *    `works` 已经是那一排相关图片，而 `postId` 还是上一条 ⇒
 *    `indexOfFirst { it.id == postId }` = -1 ⇒ `coerceAtLeast(0)` = 0 ⇒
 *    **落在那一排的第一张**，正好不是用户点的那张。
 *
 * 现在的分工：
 * - 换作品走**新开一颗 Activity**（`startActivity`，栈深 +1，返回逐级退回）；
 * - 那一排经本件**按目标 id 配对**地交给目标那一条。
 *
 * ## 为什么按 id 配对、且**不做一次性消费**
 *
 * 按 id 配对：交棒途中若用户连点两张，后一笔覆盖前一笔，而先落地的那一颗拿到的是
 * `relatedOf(自己的 id)` ⇒ 认不出 ⇒ 退回 [GalleryViewerQueue] 那面墙，**不会**读到别人的那一排。
 *
 * 不做一次性消费：[GalleryViewerQueue] 的头注记着同一条理由 —— 旋转 / 进程内重建时
 * 这一屏会重新组合，`remember(postId)` 会再读一次。取走即空的话，转个屏就退化成"没有邻居"。
 *
 * 残留怎么清：[GalleryViewerQueue.set] 一被调用就 [clear] —— 语义是
 * **"开了一面新墙 ⇒ 上一笔相关图交棒作废"**。于是"先点相关图、退回、再从画廊墙打开同一条"
 * 这一串不会读到上一笔。
 *
 * 与 [GalleryViewerQueue] 同一路数（跨 Activity 传不了对象，`GalleryPost` 也不是 Parcelable）：
 * 只放内存引用，随进程一起没。
 */
object PixivWorkWallHandoff {

    private var targetId: Long = 0L
    private var wall: List<GalleryPost> = emptyList()

    /** 交棒：`wall` 里必须含 [openedId]，否则这一笔是写错了（宁可落空也不给错邻居）。 */
    fun put(openedId: Long, wall: List<GalleryPost>) {
        if (openedId <= 0L) return
        if (wall.none { it.id == openedId }) return
        targetId = openedId
        this.wall = wall
    }

    /**
     * 取"与 [openedId] 配对的那一排"。
     *
     * @return 认不出（没交过棒、或交的是别人）返回 `null`，
     *   调用方退回 [GalleryViewerQueue.snapshot]。
     */
    fun relatedOf(openedId: Long): List<GalleryPost>? =
        if (openedId > 0L && targetId == openedId) wall else null

    /** 开了一面新墙 ⇒ 上一笔交棒作废（见头注「残留怎么清」）。 */
    fun clear() {
        targetId = 0L
        wall = emptyList()
    }
}
