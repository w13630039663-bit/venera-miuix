package com.venera.compose.gallery.ui

import com.venera.compose.gallery.data.GalleryPost
import com.venera.compose.gallery.data.GallerySite

/**
 * 打开大图时"我是从哪面墙点进来的" —— 大图页据此支持**左右翻页**。
 *
 * 为什么走这种跨 Activity 的静态交接（与 [GalleryFlyIn] 同一路数）：
 * 大图页是独立 Activity（实时 blur-behind + 预测式返回，理由见 `GalleryPostActivity`），
 * 而"同墙的那几张"是内存里的一个列表 —— Intent 里塞不下（`GalleryPost` 不是 Parcelable，
 * 且搜索结果动辄几百条），平台也没有跨 Activity 传对象的通用口子。
 * 所以由**打开方**（[GalleryCardsGrid] 的点击回调）在点击那一刻写进来，大图页读一次。
 *
 * 交接**不做一次性消费**（不像 [GalleryFlyIn] 那张位图要尽早还）：这份列表在旋转、
 * 进程内重建后重新组合时还要用；留在原地比"取走即空"更稳，代价只是几十 KB 的引用。
 *
 * 只放**当前这面墙真正摆出来的那些**（`displayCards`，即已经过屏蔽 / 分级过滤之后的那批）：
 * 左右翻必须与用户刚才看到的顺序一致，把被挡掉的条目也算进来会出现"翻到一张从没见过的图"。
 */
object GalleryViewerQueue {

    private var posts: List<GalleryPost> = emptyList()

    /** 打开一面墙时调（在跳转之前）。顺序即屏上顺序。 */
    fun set(list: List<GalleryPost>) {
        posts = list
    }

    /**
     * 取"与 [site] # [postId] 同墙的那一组"，供大图页左右翻。
     *
     * 认不出这一条（深链进来、或列表是上一次打开的残留）时返回**空列表**，
     * 由大图页退回单张模式 —— 宁可不给翻页，也不能拿一组不相干的图当邻居。
     */
    fun snapshot(site: GallerySite, postId: Long): List<GalleryPost> {
        val uid = "${site.routeKey}:$postId"
        return if (posts.any { it.uid == uid }) posts else emptyList()
    }
}
